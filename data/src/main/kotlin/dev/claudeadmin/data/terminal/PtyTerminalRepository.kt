package dev.claudeadmin.data.terminal

import com.jediterm.terminal.TtyConnector
import dev.claudeadmin.data.hooks.FileHookPluginInstaller
import dev.claudeadmin.data.opencode.OpenCodeDb
import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.model.TerminalSession
import dev.claudeadmin.domain.model.TerminalSessionId
import dev.claudeadmin.domain.repository.TerminalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PtyTerminalRepository(
    private val hookPluginInstaller: FileHookPluginInstaller? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : TerminalRepository {

    private data class Entry(val session: TerminalSession, val backend: PtyBackend)

    private val mutex = Mutex()
    private val entries = MutableStateFlow<Map<TerminalSessionId, Entry>>(emptyMap())
    private val claimedOpenCodeSessions = ConcurrentHashMap<TerminalSessionId, String>()

    override fun observeAll(): Flow<List<TerminalSession>> =
        entries.map { map -> map.values.map { it.session }.sortedBy { it.createdAt } }

    override fun observeByProject(projectId: ProjectId): Flow<List<TerminalSession>> =
        entries.map { map ->
            map.values
                .map { it.session }
                .filter { it.projectId == projectId }
                .sortedBy { it.createdAt }
        }

    override suspend fun open(
        project: Project,
        title: String,
        resumeSessionId: String?,
        provider: AiProvider,
        claudeSettingsPath: String?,
        resumeLastSession: Boolean,
    ): TerminalSession = spawn(
        projectId = project.id,
        cwd = project.path,
        title = title,
        resumeSessionId = resumeSessionId,
        provider = provider,
        claudeSettingsPath = claudeSettingsPath,
        resumeLastSession = resumeLastSession,
    )

    override suspend fun openDetached(
        cwd: String,
        title: String,
        resumeSessionId: String?,
        provider: AiProvider,
        claudeSettingsPath: String?,
        resumeLastSession: Boolean,
    ): TerminalSession = spawn(
        projectId = null,
        cwd = cwd,
        title = title,
        resumeSessionId = resumeSessionId,
        provider = provider,
        claudeSettingsPath = claudeSettingsPath,
        resumeLastSession = resumeLastSession,
    )

    private suspend fun spawn(
        projectId: ProjectId?,
        cwd: String,
        title: String,
        resumeSessionId: String?,
        provider: AiProvider,
        claudeSettingsPath: String?,
        resumeLastSession: Boolean,
    ): TerminalSession = withContext(Dispatchers.IO) {
        val spawnTs = System.currentTimeMillis()
        val aiSessionId = resumeSessionId ?: UUID.randomUUID().toString()
        val pluginDir = if (provider == AiProvider.CLAUDE) {
            hookPluginInstaller?.ensureInstalled()
            hookPluginInstaller?.pluginDir?.absolutePath
        } else null
        val fullCommand = buildCommand(provider, resumeSessionId, aiSessionId, claudeSettingsPath, pluginDir, resumeLastSession)
        val backend = PtyFactory.spawn(cwd, fullCommand, claudeSettingsPath)
        val session = TerminalSession(
            id = TerminalSessionId(UUID.randomUUID().toString()),
            projectId = projectId,
            cwd = cwd,
            title = title,
            createdAt = System.currentTimeMillis(),
            aiSessionId = aiSessionId,
            aiProvider = provider,
            claudeSettingsPath = claudeSettingsPath,
        )
        mutex.withLock {
            entries.value = entries.value + (session.id to Entry(session, backend))
        }
        if (provider == AiProvider.OPENCODE && resumeSessionId == null && !resumeLastSession) {
            resolveOpenCodeSessionId(session.id, cwd, spawnTs)
        }
        session
    }

    private fun resolveOpenCodeSessionId(terminalId: TerminalSessionId, cwd: String, spawnTs: Long) {
        scope.launch {
            repeat(SESSION_ID_RESOLVE_ATTEMPTS) {
                val db = OpenCodeDb.findDb()
                val excluded = mutex.withLock { claimedOpenCodeSessions.values.toSet() }
                val candidates = if (db != null) {
                    withContext(Dispatchers.IO) { OpenCodeDb.findSessionIds(db, cwd, spawnTs, excluded) }
                } else emptyList()
                var joined = false
                for (realId in candidates) {
                    val occupy = mutex.withLock {
                        if (!entries.value.containsKey(terminalId)) return@withLock false
                        if (claimedOpenCodeSessions.containsValue(realId)) return@withLock true
                        claimedOpenCodeSessions[terminalId] = realId
                        entries.value[terminalId]?.let { entry ->
                            entries.value = entries.value +
                                (terminalId to entry.copy(session = entry.session.copy(aiSessionId = realId)))
                        }
                        false
                    }
                    when (occupy) {
                        false -> { joined = true; break }
                        true -> continue
                    }
                }
                if (joined) return@launch
                delay(SESSION_ID_RESOLVE_POLL_MS)
            }
        }
    }

    private fun buildCommand(
        provider: AiProvider,
        resumeSessionId: String?,
        aiSessionId: String,
        claudeSettingsPath: String?,
        hookPluginDir: String?,
        resumeLastSession: Boolean,
    ): String = when (provider) {
        AiProvider.CLAUDE -> {
            val settings = if (claudeSettingsPath != null) " --settings $claudeSettingsPath" else ""
            val plugin = if (hookPluginDir != null) " --plugin-dir \"$hookPluginDir\"" else ""
            when {
                resumeLastSession -> "${provider.cliCommand}$settings$plugin --continue"
                resumeSessionId != null -> "${provider.cliCommand}$settings$plugin --resume $resumeSessionId"
                else -> "${provider.cliCommand}$settings$plugin --session-id=$aiSessionId"
            }
        }
        AiProvider.OPENCODE -> {
            when {
                resumeLastSession -> "${provider.cliCommand} --continue"
                resumeSessionId != null -> "${provider.cliCommand} --session $resumeSessionId"
                else -> provider.cliCommand
            }
        }
    }

    override suspend fun close(id: TerminalSessionId) {
        val entry = mutex.withLock {
            claimedOpenCodeSessions.remove(id)
            val e = entries.value[id] ?: return@withLock null
            entries.value = entries.value - id
            e
        }
        entry?.backend?.dispose()
    }

    fun connectorFor(id: TerminalSessionId): TtyConnector? = entries.value[id]?.backend?.connector

    fun isAlive(id: TerminalSessionId): Boolean = entries.value[id]?.backend?.isAlive == true

    fun livePids(): Map<TerminalSessionId, Long> =
        entries.value.mapNotNull { (id, entry) ->
            if (entry.backend.isAlive) id to entry.backend.process.pid() else null
        }.toMap()

    private companion object {
        const val SESSION_ID_RESOLVE_ATTEMPTS = 15
        const val SESSION_ID_RESOLVE_POLL_MS = 1_000L
    }
}
