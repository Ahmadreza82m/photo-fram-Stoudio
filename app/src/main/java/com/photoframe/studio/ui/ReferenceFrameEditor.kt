package com.photoframe.studio.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.photoframe.studio.data.DetectedFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun ReferenceFrameEditor(
    uri: Uri,
    frame: DetectedFrame?,
    onFrameChange: (DetectedFrame) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value?.recycle()
        value = withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                var sample = 1
                while (bounds.outWidth / (sample * 2) > 1600 || bounds.outHeight / (sample * 2) > 1600) sample *= 2
                resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }.getOrNull()
        }
    }
    val currentCallback by rememberUpdatedState(onFrameChange)
    val latestFrame by rememberUpdatedState(frame)
    val image = bitmap
    if (image != null) {
        var activeHandle by remember(image) { mutableIntStateOf(-1) }
        Canvas(
            Modifier.fillMaxWidth().height(250.dp).pointerInput(image) {
                fun fittedRect(): androidx.compose.ui.geometry.Rect {
                    val scale = min(size.width / image.width.toFloat(), size.height / image.height.toFloat())
                    val w = image.width * scale
                    val h = image.height * scale
                    val x = (size.width - w) / 2f
                    val y = (size.height - h) / 2f
                    return androidx.compose.ui.geometry.Rect(x, y, x + w, y + h)
                }
                detectDragGestures(
                    onDragStart = { point ->
                        val dst = fittedRect()
                        val f = latestFrame?.let { saved ->
                            val sx = image.width.toFloat() / saved.imageWidth
                            val sy = image.height.toFloat() / saved.imageHeight
                            saved.copy(left = (saved.left * sx).toInt(), right = (saved.right * sx).toInt(), top = (saved.top * sy).toInt(), bottom = (saved.bottom * sy).toInt(), imageWidth = image.width, imageHeight = image.height)
                        } ?: DetectedFrame(
                            (image.width * 0.05f).toInt(), (image.height * 0.05f).toInt(),
                            (image.width * 0.95f).toInt(), (image.height * 0.95f).toInt(),
                            0.0, image.width, image.height
                        )
                        val sx = dst.width / image.width
                        val sy = dst.height / image.height
                        val xs = listOf(f.left, (f.left + f.right) / 2, f.right).map { dst.left + it * sx }
                        val ys = listOf(f.top, (f.top + f.bottom) / 2, f.bottom).map { dst.top + it * sy }
                        val points = listOf(
                            Offset(xs[0], ys[0]), Offset(xs[1], ys[0]), Offset(xs[2], ys[0]),
                            Offset(xs[2], ys[1]), Offset(xs[2], ys[2]), Offset(xs[1], ys[2]),
                            Offset(xs[0], ys[2]), Offset(xs[0], ys[1])
                        )
                        val nearest = points.indices.minByOrNull { (points[it] - point).getDistance() }
                        activeHandle = nearest?.takeIf { (points[it] - point).getDistance() <= 48.dp.toPx() } ?: -1
                    },
                    onDragEnd = { activeHandle = -1 },
                    onDragCancel = { activeHandle = -1 },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val dst = fittedRect()
                        val old = latestFrame?.let { saved ->
                            val sx = image.width.toFloat() / saved.imageWidth
                            val sy = image.height.toFloat() / saved.imageHeight
                            saved.copy(left = (saved.left * sx).toInt(), right = (saved.right * sx).toInt(), top = (saved.top * sy).toInt(), bottom = (saved.bottom * sy).toInt(), imageWidth = image.width, imageHeight = image.height)
                        } ?: DetectedFrame(
                            (image.width * 0.05f).toInt(), (image.height * 0.05f).toInt(),
                            (image.width * 0.95f).toInt(), (image.height * 0.95f).toInt(),
                            0.0, image.width, image.height
                        )
                        val dx = (dragAmount.x / dst.width * image.width).toInt()
                        val dy = (dragAmount.y / dst.height * image.height).toInt()
                        var l = old.left
                        var t = old.top
                        var r = old.right
                        var b = old.bottom
                        when (activeHandle) {
                            0 -> { l += dx; t += dy }
                            1 -> t += dy
                            2 -> { r += dx; t += dy }
                            3 -> r += dx
                            4 -> { r += dx; b += dy }
                            5 -> b += dy
                            6 -> { l += dx; b += dy }
                            7 -> l += dx
                        }
                        l = l.coerceIn(0, image.width - 2)
                        t = t.coerceIn(0, image.height - 2)
                        r = r.coerceIn(l + 2, image.width)
                        b = b.coerceIn(t + 2, image.height)
                        currentCallback(DetectedFrame(l, t, r, b, old.confidence, image.width, image.height))
                    }
                )
            }
        ) {
            val scale = min(size.width / image.width.toFloat(), size.height / image.height.toFloat())
            val w = image.width * scale
            val h = image.height * scale
            val left = (size.width - w) / 2f
            val top = (size.height - h) / 2f
            val dst = androidx.compose.ui.geometry.Rect(left, top, left + w, top + h)
            drawImage(image.asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()), dstSize = IntSize(w.toInt(), h.toInt()))
            val f = latestFrame?.let { saved ->
                val sx = image.width.toFloat() / saved.imageWidth
                val sy = image.height.toFloat() / saved.imageHeight
                saved.copy(left = (saved.left * sx).toInt(), right = (saved.right * sx).toInt(), top = (saved.top * sy).toInt(), bottom = (saved.bottom * sy).toInt(), imageWidth = image.width, imageHeight = image.height)
            } ?: DetectedFrame(
                (image.width * 0.05f).toInt(), (image.height * 0.05f).toInt(),
                (image.width * 0.95f).toInt(), (image.height * 0.95f).toInt(),
                0.0, image.width, image.height
            )
            val rect = androidx.compose.ui.geometry.Rect(
                left + f.left * scale, top + f.top * scale,
                left + f.right * scale, top + f.bottom * scale
            )
            drawRect(Color(0x55000000), topLeft = Offset(left, top), size = Size(w, (rect.top - top).coerceAtLeast(0f)))
            drawRect(Color(0x55000000), topLeft = Offset(left, rect.bottom), size = Size(w, (top + h - rect.bottom).coerceAtLeast(0f)))
            drawRect(Color(0x55000000), topLeft = Offset(left, rect.top), size = Size((rect.left - left).coerceAtLeast(0f), rect.height))
            drawRect(Color(0x55000000), topLeft = Offset(rect.right, rect.top), size = Size((left + w - rect.right).coerceAtLeast(0f), rect.height))
            drawRect(Color(0xFF7DD3FC), topLeft = rect.topLeft, size = rect.size, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
            val handles = listOf(
                rect.topLeft, Offset(rect.center.x, rect.top), rect.topRight,
                Offset(rect.right, rect.center.y), rect.bottomRight,
                Offset(rect.center.x, rect.bottom), rect.bottomLeft,
                Offset(rect.left, rect.center.y)
            )
            handles.forEach { drawCircle(Color.White, radius = 7.dp.toPx(), center = it); drawCircle(Color(0xFF168ACB), radius = 4.dp.toPx(), center = it) }
        }
    }
}
