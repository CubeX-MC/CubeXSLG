package io.github.adlamb.cubex.shared

import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin

class MarkerKeys(plugin: JavaPlugin) {
    val buildingType = NamespacedKey(plugin, "building_type")
    val buildingId = NamespacedKey(plugin, "building_id")
    val townId = NamespacedKey(plugin, "town_id")
    val residentId = NamespacedKey(plugin, "resident_id")
    val pendingAction = NamespacedKey(plugin, "pending_action")
    val menuId = NamespacedKey(plugin, "menu_id")
}
