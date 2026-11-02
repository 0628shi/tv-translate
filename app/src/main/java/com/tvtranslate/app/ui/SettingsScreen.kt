package com.tvtranslate.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvtranslate.app.AppViewModel
import com.tvtranslate.app.Settings

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var draft by remember(vm.settings) { mutableStateOf(vm.settings) }

    BackHandler { vm.backHome() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("设置", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.backHome() }) { Text("返回") }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "所有 Key 只存在本机，不会上传到别处。接口需兼容 OpenAI 格式。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        Text("语音识别（ASR）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft.asrBaseUrl,
            onValueChange = { draft = draft.copy(asrBaseUrl = it) },
            label = { Text("Base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft.asrModel,
            onValueChange = { draft = draft.copy(asrModel = it) },
            label = { Text("模型名，例如 whisper-1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft.asrApiKey,
            onValueChange = { draft = draft.copy(asrApiKey = it) },
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "可选：OpenAI、硅基流动 SenseVoice、自建 faster-whisper 网关。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        Text("翻译（LLM）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft.mtBaseUrl,
            onValueChange = { draft = draft.copy(mtBaseUrl = it) },
            label = { Text("Base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft.mtModel,
            onValueChange = { draft = draft.copy(mtModel = it) },
            label = { Text("模型名，例如 deepseek-chat") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft.mtApiKey,
            onValueChange = { draft = draft.copy(mtApiKey = it) },
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        Text("译成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Settings.TARGET_LABELS.forEach { lang ->
                if (draft.targetLang == lang) {
                    Button(onClick = { draft = draft.copy(targetLang = lang) }) { Text(lang) }
                } else {
                    OutlinedButton(onClick = { draft = draft.copy(targetLang = lang) }) { Text(lang) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("原声语言", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Settings.SOURCE_LABELS.forEach { (code, label) ->
                if (draft.sourceHint == code) {
                    Button(onClick = { draft = draft.copy(sourceHint = code) }) { Text(label, fontSize = 13.sp) }
                } else {
                    OutlinedButton(onClick = { draft = draft.copy(sourceHint = code) }) {
                        Text(label, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("术语表（可选）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft.glossary,
            onValueChange = { draft = draft.copy(glossary = it) },
            label = { Text("每行一条，例如：金秀贤=김수현") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                vm.updateSettings(draft)
                vm.backHome()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("保存")
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "提示：ASR 和翻译是两个独立服务。只想翻译现成字幕时，可以不填 ASR。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
