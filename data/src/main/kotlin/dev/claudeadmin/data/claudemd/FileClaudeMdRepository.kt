package dev.claudeadmin.data.claudemd

import dev.claudeadmin.domain.model.ClaudeMd
import dev.claudeadmin.domain.repository.ClaudeMdRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileClaudeMdRepository : ClaudeMdRepository {

    override suspend fun load(projectPath: String): ClaudeMd? = withContext(Dispatchers.IO) {
        val file = File(projectPath, "CLAUDE.md")
        if (!file.isFile) return@withContext null
        val content = file.readText()
        ClaudeMd(path = file.absolutePath, content = content, imports = extractImports(content))
    }

    private fun extractImports(content: String): List<String> =
        IMPORT_REGEX.findAll(content).map { it.groupValues[1] }.toList()

    private companion object {
        val IMPORT_REGEX = Regex("""@([\w./~\\-]+)""")
    }
}
