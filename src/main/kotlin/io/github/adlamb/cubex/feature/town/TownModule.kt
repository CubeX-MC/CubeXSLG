package io.github.adlamb.cubex.feature.town

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.module.FeatureModule
import io.github.adlamb.cubex.shared.MarkerKeys
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.block.TileState
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.persistence.PersistentDataType

class TownListener(
    private val context: PluginContext,
) : Listener {
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }
        if (context.gameplay.buildingFrom(event.item) != null) {
            return
        }
        val block = event.clickedBlock ?: return
        val state = block.state as? TileState ?: return
        val container = state.persistentDataContainer
        val buildingType = container.get(context.keys.buildingType, PersistentDataType.STRING) ?: return
        if (buildingType != "town_hall") {
            return
        }
        event.isCancelled = true
        context.gameplay.openTownHall(event.player)
    }
}

class TownCommands(
    private val context: PluginContext,
) : CommandContributor {
    override fun contribute(root: LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("create")
                .then(
                    Commands.argument("name", StringArgumentType.greedyString())
                        .executes { command ->
                            val sender = command.source.sender as? Player ?: return@executes 0
                            context.gameplay.createTown(sender, StringArgumentType.getString(command, "name"))?.let {
                                context.gameplay.openTownHall(sender)
                                1
                            } ?: 0
                        },
                ),
        )

        root.then(
            Commands.literal("border").executes { command ->
                val sender = command.source.sender as? Player ?: return@executes 0
                if (context.gameplay.showBorder(sender)) 1 else 0
            },
        )
    }
}

class TownModule(context: PluginContext) : FeatureModule {
    override val id: String = "town"
    override val listeners: List<Listener> = emptyList()
    override val commandContributors: List<CommandContributor> = listOf(TownCommands(context))
}
