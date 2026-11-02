package com.tvtranslate.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvtranslate.app.AppViewModel
import com.tvtranslate.app.Settings
import com.tvtranslate.app.queryDisplayName

@Composable
fun HomeScreen(vm: AppViewModel) {
    val context = LocalContext.current

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            vm.setVideo(uri, queryDisplayName(context, uri))
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            vm.setSubtitle(uri, queryDisplayName(context, uri))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("剧译", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    "本地视频 · 没有字幕也能看",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { vm.openSettings() }) { Text("设置") }
        }

        Spacer(Modifier.height(20.dp))

        SectionCard("1 · 选择视频") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { videoPicker.launch(arrayOf("video/*")) }) {
                    Text("选择视频")
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    vm.videoName.ifBlank { "未选择" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("2 · 字幕文件（可选）") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { subtitlePicker.launch(arrayOf("*/*")) }) {
                    Text("选择字幕")
                }
                Spacer(Modifier.width(8.dp))
                if (vm.subtitleUri != null) {
                    TextButton(onClick = { vm.clearSubtitle() }) { Text("清除") }
                }
            }
            if (vm.subtitleName.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    vm.subtitleName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "有 SRT/ASS 字幕就直接翻译，跳过语音识别；没有就留空，App 会自己听。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("3 · 语言") {
            Text("译成", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Settings.TARGET_LABELS.forEach { lang ->
                    if (vm.settings.targetLang == lang) {
                        Button(onClick = { vm.updateSettings(vm.settings.copy(targetLang = lang)) }) {
                            Text(lang)
                        }
                    } else {
                        OutlinedButton(onClick = { vm.updateSettings(vm.settings.copy(targetLang = lang)) }) {
                            Text(lang)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("原声语言", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Settings.SOURCE_LABELS.forEach { (code, label) ->
                    if (vm.settings.sourceHint == code) {
                        Button(onClick = { vm.updateSettings(vm.settings.copy(sourceHint = code)) }) {
                            Text(label, fontSize = 13.sp)
                        }
                    } else {
                        OutlinedButton(onClick = { vm.updateSettings(vm.settings.copy(sourceHint = code)) }) {
                            Text(label, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (!vm.settings.asrReady || !vm.settings.mtReady) {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("还没配置 API", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "没有字幕的视频需要语音识别 Key，翻译还需要一个模型 Key。点右上角「设置」填入。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { vm.openSettings() }) { Text("去设置") }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { vm.start() },
            enabled = vm.videoUri != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("开始生成字幕", fontSize = 16.sp)
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "流程：抽音频 → 语音识别 → 翻译 → 生成字幕。45 分钟一集约 1–5 分钟。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
