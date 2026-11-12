"""High-precision Qt frame timing with drift compensation."""

from __future__ import annotations

from dataclasses import dataclass
import threading
import time

from PyQt6.QtCore import QThread, pyqtSignal


@dataclass(frozen=True, slots=True)
class TimingDiagnostics:
    frame_index: int
    scheduled_ns: int
    actual_ns: int
    lateness_ns: int
    interval_ns: int


def frame_interval_ns(fps: int | float) -> int:
    try:
        val = float(fps)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"Invalid fps: {fps}") from exc
    if val <= 0 or val > 240:
        raise ValueError("fps must be between 1 and 240")
    return round(1_000_000_000 / val)


def simulate_deadlines(fps: int, tick_count: int, start_ns: int = 0) -> list[int]:
    """Return ideal compensated deadlines without sleeping, useful for tests."""
    if tick_count < 0:
        raise ValueError("tick_count cannot be negative")
    interval = frame_interval_ns(fps)
    return [start_ns + index * interval for index in range(tick_count)]


class PrecisionTimer(QThread):
    """QThread-based frame clock using absolute perf-counter deadlines."""

    frame_tick = pyqtSignal(int, float, object)
    timing_diagnostics = pyqtSignal(object)

    def __init__(self, fps: int = 60, parent=None) -> None:
        super().__init__(parent)
        self.fps = fps
        self.interval_ns = frame_interval_ns(fps)
        self._stop_event = threading.Event()
        self._pause_event = threading.Event()
        self._pause_event.set()
        self._command_lock = threading.RLock()

    def start_clock(self) -> None:
        with self._command_lock:
            if not self.isRunning():
                self._stop_event.clear()
                self._pause_event.set()
                self.start()

    def pause_clock(self) -> None:
        self._pause_event.clear()

    def resume_clock(self) -> None:
        self._pause_event.set()

    def stop_clock(self) -> None:
        self._stop_event.set()
        self._pause_event.set()
        if self.isRunning() and QThread.currentThread() is not self:
            self.wait(2000)

    def run(self) -> None:
        start_ns = time.perf_counter_ns()
        next_deadline = start_ns
        previous_actual = start_ns
        frame_index = 0
        while not self._stop_event.is_set():
            while not self._pause_event.wait(0.001):
                if self._stop_event.is_set():
                    return
            next_deadline += self.interval_ns if frame_index else 0
            self._wait_until(next_deadline)
            if self._stop_event.is_set():
                return
            actual = time.perf_counter_ns()
            diagnostic = TimingDiagnostics(
                frame_index,
                next_deadline,
                actual,
                actual - next_deadline,
                actual - previous_actual,
            )
            self.frame_tick.emit(frame_index, actual / 1_000_000_000, diagnostic)
            self.timing_diagnostics.emit(diagnostic)
            previous_actual = actual
            frame_index += 1
            if actual > next_deadline + self.interval_ns * 4:
                next_deadline = actual

    @staticmethod
    def _wait_until(deadline_ns: int) -> None:
        while True:
            remaining_ns = deadline_ns - time.perf_counter_ns()
            if remaining_ns <= 0:
                return
            if remaining_ns > 2_000_000:
                time.sleep((remaining_ns - 1_000_000) / 1_000_000_000)
            else:
                time.perf_counter_ns()
