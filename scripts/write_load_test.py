#!/usr/bin/env python3
"""Нагрузочный тест записи: POST /user/register.

Считает, сколько строк успешно записано (клиентски: ответ 2xx + валидный user_id).
Поддерживает --kill-cmd: команда (например, docker kill мастера) выполняется
через --kill-at секунд после начала измерений — для эксперимента с потерей
транзакций при аварийной остановке мастера.

Зависимости: только стандартная библиотека Python 3.
"""
import argparse
import http.client
import json
import random
import subprocess
import threading
import time
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor

FIRST_NAMES = ["Александр", "Константин", "Дмитрий", "Елена", "Иван",
               "Ольга", "Сергей", "Наталья", "Мария", "Владимир"]
LAST_NAMES = ["Смирнов", "Кузнецов", "Осипов", "Попов", "Васильев",
              "Петров", "Иванов", "Соколов", "Морозов", "Новиков"]
CITIES = ["Москва", "Санкт-Петербург", "Казань", "Новосибирск", "Екатеринбург"]


def run(args, stop_event, counters, written_ids, lock):
    while not stop_event.is_set():
        body = json.dumps({
            "first_name": random.choice(FIRST_NAMES),
            "second_name": random.choice(LAST_NAMES),
            "birthdate": f"19{random.randint(60, 99)}-0{random.randint(1, 9)}-1{random.randint(0, 9)}",
            "biography": "load test",
            "city": random.choice(CITIES),
            "gender": random.choice(["male", "female"]),
            "password": "secret",
        }, ensure_ascii=False).encode("utf-8")
        status = 0
        user_id = None
        try:
            conn = http.client.HTTPConnection(args.host, args.port, timeout=60)
            conn.request("POST", "/user/register", body=body,
                         headers={"Content-Type": "application/json"})
            resp = conn.getresponse()
            resp_body = resp.read()
            status = resp.status
            if status == 200:
                user_id = json.loads(resp_body).get("user_id")
            conn.close()
        except Exception:
            status = -1
        with lock:
            counters[status] += 1
            if user_id:
                written_ids.append(user_id)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="localhost")
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--concurrency", type=int, default=10)
    ap.add_argument("--duration", type=int, default=30, help="секунд записи")
    ap.add_argument("--warmup", type=int, default=2, help="секунд разогрева")
    ap.add_argument("--label", default="", help="метка прогона (async/sync2)")
    ap.add_argument("--kill-cmd", default="", help="команда аварийной остановки мастера")
    ap.add_argument("--kill-at", type=int, default=0, help="через сколько секунд после начала измерений выполнить --kill-cmd")
    ap.add_argument("--out", default="", help="куда дописать результат (json-файл)")
    args = ap.parse_args()

    lock = threading.Lock()
    stop_event = threading.Event()
    counters = defaultdict(int)
    written_ids = []

    if args.warmup > 0:
        warm_stop = threading.Event()
        with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
            for _ in range(args.concurrency):
                pool.submit(run, args, warm_stop, defaultdict(int), [], lock)
            time.sleep(args.warmup)
            warm_stop.set()
        time.sleep(1)

    if args.kill_cmd and args.kill_at > 0:
        def killer():
            time.sleep(args.kill_at)
            print(f"Executing kill command: {args.kill_cmd}", flush=True)
            subprocess.run(args.kill_cmd, shell=True, check=False)
        threading.Thread(target=killer, daemon=True).start()

    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        for _ in range(args.concurrency):
            pool.submit(run, args, stop_event, counters, written_ids, lock)
        time.sleep(args.duration)
        stop_event.set()

    ok = counters.get(200, 0)
    result = {
        "label": args.label,
        "concurrency": args.concurrency,
        "duration_s": args.duration,
        "successful_writes": ok,
        "total_attempts": sum(counters.values()),
        "status_counts": {str(k): v for k, v in sorted(counters.items())},
    }
    print(json.dumps(result, ensure_ascii=False))

    if args.out:
        with open(args.out, "w") as f:
            json.dump({"result": result, "written_ids": written_ids}, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()
