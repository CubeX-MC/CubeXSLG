package io.github.adlamb.cubex.menu

import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class MenuHolder(
    val menuId: MenuId,
) : InventoryHolder {
    val actions: MutableMap<Int, (Player) -> Unit> = linkedMapOf()
    lateinit var backingInventory: Inventory

    override fun getInventory(): Inventory = backingInventory
}
