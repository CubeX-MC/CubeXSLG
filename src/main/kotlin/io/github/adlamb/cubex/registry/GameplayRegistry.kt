package io.github.adlamb.cubex.registry

import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.InputStreamReader

enum class ResourceCategory {
    BASIC,
    PROCESSED,
    SPECIAL,
}

enum class BuildingKind {
    GATHERING,
    PROCESSING,
    DEFENSE,
    SUPPORT,
    UTILITY,
}

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

data class TownLevelDefinition(
    val level: Int,
    val radius: Int,
    val buildingLimit: Int,
    val residentLimit: Int,
)

data class ResourceDescriptor(
    val key: String,
    val displayName: String,
    val category: ResourceCategory,
)

data class BlockSpec(
    val dx: Int,
    val dy: Int,
    val dz: Int,
    val material: Material,
)

data class ProductionRecipe(
    val input: Map<String, Long> = emptyMap(),
    val output: Map<String, Long> = emptyMap(),
    val cycleTicks: Int = 20,
)

data class BuildingDescriptor(
    val key: String,
    val displayName: String,
    val wandMaterial: Material,
    val coreMaterial: Material,
    val kind: BuildingKind,
    val buildCost: Map<String, Long>,
    val footprint: List<BlockSpec>,
    val recipe: ProductionRecipe? = null,
    val powerProduction: Int = 0,
    val powerCost: Int = 0,
)

data class TechNode(
    val key: String,
    val displayName: String,
    val branch: TechBranch,
    val townLevel: Int,
    val prerequisites: List<String>,
    val cost: Map<String, Long>,
    val unlockBuildings: Set<String> = emptySet(),
    val productionMultipliers: Map<String, Double> = emptyMap(),
)

data class GameplayRegistry(
    val resources: Map<String, ResourceDescriptor>,
    val buildings: Map<String, BuildingDescriptor>,
    val townLevels: Map<Int, TownLevelDefinition>,
    val techNodes: Map<String, TechNode>,
) {
    fun findResource(key: String): ResourceDescriptor? = resources[key.lowercase()]

    fun findBuilding(key: String): BuildingDescriptor? = buildings[key.lowercase()]

    fun levelFor(level: Int): TownLevelDefinition = townLevels[level] ?: townLevels.maxBy { it.key }.value

    fun tech(key: String): TechNode? = techNodes[key.lowercase()]

    fun unlocksBuilding(researchedTechs: Set<String>, buildingKey: String): Boolean {
        if (buildingKey.equals("town_hall", ignoreCase = true)) {
            return true
        }
        return techNodes.values.any { node ->
            node.unlockBuildings.any { it.equals(buildingKey, ignoreCase = true) } &&
                researchedTechs.contains(node.key)
        }
    }

    fun productionMultiplier(researchedTechs: Set<String>, resourceKey: String): Double {
        return techNodes.values
            .filter { researchedTechs.contains(it.key) }
            .sumOf { it.productionMultipliers[resourceKey.lowercase()] ?: 0.0 }
            .let { 1.0 + it }
    }

    companion object {
        fun load(plugin: JavaPlugin): GameplayRegistry {
            fun readYaml(path: String): YamlConfiguration {
                val stream = requireNotNull(plugin.getResource(path)) { "Missing gameplay resource: $path" }
                return YamlConfiguration.loadConfiguration(InputStreamReader(stream))
            }

            val resources = readYaml("gameplay/resources.yml")
            val buildings = readYaml("gameplay/buildings.yml")
            val towns = readYaml("gameplay/town.yml")
            val tech = readYaml("gameplay/tech.yml")

            return GameplayRegistry(
                resources = resources.sectionAsMap("resources") { key, section ->
                    ResourceDescriptor(
                        key = key,
                        displayName = section.getString("display-name", key).orEmpty(),
                        category = ResourceCategory.valueOf(section.getString("category", "BASIC").orEmpty().uppercase()),
                    )
                },
                buildings = buildings.sectionAsMap("buildings") { key, section ->
                    val template = section.getMapList("footprint").mapNotNull { row ->
                        val dx = (row["dx"] as? Number)?.toInt() ?: return@mapNotNull null
                        val dy = (row["dy"] as? Number)?.toInt() ?: return@mapNotNull null
                        val dz = (row["dz"] as? Number)?.toInt() ?: return@mapNotNull null
                        val material = Material.valueOf((row["material"] as? String).orEmpty().uppercase())
                        BlockSpec(dx, dy, dz, material)
                    }
                    BuildingDescriptor(
                        key = key,
                        displayName = section.getString("display-name", key).orEmpty(),
                        wandMaterial = Material.valueOf(section.getString("wand-material", "BARREL").orEmpty().uppercase()),
                        coreMaterial = Material.valueOf(section.getString("core-material", "BARREL").orEmpty().uppercase()),
                        kind = BuildingKind.valueOf(section.getString("kind", "UTILITY").orEmpty().uppercase()),
                        buildCost = section.stringLongMap("build-cost"),
                        footprint = template.ifEmpty { listOf(BlockSpec(0, 0, 0, Material.BARREL)) },
                        recipe = section.getConfigurationSection("recipe")?.let { recipeSection ->
                            ProductionRecipe(
                                input = recipeSection.stringLongMap("input"),
                                output = recipeSection.stringLongMap("output"),
                                cycleTicks = recipeSection.getInt("cycle-ticks", 20),
                            )
                        },
                        powerProduction = section.getInt("power-production", 0).coerceAtLeast(0),
                        powerCost = section.getInt("power-cost", 0).coerceAtLeast(0),
                    )
                },
                townLevels = towns.getConfigurationSection("levels")?.getKeys(false)?.associate { key ->
                    val section = requireNotNull(towns.getConfigurationSection("levels.$key")) { "Missing section levels.$key" }
                    val level = key.toIntOrNull() ?: section.getInt("level", 1)
                    level to TownLevelDefinition(
                        level = level,
                        radius = section.getInt("radius", 24),
                        buildingLimit = section.getInt("building-limit", 6),
                        residentLimit = section.getInt("resident-limit", 8),
                    )
                } ?: emptyMap(),
                techNodes = tech.sectionAsMap("tech") { key, section ->
                    TechNode(
                        key = key,
                        displayName = section.getString("display-name", key).orEmpty(),
                        branch = TechBranch.valueOf(section.getString("branch", "PRODUCTION").orEmpty().uppercase()),
                        townLevel = section.getInt("town-level", 1),
                        prerequisites = section.getStringList("prerequisites").map { it.lowercase() },
                        cost = section.stringLongMap("cost"),
                        unlockBuildings = section.getStringList("unlock-buildings").map { it.lowercase() }.toSet(),
                        productionMultipliers = section.doubleMap("production-multipliers"),
                    )
                },
            )
        }

        private fun ConfigurationSection.stringLongMap(path: String): Map<String, Long> {
            val section = getConfigurationSection(path) ?: return emptyMap()
            return section.getKeys(false).associate { key -> key.lowercase() to section.getLong(key, 0L) }
        }

        private fun ConfigurationSection.doubleMap(path: String): Map<String, Double> {
            val section = getConfigurationSection(path) ?: return emptyMap()
            return section.getKeys(false).associate { key -> key.lowercase() to section.getDouble(key, 0.0) }
        }

        private fun <T> YamlConfiguration.sectionAsMap(
            root: String,
            mapper: (String, ConfigurationSection) -> T,
        ): Map<String, T> {
            val section = getConfigurationSection(root) ?: return emptyMap()
            return section.getKeys(false).associate { key ->
                val lower = key.lowercase()
                lower to mapper(lower, requireNotNull(section.getConfigurationSection(key)) { "Missing section $root.$key" })
            }
        }
    }
}
