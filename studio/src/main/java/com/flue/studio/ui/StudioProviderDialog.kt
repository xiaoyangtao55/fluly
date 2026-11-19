package com.flue.studio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flue.launcher.watchface.agent.model.AiProtocol
import com.flue.launcher.watchface.agent.model.AiProviderProfile
import com.flue.launcher.watchface.agent.storage.ProviderSettingsStore

/**
 * 最小可用的模型配置：一个 provider（baseUrl + model + key）。
 *
 * 完整的多 provider / 模型列表 / reasoning 选项留在 launcher 的设置页里，
 * Studio 只保证「能填、能存、能发请求」。
 */
@Composable
fun StudioProviderDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ProviderSettingsStore(context.applicationContext) }
    val existing = remember { store.active() }
    val existingKey = remember(existing) { existing?.let { store.apiKey(it.id) }.orEmpty() }

    var baseUrl by remember { mutableStateOf(existing?.baseUrl ?: "https://api.openai.com/v1") }
    var model by remember { mutableStateOf(existing?.modelId.orEmpty()) }
    var apiKey by remember { mutableStateOf(existingKey) }
    var protocol by remember { mutableStateOf(existing?.protocol ?: AiProtocol.CHAT_COMPLETIONS) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模型设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiProtocol.entries.forEach { candidate ->
                        FilterChip(
                            selected = protocol == candidate,
                            onClick = { protocol = candidate },
                            label = { Text(candidate.shortLabel()) },
                        )
                    }
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型 ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    "Key 存进 Android Keystore 加密的偏好文件，不写入日志。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (baseUrl.isBlank()) {
                        error = "Base URL 不能为空"
                        return@TextButton
                    }
                    val profile = AiProviderProfile(
                        id = existing?.id ?: "studio-default",
                        name = existing?.name ?: "Studio",
                        protocol = protocol,
                        baseUrl = baseUrl.trim(),
                        modelId = model.trim().ifBlank { null },
                        reasoningEffort = existing?.reasoningEffort,
                    )
                    val saved = store.save(profile, apiKey.takeIf { it.isNotBlank() })
                    if (!saved) {
                        error = "保存失败：Keystore 不可用或 key 写入被拒绝"
                        return@TextButton
                    }
                    store.setActiveProvider(profile.id)
                    onSaved()
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun AiProtocol.shortLabel(): String = when (this) {
    AiProtocol.CHAT_COMPLETIONS -> "Chat"
    AiProtocol.RESPONSES -> "Responses"
    AiProtocol.CLAUDE_MESSAGES -> "Claude"
}
