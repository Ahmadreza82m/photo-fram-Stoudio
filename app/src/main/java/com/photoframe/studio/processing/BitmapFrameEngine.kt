package com.photoframe.studio.processing

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import com.photoframe.studio.data.ExportFormat
import com.photoframe.studio.data.FrameAdjustments
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pixel-based frame preparation. The input URI is only read; output is always a new document.
 *
 * Empty margins are extended from the actual image edges. The extender estimates the local
 * colour slope from several pixels inward instead of painting a single flat fill colour.
 * It is deliberately deterministic and fully offline (no paid API, network or AI model).
 */
class BitmapFrameEngine(private val resolver: ContentResolver) {
    data class Processed(val width: Int, val height: Int)

    fun process(sourceUri: Uri, outputUri: Uri, raw: FrameAdjustments): Processed {
        val settings = raw.safe()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(sourceUri, bounds)
        val originalW = bounds.outWidth
        val originalH = bounds.outHeight
        if (originalW < 1 || originalH < 1) throw IOException("فرمت تصویر قابل رمزگشایی نیست یا فایل خراب است")

        // Keep memory use predictable on Android devices. The source is never overwritten.
        val maxPixels = 18_000_000L
        val sample = chooseSample(originalW, originalH, maxPixels)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val input = decode(sourceUri, options)
            ?: throw IOException("رمزگشایی تصویر ناموفق بود؛ فایل خالی یا خراب است")

        try {
            val sx = input.width.toFloat() / originalW
            val sy = input.height.toFloat() / originalH
            val cropL = ((settings.left.cropFraction?.times(originalW.toFloat()) ?: settings.left.cropPx.toFloat()) * sx).toInt()
            val cropR = ((settings.right.cropFraction?.times(originalW.toFloat()) ?: settings.right.cropPx.toFloat()) * sx).toInt()
            val cropT = ((settings.top.cropFraction?.times(originalH.toFloat()) ?: settings.top.cropPx.toFloat()) * sy).toInt()
            val cropB = ((settings.bottom.cropFraction?.times(originalH.toFloat()) ?: settings.bottom.cropPx.toFloat()) * sy).toInt()
            val padL = ((settings.left.extendFraction?.times(originalW.toFloat()) ?: settings.left.extendPx.toFloat()) * sx).toInt()
            val padR = ((settings.right.extendFraction?.times(originalW.toFloat()) ?: settings.right.extendPx.toFloat()) * sx).toInt()
            val padT = ((settings.top.extendFraction?.times(originalH.toFloat()) ?: settings.top.extendPx.toFloat()) * sy).toInt()
            val padB = ((settings.bottom.extendFraction?.times(originalH.toFloat()) ?: settings.bottom.extendPx.toFloat()) * sy).toInt()

            val src = Rect(cropL, cropT, input.width - cropR, input.height - cropB)
            if (src.width() <= 0 || src.height() <= 0) throw IOException("مقدار برش از اندازه تصویر بیشتر است")
            val outW = checkedDimension(src.width(), padL, padR)
            val outH = checkedDimension(src.height(), padT, padB)
            if (outW.toLong() * outH.toLong() > maxPixels) {
                throw IOException("ابعاد خروجی بیش از حد مجاز حافظه است؛ حاشیه‌ها را کمتر کن.")
            }

            val output = renderExtended(input, src, padL, padT, outW, outH)
            try {
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
        decode(sourceUri, bounds)
        val originalW = bounds.outWidth
        val originalH = bounds.outHeight
        if (originalW <= 0 || originalH <= 0) throw IOException("فرمت تصویر پیش‌نمایش قابل رمزگشایی نیست")

        var sample = 1
        while (originalW / (sample * 2) > 1200 || originalH / (sample * 2) > 1200) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val before = decode(sourceUri, opts)
            ?: throw IOException("رمزگشایی پیش‌نمایش ناموفق بود؛ فایل خالی یا خراب است")
        try {
            val sx = before.width.toFloat() / originalW
            val sy = before.height.toFloat() / originalH
            val left = ((settings.left.cropFraction?.times(originalW.toFloat()) ?: settings.left.cropPx.toFloat()) * sx).toInt()
            val right = ((settings.right.cropFraction?.times(originalW.toFloat()) ?: settings.right.cropPx.toFloat()) * sx).toInt()
            val top = ((settings.top.cropFraction?.times(originalH.toFloat()) ?: settings.top.cropPx.toFloat()) * sy).toInt()
            val bottom = ((settings.bottom.cropFraction?.times(originalH.toFloat()) ?: settings.bottom.cropPx.toFloat()) * sy).toInt()
            val padL = ((settings.left.extendFraction?.times(originalW.toFloat()) ?: settings.left.extendPx.toFloat()) * sx).toInt()
            val padR = ((settings.right.extendFraction?.times(originalW.toFloat()) ?: settings.right.extendPx.toFloat()) * sx).toInt()
            val padT = ((settings.top.extendFraction?.times(originalH.toFloat()) ?: settings.top.extendPx.toFloat()) * sy).toInt()
            val padB = ((settings.bottom.extendFraction?.times(originalH.toFloat()) ?: settings.bottom.extendPx.toFloat()) * sy).toInt()
            val src = Rect(left, top, before.width - right, before.height - bottom)
            if (src.width() <= 0 || src.height() <= 0) throw IOException("مقدار برش از اندازه تصویر پیش‌نمایش بیشتر است")
            val outW = checkedDimension(src.width(), padL, padR)
            val outH = checkedDimension(src.height(), padT, padB)
            if (outW.toLong() * outH.toLong() > 8_000_000L) throw IOException("اندازه پیش‌نمایش بیش از حد مجاز است")
            val after = renderExtended(before, src, padL, padT, outW, outH)
            return before to after
        } catch (e: Exception) {
            before.recycle()
            throw e
        }
    }

    private fun checkedDimension(content: Int, firstPad: Int, secondPad: Int): Int {
        val result = content.toLong() + firstPad.toLong() + secondPad.toLong()
        if (firstPad < 0 || secondPad < 0 || result !in 1L..Int.MAX_VALUE.toLong()) {
            throw IOException("ابعاد حاشیه نامعتبر است")
        }
        return result.toInt()
    }

    /**
     * Extrapolates the colour field at each edge using a least-squares line fitted to several
     * inward samples. At corners, the two edge estimates are blended. Source pixels are copied
     * unchanged into the content rectangle.
     */
    private fun renderExtended(input: Bitmap, src: Rect, padLeft: Int, padTop: Int, outW: Int, outH: Int): Bitmap {
        val targetLeft = padLeft
        val targetTop = padTop
        val targetRight = targetLeft + src.width()
        val targetBottom = targetTop + src.height()
        val profiles = EdgeProfiles(input, src)
        val output = try {
            Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            throw IOException("حافظه کافی برای ساخت تصویر خروجی وجود ندارد؛ اندازه خروجی یا حاشیه‌ها را کاهش بده.", e)
        }

        try {
            val row = IntArray(outW)
            val sourceRow = IntArray(src.width())
            for (y in 0 until outH) {
                val sourceY = y - targetTop
                val insideY = y in targetTop until targetBottom
                if (insideY) input.getPixels(sourceRow, 0, src.width(), src.left, src.top + sourceY, src.width(), 1)

                for (x in 0 until outW) {
                    val sourceX = x - targetLeft
                    if (insideY && x in targetLeft until targetRight) {
                        row[x] = sourceRow[sourceX]
                        continue
                    }

                    val outsideTop = y < targetTop
                    val outsideBottom = y >= targetBottom
                    val outsideLeft = x < targetLeft
                    val outsideRight = x >= targetRight
                    val vertical = when {
                        outsideTop -> profiles.top.estimate((x - targetLeft).coerceIn(0, src.width() - 1), targetTop - y)
                        outsideBottom -> profiles.bottom.estimate((x - targetLeft).coerceIn(0, src.width() - 1), y - targetBottom + 1)
                        else -> null
                    }
                    val horizontal = when {
                        outsideLeft -> profiles.left.estimate((y - targetTop).coerceIn(0, src.height() - 1), targetLeft - x)
                        outsideRight -> profiles.right.estimate((y - targetTop).coerceIn(0, src.height() - 1), x - targetRight + 1)
                        else -> null
                    }
                    row[x] = when {
                        vertical != null && horizontal != null -> blend(vertical, horizontal)
                        vertical != null -> vertical
                        horizontal != null -> horizontal
                        else -> input.getPixel(src.left, src.top)
                    }
                }
                output.setPixels(row, 0, outW, 0, y, outW, 1)
            }
            return output
        } catch (e: Exception) {
            output.recycle()
            throw e
        } catch (e: OutOfMemoryError) {
            output.recycle()
            throw IOException("حافظه کافی برای پردازش این تصویر وجود ندارد؛ اندازه تصویر یا حاشیه‌ها را کاهش بده.", e)
        }
    }

    private data class RgbLine(val baseR: Float, val baseG: Float, val baseB: Float, val slopeR: Float, val slopeG: Float, val slopeB: Float) {
        fun estimate(distance: Int): Int {
            fun channel(base: Float, slope: Float) = (base - slope * distance).roundToInt().coerceIn(0, 255)
            return (0xFF shl 24) or (channel(baseR, slopeR) shl 16) or
                (channel(baseG, slopeG) shl 8) or channel(baseB, slopeB)
        }
    }

    private class EdgeProfile(private val lines: Array<RgbLine>) {
        fun estimate(index: Int, distance: Int): Int = lines[index.coerceIn(0, lines.lastIndex)].estimate(distance)
    }

    private class EdgeProfiles(bitmap: Bitmap, src: Rect) {
        val top = EdgeProfile(build(bitmap, src, Side.TOP))
        val bottom = EdgeProfile(build(bitmap, src, Side.BOTTOM))
        val left = EdgeProfile(build(bitmap, src, Side.LEFT))
        val right = EdgeProfile(build(bitmap, src, Side.RIGHT))

        private enum class Side { TOP, BOTTOM, LEFT, RIGHT }

        companion object {
            private fun build(bitmap: Bitmap, src: Rect, side: Side): Array<RgbLine> {
                val horizontal = side == Side.TOP || side == Side.BOTTOM
                val count = if (horizontal) src.width() else src.height()
                val depthLimit = if (horizontal) src.height() - 1 else src.width() - 1
                val depths = intArrayOf(0, 1, 2, 4, 8, 12).filter { it <= depthLimit }.distinct()
                val usableDepths = if (depths.size >= 2) depths else listOf(0)
                return Array(count) { tangent ->
                    val reds = FloatArray(usableDepths.size)
                    val greens = FloatArray(usableDepths.size)
                    val blues = FloatArray(usableDepths.size)
                    for (i in usableDepths.indices) {
                        val d = usableDepths[i]
                        var r = 0f; var g = 0f; var b = 0f; var n = 0
                        for (offset in -2..2) {
                            val t = tangent + offset
                            if (t !in 0 until count) continue
                            val x: Int
                            val y: Int
                            when (side) {
                                Side.TOP -> { x = src.left + t; y = src.top + d }
                                Side.BOTTOM -> { x = src.left + t; y = src.bottom - 1 - d }
                                Side.LEFT -> { x = src.left + d; y = src.top + t }
                                Side.RIGHT -> { x = src.right - 1 - d; y = src.top + t }
                            }
                            val color = bitmap.getPixel(x, y)
                            r += ((color shr 16) and 0xFF).toFloat()
                            g += ((color shr 8) and 0xFF).toFloat()
                            b += (color and 0xFF).toFloat()
                            n++
                        }
                        val divisor = n.coerceAtLeast(1).toFloat()
                        reds[i] = r / divisor; greens[i] = g / divisor; blues[i] = b / divisor
                    }
                    RgbLine(
                        fitIntercept(usableDepths, reds), fitIntercept(usableDepths, greens), fitIntercept(usableDepths, blues),
                        fitSlope(usableDepths, reds), fitSlope(usableDepths, greens), fitSlope(usableDepths, blues)
                    )
                }
            }

            private fun fitIntercept(x: List<Int>, y: FloatArray): Float {
                if (x.size == 1) return y[0]
                val (intercept, _) = fit(x, y)
                return intercept
            }

            private fun fitSlope(x: List<Int>, y: FloatArray): Float {
                if (x.size == 1) return 0f
                return fit(x, y).second
            }

            private fun fit(x: List<Int>, y: FloatArray): Pair<Float, Float> {
                val meanX = x.average().toFloat()
                val meanY = y.average().toFloat()
                var numerator = 0f
                var denominator = 0f
                for (i in x.indices) {
                    val dx = x[i] - meanX
                    numerator += dx * (y[i] - meanY)
                    denominator += dx * dx
                }
                val slope = if (denominator == 0f) 0f else numerator / denominator
                return (meanY - slope * meanX) to slope
            }
        }
    }

    private fun blend(a: Int, b: Int): Int {
        fun avg(shift: Int) = ((((a shr shift) and 0xFF) + ((b shr shift) and 0xFF)) / 2)
        return (0xFF shl 24) or (avg(16) shl 16) or (avg(8) shl 8) or avg(0)
    }

    private fun decode(uri: Uri, options: BitmapFactory.Options): Bitmap? {
        var streamError: Exception? = null
        try {
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            if (decoded != null || (options.inJustDecodeBounds && options.outWidth > 0 && options.outHeight > 0)) return decoded
        } catch (e: Exception) {
            streamError = e
        }

        try {
            val decoded = resolver.openFileDescriptor(uri, "r")?.use {
                BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, options)
            }
            if (decoded != null || (options.inJustDecodeBounds && options.outWidth > 0 && options.outHeight > 0)) return decoded
        } catch (e: Exception) {
            if (streamError == null) streamError = e
        }

        throw IOException(
            "دسترسی به فایل ورودی ممکن نشد. ممکن است مجوز فایل از دست رفته یا فایل از دسترس خارج شده باشد.",
            streamError
        )
    }

    private fun chooseSample(width: Int, height: Int, maxPixels: Long): Int {
        var sample = 1
        while ((width.toLong() / (sample * 2)) * (height.toLong() / (sample * 2)) > maxPixels) {
            sample *= 2
        }
        return max(1, sample)
    }
}
