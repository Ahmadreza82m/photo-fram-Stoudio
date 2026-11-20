package com.photoframe.studio.data

import android.net.Uri

enum class ExportFormat { PNG, JPEG }

data class EdgeRule(
    val cropPx: Int = 0,
    val extendPx: Int = 0,
    val cropFraction: Float? = null,
    val extendFraction: Float? = null
)

data class FrameAdjustments(
    val top: EdgeRule = EdgeRule(),
    val bottom: EdgeRule = EdgeRule(),
    val left: EdgeRule = EdgeRule(),
    val right: EdgeRule = EdgeRule(),
    val fillColor: Long = 0xFFFFFFFF,
    val format: ExportFormat = ExportFormat.PNG,
    val jpegQuality: Int = 96
) {
    fun safe() = copy(
        top = top.safe(), bottom = bottom.safe(), left = left.safe(), right = right.safe(),
        jpegQuality = jpegQuality.coerceIn(1, 100)
    )
}

private fun EdgeRule.safe() = copy(cropPx = cropPx.coerceIn(0, 20000), extendPx = extendPx.coerceIn(0, 20000), cropFraction = cropFraction?.coerceIn(0f, 0.95f), extendFraction = extendFraction?.coerceIn(0f, 0.95f))

data class SelectedImage(val uri: Uri, val name: String, val bytes: Long = 0L)

data class FailedImage(val name: String, val reason: String)

data class BatchReport(val total: Int, val succeeded: Int, val failed: List<FailedImage>)

data class DetectedFrame(val left: Int, val top: Int, val right: Int, val bottom: Int, val confidence: Double, val imageWidth: Int, val imageHeight: Int)

data class StudioState(
    val referenceUri: Uri? = null,
    val referenceName: String? = null,
    val detectedFrame: DetectedFrame? = null,
    val detectionMessage: String? = null,
    val images: List<SelectedImage> = emptyList(),
    val outputFolder: Uri? = null,
    val adjustments: FrameAdjustments = FrameAdjustments(),
    val busy: Boolean = false,
    val paused: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val currentName: String = "",
    val report: BatchReport? = null,
    val notice: String? = null
) {
    val progress: Float get() = if (total == 0) 0f else completed.toFloat() / total
}
