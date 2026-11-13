"""Optical feedback detector using PC webcam to sense receiver flash-pulse signals.

Reverse Optical Link (Smartphone Flashlight -> PC Webcam):
- 1 Pulse  (~150ms): Receiver Locked & Ready (READY) -> Triggers Auto-Start on PC
- 2 Pulses (~130ms each, ~120ms gap): Retransmission Request (NACK)
- 3 Pulses (~140ms each, ~120ms gap): Transfer Complete & Verified (ACK) -> Triggers Auto-Stop on PC

Detection Architecture:
- Calibrated to real-world optical conditions (including off-axis, low-angle, and tilted phone placements).
- Multi-feature detection:
  1. Saturated Pixel Cluster Counter (intensity >= 245 in the smartphone field-of-view ROI).
  2. P98 Upper-Percentile Flare Tracking (captures camera lens diffraction spikes).
  3. Dynamic ambient baseline tracking with hysteresis filtering.
"""

from __future__ import annotations

import logging
import threading
import time
from typing import Optional

import cv2
import numpy as np
from PyQt6.QtCore import QObject, pyqtSignal

logger = logging.getLogger(__name__)


class OpticalFeedbackDetector(QObject):
    """Detects flashlight strobe pulses from the smartphone receiver via the PC webcam."""

    # (pattern: "ACK" | "NACK" | "READY", pulse_count: int, description: str)
    pulse_detected = pyqtSignal(str, int, str)
    # (current_luminance: float, baseline: float, is_flash_on: bool)
    luminance_updated = pyqtSignal(float, float, bool)
    # (status: "RUNNING" | "STOPPED" | "NO_WEBCAM" | "ERROR")
    status_changed = pyqtSignal(str)
    _webcam_lock = threading.Lock()

    def __init__(
        self,
        camera_index: int = 0,
        threshold_delta: float = 14.0,
        min_pulse_duration: float = 0.04,
        max_pulse_duration: float = 0.65,
        burst_timeout: float = 0.32,
        parent: Optional[QObject] = None,
    ) -> None:
        super().__init__(parent)
        self.camera_index = camera_index
        self.threshold_delta = threshold_delta
        self.min_pulse_duration = min_pulse_duration
        self.max_pulse_duration = max_pulse_duration
        self.burst_timeout = burst_timeout

        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._stop_event = threading.Event()
        self._last_ack_time = 0.0

    @property
    def is_running(self) -> bool:
        return self._running

    def start_listening(self) -> bool:
        """Starts the background webcam listener thread."""
        if self._running:
            return True

        self._stop_event.clear()
        self._thread = threading.Thread(target=self._capture_loop, daemon=True, name="OpticalFeedbackWorker")
        self._running = True
        self._thread.start()
        return True

    def stop_listening(self) -> None:
        """Stops the background webcam listener."""
        if not self._running:
            return

        self._stop_event.set()
        self._running = False
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=1.5)
        self._thread = None
        self.status_changed.emit("STOPPED")

    def process_features_sample(
        self,
        sat_count: int,
        max_val: int,
        p98: float,
        mean_val: float,
        now: float,
        state: dict,
    ) -> Optional[tuple[str, int, str]]:
        """Multi-feature pulse processor supporting off-axis and tilted flash angles.

        State dictionary tracks:
        - baseline_p98: float
        - baseline_mean: float
        - baseline_sat: float
        - is_high: bool
        - pulse_start: float
        - burst_count: int
        - last_pulse_end: float
        """
        baseline_p98 = state.get("baseline_p98", 150.0)
        baseline_mean = state.get("baseline_mean", 90.0)
        baseline_sat = state.get("baseline_sat", 0.0)
        is_high = state.get("is_high", False)
        pulse_start = state.get("pulse_start", 0.0)
        burst_count = state.get("burst_count", 0)
        last_pulse_end = state.get("last_pulse_end", 0.0)

        delta_mean = mean_val - baseline_mean
        delta_p98 = p98 - baseline_p98
        delta_sat = sat_count - baseline_sat

        # Dynamic ambient baseline update when flash is OFF:
        # Continuously adapt to slow changes in ambient room illumination
        if not is_high:
            if abs(delta_mean) < (self.threshold_delta * 0.65) and abs(delta_p98) < 14.0:
                baseline_p98 = 0.94 * baseline_p98 + 0.06 * p98
                baseline_mean = 0.94 * baseline_mean + 0.06 * mean_val
                baseline_sat = 0.94 * baseline_sat + 0.06 * sat_count
                state["baseline_p98"] = baseline_p98
                state["baseline_mean"] = baseline_mean
                state["baseline_sat"] = baseline_sat

        detected_event: Optional[tuple[str, int, str]] = None

        # Flash ON condition:
        # Requires a genuine differential surge above the ambient baseline!
        # Either:
        # 1. Saturated cluster surge (delta_sat >= 20 and max_val >= 245)
        # 2. Significant flare jump (delta_p98 >= 14.0 and max_val >= 245 and sat_count >= (baseline_sat + 8))
        # 3. Overall luminance surge (delta_mean >= self.threshold_delta and delta_p98 >= 10.0 and max_val >= 240)
        is_flash_now = (
            (delta_sat >= 20 and max_val >= 245)
            or (delta_p98 >= 14.0 and max_val >= 245 and sat_count >= (baseline_sat + 8))
            or (delta_mean >= self.threshold_delta and delta_p98 >= 10.0 and max_val >= 240)
        )

        # Rising edge: Flash turns ON
        if is_flash_now and not is_high:
            is_high = True
            pulse_start = now
            state["is_high"] = True
            state["pulse_start"] = pulse_start

        # Continuous light rejection (Room light / Lamp left on):
        # A smartphone flash never stays on continuously > 1.25s.
        # If light stays high > 1.25s, it is ambient illumination! CANCEL IT!
        if is_high and (now - pulse_start) > 1.25:
            is_high = False
            state["is_high"] = False
            state["burst_count"] = 0
            state.pop("ack_fired_during_strobe", None)
            state["baseline_mean"] = mean_val
            state["baseline_p98"] = p98
            state["baseline_sat"] = sat_count
            logger.info("Continuous illumination detected (>1.25s) -> Rejecting ambient light, adapting baseline.")
            return None

        # Sustained strobe check: A continuous bright flash >= 0.65s (up to 1.25s)
        # Only fires if accompanied by a strong saturated point source (delta_sat >= 20 or sat_count >= baseline_sat + 15)
        if (
            is_high
            and (now - pulse_start) >= 0.65
            and (delta_sat >= 20 or sat_count >= (baseline_sat + 15))
            and max_val >= 245
            and not state.get("ack_fired_during_strobe", False)
        ):
            state["ack_fired_during_strobe"] = True
            state["burst_count"] = 0
            detected_event = ("ACK", 3, "Transfer Verified & Complete (Sustained Strobe >= 650ms)")
            logger.info("Sustained flashlight strobe detected (duration: %.0f ms) -> Immediate ACK", (now - pulse_start) * 1000)

        # Falling edge: Flash turns OFF
        is_flash_off = (
            not is_flash_now
            and (delta_mean < self.threshold_delta * 0.50)
            and (delta_p98 < 10.0)
            and (delta_sat < 12)
        )
        if is_high and is_flash_off:
            is_high = False
            duration = now - pulse_start
            state["is_high"] = False
            fired = state.pop("ack_fired_during_strobe", False)

            if not fired:
                if 0.55 <= duration <= 1.20:
                    detected_event = ("ACK", 3, "Transfer Verified & Complete (Long Strobe >= 550ms)")
                    logger.info("Long flashlight pulse ended (duration: %.0f ms) -> ACK", duration * 1000)
                elif self.min_pulse_duration <= duration <= self.max_pulse_duration:
                    burst_count += 1
                    last_pulse_end = now
                    state["burst_count"] = burst_count
                    state["last_pulse_end"] = last_pulse_end
                    logger.info(
                        "Detected valid flash pulse #%d (duration: %.0f ms)",
                        burst_count,
                        duration * 1000,
                    )

        # Burst completion: quiet window elapsed after last pulse
        if burst_count > 0 and (now - last_pulse_end) >= self.burst_timeout:
            count = burst_count
            state["burst_count"] = 0
            state["last_pulse_end"] = 0.0

            if count == 1:
                detected_event = ("READY", 1, "Receiver Acquired Grid (1 pulse)")
            elif count == 2:
                detected_event = ("NACK", 2, "Retransmission Requested (2 pulses)")
            elif count >= 3:
                detected_event = ("ACK", count, "Transfer Verified & Complete (3 pulses)")

            logger.info("Flash burst completed: %s (count=%d)", detected_event[0], count)

        return detected_event

    def process_luminance_sample(
        self,
        lum: float,
        now: float,
        state: dict,
    ) -> Optional[tuple[str, int, str]]:
        """Compatible 1D luminance interface for synthetic tests and backwards compatibility."""
        baseline = state.get("baseline", lum)
        is_high = state.get("is_high", False)
        pulse_start = state.get("pulse_start", 0.0)
        burst_count = state.get("burst_count", 0)
        last_pulse_end = state.get("last_pulse_end", 0.0)

        dyn_thresh = max(self.threshold_delta, baseline * 0.20)
        delta = lum - baseline

        if not is_high and delta < dyn_thresh:
            baseline = 0.94 * baseline + 0.06 * lum
            state["baseline"] = baseline

        detected_event: Optional[tuple[str, int, str]] = None

        if delta >= dyn_thresh and not is_high:
            is_high = True
            pulse_start = now
            state["is_high"] = True
            state["pulse_start"] = pulse_start

        # Sustained strobe check
        if is_high and (now - pulse_start) >= 0.65 and not state.get("ack_fired_during_strobe", False):
            state["ack_fired_during_strobe"] = True
            state["burst_count"] = 0
            detected_event = ("ACK", 3, "Transfer Verified & Complete (Sustained Strobe >= 650ms)")

        elif delta < (dyn_thresh * 0.60) and is_high:
            is_high = False
            duration = now - pulse_start
            state["is_high"] = False
            fired = state.pop("ack_fired_during_strobe", False)

            if not fired:
                if duration >= 0.55:
                    detected_event = ("ACK", 3, "Transfer Verified & Complete (Long Strobe >= 550ms)")
                elif self.min_pulse_duration <= duration <= self.max_pulse_duration:
                    burst_count += 1
                    last_pulse_end = now
                    state["burst_count"] = burst_count
                    state["last_pulse_end"] = last_pulse_end

        if burst_count > 0 and (now - last_pulse_end) >= self.burst_timeout:
            count = burst_count
            state["burst_count"] = 0
            state["last_pulse_end"] = 0.0

            if count == 1:
                detected_event = ("READY", 1, "Receiver Acquired Grid (1 pulse)")
            elif count == 2:
                detected_event = ("NACK", 2, "Retransmission Requested (2 pulses)")
            elif count >= 3:
                detected_event = ("ACK", count, "Transfer Verified & Complete (3 pulses)")

        return detected_event

    def _capture_loop(self) -> None:
        """Captures frames from webcam and runs angle-proof flash detection."""
        if self._stop_event.is_set():
            self._running = False
            return
        cap = None
        try:
            # Use class-level lock to prevent simultaneous DirectShow access on Windows
            with OpticalFeedbackDetector._webcam_lock:
                if self._stop_event.is_set():
                    return
                cap = cv2.VideoCapture(self.camera_index, cv2.CAP_DSHOW)
                if not cap.isOpened():
                    cap = cv2.VideoCapture(self.camera_index)

            if not cap.isOpened():
                logger.warning("Optical feedback webcam (index %d) could not be opened.", self.camera_index)
                self.status_changed.emit("NO_WEBCAM")
                self._running = False
                return

            self.status_changed.emit("RUNNING")

            state = {
                "baseline_p98": 150.0,
                "baseline_mean": 90.0,
                "is_high": False,
                "pulse_start": 0.0,
                "burst_count": 0,
                "last_pulse_end": 0.0,
            }
            last_gui_update = 0.0

            # Initial baseline warmup: 15 frames to adapt to room lighting
            warmup_means, warmup_p98s, warmup_sats = [], [], []
            for _ in range(15):
                ret, frame = cap.read()
                if ret and frame is not None:
                    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
                    small = cv2.resize(gray, (160, 90))
                    roi = small[int(90 * 0.20):, :]
                    warmup_means.append(float(np.mean(roi)))
                    warmup_p98s.append(float(np.percentile(roi, 98)))
                    warmup_sats.append(int(np.sum(roi >= 245)))
                    time.sleep(0.01)

            if warmup_means:
                state["baseline_mean"] = float(np.mean(warmup_means))
                state["baseline_p98"] = float(np.mean(warmup_p98s))
                state["baseline_sat"] = float(np.mean(warmup_sats))

            while not self._stop_event.is_set():
                ret, frame = cap.read()
                if not ret or frame is None:
                    time.sleep(0.01)
                    continue

                now = time.perf_counter()
                gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
                small = cv2.resize(gray, (160, 90))

                # Focus on the lower 80% ROI where the smartphone is held in front of the screen
                roi = small[int(90 * 0.20):, :]

                sat_count = int(np.sum(roi >= 245))
                max_val = int(np.max(roi))
                p98 = float(np.percentile(roi, 98))
                mean_val = float(np.mean(roi))

                event = self.process_features_sample(sat_count, max_val, p98, mean_val, now, state)

                if event:
                    pattern, count, desc = event
                    # Rate-limit duplicate ACK events
                    if pattern != "ACK" or (now - self._last_ack_time) > 2.0:
                        if pattern == "ACK":
                            self._last_ack_time = now
                        self.pulse_detected.emit(pattern, count, desc)

                # Throttle GUI telemetry updates (~20 Hz)
                if now - last_gui_update >= 0.05:
                    try:
                        self.luminance_updated.emit(mean_val, state["baseline_mean"], state["is_high"])
                    except RuntimeError:
                        break
                    last_gui_update = now

        except RuntimeError as e:
            if "deleted" in str(e):
                pass
            else:
                logger.exception("RuntimeError in optical feedback capture loop: %s", e)
        except Exception as e:
            logger.exception("Error in optical feedback capture loop: %s", e)
            try:
                self.status_changed.emit("ERROR")
            except RuntimeError:
                pass
        finally:
            if cap is not None and cap.isOpened():
                cap.release()
            self._running = False
            try:
                self.status_changed.emit("STOPPED")
            except RuntimeError:
                pass
