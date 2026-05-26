package io.github.adlamb.cubex.menu

import io.github.adlamb.cubex.audio.SoundService
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent

class MenuListener : Listener {
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? MenuHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val action = holder.actions[event.rawSlot]
        if (action != null) {
            SoundService.playTo(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.0f)
            action(player)
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? MenuHolder ?: return
        val player = event.player as? Player ?: return
        SoundService.playTo(player, Sound.BLOCK_NOTE_BLOCK_HARP, 0.2f, 1.0f)
    }
}
