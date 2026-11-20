package com.photoframe.studio.processing

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import com.photoframe.studio.data.ExportFormat
import com.photoframe.studio.data.FrameAdjustments
import java.io.IOException
import kotlin.math.max

/**
 * Pixel-based frame preparation. The input URI is only read; output is always a new document.
 * To cap memory use, very large source images are decoded at a safe sample size.
 */
class BitmapFrameEngine(private val resolver: ContentResolver) {
    data class Processed(val width: Int, val height: Int)

    fun process(sourceUri: Uri, outputUri: Uri, raw: FrameAdjustments): Processed {
        val settings = raw.safe()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("فایل ورودی قابل خواندن نیست")
        val originalW = bounds.outWidth
        val originalH = bounds.outHeight
        if (originalW < 1 || originalH < 1) throw IOException("تصویر معتبر نیست")

        val maxPixels = 28_000_000L
        val sample = chooseSample(originalW, originalH, maxPixels)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val input = resolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("رمزگشایی تصویر ناموفق بود")

        try {
            val sx = input.width.toFloat() / originalW
            val sy = input.height.toFloat() / originalH
            val cropL = (((settings.left.cropFraction?.times(originalW.toFloat()) ?: settings.left.cropPx.toFloat())) * sx).toInt()
            val cropR = (((settings.right.cropFraction?.times(originalW.toFloat()) ?: settings.right.cropPx.toFloat())) * sx).toInt()
            val cropT = (((settings.top.cropFraction?.times(originalH.toFloat()) ?: settings.top.cropPx.toFloat())) * sy).toInt()
            val cropB = (((settings.bottom.cropFraction?.times(originalH.toFloat()) ?: settings.bottom.cropPx.toFloat())) * sy).toInt()
            val padL = (((settings.left.extendFraction?.times(originalW.toFloat()) ?: settings.left.extendPx.toFloat())) * sx).toInt()
            val padR = (((settings.right.extendFraction?.times(originalW.toFloat()) ?: settings.right.extendPx.toFloat())) * sx).toInt()
            val padT = (((settings.top.extendFraction?.times(originalH.toFloat()) ?: settings.top.extendPx.toFloat())) * sy).toInt()
            val padB = (((settings.bottom.extendFraction?.times(originalH.toFloat()) ?: settings.bottom.extendPx.toFloat())) * sy).toInt()

            val src = Rect(cropL, cropT, input.width - cropR, input.height - cropB)
            if (src.width() <= 0 || src.height() <= 0) throw IOException("مقدار برش از اندازه تصویر بیشتر است")
            val outW = src.width() + padL + padR
            val outH = src.height() + padT + padB
            if (outW < 1 || outH < 1 || outW.toLong() * outH > maxPixels) {
                throw IOException("ابعاد خروجی بیش از حد مجاز حافظه است")
            }

            val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(output)
                canvas.drawColor(android.graphics.Color.argb(
                    ((settings.fillColor shr 24) and 0xFF).toInt(),
                    ((settings.fillColor shr 16) and 0xFF).toInt(),
                    ((settings.fillColor shr 8) and 0xFF).toInt(),
                    (settings.fillColor and 0xFF).toInt()
                ))
                val target = Rect(padL, padT, padL + src.width(), padT + src.height())
                canvas.drawBitmap(input, src, target, Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
                resolver.openOutputStream(outputUri, "w")?.use { stream ->
                    val format = if (settings.format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    if (!output.compress(format, settings.jpegQuality, stream)) throw IOException("کدگذاری خروجی ناموفق بود")
                    stream.flush()
                } ?: throw IOException("نوشتن در پوشه مقصد ممکن نیست")
            } finally {
                output.recycle()
            }
            return Processed(outW, outH)
        } finally {
            input.recycle()
        }
    }


    fun previewPair(sourceUri: Uri, raw: FrameAdjustments): Pair<Bitmap, Bitmap> {
        val settings = raw.safe()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("خواندن تصویر پیش‌نمایش ناموفق بود")
        val originalW = bounds.outWidth
        val originalH = bounds.outHeight
        if (originalW <= 0 || originalH <= 0) throw IOException("تصویر پیش‌نمایش معتبر نیست")
        var sample = 1
        while (originalW / (sample * 2) > 1200 || originalH / (sample * 2) > 1200) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val before = resolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("رمزگشایی پیش‌نمایش ناموفق بود")
        val sx = before.width.toFloat() / originalW
        val sy = before.height.toFloat() / originalH
        val left = (((settings.left.cropFraction?.times(originalW.toFloat()) ?: settings.left.cropPx.toFloat())) * sx).toInt()
        val right = (((settings.right.cropFraction?.times(originalW.toFloat()) ?: settings.right.cropPx.toFloat())) * sx).toInt()
        val top = (((settings.top.cropFraction?.times(originalH.toFloat()) ?: settings.top.cropPx.toFloat())) * sy).toInt()
        val bottom = (((settings.bottom.cropFraction?.times(originalH.toFloat()) ?: settings.bottom.cropPx.toFloat())) * sy).toInt()
        val padL = (((settings.left.extendFraction?.times(originalW.toFloat()) ?: settings.left.extendPx.toFloat())) * sx).toInt()
        val padR = (((settings.right.extendFraction?.times(originalW.toFloat()) ?: settings.right.extendPx.toFloat())) * sx).toInt()
        val padT = (((settings.top.extendFraction?.times(originalH.toFloat()) ?: settings.top.extendPx.toFloat())) * sy).toInt()
        val padB = (((settings.bottom.extendFraction?.times(originalH.toFloat()) ?: settings.bottom.extendPx.toFloat())) * sy).toInt()
        val src = Rect(left, top, before.width - right, before.height - bottom)
        if (src.width() <= 0 || src.height() <= 0) {
            before.recycle()
            throw IOException("مقدار برش از اندازه تصویر پیش‌نمایش بیشتر است")
        }
        val outW = src.width() + padL + padR
        val outH = src.height() + padT + padB
        if (outW <= 0 || outH <= 0 || outW.toLong() * outH > 8_000_000L) {
            before.recycle()
            throw IOException("اندازه پیش‌نمایش بیش از حد مجاز است")
        }
        val after = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val color = settings.fillColor.toInt()
        Canvas(after).apply {
            drawColor(color)
            drawBitmap(before, src, Rect(padL, padT, padL + src.width(), padT + src.height()), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return before to after
    }

    private fun chooseSample(width: Int, height: Int, maxPixels: Long): Int {
        var sample = 1
        while ((width.toLong() / (sample * 2)) * (height.toLong() / (sample * 2)) > maxPixels) {
            sample *= 2
        }
        return max(1, sample)
    }
}
