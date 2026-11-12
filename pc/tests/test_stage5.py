import json

from pc.app.diagnostics.benchmark import (
    benchmark_fec,
    benchmark_frame_generation,
    run_benchmarks,
    theoretical_throughput,
)


def test_frame_benchmark_covers_all_layouts(tmp_path) -> None:
    results = [benchmark_frame_generation(layout, iterations=1) for layout in ("1x1", "2x2", "4x4")]

    assert [result["layout"] for result in results] == ["1x1", "2x2", "4x4"]
    assert all(result["mean_ms_per_frame"] >= 0 for result in results)


def test_theoretical_throughput_covers_layouts_and_fps() -> None:
    results = theoretical_throughput(payload_size=256)

    assert len(results) == 9
    assert results[0]["bytes_per_second"] == 1 * 256 * 30
    assert results[-1]["bytes_per_second"] == 16 * 256 * 120
    assert results[-1]["kilobytes_per_second"] == 480.0


def test_fec_benchmark_reports_overhead_and_cpu() -> None:
    result = benchmark_fec(iterations=1)

    assert result["scheme"] == "RS(255,223)"
    assert result["encoded_size_bytes"] == 255
    assert result["parity_symbols"] == 32
    assert 0 <= result["cpu_utilization_percent"] <= 100


def test_benchmark_runner_writes_json(tmp_path) -> None:
    destination = tmp_path / "benchmark_results.json"
    metrics = run_benchmarks(destination, iterations=1)
    loaded = json.loads(destination.read_text(encoding="utf-8"))

    assert loaded == metrics
    assert len(loaded["frame_generation"]) == 3
    assert len(loaded["theoretical_throughput"]) == 9
    assert loaded["fec"]["scheme"] == "RS(255,223)"
