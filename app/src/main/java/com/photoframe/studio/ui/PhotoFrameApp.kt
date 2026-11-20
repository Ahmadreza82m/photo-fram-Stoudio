package com.photoframe.studio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photoframe.studio.data.EdgeRule
import com.photoframe.studio.data.ExportFormat
import com.photoframe.studio.data.FrameAdjustments
import com.photoframe.studio.viewmodel.StudioViewModel

private val canvasTop = Color(0xFF172B47)
private val canvasBottom = Color(0xFF080F1C)
private val glass = Color(0xD91A2B42)

@Composable
fun PhotoFrameApp(vm: StudioViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val imagesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addImages(it) }
    val sourceFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { if (it != null) vm.scanFolder(it) }
    val outputPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { if (it != null) vm.setOutputFolder(it) }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF91D0FF), secondary = Color(0xFFC3B5FF),
        background = canvasBottom, surface = Color(0xFF17283E),
        onSurface = Color(0xFFF2F6FC), onBackground = Color(0xFFF2F6FC)
    )) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(canvasTop, canvasBottom, Color(0xFF101A2B))))) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 42.dp, bottom = 34.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("PhotoFrame Studio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text("Professional Batch Image Preparation", color = Color(0xFF9EB4D0), style = MaterialTheme.typography.labelSmall)
                            }
                            Surface(color = Color(0x338CCBFF), shape = RoundedCornerShape(18.dp), modifier = Modifier.size(54.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFF91D0FF), modifier = Modifier.size(29.dp)) }
                            }
                        }
                    }
                    item {
                        GlassCard {
                            SectionHeading("ورودی تصاویر", Icons.Rounded.PhotoLibrary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { imagesPicker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(5.dp)); Text("انتخاب تصاویر")
                                }
                                OutlinedButton(onClick = { sourceFolderPicker.launch(null) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(5.dp)); Text("انتخاب پوشه")
                                }
                            }
                            Text("${state.images.size} تصویر انتخاب شده", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            if (state.images.isNotEmpty()) {
                                TextButton(onClick = vm::clearImages) { Text("پاک کردن فهرست") }
                                state.images.take(5).forEach { image ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Rounded.Image, null, tint = Color(0xFF8CCBFF), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(7.dp))
                                        Text(image.name, maxLines = 1, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                        Text(if (image.bytes > 0) "${image.bytes / 1024} KB" else "", color = Color(0xFF94A8C2), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                if (state.images.size > 5) Text("و ${state.images.size - 5} تصویر دیگر", color = Color(0xFF94A8C2), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    item {
                        GlassCard {
                            SectionHeading("محل ذخیره خروجی", Icons.Rounded.FolderSpecial)
                            Text(if (state.outputFolder == null) "پوشه مقصد انتخاب نشده" else "پوشه مقصد آماده است", color = Color(0xFFB6C7DB))
                            OutlinedButton(onClick = { outputPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Folder, null); Spacer(Modifier.width(7.dp)); Text("انتخاب پوشه خروجی")
                            }
                        }
                    }
                    item {
                        GlassCard {
                            SectionHeading("تنظیمات چهارطرفه", Icons.Rounded.Tune)
                            Text("مقادیر برحسب پیکسل تصویر اصلی هستند. برش به داخل و حاشیه به بیرون مستقل تنظیم می‌شوند.", color = Color(0xFF9EB4D0), style = MaterialTheme.typography.bodySmall)
                            EdgeEditor("لبه بالا", state.adjustments.top) { vm.updateAdjustments(state.adjustments.copy(top = it)) }
                            EdgeEditor("لبه پایین", state.adjustments.bottom) { vm.updateAdjustments(state.adjustments.copy(bottom = it)) }
                            EdgeEditor("لبه چپ", state.adjustments.left) { vm.updateAdjustments(state.adjustments.copy(left = it)) }
                            EdgeEditor("لبه راست", state.adjustments.right) { vm.updateAdjustments(state.adjustments.copy(right = it)) }
                        }
                    }
                    item {
                        GlassCard {
                            SectionHeading("فرمت خروجی", Icons.Rounded.Image)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(selected = state.adjustments.format == ExportFormat.PNG, onClick = { vm.updateAdjustments(state.adjustments.copy(format = ExportFormat.PNG)) }, label = { Text("PNG") })
                                Spacer(Modifier.width(8.dp))
                                FilterChip(selected = state.adjustments.format == ExportFormat.JPEG, onClick = { vm.updateAdjustments(state.adjustments.copy(format = ExportFormat.JPEG)) }, label = { Text("JPEG") })
                            }
                            if (state.adjustments.format == ExportFormat.JPEG) {
                                Text("کیفیت JPEG: ${state.adjustments.jpegQuality}%")
                                Slider(value = state.adjustments.jpegQuality.toFloat(), onValueChange = { vm.updateAdjustments(state.adjustments.copy(jpegQuality = it.toInt())) }, valueRange = 1f..100f)
                            }
                        }
                    }
                    if (state.busy || state.report != null) item {
                        GlassCard {
                            SectionHeading(if (state.busy) "پردازش گروهی" else "گزارش پردازش", Icons.Rounded.AutoGraph)
                            if (state.busy) {
                                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                                Text("${state.completed} از ${state.total} • ${state.currentName}", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = vm::togglePause) { Text(if (state.paused) "ادامه" else "مکث") }
                                    TextButton(onClick = vm::stopBatch) { Text("توقف") }
                                }
                            } else state.report?.let { report ->
                                Text("موفق: ${report.succeeded} از ${report.total}", color = Color(0xFF9FE0BD))
                                Text("ناموفق: ${report.failed.size}", color = if (report.failed.isEmpty()) Color(0xFF9FE0BD) else MaterialTheme.colorScheme.error)
                                report.failed.take(8).forEach { Text("• ${it.name}: ${it.reason}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                    if (state.notice != null) item { Text(state.notice!!, color = Color(0xFF9DD2FF), style = MaterialTheme.typography.bodySmall) }
                    item {
                        Button(onClick = vm::startBatch, enabled = !state.busy && state.images.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("شروع پردازش گروهی", fontWeight = FontWeight.Bold)
                        }
                    }
                    item { Text("PhotoFrame Studio  •  v0.2.0", color = Color(0xFF7186A3), style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = glass,
        tonalElevation = 3.dp,
        shadowElevation = 9.dp
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content) }
}

@Composable
private fun SectionHeading(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFF91D0FF))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EdgeEditor(title: String, value: EdgeRule, onChange: (EdgeRule) -> Unit) {
    var crop by remember(value.cropPx) { mutableStateOf(value.cropPx.toString()) }
    var extend by remember(value.extendPx) { mutableStateOf(value.extendPx.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = crop, onValueChange = { next ->
                    crop = next.filter(Char::isDigit).take(5)
                    onChange(value.copy(cropPx = crop.toIntOrNull() ?: 0))
                }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("برش داخل (px)") }
            )
            OutlinedTextField(
                value = extend, onValueChange = { next ->
                    extend = next.filter(Char::isDigit).take(5)
                    onChange(value.copy(extendPx = extend.toIntOrNull() ?: 0))
                }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("حاشیه بیرون (px)") }
            )
        }
    }
}
