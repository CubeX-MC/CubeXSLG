package io.github.adlamb.cubex.feature.building

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.command.suggestMatching
import io.github.adlamb.cubex.module.FeatureModule
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.block.BlockFace
import org.bukkit.block.TileState
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.persistence.PersistentDataType

class BuildingListener(
    private val context: PluginContext,
) : Listener {
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
}
