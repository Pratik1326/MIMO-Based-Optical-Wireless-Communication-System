"""Live simplex transmitter with QR standby and endless binary carousel."""

import logging
from dataclasses import replace
from pathlib import Path
import time

import numpy as np
from PyQt6.QtCore import QSettings, Qt, QTimer, pyqtSignal, pyqtSlot
from PyQt6.QtGui import QImage, QPixmap
from PyQt6.QtWidgets import QDialog, QLabel, QVBoxLayout, QWidget

logger = logging.getLogger(__name__)

from ..data.packetizer import Packetizer
from ..mimo.splitter import SpatialLayout
from ..optical.frame_generator import FrameGenerator, FrameRenderConfig
from ..optical.optical_feedback_detector import OpticalFeedbackDetector
from ..optical.qr_metadata import QRMetadata, create_metadata, generate_qr
from ..optical.state_machine import TransmissionState, TransmissionStateMachine
from ..optical.symbol_encoder import SymbolEncoder, SymbolMode
from ..optical.timing import PrecisionTimer
from .components.display_canvas import DisplayCanvas


class TransmissionPage(QWidget):
    diagnostics_changed = pyqtSignal(object)

    def __init__(self, parent=None) -> None:
        super().__init__(parent)
        self.renderer = DisplayCanvas()
        self.state_machine = TransmissionStateMachine(self)
        self.timer: PrecisionTimer | None = None
        self.feedback_detector = OpticalFeedbackDetector(parent=self)
        self.acknowledgement = "NOT AVAILABLE"
        self.packets = []
        self.metadata: QRMetadata | None = None
        self.qr_image: np.ndarray | None = None
        self.frame_count = 0
        self.current_packet = 0
        self.carousel_cycle = 0
        self._packet_index = 0
        self.display_paced = False
        self._started_at = 0.0
        self._auto_start_timer = QTimer(self)
        self._auto_start_timer.setSingleShot(True)
        self._auto_start_timer.timeout.connect(self._on_auto_start_timeout)
        self._packet_matrix_cache: dict[tuple[int, int], np.ndarray] = {}
        self.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        self._build_layout()
        self.state_machine.state_changed.connect(self._on_state_changed)
        self.state_machine.frame_ready.connect(self._on_frame_emitted, Qt.ConnectionType.QueuedConnection)
        self.renderer.frameSwapped.connect(self._on_frame_swapped)
        self.feedback_detector.pulse_detected.connect(self._on_pulse_detected)

    def _build_layout(self) -> None:
        root = QVBoxLayout(self)
        root.addWidget(QLabel("Live Binary Transmission"))
        root.addWidget(self.renderer, 1)

    def configure(self, path: str | Path, payload_size: int = 256, mimo_mode: str = "2x2", symbol_mode: str = "BINARY", fps: int = 10, compress: bool = True, stride: int | None = None) -> None:
        self.stride = stride
        selected_mode = SymbolMode.parse(symbol_mode)
        source = Path(path)
        data = source.read_bytes()
        self.packets = list(Packetizer(payload_size=payload_size, compress=compress).packetize(data))
        layout = SpatialLayout.parse(mimo_mode)
        self.metadata = create_metadata(source.name, len(data), len(self.packets), layout.value, payload_size, symbol_mode=selected_mode.value)
        self.qr_image = generate_qr(self.metadata, compact=True)
        self.encoder = SymbolEncoder(cell_size=4, padding=1)
        self.frame_generator = FrameGenerator(FrameRenderConfig(layout=layout))
        self.fps = fps
        if self.timer is not None:
            self.timer.stop_clock()
            self.timer = None
        self.mimo_layout = layout
        self.frame_count = 0
        self.current_packet = 0
        self.carousel_cycle = 0
        self._packet_index = 0
        self.acknowledgement = "NOT AVAILABLE"

        # Pre-encode all packet matrices once for zero allocations during playback
        min_bytes = (self.metadata.packet_size + 15) if self.metadata else 0
        self._packet_matrix_cache.clear()
        for p_idx, packet in enumerate(self.packets):
            for eff_ch in range(self.mimo_layout.channel_count):
                p = replace(packet, channel_id=eff_ch)
                self._packet_matrix_cache[(p_idx, eff_ch)] = self.encoder.encode(
                    p.to_bytes(), selected_mode, min_bytes=min_bytes
                )

        self.state_machine.prepare()
        self.renderer.set_frame(self.frame_generator.render_qr(self.qr_image))
        self.renderer.update()

        settings = QSettings("MIMO", "OpticalTransmitter")
        if settings.value("optical_feedback_enabled", True, bool):
            self.feedback_detector.camera_index = int(settings.value("webcam_index", 0))
            self.feedback_detector.start_listening()

        # Auto-start fallback timer: If optical flash is missed or receiver is slow to signal,
        # auto-transition to active transmission after 6s so the system never sits idle
        auto_start_delay = int(settings.value("auto_start_delay_sec", 6))
        if auto_start_delay > 0:
            self._auto_start_timer.start(auto_start_delay * 1000)

        self._publish_diagnostics()

    def _on_auto_start_timeout(self) -> None:
        if self.state_machine.state is TransmissionState.WAITING_FOR_RECEIVER and self.packets:
            logger.info("Auto-start timeout reached -> Beginning transmission automatically")
            self.acknowledgement = "AUTO-START (QR Handshake Window Elapsed)"
            self.start_transmission()

    def keyPressEvent(self, event) -> None:
        if event.key() in (Qt.Key.Key_Space, Qt.Key.Key_Return, Qt.Key.Key_Enter):
            if self.state_machine.state is TransmissionState.WAITING_FOR_RECEIVER:
                self.start_transmission()
            elif self.state_machine.state is TransmissionState.TRANSMITTING:
                if self.state_machine.paused:
                    self.resume_transmission()
                else:
                    self.pause_transmission()
        elif event.key() == Qt.Key.Key_Escape:
            self.stop_transmission()
        else:
            super().keyPressEvent(event)

    def start_transmission(self) -> None:
        if not self.packets:
            return
        self._auto_start_timer.stop()
        if getattr(self, "_qr_dialog", None) is not None:
            try:
                self._qr_dialog.accept()
            except Exception:
                pass
            self._qr_dialog = None
        self.state_machine.start()
        self._started_at = time.perf_counter()
        if not self.display_paced:
            if self.timer is None:
                self.timer = PrecisionTimer(self.fps, self)
                self.timer.frame_tick.connect(self._render_tick)
            self.timer.start_clock()

        settings = QSettings("MIMO", "OpticalTransmitter")
        if settings.value("optical_feedback_enabled", True, bool) and not self.feedback_detector.is_running:
            self.feedback_detector.camera_index = int(settings.value("webcam_index", 0))
            self.feedback_detector.start_listening()

        self._publish_diagnostics()

    def pause_transmission(self) -> None:
        self.state_machine.pause()
        if self.timer:
            self.timer.pause_clock()
        self._publish_diagnostics()

    def resume_transmission(self) -> None:
        self.state_machine.resume()
        if self.timer:
            self.timer.resume_clock()
        self._publish_diagnostics()

    def stop_transmission(self) -> None:
        self._auto_start_timer.stop()
        if self.timer:
            self.timer.stop_clock()
        self.feedback_detector.stop_listening()
        self.state_machine.stop()
        if self.qr_image is not None:
            self.renderer.set_frame(self.frame_generator.render_qr(self.qr_image))
            self.renderer.update()
        self._publish_diagnostics()

    def reset_transmission(self) -> None:
        self.stop_transmission()
        self.acknowledgement = "NOT AVAILABLE"
        self.packets = []
        self.metadata = None
        self.qr_image = None
        self.frame_count = 0
        self.current_packet = 0
        self.carousel_cycle = 0
        self._packet_index = 0
        self._publish_diagnostics()

    @pyqtSlot(str, int, str)
    def _on_pulse_detected(self, pattern: str, count: int, description: str) -> None:
        if pattern == "ACK":
            # REJECT spurious ACK if not actively transmitting
            if self.state_machine.state is not TransmissionState.TRANSMITTING:
                return
            # REJECT premature ACK: Require at least one full carousel cycle and a safe minimum frame count
            min_required_frames = max(len(self.packets), 8) if self.packets else 8
            if self.carousel_cycle < 1 or self.frame_count < min_required_frames:
                logger.info(
                    "Spurious/Premature ACK rejected: carousel_cycle=%d, frame_count=%d (min required frames=%d)",
                    self.carousel_cycle,
                    self.frame_count,
                    min_required_frames,
                )
                return

            self.acknowledgement = "RECEIVED (ACK - Transfer Complete)"
            settings = QSettings("MIMO", "OpticalTransmitter")
            if settings.value("auto_stop_on_ack", True, bool):
                self.stop_transmission()
        elif pattern == "NACK":
            if self.state_machine.state is TransmissionState.WAITING_FOR_RECEIVER:
                # 2-pulse handshake from receiver also unlocks transmission
                self.acknowledgement = "LOCKED (Receiver ready - Starting transmission)"
                self.start_transmission()
            elif self.state_machine.state is TransmissionState.TRANSMITTING:
                self.acknowledgement = f"NACK (Retransmission requested - {count} pulses)"
                if self.packets:
                    settings = QSettings("MIMO", "OpticalTransmitter")
                    stride = settings.value("mimo_stride", 4, type=int)
                    self._packet_index = max(0, self._packet_index - stride * 2)
        elif pattern == "READY":
            if self.state_machine.state == TransmissionState.WAITING_FOR_RECEIVER:
                self.acknowledgement = "LOCKED (Receiver ready - Starting transmission)"
                self.start_transmission()
        self._publish_diagnostics()

    def show_qr(self) -> None:
        if self.qr_image is None:
            return
        if getattr(self, "_qr_dialog", None) is not None:
            try:
                self._qr_dialog.raise_()
                self._qr_dialog.activateWindow()
                return
            except Exception:
                self._qr_dialog = None
        dialog = QDialog(self)
        self._qr_dialog = dialog
        dialog.setWindowTitle("Pre-Flight QR Handshake")
        label = QLabel(dialog)
        image = QImage(self.qr_image.data, self.qr_image.shape[1], self.qr_image.shape[0], self.qr_image.strides[0], QImage.Format.Format_Grayscale8).copy()
        label.setPixmap(QPixmap.fromImage(image))
        layout = QVBoxLayout(dialog)
        layout.addWidget(label)
        dialog.resize(image.width() + 24, image.height() + 24)
        dialog.finished.connect(lambda: setattr(self, "_qr_dialog", None))
        dialog.show()

    def _render_tick(self, index: int, _timestamp: float, _diagnostic: object) -> None:
        if self.state_machine.state is not TransmissionState.TRANSMITTING:
            return
        packet_position = self._packet_index
        packet = self.packets[packet_position]
        self.current_packet = packet_position + 1
        min_bytes = (self.metadata.packet_size + 15) if self.metadata else 0
        stride = 1
        if self.mimo_layout.channel_count == 1:
            mat = self._packet_matrix_cache.get((packet_position, 0))
            if mat is None:
                mat = self.encoder.encode(packet.to_bytes(), SymbolMode.BINARY, min_bytes=min_bytes)
            matrices = {0: mat}
        else:
            matrices = {}
            configured_stride = getattr(self, "stride", None)
            if configured_stride is None:
                configured_stride = 1
            if configured_stride in (1, 2, 4):
                stride = min(configured_stride, self.mimo_layout.channel_count, max(1, len(self.packets)))

            # Spatial diversity rotation: Shift channel mapping by 1 each carousel pass
            # so packets never get permanently trapped in a glare-affected or blurred quadrant!
            channel_rotation = self.carousel_cycle % self.mimo_layout.channel_count

            if stride in (2, 3) and self.mimo_layout.channel_count == 4:
                # 3:1 Dynamic Rolling Pipeline (3 new packets + 1 rotating repeat packet)
                repeat_q = (self.frame_count + self.carousel_cycle) % 4
                prev_pos = (packet_position - 3) % len(self.packets)
                packet_indices = [
                    packet_position % len(self.packets),
                    (packet_position + 1) % len(self.packets),
                    (packet_position + 2) % len(self.packets),
                ]
                active_channels = [c for c in range(4) if c != repeat_q]
                for idx, c in enumerate(active_channels):
                    p_idx = packet_indices[idx]
                    eff_ch = (c + channel_rotation) % 4
                    mat = self._packet_matrix_cache.get((p_idx, eff_ch))
                    if mat is None:
                        p = replace(self.packets[p_idx], channel_id=eff_ch)
                        mat = self.encoder.encode(p.to_bytes(), SymbolMode.BINARY, min_bytes=min_bytes)
                    matrices[eff_ch] = mat
                # Redundant channel (Rotating immediate 2nd chance)
                eff_rep_ch = (repeat_q + channel_rotation) % 4
                mat_rep = self._packet_matrix_cache.get((prev_pos, eff_rep_ch))
                if mat_rep is None:
                    p = replace(self.packets[prev_pos], channel_id=eff_rep_ch)
                    mat_rep = self.encoder.encode(p.to_bytes(), SymbolMode.BINARY, min_bytes=min_bytes)
                matrices[eff_rep_ch] = mat_rep
            else:
                for channel_id in range(self.mimo_layout.channel_count):
                    p_idx = (packet_position + channel_id) % len(self.packets)
                    eff_ch = (channel_id + channel_rotation) % self.mimo_layout.channel_count
                    mat = self._packet_matrix_cache.get((p_idx, eff_ch))
                    if mat is None:
                        p = replace(self.packets[p_idx], channel_id=eff_ch)
                        mat = self.encoder.encode(p.to_bytes(), SymbolMode.BINARY, min_bytes=min_bytes)
                    matrices[eff_ch] = mat

        self.state_machine.emit_frame(self.frame_generator.render(matrices))
        self.frame_count += 1
        self._packet_index += stride
        if self._packet_index >= len(self.packets):
            self._packet_index = 0
            self.carousel_cycle += 1
        self._publish_diagnostics()

    @pyqtSlot(object, int)
    def _on_frame_emitted(self, frame_matrix: np.ndarray, _frame_index: int) -> None:
        self.renderer.set_frame(frame_matrix)
        self.renderer.update()

    def _on_state_changed(self, _old: object, _new: object) -> None:
        self._publish_diagnostics()

    def _on_frame_swapped(self) -> None:
        if self.display_paced and self.state_machine.state is TransmissionState.TRANSMITTING and not self.state_machine.paused:
            self._render_tick(self.state_machine.frame_index, time.perf_counter(), None)

    def _publish_diagnostics(self) -> None:
        metadata = self.metadata
        elapsed = time.perf_counter() - self._started_at if self._started_at else 0.0
        self.diagnostics_changed.emit({
            "state": self.state_machine.state.value,
            "qr_status": "GENERATED" if self.qr_image is not None else "NOT GENERATED",
            "session_id": metadata.session_id if metadata else "--",
            "filename": metadata.filename if metadata else "--",
            "file_size": metadata.file_size if metadata else 0,
            "total_packets": metadata.total_packets if metadata else 0,
            "mimo_mode": metadata.mimo_mode if metadata else "--",
            "symbol_mode": metadata.symbol_mode if metadata else "BINARY",
            "packet_size": metadata.packet_size if metadata else 0,
            "target_fps": self.fps if metadata else 0,
            "current_packet": f"{self.current_packet}/{len(self.packets)}" if self.packets else "0/0",
            "carousel_cycle": self.carousel_cycle,
            "frames_transmitted": self.frame_count,
            "actual_fps": self.frame_count / elapsed if elapsed > 0 else 0.0,
            "acknowledgement": self.acknowledgement,
        })
