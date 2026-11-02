package com.tvtranslate.app.ui

import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tvtranslate.app.AppViewModel
import com.tvtranslate.app.formatClock
import com.tvtranslate.app.pipeline.CueStore
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val videoView = remember { VideoView(context) }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var bilingual by remember { mutableStateOf(true) }
    var fontSize by remember { mutableFloatStateOf(18f) }
    var offsetMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(vm.videoUri) {
        val uri = vm.videoUri ?: return@LaunchedEffect
        try {
            videoView.setOnPreparedListener { mp ->
                duration = mp.duration.toLong()
                mp.start()
            }
            videoView.setOnCompletionListener { playing = false }
            videoView.setOnErrorListener { _, what, extra ->
                Toast.makeText(
                    context,
                    "视频无法播放（$what/$extra）。系统解码器可能不支持这个封装或编码，试试 MP4/H.264。",
                    Toast.LENGTH_LONG
                ).show()
                true
            }
            videoView.setVideoURI(uri)
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                position = videoView.currentPosition.toLong()
                playing = videoView.isPlaying
                val d = videoView.duration.toLong()
                if (d > 0) duration = d
            } catch (_: Exception) {
            }
            delay(80)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                videoView.stopPlayback()
            } catch (_: Exception) {
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(CueStore.toSrt(vm.cues).toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "字幕已导出", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler {
        try {
            videoView.stopPlayback()
        } catch (_: Exception) {
        }
        vm.backHome()
    }

    val active = vm.cues.filter {
        position + offsetMs >= it.startMs && position + offsetMs < it.endMs
    }
    val maxDuration = duration.coerceAtLeast(1L).toFloat()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(factory = { videoView }, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            active.takeLast(2).forEach { cue ->
                if (bilingual && cue.translation.isNotBlank()) {
                    Text(
                        cue.text,
                        color = Color(0xFFE0E0E0),
                        fontSize = (fontSize * 0.64f).sp,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            shadow = Shadow(Color.Black, Offset(2f, 2f), 6f)
                        )
                    )
                }
                Text(
                    cue.display,
                    color = Color.White,
                    fontSize = fontSize.sp,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        shadow = Shadow(Color.Black, Offset(2f, 2f), 8f)
                    )
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Slider(
                value = position.toFloat().coerceIn(0f, maxDuration),
                onValueChange = { videoView.seekTo(it.toInt()) },
                valueRange = 0f..maxDuration
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    if (videoView.isPlaying) videoView.pause() else videoView.start()
                }) {
                    Text(if (playing) "暂停" else "播放", color = Color.White, fontSize = 13.sp)
                }
                Text(
                    "${formatClock(position)} / ${formatClock(duration)}",
                    color = Color.White,
                    fontSize = 12.sp
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { bilingual = !bilingual }) {
                    Text(if (bilingual) "双语" else "译文", color = Color.White, fontSize = 12.sp)
                }
                TextButton(onClick = {
                    exportLauncher.launch(exportName(vm.videoName))
                }) {
                    Text("导出", color = Color.White, fontSize = 12.sp)
                }
                TextButton(onClick = {
                    try {
                        videoView.stopPlayback()
                    } catch (_: Exception) {
                    }
                    vm.backHome()
                }) {
                    Text("返回", color = Color.White, fontSize = 12.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { fontSize = (fontSize - 1f).coerceAtLeast(12f) }) {
                    Text("A-", color = Color.White, fontSize = 12.sp)
                }
                TextButton(onClick = { fontSize = (fontSize + 1f).coerceAtMost(40f) }) {
                    Text("A+", color = Color.White, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { offsetMs -= 300 }) {
                    Text("-0.3s", color = Color.White, fontSize = 11.sp)
                }
                Text("${offsetMs}ms", color = Color.White, fontSize = 11.sp)
                TextButton(onClick = { offsetMs += 300 }) {
                    Text("+0.3s", color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun exportName(videoName: String): String {
    val base = videoName.substringBeforeLast('.', videoName)
    return "$base.srt"
}
