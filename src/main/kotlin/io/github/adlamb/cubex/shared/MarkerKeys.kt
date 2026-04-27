package io.github.adlamb.cubex.shared

import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin

class MarkerKeys(plugin: JavaPlugin) {
    val buildingType = NamespacedKey(plugin, "building_type")
    val menuId = NamespacedKey(plugin, "menu_id")
}
