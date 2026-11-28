package com.fluly.studio

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

/** All locales and both workspace screens share the same one-argument usage contract. */
class WorkspaceUsageResourceTest {
    private val moduleRoot = sequenceOf(File("."), File("studio"))
        .first { File(it, "src/main/java/com/fluly/studio").isDirectory }

    @Test
    fun workspaceUsageAcceptsOneArgumentInEveryLocale() {
        listOf("values", "values-en", "values-ja").forEach { locale ->
            val file = File(moduleRoot, "src/main/res/$locale/strings.xml")
            val entries = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(file).getElementsByTagName("string")
            val text = (0 until entries.length).map { entries.item(it) }
                .single { it.attributes.getNamedItem("name").nodeValue == "agent_workspace_usage" }
                .textContent
            assertEquals(locale, setOf("%1\$s"), Regex("%[0-9]+\\\$s").findAll(text).map { it.value }.toSet())
            assertEquals(locale, text.replace("%1\$s", "1 MB"), String.format(text, "1 MB"))
        }
    }

    @Test
    fun workspaceScreensOnlyPassUsedBytes() {
        val sources = listOf("AgentSettingsPage.kt", "AgentWorkspaceSettingsPages.kt")
        sources.forEach { name ->
            val source = File(moduleRoot, "src/main/java/com/fluly/studio/ui/settings/agent/$name").readText()
            val calls = Regex("R\\.string\\.agent_workspace_usage,([\\s\\S]*?)\\)").findAll(source).toList()
            assertEquals(name, 1, calls.size)
            assertEquals(name, false, calls.single().value.contains("limitBytes"))
        }
    }
}
