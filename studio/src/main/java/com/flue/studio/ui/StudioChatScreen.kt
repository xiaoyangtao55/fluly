package com.flue.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flue.launcher.ui.style.FlueDialog
import com.flue.launcher.ui.style.FlueOverlayHost
import com.flue.launcher.watchface.agent.model.AgentMessage
import com.flue.launcher.watchface.agent.model.AgentMessageRole
import com.flue.launcher.watchface.agent.model.AgentMessageStatus
import com.flue.launcher.watchface.agent.runtime.WatchAgentViewModel

/**
 * Studio 的对话页：只依赖 `:agent` 暴露的 ViewModel 与模型类型，不引用 launcher 的 UI。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioChatScreen(
    viewModel: WatchAgentViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showHistory by remember { mutableStateOf(false) }

    // Studio 没有编辑器会话，用一个固定 scope 让对话独立于 launcher。
    LaunchedEffect(Unit) {
        viewModel.onEditorEntered("studio")
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Studio 当前外壳只有 MaterialTheme；在本页提供同窗口提示宿主，避免独立 Popup。
    FlueOverlayHost(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            TopAppBar(
                title = {
                    Column {
                        Text("Agent", style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.generationStatus?.let { "生成中" } ?: "工作区: ${state.watchScope}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { showHistory = true }) { Text("历史") }
                    }
                    TextButton(onClick = { viewModel.newConversation() }) { Text("新会话") }
                    TextButton(onClick = onOpenSettings) { Text("设置") }
                },
            )

            if (messages.isEmpty()) {
                EmptyHint()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.inputDraft,
                    onValueChange = viewModel::updateDraft,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("描述想要的表盘，或直接给 Agent 下指令") },
                    maxLines = 4,
                )
                if (state.isGenerating) {
                    Button(onClick = viewModel::stop) { Text("停止") }
                } else {
                    Button(
                        onClick = viewModel::send,
                        enabled = state.inputDraft.isNotBlank(),
                    ) { Text("发送") }
                }
            }
        }
        if (showHistory) {
            FlueDialog(onDismissRequest = { showHistory = false }) {
                Text("历史", style = MaterialTheme.typography.titleMedium)
                conversations.take(12).forEach { conversation ->
                    TextButton(
                        onClick = {
                            showHistory = false
                            viewModel.selectConversation(conversation.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(conversation.title)
                    }
                }
                if (conversations.isEmpty()) {
                    TextButton(onClick = { showHistory = false }) { Text("还没有会话") }
                }
            }
        }
    } // FlueOverlayHost
}

@Composable
private fun ColumnScope.EmptyHint() {
    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("和 Agent 一起做表盘", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "先在设置里填好模型与 API Key。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageBubble(message: AgentMessage) {
    val isUser = message.role == AgentMessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.85f else 1f)
                .background(
                    color = if (isUser) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(12.dp),
        ) {
            val reasoning = message.reasoningContent
            if (!reasoning.isNullOrBlank() && !isUser) {
                Text(
                    text = "思考过程",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = reasoning.take(600),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (message.content.isNotBlank()) {
                SelectionContainer {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else if (message.status == AgentMessageStatus.STREAMING) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("等待模型输出…", style = MaterialTheme.typography.bodySmall)
                }
            }
            message.toolCalls.forEach { call ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "→ ${call.name}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (message.status == AgentMessageStatus.FAILED) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "生成失败",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
