package io.github.adlamb.cubex.menu

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class MenuHolder(
    val menuId: MenuId,
) : InventoryHolder {
    lateinit var backingInventory: Inventory

    override fun getInventory(): Inventory = backingInventory
}
