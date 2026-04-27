package io.github.adlamb.cubex.menu

import io.github.adlamb.cubex.config.PluginConfigs
import io.github.adlamb.cubex.message.MessageService
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.java.JavaPlugin

class MenuFactory(
    private val plugin: JavaPlugin,
    private val messages: MessageService,
    private val configs: PluginConfigs,
) {
    fun openPlaceholder(player: Player, menuId: MenuId, title: String, detail: String) {
        val holder = MenuHolder(menuId)
        val inventory = Bukkit.createInventory(holder, configs.placeholderMenuRows * 9, Component.text(title))
        holder.backingInventory = inventory

        repeat(inventory.size) { index ->
            inventory.setItem(index, ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
                editMeta { meta ->
                    meta.displayName(Component.space())
                }
            })
        }

        inventory.setItem(13, ItemStack(Material.BOOK).apply {
            editMeta { meta ->
                meta.displayName(Component.text(title))
                meta.lore(listOf(Component.text(detail)))
            }
        })

        player.openInventory(inventory)
        messages.send(player, "command.menu.opened", "menu" to title)
    }
}
