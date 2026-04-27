package io.github.adlamb.cubex.config

data class MenuItemConfigs(
    val menus: Map<String, MenuViewConfig>,
)

data class MenuViewConfig(
    val title: String,
    val rows: Int? = null,
    val glass: MenuItemTemplate = MenuItemTemplate(),
    val body: MenuBodyConfig = MenuBodyConfig(),
    val buttons: List<MenuButtonTemplate> = emptyList(),
    val dynamicLists: Map<String, MenuDynamicListTemplate> = emptyMap(),
)

data class MenuItemTemplate(
    val material: String = "GRAY_STAINED_GLASS_PANE",
    val name: String = "<white> ",
    val lore: List<String> = emptyList(),
)

data class MenuBodyConfig(
    val templates: List<MenuBodyTemplate> = listOf(MenuBodyTemplate()),
    val slots: List<Int> = listOf(4, 5, 6, 7, 8),
)

data class MenuBodyTemplate(
    val material: String = "PAPER",
    val name: String = "<white> ",
    val lore: List<String> = emptyList(),
)

data class MenuButtonTemplate(
    val id: String,
    val slot: Int,
    val material: String,
    val title: String,
    val lore: List<String> = emptyList(),
    val visible: Boolean = true,
)

data class MenuDynamicListTemplate(
    val slots: List<Int>,
    val material: String,
    val title: String,
    val lore: List<String> = emptyList(),
    val visible: Boolean = true,
)
