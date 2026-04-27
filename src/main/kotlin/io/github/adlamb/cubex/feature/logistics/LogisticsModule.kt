package io.github.adlamb.cubex.feature.logistics

import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.menu.MenuId
import io.github.adlamb.cubex.module.FeatureModule
import org.bukkit.entity.Player
import org.bukkit.event.Listener

data class LogisticsModel(val placeholder: String = "logistics")

class LogisticsRepository

class LogisticsService

class LogisticsUi(private val context: PluginContext) {
    fun open(player: Player) {
        context.menuFactory.openPlaceholder(player, MenuId.LOGISTICS, "物流系统", "虚拟仓储、铁轨运输、导航与智能调度入口已预留。")
    }
}

class LogisticsListener : Listener

class LogisticsCommands : CommandContributor {
    override fun contribute(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<io.papermc.paper.command.brigadier.CommandSourceStack>) = Unit
}

class LogisticsModule(context: PluginContext) : FeatureModule {
    override val id: String = "logistics"
    override val listeners: List<Listener> = listOf(LogisticsListener())
    override val commandContributors: List<CommandContributor> = listOf(LogisticsCommands())

    private val repository = LogisticsRepository()
    private val service = LogisticsService()
    private val ui = LogisticsUi(context)
}
