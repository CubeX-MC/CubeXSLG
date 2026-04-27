package io.github.adlamb.cubex.registry

import org.bukkit.Material

enum class ResourceCategory {
    BASIC,
    PROCESSED,
    SPECIAL,
}

data class ResourceDescriptor(
    val key: String,
    val displayName: String,
    val category: ResourceCategory,
)

data class BuildingDescriptor(
    val key: String,
    val displayName: String,
    val wandMaterial: Material,
)

enum class TechBranch(val displayName: String) {
    PRODUCTION("生产分支"),
    MILITARY("军事分支"),
    RESIDENT("居民分支"),
    LOGISTICS("物流分支"),
    TOWN("城镇分支"),
}

enum class ResidentAttribute(val displayName: String) {
    STRENGTH("力量"),
    AGILITY("敏捷"),
    INTELLIGENCE("智力"),
    ENDURANCE("耐力"),
    MANAGEMENT("管理"),
}

enum class TownLevel(val level: Int) {
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5),
}

data class GameplayRegistry(
    val resources: List<ResourceDescriptor>,
    val buildings: List<BuildingDescriptor>,
) {
    fun findBuilding(key: String): BuildingDescriptor? = buildings.firstOrNull { it.key.equals(key, ignoreCase = true) }

    companion object {
        fun default(): GameplayRegistry = GameplayRegistry(
            resources = listOf(
                ResourceDescriptor("wood", "木材", ResourceCategory.BASIC),
                ResourceDescriptor("stone", "石头", ResourceCategory.BASIC),
                ResourceDescriptor("ore", "矿石", ResourceCategory.BASIC),
                ResourceDescriptor("food", "粮食", ResourceCategory.BASIC),
                ResourceDescriptor("plank", "木板", ResourceCategory.PROCESSED),
                ResourceDescriptor("brick", "石材", ResourceCategory.PROCESSED),
                ResourceDescriptor("ingot", "金属锭", ResourceCategory.PROCESSED),
                ResourceDescriptor("tech_point", "科技点", ResourceCategory.SPECIAL),
                ResourceDescriptor("ration", "居民口粮", ResourceCategory.SPECIAL),
            ),
            buildings = listOf(
                BuildingDescriptor("mine", "矿场", Material.IRON_PICKAXE),
                BuildingDescriptor("farm", "农场", Material.IRON_HOE),
                BuildingDescriptor("lumberyard", "伐木场", Material.IRON_AXE),
                BuildingDescriptor("quarry", "采石场", Material.STONE_PICKAXE),
                BuildingDescriptor("sawmill", "木材加工厂", Material.STICK),
                BuildingDescriptor("masonry", "石材加工厂", Material.BRICK),
                BuildingDescriptor("alchemy", "炼金厂", Material.BLAZE_POWDER),
                BuildingDescriptor("barracks", "兵营", Material.IRON_SWORD),
                BuildingDescriptor("watchtower", "哨塔", Material.SPYGLASS),
                BuildingDescriptor("academy", "学院", Material.BOOK),
                BuildingDescriptor("warehouse", "仓库", Material.BARREL),
            ),
        )
    }
}
