package io.github.adlamb.cubex.feature.resource

import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.module.FeatureModule
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.entity.Player
import org.bukkit.event.Listener

class ResourceListener : Listener

class ResourceCommands(
    private val context: PluginContext,
) : CommandContributor {
    override fun contribute(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("resources").executes { command ->
                val player = command.source.sender as? Player ?: return@executes 0
                context.gameplay.openStorage(player)
                1
            }.then(
                Commands.literal("stats").executes { command ->
                    val player = command.source.sender as? Player ?: return@executes 0
                    val town = context.gameplay.townOf(player) ?: return@executes 0
                    context.gameplay.sendResourceStats(player, town.id)
                    1
                },
            ).then(
                Commands.literal("history").executes { command ->
                    val player = command.source.sender as? Player ?: return@executes 0
                    val town = context.gameplay.townOf(player) ?: return@executes 0
                    context.gameplay.sendResourceHistory(player, town.id)
                    1
                },
            ),
        )
    }
}

class ResourceModule(context: PluginContext) : FeatureModule {
    override val id: String = "resource"
    override val listeners: List<Listener> = listOf(ResourceListener())
    override val commandContributors: List<CommandContributor> = listOf(ResourceCommands(context))
}
