package io.github.adlamb.cubex.menu

import org.bukkit.entity.Player

data class MenuRenderContext(
    val placeholders: Map<String, String> = emptyMap(),
    val bodyEntries: List<MenuBodyEntry> = emptyList(),
    val buttons: Map<String, MenuButtonContext> = emptyMap(),
    val dynamicLists: Map<String, List<MenuDynamicEntry>> = emptyMap(),
)

data class MenuBodyEntry(
    val placeholders: Map<String, String>,
)

data class MenuButtonContext(
    val action: ((Player) -> Unit)? = null,
    val placeholders: Map<String, String> = emptyMap(),
    val visible: Boolean? = null,
)

data class MenuDynamicEntry(
    val placeholders: Map<String, String> = emptyMap(),
    val action: ((Player) -> Unit)? = null,
    val visible: Boolean? = null,
)
