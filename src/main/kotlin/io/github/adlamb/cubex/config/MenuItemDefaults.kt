package io.github.adlamb.cubex.config

object MenuItemDefaults {
    fun create(): MenuItemConfigs = MenuItemConfigs(
        menus = mapOf(
            "town-hall" to MenuViewConfig(
                title = "<white>城府管理",
                buttons = listOf(
                    MenuButtonTemplate("storage", 10, "BARREL", "<white>仓储", listOf("<white>查看资源与历史")),
                    MenuButtonTemplate("residents", 11, "VILLAGER_SPAWN_EGG", "<white>居民", listOf("<white>查看居民与招募")),
                    MenuButtonTemplate("tech", 12, "ENCHANTING_TABLE", "<white>科技", listOf("<white>研究科技")),
                    MenuButtonTemplate("production", 13, "CHEST", "<white>生产", listOf("<white>生产概览")),
                    MenuButtonTemplate("combat", 14, "IRON_SWORD", "<white>战斗", listOf("<white>防御与受损状态")),
                    MenuButtonTemplate("logistics", 15, "MINECART", "<white>物流", listOf("<white>铁轨与路由")),
                    MenuButtonTemplate("border", 16, "NETHER_STAR", "<white>边界", listOf("<white>显示15秒边界预览")),
                ),
            ),
            "storage" to MenuViewConfig(
                title = "<white>仓储总览",
                buttons = listOf(
                    MenuButtonTemplate("history", 21, "PAPER", "<white>历史", listOf("<white>查看最近100条记录")),
                    MenuButtonTemplate("stats", 23, "WRITABLE_BOOK", "<white>统计", listOf("<white>查看资源统计")),
                ),
            ),
            "resident" to MenuViewConfig(
                title = "<white>居民管理",
                buttons = listOf(
                    MenuButtonTemplate("recruit", 22, "EMERALD", "<white>招募", listOf("<white>消耗粮食招募居民")),
                ),
            ),
            "tech" to MenuViewConfig(
                title = "<white>科技树",
                dynamicLists = mapOf(
                    "tech-list" to MenuDynamicListTemplate(
                        slots = listOf(10, 11, 12, 13, 14, 15),
                        material = "LECTERN",
                        title = "<white>{tech_name}",
                        lore = listOf("<white>{tech_hint}"),
                    ),
                ),
            ),
            "production" to MenuViewConfig(
                title = "<white>生产系统",
            ),
            "building" to MenuViewConfig(
                title = "<white>{building_name}",
                buttons = listOf(
                    MenuButtonTemplate("upgrade", 10, "DIAMOND", "<white>升级", listOf("<white>提升建筑等级")),
                    MenuButtonTemplate("move", 12, "PAPER", "<white>移动", listOf("<white>创建移动待确认")),
                    MenuButtonTemplate("delete", 14, "BARRIER", "<white>删除", listOf("<white>创建删除待确认")),
                    MenuButtonTemplate("repair", 16, "ANVIL", "<white>修复", listOf("<white>消耗资源恢复生命")),
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
