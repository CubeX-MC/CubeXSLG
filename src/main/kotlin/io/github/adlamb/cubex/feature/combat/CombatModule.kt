package io.github.adlamb.cubex.feature.combat

import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.menu.MenuId
import io.github.adlamb.cubex.module.FeatureModule
import org.bukkit.entity.Player
import org.bukkit.event.Listener

data class CombatModel(val placeholder: String = "combat")

class CombatRepository

class CombatService

class CombatUi(private val context: PluginContext) {
    fun open(player: Player) {
        context.menuFactory.openPlaceholder(player, MenuId.COMBAT, "战斗系统", "建筑生命值、哨塔防御、兵营训练入口已预留。")
    }
}

class CombatListener : Listener

class CombatCommands : CommandContributor {
    override fun contribute(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<io.papermc.paper.command.brigadier.CommandSourceStack>) = Unit
}

class CombatModule(context: PluginContext) : FeatureModule {
    override val id: String = "combat"
    override val listeners: List<Listener> = listOf(CombatListener())
    override val commandContributors: List<CommandContributor> = listOf(CombatCommands())

    private val repository = CombatRepository()
    private val service = CombatService()
    private val ui = CombatUi(context)
}
