package io.github.adlamb.cubex.feature.resident

import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.module.FeatureModule
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityBreedEvent
import org.bukkit.event.player.PlayerInteractEntityEvent

class ResidentListener : Listener {
    @EventHandler
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.rightClicked is Villager) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onBreed(event: EntityBreedEvent) {
        if (event.entity is Villager) {
            event.isCancelled = true
        }
    }
}

class ResidentCommands(
    private val context: PluginContext,
) : CommandContributor {
    override fun contribute(root: LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("residents").executes { command ->
                val player = command.source.sender as? Player ?: return@executes 0
                context.gameplay.openResidents(player)
                1
            },
        )
        root.then(
            Commands.literal("recruit").executes { command ->
                val player = command.source.sender as? Player ?: return@executes 0
                if (context.gameplay.recruit(player)) 1 else 0
            },
        )
    }
}

class ResidentModule(context: PluginContext) : FeatureModule {
    override val id: String = "resident"
    override val listeners: List<Listener> = listOf(ResidentListener())
    override val commandContributors: List<CommandContributor> = listOf(ResidentCommands(context))
}
