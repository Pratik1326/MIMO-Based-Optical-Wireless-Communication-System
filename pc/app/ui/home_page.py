"""Home page for connection and optical-mode overview."""

from PyQt6.QtCore import pyqtSignal
from PyQt6.QtWidgets import (
    QFormLayout,
    QGroupBox,
    QHBoxLayout,
    QLabel,
    QPushButton,
    QVBoxLayout,
    QWidget,
)


class HomePage(QWidget):
    start_send_requested = pyqtSignal()
    open_diagnostics_requested = pyqtSignal()

    def __init__(self, parent=None) -> None:
        super().__init__(parent)
        self._build_ui()

    def _build_ui(self) -> None:
        root = QVBoxLayout(self)

        # Header Title
        title = QLabel("MIMO Optical Wireless Communication")
        title.setObjectName("pageTitle")
        title.setStyleSheet("font-size: 20px; font-weight: bold;")
        root.addWidget(title)

        subtitle = QLabel("High-Throughput Simplex Screen-to-Camera Optical Link")
        subtitle.setStyleSheet("color: #888888; font-size: 13px;")
        root.addWidget(subtitle)
        root.addSpacing(10)

        # 1. Quick Action Banner
        action_group = QGroupBox("Quick Actions")
        action_layout = QHBoxLayout(action_group)
        self.send_button = QPushButton("Select File & Transmit")
        self.send_button.setStyleSheet("font-weight: bold; padding: 10px 18px; font-size: 14px;")
        self.send_button.clicked.connect(self.start_send_requested.emit)
        action_layout.addWidget(self.send_button)

        self.diag_button = QPushButton("View System Diagnostics")
        self.diag_button.setStyleSheet("padding: 10px 18px; font-size: 13px;")
        self.diag_button.clicked.connect(self.open_diagnostics_requested.emit)
        action_layout.addWidget(self.diag_button)
        root.addWidget(action_group)

        # 2. Live Session & Configuration Overview
        session_group = QGroupBox("Transmission Overview")
        session_layout = QFormLayout(session_group)

        self.status_label = QLabel("Online & Ready")
        self.status_label.setStyleSheet("color: #4CAF50; font-weight: bold;")
        session_layout.addRow("Transmitter Status:", self.status_label)

        self.last_file_label = QLabel("None (waiting for file selection)")
        session_layout.addRow("Current File:", self.last_file_label)

        self.mimo_summary_label = QLabel("2x2 MIMO | 5 FPS | 64B Payload")
        session_layout.addRow("Active Configuration:", self.mimo_summary_label)

        self.carousel_stats_label = QLabel("0 frames sent")
        session_layout.addRow("Carousel Stats:", self.carousel_stats_label)

        root.addWidget(session_group)

        # 3. Transmission Workflow Guide
        guide_group = QGroupBox("Optical Simplex Workflow")
        guide_layout = QVBoxLayout(guide_group)
        guide_layout.addWidget(QLabel("1. Select your file in the 'Send' tab and choose MIMO parameters (1x1, 2x2, or 4x4)."))
        guide_layout.addWidget(QLabel("2. Click 'Prepare Transmission' to display the pre-flight QR handshake."))
        guide_layout.addWidget(QLabel("3. Point your Android phone camera at the QR code to capture metadata."))
        guide_layout.addWidget(QLabel("4. Tap 'START' on your phone and 'START TRANSMISSION' on PC to stream binary symbols."))
        guide_layout.addWidget(QLabel("5. When transfer completes, the received file appears instantly on your Android Home Screen."))
        root.addWidget(guide_group)

        root.addStretch()

    def update_session_summary(self, filename: str, mimo_mode: str, fps: int, payload_size: int, frames: int, state: str) -> None:
        self.last_file_label.setText(filename if filename != "--" else "None")
        self.mimo_summary_label.setText(f"{mimo_mode} MIMO | {fps} FPS | {payload_size}B Payload")
        self.carousel_stats_label.setText(f"{frames} frames transmitted")
        if state == "TRANSMITTING":
            self.status_label.setText("Transmitting Live...")
            self.status_label.setStyleSheet("color: #2196F3; font-weight: bold;")
        elif state == "PAUSED":
            self.status_label.setText("Paused")
            self.status_label.setStyleSheet("color: #FF9800; font-weight: bold;")
        else:
            self.status_label.setText("Online & Ready")
            self.status_label.setStyleSheet("color: #4CAF50; font-weight: bold;")
