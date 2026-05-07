package io.github.adlamb.cubex.gameplay

import io.github.adlamb.cubex.config.PluginConfigs
import io.github.adlamb.cubex.database.DatabaseManager
import io.github.adlamb.cubex.gameplay.model.*
import io.github.adlamb.cubex.gameplay.storage.*
import io.github.adlamb.cubex.menu.MenuBodyEntry
import io.github.adlamb.cubex.menu.MenuButtonContext
import io.github.adlamb.cubex.menu.MenuDynamicEntry
import io.github.adlamb.cubex.menu.MenuFactory
import io.github.adlamb.cubex.menu.MenuId
import io.github.adlamb.cubex.menu.MenuRenderContext
import io.github.adlamb.cubex.message.MessageService
import io.github.adlamb.cubex.platform.SchedulerFacade
import io.github.adlamb.cubex.registry.BuildingDescriptor
import io.github.adlamb.cubex.registry.BuildingKind
import io.github.adlamb.cubex.registry.GameplayRegistry
import io.github.adlamb.cubex.registry.ResourceCategory
import io.github.adlamb.cubex.shared.MarkerKeys
import io.github.adlamb.cubex.util.SchematicDimensions
import io.github.adlamb.cubex.util.SchematicLoader
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.TileState
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.entity.Villager
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

private const val TOWN_HALL_KEY = "town_hall"

class GameplayFacade(
    private val plugin: JavaPlugin,
    private val configs: PluginConfigs,
    private val messages: MessageService,
    private val scheduler: SchedulerFacade,
    private val database: DatabaseManager,
    private val registry: GameplayRegistry,
    private val keys: MarkerKeys,
    private val menuFactory: MenuFactory,
) {
    val repository = GameplayRepository()
    private val schematicLoader = SchematicLoader(plugin)
    private val started = AtomicBoolean(false)
    private val buildingBoundsMap = mutableMapOf<BuildingId, BuildingBounds>()
    private var tickCounter = 0

    data class BuildingBounds(
        val buildingId: BuildingId,
        val world: String,
        val minX: Int,
        val minY: Int,
        val minZ: Int,
        val maxX: Int,
        val maxY: Int,
        val maxZ: Int,
    )

    fun initialize() {
        database.initialize()
        repository.initializeSchema()
        restoreWorldProjection()
    }

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }

        scheduler.global().runLater(100) {
            loadBuildingBounds()
        }

        scheduler.global().runTimer(20, 20) {
            tickWorld()
        }
    }

    fun townOf(player: Player): TownState? = repository.townByOwner(player.uniqueId)

    fun createTown(player: Player, name: String): TownState? {
        if (repository.townByOwner(player.uniqueId) != null) {
            messages.send(player, "town.create.exists")
            return null
        }

        // 验证城镇名：只允许英文字母、数字、下划线
        val trimmedName = name.trim()
        if (trimmedName.isNotEmpty() && !trimmedName.matches(Regex("[a-zA-Z0-9_]+"))) {
            messages.send(player, "town.create.invalid-name")
            return null
        }

        val location = player.location.block.location.add(0.5, 0.0, 0.5)
        val townLevel = registry.levelFor(1)
        val overlap = repository.towns().any { existing ->
            existing.world == location.world.name && existing.location(plugin)?.distance(location)?.let { it <= existing.radius + townLevel.radius } == true
        }
        if (overlap) {
            messages.send(player, "town.create.overlap")
            return null
        }
        val now = System.currentTimeMillis()
        val town = TownState(
            id = TownId(UUID.randomUUID().toString()),
            ownerUuid = player.uniqueId,
            name = trimmedName.ifBlank { "Town_${player.name}" },
            world = location.world.name,
            x = location.x,
            y = location.y,
            z = location.z,
            yaw = location.yaw,
            pitch = location.pitch,
            level = 1,
            radius = townLevel.radius,
            buildingLimit = townLevel.buildingLimit,
            residentLimit = townLevel.residentLimit,
            createdAt = now,
            updatedAt = now,
        )
        repository.saveTown(town)

        listOf(
            "wood" to 100L,
            "stone" to 100L,
            "ore" to 50L,
            "food" to 100L,
            "plank" to 20L,
        ).forEach { (key, amount) ->
            repository.setBalance(town.id, key, amount)
        }

        markInitialTech(town, player.uniqueId)
        placeTownHall(town, location)
        messages.send(player, "town.create.success", Placeholder.unparsed("name", town.name))
        return town
    }

    fun showBorder(player: Player): Boolean {
        val town = townOf(player) ?: return false
        val center = town.location(plugin) ?: return false
        val radius = town.radius.toDouble()
        val dust = Particle.DustOptions(Color.YELLOW, 1.2f)

        previewBorder(center, radius, dust, 15)
        messages.send(player, "town.border.preview", Placeholder.unparsed("radius", town.radius.toString()))
        return true
    }

    private fun lineEntry(line: String): MenuBodyEntry =
        MenuBodyEntry(mapOf("line" to Placeholder.unparsed("line", line)))

    fun openTownHall(player: Player) {
        val town = townOf(player)
        menuFactory.open(
            player = player,
            menuId = MenuId.TOWN_HALL,
            context = MenuRenderContext(
                bodyEntries = listOf(
                    lineEntry("城镇: ${town?.name ?: "未创建"}"),
                    lineEntry("建筑上限: ${town?.buildingLimit ?: 0}"),
                    lineEntry("居民上限: ${town?.residentLimit ?: 0}"),
                    lineEntry("领地半径: ${town?.radius ?: 0}"),
                ),
                buttons = mapOf(
                    "storage" to MenuButtonContext(action = { openStorage(it) }),
                    "residents" to MenuButtonContext(action = { openResidents(it) }),
                    "tech" to MenuButtonContext(action = { openTech(it) }),
                    "production" to MenuButtonContext(action = { openProduction(it) }),
                    "combat" to MenuButtonContext(action = { openCombat(it) }),
                    "logistics" to MenuButtonContext(action = { openLogistics(it) }),
                    "border" to MenuButtonContext(action = {
                        showBorder(it)
                        Unit
                    }),
                ),
            ),
        )
    }

    fun openStorage(player: Player) {
        val town = townOf(player) ?: return messages.send(player, "town.missing")
        val balances = repository.loadBalances(town.id)
        val latest = repository.ledger(town.id, 5)
        val body = registry.resources.values.sortedBy { it.category.ordinal }.map { resource ->
            val amount = balances[resource.key] ?: 0L
            lineEntry("${resource.displayName}: $amount")
        } + lineEntry("最近变动: ${latest.size} 条")
        menuFactory.open(
            player = player,
            menuId = MenuId.STORAGE,
            context = MenuRenderContext(
                bodyEntries = body,
                buttons = mapOf(
                    "history" to MenuButtonContext(action = { sendResourceHistory(it, town.id) }),
                    "stats" to MenuButtonContext(action = { sendResourceStats(it, town.id) }),
                ),
            ),
        )
    }

    fun openResidents(player: Player) {
        val town = townOf(player) ?: return messages.send(player, "town.missing")
        val residents = repository.residentsByTown(town.id)
        val body = buildList {
            add(lineEntry("居民数量: ${residents.size}/${town.residentLimit}"))
            residents.take(7).forEach { resident ->
                add(lineEntry("${resident.name} | 力:${resident.strength} 敏:${resident.agility} 智:${resident.intelligence}"))
            }
        }
        menuFactory.open(
            player = player,
            menuId = MenuId.RESIDENT,
            context = MenuRenderContext(
                bodyEntries = body,
                buttons = mapOf(
                    "recruit" to MenuButtonContext(action = {
                        recruit(it)
                        Unit
                    }),
                ),
            ),
        )
    }

    fun openTech(player: Player) {
        val town = townOf(player) ?: return messages.send(player, "town.missing")
        val researched = repository.techProgress(town.id)
        val techNodes = registry.techNodes.values.sortedBy { it.branch.ordinal * 100 + it.townLevel }
        val body = buildList {
            add(lineEntry("已研究: ${researched.size}"))
            techNodes.take(8).forEach { tech ->
                val flag = if (researched.contains(tech.key)) "已解锁" else "未解锁"
                add(lineEntry("${tech.displayName} [$flag]"))
            }
        }
        menuFactory.open(
            player = player,
            menuId = MenuId.TECH,
            context = MenuRenderContext(
                bodyEntries = body,
                dynamicLists = mapOf(
                    "tech-list" to techNodes.take(6).map { tech ->
                        MenuDynamicEntry(
                            placeholders = mapOf(
                                "tech_name" to Placeholder.unparsed("tech_name", tech.displayName),
                                "tech_hint" to Placeholder.unparsed("tech_hint", "点击研究"),
                            ),
                            action = {
                                research(it, tech.key)
                                Unit
                            },
                        )
                    },
                ),
            ),
        )
    }

    fun openProduction(player: Player) {
        val town = townOf(player) ?: return messages.send(player, "town.missing")
        val balances = repository.loadBalances(town.id)
        val body = listOf(
            lineEntry("木材: ${balances["wood"] ?: 0L}"),
            lineEntry("石头: ${balances["stone"] ?: 0L}"),
            lineEntry("矿石: ${balances["ore"] ?: 0L}"),
            lineEntry("粮食: ${balances["food"] ?: 0L}"),
        )
        menuFactory.open(
            player = player,
            menuId = MenuId.PRODUCTION,
            context = MenuRenderContext(bodyEntries = body),
        )
    }

    fun openBuildingMenu(player: Player, buildingId: String) {
        val building = repository.buildingById(BuildingId(buildingId)) ?: return
        val descriptor = registry.findBuilding(building.buildingKey)
        val body = buildList {
            add(lineEntry("建筑: ${descriptor?.displayName ?: building.buildingKey}"))
            add(lineEntry("等级: ${building.level}"))
            add(lineEntry("生命值: ${building.health}/${building.maxHealth}"))
            add(lineEntry("状态: ${if (building.collapsed) "坍塌" else "运行中"}"))
        }
        menuFactory.open(
            player = player,
            menuId = MenuId.BUILDING,
            context = MenuRenderContext(
                placeholders = mapOf(
                    "building_name" to Placeholder.unparsed("building_name", descriptor?.displayName ?: building.buildingKey),
                ),
                bodyEntries = body,
                buttons = mapOf(
                    "upgrade" to MenuButtonContext(action = {
                        upgradeBuilding(it, building.id.value)
                        Unit
                    }),
                    "move" to MenuButtonContext(action = {
                        requestMoveById(it, building.id.value)
                        Unit
                    }),
                    "delete" to MenuButtonContext(action = {
                        requestDeleteById(it, building.id.value)
                        Unit
                    }),
                    "repair" to MenuButtonContext(action = {
                        repairBuilding(it, building.id.value)
                        Unit
                    }),
                ),
            ),
        )
    }

    fun openCombat(player: Player) {
        menuFactory.openInfo(player, MenuId.COMBAT, "建筑受损、哨塔防御和兵营训练状态由数据库维护。")
    }

    fun openLogistics(player: Player) {
        val town = townOf(player) ?: return messages.send(player, "town.missing")
        val routes = repository.routes(town.id)
        val body = buildList {
            add(lineEntry("路由数量: ${routes.size}"))
            routes.take(5).forEach { route ->
                add(lineEntry("${route.name}: ${route.throughput}/s, 饱和 ${"%.0f".format(route.saturation * 100)}%"))
            }
        }
        menuFactory.open(
            player = player,
            menuId = MenuId.LOGISTICS,
            context = MenuRenderContext(bodyEntries = body),
        )
    }

    fun openRpgLink(player: Player) {
        menuFactory.openInfo(player, MenuId.RPG_LINK, "仅保留桥接状态，不接外部插件。")
    }

    fun giveWand(player: Player, buildingKey: String): Boolean {
        val town = townOf(player) ?: return false
        if (!canUseBuilding(town.id, buildingKey)) {
            messages.send(player, "building.locked", Placeholder.unparsed("building", buildingKey))
            return false
        }
        val descriptor = registry.findBuilding(buildingKey) ?: return false
        player.inventory.addItem(createWand(descriptor))
        messages.send(player, "command.wand.given", Placeholder.unparsed("building", descriptor.displayName))
        return true
    }

    fun createWand(building: BuildingDescriptor): ItemStack = ItemStack(building.wandMaterial).apply {
        editMeta { meta ->
            meta.displayName(Component.text("${building.displayName}建筑核心"))
            meta.lore(listOf(Component.text("右键放置，F 键旋转由后续扩展。")))
            meta.persistentDataContainer.set(keys.buildingType, PersistentDataType.STRING, building.key)
        }
    }

    fun buildingFrom(item: ItemStack?): String? = item?.itemMeta?.persistentDataContainer?.get(keys.buildingType, PersistentDataType.STRING)

    fun getBuildingDimensions(buildingKey: String, level: Int): SchematicDimensions? {
        return schematicLoader.getSchematicDimensions(buildingKey, level)
    }

    fun playerHasTownWithLocation(player: Player, location: Location): Boolean {
        val town = townOf(player) ?: return false
        return isWithinTown(town, location)
    }

    fun getPendingMoveInfo(player: Player): Pair<String, Int>? {
        val town = townOf(player) ?: return null
        val pending = repository.pendingActions(town.id, player.uniqueId).firstOrNull { it.actionType == "move" } ?: return null
        val building = repository.buildingById(BuildingId(pending.payload)) ?: return null
        return Pair(building.buildingKey, building.level)
    }

    fun handleWandPlacement(player: Player, buildingKey: String, block: Block, face: BlockFace): Boolean {
        val town = townOf(player) ?: return false
        val descriptor = registry.findBuilding(buildingKey) ?: return false
        if (!canUseBuilding(town.id, buildingKey)) {
            messages.send(player, "building.locked", Placeholder.unparsed("building", descriptor.displayName))
            return false
        }

        val base = block.getRelative(face).location.block.location
        if (!isWithinTown(town, base)) {
            messages.send(player, "building.outside-town", Placeholder.unparsed("building", descriptor.displayName))
            return false
        }
        
        // 检查单区块限制
        if (!supportsSingleChunkFootprint(base, descriptor)) {
            messages.send(player, "building.invalid-terrain", Placeholder.unparsed("building", descriptor.displayName))
            return false
        }

        if (checkBuildingOverlap(buildingKey, base)) {
            messages.send(player, "building.overlap", Placeholder.unparsed("building", descriptor.displayName))
            return false
        }
        
        if (repository.buildingsByTown(town.id).count { !it.collapsed } >= town.buildingLimit) {
            messages.send(player, "building.limit-reached", Placeholder.unparsed("limit", town.buildingLimit.toString()))
            return false
        }

        val cost = scaledCost(descriptor, 1)
        if (!hasResources(town.id, cost)) {
            messages.send(player, "building.insufficient", Placeholder.unparsed("building", descriptor.displayName))
            return false
        }

        val buildingId = BuildingId(UUID.randomUUID().toString())
        
        val schemFileName = schematicLoader.getSchematicFileName(buildingKey, 1)
        
        // 使用 WorldEdit 加载 schematic（异步处理标记）
        val future = schematicLoader.pasteSchematicAndScan(base, schemFileName)
        if (future == null) {
            messages.send(player, "building.failed", Placeholder.unparsed("building", descriptor.displayName))
            return false
        }
        
        // 等待异步完成并处理结果
        future.thenAccept { result ->
            var actualLocation = base
            
            // 如果有核心方块标记，更新位置并写入 NBT 数据
            result.markers["CORE"]?.let { coreLoc ->
                actualLocation = coreLoc
                val coreBlock = coreLoc.block
                if (coreBlock.type == Material.BARREL && coreBlock.state is TileState) {
                    val tileState = coreBlock.state as TileState
                    val pdc = tileState.persistentDataContainer
                    pdc.set(keys.townId, PersistentDataType.STRING, town.id.value)
                    pdc.set(keys.buildingId, PersistentDataType.STRING, buildingId.value)
                    pdc.set(keys.buildingType, PersistentDataType.STRING, buildingKey)
                    pdc.set(keys.schemOriginX, PersistentDataType.INTEGER, result.originX)
                    pdc.set(keys.schemOriginY, PersistentDataType.INTEGER, result.originY)
                    pdc.set(keys.schemOriginZ, PersistentDataType.INTEGER, result.originZ)
                    pdc.set(keys.schemWidth, PersistentDataType.INTEGER, result.width)
                    pdc.set(keys.schemHeight, PersistentDataType.INTEGER, result.height)
                    pdc.set(keys.schemLength, PersistentDataType.INTEGER, result.length)
                    pdc.set(keys.schemNonAirCount, PersistentDataType.INTEGER, result.nonAirBlockCount)
                    pdc.set(keys.schemPasteX, PersistentDataType.INTEGER, result.pasteOriginX)
                    pdc.set(keys.schemPasteY, PersistentDataType.INTEGER, result.pasteOriginY)
                    pdc.set(keys.schemPasteZ, PersistentDataType.INTEGER, result.pasteOriginZ)
                    tileState.update(true, false)
                    
                    plugin.logger.info("建筑 $buildingId 核心方块已设置: ${coreLoc.blockX}, ${coreLoc.blockY}, ${coreLoc.blockZ}")
                }
            }
            
            val actualHealth = result.nonAirBlockCount.coerceAtLeast(1)
            val state = BuildingState(
                id = buildingId,
                townId = town.id,
                buildingKey = descriptor.key,
                world = actualLocation.world.name,
                x = actualLocation.x + 0.5,
                y = actualLocation.y,
                z = actualLocation.z + 0.5,
                yaw = player.location.yaw,
                pitch = player.location.pitch,
                level = 1,
                health = actualHealth,
                maxHealth = actualHealth,
                active = true,
                collapsed = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            
            repository.saveBuilding(state, emptyList())
            
            registerBuildingBounds(buildingId, state.world, result.originX, result.originY, result.originZ, result.width, result.height, result.length)
            
            // 扣除资源
            cost.forEach { (resource, amount) ->
                repository.adjustBalance(town.id, resource, -amount, "建造${descriptor.displayName}", descriptor.displayName, player.uniqueId.toString())
            }
            messages.send(player, "building.built", Placeholder.unparsed("building", descriptor.displayName))
            actualLocation.world.playSound(actualLocation, Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f)
            actualLocation.world.playSound(actualLocation, Sound.BLOCK_WOOD_PLACE, 1.0f, 0.8f)
        }.exceptionally { ex ->
            plugin.logger.severe("粘贴 schematic 失败: ${ex.message}")
            ex.printStackTrace()
            messages.send(player, "building.failed", Placeholder.unparsed("building", descriptor.displayName))
            null
        }
        
        return true
    }

    fun requestRepair(player: Player): Boolean {
        val target = player.getTargetBlockExact(6) ?: return false
        val building = repository.buildingAt(target.world.name, target.x, target.y, target.z) ?: return false
        return repairBuilding(player, building.id.value)
    }

    fun repairBuilding(player: Player, buildingId: String): Boolean {
        val building = repository.buildingById(BuildingId(buildingId)) ?: return false
        val town = repository.townById(building.townId) ?: return false
        if (town.ownerUuid != player.uniqueId) {
            return false
        }

        val coreLocation = building.location(plugin) ?: return false
        val coreBlock = coreLocation.block
        if (coreBlock.type != Material.BARREL || coreBlock.state !is TileState) {
            messages.send(player, "building.no-repair-needed")
            return false
        }
        val pdc = (coreBlock.state as TileState).persistentDataContainer
        val pasteX = pdc.get(keys.schemPasteX, PersistentDataType.INTEGER) ?: return false
        val pasteY = pdc.get(keys.schemPasteY, PersistentDataType.INTEGER) ?: return false
        val pasteZ = pdc.get(keys.schemPasteZ, PersistentDataType.INTEGER) ?: return false
        val ox = pdc.get(keys.schemOriginX, PersistentDataType.INTEGER) ?: return false
        val oy = pdc.get(keys.schemOriginY, PersistentDataType.INTEGER) ?: return false
        val oz = pdc.get(keys.schemOriginZ, PersistentDataType.INTEGER) ?: return false
        val buildingKey = building.buildingKey
        val level = building.level
        val worldName = building.world
        val maxHealth = building.maxHealth

        val uid = player.uniqueId
        val townId = town.id

        scheduler.executeRegion(coreLocation) {
            val world = plugin.server.getWorld(worldName) ?: return@executeRegion

            var currentHealth = 0
            val w = pdc.get(keys.schemWidth, PersistentDataType.INTEGER) ?: return@executeRegion
            val h = pdc.get(keys.schemHeight, PersistentDataType.INTEGER) ?: return@executeRegion
            val l = pdc.get(keys.schemLength, PersistentDataType.INTEGER) ?: return@executeRegion
            for (dx in 0 until w) {
                for (dy in 0 until h) {
                    for (dz in 0 until l) {
                        val type = world.getBlockAt(ox + dx, oy + dy, oz + dz).type
                        if (type != Material.AIR && type != Material.CAVE_AIR && type != Material.VOID_AIR && type != Material.STRUCTURE_VOID) {
                            currentHealth++
                        }
                    }
                }
            }

            val missing = maxHealth - currentHealth
            if (missing <= 0) {
                messages.send(player, "building.no-repair-needed")
                return@executeRegion
            }

            val stone = (missing / 10L).coerceAtLeast(1L)
            val food = (missing / 20L).coerceAtLeast(1L)
            val cost = mapOf("stone" to stone, "food" to food)
            if (!hasResources(townId, cost)) {
                messages.send(player, "building.insufficient", Placeholder.unparsed("building", buildingKey))
                return@executeRegion
            }
            cost.forEach { (resource, amount) ->
                repository.adjustBalance(townId, resource, -amount, "修复建筑", buildingKey, uid.toString())
            }
            messages.send(player, "building.repair-cost",
                Placeholder.unparsed("stone", stone.toString()),
                Placeholder.unparsed("food", food.toString()),
            )

            val repairOrigin = Location(world, pasteX.toDouble(), pasteY.toDouble(), pasteZ.toDouble())
            val schemFileName = schematicLoader.getSchematicFileName(buildingKey, level)
            val future = schematicLoader.pasteSchematicAndScan(repairOrigin, schemFileName, ignoreAirBlocks = true)
            if (future == null) {
                messages.send(player, "building.failed", Placeholder.unparsed("building", buildingKey))
                return@executeRegion
            }
            future.thenAccept { result ->
                result.markers["CORE"]?.let { coreLoc ->
                    val newCoreBlock = coreLoc.block
                    if (newCoreBlock.type == Material.BARREL && newCoreBlock.state is TileState) {
                        val tileState = newCoreBlock.state as TileState
                        val newPdc = tileState.persistentDataContainer
                        newPdc.set(keys.townId, PersistentDataType.STRING, townId.value)
                        newPdc.set(keys.buildingId, PersistentDataType.STRING, buildingId)
                        newPdc.set(keys.buildingType, PersistentDataType.STRING, buildingKey)
                        newPdc.set(keys.schemOriginX, PersistentDataType.INTEGER, ox)
                        newPdc.set(keys.schemOriginY, PersistentDataType.INTEGER, oy)
                        newPdc.set(keys.schemOriginZ, PersistentDataType.INTEGER, oz)
                        newPdc.set(keys.schemWidth, PersistentDataType.INTEGER, w)
                        newPdc.set(keys.schemHeight, PersistentDataType.INTEGER, h)
                        newPdc.set(keys.schemLength, PersistentDataType.INTEGER, l)
                        newPdc.set(keys.schemNonAirCount, PersistentDataType.INTEGER, maxHealth)
                        newPdc.set(keys.schemPasteX, PersistentDataType.INTEGER, pasteX)
                        newPdc.set(keys.schemPasteY, PersistentDataType.INTEGER, pasteY)
                        newPdc.set(keys.schemPasteZ, PersistentDataType.INTEGER, pasteZ)
                        tileState.update(true, false)

                        val newBuilding = repository.buildingById(BuildingId(buildingId)) ?: return@thenAccept
                        val updatedBuilding = newBuilding.copy(
                            x = coreLoc.x + 0.5,
                            y = coreLoc.y,
                            z = coreLoc.z + 0.5,
                        )
                        repository.updateBuilding(updatedBuilding)
                        unregisterBuildingBounds(BuildingId(buildingId))
                        registerBuildingBounds(BuildingId(buildingId), worldName, ox, oy, oz, w, h, l)
                    }
                }
                recalculateHealthInternal(repository.buildingById(BuildingId(buildingId)) ?: return@thenAccept)
                messages.send(player, "building.repaired", Placeholder.unparsed("building", buildingKey))
                coreLocation.world.playSound(coreLocation, Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f)
            }.exceptionally { ex ->
                plugin.logger.severe("修复建筑失败: ${ex.message}")
                messages.send(player, "building.failed", Placeholder.unparsed("building", buildingKey))
                null
            }
        }
        return true
    }

    fun upgradeBuilding(player: Player, buildingId: String): Boolean {
        val building = repository.buildingById(BuildingId(buildingId)) ?: return false
        val town = repository.townById(building.townId) ?: return false
        if (town.ownerUuid != player.uniqueId) {
            return false
        }
        val descriptor = registry.findBuilding(building.buildingKey) ?: return false
        val cost = descriptor.buildCost.mapValues { (_, amount) -> ceil(amount * (1.0 + 0.65 * building.level)).toLong() }
        if (!hasResources(town.id, cost)) {
            messages.send(player, "building.insufficient", Placeholder.unparsed("building", descriptor.displayName))
            return false
        }
        cost.forEach { (resource, amount) ->
            repository.adjustBalance(town.id, resource, -amount, "升级建筑", descriptor.displayName, player.uniqueId.toString())
        }
        val newLevel = building.level + 1
        repository.updateBuilding(building.copy(level = newLevel, updatedAt = System.currentTimeMillis()))
        messages.send(player, "building.upgraded", Placeholder.unparsed("building", descriptor.displayName))
        building.location(plugin)?.let { loc ->
            loc.world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f)
            loc.world.playSound(loc, Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f)
        }
        return true
    }

    fun requestDelete(player: Player): Boolean {
        val target = player.getTargetBlockExact(6) ?: return false
        val building = repository.buildingAt(target.world.name, target.x, target.y, target.z) ?: return false
        return requestDeleteById(player, building.id.value)
    }

    fun requestDeleteById(player: Player, buildingId: String): Boolean {
        val building = repository.buildingById(BuildingId(buildingId)) ?: return false
        val town = repository.townById(building.townId) ?: return false
        if (town.ownerUuid != player.uniqueId) {
            return false
        }
        repository.createPendingAction(
            PendingAction(
                townId = town.id,
                actorUuid = player.uniqueId.toString(),
                actionType = "delete",
                payload = building.id.value,
                expiresAt = System.currentTimeMillis() + 60_000,
                createdAt = System.currentTimeMillis(),
            ),
        )
        messages.send(player, "building.pending-delete")
        return true
    }

    fun requestMove(player: Player): Boolean {
        val target = player.getTargetBlockExact(6) ?: return false
        val building = repository.buildingAt(target.world.name, target.x, target.y, target.z) ?: return false
        return requestMoveById(player, building.id.value)
    }

    fun requestMoveById(player: Player, buildingId: String): Boolean {
        val building = repository.buildingById(BuildingId(buildingId)) ?: return false
        val town = repository.townById(building.townId) ?: return false
        if (town.ownerUuid != player.uniqueId) {
            return false
        }
        repository.createPendingAction(
            PendingAction(
                townId = town.id,
                actorUuid = player.uniqueId.toString(),
                actionType = "move",
                payload = building.id.value,
                expiresAt = System.currentTimeMillis() + 60_000,
                createdAt = System.currentTimeMillis(),
            ),
        )
        messages.send(player, "building.pending-move")
        return true
    }

    fun confirm(player: Player): Boolean {
        val town = townOf(player) ?: return false
        val pending = repository.pendingActions(town.id, player.uniqueId).firstOrNull() ?: return false
        return when (pending.actionType) {
            "delete" -> {
                val building = repository.buildingById(BuildingId(pending.payload)) ?: return false
                // schematic 模式下，需要手动破坏方块或记录位置供后续清理
                removeBuildingProjection(building)
                repository.deleteBuilding(building.id)
                repository.deletePendingAction(pending.id ?: return false)
                dropCore(building)
                messages.send(player, "building.deleted")
                building.location(plugin)?.let { loc ->
                    loc.world.playSound(loc, Sound.BLOCK_ANVIL_BREAK, 1.0f, 0.8f)
                    loc.world.playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 0.8f)
                }
                true
            }

            "move" -> {
                val building = repository.buildingById(BuildingId(pending.payload)) ?: return false
                val target = player.getTargetBlockExact(6) ?: return false
                val targetLoc = target.location
                val face = player.rayTraceBlocks(6.0)?.hitBlockFace ?: BlockFace.UP
                val newBase = target.getRelative(face).location.block.location

                val coreLocation = building.location(plugin) ?: return false
                repository.deletePendingAction(pending.id ?: return false)
                removeBuildingProjection(building)
                unregisterBuildingBounds(building.id)

                val schemFileName = schematicLoader.getSchematicFileName(building.buildingKey, building.level)
                val future = schematicLoader.pasteSchematicAndScan(newBase, schemFileName)
                if (future == null) {
                    messages.send(player, "building.failed", Placeholder.unparsed("building", building.buildingKey))
                    return false
                }
                future.thenAccept { result ->
                    var actualLocation = newBase
                    result.markers["CORE"]?.let { coreLoc ->
                        actualLocation = coreLoc
                        val coreBlock = coreLoc.block
                        if (coreBlock.type == Material.BARREL && coreBlock.state is TileState) {
                            val tileState = coreBlock.state as TileState
                            val newPdc = tileState.persistentDataContainer
                            newPdc.set(keys.townId, PersistentDataType.STRING, town.id.value)
                            newPdc.set(keys.buildingId, PersistentDataType.STRING, building.id.value)
                            newPdc.set(keys.buildingType, PersistentDataType.STRING, building.buildingKey)
                            newPdc.set(keys.schemOriginX, PersistentDataType.INTEGER, result.originX)
                            newPdc.set(keys.schemOriginY, PersistentDataType.INTEGER, result.originY)
                            newPdc.set(keys.schemOriginZ, PersistentDataType.INTEGER, result.originZ)
                            newPdc.set(keys.schemWidth, PersistentDataType.INTEGER, result.width)
                            newPdc.set(keys.schemHeight, PersistentDataType.INTEGER, result.height)
                            newPdc.set(keys.schemLength, PersistentDataType.INTEGER, result.length)
                            newPdc.set(keys.schemNonAirCount, PersistentDataType.INTEGER, result.nonAirBlockCount)
                            newPdc.set(keys.schemPasteX, PersistentDataType.INTEGER, result.pasteOriginX)
                            newPdc.set(keys.schemPasteY, PersistentDataType.INTEGER, result.pasteOriginY)
                            newPdc.set(keys.schemPasteZ, PersistentDataType.INTEGER, result.pasteOriginZ)
                            tileState.update(true, false)
                        }
                    }
                    val updated = building.copy(
                        world = actualLocation.world.name,
                        x = actualLocation.x + 0.5,
                        y = actualLocation.y,
                        z = actualLocation.z + 0.5,
                        health = result.nonAirBlockCount.coerceAtLeast(1),
                        maxHealth = result.nonAirBlockCount.coerceAtLeast(1),
                        updatedAt = System.currentTimeMillis(),
                    )
                    repository.updateBuilding(updated)
                    registerBuildingBounds(building.id, updated.world, result.originX, result.originY, result.originZ, result.width, result.height, result.length)
                    messages.send(player, "building.moved")
                }.exceptionally { ex ->
                    plugin.logger.severe("移动建筑失败: ${ex.message}")
                    messages.send(player, "building.failed", Placeholder.unparsed("building", building.buildingKey))
                    null
                }
                true
            }

            else -> false
        }
    }

    fun recruit(player: Player): Boolean {
        val town = townOf(player) ?: return false
        val count = repository.residentsByTown(town.id).count { it.active }
        if (count >= town.residentLimit) {
            messages.send(player, "resident.limit-reached")
            return false
        }
        if (!hasResources(town.id, mapOf("food" to 50L))) {
            messages.send(player, "resident.insufficient-food")
            return false
        }

        val home = town.location(plugin) ?: return false
        val spawn = home.clone().add(1.0, 0.0, 0.0)
        val residentId = ResidentId(UUID.randomUUID().toString())
        val createdAt = System.currentTimeMillis()
        val strength = 1 + (0..3).random()
        val agility = 1 + (0..3).random()
        val intelligence = 1 + (0..3).random()
        val endurance = 1 + (0..3).random()
        val management = 1 + (0..3).random()

        repository.adjustBalance(town.id, "food", -50, "招募居民", "居民系统", player.uniqueId.toString())
        scheduler.executeRegion(spawn) {
            val villager = spawn.world.spawn(spawn, Villager::class.java)
            val resident = ResidentState(
                id = residentId,
                townId = town.id,
                uuid = villager.uniqueId,
                name = "居民-${villager.uniqueId.toString().takeLast(4)}",
                world = villager.world.name,
                x = villager.location.x,
                y = villager.location.y,
                z = villager.location.z,
                homeX = home.x,
                homeY = home.y,
                homeZ = home.z,
                jobBuildingId = null,
                jobRole = null,
                strength = strength,
                agility = agility,
                intelligence = intelligence,
                endurance = endurance,
                management = management,
                active = true,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
            refreshResidentEntity(villager, resident, home, isDaytime = true)
            repository.saveResident(resident)
        }
        messages.send(player, "resident.recruited", Placeholder.unparsed("name", "居民"))
        return true
    }

    fun research(player: Player, techKey: String): Boolean {
        val town = townOf(player) ?: return false
        val node = registry.tech(techKey) ?: return false
        val researched = repository.techProgress(town.id)
        if (researched.contains(node.key)) {
            messages.send(player, "tech.already", Placeholder.unparsed("tech", node.displayName))
            return false
        }
        if (town.level < node.townLevel) {
            messages.send(
                player,
                "tech.town-level",
                Placeholder.unparsed("tech", node.displayName),
                Placeholder.unparsed("level", node.townLevel.toString()),
            )
            return false
        }
        if (node.prerequisites.any { !researched.contains(it.lowercase()) }) {
            messages.send(player, "tech.prerequisite")
            return false
        }
        if (!hasResources(town.id, node.cost)) {
            messages.send(player, "tech.insufficient", Placeholder.unparsed("tech", node.displayName))
            return false
        }
        node.cost.forEach { (resource, amount) ->
            repository.adjustBalance(town.id, resource, -amount, "研究科技", node.displayName, player.uniqueId.toString())
        }
        repository.markTech(town.id, node.key, player.uniqueId)
        messages.send(player, "tech.researched", Placeholder.unparsed("tech", node.displayName))
        return true
    }

    fun sendResourceHistory(player: Player, townId: TownId) {
        val ledger = repository.ledger(townId, 10)
        ledger.forEach { entry ->
            messages.send(
                player,
                "resource.history.line",
                Placeholder.unparsed("delta", if (entry.delta >= 0) "+${entry.delta}" else entry.delta.toString()),
                Placeholder.unparsed("resource", entry.resourceKey),
                Placeholder.unparsed("source", entry.source),
            )
        }
    }

    fun sendResourceStats(player: Player, townId: TownId) {
        val ledger = repository.ledger(townId, 100)
        val income = ledger.filter { it.delta > 0 }.sumOf { it.delta }
        val expense = ledger.filter { it.delta < 0 }.sumOf { -it.delta }
        messages.send(
            player,
            "resource.stats.line",
            Placeholder.unparsed("income", income.toString()),
            Placeholder.unparsed("expense", expense.toString()),
        )
    }

    fun handleResidentDeath(uuid: UUID): Boolean {
        val resident = repository.residentByUuid(uuid) ?: return false
        repository.deleteResidentByUuid(uuid)
        val town = repository.townById(resident.townId) ?: return true
        val owner = plugin.server.getPlayer(town.ownerUuid) ?: return true
        messages.send(owner, "resident.died", Placeholder.unparsed("name", resident.name))
        return true
    }

    private fun tickWorld() {
        try {
            tickCounter++
            val doHealthRecalc = tickCounter % 100 == 0
            repository.towns().forEach { town ->
                val researched = repository.techProgress(town.id)
                repository.buildingsByTown(town.id).forEach { building ->
                    if (!building.active || building.collapsed) {
                        return@forEach
                    }
                    val descriptor = registry.findBuilding(building.buildingKey) ?: return@forEach
                    val multiplier = levelMultiplier(building.level)
                    val recipe = descriptor.recipe
                    if (recipe == null) {
                        return@forEach
                    }
                    if (recipe.input.isNotEmpty() && !hasResources(town.id, recipe.input)) {
                        return@forEach
                    }
                    recipe.input.forEach { (resource, amount) ->
                        repository.adjustBalance(town.id, resource, -amount, "生产消耗", descriptor.displayName)
                    }
                    recipe.output.forEach { (resource, amount) ->
                        val effective = max(0L, (amount * multiplier * registry.productionMultiplier(researched, resource)).roundToInt().toLong())
                        if (effective > 0) {
                            repository.adjustBalance(town.id, resource, effective, "建筑产出", descriptor.displayName)
                        }
                    }
                }
                if (doHealthRecalc) {
                    repository.buildingsByTown(town.id).forEach { building ->
                        if (building.collapsed || !building.active) return@forEach
                        recalculateHealth(building.id)
                    }
                }
                checkBuildingHealthTicks()
                try {
                    refreshResidents(town, town.location(plugin)?.world?.time?.let { it in 0..12000L } ?: true)
                } catch (e: Exception) {
                    plugin.logger.warning("刷新居民失败 (城镇: ${town.name}): ${e.message}")
                }
                expirePendingActions(town)
            }
        } catch (e: Exception) {
            plugin.logger.severe("Tick 循环异常: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun refreshResidents(town: TownState, isDaytime: Boolean) {
        repository.residentsByTown(town.id).forEach { resident ->
            val world = plugin.server.getWorld(resident.world) ?: return@forEach
            val home = Location(world, resident.homeX, resident.homeY, resident.homeZ)
            scheduler.executeRegion(home) {
                val existing = world.getEntity(resident.uuid) as? Villager
                if (existing != null) {
                    scheduler.executeEntity(existing) {
                        refreshResidentEntity(existing, resident, home, isDaytime)
                    }
                    return@executeRegion
                }

                val spawned = world.spawn(Location(world, resident.x, resident.y, resident.z), Villager::class.java)
                val updatedResident = resident.copy(
                    uuid = spawned.uniqueId,
                    world = spawned.world.name,
                    x = spawned.location.x,
                    y = spawned.location.y,
                    z = spawned.location.z,
                    updatedAt = System.currentTimeMillis(),
                )
                refreshResidentEntity(spawned, updatedResident, home, isDaytime)
                repository.saveResident(updatedResident)
            }
        }
    }

    private fun expirePendingActions(town: TownState) {
        val now = System.currentTimeMillis()
        repository.allPendingActions().filter { it.townId == town.id && it.expiresAt < now }.forEach {
            repository.deletePendingAction(it.id ?: return@forEach)
        }
    }

    private fun restoreWorldProjection() {
        // schematic 模式下，建筑已经在放置时由 WorldEdit 投影到世界
        // 服务器重启后需要重新加载 schematic（可选功能）
        plugin.logger.info("Schematic 模式：建筑投影已由 WorldEdit 处理")
    }

    private fun placeTownHall(town: TownState, location: Location) {
        val buildingId = BuildingId(town.id.value)
        
        // 使用 schematic 加载城镇大厅（异步处理标记）
        val schemFileName = schematicLoader.getSchematicFileName(TOWN_HALL_KEY, 1)
        val future = schematicLoader.pasteSchematicAndScan(location, schemFileName)
        
        if (future == null) {
            plugin.logger.severe("无法加载城镇大厅 schematic: $schemFileName")
            return
        }
        
        future.thenAccept { result ->
            var actualLocation = location
            
            result.markers["CORE"]?.let { coreLoc ->
                actualLocation = coreLoc
                // 写入 NBT 数据
                val coreBlock = actualLocation.block
                if (coreBlock.type == Material.BARREL && coreBlock.state is TileState) {
                    val tileState = coreBlock.state as TileState
                    val pdc = tileState.persistentDataContainer
                    pdc.set(keys.townId, PersistentDataType.STRING, town.id.value)
                    pdc.set(keys.buildingId, PersistentDataType.STRING, buildingId.value)
                    pdc.set(keys.buildingType, PersistentDataType.STRING, TOWN_HALL_KEY)
                    pdc.set(keys.schemOriginX, PersistentDataType.INTEGER, result.originX)
                    pdc.set(keys.schemOriginY, PersistentDataType.INTEGER, result.originY)
                    pdc.set(keys.schemOriginZ, PersistentDataType.INTEGER, result.originZ)
                    pdc.set(keys.schemWidth, PersistentDataType.INTEGER, result.width)
                    pdc.set(keys.schemHeight, PersistentDataType.INTEGER, result.height)
                    pdc.set(keys.schemLength, PersistentDataType.INTEGER, result.length)
                    pdc.set(keys.schemNonAirCount, PersistentDataType.INTEGER, result.nonAirBlockCount)
                    pdc.set(keys.schemPasteX, PersistentDataType.INTEGER, result.pasteOriginX)
                    pdc.set(keys.schemPasteY, PersistentDataType.INTEGER, result.pasteOriginY)
                    pdc.set(keys.schemPasteZ, PersistentDataType.INTEGER, result.pasteOriginZ)
                    tileState.update(true, false)
                    
                    plugin.logger.info("城镇大厅核心方块已设置: ${actualLocation.blockX}, ${actualLocation.blockY}, ${actualLocation.blockZ}")
                }
            }
            
            val actualHealth = result.nonAirBlockCount.coerceAtLeast(1)
            val building = BuildingState(
                id = buildingId,
                townId = town.id,
                buildingKey = TOWN_HALL_KEY,
                world = actualLocation.world.name,
                x = actualLocation.x + 0.5,
                y = actualLocation.y,
                z = actualLocation.z + 0.5,
                yaw = location.yaw,
                pitch = location.pitch,
                level = 1,
                health = actualHealth,
                maxHealth = actualHealth,
                active = true,
                collapsed = false,
                createdAt = town.createdAt,
                updatedAt = town.updatedAt,
            )
            repository.saveBuilding(building, emptyList())
            
            registerBuildingBounds(buildingId, building.world, result.originX, result.originY, result.originZ, result.width, result.height, result.length)
        }.exceptionally { ex ->
            plugin.logger.severe("粘贴城镇大厅 schematic 失败: ${ex.message}")
            ex.printStackTrace()
            null
        }
    }

    fun removeBuildingProjection(building: BuildingState) {
        unregisterBuildingBounds(building.id)
        val world = plugin.server.getWorld(building.world) ?: run {
            plugin.logger.warning("建筑 ${building.id.value} 所在世界 ${building.world} 不存在，跳过清除")
            return
        }
        val coreLocation = building.location(plugin) ?: return
        val coreBlock = coreLocation.block
        if (coreBlock.type == Material.BARREL && coreBlock.state is TileState) {
            val pdc = (coreBlock.state as TileState).persistentDataContainer
            val originX = pdc.get(keys.schemOriginX, PersistentDataType.INTEGER)
            val originY = pdc.get(keys.schemOriginY, PersistentDataType.INTEGER)
            val originZ = pdc.get(keys.schemOriginZ, PersistentDataType.INTEGER)
            val width = pdc.get(keys.schemWidth, PersistentDataType.INTEGER)
            val height = pdc.get(keys.schemHeight, PersistentDataType.INTEGER)
            val length = pdc.get(keys.schemLength, PersistentDataType.INTEGER)
            if (originX != null && originY != null && originZ != null && width != null && height != null && length != null) {
                schematicLoader.removeSchematicFromWorld(originX, originY, originZ, width, height, length, world)
                plugin.logger.info("建筑 ${building.id.value} 已清除: ($width x $height x $length) at ($originX, $originY, $originZ)")
            } else {
                plugin.logger.warning("建筑 ${building.id.value} 缺少 schematic 边界信息，仅清除核心方块")
            }
        }
        if (coreBlock.type == Material.BARREL) {
            coreBlock.setType(Material.AIR)
        }
    }

    private fun dropCore(building: BuildingState) {
        registry.findBuilding(building.buildingKey)?.let { descriptor ->
            val dropLocation = building.location(plugin) ?: return@let
            scheduler.executeRegion(dropLocation) {
                dropLocation.world.dropItemNaturally(dropLocation, createWand(descriptor))
            }
        }
    }

    private fun loadBuildingBounds() {
        try {
            var loadedCount = 0
            val allBuildings = repository.towns().flatMap { town -> repository.buildingsByTown(town.id) }
            allBuildings.forEach { building ->
                if (building.collapsed) return@forEach
                val coreLocation = building.location(plugin) ?: return@forEach
                scheduler.executeRegion(coreLocation) {
                    try {
                        val coreBlock = coreLocation.block
                        if (coreBlock.type == Material.BARREL && coreBlock.state is TileState) {
                            val pdc = (coreBlock.state as TileState).persistentDataContainer
                            val ox = pdc.get(keys.schemOriginX, PersistentDataType.INTEGER) ?: return@executeRegion
                            val oy = pdc.get(keys.schemOriginY, PersistentDataType.INTEGER) ?: return@executeRegion
                            val oz = pdc.get(keys.schemOriginZ, PersistentDataType.INTEGER) ?: return@executeRegion
                            val w = pdc.get(keys.schemWidth, PersistentDataType.INTEGER) ?: return@executeRegion
                            val h = pdc.get(keys.schemHeight, PersistentDataType.INTEGER) ?: return@executeRegion
                            val l = pdc.get(keys.schemLength, PersistentDataType.INTEGER) ?: return@executeRegion
                            registerBuildingBounds(building.id, building.world, ox, oy, oz, w, h, l)
                            loadedCount++
                        }
                    } catch (e: Exception) {
                        plugin.logger.warning("加载建筑边界失败 (${building.id.value}): ${e.message}")
                    }
                }
            }
            scheduler.global().runLater(10) {
                plugin.logger.info("已加载 ${buildingBoundsMap.size} 个建筑边界")
            }
        } catch (ex: Exception) {
            plugin.logger.severe("加载建筑边界失败: ${ex.message}")
            ex.printStackTrace()
        }
    }

    fun registerBuildingBounds(buildingId: BuildingId, world: String, originX: Int, originY: Int, originZ: Int, width: Int, height: Int, length: Int) {
        buildingBoundsMap[buildingId] = BuildingBounds(
            buildingId = buildingId,
            world = world,
            minX = originX,
            minY = originY,
            minZ = originZ,
            maxX = originX + width - 1,
            maxY = originY + height - 1,
            maxZ = originZ + length - 1,
        )
    }

    fun unregisterBuildingBounds(buildingId: BuildingId) {
        buildingBoundsMap.remove(buildingId)
    }

    fun findBuildingAt(location: Location): BuildingBounds? {
        return buildingBoundsMap.values.firstOrNull { bounds ->
            bounds.world == location.world.name &&
                location.blockX in bounds.minX..bounds.maxX &&
                location.blockY in bounds.minY..bounds.maxY &&
                location.blockZ in bounds.minZ..bounds.maxZ
        }
    }

    fun hitBuildingBlock(buildingId: String): Boolean {
        val building = repository.buildingById(BuildingId(buildingId)) ?: return false
        if (building.collapsed || !building.active) return false
        val nextHealth = (building.health - 1).coerceAtLeast(0)
        if (nextHealth == building.health) return false
        val collapsed = nextHealth < building.maxHealth * 0.6
        repository.updateBuilding(building.copy(health = nextHealth, collapsed = collapsed, active = !collapsed, updatedAt = System.currentTimeMillis()))
        if (collapsed) {
            triggerBuildingExplosion(building)
        }
        return true
    }

    fun applyExplosionDamageToBuilding(buildingId: String): Boolean {
        val building = repository.buildingById(BuildingId(buildingId)) ?: return false
        if (building.collapsed || !building.active) return false
        val nextHealth = (building.health - 1).coerceAtLeast(0)
        val collapsed = nextHealth < building.maxHealth * 0.6
        repository.updateBuilding(building.copy(health = nextHealth, collapsed = collapsed, active = !collapsed, updatedAt = System.currentTimeMillis()))
        if (collapsed) {
            scheduleDeferredBuildingCleanup(building)
            triggerBuildingExplosion(building)
        }
        return collapsed
    }

    fun recalculateHealth(buildingId: BuildingId): Boolean {
        val building = repository.buildingById(buildingId) ?: return false
        val coreLocation = building.location(plugin) ?: return false
        scheduler.executeRegion(coreLocation) {
            recalculateHealthInternal(building)
        }
        return true
    }

    private fun recalculateHealthInternal(building: BuildingState) {
        val coreBlock = building.location(plugin)?.block ?: return
        if (coreBlock.type != Material.BARREL || coreBlock.state !is TileState) return
        val pdc = (coreBlock.state as TileState).persistentDataContainer
        val ox = pdc.get(keys.schemOriginX, PersistentDataType.INTEGER) ?: return
        val oy = pdc.get(keys.schemOriginY, PersistentDataType.INTEGER) ?: return
        val oz = pdc.get(keys.schemOriginZ, PersistentDataType.INTEGER) ?: return
        val w = pdc.get(keys.schemWidth, PersistentDataType.INTEGER) ?: return
        val h = pdc.get(keys.schemHeight, PersistentDataType.INTEGER) ?: return
        val l = pdc.get(keys.schemLength, PersistentDataType.INTEGER) ?: return
        val world = plugin.server.getWorld(building.world) ?: return
        var count = 0
        for (dx in 0 until w) {
            for (dy in 0 until h) {
                for (dz in 0 until l) {
                    val type = world.getBlockAt(ox + dx, oy + dy, oz + dz).type
                    if (type != Material.AIR && type != Material.CAVE_AIR && type != Material.VOID_AIR && type != Material.STRUCTURE_VOID) {
                        count++
                    }
                }
            }
        }
        val collapsed = count < building.maxHealth * 0.6
        repository.updateBuilding(building.copy(health = count, collapsed = collapsed, active = !collapsed, updatedAt = System.currentTimeMillis()))
        if (collapsed) {
            triggerBuildingExplosion(building)
        }
    }

    private fun triggerBuildingExplosion(building: BuildingState) {
        val world = plugin.server.getWorld(building.world) ?: return
        val core = building.location(plugin) ?: return
        val coreBlock = core.block
        val buildingKey = building.buildingKey
        val townId = building.townId
        val isTownHall = buildingKey == TOWN_HALL_KEY
        if (coreBlock.type == Material.BARREL && coreBlock.state is TileState) {
            val pdc = (coreBlock.state as TileState).persistentDataContainer
            pdc.remove(keys.buildingId)
            pdc.remove(keys.buildingType)
            pdc.remove(keys.townId)
            (coreBlock.state as TileState).update(true, false)
        }
        scheduler.executeRegion(core) {
            coreBlock.setType(Material.TNT)
            val tnt = world.spawn(core.clone().add(0.5, 0.0, 0.5), TNTPrimed::class.java)
            tnt.fuseTicks = 40
            tnt.yield = 3f
            world.playSound(core, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f)
            world.playSound(core, Sound.BLOCK_ANVIL_BREAK, 1.0f, 0.5f)
            plugin.logger.info("建筑 ${building.id.value} 生命值过低 (< 60%)，触发自毁")
        }
        
        scheduler.region(core).runLater(80) {
            if (isTownHall) {
                dropCoreAt(world, core, buildingKey)
                deleteTown(townId)
                plugin.logger.info("城镇大厅被摧毁，整个城镇 $townId 已删除")
            } else {
                repository.deleteBuilding(building.id)
                unregisterBuildingBounds(building.id)
                dropCoreAt(world, core, buildingKey)
                plugin.logger.info("建筑 ${building.id.value} 已从数据库中删除")
            }
        }
    }

    private fun dropCoreAt(world: org.bukkit.World, location: Location, buildingKey: String) {
        registry.findBuilding(buildingKey)?.let { descriptor ->
            scheduler.executeRegion(location) {
                world.dropItemNaturally(location, createWand(descriptor))
            }
        }
    }

    private fun scheduleDeferredBuildingCleanup(building: BuildingState) {
        // bounds already unregistered in triggerBuildingExplosion
    }

    fun checkBuildingHealthTicks() {
        repository.towns().forEach { town ->
            repository.buildingsByTown(town.id).forEach { building ->
                if (building.collapsed || !building.active) return@forEach
                if (building.maxHealth > 0 && building.health < building.maxHealth * 0.6) {
                    repository.updateBuilding(building.copy(collapsed = true, active = false, updatedAt = System.currentTimeMillis()))
                    triggerBuildingExplosion(building)
                }
            }
        }
    }

    private fun canUseBuilding(townId: TownId, buildingKey: String): Boolean {
        if (buildingKey == TOWN_HALL_KEY) {
            return true
        }
        val townTechs = repository.techProgress(townId)
        return registry.techNodes.values.any { node ->
            townTechs.contains(node.key) && node.unlockBuildings.any { it.equals(buildingKey, ignoreCase = true) }
        }
    }

    private fun hasResources(townId: TownId, cost: Map<String, Long>): Boolean {
        return cost.all { (resource, amount) -> repository.balanceOf(townId, resource) >= amount }
    }

    private fun levelMultiplier(level: Int): Double = 1.0 + 0.2 * (level - 1)

    private fun calculateMaxHealth(descriptor: BuildingDescriptor, level: Int): Int {
        return ceil(descriptor.footprint.size * 10.0 * levelMultiplier(level)).toInt().coerceAtLeast(20)
    }

    private fun scaledCost(descriptor: BuildingDescriptor, level: Int): Map<String, Long> {
        val scale = 1.0 + 0.25 * (level - 1)
        return descriptor.buildCost.mapValues { (_, amount) -> ceil(amount * scale).toLong() }
    }

    private fun isWithinTown(town: TownState, location: Location): Boolean {
        val center = town.location(plugin) ?: return false
        return center.world.name == location.world.name && center.distance(location) <= town.radius.toDouble()
    }

    private fun previewBorder(center: Location, radius: Double, dust: Particle.DustOptions, remaining: Int) {
        if (remaining <= 0) {
            return
        }
        spawnSquare(center, radius, dust)
        scheduler.region(center).runLater(20) {
            previewBorder(center, radius, dust, remaining - 1)
        }
    }

    private fun TownState.location(plugin: JavaPlugin): Location? {
        val serverWorld = plugin.server.getWorld(world) ?: return null
        return Location(serverWorld, x, y, z, yaw, pitch)
    }

    private fun BuildingState.location(plugin: JavaPlugin): Location? {
        val serverWorld = plugin.server.getWorld(world) ?: return null
        return Location(serverWorld, x, y, z, yaw, pitch)
    }

    private fun spawnSquare(center: Location, radius: Double, dust: Particle.DustOptions) {
        val world = center.world
        val minX = center.x - radius
        val maxX = center.x + radius
        val minZ = center.z - radius
        val maxZ = center.z + radius
        val y = center.y + 1.0
        val points = listOf(
            Location(world, minX, y, minZ),
            Location(world, maxX, y, minZ),
            Location(world, maxX, y, maxZ),
            Location(world, minX, y, maxZ),
        )
        points.forEach { point ->
            scheduler.executeRegion(point) {
                point.world.spawnParticle(Particle.DUST, point, 25, 0.15, 0.15, 0.15, 0.0, dust)
            }
        }
    }

    private fun refreshResidentEntity(entity: Villager, resident: ResidentState, home: Location, isDaytime: Boolean) {
        entity.customName(Component.text(resident.name))
        entity.isCustomNameVisible = true
        entity.isPersistent = true
        entity.removeWhenFarAway = false

        val target = residentTarget(resident, home, isDaytime)
        if (shouldTeleport(entity, target)) {
            entity.teleportAsync(target)
        }
    }

    private fun residentTarget(resident: ResidentState, home: Location, isDaytime: Boolean): Location {
        if (!isDaytime || resident.jobBuildingId == null) {
            return home
        }

        val building = repository.buildingById(BuildingId(resident.jobBuildingId)) ?: return home
        val world = plugin.server.getWorld(building.world) ?: return home
        return Location(world, building.x, building.y, building.z)
    }

    private fun shouldTeleport(entity: Villager, target: Location): Boolean {
        if (entity.world.uid != target.world.uid) {
            return true
        }
        return entity.location.distanceSquared(target) > 16.0
    }

    private fun checkBuildingOverlap(buildingKey: String, base: Location): Boolean {
        val dims = getBuildingDimensions(buildingKey, 1) ?: return false
        val minX = base.blockX - dims.offsetX
        val minY = base.blockY - dims.offsetY
        val minZ = base.blockZ - dims.offsetZ
        val maxX = minX + dims.width
        val maxY = minY + dims.height
        val maxZ = minZ + dims.length
        return buildingBoundsMap.values.any { existing ->
            existing.world == base.world.name &&
                !(maxX <= existing.minX || minX >= existing.maxX ||
                  maxY <= existing.minY || minY >= existing.maxY ||
                  maxZ <= existing.minZ || minZ >= existing.maxZ)
        }
    }

    private fun supportsSingleChunkFootprint(base: Location, descriptor: BuildingDescriptor): Boolean {
        val chunks = descriptor.footprint.map { spec ->
            ((base.blockX + spec.dx) shr 4) to ((base.blockZ + spec.dz) shr 4)
        }.toSet()
        if (chunks.size <= 1) {
            return true
        }

        plugin.logger.warning("CubeXSLG refuses to project building ${descriptor.key}: footprint crosses multiple chunks, which is not supported safely on Folia.")
        return false
    }

    private fun markInitialTech(town: TownState, owner: UUID) {
        listOf("base_collect", "base_agriculture", "wood_defense").forEach {
            repository.markTech(town.id, it, owner)
        }
    }

    // ========== Admin Methods ==========

    fun getAllTownNames(): List<String> {
        return repository.towns().map { it.name }
    }

    fun getTownByName(name: String): TownState? {
        return repository.towns().find { it.name.equals(name, ignoreCase = true) }
    }

    fun deleteTownByName(name: String): Boolean {
        val town = getTownByName(name) ?: return false
        deleteTown(town.id)
        return true
    }

    fun deleteTown(townId: TownId) {
        val buildings = repository.buildingsByTown(townId)
        buildings.forEach { building ->
            removeBuildingProjection(building)
        }
        repository.deleteTown(townId)
    }

    fun transferTown(townName: String, newOwnerUuid: UUID): Boolean {
        val town = getTownByName(townName) ?: return false
        val updated = town.copy(ownerUuid = newOwnerUuid, updatedAt = System.currentTimeMillis())
        repository.saveTown(updated)
        return true
    }

    fun setTownLevel(townName: String, level: Int): Boolean {
        val town = getTownByName(townName) ?: return false
        val levelDef = registry.levelFor(level)
        val updated = town.copy(
            level = level,
            radius = levelDef.radius,
            buildingLimit = levelDef.buildingLimit,
            residentLimit = levelDef.residentLimit,
            updatedAt = System.currentTimeMillis(),
        )
        repository.saveTown(updated)
        return true
    }

    fun addResourceToTown(townName: String, resource: String, amount: Long): Boolean {
        val town = getTownByName(townName) ?: return false
        repository.adjustBalance(town.id, resource, amount, "管理员添加", "Admin", null)
        return true
    }

    fun modifyResidentAttribute(residentId: String, attribute: io.github.adlamb.cubex.registry.ResidentAttribute, value: Int): Boolean {
        val resident = repository.towns().flatMap { town -> 
            repository.residentsByTown(town.id) 
        }.find { it.id.value == residentId } ?: return false

        val updated = when (attribute) {
            io.github.adlamb.cubex.registry.ResidentAttribute.STRENGTH -> resident.copy(strength = value)
            io.github.adlamb.cubex.registry.ResidentAttribute.AGILITY -> resident.copy(agility = value)
            io.github.adlamb.cubex.registry.ResidentAttribute.INTELLIGENCE -> resident.copy(intelligence = value)
            io.github.adlamb.cubex.registry.ResidentAttribute.ENDURANCE -> resident.copy(endurance = value)
            io.github.adlamb.cubex.registry.ResidentAttribute.MANAGEMENT -> resident.copy(management = value)
        }
        repository.saveResident(updated)
        return true
    }
}
