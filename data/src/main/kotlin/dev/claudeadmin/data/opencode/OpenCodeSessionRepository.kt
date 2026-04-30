package dev.claudeadmin.data.opencode

import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.AiSession
import dev.claudeadmin.domain.repository.AiSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.sql.DriverManager

class OpenCodeSessionRepository(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val dbPath: File = File(AppDirs.userOpenCodeDir, "opencode.db"),
) : AiSessionRepository {

    override val provider: AiProvider = AiProvider.OPENCODE

    private val _sessions = MutableStateFlow<List<AiSession>>(emptyList())
    private val sessions = _sessions.asStateFlow()

    init {
        scope.launch {
            while (true) {
                if (dbPath.exists()) {
                    val current = querySessions()
                    if (current != _sessions.value) {
                        _sessions.value = current
                    }
                }
                delay(POLL_MS)
            }
        }
    }

    override fun observeAll(): Flow<List<AiSession>> = sessions

    private fun querySessions(): List<AiSession> {
        if (!dbPath.exists()) return emptyList()
        return runCatching {
            val sessions = mutableListOf<AiSession>()
            DriverManager.getConnection("jdbc:sqlite:${dbPath.absolutePath}").use { conn ->
                conn.prepareStatement(
                    """
                    SELECT s.id, s.directory, s.title, s.time_updated
                    FROM session s
                    ORDER BY s.time_updated DESC
                    """.trimIndent()
                ).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            sessions.add(
                                AiSession(
                                    id = rs.getString("id"),
                                    cwd = rs.getString("directory"),
                                    preview = rs.getString("title"),
                                    lastModified = rs.getLong("time_updated"),
                                    provider = AiProvider.OPENCODE,
                                ),
                            )
                        }
                    }
                }
            }
            sessions
        }.getOrNull().orEmpty()
    }

    private companion object {
        const val POLL_MS = 2_000L
    }
}
