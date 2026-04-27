package io.github.adlamb.cubex.menu

import io.github.adlamb.cubex.config.PluginConfigs
import io.github.adlamb.cubex.message.MessageService
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

data class MenuButton(
    val slot: Int,
    val material: Material,
    val title: Component,
    val lore: List<Component> = emptyList(),
    val action: ((Player) -> Unit)? = null,
)

class MenuFactory(
    private val plugin: JavaPlugin,
    private val messages: MessageService,
    private val configs: PluginConfigs,
) {
    fun openInfo(
        player: Player,
        menuId: MenuId,
        title: String,
        detail: String,
        buttons: List<MenuButton> = emptyList(),
    ) {
        open(player, menuId, title, listOf(Component.text(detail)), buttons)
    }

    fun open(
        player: Player,
        menuId: MenuId,
        title: String,
        body: List<Component>,
        buttons: List<MenuButton>,
    ) {
        val holder = MenuHolder(menuId)
        val inventory = Bukkit.createInventory(holder, configs.placeholderMenuRows * 9, Component.text(title))
        holder.backingInventory = inventory

        repeat(inventory.size) { index ->
            inventory.setItem(index, glass())
        }

        body.take(5).forEachIndexed { index, line ->
            inventory.setItem(4 + index, ItemStack(Material.PAPER).apply {
                editMeta { meta ->
                    meta.displayName(Component.space())
                    meta.lore(listOf(line))
                }
            })
        }

        buttons.forEach { button ->
            inventory.setItem(button.slot, ItemStack(button.material).apply {
                editMeta { meta ->
                    meta.displayName(button.title)
                    meta.lore(button.lore)
                }
            })
            button.action?.let { holder.actions[button.slot] = it }
        }

        player.openInventory(inventory)
        messages.send(player, "command.menu.opened", "menu" to title)
    }

    private fun glass(): ItemStack = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
        editMeta { meta ->
            meta.displayName(Component.space())
        }
    }
}
