package dev.claudeadmin.data.outputstyle

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.OutputStyle
import dev.claudeadmin.domain.model.OutputStyleScope
import dev.claudeadmin.domain.repository.OutputStyleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

class FileOutputStyleRepository : OutputStyleRepository {

    override suspend fun loadForProject(projectPath: String): List<OutputStyle> = withContext(Dispatchers.IO) {
        val project = scan(File(projectPath, ".claude/output-styles"), OutputStyleScope.PROJECT)
        val user = scan(File(AppDirs.userClaudeDir, "output-styles"), OutputStyleScope.USER)
        (project + user).sortedBy { it.name.lowercase() }
    }

    private fun scan(dir: File, scope: OutputStyleScope): List<OutputStyle> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.extension == "md" }
            ?.mapNotNull { parse(it, scope) }
            ?: emptyList()
    }

    private fun parse(file: File, scope: OutputStyleScope): OutputStyle? {
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val (frontmatter, body) = split(text)
        val dto = frontmatter?.let {
            runCatching { yaml.decodeFromString(FrontmatterDto.serializer(), it) }.getOrNull()
        }
        val name = dto?.name?.trim()?.takeIf { it.isNotEmpty() } ?: file.nameWithoutExtension
        return OutputStyle(
            name = name,
            description = dto?.description?.trim()?.takeIf { it.isNotEmpty() },
            scope = scope,
            path = file.absolutePath,
            body = body.trim(),
        )
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
        val name: String? = null,
        val description: String? = null,
    )

    private companion object {
        val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))
    }
}
