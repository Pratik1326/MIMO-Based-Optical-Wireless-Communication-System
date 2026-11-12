"""Thread-safe two-phase simplex transmitter state and frame sequencing."""

from __future__ import annotations

from enum import Enum
import threading

import numpy as np
from PyQt6.QtCore import QObject, pyqtSignal


class TransmitterState(str, Enum):
    WAITING_FOR_RECEIVER = "WAITING_FOR_RECEIVER"
    TRANSMITTING = "TRANSMITTING"


# Backward-compatible import name for existing callers.
TransmissionState = TransmitterState


class TransmissionStateMachine(QObject):
    """QR standby followed by an endless carousel; pause is not a protocol state."""

    state_changed = pyqtSignal(object, object)
    frame_ready = pyqtSignal(object, int)

    def __init__(self, parent=None) -> None:
        super().__init__(parent)
        self._state = TransmitterState.WAITING_FOR_RECEIVER
        self._paused = False
        self._lock = threading.RLock()
        self._frame_index = 0

    @property
    def state(self) -> TransmitterState:
        with self._lock:
            return self._state

    @property
    def paused(self) -> bool:
        with self._lock:
            return self._paused

    @property
    def frame_index(self) -> int:
        with self._lock:
            return self._frame_index

    def prepare(self) -> TransmitterState:
        with self._lock:
            self._paused = False
            self._frame_index = 0
            return self._set_state_locked(TransmitterState.WAITING_FOR_RECEIVER)

    def start(self) -> TransmitterState:
        with self._lock:
            if self._state is not TransmitterState.WAITING_FOR_RECEIVER:
                raise RuntimeError("transmission must be in QR standby before starting")
            self._paused = False
            return self._set_state_locked(TransmitterState.TRANSMITTING)

    def pause(self) -> TransmitterState:
        with self._lock:
            if self._state is TransmitterState.TRANSMITTING:
                self._paused = True
            return self._state

    def resume(self) -> TransmitterState:
        with self._lock:
            if self._state is TransmitterState.TRANSMITTING:
                self._paused = False
            return self._state

    def stop(self) -> TransmitterState:
        with self._lock:
            self._paused = False
            self._frame_index = 0
            return self._set_state_locked(TransmitterState.WAITING_FOR_RECEIVER)

    reset = stop

    def emit_frame(self, frame: np.ndarray) -> bool:
        with self._lock:
            if self._state is not TransmitterState.TRANSMITTING or self._paused:
                return False
            frame_index = self._frame_index
            self._frame_index += 1
        self.frame_ready.emit(frame, frame_index)
        return True

    def _set_state_locked(self, new_state: TransmitterState) -> TransmitterState:
        old_state = self._state
        self._state = new_state
        if old_state is not new_state:
            self.state_changed.emit(old_state, new_state)
        return new_state
