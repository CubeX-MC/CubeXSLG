package io.github.adlamb.cubex.config

object MenuItemDefaults {
    fun create(): MenuItemConfigs = MenuItemConfigs(
        menus = mapOf(
            "town-hall" to MenuViewConfig(
                title = "<white>城府管理",
                buttons = listOf(
                    MenuButtonTemplate("storage", 10, "BARREL", "<white>仓储"),
                    MenuButtonTemplate("residents", 11, "VILLAGER_SPAWN_EGG", "<white>居民"),
                    MenuButtonTemplate("tech", 12, "ENCHANTING_TABLE", "<white>科技"),
                    MenuButtonTemplate("production", 13, "CHEST", "<white>生产"),
                    MenuButtonTemplate("combat", 14, "IRON_SWORD", "<white>战斗"),
                    MenuButtonTemplate("logistics", 15, "MINECART", "<white>物流"),
                    MenuButtonTemplate("border", 16, "NETHER_STAR", "<white>边界"),
                ),
            ),
            "storage" to MenuViewConfig(
                title = "<white>仓储总览",
                buttons = listOf(
                    MenuButtonTemplate("history", 21, "PAPER", "<white>历史"),
                    MenuButtonTemplate("stats", 23, "WRITABLE_BOOK", "<white>统计"),
                ),
            ),
            "resident" to MenuViewConfig(
                title = "<white>居民管理",
                buttons = listOf(
                    MenuButtonTemplate("recruit", 22, "EMERALD", "<white>招募"),
                ),
            ),
            "tech" to MenuViewConfig(
                title = "<white>科技树",
                dynamicLists = mapOf(
                    "tech-list" to MenuDynamicListTemplate(
                        slots = listOf(10, 11, 12, 13, 14, 15),
                        material = "LECTERN",
                        title = "<white><tech_name>",
                    ),
                ),
            ),
            "production" to MenuViewConfig(
                title = "<white>生产系统",
            ),
            "building" to MenuViewConfig(
                title = "<white><building_name>",
                buttons = listOf(
                    MenuButtonTemplate("upgrade", 10, "DIAMOND", "<white>升级"),
                    MenuButtonTemplate("move", 12, "PAPER", "<white>移动"),
                    MenuButtonTemplate("delete", 14, "BARRIER", "<white>删除"),
                    MenuButtonTemplate("repair", 16, "ANVIL", "<white>修复"),
                ),
            ),
            "combat" to MenuViewConfig(
                title = "<white>战斗系统",
            ),
            "logistics" to MenuViewConfig(
                title = "<white>物流系统",
            ),
            "rpg-link" to MenuViewConfig(
                title = "<white>RPG 联动",
            ),
        ),
    )
}
