#!/usr/bin/env python3
"""Группированные графики сравнения master-only vs master+2-slaves из read_results.json."""
import json
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

PATH = sys.argv[1] if len(sys.argv) > 1 else "report/replication/read_results.json"
PREFIX = sys.argv[2] if len(sys.argv) > 2 else "report/replication/read"

with open(PATH) as f:
    data = json.load(f)

labels = ["master-only", "master+2-slaves"]
conc = sorted({r["concurrency"] for r in data})
x = np.arange(len(conc))
width = 0.35


def series(label, key):
    by_c = {r["concurrency"]: r for r in data if r["label"] == label}
    return [by_c[c][key] if key != "latency_ms" else by_c[c]["latency_ms"]["p95"] for c in conc]


for metric, fname, ylabel in (
    ("throughput_rps", f"{PREFIX}_throughput.png", "Throughput, req/s"),
    ("latency_ms", f"{PREFIX}_latency_p95.png", "Latency p95, мс"),
):
    fig, ax = plt.subplots(figsize=(8, 5))
    for i, label in enumerate(labels):
        ax.bar(x + (i - 0.5) * width, series(label, metric), width, label=label)
    ax.set_xticks(x)
    ax.set_xticklabels([str(c) for c in conc])
    ax.set_xlabel("Конкурентность (потоков)")
    ax.set_ylabel(ylabel)
    ax.set_title("Чтение: GET /user/get/{id} + GET /user/search (mix 50/50)")
    ax.grid(True, axis="y", alpha=0.3)
    ax.legend()
    fig.tight_layout()
    fig.savefig(fname, dpi=150)
    print("saved:", fname)
