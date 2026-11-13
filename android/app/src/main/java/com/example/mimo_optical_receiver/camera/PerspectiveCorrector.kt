package com.example.mimo_optical_receiver.camera

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class PerspectiveCorrector {

    companion object {
        // Physical geometry alignment between PC Transmitter and Android Receiver:
        // PC Transmitter frame: 800x800 px.
        // ArUco marker size = 44 px, margin = 6 px.
        // Inner corners of ArUco markers span 50 px to 750 px (width = 700 px).
        // Central data area spans 56 px to 744 px (width = 688 px).
        // The 6 px quiet margin on each side corresponds to:
        // 6.0 * (768.0 / 688.0) = 6.697674 px in 768x768 warped space.
        private const val INNER_MARKER_SPAN = 700.0
        private const val DATA_AREA_SPAN = 688.0
        private const val TARGET_WARP_SIZE = 768.0
        private const val MARGIN_OFFSET = (INNER_MARKER_SPAN - DATA_AREA_SPAN) / 2.0 * (TARGET_WARP_SIZE / DATA_AREA_SPAN)
    }

    fun correct(grayMat: Mat, corners: List<Point>): Mat? {
        if (corners.size != 4) return null

        var transform: Mat? = null
        var srcPts: MatOfPoint2f? = null
        var dstPts: MatOfPoint2f? = null
        var warpedMat: Mat? = null

        try {
            // Construct source points array (TL, TR, BR, BL) from detected ArUco marker inner corners
            srcPts = MatOfPoint2f(corners[0], corners[1], corners[2], corners[3])

            // Map ArUco inner corners outward by MARGIN_OFFSET (~4.465 px) so that the
            // 512x512 warped output matrix aligns 100% to the active data grid cells (0 px offset).
            dstPts = MatOfPoint2f(
                Point(-MARGIN_OFFSET, -MARGIN_OFFSET),
                Point(TARGET_WARP_SIZE + MARGIN_OFFSET, -MARGIN_OFFSET),
                Point(TARGET_WARP_SIZE + MARGIN_OFFSET, TARGET_WARP_SIZE + MARGIN_OFFSET),
                Point(-MARGIN_OFFSET, TARGET_WARP_SIZE + MARGIN_OFFSET)
            )

            // Compute Perspective Transform and Warp
            transform = Imgproc.getPerspectiveTransform(srcPts, dstPts)
            warpedMat = Mat()
            Imgproc.warpPerspective(grayMat, warpedMat, transform, Size(TARGET_WARP_SIZE, TARGET_WARP_SIZE))

            return warpedMat
        } catch (e: Exception) {
            e.printStackTrace()
            warpedMat?.release() // Ensure we don't leak an incomplete Mat on failure
            return null
        } finally {
            // Explicitly release intermediate transformation matrices
            transform?.release()
            srcPts?.release()
            dstPts?.release()
        }
    }
}
