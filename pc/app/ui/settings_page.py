"""Transmission and tracking settings page."""

from PyQt6.QtCore import QSettings, pyqtSignal
from PyQt6.QtWidgets import (
    QCheckBox,
    QComboBox,
    QFormLayout,
    QGroupBox,
    QLabel,
    QPushButton,
    QSpinBox,
    QVBoxLayout,
    QWidget,
)


class SettingsPage(QWidget):
    settings_changed = pyqtSignal()

    def __init__(self, parent=None) -> None:
        super().__init__(parent)
        self.settings = QSettings("MIMO", "OpticalTransmitter")
        self._build_ui()
        self.load_settings()

    def _build_ui(self) -> None:
        root = QVBoxLayout(self)

        # 1. Optical Protocol Defaults
        proto_group = QGroupBox("Default Transmission Parameters")
        proto_layout = QFormLayout(proto_group)

        self.default_mimo_mode = QComboBox()
        self.default_mimo_mode.addItems(["1x1", "2x2", "4x4"])
        proto_layout.addRow("Default MIMO mode", self.default_mimo_mode)

        self.default_fps = QComboBox()
        self.default_fps.addItems(["1", "2", "4", "5", "10", "15", "20"])
        proto_layout.addRow("Default frame rate (FPS)", self.default_fps)

        self.mimo_stride = QComboBox()
        self.mimo_stride.addItem("4 (Pure Throughput - 4x Parallel)", 4)
        self.mimo_stride.addItem("2 (Space-Time Redundancy)", 2)
        self.mimo_stride.addItem("1 (Sliding Carousel)", 1)
        proto_layout.addRow("MIMO transmission stride", self.mimo_stride)

        self.default_payload_size = QSpinBox()
        self.default_payload_size.setRange(64, 512)
        self.default_payload_size.setSingleStep(64)
        proto_layout.addRow("Default payload size (bytes)", self.default_payload_size)

        self.color_mapping = QComboBox()
        self.color_mapping.addItems(["BGR", "RGB"])
        proto_layout.addRow("Color channel mapping", self.color_mapping)

        self.parity_ratio = QSpinBox()
        self.parity_ratio.setRange(1, 254)
        proto_layout.addRow("FEC parity symbols", self.parity_ratio)

        root.addWidget(proto_group)

        # 2. Tracking & Display Anchors
        anchor_group = QGroupBox("Tracking & Display Anchors")
        anchor_layout = QFormLayout(anchor_group)

        self.aruco_enabled = QCheckBox("Enable ArUco tracking corner markers")
        anchor_layout.addRow(self.aruco_enabled)

        self.safe_zone = QCheckBox("Add 10% black safe zone margin (prevents screen edge cutoff)")
        anchor_layout.addRow(self.safe_zone)

        root.addWidget(anchor_group)

        # 3. Reverse Optical Feedback (Receiver Flashlight Link)
        feedback_group = QGroupBox("Reverse Optical Link (Smartphone Flashlight Feedback)")
        feedback_layout = QFormLayout(feedback_group)

        self.optical_feedback_enabled = QCheckBox("Enable reverse optical feedback (webcam flash pulse detection)")
        feedback_layout.addRow(self.optical_feedback_enabled)

        self.webcam_index = QSpinBox()
        self.webcam_index.setRange(0, 10)
        feedback_layout.addRow("Webcam device index", self.webcam_index)

        self.auto_stop_on_ack = QCheckBox("Automatically stop transmission upon Complete ACK (3 pulses)")
        feedback_layout.addRow(self.auto_stop_on_ack)

        root.addWidget(feedback_group)

        # 4. Actions
        self.save_button = QPushButton("Save Settings")
        self.save_button.clicked.connect(self.save_settings)
        root.addWidget(self.save_button)

        self.status_label = QLabel("")
        root.addWidget(self.status_label)

        root.addStretch()

    def load_settings(self) -> None:
        mimo = self.settings.value("default_mimo_mode", "1x1", str)
        self.default_mimo_mode.setCurrentText(mimo)

        fps = self.settings.value("default_fps", "10", str)
        self.default_fps.setCurrentText(fps)

        stride = int(self.settings.value("mimo_stride", 4))
        for idx in range(self.mimo_stride.count()):
            if self.mimo_stride.itemData(idx) == stride:
                self.mimo_stride.setCurrentIndex(idx)
                break

        payload = int(self.settings.value("default_payload_size", 256))
        self.default_payload_size.setValue(payload)

        color = self.settings.value("color_mapping", "BGR", str)
        self.color_mapping.setCurrentText(color)

        parity = int(self.settings.value("parity_ratio", 32))
        self.parity_ratio.setValue(parity)

        aruco = self.settings.value("aruco_enabled", True, bool)
        self.aruco_enabled.setChecked(aruco)

        safe = self.settings.value("safe_zone", True, bool)
        self.safe_zone.setChecked(safe)

        fb_enabled = self.settings.value("optical_feedback_enabled", True, bool)
        self.optical_feedback_enabled.setChecked(fb_enabled)

        cam_idx = int(self.settings.value("webcam_index", 0))
        self.webcam_index.setValue(cam_idx)

        auto_stop = self.settings.value("auto_stop_on_ack", True, bool)
        self.auto_stop_on_ack.setChecked(auto_stop)

    def save_settings(self) -> None:
        self.settings.setValue("default_mimo_mode", self.default_mimo_mode.currentText())
        self.settings.setValue("default_fps", self.default_fps.currentText())
        self.settings.setValue("mimo_stride", self.mimo_stride.currentData())
        self.settings.setValue("default_payload_size", self.default_payload_size.value())
        self.settings.setValue("color_mapping", self.color_mapping.currentText())
        self.settings.setValue("parity_ratio", self.parity_ratio.value())
        self.settings.setValue("aruco_enabled", self.aruco_enabled.isChecked())
        self.settings.setValue("safe_zone", self.safe_zone.isChecked())
        self.settings.setValue("optical_feedback_enabled", self.optical_feedback_enabled.isChecked())
        self.settings.setValue("webcam_index", self.webcam_index.value())
        self.settings.setValue("auto_stop_on_ack", self.auto_stop_on_ack.isChecked())
        self.status_label.setText("Settings saved successfully.")
        self.settings_changed.emit()
