package com.photoframe.studio.ui

import android.net.Uri
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

private val bg = Color(0xFF0A1220)
private val panel = Color(0xCC1B2B43)

@Composable
fun PhotoFrameApp() {
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var output by remember { mutableStateOf<Uri?>(null) }
    var status by remember { mutableStateOf("برای شروع، تصاویر یا یک پوشه را انتخاب کن.") }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        images = (images + it).distinct()
        status = "${images.size} تصویر انتخاب شده است."
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        if (it != null) {
            output = it
            status = "پوشه انتخاب شد. در نسخه بعدی اسکن زیرپوشه‌ها فعال می‌شود."
        }
    }
    val outputPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        if (it != null) { output = it; status = "محل خروجی انتخاب شد." }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF8CCBFF), secondary = Color(0xFFB8A6FF),
        background = bg, surface = Color(0xFF17263B), onSurface = Color(0xFFF1F5FC)
    )) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF182B48), bg, Color(0xFF101B2D))))) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(18.dp, 42.dp, 18.dp, 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("PhotoFrame Studio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text("Professional Batch Image Preparation", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9EB1CB))
                            }
                            Surface(color = Color(0x338CCBFF), shape = RoundedCornerShape(18.dp), modifier = Modifier.size(54.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFF8CCBFF), modifier = Modifier.size(30.dp)) }
                            }
                        }
                    }
                    item {
                        Panel {
                            Text("پروژه جدید", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("انتخاب تصاویر")
                                }
                                OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("انتخاب پوشه")
                                }
                            }
                            Text("${images.size} تصویر انتخاب شده", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    item {
                        Panel {
                            Text("پوشه خروجی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(if (output == null) "هنوز انتخاب نشده" else "پوشه مقصد انتخاب شده", color = Color(0xFF9EB1CB))
                            OutlinedButton(onClick = { outputPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Folder, null); Spacer(Modifier.width(8.dp)); Text("انتخاب محل ذخیره")
                            }
                        }
                    }
                    item {
                        Panel {
                            Text("وضعیت پروژه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(status, color = Color(0xFFB7C5D9))
                            if (images.isNotEmpty()) TextButton(onClick = { images = emptyList(); status = "فهرست تصاویر پاک شد." }) { Text("پاک‌کردن فهرست") }
                        }
                    }
                    item {
                        Panel {
                            Text("مراحل بعدی توسعه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("موتور پردازش، تنظیم مستقل چهار لبه، پیش‌نمایش و پردازش گروهی در حال پیاده‌سازی هستند.", color = Color(0xFFB7C5D9))
                        }
                    }
                    item { Text("نسخه توسعه  •  1.0.0", color = Color(0xFF7F93AE), modifier = Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = panel,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) { Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content) }
}
