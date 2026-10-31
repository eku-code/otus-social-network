#!/usr/bin/env python3
"""Нагрузочный тест GET /user/search.

Запускает серию прогонов с разной конкурентностью (1/10/100/1000 виртуальных
пользователей). Каждый прогон длится фиксированное время; замеряются latency
каждого запроса и throughput. Результаты пишутся в JSON.

Зависимости: только стандартная библиотека Python 3.
"""
import argparse
import http.client
import json
import random
import statistics
import threading
import time
import urllib.parse
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor

FIRST_PREFIXES = ["Але", "Конст", "Дмит", "Еле", "Ива", "Оле", "Сер", "Нат", "Мари", "Влад"]
LAST_PREFIXES = ["Смир", "Куз", "Оси", "Поп", "Вас", "Пет", "Ива", "Сок", "Мор", "Нов"]


def percentile(sorted_values, p):
    if not sorted_values:
        return 0.0
    idx = min(int(len(sorted_values) * p / 100.0), len(sorted_values) - 1)
    return sorted_values[idx]


def run(args, stop_event, latencies, errors, lock):
    prefix_pairs = list(zip(
        random.choices(FIRST_PREFIXES, k=50), random.choices(LAST_PREFIXES, k=50)))
    i = 0
    while not stop_event.is_set():
        fn, ln = prefix_pairs[i % len(prefix_pairs)]
        i += 1
        qs = urllib.parse.urlencode({"first_name": fn, "last_name": ln})
        path = f"/user/search?{qs}"
        start = time.perf_counter()
        status = 0
        try:
            conn = http.client.HTTPConnection(args.host, args.port, timeout=60)
            conn.request("GET", path, headers={"Authorization": f"Bearer {args.token}"})
            resp = conn.getresponse()
            resp.read()
            status = resp.status
            conn.close()
        except Exception:
            status = -1
        elapsed_ms = (time.perf_counter() - start) * 1000.0
        with lock:
            latencies.append(elapsed_ms)
            if status != 200:
                errors[status] += 1


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="localhost")
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--token", required=True)
    ap.add_argument("--concurrency", type=int, required=True)
    ap.add_argument("--duration", type=int, default=30, help="секунд на прогон")
    ap.add_argument("--warmup", type=int, default=5, help="секунд разогрева")
    ap.add_argument("--label", default="", help="метка прогона (before/after)")
    ap.add_argument("--out", default="", help="куда дописать результат (json-файл)")
    args = ap.parse_args()

    lock = threading.Lock()
    stop_event = threading.Event()

    # Разогрев (JIT, пулы соединений, кэши БД).
    if args.warmup > 0:
        warm_stop = threading.Event()
        warm_lat, warm_err = [], defaultdict(int)
        with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
            futures = [pool.submit(run, args, warm_stop, warm_lat, warm_err, lock)
                       for _ in range(args.concurrency)]
            time.sleep(args.warmup)
            warm_stop.set()
        time.sleep(1)

    latencies, errors = [], defaultdict(int)
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = [pool.submit(run, args, stop_event, latencies, errors, lock)
                   for _ in range(args.concurrency)]
        time.sleep(args.duration)
        stop_event.set()

    lat_sorted = sorted(latencies)
    result = {
        "label": args.label,
        "concurrency": args.concurrency,
        "duration_s": args.duration,
        "requests": len(latencies),
        "throughput_rps": round(len(latencies) / args.duration, 1),
        "latency_ms": {
            "min": round(lat_sorted[0], 2) if lat_sorted else 0,
            "mean": round(statistics.mean(latencies), 2) if latencies else 0,
            "p50": round(percentile(lat_sorted, 50), 2),
            "p95": round(percentile(lat_sorted, 95), 2),
            "p99": round(percentile(lat_sorted, 99), 2),
            "max": round(lat_sorted[-1], 2) if lat_sorted else 0,
        },
        "errors": dict(errors),
    }
    print(json.dumps(result, ensure_ascii=False))

    if args.out:
        try:
            with open(args.out) as f:
                data = json.load(f)
        except (FileNotFoundError, json.JSONDecodeError):
            data = []
        data = [r for r in data if r.get("concurrency") != args.concurrency
                or r.get("label") != args.label]
        data.append(result)
        with open(args.out, "w") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()
