package com.photoframe.studio.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photoframe.studio.data.*
import com.photoframe.studio.processing.BitmapFrameEngine
import com.photoframe.studio.processing.ReferenceFrameDetector
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class StudioViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = SafImageRepository(app)
    private val _state = MutableStateFlow(StudioState())
    val state: StateFlow<StudioState> = _state.asStateFlow()
    private var job: Job? = null
    private val pauseRequested = AtomicBoolean(false)

    init { _state.value = _state.value.copy(adjustments = loadAdjustments(), savedProjects = loadProjects()) }

    fun setProjectName(value: String) { _state.value = _state.value.copy(projectName = value) }

    fun saveCurrentProject() {
        val current = _state.value
        if (current.images.isEmpty()) { _state.value = current.copy(notice = "برای ذخیره پروژه ابتدا تصاویر را انتخاب کن."); return }
        val project = SavedProject(UUID.randomUUID().toString(), current.projectName.trim().ifBlank { "پروژه بدون نام" }, current.images, current.outputFolder, current.adjustments, current.referenceUri)
        val updated = current.savedProjects + project
        _state.value = current.copy(savedProjects = updated, notice = "پروژه «${project.name}» ذخیره شد.")
        persistProjects(updated)
    }

    fun openSavedProject(id: String) {
        val project = _state.value.savedProjects.firstOrNull { it.id == id } ?: return
        project.images.forEach { image -> runCatching { getApplication<Application>().contentResolver.takePersistableUriPermission(image.uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        project.outputFolder?.let { uri -> runCatching { getApplication<Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        project.referenceUri?.let { uri -> runCatching { getApplication<Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        _state.value = _state.value.copy(projectName = project.name, images = project.images, outputFolder = project.outputFolder, adjustments = project.adjustments, referenceUri = project.referenceUri, referenceName = project.referenceUri?.let { runCatching { repository.imageInfo(it).name }.getOrNull() }, report = null, notice = "پروژه «${project.name}» باز شد.")
        saveAdjustments(project.adjustments)
    }

    private fun persistProjects(projects: List<SavedProject>) {
        val array = JSONArray()
        projects.forEach { project ->
            val obj = JSONObject().put("id", project.id).put("name", project.name)
                .put("output", project.outputFolder?.toString()).put("reference", project.referenceUri?.toString())
                .put("topCrop", project.adjustments.top.cropPx).put("topExtend", project.adjustments.top.extendPx).put("topCf", project.adjustments.top.cropFraction ?: JSONObject.NULL).put("topEf", project.adjustments.top.extendFraction ?: JSONObject.NULL)
                .put("bottomCrop", project.adjustments.bottom.cropPx).put("bottomExtend", project.adjustments.bottom.extendPx).put("bottomCf", project.adjustments.bottom.cropFraction ?: JSONObject.NULL).put("bottomEf", project.adjustments.bottom.extendFraction ?: JSONObject.NULL)
                .put("leftCrop", project.adjustments.left.cropPx).put("leftExtend", project.adjustments.left.extendPx).put("leftCf", project.adjustments.left.cropFraction ?: JSONObject.NULL).put("leftEf", project.adjustments.left.extendFraction ?: JSONObject.NULL)
                .put("rightCrop", project.adjustments.right.cropPx).put("rightExtend", project.adjustments.right.extendPx).put("rightCf", project.adjustments.right.cropFraction ?: JSONObject.NULL).put("rightEf", project.adjustments.right.extendFraction ?: JSONObject.NULL)
                .put("format", project.adjustments.format.ordinal).put("quality", project.adjustments.jpegQuality)
            val images = JSONArray()
            project.images.forEach { images.put(JSONObject().put("uri", it.uri.toString()).put("name", it.name).put("bytes", it.bytes)) }
            obj.put("images", images)
            array.put(obj)
        }
        getApplication<Application>().getSharedPreferences("studio_projects", 0).edit().putString("projects", array.toString()).apply()
    }

    private fun loadProjects(): List<SavedProject> = runCatching {
        val raw = getApplication<Application>().getSharedPreferences("studio_projects", 0).getString("projects", "[]") ?: "[]"
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.getJSONObject(index)
            val imagesJson = obj.optJSONArray("images") ?: JSONArray()
            val images = (0 until imagesJson.length()).mapNotNull { i -> runCatching {
                val item = imagesJson.getJSONObject(i)
                SelectedImage(Uri.parse(item.getString("uri")), item.optString("name", "image"), item.optLong("bytes", 0L))
            }.getOrNull() }
            val a = FrameAdjustments(
                top = EdgeRule(obj.optInt("topCrop"), obj.optInt("topExtend"), obj.optDouble("topCf", -1.0).takeIf { it >= 0.0 }?.toFloat(), obj.optDouble("topEf", -1.0).takeIf { it >= 0.0 }?.toFloat()),
                bottom = EdgeRule(obj.optInt("bottomCrop"), obj.optInt("bottomExtend"), obj.optDouble("bottomCf", -1.0).takeIf { it >= 0.0 }?.toFloat(), obj.optDouble("bottomEf", -1.0).takeIf { it >= 0.0 }?.toFloat()),
                left = EdgeRule(obj.optInt("leftCrop"), obj.optInt("leftExtend"), obj.optDouble("leftCf", -1.0).takeIf { it >= 0.0 }?.toFloat(), obj.optDouble("leftEf", -1.0).takeIf { it >= 0.0 }?.toFloat()),
                right = EdgeRule(obj.optInt("rightCrop"), obj.optInt("rightExtend"), obj.optDouble("rightCf", -1.0).takeIf { it >= 0.0 }?.toFloat(), obj.optDouble("rightEf", -1.0).takeIf { it >= 0.0 }?.toFloat()),
                format = ExportFormat.values().getOrElse(obj.optInt("format")) { ExportFormat.PNG },
                jpegQuality = obj.optInt("quality", 96)
            )
            SavedProject(obj.getString("id"), obj.optString("name", "پروژه"), images, obj.optString("output").takeIf { it.isNotBlank() && it != "null" }?.let(Uri::parse), a.safe(), obj.optString("reference").takeIf { it.isNotBlank() && it != "null" }?.let(Uri::parse))
        }
    }.getOrDefault(emptyList())

    fun deleteSavedProject(id: String) {
        val updated = _state.value.savedProjects.filterNot { it.id == id }
        _state.value = _state.value.copy(savedProjects = updated, notice = "پروژه حذف شد.")
        persistProjects(updated)
    }

    fun addImages(uris: List<Uri>) {
        uris.forEach { uri -> runCatching { getApplication<Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        val merged = (_state.value.images + uris.mapNotNull { runCatching { repository.imageInfo(it) }.getOrNull() }).distinctBy { it.uri }
        _state.value = _state.value.copy(images = merged, report = null, notice = "${merged.size} تصویر در فهرست قرار دارد.")
    }

    fun scanFolder(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val found = runCatching { repository.scanRecursively(uri) }
            if (found.isSuccess) {
                val merged = (_state.value.images + found.getOrThrow()).distinctBy { it.uri }
                _state.value = _state.value.copy(images = merged, notice = "${found.getOrThrow().size} تصویر از پوشه و زیرپوشه‌ها پیدا شد.")
            } else _state.value = _state.value.copy(notice = "خواندن پوشه ناموفق بود: ${found.exceptionOrNull()?.message}")
        }
    }

    fun setReference(uri: Uri) {
        runCatching { getApplication<Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = runCatching { repository.imageInfo(uri).name }.getOrDefault("reference")
        _state.value = _state.value.copy(referenceUri = uri, referenceName = name, detectedFrame = null, detectionMessage = "تصویر مرجع انتخاب شد؛ حالا تشخیص کادر را اجرا کن.")
    }

    fun detectReferenceFrame() {
        val uri = _state.value.referenceUri ?: run {
            _state.value = _state.value.copy(detectionMessage = "ابتدا تصویر مرجع را انتخاب کن.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val originalW = bounds.outWidth
                val originalH = bounds.outHeight
                if (originalW <= 0 || originalH <= 0) error("تصویر مرجع معتبر نیست")
                var sample = 1
                while (originalW / (sample * 2) > 1800 || originalH / (sample * 2) > 1800) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: error("خواندن تصویر مرجع ناموفق بود")
                try {
                    val candidate = ReferenceFrameDetector.detect(bitmap) ?: error("کادر واضحی پیدا نشد؛ مختصات را دستی تنظیم کن.")
                    val scaleX = originalW.toFloat() / bitmap.width
                    val scaleY = originalH.toFloat() / bitmap.height
                    val mapped = candidate.copy(
                        left = (candidate.left * scaleX).toInt(),
                        top = (candidate.top * scaleY).toInt(),
                        right = (candidate.right * scaleX).toInt(),
                        bottom = (candidate.bottom * scaleY).toInt(),
                        imageWidth = originalW,
                        imageHeight = originalH
                    )
                    _state.value = _state.value.copy(detectedFrame = mapped, detectionMessage = "کادر پیشنهادی پیدا شد. قبل از اعمال، نتیجه را بررسی کن.")
                } finally { bitmap.recycle() }
            } catch (e: Exception) {
                _state.value = _state.value.copy(detectedFrame = null, detectionMessage = e.message ?: "تشخیص کادر ناموفق بود.")
            }
        }
    }

    fun applyDetectedFrame() {
        val frame = _state.value.detectedFrame ?: return
        val old = _state.value.adjustments
        updateAdjustments(old.copy(
            left = old.left.copy(cropPx = frame.left, cropFraction = frame.left.toFloat() / frame.imageWidth),
            top = old.top.copy(cropPx = frame.top, cropFraction = frame.top.toFloat() / frame.imageHeight),
            right = old.right.copy(cropPx = (frame.imageWidth - frame.right).coerceAtLeast(0), cropFraction = (frame.imageWidth - frame.right).toFloat() / frame.imageWidth),
            bottom = old.bottom.copy(cropPx = (frame.imageHeight - frame.bottom).coerceAtLeast(0), cropFraction = (frame.imageHeight - frame.bottom).toFloat() / frame.imageHeight)
        ))
        _state.value = _state.value.copy(detectionMessage = "مختصات پیشنهادی اعمال شد؛ مقادیر چهارطرفه را بازبینی کن.")
    }

    fun setOutputFolder(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        _state.value = _state.value.copy(outputFolder = uri, notice = "پوشه خروجی انتخاب شد.")
    }

    fun updateAdjustments(value: FrameAdjustments) {
        val safe = value.safe()
        _state.value = _state.value.copy(adjustments = safe)
        saveAdjustments(safe)
    }

    fun retryFailed() {
        val failedNames = _state.value.report?.failed?.map { it.name }?.toSet().orEmpty()
        if (failedNames.isEmpty()) return
        val failedItems = _state.value.images.filter { it.name in failedNames }
        _state.value = _state.value.copy(images = failedItems, report = null, notice = "${failedItems.size} فایل ناموفق برای تلاش مجدد انتخاب شد.")
    }

    private fun saveAdjustments(a: FrameAdjustments) {
        getApplication<Application>().getSharedPreferences("studio_settings", 0).edit()
            .putInt("top_crop", a.top.cropPx).putInt("top_extend", a.top.extendPx).putFloat("top_cf", a.top.cropFraction ?: -1f).putFloat("top_ef", a.top.extendFraction ?: -1f)
            .putInt("bottom_crop", a.bottom.cropPx).putInt("bottom_extend", a.bottom.extendPx).putFloat("bottom_cf", a.bottom.cropFraction ?: -1f).putFloat("bottom_ef", a.bottom.extendFraction ?: -1f)
            .putInt("left_crop", a.left.cropPx).putInt("left_extend", a.left.extendPx).putFloat("left_cf", a.left.cropFraction ?: -1f).putFloat("left_ef", a.left.extendFraction ?: -1f)
            .putInt("right_crop", a.right.cropPx).putInt("right_extend", a.right.extendPx).putFloat("right_cf", a.right.cropFraction ?: -1f).putFloat("right_ef", a.right.extendFraction ?: -1f)
            .putInt("format", a.format.ordinal).putInt("quality", a.jpegQuality).putLong("fill", a.fillColor).apply()
    }

    private fun loadAdjustments(): FrameAdjustments {
        val p = getApplication<Application>().getSharedPreferences("studio_settings", 0)
        fun edge(side: String) = EdgeRule(p.getInt("${side}_crop", 0), p.getInt("${side}_extend", 0), p.getFloat("${side}_cf", -1f).takeIf { it >= 0f }, p.getFloat("${side}_ef", -1f).takeIf { it >= 0f })
        return FrameAdjustments(
            top = edge("top"), bottom = edge("bottom"), left = edge("left"), right = edge("right"),
            format = ExportFormat.values().getOrElse(p.getInt("format", 0)) { ExportFormat.PNG },
            jpegQuality = p.getInt("quality", 96), fillColor = p.getLong("fill", 0xFFFFFFFF)
        ).safe()
    }

    fun clearImages() { _state.value = _state.value.copy(images = emptyList(), report = null, notice = "فهرست تصاویر پاک شد.") }

    fun togglePause() {
        pauseRequested.set(!pauseRequested.get())
        _state.value = _state.value.copy(paused = pauseRequested.get())
    }

    fun startBatch() {
        val snapshot = _state.value
        val destination = snapshot.outputFolder
        if (snapshot.images.isEmpty()) { _state.value = snapshot.copy(notice = "ابتدا تصویر انتخاب کن."); return }
        if (destination == null) { _state.value = snapshot.copy(notice = "ابتدا پوشه خروجی را انتخاب کن."); return }
        if (snapshot.busy) return
        pauseRequested.set(false)
        _state.value = snapshot.copy(busy = true, paused = false, completed = 0, total = snapshot.images.size, report = null, notice = null)
        job = viewModelScope.launch(Dispatchers.IO) {
            val engine = BitmapFrameEngine(getApplication<Application>().contentResolver)
            var succeeded = 0
            val failures = mutableListOf<FailedImage>()
            for ((index, image) in snapshot.images.withIndex()) {
                while (pauseRequested.get() && isActive) delay(120)
                if (!isActive) break
                _state.value = _state.value.copy(currentName = image.name)
                var target: Uri? = null
                try {
                    target = repository.createUniqueOutput(destination, image.name, snapshot.adjustments.format)
                    engine.process(image.uri, target, snapshot.adjustments)
                    succeeded++
                } catch (e: Exception) {
                    target?.let { repository.deleteOutput(it) }
                    failures += FailedImage(image.name, e.message ?: "خطای ناشناخته")
                }
                _state.value = _state.value.copy(completed = index + 1)
            }
            if (isActive) {
                _state.value = _state.value.copy(
                    busy = false, paused = false, currentName = "",
                    report = BatchReport(snapshot.images.size, succeeded, failures),
                    notice = "پردازش تمام شد."
                )
            }
        }
    }

    fun stopBatch() {
        job?.cancel()
        pauseRequested.set(false)
        _state.value = _state.value.copy(busy = false, paused = false, currentName = "", notice = "پردازش توسط کاربر متوقف شد.")
    }
}
