package io.github.adlamb.cubex.feature.production

import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.module.FeatureModule
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.entity.Player
import org.bukkit.event.Listener

class ProductionListener : Listener

class ProductionCommands(
    private val context: PluginContext,
) : CommandContributor {
    override fun contribute(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("production").executes { command ->
                val player = command.source.sender as? Player ?: return@executes 0
                context.gameplay.openProduction(player)
                1
            },
        )
    }
}

class ProductionModule(context: PluginContext) : FeatureModule {
    override val id: String = "production"
    override val listeners: List<Listener> = listOf(ProductionListener())
    override val commandContributors: List<CommandContributor> = listOf(ProductionCommands(context))
}
