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
) : AiSessionRepository {

    override val provider: AiProvider = AiProvider.OPENCODE

    private val _sessions = MutableStateFlow<List<AiSession>>(emptyList())
    private val sessions = _sessions.asStateFlow()

    private val possibleDbDirs = listOf(
        File(AppDirs.userHome, ".local/share/opencode"),
        File(AppDirs.userHome, ".opencode"),
        File(AppDirs.userHome, "Library/Application Support/opencode"),
    )

    private fun findDb(): File? {
        for (dir in possibleDbDirs) {
            val db = File(dir, "opencode.db")
            if (db.exists()) {
                println("[OpenCode] Found DB at: ${db.absolutePath}")
                return db
            }
        }
        println("[OpenCode] DB not found. Checked:")
        possibleDbDirs.forEach { dir ->
            println("  - ${File(dir, "opencode.db").absolutePath} (exists: ${File(dir, "opencode.db").exists()})")
        }
        println("[OpenCode] user.home = ${AppDirs.userHome.absolutePath}")
        return null
    }

    init {
        scope.launch {
            while (true) {
                val db = findDb()
                if (db != null) {
                    val current = querySessions(db)
                    if (current != _sessions.value) {
                        _sessions.value = current
                    }
                }
                delay(POLL_MS)
            }
        }
    }

    override fun observeAll(): Flow<List<AiSession>> = sessions

    private fun querySessions(dbFile: File): List<AiSession> {
        return runCatching {
            val sessions = mutableListOf<AiSession>()
            DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}?mode=ro").use { conn ->
                conn.prepareStatement(
                    """
                    SELECT s.id, s.directory, s.title, s.time_updated,
                           json_extract(p.data, '$.text') AS first_prompt
                    FROM session s
                    LEFT JOIN message m ON m.session_id = s.id
                                       AND json_extract(m.data, '$.role') = 'user'
                    LEFT JOIN part p ON p.message_id = m.id
                    WHERE p.id = (
                        SELECT p2.id FROM part p2
                        JOIN message m2 ON p2.message_id = m2.id
                        WHERE m2.session_id = s.id
                          AND json_extract(m2.data, '$.role') = 'user'
                        ORDER BY m2.time_created ASC, p2.time_created ASC
                        LIMIT 1
                    )
                    ORDER BY s.time_updated DESC
                    """.trimIndent()
                ).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        val meta = rs.metaData
                        println("[OpenCode] Query OK, columns: ${(1..meta.columnCount).joinToString { meta.getColumnName(it) }}")
                        while (rs.next()) {
                            val title = rs.getString("title").orEmpty()
                            val prompt = rs.getString("first_prompt")
                            val preview = if (!prompt.isNullOrBlank()) prompt else title
                            val session = AiSession(
                                id = rs.getString("id"),
                                cwd = rs.getString("directory"),
                                preview = preview,
                                lastModified = rs.getLong("time_updated"),
                                provider = AiProvider.OPENCODE,
                            )
                            println("[OpenCode] Session: id=${session.id} preview=${session.preview.take(40)}")
                            sessions.add(session)
                        }
                    }
                }
            }
            println("[OpenCode] Total sessions: ${sessions.size}")
            sessions
        }.getOrElse { t ->
            println("[OpenCode] Query error: ${t.message}")
            t.printStackTrace()
            emptyList()
        }
    }

    private companion object {
        const val POLL_MS = 2_000L
    }
}
