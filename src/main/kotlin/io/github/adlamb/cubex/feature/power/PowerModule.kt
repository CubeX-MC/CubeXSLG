package io.github.adlamb.cubex.feature.power

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.module.FeatureModule
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityUnleashEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot

class PowerListener(
    private val context: PluginContext,
) : Listener {
    @EventHandler
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) {
            return
        }
        if (context.gameplay.handlePowerEntityInteract(event.player, event.rightClicked)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onEntityUnleash(event: EntityUnleashEvent) {
        context.gameplay.handlePowerEntityUnleash(event.entity)
    }
}

class PowerCommands(
    private val context: PluginContext,
) : CommandContributor {
    override fun contribute(root: LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("power")
                .executes { command ->
                    val player = command.source.sender as? Player ?: return@executes 0
                    context.gameplay.openPower(player)
                    1
                }
                .then(
                    Commands.literal("cable").executes { command ->
                        val player = command.source.sender as? Player ?: return@executes 0
                        if (context.gameplay.givePowerCable(player)) 1 else 0
                    },
                ),
        )
    }
}

class PowerModule(context: PluginContext) : FeatureModule {
    override val id: String = "power"
    override val listeners: List<Listener> = listOf(PowerListener(context))
    override val commandContributors: List<CommandContributor> = listOf(PowerCommands(context))
}
