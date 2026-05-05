package io.github.adlamb.cubex.feature.building

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.command.suggestMatching
import io.github.adlamb.cubex.module.FeatureModule
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.BlockFace
import org.bukkit.block.TileState
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.RayTraceResult

class BuildingListener(
    private val context: PluginContext,
) : Listener {
    private val wandPreviewTaskId = java.util.concurrent.atomic.AtomicReference<Any>(null)

    fun startWandPreview() {
        context.scheduler.global().runTimer(5, 5) {
            context.plugin.server.onlinePlayers.forEach { player ->
                val wandKey = wandItemInHand(player)
                val pendingMoveInfo = context.gameplay.getPendingMoveInfo(player)
                if (wandKey != null) {
                    context.scheduler.executeRegion(player.location) {
                        drawWandPreview(player, wandKey)
                    }
                } else if (pendingMoveInfo != null) {
                    context.scheduler.executeRegion(player.location) {
                        drawMovePreview(player, pendingMoveInfo.first, pendingMoveInfo.second)
                    }
                }
            }
        }
    }

    private fun wandItemInHand(player: Player): String? {
        val item = player.inventory.itemInMainHand
        return if (item.type != Material.AIR) context.gameplay.buildingFrom(item) else null
    }

    private fun drawWandPreview(player: Player, wandKey: String) {
        val target = player.rayTraceBlocks(12.0) ?: return
        val clicked = target.hitBlock ?: return
        val face = target.hitBlockFace ?: BlockFace.UP
        val base = clicked.getRelative(face).location.block.location
        val dims = context.gameplay.getBuildingDimensions(wandKey, 1) ?: return

        val withinTown = context.gameplay.playerHasTownWithLocation(player, base)
        val color = if (withinTown) Color.fromRGB(0, 255, 0) else Color.fromRGB(255, 0, 0)
        val dust = Particle.DustOptions(color, 1.0f)

        val minX = base.x - dims.offsetX
        val minY = base.y - dims.offsetY
        val minZ = base.z - dims.offsetZ
        val maxX = minX + dims.width
        val maxY = minY + dims.height
        val maxZ = minZ + dims.length
        val world = base.world ?: return

        drawBoxEdges(world, minX, minY, minZ, maxX, maxY, maxZ, dust, player)
    }

    private fun drawMovePreview(player: Player, buildingKey: String, level: Int) {
        val target = player.rayTraceBlocks(12.0) ?: return
        val clicked = target.hitBlock ?: return
        val face = target.hitBlockFace ?: BlockFace.UP
        val base = clicked.getRelative(face).location.block.location
        val dims = context.gameplay.getBuildingDimensions(buildingKey, level) ?: return

        val withinTown = context.gameplay.playerHasTownWithLocation(player, base)
        val color = if (withinTown) Color.fromRGB(0, 255, 255) else Color.fromRGB(255, 0, 0)
        val dust = Particle.DustOptions(color, 1.0f)

        val minX = base.x - dims.offsetX
        val minY = base.y - dims.offsetY
        val minZ = base.z - dims.offsetZ
        val maxX = minX + dims.width
        val maxY = minY + dims.height
        val maxZ = minZ + dims.length
        val world = base.world ?: return

        drawBoxEdges(world, minX, minY, minZ, maxX, maxY, maxZ, dust, player)
    }

    private fun drawBoxEdges(world: org.bukkit.World, minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, dust: Particle.DustOptions, player: Player) {
        val corners = listOf(
            Triple(minX, minY, minZ), Triple(maxX, minY, minZ), Triple(maxX, minY, maxZ), Triple(minX, minY, maxZ),
            Triple(minX, maxY, minZ), Triple(maxX, maxY, minZ), Triple(maxX, maxY, maxZ), Triple(minX, maxY, maxZ),
        )
        val edges = listOf(
            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 0),
            Pair(4, 5), Pair(5, 6), Pair(6, 7), Pair(7, 4),
            Pair(0, 4), Pair(1, 5), Pair(2, 6), Pair(3, 7),
        )
        edges.forEach { (a, b) ->
            drawLine(world, corners[a], corners[b], dust, 0.3, player)
        }
    }

    private fun drawLine(world: org.bukkit.World, start: Triple<Double, Double, Double>, end: Triple<Double, Double, Double>, dust: Particle.DustOptions, step: Double, player: Player) {
        val dx = end.first - start.first
        val dy = end.second - start.second
        val dz = end.third - start.third
        val dist = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        val steps = (dist / step).toInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val loc = Location(world, start.first + dx * t, start.second + dy * t + 0.05, start.third + dz * t)
            player.spawnParticle(Particle.DUST, loc, 1, 0.0, 0.0, 0.0, 0.0, dust)
        }
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val item = event.item ?: return
        val wandKey = context.gameplay.buildingFrom(item) ?: return
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR) {
            return
        }
        val clicked = event.clickedBlock ?: event.player.getTargetBlockExact(6) ?: return
        val face = if (event.action == Action.RIGHT_CLICK_BLOCK) event.blockFace else BlockFace.UP
        event.isCancelled = true
        context.gameplay.handleWandPlacement(event.player, wandKey, clicked, face)
    }

    @EventHandler
    fun onBuildingCoreInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (context.gameplay.buildingFrom(event.item) != null) {
            return
        }
        val block = event.clickedBlock ?: return
        val state = block.state as? TileState ?: return
        val pdc = state.persistentDataContainer
        val buildingId = pdc.get(context.keys.buildingId, PersistentDataType.STRING) ?: return
        val buildingType = pdc.get(context.keys.buildingType, PersistentDataType.STRING) ?: return
        event.isCancelled = true
        if (buildingType == "town_hall") {
            context.gameplay.openTownHall(event.player)
        } else {
            context.gameplay.openBuildingMenu(event.player, buildingId)
        }
    }
}

class BuildingCommands(
    private val context: PluginContext,
) : CommandContributor {
    override fun contribute(root: LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("wand")
                .then(
                    Commands.argument("building", StringArgumentType.word())
                        .suggests { _, builder ->
                            suggestMatching(builder, context.registry.buildings.keys)
                        }.executes { command ->
                            val player = command.source.sender as? Player ?: return@executes 0
                            if (context.gameplay.giveWand(player, StringArgumentType.getString(command, "building"))) 1 else 0
                        },
                ),
        )
        root.then(
            Commands.literal("repair").executes { command ->
                val player = command.source.sender as? Player ?: return@executes 0
                if (context.gameplay.requestRepair(player)) 1 else 0
            },
        )
        root.then(
            Commands.literal("confirm").executes { command ->
                val player = command.source.sender as? Player ?: return@executes 0
                if (context.gameplay.confirm(player)) 1 else 0
            },
        )
    }
}

class BuildingModule(context: PluginContext) : FeatureModule {
    override val id: String = "building"
    override val listeners: List<Listener> = listOf(BuildingListener(context))
    override val commandContributors: List<CommandContributor> = listOf(BuildingCommands(context))

    override fun onEnable() {
        (listeners.firstOrNull() as? BuildingListener)?.startWandPreview()
    }
}
