package io.github.adlamb.cubex.feature.rpglink

import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.module.FeatureModule
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.entity.Player
import org.bukkit.event.Listener

class RpgLinkListener : Listener

class RpgLinkCommands(
    private val context: PluginContext,
) : CommandContributor {
    override fun contribute(root: LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("rpglink").executes { command ->
                val player = command.source.sender as? Player ?: return@executes 0
                context.gameplay.openRpgLink(player)
                1
            },
        )
    }
}

class RpgLinkModule(context: PluginContext) : FeatureModule {
    override val id: String = "rpglink"
    override val listeners: List<Listener> = listOf(RpgLinkListener())
    override val commandContributors: List<CommandContributor> = listOf(RpgLinkCommands(context))
}
