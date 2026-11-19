package com.flue.studio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flue.launcher.watchface.WatchFaceDescriptor
import com.flue.launcher.watchface.standard.WatchmakerWatchFaceHost
import com.flue.launcher.watchface.standard.WatchmakerWatchFaceStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Studio 的表盘预览页：直接挂 `:watchface-runtime` 的渲染器跑真表盘。
 *
 * 这里刻意不复用 HOME 的任何 View / ViewModel —— 预览走的就是桌面同一条渲染链路，
 * 形状由本页的开关显式传给 `roundScreen`，不读系统配置，也不读启动器设置。
 */
@Composable
fun StudioWatchFaceScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var faces by remember { mutableStateOf<List<WatchFaceDescriptor>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var roundScreen by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var renderReady by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableStateOf(0) }

    val selected = faces.firstOrNull { it.id == selectedId } ?: faces.firstOrNull()

    fun reload() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { WatchmakerWatchFaceStorage.scanWithDiagnostics(context) }
            }
            result.onSuccess { scanned ->
                faces = scanned.descriptors
                if (faces.none { it.id == selectedId }) selectedId = faces.firstOrNull()?.id
                status = scanned.failures.firstOrNull()?.let {
                    "${scanned.failures.size} 个表盘读取失败：${it.directoryName} · ${it.message}"
                }
            }.onFailure { error ->
                status = "扫描表盘失败：${error.message ?: error.javaClass.simpleName}"
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        status = "正在安装表盘…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { WatchmakerWatchFaceStorage.importArchive(context, uri) }
            }
            busy = false
            result.onSuccess { descriptor ->
                status = "已安装：${descriptor.displayName}"
                selectedId = descriptor.id
                refreshToken += 1
                reload()
            }.onFailure { error ->
                status = "安装失败：${error.message ?: error.javaClass.simpleName}"
            }
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { roundScreen = true }) {
                Text(if (roundScreen) "● 圆形" else "○ 圆形")
            }
            OutlinedButton(onClick = { roundScreen = false }) {
                Text(if (!roundScreen) "● 方形" else "○ 方形")
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = {
                    importer.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream",
                            "application/*",
                            "*/*",
                        )
                    )
                },
                enabled = !busy,
            ) { Text("导入 .watch") }
            TextButton(onClick = { reload() }, enabled = !busy) { Text("刷新") }
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val side = minOf(maxWidth - 32.dp, maxHeight * 0.62f)
            val previewShape = if (roundScreen) CircleShape else RoundedCornerShape(side * 0.18f)
            Box(
                modifier = Modifier
                    .size(side)
                    .clip(previewShape)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (selected == null) {
                    Text(
                        text = if (busy) "正在安装…" else "还没有 .watch 表盘\n点右上「导入 .watch」",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF9E9E9E),
                    )
                } else {
                    // 换表盘时画面不会立刻出来，先压一层加载态，等渲染器回调再撤。
                    WatchmakerWatchFaceHost(
                        descriptor = selected,
                        isFaceVisible = true,
                        refreshToken = refreshToken,
                        onLongPress = null,
                        onDoubleTap = null,
                        onLoadFailure = { failed, error ->
                            status = "渲染失败：${failed.displayName} · ${error.message ?: error.javaClass.simpleName}"
                        },
                        onRenderReady = { renderReady = true },
                        roundScreen = roundScreen,
                    )
                    if (!renderReady) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
                    }
                }
            }
        }

        status?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Text(
            text = "已安装 ${faces.size} 个表盘",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        if (faces.isEmpty()) {
            Text(
                text = "把 .watch 文件（WatchMaker 1.0 / Studio 2.0）导入后即可在这里预览。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(faces, key = { it.id }) { face ->
                    FaceRow(
                        face = face,
                        selected = face.id == selected?.id,
                        onSelect = {
                            if (face.id != selected?.id) {
                                selectedId = face.id
                                renderReady = false
                            }
                        },
                        onDelete = {
                            scope.launch {
                                val deleted = withContext(Dispatchers.IO) {
                                    WatchmakerWatchFaceStorage.delete(context, face)
                                }
                                status = if (deleted) "已删除：${face.displayName}" else "删除失败：${face.displayName}"
                                if (deleted && face.id == selected?.id) selectedId = null
                                reload()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FaceRow(
    face: WatchFaceDescriptor,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = face.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = face.summary.ifBlank { face.id },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Text(
                    text = "预览中",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}
