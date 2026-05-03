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
import io.github.adlamb.cubex.util.SchematicLoader
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.TileState
import org.bukkit.entity.Player
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

    fun initialize() {
        database.initialize()
        repository.initializeSchema()
        restoreWorldProjection()
    }

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
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
        val health = calculateMaxHealth(descriptor, 1)
        
        // 使用 schematic 加载建筑
        val schemFileName = schematicLoader.getSchematicFileName(buildingKey, 1)
        
        // 使用 WorldEdit 加载 schematic（同步处理标记）
        val markers = schematicLoader.pasteSchematicWithMarkers(base, schemFileName)
        if (markers == null) {
            messages.send(player, "building.failed", Placeholder.unparsed("building", descriptor.displayName))
            return false
        }
        
        var actualLocation = base
        
        // 如果有核心方块标记，更新位置并写入 NBT 数据
        markers.coreLocation?.let { coreLoc ->
            actualLocation = coreLoc
            val coreBlock = coreLoc.block
            if (coreBlock.type == Material.BARREL && coreBlock.state is TileState) {
                val tileState = coreBlock.state as TileState
                val pdc = tileState.persistentDataContainer
                pdc.set(keys.townId, PersistentDataType.STRING, town.id.value)
                pdc.set(keys.buildingId, PersistentDataType.STRING, buildingId.value)
                pdc.set(keys.buildingType, PersistentDataType.STRING, buildingKey)
                tileState.update(true, false)
                
                plugin.logger.info("建筑 $buildingId 核心方块已设置: ${coreLoc.blockX}, ${coreLoc.blockY}, ${coreLoc.blockZ}")
            }
        }
        
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
            health = health,
            maxHealth = health,
            active = true,
            collapsed = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        
        // 保存建筑状态（schematic 模式不需要存储方块列表）
        repository.saveBuilding(state, emptyList())
        
        // 扣除资源
        cost.forEach { (resource, amount) ->
            repository.adjustBalance(town.id, resource, -amount, "建造${descriptor.displayName}", descriptor.displayName, player.uniqueId.toString())
        }
        messages.send(player, "building.built", Placeholder.unparsed("building", descriptor.displayName))
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

        val missing = building.maxHealth - building.health
        if (missing <= 0) {
            messages.send(player, "building.no-repair-needed")
            return true
        }
        val stone = (missing / 10L).coerceAtLeast(1L)
        val food = (missing / 20L).coerceAtLeast(1L)
        val cost = mapOf("stone" to stone, "food" to food)
        if (!hasResources(town.id, cost)) {
            messages.send(player, "building.insufficient", Placeholder.unparsed("building", building.buildingKey))
            return false
        }
        cost.forEach { (resource, amount) ->
            repository.adjustBalance(town.id, resource, -amount, "修复建筑", building.buildingKey, player.uniqueId.toString())
        }
        repository.updateBuilding(building.copy(health = building.maxHealth, updatedAt = System.currentTimeMillis()))
        messages.send(player, "building.repaired", Placeholder.unparsed("building", building.buildingKey))
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
        val newMax = calculateMaxHealth(descriptor, newLevel)
        repository.updateBuilding(building.copy(level = newLevel, maxHealth = newMax, health = newMax, updatedAt = System.currentTimeMillis()))
        messages.send(player, "building.upgraded", Placeholder.unparsed("building", descriptor.displayName))
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
                true
            }

            "move" -> {
                // TODO: schematic 模式下的移动功能需要重新实现
                messages.send(player, "building.move-not-supported")
                repository.deletePendingAction(pending.id ?: return false)
                false
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
            refreshResidents(town, town.location(plugin)?.world?.time?.let { it in 0..12000L } ?: true)
            expirePendingActions(town)
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
        
        // 使用 schematic 加载城镇大厅（同步处理标记）
        val schemFileName = schematicLoader.getSchematicFileName(TOWN_HALL_KEY, 1)
        val markers = schematicLoader.pasteSchematicWithMarkers(location, schemFileName)
        
        if (markers == null) {
            plugin.logger.severe("无法加载城镇大厅 schematic: $schemFileName")
            return
        }
        
        var actualLocation = location
        
        if (markers.coreLocation != null) {
            actualLocation = markers.coreLocation!!
            // 写入 NBT 数据
            val coreBlock = actualLocation.block
            if (coreBlock.type == Material.BARREL && coreBlock.state is TileState) {
                val tileState = coreBlock.state as TileState
                val pdc = tileState.persistentDataContainer
                pdc.set(keys.townId, PersistentDataType.STRING, town.id.value)
                pdc.set(keys.buildingId, PersistentDataType.STRING, buildingId.value)
                pdc.set(keys.buildingType, PersistentDataType.STRING, TOWN_HALL_KEY)
                tileState.update(true, false)
                
                plugin.logger.info("城镇大厅核心方块已设置: ${actualLocation.blockX}, ${actualLocation.blockY}, ${actualLocation.blockZ}")
            }
        }
        
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
            health = 200,
            maxHealth = 200,
            active = true,
            collapsed = false,
            createdAt = town.createdAt,
            updatedAt = town.updatedAt,
        )
        repository.saveBuilding(building, emptyList())
    }

    private fun removeBuildingProjection(building: BuildingState) {
        // schematic 模式下不需要手动移除方块，由 WorldEdit 处理
        plugin.logger.info("建筑 ${building.id.value} 被删除（schematic 模式）")
    }

    private fun dropCore(building: BuildingState) {
        registry.findBuilding(building.buildingKey)?.let { descriptor ->
            val dropLocation = building.location(plugin) ?: return@let
            scheduler.executeRegion(dropLocation) {
                dropLocation.world.dropItemNaturally(dropLocation, createWand(descriptor))
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
            repository.deleteBuilding(building.id)
        }
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
