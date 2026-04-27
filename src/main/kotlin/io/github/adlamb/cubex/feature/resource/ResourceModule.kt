package io.github.adlamb.cubex.feature.resource

import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.menu.MenuId
import io.github.adlamb.cubex.message.MessageService
import io.github.adlamb.cubex.module.FeatureModule
import io.github.adlamb.cubex.shared.PlaceholderResponses
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.Listener

data class ResourceSummaryRequest(val mode: String)

class ResourceRepository

class ResourceService(messages: MessageService) {
    private val placeholders = PlaceholderResponses(messages, "仓储系统")

    fun sendPlaceholder(sender: CommandSender, action: String) {
        sender.sendMessage(placeholders.action(action))
    }
}

class ResourceUi(private val context: PluginContext) {
    fun openStorage(player: Player) {
        context.menuFactory.openPlaceholder(player, MenuId.STORAGE, "仓储总览", "资源统计、搜索、变化历史和趋势图界面都已预留。")
    }
}

class ResourceListener : Listener

class ResourceCommands(
    private val service: ResourceService,
    private val ui: ResourceUi,
) : CommandContributor {
    override fun contribute(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("resources")
                .executes { command ->
                    val sender = command.source.sender
                    service.sendPlaceholder(sender, "查看主要资源")
                    (sender as? Player)?.let(ui::openStorage)
                    1
                }
                .then(
                    Commands.literal("stats").executes { command ->
                        service.sendPlaceholder(command.source.sender, "查看资源统计摘要")
                        1
                    },
                )
                .then(
                    Commands.literal("history").executes { command ->
                        service.sendPlaceholder(command.source.sender, "查看资源变化历史")
                        1
                    },
                ),
        )
    }
}

class ResourceModule(context: PluginContext) : FeatureModule {
    override val id: String = "resource"
    override val listeners: List<Listener> = listOf(ResourceListener())
    override val commandContributors: List<CommandContributor>

    private val repository = ResourceRepository()
    private val service = ResourceService(context.messages)
    private val ui = ResourceUi(context)

    init {
        commandContributors = listOf(ResourceCommands(service, ui))
    }
}
