package dev.claudeadmin.data.opencode

import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.OpenCodeMd
import dev.claudeadmin.domain.repository.OpenCodeMdRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileOpenCodeMdRepository : OpenCodeMdRepository {

    private val contextFileNames = listOf("AGENTS.md", "opencode.md", "OPENCODE.md")

    override suspend fun load(projectPath: String): List<OpenCodeMd> = withContext(Dispatchers.IO) {
        val projectDirs = listOf(
            File(projectPath, ".opencode"),
        )
        val globalDirs = listOf(
            AppDirs.userOpenCodeConfigDir,
            AppDirs.userOpenCodeAltDir,
        )
        val result = mutableListOf<OpenCodeMd>()
        for (dir in projectDirs) {
            for (name in contextFileNames) {
                readMdFile(File(dir, name), "Project")?.let { result.add(it) }
            }
        }
        for (dir in globalDirs) {
            for (name in contextFileNames) {
                readMdFile(File(dir, name), "User")?.let { result.add(it) }
            }
        }
        result.distinctBy { it.path }
    }

    override suspend fun loadUser(): List<OpenCodeMd> = withContext(Dispatchers.IO) {
        val globalDirs = listOf(
            AppDirs.userOpenCodeConfigDir,
            AppDirs.userOpenCodeAltDir,
        )
        val result = mutableListOf<OpenCodeMd>()
        for (dir in globalDirs) {
            for (name in contextFileNames) {
                readMdFile(File(dir, name), "User")?.let { result.add(it) }
            }
        }
        result.distinctBy { it.path }
    }

    private fun readMdFile(file: File, scopePrefix: String): OpenCodeMd? {
        if (!file.isFile) return null
        val content = runCatching { file.readText() }.getOrNull() ?: return null
        return OpenCodeMd(
            path = file.absolutePath,
            content = content,
            name = "$scopePrefix ${file.name}",
            imports = extractImports(content),
        )
    }

    private fun extractImports(content: String): List<String> =
        IMPORT_REGEX.findAll(content).map { it.groupValues[1] }.toList()

    private companion object {
        val IMPORT_REGEX = Regex("""@([\w./~\\-]+)""")
    }
}
