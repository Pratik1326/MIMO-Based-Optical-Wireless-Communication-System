import numpy as np

from pc.app.optical.frame_generator import FrameGenerator, FrameRenderConfig
from pc.app.optical.symbol_encoder import SymbolEncoder, SymbolMode


def test_manchester_symbol_encoding_is_reversible():
    encoder = SymbolEncoder(cell_size=2, padding=1)
    data = b"OCC"

    matrix = encoder.encode(data, SymbolMode.MANCHESTER)

    assert encoder.decode(matrix, len(data), SymbolMode.MANCHESTER) == data


def test_data_frames_keep_full_grid_geometry_and_tracking_markers():
    generator = FrameGenerator(FrameRenderConfig(width=480, height=480, layout="2x2", marker_size=48))
    matrices = {0: np.zeros((8, 8), dtype=np.uint8)}

    frame = generator.render(matrices)

    assert frame.shape == (576, 576, 3)
    assert np.array_equal(frame[240, 240], [0, 0, 0])
    assert generator.anchors.marker_size == 48


def test_aruco_marker_positions_are_clockwise_tl_tr_br_bl():
    import cv2
    generator = FrameGenerator(FrameRenderConfig(width=480, height=480, layout="1x1", marker_size=48))
    frame = generator.render({0: np.zeros((8, 8), dtype=np.uint8)})

    dictionary = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_4X4_50)
    detector = cv2.aruco.ArucoDetector(dictionary, cv2.aruco.DetectorParameters())
    corners, ids, _ = detector.detectMarkers(frame)

    assert ids is not None
    assert set(ids.ravel()) == {0, 1, 2, 3}

    height, width = frame.shape[:2]
    mid_x, mid_y = width / 2.0, height / 2.0

    marker_centers = {}
    for marker_id, corner_pts in zip(ids.ravel(), corners):
        cx = corner_pts[0][:, 0].mean()
        cy = corner_pts[0][:, 1].mean()
        marker_centers[marker_id] = (cx, cy)

    # 0 = TL: cx < mid_x, cy < mid_y
    assert marker_centers[0][0] < mid_x and marker_centers[0][1] < mid_y
    # 1 = TR: cx > mid_x, cy < mid_y
    assert marker_centers[1][0] > mid_x and marker_centers[1][1] < mid_y
    # 2 = BR: cx > mid_x, cy > mid_y
    assert marker_centers[2][0] > mid_x and marker_centers[2][1] > mid_y
    # 3 = BL: cx < mid_x, cy > mid_y
    assert marker_centers[3][0] < mid_x and marker_centers[3][1] > mid_y


def test_terminal_packet_padding_maintains_grid_dimensions():
    encoder = SymbolEncoder(cell_size=4, padding=1)
    # A full 64-byte payload packet has 79 bytes total
    full_packet_bytes = b"X" * 79
    # A short terminal packet might have only 35 bytes
    short_packet_bytes = b"X" * 35

    grid_full = encoder.encode(full_packet_bytes, SymbolMode.BINARY, min_bytes=79)
    grid_short = encoder.encode(short_packet_bytes, SymbolMode.BINARY, min_bytes=79)

    assert grid_full.shape == grid_short.shape == (112, 112)  # 28 cells * 4 pixels = 112