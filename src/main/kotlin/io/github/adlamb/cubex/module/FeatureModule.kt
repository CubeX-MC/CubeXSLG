package io.github.adlamb.cubex.module

import io.github.adlamb.cubex.command.CommandContributor
import org.bukkit.event.Listener

interface FeatureModule {
    val id: String
    val commandContributors: List<CommandContributor>
    val listeners: List<Listener>

    fun onEnable() = Unit

    fun onDisable() = Unit
}
