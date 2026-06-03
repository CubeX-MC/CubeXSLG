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
    val schemOriginX = NamespacedKey(plugin, "schem_origin_x")
    val schemOriginY = NamespacedKey(plugin, "schem_origin_y")
    val schemOriginZ = NamespacedKey(plugin, "schem_origin_z")
    val schemWidth = NamespacedKey(plugin, "schem_width")
    val schemHeight = NamespacedKey(plugin, "schem_height")
    val schemLength = NamespacedKey(plugin, "schem_length")
    val schemNonAirCount = NamespacedKey(plugin, "schem_non_air_count")
    val schemPasteX = NamespacedKey(plugin, "schem_paste_x")
    val schemPasteY = NamespacedKey(plugin, "schem_paste_y")
    val schemPasteZ = NamespacedKey(plugin, "schem_paste_z")
    val powerNodeX = NamespacedKey(plugin, "power_node_x")
    val powerNodeY = NamespacedKey(plugin, "power_node_y")
    val powerNodeZ = NamespacedKey(plugin, "power_node_z")
    val powerEntityRole = NamespacedKey(plugin, "power_entity_role")
    val powerConnectionId = NamespacedKey(plugin, "power_connection_id")
    val powerCable = NamespacedKey(plugin, "power_cable")
}
