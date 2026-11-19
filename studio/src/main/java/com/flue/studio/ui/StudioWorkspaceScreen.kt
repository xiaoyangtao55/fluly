package com.flue.studio.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flue.launcher.watchface.agent.workspace.AgentWorkspace
import com.flue.launcher.watchface.agent.workspace.AndroidRootfsSelfChecker
import com.flue.launcher.watchface.agent.workspace.HttpRootfsArchiveSource
import com.flue.launcher.watchface.agent.workspace.RootfsCatalog
import com.flue.launcher.watchface.agent.workspace.RootfsInstallProgress
import com.flue.launcher.watchface.agent.workspace.RootfsInstallResult
import com.flue.launcher.watchface.agent.workspace.RootfsInstaller
import com.flue.launcher.watchface.agent.workspace.SafeProotShellRunner
import com.flue.launcher.watchface.agent.workspace.ShellCommandRequest
import com.flue.launcher.watchface.agent.workspace.ShellRunResult
import com.flue.launcher.watchface.agent.workspace.WorkspaceCapability
import com.flue.launcher.watchface.agent.workspace.WorkspaceManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Studio 的工作区页：rootfs 安装 + proot 终端。 */
@Composable
fun StudioWorkspaceScreen(
    manager: WorkspaceManager,
    runner: SafeProotShellRunner,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var workspaces by remember { mutableStateOf(manager.list()) }
    var selectedId by remember { mutableStateOf(workspaces.firstOrNull()?.id) }
    var newName by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("uname -a && cat /etc/os-release | head -3") }
    var log by remember { mutableStateOf("选择或新建一个工作区，安装 rootfs 后即可执行命令。") }
    var busy by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf<String?>(null) }
    var progressFraction by remember { mutableStateOf<Float?>(null) }

    val abi = remember {
        Build.SUPPORTED_ABIS.firstOrNull { RootfsCatalog.forAbi(it) != null }
            ?: Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
    }
    val onlinePackage = remember(abi) { RootfsCatalog.forAbi(abi) }
    val installer = remember(abi) {
        RootfsInstaller(
            manager = manager,
            currentAbi = { abi },
            selfChecker = AndroidRootfsSelfChecker(
                File(context.applicationInfo.nativeLibraryDir).toPath(),
            ),
        )
    }

    fun refresh() {
        workspaces = manager.list()
        if (workspaces.none { it.id == selectedId }) {
            selectedId = workspaces.firstOrNull()?.id
        }
    }

    fun runInstall(workspace: AgentWorkspace) {
        val pkg = onlinePackage
        if (pkg == null) {
            log = "当前 ABI（$abi）没有可用的 rootfs 包。"
            return
        }
        busy = true
        progressText = "准备下载"
        progressFraction = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                installer.install(
                    workspaceId = workspace.id,
                    manifest = pkg.manifest,
                    format = pkg.format,
                    source = HttpRootfsArchiveSource(pkg.url),
                    onProgress = { progress: RootfsInstallProgress ->
                        progressText = progress.stage.name
                        progressFraction = progress.totalBytes?.takeIf { it > 0L }?.let { total ->
                            (progress.bytesCopied.toFloat() / total).coerceIn(0f, 1f)
                        }
                    },
                )
            }
            log = when (result) {
                is RootfsInstallResult.Installed ->
                    "rootfs 安装完成：${result.workspace.rootfs.version ?: pkg.manifest.version}"
                is RootfsInstallResult.Invalid -> "rootfs 安装被拒绝：${result.reason.name}"
                is RootfsInstallResult.Failed -> "rootfs 安装失败：${result.message}"
            }
            progressText = null
            progressFraction = null
            busy = false
            refresh()
        }
    }

    fun runCommand(workspaceId: String) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runner.run(
                    ShellCommandRequest(
                        workspaceId = workspaceId,
                        command = command,
                        timeoutSeconds = 120L,
                    ),
                )
            }
            log = renderShellResult(result)
            busy = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Agent 工作区", style = MaterialTheme.typography.titleMedium)
            Text(
                "proot · ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (workspaces.isEmpty()) {
            Text(
                "还没有工作区。先建一个，再安装 rootfs。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(workspaces, key = { it.id }) { workspace ->
                    WorkspaceCard(
                        workspace = workspace,
                        capability = manager.capability(workspace.id),
                        selected = workspace.id == selectedId,
                        onClick = { selectedId = workspace.id },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("新工作区名称") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    val name = newName.trim().ifBlank { "工作区 ${workspaces.size + 1}" }
                    val created = manager.createWorkspace(name)
                    newName = ""
                    refresh()
                    selectedId = created.id
                    log = "已创建工作区：${created.displayName}"
                },
                enabled = !busy,
            ) { Text("新建") }
        }

        val selected = workspaces.firstOrNull { it.id == selectedId }
        if (selected != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { runInstall(selected) },
                    enabled = !busy && onlinePackage != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (selected.rootfs.status.name == "INSTALLED") "重装 rootfs" else "安装 rootfs")
                }
                TextButton(onClick = { runCommand(selected.id) }, enabled = !busy) {
                    Text("执行命令")
                }
            }
            if (progressText != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(progressText!!, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    val fraction = progressFraction
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            label = { Text("shell 命令") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )

        Card(modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = log,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                )
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceCard(
    workspace: AgentWorkspace,
    capability: WorkspaceCapability,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                workspace.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append("rootfs: ")
                    append(if (capability.rootfsInstalled) "已安装" else "未安装")
                    append(" · bash: ")
                    append(if (capability.bashAvailable) "可用" else "不可用")
                    append(" · apt: ")
                    append(if (capability.aptAvailable) "可用" else "不可用")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun renderShellResult(result: ShellRunResult): String = when (result) {
    is ShellRunResult.Completed -> buildString {
        append("exit=").append(result.exitCode)
        if (result.truncated) append("（输出已截断）")
        append("\n\n")
        append(result.stdout.ifBlank { "（无输出）" })
    }
    is ShellRunResult.RootfsNotInstalled -> "这个工作区还没装 rootfs。"
    is ShellRunResult.WorkspaceNotFound -> "工作区不存在：${result.workspaceId}"
    is ShellRunResult.RuntimeUnavailable -> "运行时不可用：${result.workspaceId}"
    is ShellRunResult.InvalidRequest -> "请求无效：${result.reason}"
    is ShellRunResult.RejectedApproval -> "命令被审批策略拒绝。"
    is ShellRunResult.AwaitingApproval -> "命令需要确认，当前壳里还没有确认 UI。"
    is ShellRunResult.DryRun -> "dry-run：${result.command}"
}
