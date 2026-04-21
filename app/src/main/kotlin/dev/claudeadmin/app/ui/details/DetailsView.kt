package dev.claudeadmin.app.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.claudeadmin.domain.model.Agent
import dev.claudeadmin.domain.model.AgentScope
import dev.claudeadmin.domain.model.ClaudeMd
import dev.claudeadmin.domain.model.ClaudeSettings
import dev.claudeadmin.domain.model.Command
import dev.claudeadmin.domain.model.CommandScope
import dev.claudeadmin.domain.model.Hook
import dev.claudeadmin.domain.model.HookScope
import dev.claudeadmin.domain.model.McpServer
import dev.claudeadmin.domain.model.McpServerScope
import dev.claudeadmin.domain.model.OutputStyle
import dev.claudeadmin.domain.model.OutputStyleScope
import dev.claudeadmin.domain.model.ProjectDetails
import dev.claudeadmin.domain.model.Skill
import dev.claudeadmin.domain.model.SkillScope
import dev.claudeadmin.presentation.root.DetailsState

private enum class ScopeTab(val label: String) {
    PROJECT("Проектные"),
    GLOBAL("Глобальные"),
}

@Composable
fun DetailsView(
    state: DetailsState,
    onOpenFile: (String) -> Unit,
    onRevealInFinder: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        when (state) {
            DetailsState.Empty -> EmptyText("No details")
            DetailsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is DetailsState.Error -> EmptyText(state.message)
            is DetailsState.Loaded -> Content(
                details = state.details,
                onOpenFile = onOpenFile,
                onRevealInFinder = onRevealInFinder,
            )
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Content(
    details: ProjectDetails,
    onOpenFile: (String) -> Unit,
    onRevealInFinder: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ScopeTab.PROJECT) }
    val sectionExpanded = remember { mutableStateMapOf<String, Boolean>() }
    Column(Modifier.fillMaxSize()) {
        Text(
            text = details.project.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        TabRow(selectedTabIndex = tab.ordinal) {
            ScopeTab.entries.forEach { entry ->
                Tab(
                    selected = tab == entry,
                    onClick = { tab = entry },
                    text = { Text(entry.label) },
                )
            }
        }
        val sections = when (tab) {
            ScopeTab.PROJECT -> buildProjectSections(details)
            ScopeTab.GLOBAL -> buildGlobalSections(details)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sections.forEach { section ->
                val defaultExpanded = section.items.size <= 1
                val expanded = sectionExpanded[section.title] ?: defaultExpanded
                sectionHeaderItem(
                    title = section.title,
                    count = section.count,
                    expanded = expanded,
                    onToggle = { sectionExpanded[section.title] = !expanded },
                )
                if (expanded) {
                    items(section.items, key = { section.title + it.key }) { item ->
                        ExpandableCard(
                            item = item,
                            onOpenFile = onOpenFile,
                            onRevealInFinder = onRevealInFinder,
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sectionHeaderItem(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    item(key = "header:$title") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = "$title ($count)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private data class Section(
    val title: String,
    val items: List<DetailItem>,
) {
    val count: Int get() = items.size
}

private data class DetailItem(
    val key: String,
    val title: String,
    val titleMonospace: Boolean = false,
    val subtitle: String? = null,
    val badges: List<Badge> = emptyList(),
    val meta: List<String> = emptyList(),
    val body: String?,
    val filePath: String,
)

private data class Badge(val label: String, val kind: BadgeKind)

private enum class BadgeKind { PROJECT, PROJECT_LOCAL, USER, NEUTRAL }

private fun buildProjectSections(d: ProjectDetails): List<Section> = listOfNotNull(
    claudeMdSection(d.projectClaudeMd, "CLAUDE.md", BadgeKind.PROJECT),
    settingsSection(
        listOfNotNull(
            d.projectSettings?.let { it to (".claude/settings.json" to BadgeKind.PROJECT) },
            d.projectSettingsLocal?.let { it to (".claude/settings.local.json" to BadgeKind.PROJECT_LOCAL) },
        ),
    ),
    agentsSection(d.agents.filter { it.scope == AgentScope.PROJECT }),
    commandsSection(d.commands.filter { it.scope == CommandScope.PROJECT }),
    skillsSection(d.skills.filter { it.scope == SkillScope.PROJECT }),
    outputStylesSection(d.outputStyles.filter { it.scope == OutputStyleScope.PROJECT }),
    hooksSection(
        d.hooks.filter { it.scope == HookScope.PROJECT || it.scope == HookScope.PROJECT_LOCAL },
    ),
    mcpSection(
        d.mcpServers.filter {
            it.scope == McpServerScope.PROJECT || it.scope == McpServerScope.PROJECT_LOCAL
        },
    ),
)

private fun buildGlobalSections(d: ProjectDetails): List<Section> = listOfNotNull(
    claudeMdSection(d.userClaudeMd, "~/.claude/CLAUDE.md", BadgeKind.USER),
    settingsSection(
        listOfNotNull(
            d.userSettings?.let { it to ("~/.claude/settings.json" to BadgeKind.USER) },
        ),
    ),
    agentsSection(d.agents.filter { it.scope == AgentScope.USER }),
    commandsSection(d.commands.filter { it.scope == CommandScope.USER }),
    skillsSection(d.skills.filter { it.scope == SkillScope.USER }),
    outputStylesSection(d.outputStyles.filter { it.scope == OutputStyleScope.USER }),
    hooksSection(d.hooks.filter { it.scope == HookScope.USER }),
    mcpSection(d.mcpServers.filter { it.scope == McpServerScope.USER }),
)

private fun claudeMdSection(claudeMd: ClaudeMd?, title: String, badge: BadgeKind): Section? {
    if (claudeMd == null) return null
    val item = DetailItem(
        key = "md:" + claudeMd.path,
        title = title,
        badges = listOf(Badge(badge.label(), badge)),
        meta = if (claudeMd.imports.isEmpty()) emptyList() else listOf("imports: " + claudeMd.imports.joinToString(", ")),
        body = claudeMd.content,
        filePath = claudeMd.path,
    )
    return Section(title = "Rules", items = listOf(item))
}

private fun settingsSection(entries: List<Pair<ClaudeSettings, Pair<String, BadgeKind>>>): Section? {
    if (entries.isEmpty()) return null
    return Section(
        title = "Settings",
        items = entries.map { (settings, meta) ->
            val (title, badge) = meta
            DetailItem(
                key = "settings:" + settings.path,
                title = title,
                titleMonospace = true,
                badges = listOf(Badge(badge.label(), badge)),
                body = settings.content,
                filePath = settings.path,
            )
        },
    )
}

private fun agentsSection(agents: List<Agent>): Section? {
    if (agents.isEmpty()) return null
    return Section(
        title = "Agents",
        items = agents.map { a ->
            DetailItem(
                key = "agent:" + a.path,
                title = a.name,
                subtitle = a.description,
                badges = listOfNotNull(
                    Badge(a.scope.label(), a.scope.badge()),
                    a.model?.let { Badge(it, BadgeKind.NEUTRAL) },
                ),
                meta = buildList {
                    if (a.tools.isNotEmpty()) add("tools: " + a.tools.joinToString(", "))
                    a.permissionMode?.let { add("permissionMode: $it") }
                },
                body = a.body,
                filePath = a.path,
            )
        },
    )
}

private fun commandsSection(commands: List<Command>): Section? {
    if (commands.isEmpty()) return null
    return Section(
        title = "Commands",
        items = commands.map { c ->
            DetailItem(
                key = "cmd:" + c.path,
                title = "/" + c.name,
                titleMonospace = true,
                subtitle = c.description,
                badges = listOfNotNull(
                    Badge(c.scope.label(), c.scope.badge()),
                    c.model?.let { Badge(it, BadgeKind.NEUTRAL) },
                ),
                meta = listOfNotNull(c.argumentHint?.let { "args: $it" }),
                body = c.body,
                filePath = c.path,
            )
        },
    )
}

private fun skillsSection(skills: List<Skill>): Section? {
    if (skills.isEmpty()) return null
    return Section(
        title = "Skills",
        items = skills.map { s ->
            DetailItem(
                key = "skill:" + s.path,
                title = s.name,
                subtitle = s.description,
                badges = listOf(Badge(s.scope.label(), s.scope.badge())),
                body = s.body,
                filePath = s.path,
            )
        },
    )
}

private fun outputStylesSection(styles: List<OutputStyle>): Section? {
    if (styles.isEmpty()) return null
    return Section(
        title = "Output Styles",
        items = styles.map { s ->
            DetailItem(
                key = "style:" + s.path,
                title = s.name,
                subtitle = s.description,
                badges = listOf(Badge(s.scope.label(), s.scope.badge())),
                body = s.body,
                filePath = s.path,
            )
        },
    )
}

private fun hooksSection(hooks: List<Hook>): Section? {
    if (hooks.isEmpty()) return null
    return Section(
        title = "Hooks",
        items = hooks.mapIndexed { i, h ->
            DetailItem(
                key = "hook:$i:" + h.sourcePath + ":" + h.event + ":" + (h.matcher ?: ""),
                title = h.event + if (h.matcher != null) " [${h.matcher}]" else "",
                titleMonospace = true,
                badges = listOfNotNull(
                    Badge(h.scope.label(), h.scope.badge()),
                    if (h.async) Badge("async", BadgeKind.NEUTRAL) else null,
                    h.type.takeIf { it.isNotEmpty() }?.let { Badge(it, BadgeKind.NEUTRAL) },
                ),
                body = h.command,
                filePath = h.sourcePath,
            )
        },
    )
}

private fun mcpSection(servers: List<McpServer>): Section? {
    if (servers.isEmpty()) return null
    return Section(
        title = "MCP Servers",
        items = servers.map { s ->
            DetailItem(
                key = "mcp:" + s.sourcePath + ":" + s.name,
                title = s.name,
                subtitle = s.url ?: s.command,
                badges = listOfNotNull(
                    Badge(s.scope.label(), s.scope.badge()),
                    s.type?.let { Badge(it, BadgeKind.NEUTRAL) },
                ),
                meta = buildList {
                    if (s.args.isNotEmpty()) add("args: " + s.args.joinToString(" "))
                },
                body = null,
                filePath = s.sourcePath,
            )
        },
    )
}

@Composable
private fun ExpandableCard(
    item: DetailItem,
    onOpenFile: (String) -> Unit,
    onRevealInFinder: (String) -> Unit,
) {
    var expanded by remember(item.key) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = item.body != null) { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = if (item.titleMonospace) FontFamily.Monospace else FontFamily.Default,
                    )
                    item.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.padding(1.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.badges.isNotEmpty() || item.meta.isNotEmpty()) {
                        Spacer(Modifier.padding(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            item.badges.forEach { BadgeView(it) }
                        }
                        if (item.meta.isNotEmpty()) {
                            Spacer(Modifier.padding(1.dp))
                            item.meta.forEach {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                }
                FileActionButtons(
                    path = item.filePath,
                    onOpenFile = onOpenFile,
                    onRevealInFinder = onRevealInFinder,
                )
                if (item.body != null) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }
            AnimatedVisibility(visible = expanded && item.body != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = item.body.orEmpty(),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FileActionButtons(
    path: String,
    onOpenFile: (String) -> Unit,
    onRevealInFinder: (String) -> Unit,
) {
    IconButton(onClick = { onOpenFile(path) }, modifier = Modifier.size(28.dp)) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Open in default app",
            modifier = Modifier.size(16.dp),
        )
    }
    IconButton(onClick = { onRevealInFinder(path) }, modifier = Modifier.size(28.dp)) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = "Reveal in Finder",
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun BadgeView(badge: Badge) {
    val color = when (badge.kind) {
        BadgeKind.PROJECT -> MaterialTheme.colorScheme.primary
        BadgeKind.PROJECT_LOCAL -> MaterialTheme.colorScheme.tertiary
        BadgeKind.USER -> MaterialTheme.colorScheme.secondary
        BadgeKind.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when (badge.kind) {
        BadgeKind.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Surface(color = color, modifier = Modifier) {
        Text(
            text = badge.label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun BadgeKind.label(): String = when (this) {
    BadgeKind.PROJECT -> "project"
    BadgeKind.PROJECT_LOCAL -> "local"
    BadgeKind.USER -> "user"
    BadgeKind.NEUTRAL -> ""
}

private fun AgentScope.label(): String = when (this) {
    AgentScope.PROJECT -> "project"
    AgentScope.USER -> "user"
}

private fun AgentScope.badge(): BadgeKind = when (this) {
    AgentScope.PROJECT -> BadgeKind.PROJECT
    AgentScope.USER -> BadgeKind.USER
}

private fun CommandScope.label(): String = when (this) {
    CommandScope.PROJECT -> "project"
    CommandScope.USER -> "user"
}

private fun CommandScope.badge(): BadgeKind = when (this) {
    CommandScope.PROJECT -> BadgeKind.PROJECT
    CommandScope.USER -> BadgeKind.USER
}

private fun SkillScope.label(): String = when (this) {
    SkillScope.PROJECT -> "project"
    SkillScope.USER -> "user"
}

private fun SkillScope.badge(): BadgeKind = when (this) {
    SkillScope.PROJECT -> BadgeKind.PROJECT
    SkillScope.USER -> BadgeKind.USER
}

private fun OutputStyleScope.label(): String = when (this) {
    OutputStyleScope.PROJECT -> "project"
    OutputStyleScope.USER -> "user"
}

private fun OutputStyleScope.badge(): BadgeKind = when (this) {
    OutputStyleScope.PROJECT -> BadgeKind.PROJECT
    OutputStyleScope.USER -> BadgeKind.USER
}

private fun HookScope.label(): String = when (this) {
    HookScope.PROJECT -> "project"
    HookScope.PROJECT_LOCAL -> "local"
    HookScope.USER -> "user"
}

private fun HookScope.badge(): BadgeKind = when (this) {
    HookScope.PROJECT -> BadgeKind.PROJECT
    HookScope.PROJECT_LOCAL -> BadgeKind.PROJECT_LOCAL
    HookScope.USER -> BadgeKind.USER
}

private fun McpServerScope.label(): String = when (this) {
    McpServerScope.PROJECT -> "project"
    McpServerScope.PROJECT_LOCAL -> "local"
    McpServerScope.USER -> "user"
}

private fun McpServerScope.badge(): BadgeKind = when (this) {
    McpServerScope.PROJECT -> BadgeKind.PROJECT
    McpServerScope.PROJECT_LOCAL -> BadgeKind.PROJECT_LOCAL
    McpServerScope.USER -> BadgeKind.USER
}
