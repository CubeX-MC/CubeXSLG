package io.github.adlamb.cubex.bootstrap

import io.github.adlamb.cubex.config.ConfigService
import io.github.adlamb.cubex.coroutine.DefaultPluginCoroutines
import io.github.adlamb.cubex.database.DatabaseManager
import io.github.adlamb.cubex.feature.building.BuildingModule
import io.github.adlamb.cubex.feature.combat.CombatModule
import io.github.adlamb.cubex.feature.logistics.LogisticsModule
import io.github.adlamb.cubex.feature.production.ProductionModule
import io.github.adlamb.cubex.feature.resource.ResourceModule
import io.github.adlamb.cubex.feature.resident.ResidentModule
import io.github.adlamb.cubex.feature.rpglink.RpgLinkModule
import io.github.adlamb.cubex.feature.tech.TechModule
import io.github.adlamb.cubex.feature.town.TownModule
import io.github.adlamb.cubex.menu.MenuFactory
import io.github.adlamb.cubex.menu.MenuListener
import io.github.adlamb.cubex.message.MessageService
import io.github.adlamb.cubex.platform.PaperSchedulerFacade
import io.github.adlamb.cubex.registry.GameplayRegistry
import org.bukkit.plugin.java.JavaPlugin

class CubeXBootstrap(private val plugin: JavaPlugin) {
    fun initialize(): PluginRuntime {
        val configs = ConfigService(plugin).load()
        val messages = MessageService(plugin, configs.locale)
        val coroutines = DefaultPluginCoroutines(plugin)
        val scheduler = PaperSchedulerFacade(plugin)
        val database = DatabaseManager(plugin, configs.database, coroutines)
        val registry = GameplayRegistry.default()
        val menuFactory = MenuFactory(plugin, messages, configs)
        val menuListener = MenuListener(messages)

        if (configs.validateDatabaseOnStartup) {
            database.initialize()
            plugin.logger.info("CubeXSLG database initialized using ${configs.database.mode.name}.")
        }

        val context = PluginContext(
            plugin = plugin,
            configs = configs,
            messages = messages,
            coroutines = coroutines,
            scheduler = scheduler,
            database = database,
            registry = registry,
            menuFactory = menuFactory,
            menuListener = menuListener,
        )

        val modules = listOf(
            TownModule(context),
            ResourceModule(context),
            ProductionModule(context),
            BuildingModule(context),
            ResidentModule(context),
            TechModule(context),
            CombatModule(context),
            LogisticsModule(context),
            RpgLinkModule(context),
        )

        modules.forEach { it.onEnable() }
        return PluginRuntime(context, modules)
    }
}
