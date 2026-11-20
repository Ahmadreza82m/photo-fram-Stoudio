package com.photoframe.studio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.photoframe.studio.processing.BitmapFrameEngine
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
import com.photoframe.studio.viewmodel.StudioViewModel
import com.photoframe.studio.data.DetectedFrame

private val canvasTop = Color(0xFF172B47)
private val canvasBottom = Color(0xFF080F1C)
private val glass = Color(0xD91A2B42)

@Composable
fun PhotoFrameApp(vm: StudioViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var pendingDeleteProject by remember { mutableStateOf<String?>(null) }
    val referencePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) vm.setReference(it) }
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
                            SectionHeading("پروژه و سوابق", Icons.Rounded.FolderCopy)
                            OutlinedTextField(value = state.projectName, onValueChange = vm::setProjectName, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("نام پروژه") })
                            OutlinedButton(onClick = vm::saveCurrentProject, enabled = state.images.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(7.dp)); Text("ذخیره پروژه فعلی")
                            }
                            if (state.savedProjects.isNotEmpty()) {
                                Text("پروژه‌های ذخیره‌شده", fontWeight = FontWeight.SemiBold)
                                state.savedProjects.takeLast(5).reversed().forEach { project ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(project.name, fontWeight = FontWeight.Medium)
                                            Text(project.images.size.toString() + " تصویر", color = Color(0xFF9EB4D0), style = MaterialTheme.typography.labelSmall)
                                        }
                                        TextButton(onClick = { vm.openSavedProject(project.id) }) { Text("باز کردن") }
                                        TextButton(onClick = { pendingDeleteProject = project.id }) { Text("حذف") }
                                    }
                                }
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
                    if (state.images.isNotEmpty()) item {
                        PreviewCard(state.images.first().uri, state.adjustments)
                    }
                    item {
                        GlassCard {
                            SectionHeading("الگوی مرجع و تشخیص کادر", Icons.Rounded.CropFree)
                            Text(state.referenceName ?: "تصویر نمونه‌ای برای تعیین محدوده کادر انتخاب نشده است.", color = Color(0xFFB6C7DB), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { referencePicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.ImageSearch, null); Spacer(Modifier.width(7.dp)); Text("انتخاب تصویر مرجع")
                            }
                            state.referenceUri?.let { reference ->
                                ReferenceFrameEditor(
                                    uri = reference,
                                    frame = state.detectedFrame,
                                    onFrameChange = vm::setManualReferenceFrame
                                )
                            }
                            Button(onClick = vm::detectReferenceFrame, enabled = state.referenceUri != null, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.AutoFixHigh, null); Spacer(Modifier.width(7.dp)); Text("تشخیص خودکار کادر با OpenCV")
                            }
                            state.detectedFrame?.let { frame ->
                                Text("چپ: " + frame.left + " • بالا: " + frame.top + " • راست: " + frame.right + " • پایین: " + frame.bottom, style = MaterialTheme.typography.bodySmall)
                                Text("کادر پیشنهادی پیدا شد؛ تشخیص ممکن است اشتباه باشد.", color = Color(0xFF9EB4D0), style = MaterialTheme.typography.labelSmall)
                                OutlinedButton(onClick = vm::applyDetectedFrame, modifier = Modifier.fillMaxWidth()) { Text("اعمال مختصات پیشنهادی") }
                            }
                            state.detectionMessage?.let { Text(it, color = Color(0xFF9DD2FF), style = MaterialTheme.typography.bodySmall) }
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
                                if (report.failed.isNotEmpty()) OutlinedButton(onClick = vm::retryFailed) { Text("فقط موارد ناموفق را دوباره پردازش کن") }
                            }
                        }
                    }
                    if (state.notice != null) item { Text(state.notice!!, color = Color(0xFF9DD2FF), style = MaterialTheme.typography.bodySmall) }
                    item {
                        Button(onClick = vm::startBatch, enabled = !state.busy && state.images.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("شروع پردازش گروهی", fontWeight = FontWeight.Bold)
                        }
                    }
                    item { Text("PhotoFrame Studio  •  v0.3.0", color = Color(0xFF7186A3), style = MaterialTheme.typography.labelSmall) }
                }
            }
            pendingDeleteProject?.let { projectId ->
                val projectName = state.savedProjects.firstOrNull { it.id == projectId }?.name.orEmpty()
                AlertDialog(
                    onDismissRequest = { pendingDeleteProject = null },
                    title = { Text("حذف پروژه؟") },
                    text = { Text("پروژه «$projectName» از فهرست پروژه‌های ذخیره‌شده حذف می‌شود.") },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.deleteSavedProject(projectId)
                            pendingDeleteProject = null
                        }) { Text("حذف") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteProject = null }) { Text("انصراف") }
                    }
                )
            }
        }
    }
}


@Composable
private fun PreviewCard(uri: android.net.Uri, settings: com.photoframe.studio.data.FrameAdjustments) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pair by produceState<Pair<Bitmap, Bitmap>?>(initialValue = null, uri, settings) {
        value?.first?.recycle()
        value?.second?.recycle()
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFrameEngine(context.contentResolver).previewPair(uri, settings) }.getOrNull()
        }
    }
    GlassCard {
        SectionHeading("پیش‌نمایش قبل و بعد", Icons.Rounded.Compare)
        if (pair == null) {
            Text("برای این تصویر با تنظیمات فعلی پیش‌نمایش در دسترس نیست.", color = Color(0xFF9EB4D0), style = MaterialTheme.typography.bodySmall)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("قبل", color = Color(0xFFB6C7DB), style = MaterialTheme.typography.labelMedium)
                    Image(pair!!.first.asImageBitmap(), null, modifier = Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Fit)
                }
                Column(Modifier.weight(1f)) {
                    Text("بعد", color = Color(0xFF91D0FF), style = MaterialTheme.typography.labelMedium)
                    Image(pair!!.second.asImageBitmap(), null, modifier = Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Fit)
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
