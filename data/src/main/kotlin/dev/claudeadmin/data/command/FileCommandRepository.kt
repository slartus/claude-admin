package dev.claudeadmin.data.command

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.Command
import dev.claudeadmin.domain.model.CommandScope
import dev.claudeadmin.domain.repository.CommandRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

class FileCommandRepository : CommandRepository {

    override suspend fun loadForProject(projectPath: String): List<Command> = withContext(Dispatchers.IO) {
        val projectDir = File(projectPath, ".claude/commands")
        val userDir = File(AppDirs.userClaudeDir, "commands")
        val project = scanDir(projectDir, projectDir, CommandScope.PROJECT)
        val user = scanDir(userDir, userDir, CommandScope.USER)
        (project + user).sortedBy { it.name.lowercase() }
    }

    private fun scanDir(root: File, dir: File, scope: CommandScope): List<Command> {
        if (!dir.isDirectory) return emptyList()
        val files = dir.listFiles() ?: return emptyList()
        return files.flatMap { f ->
            when {
                f.isDirectory -> scanDir(root, f, scope)
                f.isFile && f.extension == "md" -> listOfNotNull(parse(root, f, scope))
                else -> emptyList()
            }
        }
    }

    private fun parse(root: File, file: File, scope: CommandScope): Command? {
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val (frontmatter, body) = split(text)
        val dto = frontmatter?.let {
            runCatching { yaml.decodeFromString(FrontmatterDto.serializer(), it) }.getOrNull()
        }
        val name = nameFor(root, file)
        return Command(
            name = name,
            description = dto?.description?.trim()?.takeIf { it.isNotEmpty() },
            scope = scope,
            path = file.absolutePath,
            argumentHint = dto?.argumentHint?.trim()?.takeIf { it.isNotEmpty() },
            model = dto?.model?.trim()?.takeIf { it.isNotEmpty() },
            body = body.trim(),
        )
    }

    private fun nameFor(root: File, file: File): String {
        val relative = file.relativeTo(root).invariantSeparatorsPath
        val withoutExt = relative.removeSuffix(".md")
        return withoutExt.replace('/', ':')
    }

    private fun split(text: String): Pair<String?, String> {
        if (!text.startsWith("---")) return null to text
        val afterFirst = text.indexOf('\n', 3).takeIf { it >= 0 }?.let { it + 1 } ?: return null to text
        val closeIdx = text.indexOf("\n---", afterFirst).takeIf { it >= 0 } ?: return null to text
        val frontmatter = text.substring(afterFirst, closeIdx)
        val bodyStart = (text.indexOf('\n', closeIdx + 1).takeIf { it >= 0 } ?: text.length)
        val body = text.substring(bodyStart).removePrefix("\n")
        return frontmatter to body
    }

    @Serializable
    private data class FrontmatterDto(
        val description: String? = null,
        @SerialName("argument-hint") val argumentHint: String? = null,
        val model: String? = null,
    )

    private companion object {
        val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))
    }
}
