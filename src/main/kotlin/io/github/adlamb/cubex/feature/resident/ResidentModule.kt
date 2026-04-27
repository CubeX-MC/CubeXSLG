package io.github.adlamb.cubex.feature.resident

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

data class ResidentModel(val placeholder: String = "resident")

class ResidentRepository

class ResidentService(messages: MessageService) {
    private val placeholders = PlaceholderResponses(messages, "居民系统")

    fun send(sender: CommandSender, action: String) {
        sender.sendMessage(placeholders.action(action))
    }
}

class ResidentUi(private val context: PluginContext) {
    fun open(player: Player) {
        context.menuFactory.openPlaceholder(player, MenuId.RESIDENT, "居民管理", "招募、属性、岗位分配、学院培养入口已预留。")
    }
}

class ResidentListener : Listener

class ResidentCommands(
    private val service: ResidentService,
    private val ui: ResidentUi,
) : CommandContributor {
    override fun contribute(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("residents").executes { command ->
                val sender = command.source.sender
                service.send(sender, "查看居民列表")
                (sender as? Player)?.let(ui::open)
                1
            },
        )
        root.then(
            Commands.literal("recruit").executes { command ->
                service.send(command.source.sender, "招募新居民")
                1
            },
        )
    }
}

class ResidentModule(context: PluginContext) : FeatureModule {
    override val id: String = "resident"
    override val listeners: List<Listener> = listOf(ResidentListener())
    override val commandContributors: List<CommandContributor>

    private val repository = ResidentRepository()
    private val service = ResidentService(context.messages)
    private val ui = ResidentUi(context)

    init {
        commandContributors = listOf(ResidentCommands(service, ui))
    }
}
