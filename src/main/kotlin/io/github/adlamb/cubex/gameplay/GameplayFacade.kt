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
import net.kyori.adventure.text.Component
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
            name = name.trim().ifBlank { "${player.name}的城镇" },
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
        messages.send(player, "town.create.success", "name" to town.name)
        return town
    }

    fun showBorder(player: Player): Boolean {
        val town = townOf(player) ?: return false
        val center = town.location(plugin) ?: return false
        val world = center.world
        val radius = town.radius.toDouble()
        val dust = Particle.DustOptions(Color.YELLOW, 1.2f)
        var remaining = 15

        scheduler.region(center).runTimer(20, 20) {
            if (remaining-- <= 0) {
                return@runTimer
            }
            spawnSquare(world, center, radius, dust)
        }
        messages.send(player, "town.border.preview", "radius" to town.radius.toString())
        return true
    }

    private fun lineEntry(line: String): MenuBodyEntry = MenuBodyEntry(mapOf("line" to line))

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
                                "tech_name" to tech.displayName,
                                "tech_hint" to "点击研究",
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
                    "building_name" to (descriptor?.displayName ?: building.buildingKey),
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
            messages.send(player, "building.locked", "building" to buildingKey)
            return false
        }
        val descriptor = registry.findBuilding(buildingKey) ?: return false
        player.inventory.addItem(createWand(descriptor))
        messages.send(player, "command.wand.given", "building" to descriptor.displayName)
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
            messages.send(player, "building.locked", "building" to descriptor.displayName)
            return false
        }

        val base = block.getRelative(face).location.block.location
        if (!isWithinTown(town, base)) {
            messages.send(player, "building.outside-town", "building" to descriptor.displayName)
            return false
        }
        if (!isFlatEnough(base, descriptor)) {
            messages.send(player, "building.invalid-terrain", "building" to descriptor.displayName)
            return false
        }
        if (repository.buildingsByTown(town.id).count { !it.collapsed } >= town.buildingLimit) {
            messages.send(player, "building.limit-reached", "limit" to town.buildingLimit.toString())
            return false
        }

        val cost = scaledCost(descriptor, 1)
        if (!hasResources(town.id, cost)) {
            messages.send(player, "building.insufficient", "building" to descriptor.displayName)
            return false
        }

        val buildingId = BuildingId(UUID.randomUUID().toString())
        val health = calculateMaxHealth(descriptor, 1)
        val state = BuildingState(
            id = buildingId,
            townId = town.id,
            buildingKey = descriptor.key,
            world = base.world.name,
            x = base.x.toDouble() + 0.5,
            y = base.y.toDouble(),
            z = base.z.toDouble() + 0.5,
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
        val blocks = placeTemplate(base, descriptor, town.id, buildingId)
        repository.saveBuilding(state, blocks)
        cost.forEach { (resource, amount) ->
            repository.adjustBalance(town.id, resource, -amount, "建造${descriptor.displayName}", descriptor.displayName, player.uniqueId.toString())
        }
        messages.send(player, "building.built", "building" to descriptor.displayName)
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
            messages.send(player, "building.insufficient", "building" to building.buildingKey)
            return false
        }
        cost.forEach { (resource, amount) ->
            repository.adjustBalance(town.id, resource, -amount, "修复建筑", building.buildingKey, player.uniqueId.toString())
        }
        repository.updateBuilding(building.copy(health = building.maxHealth, updatedAt = System.currentTimeMillis()))
        messages.send(player, "building.repaired", "building" to building.buildingKey)
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
            messages.send(player, "building.insufficient", "building" to descriptor.displayName)
            return false
        }
        cost.forEach { (resource, amount) ->
            repository.adjustBalance(town.id, resource, -amount, "升级建筑", descriptor.displayName, player.uniqueId.toString())
        }
        val newLevel = building.level + 1
        val newMax = calculateMaxHealth(descriptor, newLevel)
        repository.updateBuilding(building.copy(level = newLevel, maxHealth = newMax, health = newMax, updatedAt = System.currentTimeMillis()))
        messages.send(player, "building.upgraded", "building" to descriptor.displayName)
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
                removeBuildingProjection(building)
                repository.deleteBuilding(building.id)
                repository.deletePendingAction(pending.id ?: return false)
                dropCore(building)
                messages.send(player, "building.deleted")
                true
            }

            "move" -> {
                val building = repository.buildingById(BuildingId(pending.payload)) ?: return false
                val descriptor = registry.findBuilding(building.buildingKey) ?: return false
                val destination = player.location.block.location
                if (!isWithinTown(town, destination)) {
                    messages.send(player, "building.outside-town", "building" to descriptor.displayName)
                    return false
                }
                removeBuildingProjection(building)
                val blocks = placeTemplate(destination, descriptor, town.id, building.id)
                repository.saveBuilding(building.copy(
                    world = destination.world.name,
                    x = destination.x.toDouble() + 0.5,
                    y = destination.y.toDouble(),
                    z = destination.z.toDouble() + 0.5,
                    updatedAt = System.currentTimeMillis(),
                ), blocks)
                repository.deletePendingAction(pending.id ?: return false)
                messages.send(player, "building.moved")
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

        repository.adjustBalance(town.id, "food", -50, "招募居民", "居民系统", player.uniqueId.toString())
        val spawn = town.location(plugin) ?: return false
        val villager = spawn.world.spawn(spawn.add(1.0, 0.0, 0.0), Villager::class.java) { entity ->
            entity.customName(Component.text("居民"))
            entity.isCustomNameVisible = true
            entity.isPersistent = true
            entity.removeWhenFarAway = false
        }
        val resident = ResidentState(
            id = ResidentId(UUID.randomUUID().toString()),
            townId = town.id,
            uuid = villager.uniqueId,
            name = "居民-${villager.uniqueId.toString().takeLast(4)}",
            world = villager.world.name,
            x = villager.location.x,
            y = villager.location.y,
            z = villager.location.z,
            homeX = spawn.x,
            homeY = spawn.y,
            homeZ = spawn.z,
            jobBuildingId = null,
            jobRole = null,
            strength = 1 + (0..3).random(),
            agility = 1 + (0..3).random(),
            intelligence = 1 + (0..3).random(),
            endurance = 1 + (0..3).random(),
            management = 1 + (0..3).random(),
            active = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        repository.saveResident(resident)
        messages.send(player, "resident.recruited", "name" to resident.name)
        return true
    }

    fun research(player: Player, techKey: String): Boolean {
        val town = townOf(player) ?: return false
        val node = registry.tech(techKey) ?: return false
        val researched = repository.techProgress(town.id)
        if (researched.contains(node.key)) {
            messages.send(player, "tech.already", "tech" to node.displayName)
            return false
        }
        if (town.level < node.townLevel) {
            messages.send(player, "tech.town-level", "tech" to node.displayName, "level" to node.townLevel.toString())
            return false
        }
        if (node.prerequisites.any { !researched.contains(it.lowercase()) }) {
            messages.send(player, "tech.prerequisite")
            return false
        }
        if (!hasResources(town.id, node.cost)) {
            messages.send(player, "tech.insufficient", "tech" to node.displayName)
            return false
        }
        node.cost.forEach { (resource, amount) ->
            repository.adjustBalance(town.id, resource, -amount, "研究科技", node.displayName, player.uniqueId.toString())
        }
        repository.markTech(town.id, node.key, player.uniqueId)
        messages.send(player, "tech.researched", "tech" to node.displayName)
        return true
    }

    fun sendResourceHistory(player: Player, townId: TownId) {
        val ledger = repository.ledger(townId, 10)
        ledger.forEach { entry ->
            messages.send(
                player,
                "resource.history.line",
                "delta" to if (entry.delta >= 0) "+${entry.delta}" else entry.delta.toString(),
                "resource" to entry.resourceKey,
                "source" to entry.source,
            )
        }
    }

    fun sendResourceStats(player: Player, townId: TownId) {
        val ledger = repository.ledger(townId, 100)
        val income = ledger.filter { it.delta > 0 }.sumOf { it.delta }
        val expense = ledger.filter { it.delta < 0 }.sumOf { -it.delta }
        messages.send(player, "resource.stats.line", "income" to income.toString(), "expense" to expense.toString())
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
            refreshResidents(town)
            expirePendingActions(town)
        }
    }

    private fun refreshResidents(town: TownState) {
        repository.residentsByTown(town.id).forEach { resident ->
            val world = plugin.server.getWorld(resident.world) ?: return@forEach
            val entity = world.entities.filterIsInstance<Villager>().firstOrNull { it.uniqueId == resident.uuid }
                ?: world.spawn(Location(world, resident.x, resident.y, resident.z), Villager::class.java)
            entity.customName(Component.text(resident.name))
            entity.isCustomNameVisible = true
            entity.isPersistent = true
            entity.removeWhenFarAway = false
            val home = Location(world, resident.homeX, resident.homeY, resident.homeZ)
            val target = home
            if (world.time in 0..12000L) {
                if (resident.jobBuildingId != null) {
                    val building = repository.buildingById(BuildingId(resident.jobBuildingId))
                    if (building != null) {
                        val destination = Location(world, building.x, building.y, building.z)
                        if (entity.location.distanceSquared(destination) > 16.0) {
                            entity.teleport(destination)
                        }
                    }
                } else if (entity.location.distanceSquared(target) > 16.0) {
                    entity.teleport(target)
                }
            } else if (entity.location.distanceSquared(target) > 16.0) {
                entity.teleport(target)
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
        repository.towns().forEach { town ->
            val world = plugin.server.getWorld(town.world) ?: return@forEach
            val townLocation = Location(world, town.x, town.y, town.z)
            val townHall = repository.buildingsByTown(town.id).firstOrNull { it.buildingKey == TOWN_HALL_KEY }
            if (townHall == null) {
                placeTownHall(town, townLocation)
            }
            repository.buildingsByTown(town.id).forEach { building ->
                val descriptor = registry.findBuilding(building.buildingKey)
                val blocks = repository.blocksForBuilding(building.id)
                if (descriptor != null && blocks.isNotEmpty()) {
                    projectBuilding(world, building, blocks)
                }
            }
        }
    }

    private fun placeTownHall(town: TownState, location: Location) {
        val world = location.world
        val core = location.block
        core.type = Material.BARREL
        val state = core.state as? TileState ?: return
        state.persistentDataContainer.set(keys.townId, PersistentDataType.STRING, town.id.value)
        state.persistentDataContainer.set(keys.buildingId, PersistentDataType.STRING, town.id.value)
        state.persistentDataContainer.set(keys.buildingType, PersistentDataType.STRING, TOWN_HALL_KEY)
        state.update(true, false)

        val building = BuildingState(
            id = BuildingId(town.id.value),
            townId = town.id,
            buildingKey = TOWN_HALL_KEY,
            world = world.name,
            x = location.x,
            y = location.y,
            z = location.z,
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
        val blocks = listOf(
            BuildingBlockState(BuildingId(town.id.value), world.name, core.x, core.y, core.z, Material.BARREL.name),
        )
        repository.saveBuilding(building, blocks)
    }

    private fun projectBuilding(world: org.bukkit.World, building: BuildingState, blocks: List<BuildingBlockState>) {
        val core = blocks.firstOrNull { it.material.equals(Material.BARREL.name, ignoreCase = true) }
        blocks.forEach { blockState ->
            val block = world.getBlockAt(blockState.x, blockState.y, blockState.z)
            block.type = Material.valueOf(blockState.material)
            if (block.type == Material.BARREL) {
                val tile = block.state as? TileState ?: return@forEach
                tile.persistentDataContainer.set(keys.townId, PersistentDataType.STRING, building.townId.value)
                tile.persistentDataContainer.set(keys.buildingId, PersistentDataType.STRING, building.id.value)
                tile.persistentDataContainer.set(keys.buildingType, PersistentDataType.STRING, building.buildingKey)
                tile.update(true, false)
            }
        }
    }

    private fun placeTemplate(base: Location, descriptor: BuildingDescriptor, townId: TownId, buildingId: BuildingId): List<BuildingBlockState> {
        val blocks = mutableListOf<BuildingBlockState>()
        descriptor.footprint.forEach { spec ->
            val block = base.world.getBlockAt(base.blockX + spec.dx, base.blockY + spec.dy, base.blockZ + spec.dz)
            block.type = spec.material
            if (spec.material == Material.BARREL) {
                val state = block.state as? TileState
                state?.persistentDataContainer?.set(keys.townId, PersistentDataType.STRING, townId.value)
                state?.persistentDataContainer?.set(keys.buildingId, PersistentDataType.STRING, buildingId.value)
                state?.persistentDataContainer?.set(keys.buildingType, PersistentDataType.STRING, descriptor.key)
                state?.update(true, false)
            }
            blocks += BuildingBlockState(buildingId, base.world.name, block.x, block.y, block.z, spec.material.name)
        }
        return blocks
    }

    private fun removeBuildingProjection(building: BuildingState) {
        repository.blocksForBuilding(building.id).forEach { blockState ->
            val world = plugin.server.getWorld(blockState.world) ?: return@forEach
            val block = world.getBlockAt(blockState.x, blockState.y, blockState.z)
            block.type = Material.AIR
        }
    }

    private fun dropCore(building: BuildingState) {
        registry.findBuilding(building.buildingKey)?.let { descriptor ->
            val world = plugin.server.getWorld(building.world) ?: return@let
            world.dropItemNaturally(Location(world, building.x, building.y, building.z), createWand(descriptor))
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

    private fun isFlatEnough(base: Location, descriptor: BuildingDescriptor): Boolean {
        return descriptor.footprint.all { spec ->
            val block = base.world.getBlockAt(base.blockX + spec.dx, base.blockY + spec.dy, base.blockZ + spec.dz)
            block.type.isAir || block.isEmpty || block.type == Material.GRASS_BLOCK || block.type == Material.DIRT
        }
    }

    private fun TownState.location(plugin: JavaPlugin): Location? {
        val serverWorld = plugin.server.getWorld(world) ?: return null
        return Location(serverWorld, x, y, z, yaw, pitch)
    }

    private fun spawnSquare(world: org.bukkit.World, center: Location, radius: Double, dust: Particle.DustOptions) {
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
            world.spawnParticle(Particle.DUST, point, 25, 0.15, 0.15, 0.15, 0.0, dust)
        }
    }

    private fun markInitialTech(town: TownState, owner: UUID) {
        listOf("base_collect", "base_agriculture", "wood_defense").forEach {
            repository.markTech(town.id, it, owner)
        }
    }
}
