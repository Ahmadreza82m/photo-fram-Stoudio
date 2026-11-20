package com.photoframe.studio.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photoframe.studio.data.*
import com.photoframe.studio.processing.BitmapFrameEngine
import kotlinx.coroutines.CancellationException
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

    fun setOutputFolder(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        _state.value = _state.value.copy(outputFolder = uri, notice = "پوشه خروجی انتخاب شد.")
    }

    fun updateAdjustments(value: FrameAdjustments) { _state.value = _state.value.copy(adjustments = value.safe()) }
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
                try {
                    val target = repository.createUniqueOutput(destination, image.name, snapshot.adjustments.format)
                    engine.process(image.uri, target, snapshot.adjustments)
                    succeeded++
                } catch (e: Exception) {
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
