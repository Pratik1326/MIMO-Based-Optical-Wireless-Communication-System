from pathlib import Path

import pytest
from PyQt6.QtWidgets import QApplication

from pc.app.ui.main_window import MainWindow


@pytest.fixture(scope="module")
def qapp():
    app = QApplication.instance() or QApplication([])
    yield app
    app.processEvents()


def test_main_window_has_all_pages_and_dark_theme(qapp) -> None:
    window = MainWindow()

    assert window.pages.count() == 5
    assert [window.tabs.tabText(index) for index in range(5)] == ["Home", "Send", "Transmission", "Settings", "Diagnostics"]
    assert qapp.style().objectName().lower() == "fusion"
    window.close()


def test_send_page_file_selection_enables_transmission(qapp, tmp_path: Path) -> None:
    source = tmp_path / "payload.txt"
    source.write_bytes(b"hello optical UI")
    window = MainWindow()

    assert not window.send_page.start_button.isEnabled()
    window.send_page.set_file(source)
    assert window.send_page.start_button.isEnabled()
    config = window.send_page.configuration()
    assert config.path == source
    assert config.payload_size == 256
    assert config.mimo_mode == "1x1"
    window.start_transmission()
    qapp.processEvents()

    assert window.tabs.currentIndex() == 2
    assert window.transmission_page.state_machine.state.value == "WAITING_FOR_RECEIVER"
    assert window.transmission_page.metadata is not None
    assert window.transmission_page.metadata.symbol_mode == "BINARY"
    assert window.transmission_page.qr_image is not None
    assert window.transmission_page.qr_image.size > 0
    assert window.acknowledgement_label.text() == "Receiver acknowledgement: NOT AVAILABLE"
    window.transmission_page.stop_transmission()
    window.close()


def test_settings_and_diagnostics_controls(qapp) -> None:
    window = MainWindow()

    assert window.settings_page.aruco_enabled.isChecked()
    window.settings_page.parity_ratio.setValue(64)
    assert window.settings_page.parity_ratio.value() == 64
    window.diagnostics_page.run_benchmark()
    assert "benchmark" in window.diagnostics_page.log_viewer.toPlainText().lower()
    window.close()


def test_endless_carousel_wraps_packet_buffer(qapp, tmp_path: Path) -> None:
    source = tmp_path / "carousel.bin"
    source.write_bytes(bytes(range(84)))
    window = MainWindow()
    window.transmission_page.configure(source, payload_size=64, mimo_mode="2x2", symbol_mode="BINARY", fps=60)
    page = window.transmission_page
    page.state_machine.start()

    page._render_tick(0, 0.0, None)
    assert page.current_packet == 1
    assert page.carousel_cycle == 0
    page._render_tick(1, 0.0, None)
    assert page.current_packet == 2
    assert page.carousel_cycle == 1
    page._render_tick(2, 0.0, None)
    assert page.current_packet == 1
    assert page.carousel_cycle == 1
    assert page.state_machine.state.value == "TRANSMITTING"
    page.stop_transmission()
    window.close()
