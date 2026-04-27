package io.github.adlamb.cubex.menu

import io.github.adlamb.cubex.message.MessageService
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent

class MenuListener(
    private val messages: MessageService,
) : Listener {
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.inventory.holder !is MenuHolder) {
            return
        }

        event.isCancelled = true
    }
}
