package io.github.adlamb.cubex.feature.rpglink

import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.menu.MenuId
import io.github.adlamb.cubex.module.FeatureModule
import org.bukkit.entity.Player
import org.bukkit.event.Listener

data class RpgLinkModel(val placeholder: String = "rpglink")

class RpgLinkRepository

class RpgLinkService

class RpgLinkUi(private val context: PluginContext) {
    fun open(player: Player) {
        context.menuFactory.openPlaceholder(player, MenuId.RPG_LINK, "RPG 联动", "双世界、BOSS 残卷和跨世界解锁入口已预留。")
    }
}

class RpgLinkListener : Listener

class RpgLinkCommands : CommandContributor {
    override fun contribute(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<io.papermc.paper.command.brigadier.CommandSourceStack>) = Unit
}

class RpgLinkModule(context: PluginContext) : FeatureModule {
    override val id: String = "rpglink"
    override val listeners: List<Listener> = listOf(RpgLinkListener())
    override val commandContributors: List<CommandContributor> = listOf(RpgLinkCommands())

    private val repository = RpgLinkRepository()
    private val service = RpgLinkService()
    private val ui = RpgLinkUi(context)
}
