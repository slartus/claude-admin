package dev.claudeadmin.data.terminal

import com.jediterm.terminal.TtyConnector
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import java.nio.charset.StandardCharsets

class PtyBackend(
    val process: PtyProcess,
    val connector: TtyConnector,
) {
    val isAlive: Boolean get() = process.isAlive

    fun dispose() {
        runCatching { connector.close() }
        runCatching { process.destroy() }
    }
}

object PtyFactory {

    fun spawn(
        projectPath: String,
        command: String = DEFAULT_COMMAND,
        claudeSettingsPath: String? = null,
    ): PtyBackend {
        val env = buildEnv(claudeSettingsPath != null)
        val shell = System.getenv("SHELL") ?: "/bin/zsh"
        val cmd = arrayOf(shell, "-l", "-i", "-c", command)
        val process: PtyProcess = PtyProcessBuilder(cmd)
            .setDirectory(projectPath)
            .setEnvironment(env)
            .setConsole(false)
            .setRedirectErrorStream(true)
            .setInitialColumns(120)
            .setInitialRows(32)
            .start()
        val connector = Pty4jTtyConnector(process, StandardCharsets.UTF_8)
        return PtyBackend(process, connector)
    }

    private fun buildEnv(hasCustomSettings: Boolean): Map<String, String> {
        val env = HashMap(System.getenv())
        env["TERM"] = "xterm-256color"
        env.putIfAbsent("LANG", "en_US.UTF-8")
        env.putIfAbsent("LC_ALL", "en_US.UTF-8")
        if (hasCustomSettings) {
            env.keys.removeIf { it.startsWith("ANTHROPIC_") }
        }
        return env
    }

    const val DEFAULT_COMMAND = "claude"
}
