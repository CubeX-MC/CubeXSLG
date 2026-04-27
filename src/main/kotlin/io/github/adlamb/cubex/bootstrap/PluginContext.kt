package io.github.adlamb.cubex.bootstrap

import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.config.PluginConfigs
import io.github.adlamb.cubex.coroutine.PluginCoroutines
import io.github.adlamb.cubex.database.DatabaseManager
import io.github.adlamb.cubex.menu.MenuFactory
import io.github.adlamb.cubex.menu.MenuListener
import io.github.adlamb.cubex.message.MessageService
import io.github.adlamb.cubex.module.FeatureModule
import io.github.adlamb.cubex.platform.SchedulerFacade
import io.github.adlamb.cubex.registry.GameplayRegistry
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

data class PluginContext(
    val plugin: JavaPlugin,
    val configs: PluginConfigs,
    val messages: MessageService,
    val coroutines: PluginCoroutines,
    val scheduler: SchedulerFacade,
    val database: DatabaseManager,
    val registry: GameplayRegistry,
    val menuFactory: MenuFactory,
    val menuListener: MenuListener,
)

data class PluginRuntime(
    val context: PluginContext,
    val modules: List<FeatureModule>,
) {
    val listeners: List<Listener>
        get() = modules.flatMap(FeatureModule::listeners) + context.menuListener

    val commandContributors: List<CommandContributor>
        get() = modules.flatMap(FeatureModule::commandContributors)

    fun shutdown() {
        modules.asReversed().forEach(FeatureModule::onDisable)
        context.database.close()
        context.coroutines.close()
    }
}
