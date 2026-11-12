"""Main window with file transmission controls and transmitter diagnostics."""

from PyQt6.QtGui import QColor, QPalette
from PyQt6.QtWidgets import QApplication, QFormLayout, QGroupBox, QHBoxLayout, QLabel, QMainWindow, QPushButton, QStackedWidget, QTabBar, QVBoxLayout, QWidget

from .diagnostics_page import DiagnosticsPage
from .home_page import HomePage
from .send_page import SendPage
from .settings_page import SettingsPage
from .transmission_page import TransmissionPage


class MainWindow(QMainWindow):
    def __init__(self, parent=None) -> None:
        super().__init__(parent)
        self.setWindowTitle("MIMO Optical Communication")
        self.resize(1440, 860)
        self._apply_theme()
        self.tabs = QTabBar()
        self.tabs.addTab("Home"); self.tabs.addTab("Send"); self.tabs.addTab("Transmission"); self.tabs.addTab("Settings"); self.tabs.addTab("Diagnostics")
        self.tabs.setExpanding(False); self.tabs.setDrawBase(False)
        self.setMenuWidget(self.tabs)
        self.pages = QStackedWidget()
        self.home_page = HomePage(); self.send_page = SendPage(); self.transmission_page = TransmissionPage()
        self.settings_page = SettingsPage(); self.diagnostics_page = DiagnosticsPage()
        for page in (self.home_page, self.send_page, self.transmission_page, self.settings_page, self.diagnostics_page): self.pages.addWidget(page)
        self.status_panel = self._build_status_panel()
        central = QWidget()
        body = QHBoxLayout(central)
        body.addWidget(self.pages, 4)
        body.addWidget(self.status_panel, 1)
        self.setCentralWidget(central)
        self.tabs.currentChanged.connect(self.pages.setCurrentIndex)
        self.home_page.start_send_requested.connect(lambda: self.tabs.setCurrentIndex(1))
        self.home_page.open_diagnostics_requested.connect(lambda: self.tabs.setCurrentIndex(4))
        self.settings_page.settings_changed.connect(self.send_page.reload_defaults)
        self.send_page.start_button.clicked.connect(self.prepare_transmission)
        self.transmission_page.diagnostics_changed.connect(self._update_diagnostics)

    def _build_status_panel(self) -> QGroupBox:
        panel = QGroupBox("Transmitter Status")
        root = QVBoxLayout(panel)
        form = QFormLayout()
        self._diagnostic_labels = {}
        fields = (
            ("state", "Current State"), ("qr_status", "QR Status"), ("session_id", "Session ID"),
            ("filename", "Filename"), ("file_size", "File Size"), ("total_packets", "Total Packets"),
            ("mimo_mode", "MIMO Mode"), ("symbol_mode", "Symbol Mode"), ("packet_size", "Packet Size"), ("target_fps", "Target FPS"),
            ("current_packet", "Current Packet"), ("carousel_cycle", "Carousel Cycle"),
            ("frames_transmitted", "Frames Transmitted"), ("actual_fps", "Actual FPS"),
        )
        for key, title in fields:
            value = QLabel("--")
            value.setWordWrap(True)
            self._diagnostic_labels[key] = value
            form.addRow(title, value)
        root.addLayout(form)
        self.acknowledgement_label = QLabel("Receiver acknowledgement: NOT AVAILABLE")
        self.acknowledgement_label.setWordWrap(True)
        root.addWidget(self.acknowledgement_label)
        controls = QVBoxLayout()
        self.show_qr_button = QPushButton("SHOW QR")
        self.start_button = QPushButton("START TRANSMISSION")
        self.pause_button = QPushButton("PAUSE")
        self.stop_button = QPushButton("STOP")
        self.reset_button = QPushButton("RESET TRANSMISSION")
        self.start_button.setEnabled(False); self.pause_button.setEnabled(False); self.stop_button.setEnabled(False); self.reset_button.setEnabled(False)
        for button in (self.show_qr_button, self.start_button, self.pause_button, self.stop_button, self.reset_button):
            controls.addWidget(button)
        root.addLayout(controls)
        self.show_qr_button.clicked.connect(self.transmission_page.show_qr)
        self.start_button.clicked.connect(self.transmission_page.start_transmission)
        self.pause_button.clicked.connect(self._toggle_pause)
        self.stop_button.clicked.connect(self.transmission_page.stop_transmission)
        self.reset_button.clicked.connect(self.transmission_page.reset_transmission)
        return panel

    def prepare_transmission(self) -> None:
        config = self.send_page.configuration()
        stride = self.send_page.mimo_stride.currentData() if hasattr(self.send_page, "mimo_stride") else 4
        self.transmission_page.configure(config.path, config.payload_size, config.mimo_mode, config.symbol_mode, config.fps, stride=stride)
        self.tabs.setCurrentIndex(2)

    start_transmission = prepare_transmission

    def _toggle_pause(self) -> None:
        if self.transmission_page.state_machine.paused:
            self.transmission_page.resume_transmission()
            self.pause_button.setText("PAUSE")
        else:
            self.transmission_page.pause_transmission()
            self.pause_button.setText("RESUME")

    def _update_diagnostics(self, values: dict[str, object]) -> None:
        for key, value in values.items():
            if key in self._diagnostic_labels:
                if key == "file_size":
                    text = f"{value} bytes"
                elif key == "actual_fps":
                    text = f"{float(value):.1f}"
                else:
                    text = str(value)
                self._diagnostic_labels[key].setText(text)
        state = values.get("state")
        ack = values.get("acknowledgement", "NOT AVAILABLE")
        self.acknowledgement_label.setText(f"Receiver acknowledgement: {ack}")
        if "ACK" in str(ack):
            self.acknowledgement_label.setStyleSheet("color: #4ade80; font-weight: bold;")
        elif "NACK" in str(ack):
            self.acknowledgement_label.setStyleSheet("color: #fbbf24; font-weight: bold;")
        elif "LOCKED" in str(ack):
            self.acknowledgement_label.setStyleSheet("color: #60a5fa; font-weight: bold;")
        else:
            self.acknowledgement_label.setStyleSheet("")
        has_file = bool(self.transmission_page.packets)
        self.show_qr_button.setEnabled(values.get("qr_status") == "GENERATED")
        self.start_button.setEnabled(has_file and state == "WAITING_FOR_RECEIVER")
        self.pause_button.setEnabled(state == "TRANSMITTING")
        self.stop_button.setEnabled(state == "TRANSMITTING")
        self.reset_button.setEnabled(has_file)
        self.home_page.update_session_summary(
            filename=str(values.get("filename", "--")),
            mimo_mode=str(values.get("mimo_mode", "--")),
            fps=int(values.get("target_fps", 0) or 0),
            payload_size=int(values.get("packet_size", 0) or 0),
            frames=int(values.get("frames_transmitted", 0) or 0),
            state=str(state or "IDLE"),
        )

    @staticmethod
    def _apply_theme() -> None:
        app = QApplication.instance()
        if app is None: return
        app.setStyle("Fusion")
        palette = QPalette()
        palette.setColor(QPalette.ColorRole.Window, QColor("#10151c")); palette.setColor(QPalette.ColorRole.WindowText, QColor("#e7edf5"))
        palette.setColor(QPalette.ColorRole.Base, QColor("#151c25")); palette.setColor(QPalette.ColorRole.AlternateBase, QColor("#202a36"))
        palette.setColor(QPalette.ColorRole.Text, QColor("#e7edf5")); palette.setColor(QPalette.ColorRole.Button, QColor("#263545"))
        palette.setColor(QPalette.ColorRole.ButtonText, QColor("#e7edf5")); palette.setColor(QPalette.ColorRole.Highlight, QColor("#2d8cff"))
        app.setPalette(palette)
