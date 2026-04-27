package io.github.adlamb.cubex.feature.production

import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.menu.MenuId
import io.github.adlamb.cubex.module.FeatureModule
import org.bukkit.entity.Player
import org.bukkit.event.Listener

data class ProductionModel(val placeholder: String = "production")

class ProductionRepository

class ProductionService

class ProductionUi(private val context: PluginContext) {
    fun open(player: Player) {
        context.menuFactory.openPlaceholder(player, MenuId.PRODUCTION, "生产系统", "生产速率、净产出和自动流转界面已预留。")
    }
}

class ProductionListener : Listener

class ProductionCommands : CommandContributor {
    override fun contribute(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<io.papermc.paper.command.brigadier.CommandSourceStack>) = Unit
}

class ProductionModule(context: PluginContext) : FeatureModule {
    override val id: String = "production"
    override val listeners: List<Listener> = listOf(ProductionListener())
    override val commandContributors: List<CommandContributor> = listOf(ProductionCommands())

    private val repository = ProductionRepository()
    private val service = ProductionService()
    private val ui = ProductionUi(context)
}
