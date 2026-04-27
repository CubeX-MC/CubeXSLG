package io.github.adlamb.cubex.feature.logistics

import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.module.FeatureModule
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.entity.Player
import org.bukkit.event.Listener

class LogisticsListener : Listener

class LogisticsCommands(
    private val context: PluginContext,
) : CommandContributor {
    override fun contribute(root: LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("logistics").executes { command ->
                val player = command.source.sender as? Player ?: return@executes 0
                context.gameplay.openLogistics(player)
                1
            },
        )
    }
}

class LogisticsModule(context: PluginContext) : FeatureModule {
    override val id: String = "logistics"
    override val listeners: List<Listener> = listOf(LogisticsListener())
    override val commandContributors: List<CommandContributor> = listOf(LogisticsCommands(context))
}
