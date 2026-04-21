package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.Skill

interface SkillRepository {
    suspend fun loadForProject(projectPath: String): List<Skill>
}
