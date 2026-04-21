package dev.claudeadmin.domain.model

data class Skill(
    val name: String,
    val description: String?,
    val scope: SkillScope,
    val path: String,
    val body: String,
)

enum class SkillScope { PROJECT, USER }
