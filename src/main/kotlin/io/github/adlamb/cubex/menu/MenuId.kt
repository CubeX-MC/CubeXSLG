package io.github.adlamb.cubex.menu

enum class MenuId {
    TOWN_HALL,
    STORAGE,
    BUILDING,
    RESIDENT,
    TECH,
    PRODUCTION,
    POWER,
    COMBAT,
    LOGISTICS,
    RPG_LINK,
    ;

    val configKey: String
        get() = name.lowercase().replace('_', '-')
}
