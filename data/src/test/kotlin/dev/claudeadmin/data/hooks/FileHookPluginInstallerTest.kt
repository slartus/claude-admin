package dev.claudeadmin.data.hooks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileHookPluginInstallerTest {

    @TempDir
    lateinit var tmp: File

    private fun pluginDir(): File = File(tmp, "plugin")

    @Test
    fun `ensureInstalled creates plugin files`() {
        val installer = FileHookPluginInstaller(pluginDir())
        assertTrue(installer.ensureInstalled())

        assertTrue(File(pluginDir(), ".claude-plugin/plugin.json").isFile)
        assertTrue(File(pluginDir(), "hooks/hooks.json").isFile)
        val script = File(pluginDir(), "hooks/status.sh")
        assertTrue(script.isFile)
        assertTrue(script.canExecute())
    }

    @Test
    fun `currentVersion reports version`() {
        val installer = FileHookPluginInstaller(pluginDir())
        installer.ensureInstalled()
        assertEquals("1.0.0", installer.currentVersion())
    }

    @Test
    fun `hooks json maps events to statuses`() {
        val installer = FileHookPluginInstaller(pluginDir())
        installer.ensureInstalled()
        val hooks = Json.parseToJsonElement(File(pluginDir(), "hooks/hooks.json").readText())
            .jsonObject["hooks"]!!.jsonObject

        fun command(event: String): String =
            hooks[event]!!.jsonArray[0].jsonObject["hooks"]!!.jsonArray[0].jsonObject["command"]!!.jsonPrimitive.content

        assertTrue(command("UserPromptSubmit").contains("working"))
        assertTrue(command("Notification").contains("waiting"))
        assertTrue(command("SessionEnd").contains("ended"))
        assertTrue(command("SessionStart").contains("idle"))
        assertTrue(command("PreToolUse").contains("working"))
        assertTrue(command("PostToolUseFailure").contains("working"))

        val matcher = hooks["PreToolUse"]!!.jsonArray[0].jsonObject["matcher"]!!.jsonPrimitive.content
        assertEquals("*", matcher)
        val noMatcher = hooks["Notification"]!!.jsonArray[0].jsonObject
        assertTrue("matcher" !in noMatcher)
    }

    @Test
    fun `is idempotent`() {
        val installer = FileHookPluginInstaller(pluginDir())
        assertTrue(installer.ensureInstalled())
        val hooksBefore = File(pluginDir(), "hooks/hooks.json").readText()
        assertTrue(installer.ensureInstalled())
        val hooksAfter = File(pluginDir(), "hooks/hooks.json").readText()
        assertEquals(hooksBefore, hooksAfter)
    }

    @Test
    fun `status script logs to jsonl and requires jq`() {
        val installer = FileHookPluginInstaller(pluginDir())
        installer.ensureInstalled()
        val script = File(pluginDir(), "hooks/status.sh")
        assertNotNull(script)
        val scriptText = script.readText()
        assertTrue(scriptText.contains("jq"))
        assertTrue(scriptText.contains("status.jsonl"))
    }
}
