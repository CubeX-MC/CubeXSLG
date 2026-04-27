package io.github.adlamb.cubex.feature.tech

import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.module.FeatureModule
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.entity.Player
import org.bukkit.event.Listener

class TechListener : Listener

class TechCommands(
    private val context: PluginContext,
) : CommandContributor {
    override fun contribute(root: LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("tech").executes { command ->
                val player = command.source.sender as? Player ?: return@executes 0
                context.gameplay.openTech(player)
                1
            },
        )
    }
}

class TechModule(context: PluginContext) : FeatureModule {
    override val id: String = "tech"
    override val listeners: List<Listener> = listOf(TechListener())
    override val commandContributors: List<CommandContributor> = listOf(TechCommands(context))
}
