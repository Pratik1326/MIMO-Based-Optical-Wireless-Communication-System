"""Qt OpenGL widget for aspect-correct optical frame presentation."""

from __future__ import annotations

import threading

import cv2
import numpy as np
from OpenGL import GL
from PyQt6.QtCore import Qt
from PyQt6.QtGui import QImage, QPainter
from PyQt6.QtOpenGL import QOpenGLVersionProfile
from PyQt6.QtOpenGLWidgets import QOpenGLWidget


class OpticalGLRenderer(QOpenGLWidget):
    """Display BGR NumPy frames with a VSync-requested OpenGL surface."""

    def __init__(self, parent=None) -> None:
        super().__init__(parent)
        self._frame_lock = threading.Lock()
        self._frame: np.ndarray | None = None
        self._texture: int | None = None
        self.setAttribute(Qt.WidgetAttribute.WA_OpaquePaintEvent)
        self.setMinimumSize(320, 240)

    def set_frame(self, frame: np.ndarray) -> None:
        array = np.ascontiguousarray(frame)
        if array.ndim != 3 or array.shape[2] != 3 or array.dtype != np.uint8:
            raise ValueError("frame must be a uint8 BGR array with shape (height, width, 3)")
        with self._frame_lock:
            self._frame = array.copy()
        self.update()

    def initializeGL(self) -> None:
        GL.glDisable(GL.GL_DEPTH_TEST)
        GL.glClearColor(0.0, 0.0, 0.0, 1.0)
        self._texture = GL.glGenTextures(1)
        GL.glBindTexture(GL.GL_TEXTURE_2D, self._texture)
        GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR)
        GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR)
        GL.glBindTexture(GL.GL_TEXTURE_2D, 0)
        self._request_vsync()

    def paintGL(self) -> None:
        with self._frame_lock:
            frame = None if self._frame is None else self._frame.copy()
        if frame is None:
            GL.glClear(GL.GL_COLOR_BUFFER_BIT)
            return
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        image = QImage(rgb.data, rgb.shape[1], rgb.shape[0], rgb.strides[0], QImage.Format.Format_RGB888).copy()
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform, False)
        target = self._aspect_rect(image.width(), image.height())
        painter.fillRect(self.rect(), Qt.GlobalColor.black)
        painter.drawImage(target, image)
        painter.end()

    def _request_vsync(self) -> None:
        context = self.context()
        if context is None:
            return
        surface = context.surface()
        if surface is not None:
            surface_format = surface.format()
            surface_format.setSwapInterval(1)

    def _aspect_rect(self, image_width: int, image_height: int):
        scale = min(self.width() / image_width, self.height() / image_height)
        width = round(image_width * scale)
        height = round(image_height * scale)
        left = (self.width() - width) // 2
        top = (self.height() - height) // 2
        from PyQt6.QtCore import QRect

        return QRect(left, top, width, height)
