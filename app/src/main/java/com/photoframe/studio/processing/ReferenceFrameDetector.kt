package com.photoframe.studio.processing

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc

/** Candidate detector only: its result must be reviewed before applying. */
object ReferenceFrameDetector {
    data class Candidate(val left: Int, val top: Int, val right: Int, val bottom: Int, val confidence: Double)

    fun detect(bitmap: Bitmap): Candidate? {
        if (!OpenCVLoader.initLocal()) return null
        val rgba = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, org.opencv.core.Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 45.0, 135.0)
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            val imageArea = bitmap.width.toDouble() * bitmap.height.toDouble()
            return contours.mapNotNull { contour ->
                val r: Rect = Imgproc.boundingRect(contour)
                val area = r.width.toDouble() * r.height.toDouble()
                val ratio = r.width.toDouble() / r.height.coerceAtLeast(1)
                if (area < imageArea * 0.06 || area > imageArea * 0.97 || ratio !in 0.35..2.8) null
                else Candidate(r.x, r.y, r.x + r.width, r.y + r.height, area / imageArea)
            }.maxByOrNull { it.confidence }
        } finally {
            contours.forEach { it.release() }
            rgba.release(); gray.release(); blurred.release(); edges.release(); hierarchy.release()
        }
    }
}
