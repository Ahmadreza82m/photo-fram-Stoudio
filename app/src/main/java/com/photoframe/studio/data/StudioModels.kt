package com.photoframe.studio.data

import android.net.Uri

enum class ExportFormat { PNG, JPEG }

data class EdgeRule(
    val cropPx: Int = 0,
    val extendPx: Int = 0
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

private fun EdgeRule.safe() = copy(cropPx = cropPx.coerceIn(0, 20000), extendPx = extendPx.coerceIn(0, 20000))

data class SelectedImage(val uri: Uri, val name: String, val bytes: Long = 0L)

data class FailedImage(val name: String, val reason: String)

data class BatchReport(val total: Int, val succeeded: Int, val failed: List<FailedImage>)

data class StudioState(
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
