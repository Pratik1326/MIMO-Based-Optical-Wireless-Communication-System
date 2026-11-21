"""Compose spatial symbol blocks, sync borders, and ArUco anchors."""

from __future__ import annotations

from dataclasses import dataclass
from collections.abc import Mapping

import cv2
import numpy as np

from ..mimo.splitter import SpatialLayout
from .aruco_generator import ArucoAnchorGenerator


@dataclass(frozen=True, slots=True)
class FrameRenderConfig:
    width: int = 800
    height: int = 800
    layout: SpatialLayout | str = SpatialLayout.GRID_2X2
    border_thickness: int = 4
    grid_thickness: int = 1
    marker_size: int = 44
    marker_margin: int = 6


class FrameGenerator:
    def __init__(self, config: FrameRenderConfig | None = None) -> None:
        self.config = config or FrameRenderConfig()
        if self.config.width <= 0 or self.config.height <= 0:
            raise ValueError("frame dimensions must be positive")
        if self.config.border_thickness <= 0 or self.config.grid_thickness < 0:
            raise ValueError("border thickness must be positive and grid thickness cannot be negative")
        self.layout = SpatialLayout.parse(self.config.layout)
        self.anchors = ArucoAnchorGenerator(self.config.marker_size, self.config.marker_margin)

    def _add_safe_zone(self, frame: np.ndarray) -> np.ndarray:
        """Adds a 10% black margin to prevent camera cutoff."""
        margin = int(frame.shape[0] * 0.10)
        return cv2.copyMakeBorder(
            frame, 
            top=margin, 
            bottom=margin, 
            left=margin, 
            right=margin, 
            borderType=cv2.BORDER_CONSTANT, 
            value=[0, 0, 0]
        )

    def render(self, channel_matrices: Mapping[int, np.ndarray]) -> np.ndarray:
        frame = np.zeros((self.config.height, self.config.width, 3), dtype=np.uint8)
        border = self.config.border_thickness
        cv2.rectangle(frame, (0, 0), (self.config.width - 1, self.config.height - 1), (255, 255, 255), border)
        
        if self.config.marker_size > 0:
            corner_zone = self.config.marker_size + self.config.marker_margin * 2
            inner_left = corner_zone
            inner_top = corner_zone
            inner_width = self.config.width - corner_zone * 2
            inner_height = self.config.height - corner_zone * 2
        else:
            inner_left, inner_top = border, border
            inner_width = self.config.width - border * 2
            inner_height = self.config.height - border * 2

        side = self.layout.side_length
        for channel_id in range(self.layout.channel_count):
            row, column = divmod(channel_id, side)
            x0 = inner_left + column * inner_width // side
            x1 = inner_left + (column + 1) * inner_width // side
            y0 = inner_top + row * inner_height // side
            y1 = inner_top + (row + 1) * inner_height // side
            matrix = channel_matrices.get(channel_id)
            if matrix is not None:
                block = self._as_bgr(matrix)
                resized = cv2.resize(block, (x1 - x0, y1 - y0), interpolation=cv2.INTER_NEAREST)
                frame[y0:y1, x0:x1] = resized
        if self.config.grid_thickness > 0:
            for coordinate in range(1, side):
                x = inner_left + coordinate * inner_width // side
                y = inner_top + coordinate * inner_height // side
                cv2.line(frame, (x, inner_top), (x, inner_top + inner_height - 1), (0, 0, 0), self.config.grid_thickness)
                cv2.line(frame, (inner_left, y), (inner_left + inner_width - 1, y), (0, 0, 0), self.config.grid_thickness)
            cv2.rectangle(frame, (inner_left, inner_top), (inner_left + inner_width - 1, inner_top + inner_height - 1), (0, 0, 0), self.config.grid_thickness)
        
        # Apply anchors, then apply the safe zone margin
        frame = self.anchors.place(frame)
        return self._add_safe_zone(frame)

    def render_qr(self, qr_image: np.ndarray) -> np.ndarray:
        """Render a centered Phase 1 QR image with high-contrast white card and four corner anchors."""
        frame = np.zeros((self.config.height, self.config.width, 3), dtype=np.uint8)
        border = self.config.border_thickness
        cv2.rectangle(frame, (0, 0), (self.config.width - 1, self.config.height - 1), (255, 255, 255), border)
        
        # Center data area boundaries with 10px dark isolation moat to prevent optical bloom into ArUco markers
        card_inset = self.config.marker_size + self.config.marker_margin * 2 + 10
        card_width = self.config.width - card_inset * 2
        card_height = self.config.height - card_inset * 2

        # Draw crisp high-contrast white background card
        cv2.rectangle(
            frame,
            (card_inset, card_inset),
            (card_inset + card_width - 1, card_inset + card_height - 1),
            (255, 255, 255),
            -1,
        )

        # Scale QR code with clean quiet margins inside the white card (guarantees ISO quiet zone)
        max_qr_size = min(card_width, card_height) - 48
        if max_qr_size <= 0:
            raise ValueError("frame is too small for the configured QR area")
        qr = np.asarray(qr_image, dtype=np.uint8)
        if qr.ndim != 2 or qr.size == 0:
            raise ValueError("qr_image must be a non-empty grayscale image")
        qr_h, qr_w = qr.shape[:2]
        if qr_w <= max_qr_size and qr_h <= max_qr_size and qr_w >= 300:
            # Already properly sized with clean quiet zone
            resized = qr
            width, height = qr_w, qr_h
        elif qr_w < 300:
            # Small raw QR: scale up using exact integer multiple to preserve finder patterns
            factor = max(1, max_qr_size // qr_w)
            width = qr_w * factor
            height = qr_h * factor
            resized = cv2.resize(qr, (width, height), interpolation=cv2.INTER_NEAREST)
        else:
            scale = min(max_qr_size / qr_w, max_qr_size / qr_h)
            width = max(1, round(qr_w * scale))
            height = max(1, round(qr_h * scale))
            resized = cv2.resize(qr, (width, height), interpolation=cv2.INTER_NEAREST)

        left = card_inset + (card_width - width) // 2
        top = card_inset + (card_height - height) // 2
        frame[top : top + height, left : left + width] = cv2.cvtColor(resized, cv2.COLOR_GRAY2BGR)
        
        # Apply anchors, then apply the safe zone margin
        frame = self.anchors.place(frame)
        return self._add_safe_zone(frame)

    @staticmethod
    def _as_bgr(matrix: np.ndarray) -> np.ndarray:
        array = np.asarray(matrix)
        if array.ndim == 2:
            return cv2.cvtColor(array.astype(np.uint8), cv2.COLOR_GRAY2BGR)
        if array.ndim == 3 and array.shape[2] == 3:
            return array.astype(np.uint8)
        raise ValueError("channel matrix must be grayscale or BGR")