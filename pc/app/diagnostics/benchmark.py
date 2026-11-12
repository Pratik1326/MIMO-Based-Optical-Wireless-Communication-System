"""Stage 5 benchmark runner for rendering, throughput, and Reed-Solomon FEC."""

from __future__ import annotations

import json
from pathlib import Path
import platform
import statistics
import time

import numpy as np

from ..mimo.splitter import SpatialLayout
from ..optical.frame_generator import FrameGenerator, FrameRenderConfig
from ..optical.symbol_encoder import SymbolEncoder, SymbolMode
from ..protocol.fec import ReedSolomonFEC

RESULTS_PATH = Path(__file__).resolve().parents[2] / "benchmark_results.json"


def benchmark_frame_generation(layout: SpatialLayout | str, iterations: int = 10) -> dict[str, float | int | str]:
    selected = SpatialLayout.parse(layout)
    if iterations <= 0:
        raise ValueError("iterations must be positive")
    encoder = SymbolEncoder(cell_size=4, padding=1)
    renderer = FrameGenerator(FrameRenderConfig(width=800, height=800, layout=selected))
    matrices = {channel_id: encoder.encode(bytes([channel_id + 1]) * 64, SymbolMode.BINARY) for channel_id in range(selected.channel_count)}
    samples = []
    for _ in range(iterations):
        started = time.perf_counter_ns()
        renderer.render(matrices)
        samples.append((time.perf_counter_ns() - started) / 1_000_000)
    return {
        "layout": selected.value,
        "iterations": iterations,
        "mean_ms_per_frame": round(statistics.fmean(samples), 6),
        "min_ms_per_frame": round(min(samples), 6),
        "max_ms_per_frame": round(max(samples), 6),
    }


def theoretical_throughput(payload_size: int = 256) -> list[dict[str, int | float | str]]:
    if payload_size <= 0:
        raise ValueError("payload_size must be positive")
    results = []
    for layout in SpatialLayout:
        for fps in (30, 60, 120):
            bytes_per_second = layout.channel_count * payload_size * fps
            results.append({
                "layout": layout.value,
                "fps": fps,
                "payload_size_bytes": payload_size,
                "bytes_per_second": bytes_per_second,
                "kilobytes_per_second": round(bytes_per_second / 1024, 3),
            })
    return results


def benchmark_fec(iterations: int = 10, payload_size: int = 223) -> dict[str, float | int]:
    if iterations <= 0 or payload_size <= 0:
        raise ValueError("iterations and payload_size must be positive")
    fec = ReedSolomonFEC.rs_255_223()
    payload = bytes((index % 256 for index in range(payload_size)))
    wall_start = time.perf_counter_ns()
    cpu_start = time.process_time_ns()
    encoded_size = 0
    for _ in range(iterations):
        encoded_size = len(fec.encode(payload))
    wall_ns = time.perf_counter_ns() - wall_start
    cpu_ns = time.process_time_ns() - cpu_start
    return {
        "scheme": "RS(255,223)",
        "iterations": iterations,
        "payload_size_bytes": payload_size,
        "encoded_size_bytes": encoded_size,
        "parity_symbols": fec.parity_symbols,
        "mean_encode_ms": round(wall_ns / iterations / 1_000_000, 6),
        "cpu_utilization_percent": round(min(100.0, cpu_ns / wall_ns * 100.0) if wall_ns else 0.0, 3),
    }


def run_benchmarks(output_path: str | Path = RESULTS_PATH, iterations: int = 10) -> dict:
    metrics = {
        "schema_version": 1,
        "generated_at_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "environment": {"python": platform.python_version(), "platform": platform.platform()},
        "frame_generation": [benchmark_frame_generation(layout, iterations) for layout in SpatialLayout],
        "theoretical_throughput": theoretical_throughput(),
        "fec": benchmark_fec(iterations),
    }
    destination = Path(output_path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(metrics, indent=2) + "\n", encoding="utf-8")
    return metrics


def main() -> int:
    metrics = run_benchmarks()
    print(json.dumps(metrics, indent=2))
    print(f"Wrote {RESULTS_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
