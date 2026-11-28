package com.fluly.studio

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
import com.fluly.launcher.watchface.agent.runtime.WatchAgentViewModel
import com.fluly.launcher.watchface.agent.tools.InMemoryWatchAgentToolHost
import com.fluly.launcher.watchface.agent.workspace.SafeProotShellRunner
import com.fluly.launcher.watchface.agent.workspace.WorkspaceManager
import com.fluly.studio.ui.StudioChatScreen
import com.fluly.studio.ui.StudioWatchFaceScreen
import com.fluly.studio.ui.StudioWorkspaceScreen
import com.fluly.studio.ui.settings.agent.AgentSettingsPage
import java.io.File

/**
 * Fluly Studio 外壳：Agent 对话 + 表盘预览 + Agent 设置 + proot 工作区。
 *
 * 表盘页走的是 `:watchface-runtime` 的渲染器，和 HOME 同一条链路；Agent 设置页原本在
 * HOME 的设置里，随着 `:agent` 一起搬过来，HOME 那边已经不留入口。
 */
class StudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = WorkspaceManager(File(filesDir, "agent-runtime").toPath())
        val runner = SafeProotShellRunner(manager)
        // 编辑器里的「去设置」会带这个 extra 进来，直接把底部导航落到设置页。
        val initialTab = if (intent?.getStringExtra(EXTRA_TAB) == TAB_SETTINGS) {
            StudioTab.SETTINGS
        } else {
            StudioTab.CHAT
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StudioRoot(manager = manager, runner = runner, initialTab = initialTab)
                }
            }
        }
    }

    companion object {
        /** 目标底部导航页；目前只有设置页需要被外部深链。 */
        const val EXTRA_TAB = "com.fluly.studio.extra.TAB"
        const val TAB_SETTINGS = "settings"
    }
}

private enum class StudioTab(val label: String) {
    CHAT("对话"),
    WATCHFACE("表盘"),
    SETTINGS("设置"),
    WORKSPACE("工作区"),
}

@Composable
private fun StudioRoot(
    manager: WorkspaceManager,
    runner: SafeProotShellRunner,
    initialTab: StudioTab,
) {
    val viewModel: WatchAgentViewModel = viewModel()
    var tab by remember { mutableStateOf(initialTab) }

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
                onOpenSettings = { tab = StudioTab.SETTINGS },
                modifier = modifier,
            )
            StudioTab.WATCHFACE -> StudioWatchFaceScreen(modifier = modifier)
            StudioTab.SETTINGS -> AgentSettingsPage(onBack = { tab = StudioTab.CHAT })
            StudioTab.WORKSPACE -> StudioWorkspaceScreen(
                manager = manager,
                runner = runner,
                modifier = modifier,
            )
        }
    }

}
