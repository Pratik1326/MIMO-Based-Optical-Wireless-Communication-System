import time
import pytest
from PyQt6.QtWidgets import QApplication

from pc.app.optical.optical_feedback_detector import OpticalFeedbackDetector


@pytest.fixture(scope="module")
def qapp():
    app = QApplication.instance() or QApplication([])
    yield app
    app.processEvents()


def test_detector_initialization_and_lifecycle(qapp) -> None:
    detector = OpticalFeedbackDetector(camera_index=99)
    assert not detector.is_running
    # Stop when not running should not throw
    detector.stop_listening()
    assert not detector.is_running


def test_process_luminance_single_pulse_ready(qapp) -> None:
    detector = OpticalFeedbackDetector()
    state = {
        "baseline": 50.0,
        "is_high": False,
        "pulse_start": 0.0,
        "burst_count": 0,
        "last_pulse_end": 0.0,
    }

    t0 = 100.0
    # Baseline frames
    assert detector.process_luminance_sample(50.0, t0, state) is None
    assert detector.process_luminance_sample(52.0, t0 + 0.02, state) is None

    # Flash turns ON (jump to 180.0)
    assert detector.process_luminance_sample(180.0, t0 + 0.05, state) is None
    assert state["is_high"] is True

    # Flash stays ON for 120ms
    assert detector.process_luminance_sample(185.0, t0 + 0.12, state) is None

    # Flash turns OFF (drops back to 50.0)
    assert detector.process_luminance_sample(51.0, t0 + 0.18, state) is None
    assert state["is_high"] is False
    assert state["burst_count"] == 1

    # Inter-pulse quiet time before timeout (200ms after pulse end)
    assert detector.process_luminance_sample(50.0, t0 + 0.38, state) is None

    # Silence reaches burst_timeout (0.40s after pulse end at t0+0.18 => t0+0.59)
    event = detector.process_luminance_sample(50.0, t0 + 0.60, state)
    assert event is not None
    pattern, count, desc = event
    assert pattern == "READY"
    assert count == 1
    assert "1 pulse" in desc


def test_process_luminance_three_pulses_ack(qapp) -> None:
    detector = OpticalFeedbackDetector()
    state = {
        "baseline": 50.0,
        "is_high": False,
        "pulse_start": 0.0,
        "burst_count": 0,
        "last_pulse_end": 0.0,
    }

    t = 10.0
    # Pulse 1
    detector.process_luminance_sample(180.0, t, state)
    detector.process_luminance_sample(180.0, t + 0.08, state)
    detector.process_luminance_sample(50.0, t + 0.10, state)
    assert state["burst_count"] == 1

    # Gap 1 (100ms)
    t += 0.20
    # Pulse 2
    detector.process_luminance_sample(190.0, t, state)
    detector.process_luminance_sample(190.0, t + 0.08, state)
    detector.process_luminance_sample(50.0, t + 0.10, state)
    assert state["burst_count"] == 2

    # Gap 2 (100ms)
    t += 0.20
    # Pulse 3
    detector.process_luminance_sample(200.0, t, state)
    detector.process_luminance_sample(200.0, t + 0.08, state)
    detector.process_luminance_sample(50.0, t + 0.10, state)
    assert state["burst_count"] == 3

    # Still waiting
    assert detector.process_luminance_sample(50.0, t + 0.20, state) is None

    # Timeout fired!
    event = detector.process_luminance_sample(50.0, t + 0.50, state)
    assert event is not None
    pattern, count, desc = event
    assert pattern == "ACK"
    assert count == 3
    assert "Complete" in desc


def test_process_luminance_two_pulses_nack(qapp) -> None:
    detector = OpticalFeedbackDetector()
    state = {
        "baseline": 50.0,
        "is_high": False,
        "pulse_start": 0.0,
        "burst_count": 0,
        "last_pulse_end": 0.0,
    }

    t = 20.0
    # Pulse 1
    detector.process_luminance_sample(180.0, t, state)
    detector.process_luminance_sample(50.0, t + 0.10, state)
    assert state["burst_count"] == 1

    # Pulse 2
    t += 0.20
    detector.process_luminance_sample(180.0, t, state)
    detector.process_luminance_sample(50.0, t + 0.10, state)
    assert state["burst_count"] == 2

    # Timeout
    event = detector.process_luminance_sample(50.0, t + 0.55, state)
    assert event is not None
    pattern, count, desc = event
    assert pattern == "NACK"
    assert count == 2
    assert "Retransmission" in desc


def test_ignore_too_short_noise_glitch(qapp) -> None:
    detector = OpticalFeedbackDetector(min_pulse_duration=0.04)
    state = {
        "baseline": 50.0,
        "is_high": False,
        "pulse_start": 0.0,
        "burst_count": 0,
        "last_pulse_end": 0.0,
    }

    t = 30.0
    # Glitch lasting only 10ms
    detector.process_luminance_sample(200.0, t, state)
    detector.process_luminance_sample(50.0, t + 0.01, state)
    assert state["burst_count"] == 0


def test_process_features_off_axis_low_angle_pulse(qapp) -> None:
    detector = OpticalFeedbackDetector()
    state = {
        "baseline_p98": 160.0,
        "baseline_mean": 95.0,
        "is_high": False,
        "pulse_start": 0.0,
        "burst_count": 0,
        "last_pulse_end": 0.0,
    }

    t0 = 50.0
    # Ambient frame: sat_count=2, max_val=225, p98=160, mean=95
    assert detector.process_features_sample(2, 225, 160.0, 95.0, t0, state) is None

    # Off-axis flash (like Frame 260 from video): sat_count=60, max_val=255, p98=202, mean=125
    # Mean delta is only +30, but sat_count=60 immediately triggers HIGH
    assert detector.process_features_sample(60, 255, 202.0, 125.0, t0 + 0.05, state) is None
    assert state["is_high"] is True

    # Flash stays on for 150ms
    assert detector.process_features_sample(65, 255, 205.0, 128.0, t0 + 0.15, state) is None

    # Flash turns OFF: sat_count drops to 3
    assert detector.process_features_sample(3, 228, 162.0, 96.0, t0 + 0.22, state) is None
    assert state["is_high"] is False
    assert state["burst_count"] == 1

    # Quiet timeout fires
    event = detector.process_features_sample(2, 225, 160.0, 95.0, t0 + 0.65, state)
    assert event is not None
    assert event[0] == "READY"
    assert event[1] == 1


def test_process_features_ambient_lamp_rejection(qapp) -> None:
    detector = OpticalFeedbackDetector()
    state = {
        "baseline_p98": 160.0,
        "baseline_mean": 95.0,
        "is_high": False,
        "pulse_start": 0.0,
        "burst_count": 0,
        "last_pulse_end": 0.0,
    }

    t0 = 100.0
    # Ceiling lamp in background (sat_count=6, max=242, p98=170) -> Should NOT trigger
    for offset in range(10):
        t = t0 + offset * 0.05
        event = detector.process_features_sample(6, 242, 170.0, 98.0, t, state)
        assert event is None
        assert state["is_high"] is False

def test_process_features_sustained_strobe_auto_ack(qapp) -> None:
    detector = OpticalFeedbackDetector()
    state = {
        "baseline_p98": 160.0,
        "baseline_mean": 95.0,
        "is_high": False,
        "pulse_start": 0.0,
        "burst_count": 0,
        "last_pulse_end": 0.0,
    }

    t0 = 200.0
    # Flash turns ON (strong saturated cluster)
    assert detector.process_features_sample(75, 255, 210.0, 130.0, t0, state) is None
    assert state["is_high"] is True

    # Flash stays on for 0.40s (not sustained yet)
    assert detector.process_features_sample(80, 255, 212.0, 132.0, t0 + 0.40, state) is None

    # Flash stays on for >= 0.65s -> Immediate ACK fires while still high!
    event = detector.process_features_sample(80, 255, 212.0, 132.0, t0 + 0.66, state)
    assert event is not None
    pattern, count, desc = event
    assert pattern == "ACK"
    assert count == 3
    assert "Sustained" in desc


def test_process_luminance_sustained_strobe_auto_ack(qapp) -> None:
    detector = OpticalFeedbackDetector()
    state = {
        "baseline": 50.0,
        "is_high": False,
        "pulse_start": 0.0,
        "burst_count": 0,
        "last_pulse_end": 0.0,
    }

    t0 = 300.0
    assert detector.process_luminance_sample(180.0, t0, state) is None
    assert state["is_high"] is True
    assert detector.process_luminance_sample(180.0, t0 + 0.50, state) is None

    # Fires immediate ACK at 0.66s
    event = detector.process_luminance_sample(180.0, t0 + 0.66, state)
    assert event is not None
    assert event[0] == "ACK"
    assert event[1] == 3



def test_process_features_continuous_room_light_rejection(qapp) -> None:
    detector = OpticalFeedbackDetector()
    state = {
        "baseline_p98": 160.0,
        "baseline_mean": 95.0,
        "baseline_sat": 25.0,
        "is_high": False,
        "pulse_start": 0.0,
        "burst_count": 0,
        "last_pulse_end": 0.0,
    }

    t0 = 400.0
    # Simulate bright ceiling room lights (sat_count=30, max=255, p98=170, mean=98)
    for i in range(30):
        t = t0 + i * 0.10
        event = detector.process_features_sample(30, 255, 170.0, 98.0, t, state)
        # MUST NEVER trigger any pulse or ACK!
        assert event is None
    # State should remain low
    assert state["is_high"] is False


def test_transmission_page_spurious_ack_rejected_in_waiting_state(qapp) -> None:
    from pc.app.ui.transmission_page import TransmissionPage
    from pc.app.optical.state_machine import TransmissionState

    page = TransmissionPage()
    # Configure a dummy transmission
    import tempfile
    with tempfile.NamedTemporaryFile(delete=False) as tf:
        tf.write(b"ABCDEFGHIJKL" * 50)
        temp_path = tf.name

    page.configure(temp_path, payload_size=64, mimo_mode="1x1")
    assert page.state_machine.state == TransmissionState.WAITING_FOR_RECEIVER
    assert page.acknowledgement == "NOT AVAILABLE"
    assert page.frame_count == 0

    # Spurious ACK emitted by room light or webcam noise while WAITING_FOR_RECEIVER
    page._on_pulse_detected("ACK", 3, "Spurious room light ACK")

    # MUST BE REJECTED: Still NOT AVAILABLE and still WAITING_FOR_RECEIVER
    assert page.acknowledgement == "NOT AVAILABLE"
    assert page.state_machine.state == TransmissionState.WAITING_FOR_RECEIVER
