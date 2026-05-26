package io.github.adlamb.cubex.config

object MenuItemDefaults {
    fun create(): MenuItemConfigs = MenuItemConfigs(
        menus = mapOf(
            "town-hall" to MenuViewConfig(
                title = "<white>城府管理",
                glass = MenuItemTemplate(
                    material = "BLACK_STAINED_GLASS_PANE",
                    name = "<dark_gray> ",
                    lore = listOf("<dark_gray> "),
                ),
                body = MenuBodyConfig(
                    slots = listOf(19, 20, 21, 22, 23),
                    templates = listOf(
                        MenuBodyTemplate(
                            material = "LIGHT_BLUE_STAINED_GLASS_PANE",
                            name = "<aqua> ",
                            lore = listOf("<white><line>"),
                        ),
                    ),
                ),
                buttons = listOf(
                    MenuButtonTemplate("storage", 10, "CHEST", "<yellow>仓  储", listOf("<gray>查看资源与历史记录", "<gray>▸ 仓库储量总览", "<gray>▸ 资源变动记录")),
                    MenuButtonTemplate("residents", 11, "VILLAGER_SPAWN_EGG", "<green>居  民", listOf("<gray>查看居民与招募", "<gray>▸ 居民属性概览", "<gray>▸ 消耗粮食招募")),
                    MenuButtonTemplate("tech", 12, "ENCHANTING_TABLE", "<light_purple>科  技", listOf("<gray>研究科技树", "<gray>▸ 五大分支科技", "<gray>▸ 消耗资源解锁")),
                    MenuButtonTemplate("production", 13, "CRAFTING_TABLE", "<gold>生  产", listOf("<gray>生产系统概览", "<gray>▸ 各建筑产出速率", "<gray>▸ 原料消耗统计")),
                    MenuButtonTemplate("combat", 14, "DIAMOND_SWORD", "<red>战  斗", listOf("<gray>防御与受损状态", "<gray>▸ 建筑生命值", "<gray>▸ 哨塔与兵营")),
                    MenuButtonTemplate("logistics", 15, "POWERED_RAIL", "<blue>物  流", listOf("<gray>铁轨与运输网络", "<gray>▸ 矿车路由管理", "<gray>▸ 自动货物调配")),
                    MenuButtonTemplate("border", 16, "NETHER_STAR", "<aqua>边  界", listOf("<gray>显示城镇边界预览", "<gray>▸ 持续 15 秒", "<gray>▸ 绿色=可建造")),
                ),
            ),
            "storage" to MenuViewConfig(
                title = "<white>仓储总览",
                glass = MenuItemTemplate(
                    material = "BLACK_STAINED_GLASS_PANE",
                    name = "<dark_gray> ",
                    lore = listOf("<dark_gray> "),
                ),
                body = MenuBodyConfig(
                    slots = listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25),
                    templates = listOf(
                        MenuBodyTemplate(
                            material = "LIGHT_BLUE_STAINED_GLASS_PANE",
                            name = "<aqua> ",
                            lore = listOf("<white><line>"),
                        ),
                    ),
                ),
                buttons = listOf(
                    MenuButtonTemplate("history", 30, "WRITABLE_BOOK", "<gold>变动历史", listOf("<gray>查看最近 100 条", "<gray>资源变动记录")),
                    MenuButtonTemplate("stats", 32, "PAPER", "<light_purple>统计报表", listOf("<gray>产量与消耗统计", "<gray>▸ 每小时净产出", "<gray>▸ 增长趋势图表")),
                ),
            ),
            "resident" to MenuViewConfig(
                title = "<white>居民管理",
                glass = MenuItemTemplate(
                    material = "BLACK_STAINED_GLASS_PANE",
                    name = "<dark_gray> ",
                    lore = listOf("<dark_gray> "),
                ),
                body = MenuBodyConfig(
                    slots = listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25),
                    templates = listOf(
                        MenuBodyTemplate(
                            material = "LIGHT_BLUE_STAINED_GLASS_PANE",
                            name = "<aqua> ",
                            lore = listOf("<white><line>"),
                        ),
                    ),
                ),
                buttons = listOf(
                    MenuButtonTemplate("recruit", 22, "EMERALD", "<green>招募居民", listOf("<gray>消耗 <gold>粮食</gold> 招募新居民", "<gray>居民将自动分配属性", "<gray>可在城府分配工作岗位")),
                ),
            ),
            "tech" to MenuViewConfig(
                title = "<white>科技树",
                glass = MenuItemTemplate(
                    material = "BLACK_STAINED_GLASS_PANE",
                    name = "<dark_gray> ",
                    lore = listOf("<dark_gray> "),
                ),
                body = MenuBodyConfig(
                    slots = listOf(19, 20, 21, 22, 23, 24, 25),
                    templates = listOf(
                        MenuBodyTemplate(
                            material = "LIGHT_BLUE_STAINED_GLASS_PANE",
                            name = "<aqua> ",
                            lore = listOf("<white><line>"),
                        ),
                    ),
                ),
                dynamicLists = mapOf(
                    "tech-list" to MenuDynamicListTemplate(
                        slots = listOf(10, 11, 12, 13, 14, 15),
                        material = "LECTERN",
                        title = "<gold><tech_name>",
                        lore = listOf("<white><tech_hint>"),
                    ),
                ),
            ),
            "production" to MenuViewConfig(
                title = "<white>生产系统",
                glass = MenuItemTemplate(
                    material = "BLACK_STAINED_GLASS_PANE",
                    name = "<dark_gray> ",
                    lore = listOf("<dark_gray> "),
                ),
                body = MenuBodyConfig(
                    slots = listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25),
                    templates = listOf(
                        MenuBodyTemplate(
                            material = "LIGHT_BLUE_STAINED_GLASS_PANE",
                            name = "<aqua> ",
                            lore = listOf("<white><line>"),
                        ),
                    ),
                ),
            ),
            "building" to MenuViewConfig(
                title = "<white><building_name>",
                glass = MenuItemTemplate(
                    material = "BLACK_STAINED_GLASS_PANE",
                    name = "<dark_gray> ",
                    lore = listOf("<dark_gray> "),
                ),
                body = MenuBodyConfig(
                    slots = listOf(19, 20, 21, 22, 23),
                    templates = listOf(
                        MenuBodyTemplate(
                            material = "LIGHT_BLUE_STAINED_GLASS_PANE",
                            name = "<aqua> ",
                            lore = listOf("<white><line>"),
                        ),
                    ),
                ),
                buttons = listOf(
                    MenuButtonTemplate("upgrade", 10, "DIAMOND", "<aqua>升  级", listOf("<gray>消耗资源提升等级", "<gray>▸ 产量 / 效率提升")),
                    MenuButtonTemplate("move", 12, "FEATHER", "<yellow>移  动", listOf("<gray>迁移建筑位置", "<gray>▸ 等级与配置保留", "<gray>▸ 走到新位置确认")),
                    MenuButtonTemplate("delete", 14, "BARRIER", "<red>删  除", listOf("<gray>拆除当前建筑", "<gray>▸ 掉落建筑核心", "<gray>▸ 请输入 /slg confirm")),
                    MenuButtonTemplate("repair", 16, "ANVIL", "<green>修  复", listOf("<gray>消耗资源恢复生命值", "<gray>▸ 需要对应材料")),
                ),
            ),
            "combat" to MenuViewConfig(
                title = "<white>战斗系统",
                glass = MenuItemTemplate(
                    material = "BLACK_STAINED_GLASS_PANE",
                    name = "<dark_gray> ",
                    lore = listOf("<dark_gray> "),
                ),
                body = MenuBodyConfig(
                    slots = listOf(21, 22, 23),
                    templates = listOf(
                        MenuBodyTemplate(
                            material = "LIGHT_BLUE_STAINED_GLASS_PANE",
                            name = "<aqua> ",
                            lore = listOf("<white><line>"),
                        ),
                    ),
                ),
            ),
            "logistics" to MenuViewConfig(
                title = "<white>物流系统",
                glass = MenuItemTemplate(
                    material = "BLACK_STAINED_GLASS_PANE",
                    name = "<dark_gray> ",
                    lore = listOf("<dark_gray> "),
                ),
                body = MenuBodyConfig(
                    slots = listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25),
                    templates = listOf(
                        MenuBodyTemplate(
                            material = "LIGHT_BLUE_STAINED_GLASS_PANE",
                            name = "<aqua> ",
                            lore = listOf("<white><line>"),
                        ),
                    ),
                ),
            ),
            "rpg-link" to MenuViewConfig(
                title = "<white>RPG 联动",
                glass = MenuItemTemplate(
                    material = "BLACK_STAINED_GLASS_PANE",
                    name = "<dark_gray> ",
                    lore = listOf("<dark_gray> "),
                ),
                body = MenuBodyConfig(
                    slots = listOf(21, 22, 23),
                    templates = listOf(
                        MenuBodyTemplate(
                            material = "LIGHT_BLUE_STAINED_GLASS_PANE",
                            name = "<aqua> ",
                            lore = listOf("<white><line>"),
                        ),
                    ),
                ),
            ),
        ),
    )
}
