package com.tvtranslate.app

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tvtranslate.app.model.Cue
import com.tvtranslate.app.pipeline.Pipeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, SETTINGS, PROCESSING, PLAYER }

class AppViewModel(app: Application) : AndroidViewModel(app) {

    var screen by mutableStateOf(Screen.HOME)
        private set

    var settings by mutableStateOf(Settings.load(app))
        private set

    var videoUri by mutableStateOf<Uri?>(null)
        private set
    var videoName by mutableStateOf("")
        private set

    var subtitleUri by mutableStateOf<Uri?>(null)
        private set
    var subtitleName by mutableStateOf("")
        private set

    var cues by mutableStateOf<List<Cue>>(emptyList())
        private set

    var progress by mutableStateOf(0f)
        private set
    var stage by mutableStateOf("")
        private set
    var logs by mutableStateOf<List<String>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var running by mutableStateOf(false)
        private set

    private var job: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun ui(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    fun setVideo(uri: Uri, name: String) {
        videoUri = uri
        videoName = name
    }

    fun setSubtitle(uri: Uri?, name: String) {
        subtitleUri = uri
        subtitleName = name
    }

    fun clearSubtitle() {
        subtitleUri = null
        subtitleName = ""
    }

    fun openSettings() {
        screen = Screen.SETTINGS
    }

    fun backHome() {
        screen = Screen.HOME
        error = null
    }

    fun updateSettings(s: Settings) {
        settings = s
        s.save(getApplication())
    }

    fun start() {
        val video = videoUri ?: return
        if (running) return

        running = true
        error = null
        logs = emptyList()
        progress = 0f
        stage = "准备中"
        screen = Screen.PROCESSING

        job = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    Pipeline(getApplication()).run(
                        videoUri = video,
                        videoName = videoName,
                        subtitleUri = subtitleUri,
                        subtitleName = subtitleName,
                        settings = settings,
                        onStage = { s, p ->
                            ui {
                                stage = s
                                progress = p.coerceIn(0f, 1f)
                            }
                        },
                        onLog = { msg ->
                            ui { logs = (logs + msg).takeLast(200) }
                        },
                        shouldStop = { !isActive }
                    )
                }
                ui {
                    cues = result
                    running = false
                    screen = Screen.PLAYER
                }
            } catch (e: CancellationException) {
                ui {
                    running = false
                    stage = "已取消"
                    screen = Screen.HOME
                }
            } catch (e: Exception) {
                ui {
                    running = false
                    stage = "失败"
                    error = e.message ?: e.javaClass.simpleName
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        ui {
            running = false
            stage = "已取消"
            screen = Screen.HOME
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
