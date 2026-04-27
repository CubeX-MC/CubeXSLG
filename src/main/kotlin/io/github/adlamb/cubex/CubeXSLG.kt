package io.github.adlamb.cubex

import io.github.adlamb.cubex.bootstrap.CubeXBootstrap
import io.github.adlamb.cubex.bootstrap.PluginRuntime
import io.github.adlamb.cubex.command.SlgCommandRegistrar
import io.github.adlamb.cubex.platform.FoliaSupport
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.plugin.java.JavaPlugin

class CubeXSLG : JavaPlugin() {
    private var runtime: PluginRuntime? = null

    override fun onEnable() {
        val initialized = CubeXBootstrap(this).initialize()
        runtime = initialized

        initialized.listeners.forEach { server.pluginManager.registerEvents(it, this) }

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            SlgCommandRegistrar(initialized).register(event)
        }

        initialized.context.messages.send(
            server.consoleSender,
            "startup.enabled",
            Placeholder.unparsed("platform", if (FoliaSupport.isFolia()) "Folia" else "Paper"),
            Placeholder.unparsed("database", initialized.context.configs.database.mode.name),
        )
    }

    override fun onDisable() {
        runtime?.let { active ->
            active.shutdown()
            active.context.messages.send(server.consoleSender, "startup.disabled")
        }
        runtime = null
    }
}
