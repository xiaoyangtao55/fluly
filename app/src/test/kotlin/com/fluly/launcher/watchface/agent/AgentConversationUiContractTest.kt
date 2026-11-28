package com.fluly.launcher.watchface.agent

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.fluly.launcher.TestSourceRoots

class AgentConversationUiContractTest {
    @Test
    fun `assistant updates and tool invocations remain visible without raw arguments`() {
        val panel = source("com/fluly/launcher/ui/editor/agent/WatchAgentPanel.kt")

        assertTrue(panel.contains("messages.lastOrNull()?.content"))
        assertTrue(panel.contains("message.toolCalls.forEach"))
        assertTrue(panel.contains("AgentToolInvocationStep("))
        assertFalse(panel.contains("call.argumentsJson"))
    }

    @Test
    fun `watch editor uses resize once for the agent keyboard`() {
        // 编辑器已经搬进 Studio，这条声明跟着搬到了 Studio 的 manifest 上。
        val manifest = studioManifest()

        val editorActivity = manifest.substringAfter("android:name=\"com.fluly.launcher.WatchEditorActivity\"")
            .substringBefore("</activity>")
            .substringBefore("/>")
        assertTrue(editorActivity.contains("android:windowSoftInputMode=\"adjustResize\""))
    }

    private fun source(relative: String): String = TestSourceRoots.read(relative)

    private fun studioManifest(): String {
        val manifest = sequenceOf(
            File("../studio/src/main/AndroidManifest.xml"),
            File("studio/src/main/AndroidManifest.xml"),
        ).firstOrNull(File::isFile) ?: error("找不到 Studio 的 AndroidManifest.xml")
        return manifest.readText()
    }
}
