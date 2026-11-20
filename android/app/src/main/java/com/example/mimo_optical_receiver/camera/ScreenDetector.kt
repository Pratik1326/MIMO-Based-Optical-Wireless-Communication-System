package com.example.mimo_optical_receiver.camera

import org.opencv.aruco.Aruco
import org.opencv.aruco.DetectorParameters
import org.opencv.aruco.Dictionary
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video

data class ScreenDetectionResult(
    val markerCount: Int,
    val screenCorners: List<Point>,
    val isObstructed: Boolean = false,
    val obstructionReason: String? = null
)

class ScreenDetector {
    private val arucoDict: Dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_4X4_50)
    
    private var prevGrayMat: Mat? = null
    private var prevCornersMat: MatOfPoint2f? = null
    private var isTracking = false
    private var trackingFrameCount = 0

    fun detect(grayMat: Mat): ScreenDetectionResult {
        val totalFrameArea = grayMat.cols().toDouble() * grayMat.rows().toDouble()
        val minRequiredArea = totalFrameArea * 0.035 // 3.5% minimum frame area (allows 40-75cm comfortable range)

        // 1. PRIMARY STRATEGY: DIRECT ARUCO ANCHOR DETECTION (Subpixel Precision, Zero Drift)
        // Evaluates every frame against ArUco markers (0, 1, 2, 3) to prevent cumulative optical flow drift
        var ids: Mat? = null
        val corners = ArrayList<Mat>()

        try {
            ids = Mat()

            val parameters = DetectorParameters.create().apply {
                set_adaptiveThreshWinSizeMin(3)
                set_adaptiveThreshWinSizeMax(55)
                set_adaptiveThreshWinSizeStep(6)
                set_adaptiveThreshConstant(7.0)
                set_polygonalApproxAccuracyRate(0.04)
                set_errorCorrectionRate(0.8)
                set_minMarkerPerimeterRate(0.01)
                set_perspectiveRemovePixelPerCell(6)
                set_cornerRefinementMethod(Aruco.CORNER_REFINE_SUBPIX)
            }

            Aruco.detectMarkers(grayMat, arucoDict, corners, ids, parameters)

            if (ids.rows() >= 2) {
                val detectedMarkers = mutableMapOf<Int, Mat>()
                for (i in 0 until ids.rows()) {
                    val markerId = ids.get(i, 0)[0].toInt()
                    if (markerId in 0..3) {
                        detectedMarkers[markerId] = corners[i]
                    }
                }

                fun getCenterAndCorners(mat: Mat): Pair<Point, List<Point>> {
                    val pts = FloatArray(8)
                    mat.get(0, 0, pts)
                    val cx = (pts[0] + pts[2] + pts[4] + pts[6]) / 4.0
                    val cy = (pts[1] + pts[3] + pts[5] + pts[7]) / 4.0
                    val cornerList = listOf(
                        Point(pts[0].toDouble(), pts[1].toDouble()),
                        Point(pts[2].toDouble(), pts[3].toDouble()),
                        Point(pts[4].toDouble(), pts[5].toDouble()),
                        Point(pts[6].toDouble(), pts[7].toDouble())
                    )
                    return Pair(Point(cx, cy), cornerList)
                }

                val parsedMarkers = mutableMapOf<Int, Pair<Point, List<Point>>>()
                for ((id, mat) in detectedMarkers) {
                    parsedMarkers[id] = getCenterAndCorners(mat)
                }

                if (parsedMarkers.size >= 2) {
                    val allIdeals = mutableListOf<Point>()
                    val allObserved = mutableListOf<Point>()
                    for ((id, pair) in parsedMarkers) {
                        val ideals = MARKER_IDEAL_CORNERS[id] ?: continue
                        allIdeals.addAll(ideals)
                        allObserved.addAll(pair.second)
                    }

                    // Project 4 outer screen corners (TL, TR, BR, BL) via RANSAC Homography
                    var outerCorners: List<Point>? = projectOuterScreenCorners(allIdeals, allObserved)

                    // Fallback: 2-Marker similarity transform if homography fails
                    if (outerCorners == null && parsedMarkers.size >= 2) {
                        val markerList = parsedMarkers.toList()
                        val (idA, pairA) = markerList[0]
                        val (idB, pairB) = markerList[1]
                        val idealA = MARKER_IDEAL_CENTERS[idA]
                        val idealB = MARKER_IDEAL_CENTERS[idB]
                        if (idealA != null && idealB != null) {
                            val cA = pairA.first
                            val cB = pairB.first
                            val idealVx = idealB.x - idealA.x
                            val idealVy = idealB.y - idealA.y
                            val idealDistSq = idealVx * idealVx + idealVy * idealVy

                            if (idealDistSq > 1e-4) {
                                val obsVx = cB.x - cA.x
                                val obsVy = cB.y - cA.y
                                val cosSin_x = (obsVx * idealVx + obsVy * idealVy) / idealDistSq
                                val cosSin_y = (obsVy * idealVx - obsVx * idealVy) / idealDistSq

                                fun mapIdeal(ip: Point): Point {
                                    val dx = ip.x - idealA.x
                                    val dy = ip.y - idealA.y
                                    val rx = dx * cosSin_x - dy * cosSin_y
                                    val ry = dx * cosSin_y + dy * cosSin_x
                                    return Point(cA.x + rx, cA.y + ry)
                                }

                                outerCorners = listOf(
                                    mapIdeal(Point(0.0, 0.0)),
                                    mapIdeal(Point(800.0, 0.0)),
                                    mapIdeal(Point(800.0, 800.0)),
                                    mapIdeal(Point(0.0, 800.0))
                                )
                            }
                        }
                    }

                    if (outerCorners != null && outerCorners.size == 4) {
                        val detectedArea = calculatePolygonArea(outerCorners)

                        if (detectedArea >= minRequiredArea) {
                            prevCornersMat?.release()
                            prevCornersMat = MatOfPoint2f(*outerCorners.toTypedArray())

                            prevGrayMat?.release()
                            prevGrayMat = grayMat.clone()

                            isTracking = true
                            trackingFrameCount = 0

                            return ScreenDetectionResult(parsedMarkers.size, outerCorners)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            ids?.release()
            for (mat in corners) {
                mat.release()
            }
            corners.clear()
        }

        // 2. SECONDARY FALLBACK: OPTICAL FLOW TRACKING (Bridge short momentary 1-5 frame occlusions/shakes)
        if (isTracking && prevGrayMat != null && prevCornersMat != null) {
            trackingFrameCount++

            if (trackingFrameCount > 5) {
                resetTracking()
            } else {
                val nextCornersMat = MatOfPoint2f()
                val status = MatOfByte()
                val err = MatOfFloat()

                try {
                    val winSize = Size(31.0, 31.0)
                    val maxLevel = 3
                    val criteria = TermCriteria(TermCriteria.EPS or TermCriteria.COUNT, 20, 0.03)

                    Video.calcOpticalFlowPyrLK(
                        prevGrayMat!!,
                        grayMat,
                        prevCornersMat!!,
                        nextCornersMat,
                        status,
                        err,
                        winSize,
                        maxLevel,
                        criteria
                    )

                    val statusArray = status.toArray()
                    val allTracked = statusArray.size == 4 && statusArray.all { it.toInt() == 1 }

                    if (allTracked) {
                        val trackedPoints = nextCornersMat.toList()
                        val currentArea = calculatePolygonArea(trackedPoints)

                        if (currentArea >= minRequiredArea) {
                            prevCornersMat?.release()
                            prevCornersMat = nextCornersMat

                            prevGrayMat?.release()
                            prevGrayMat = grayMat.clone()

                            return ScreenDetectionResult(4, trackedPoints)
                        } else {
                            resetTracking()
                        }
                    } else {
                        resetTracking()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    resetTracking()
                } finally {
                    status.release()
                    err.release()
                }
            }
        }

        // 3. TERTIARY FALLBACK: OUTER CONTOUR WITH SYMMETRY SANITIZATION
        val binaryMat = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()

        try {
            Imgproc.threshold(grayMat, binaryMat, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
            Imgproc.findContours(
                binaryMat,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            var bestCorners: List<Point>? = null
            var maxArea = 0.0

            for (contour in contours) {
                val contour2f = MatOfPoint2f(*contour.toArray())
                val area = Imgproc.contourArea(contour2f)

                if (area >= minRequiredArea && area > maxArea) {
                    val peri = Imgproc.arcLength(contour2f, true)
                    val approx2f = MatOfPoint2f()
                    Imgproc.approxPolyDP(contour2f, approx2f, 0.02 * peri, true)

                    val approxPoints = approx2f.toList()
                    val approxMatOfPoint = MatOfPoint(*approxPoints.toTypedArray())

                    if (approxPoints.size == 4 && Imgproc.isContourConvex(approxMatOfPoint)) {
                        maxArea = area
                        
                        val tl = approxPoints.minByOrNull { it.x + it.y } ?: approxPoints[0]
                        val br = approxPoints.maxByOrNull { it.x + it.y } ?: approxPoints[0]
                        var tr = approxPoints.maxByOrNull { it.x - it.y } ?: approxPoints[0]
                        var bl = approxPoints.minByOrNull { it.x - it.y } ?: approxPoints[0]
                        
                        // SYMMETRY SANITIZATION: Prevent TR or BL corner from pulling inward when data cells flash white
                        val topDist = distance(tl, tr)
                        val botDist = distance(bl, br)

                        if (topDist < botDist * 0.82) {
                            tr = Point(tl.x + (br.x - bl.x), tl.y + (br.y - bl.y))
                        }

                        bestCorners = listOf(tl, tr, br, bl)
                    }
                    
                    approx2f.release()
                    approxMatOfPoint.release()
                }
                contour2f.release()
            }

            if (bestCorners != null) {
                prevCornersMat?.release()
                prevCornersMat = MatOfPoint2f(*bestCorners.toTypedArray())

                prevGrayMat?.release()
                prevGrayMat = grayMat.clone()

                isTracking = true
                trackingFrameCount = 0

                return ScreenDetectionResult(4, bestCorners)
            }

            return ScreenDetectionResult(0, emptyList())
        } catch (e: Exception) {
            e.printStackTrace()
            return ScreenDetectionResult(0, emptyList())
        } finally {
            binaryMat.release()
            hierarchy.release()
            for (c in contours) {
                c.release()
            }
            contours.clear()
        }
    }

    companion object {
        private val MARKER_IDEAL_CORNERS = mapOf(
            0 to listOf(Point(6.0, 6.0), Point(50.0, 6.0), Point(50.0, 50.0), Point(6.0, 50.0)),
            1 to listOf(Point(750.0, 6.0), Point(794.0, 6.0), Point(794.0, 50.0), Point(750.0, 50.0)),
            2 to listOf(Point(750.0, 750.0), Point(794.0, 750.0), Point(794.0, 794.0), Point(750.0, 794.0)),
            3 to listOf(Point(6.0, 750.0), Point(50.0, 750.0), Point(50.0, 794.0), Point(6.0, 794.0))
        )

        private val MARKER_IDEAL_CENTERS = mapOf(
            0 to Point(28.0, 28.0),
            1 to Point(772.0, 28.0),
            2 to Point(772.0, 772.0),
            3 to Point(28.0, 772.0)
        )
    }

    private fun projectOuterScreenCorners(
        idealPoints: List<Point>,
        observedPoints: List<Point>
    ): List<Point>? {
        if (idealPoints.size < 4 || observedPoints.size < 4) return null
        var srcMat: MatOfPoint2f? = null
        var dstMat: MatOfPoint2f? = null
        var transformMat: Mat? = null
        var targetMat: MatOfPoint2f? = null
        var projectedMat: MatOfPoint2f? = null
        return try {
            srcMat = MatOfPoint2f(*idealPoints.toTypedArray())
            dstMat = MatOfPoint2f(*observedPoints.toTypedArray())
            transformMat = Calib3d.findHomography(srcMat, dstMat, Calib3d.RANSAC, 3.0)

            if (transformMat != null && !transformMat.empty()) {
                val targets = arrayOf(
                    Point(0.0, 0.0),
                    Point(800.0, 0.0),
                    Point(800.0, 800.0),
                    Point(0.0, 800.0)
                )
                targetMat = MatOfPoint2f(*targets)
                projectedMat = MatOfPoint2f()
                Core.perspectiveTransform(targetMat, projectedMat, transformMat)

                val result = projectedMat.toList()
                if (result.size == 4) result else null
            } else null
        } catch (e: Exception) {
            null
        } finally {
            srcMat?.release()
            dstMat?.release()
            transformMat?.release()
            targetMat?.release()
            projectedMat?.release()
        }
    }

    private fun distance(p1: Point, p2: Point): Double {
        return Math.sqrt(Math.pow(p2.x - p1.x, 2.0) + Math.pow(p2.y - p1.y, 2.0))
    }

    private fun calculatePolygonArea(corners: List<Point>): Double {
        if (corners.size < 3) return 0.0
        val matOfPoint = MatOfPoint2f(*corners.toTypedArray())
        val area = Imgproc.contourArea(matOfPoint)
        matOfPoint.release()
        return area
    }

    fun resetTracking() {
        isTracking = false
        trackingFrameCount = 0
        prevGrayMat?.release()
        prevGrayMat = null
        prevCornersMat?.release()
        prevCornersMat = null
    }
}
