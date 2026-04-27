package io.github.adlamb.cubex.feature.town

import com.mojang.brigadier.arguments.StringArgumentType
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

data class TownCreateRequest(
    val name: String,
)

class TownRepository

class TownService(messages: MessageService) {
    private val placeholders = PlaceholderResponses(messages, "城镇系统")

    fun sendCreatePlaceholder(sender: CommandSender, request: TownCreateRequest) {
        sender.sendMessage(placeholders.action("创建城镇: ${request.name}"))
    }
}

class TownUi(private val context: PluginContext) {
    fun openTownHall(player: Player) {
        context.menuFactory.openPlaceholder(player, MenuId.TOWN_HALL, "城府管理", "城镇、建筑、仓储、科技、居民设置入口已预留。")
    }
}

class TownListener : Listener

class TownCommands(
    private val context: PluginContext,
    private val service: TownService,
    private val ui: TownUi,
) : CommandContributor {
    override fun contribute(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("create")
                .then(
                    Commands.argument("name", StringArgumentType.greedyString())
                        .executes { command ->
                            service.sendCreatePlaceholder(
                                command.source.sender,
                                TownCreateRequest(StringArgumentType.getString(command, "name")),
                            )
                            (command.source.sender as? Player)?.let(ui::openTownHall)
                            1
                        },
                ),
        )
    }
}

class TownModule(context: PluginContext) : FeatureModule {
    override val id: String = "town"
    override val listeners: List<Listener> = listOf(TownListener())
    override val commandContributors: List<CommandContributor>

    private val repository = TownRepository()
    private val service = TownService(context.messages)
    private val ui = TownUi(context)

    init {
        commandContributors = listOf(TownCommands(context, service, ui))
    }
}
