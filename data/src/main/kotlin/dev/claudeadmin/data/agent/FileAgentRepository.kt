package dev.claudeadmin.data.agent

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.Agent
import dev.claudeadmin.domain.model.AgentScope
import dev.claudeadmin.domain.repository.AgentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

class FileAgentRepository : AgentRepository {

    override suspend fun loadForProject(projectPath: String): List<Agent> = withContext(Dispatchers.IO) {
        val project = scanDir(File(projectPath, ".claude/agents"), AgentScope.PROJECT)
        val user = scanDir(File(AppDirs.userClaudeDir, "agents"), AgentScope.USER)
        (project + user).sortedBy { it.name.lowercase() }
    }

    private fun scanDir(dir: File, scope: AgentScope): List<Agent> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.extension == "md" }
            ?.mapNotNull { parse(it, scope) }
            ?: emptyList()
    }

    private fun parse(file: File, scope: AgentScope): Agent? {
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val (frontmatter, body) = split(text) ?: return null
        val dto = runCatching { yaml.decodeFromString(FrontmatterDto.serializer(), frontmatter) }
            .getOrNull() ?: return null
        val name = dto.name?.trim()?.takeIf { it.isNotEmpty() } ?: file.nameWithoutExtension
        val tools = dto.tools.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return Agent(
            name = name,
            description = dto.description?.trim()?.takeIf { it.isNotEmpty() },
            scope = scope,
            path = file.absolutePath,
            tools = tools,
            model = dto.model,
            permissionMode = dto.permissionMode,
            body = body.trim(),
        )
    }

    private fun split(text: String): Pair<String, String>? {
        if (!text.startsWith("---")) return null
        val afterFirst = text.indexOf('\n', 3).takeIf { it >= 0 }?.let { it + 1 } ?: return null
        val closeIdx = text.indexOf("\n---", afterFirst).takeIf { it >= 0 } ?: return null
        val frontmatter = text.substring(afterFirst, closeIdx)
        val bodyStart = (text.indexOf('\n', closeIdx + 1).takeIf { it >= 0 } ?: text.length)
        val body = text.substring(bodyStart).removePrefix("\n")
        return frontmatter to body
    }

    @Serializable
    private data class FrontmatterDto(
        val name: String? = null,
        val description: String? = null,
        val tools: String? = null,
        val model: String? = null,
        val permissionMode: String? = null,
    )

    private companion object {
        val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))
    }
}
