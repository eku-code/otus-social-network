#!/usr/bin/env python3
"""Построение графиков latency/throughput из результатов load_test.py."""
import argparse
import json

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--out-prefix", required=True)
    ap.add_argument("--label", default="", help="фильтр по метке прогона (before/after)")
    args = ap.parse_args()

    with open(args.input) as f:
        data = json.load(f)
    if args.label:
        data = [r for r in data if r.get("label") == args.label]
    data = sorted(data, key=lambda r: r["concurrency"])
    if not data:
        raise SystemExit(f"no records for label={args.label!r}")

    conc = [r["concurrency"] for r in data]
    lat = {k: [r["latency_ms"][k] for r in data]
           for k in ("p50", "p95", "p99", "mean")}
    tps = [r["throughput_rps"] for r in data]

    title_suffix = f" ({args.label})" if args.label else ""
    fig, ax = plt.subplots(figsize=(8, 5))
    for k, series in lat.items():
        ax.plot(conc, series, marker="o", label=k)
    ax.set_xscale("log")
    ax.set_yscale("log")
    ax.set_xticks(conc)
    ax.get_xaxis().set_major_formatter(matplotlib.ticker.ScalarFormatter())
    ax.set_xlabel("Одновременные запросы (VU)")
    ax.set_ylabel("Latency, мс")
    ax.set_title(f"Latency GET /user/search{title_suffix}")
    ax.grid(True, which="both", alpha=0.3)
    ax.legend()
    fig.tight_layout()
    fig.savefig(f"{args.out_prefix}_latency.png", dpi=150)

    fig, ax = plt.subplots(figsize=(8, 5))
    ax.plot(conc, tps, marker="o", color="tab:green")
    ax.set_xscale("log")
    ax.set_xticks(conc)
    ax.get_xaxis().set_major_formatter(matplotlib.ticker.ScalarFormatter())
    ax.set_xlabel("Одновременные запросы (VU)")
    ax.set_ylabel("Throughput, req/s")
    ax.set_title(f"Throughput GET /user/search{title_suffix}")
    ax.grid(True, which="both", alpha=0.3)
    fig.tight_layout()
    fig.savefig(f"{args.out_prefix}_throughput.png", dpi=150)

    print("saved:", f"{args.out_prefix}_latency.png", f"{args.out_prefix}_throughput.png")


if __name__ == "__main__":
    main()
