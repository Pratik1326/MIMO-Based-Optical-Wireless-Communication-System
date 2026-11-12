"""Generate and place four OpenCV ArUco tracking anchors."""

from __future__ import annotations

from collections.abc import Sequence

import cv2
import numpy as np


class ArucoAnchorGenerator:
    def __init__(self, marker_size: int = 64, margin: int = 12) -> None:
        if marker_size <= 0 or margin < 0:
            raise ValueError("marker_size must be positive and margin cannot be negative")
        self.marker_size = marker_size
        self.margin = margin
        self.dictionary = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_4X4_50)

    def marker(self, marker_id: int) -> np.ndarray:
        if not 0 <= marker_id < 50:
            raise ValueError("DICT_4X4_50 marker IDs must be in the range 0..49")
        return cv2.aruco.generateImageMarker(self.dictionary, marker_id, self.marker_size)

    def place(self, frame: np.ndarray, marker_ids: Sequence[int] = (0, 1, 2, 3)) -> np.ndarray:
        if len(marker_ids) != 4:
            raise ValueError("exactly four marker IDs are required")
        output = frame.copy()
        height, width = output.shape[:2]
        if width < self.marker_size * 2 + self.margin * 2 or height < self.marker_size * 2 + self.margin * 2:
            raise ValueError("frame is too small for four corner markers")
        positions = (
            (self.margin, self.margin),
            (width - self.margin - self.marker_size, self.margin),
            (width - self.margin - self.marker_size, height - self.margin - self.marker_size),
            (self.margin, height - self.margin - self.marker_size),
        )
        for marker_id, (x, y) in zip(marker_ids, positions):
            quiet_left = max(0, x - self.margin)
            quiet_top = max(0, y - self.margin)
            quiet_right = min(width - 1, x + self.marker_size + self.margin - 1)
            quiet_bottom = min(height - 1, y + self.marker_size + self.margin - 1)
            cv2.rectangle(output, (quiet_left, quiet_top), (quiet_right, quiet_bottom), (255, 255, 255), -1)
            marker = self.marker(marker_id)
            output[y : y + self.marker_size, x : x + self.marker_size] = cv2.cvtColor(marker, cv2.COLOR_GRAY2BGR)
        return output
