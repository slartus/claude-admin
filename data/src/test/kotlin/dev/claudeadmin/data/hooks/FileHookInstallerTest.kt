package dev.claudeadmin.data.hooks

import dev.claudeadmin.domain.model.HookInstallState
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileHookInstallerTest {

    @TempDir
    lateinit var tmp: File

    private val json = Json { prettyPrint = true }

    @Test
    fun `currentState reports NotInstalled for missing file`() = runTest {
        val file = File(tmp, "settings.json")
        val installer = FileHookInstaller(file)
        assertEquals(HookInstallState.NotInstalled, installer.currentState())
    }

    @Test
    fun `install preserves existing keys and adds hooks`() = runTest {
        val file = File(tmp, "settings.json").apply {
            writeText(
                """
                {
                  "permissions": { "allow": ["Bash(ls)"], "defaultMode": "acceptEdits" },
                  "model": "opus"
                }
                """.trimIndent(),
            )
        }
        val installer = FileHookInstaller(file)

        installer.install().getOrThrow()

        val root = Json.parseToJsonElement(file.readText()).jsonObject
        assertTrue(root.containsKey("permissions")) { "permissions erased" }
        assertTrue(root.containsKey("model")) { "model erased" }
        val hooks = root["hooks"] as JsonObject
        val expectedEvents = setOf(
            "UserPromptSubmit",
            "PreToolUse",
            "Notification",
            "Stop",
            "SubagentStop",
        )
        assertEquals(expectedEvents, hooks.keys)
        assertEquals(HookInstallState.Installed("v1"), installer.currentState())
    }

    @Test
    fun `install creates backup on first run`() = runTest {
        val file = File(tmp, "settings.json").apply { writeText("""{"model":"opus"}""") }
        val installer = FileHookInstaller(file)

        installer.install().getOrThrow()

        val backup = File(tmp, "settings.json.claude-admin.bak")
        assertTrue(backup.exists())
        assertEquals("""{"model":"opus"}""", backup.readText())
    }

    @Test
    fun `install is idempotent`() = runTest {
        val file = File(tmp, "settings.json")
        val installer = FileHookInstaller(file)

        installer.install().getOrThrow()
        val after1 = file.readText()
        installer.install().getOrThrow()
        val after2 = file.readText()

        assertEquals(after1, after2)
        assertEquals(HookInstallState.Installed("v1"), installer.currentState())
    }

    @Test
    fun `uninstall removes only our hooks`() = runTest {
        val file = File(tmp, "settings.json").apply {
            writeText(
                """
                {
                  "hooks": {
                    "PreToolUse": [
                      {
                        "matcher": "Bash",
                        "hooks": [
                          { "type": "command", "command": "echo other-user-hook" }
                        ]
                      }
                    ]
                  }
                }
                """.trimIndent(),
            )
        }
        val installer = FileHookInstaller(file)

        installer.install().getOrThrow()
        installer.uninstall().getOrThrow()

        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val hooks = root["hooks"] as JsonObject
        assertTrue(hooks.containsKey("PreToolUse"))
        val preToolUse = (hooks["PreToolUse"] as kotlinx.serialization.json.JsonArray)
        assertEquals(1, preToolUse.size)
        val cmd = (preToolUse[0] as JsonObject)["hooks"]
            ?.let { (it as kotlinx.serialization.json.JsonArray)[0] as JsonObject }
            ?.get("command")
            ?.toString()
        assertTrue(cmd?.contains("echo other-user-hook") == true)
        assertFalse(cmd?.contains("claude-admin-status") == true)
        assertEquals(HookInstallState.NotInstalled, installer.currentState())
    }

    @Test
    fun `install detects outdated marker as needing update`() = runTest {
        val file = File(tmp, "settings.json").apply {
            writeText(
                """
                {
                  "hooks": {
                    "Stop": [
                      {
                        "hooks": [
                          {
                            "type": "command",
                            "command": "echo legacy >> ~/.claude/claude-admin-status.jsonl # claude-admin-status v0"
                          }
                        ]
                      }
                    ]
                  }
                }
                """.trimIndent(),
            )
        }
        val installer = FileHookInstaller(file)
        val state = installer.currentState()
        assertTrue(state is HookInstallState.OutdatedVersion) { "was $state" }
        state as HookInstallState.OutdatedVersion
        assertEquals("v0", state.installedVersion)
        assertEquals("v1", state.currentVersion)
    }
}
