package io.github.adlamb.cubex.feature.tech

import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.menu.MenuId
import io.github.adlamb.cubex.module.FeatureModule
import org.bukkit.entity.Player
import org.bukkit.event.Listener

data class TechModel(val placeholder: String = "tech")

class TechRepository

class TechService

class TechUi(private val context: PluginContext) {
    fun open(player: Player) {
        context.menuFactory.openPlaceholder(player, MenuId.TECH, "科技树", "生产、军事、居民、物流、城镇五大分支的占位界面已预留。")
    }
}

class TechListener : Listener

class TechCommands : CommandContributor {
    override fun contribute(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<io.papermc.paper.command.brigadier.CommandSourceStack>) = Unit
}

class TechModule(context: PluginContext) : FeatureModule {
    override val id: String = "tech"
    override val listeners: List<Listener> = listOf(TechListener())
    override val commandContributors: List<CommandContributor> = listOf(TechCommands())

    private val repository = TechRepository()
    private val service = TechService()
    private val ui = TechUi(context)
}
