package com.flue.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flue.launcher.watchface.agent.runtime.WatchAgentViewModel
import com.flue.launcher.watchface.agent.tools.InMemoryWatchAgentToolHost
import com.flue.launcher.watchface.agent.workspace.SafeProotShellRunner
import com.flue.launcher.watchface.agent.workspace.WorkspaceManager
import com.flue.studio.ui.StudioChatScreen
import com.flue.studio.ui.StudioProviderDialog
import com.flue.studio.ui.StudioWorkspaceScreen
import java.io.File

/**
 * Flue Studio 外壳：Agent 对话 + proot 工作区。
 *
 * 编辑器 / 视频转码 / 文件管理要等对应的运行时与编辑器模块从 app 里拆出来，
 * 所以这里刻意不引用任何 launcher 代码。
 */
class StudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = WorkspaceManager(File(filesDir, "agent-runtime").toPath())
        val runner = SafeProotShellRunner(manager)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StudioRoot(manager = manager, runner = runner)
                }
            }
        }
    }
}

private enum class StudioTab(val label: String) {
    CHAT("对话"),
    WORKSPACE("工作区"),
}

@Composable
private fun StudioRoot(manager: WorkspaceManager, runner: SafeProotShellRunner) {
    val viewModel: WatchAgentViewModel = viewModel()
    var tab by remember { mutableStateOf(StudioTab.CHAT) }
    var showProvider by remember { mutableStateOf(false) }

    // Studio 没有编辑器会话，工具宿主用内存表盘镜像，Agent 仍然可以读写 XML 与脚本。
    val toolHost = remember {
        InMemoryWatchAgentToolHost(initialWatchXml = "<Watch/>")
    }
    DisposableEffect(toolHost) {
        viewModel.bindToolHost(toolHost)
        onDispose { viewModel.unbindToolHost(toolHost) }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                StudioTab.entries.forEach { candidate ->
                    NavigationBarItem(
                        selected = tab == candidate,
                        onClick = { tab = candidate },
                        icon = {},
                        label = { Text(candidate.label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (tab) {
            StudioTab.CHAT -> StudioChatScreen(
                viewModel = viewModel,
                onOpenSettings = { showProvider = true },
                modifier = modifier,
            )
            StudioTab.WORKSPACE -> StudioWorkspaceScreen(
                manager = manager,
                runner = runner,
                modifier = modifier,
            )
        }
    }

    if (showProvider) {
        StudioProviderDialog(
            onDismiss = { showProvider = false },
            onSaved = {
                showProvider = false
                viewModel.refreshChoices(fetchModels = true)
            },
        )
    }
}
