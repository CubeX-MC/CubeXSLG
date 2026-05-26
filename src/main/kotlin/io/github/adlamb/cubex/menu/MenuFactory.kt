package io.github.adlamb.cubex.menu

import io.github.adlamb.cubex.audio.SoundService
import io.github.adlamb.cubex.config.MenuBodyTemplate
import io.github.adlamb.cubex.config.MenuItemDefaults
import io.github.adlamb.cubex.config.MenuItemConfigs
import io.github.adlamb.cubex.config.MenuItemTemplate
import io.github.adlamb.cubex.config.MenuViewConfig
import io.github.adlamb.cubex.config.PluginConfigs
import io.github.adlamb.cubex.message.MessageService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class MenuFactory(
    private val plugin: JavaPlugin,
    private val messages: MessageService,
    private val configs: PluginConfigs,
    private val menuItems: MenuItemConfigs,
) {
    private val miniMessage = MiniMessage.miniMessage()
    private val plainText = PlainTextComponentSerializer.plainText()
    private val fallbackMenus = MenuItemDefaults.create().menus

    fun openInfo(player: Player, menuId: MenuId, line: String) {
        open(
            player = player,
            menuId = menuId,
            context = MenuRenderContext(
                bodyEntries = listOf(MenuBodyEntry(mapOf("line" to Placeholder.unparsed("line", line)))),
            ),
        )
    }

    fun open(player: Player, menuId: MenuId, context: MenuRenderContext = MenuRenderContext()) {
        val holder = MenuHolder(menuId)
        val menuKey = menuId.configKey
        val menu = resolveMenu(menuId)
        val rows = (menu.rows ?: configs.placeholderMenuRows).coerceIn(1, 6)
        val size = rows * 9
        val title = sanitizeName(render(menu.title, context.placeholders))

        val inventory = Bukkit.createInventory(holder, size, title)
        holder.backingInventory = inventory

        val glassPath = "menus.$menuKey.glass"
        val glassItem = createItem(menu.glass, context.placeholders, glassPath)
        repeat(size) { index -> inventory.setItem(index, glassItem) }

        renderBody(inventory, menu, context, menuKey)
        renderButtons(holder, inventory, menu, context, menuKey)
        renderDynamicLists(holder, inventory, menu, context, menuKey)

        player.openInventory(inventory)
        SoundService.playTo(player, Sound.BLOCK_NOTE_BLOCK_HARP, 0.3f, 1.5f)
        messages.send(player, "command.menu.opened", Placeholder.unparsed("menu", plainText.serialize(title)))
    }

    private fun resolveMenu(menuId: MenuId): MenuViewConfig {
        menuItems.menus[menuId.configKey]?.let { return it }
        fallbackMenus[menuId.configKey]?.let {
            plugin.logger.warning("Menu '${menuId.configKey}' not found in menu-items.yml, using fallback menu.")
            return it
        }
        plugin.logger.warning("Menu '${menuId.configKey}' has no configuration or fallback, using minimal empty menu.")
        return MenuViewConfig(title = "<white>${menuId.name}")
    }

    private fun renderBody(inventory: org.bukkit.inventory.Inventory, menu: MenuViewConfig, context: MenuRenderContext, menuKey: String) {
        if (context.bodyEntries.isEmpty()) {
            return
        }

        val templates = menu.body.templates.ifEmpty { listOf(MenuBodyTemplate()) }
        val max = minOf(context.bodyEntries.size, menu.body.slots.size)
        repeat(max) { index ->
            val slot = menu.body.slots[index]
            if (slot !in 0 until inventory.size) {
                plugin.logger.warning("Slot $slot out of range for menu '$menuKey.body.slots[$index]' with size ${inventory.size}.")
                return@repeat
            }
            val template = templates.getOrElse(index) { templates.last() }
            val placeholders = context.placeholders + context.bodyEntries[index].placeholders
            val path = "menus.$menuKey.body.slots[$index]"
            inventory.setItem(slot, createBodyItem(template, placeholders, path))
        }
    }

    private fun renderButtons(holder: MenuHolder, inventory: org.bukkit.inventory.Inventory, menu: MenuViewConfig, context: MenuRenderContext, menuKey: String) {
        menu.buttons.forEachIndexed { index, button ->
            val runtime = context.buttons[button.id]
            val visible = button.visible && (runtime?.visible ?: true)
            if (!visible) {
                return@forEachIndexed
            }
            if (button.slot !in 0 until inventory.size) {
                plugin.logger.warning("Slot ${button.slot} out of range for menu 'menus.$menuKey.buttons[$index]'.")
                return@forEachIndexed
            }

            val placeholders = context.placeholders + (runtime?.placeholders ?: emptyMap())
            inventory.setItem(button.slot, createItem(
                template = MenuItemTemplate(button.material, button.title, button.lore),
                placeholders = placeholders,
                path = "menus.$menuKey.buttons[$index]",
                fallback = Material.PAPER,
            ))
            runtime?.action?.let { holder.actions[button.slot] = it }
        }
    }

    private fun renderDynamicLists(holder: MenuHolder, inventory: org.bukkit.inventory.Inventory, menu: MenuViewConfig, context: MenuRenderContext, menuKey: String) {
        menu.dynamicLists.forEach { (listId, template) ->
            val runtimeEntries = context.dynamicLists[listId].orEmpty()
            if (runtimeEntries.isEmpty()) {
                return@forEach
            }

            val max = minOf(runtimeEntries.size, template.slots.size)
            repeat(max) { index ->
                val entry = runtimeEntries[index]
                val visible = template.visible && (entry.visible ?: true)
                if (!visible) {
                    return@repeat
                }

                val slot = template.slots[index]
                if (slot !in 0 until inventory.size) {
                    plugin.logger.warning("Slot $slot out of range for menu 'menus.$menuKey.dynamic-lists.$listId.slots[$index]'.")
                    return@repeat
                }

                val placeholders = context.placeholders + entry.placeholders
                val path = "menus.$menuKey.dynamic-lists.$listId[$index]"
                inventory.setItem(slot, createItem(
                    template = MenuItemTemplate(template.material, template.title, template.lore),
                    placeholders = placeholders,
                    path = path,
                    fallback = Material.PAPER,
                ))
                entry.action?.let { holder.actions[slot] = it }
            }
        }
    }

    private fun createBodyItem(template: MenuBodyTemplate, placeholders: Map<String, TagResolver.Single>, path: String): ItemStack {
        return createItem(
            template = MenuItemTemplate(template.material, template.name, template.lore),
            placeholders = placeholders,
            path = path,
            fallback = Material.PAPER,
        )
    }

    private fun createItem(
        template: MenuItemTemplate,
        placeholders: Map<String, TagResolver.Single>,
        path: String,
        fallback: Material = Material.GRAY_STAINED_GLASS_PANE,
    ): ItemStack {
        val material = resolveMaterial(template.material, path, fallback)
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(sanitizeName(render(template.name, placeholders)))
                val lore = template.lore.map { sanitizeLoreLine(render(it, placeholders)) }.toMutableList()
                if (configs.debug) {
                    lore += sanitizeLoreLine(Component.text("path: $path").color(NamedTextColor.GRAY))
                }
                meta.lore(if (lore.isEmpty()) null else lore)
            }
        }
    }

    private fun render(template: String, placeholders: Map<String, TagResolver.Single>): Component {
        if (placeholders.isEmpty()) {
            return miniMessage.deserialize(template)
        }
        return miniMessage.deserialize(template, *placeholders.values.toTypedArray<TagResolver>())
    }

    private fun resolveMaterial(raw: String, path: String, fallback: Material): Material {
        val material = Material.matchMaterial(raw.uppercase())
        if (material == null) {
            plugin.logger.warning("Invalid material '$raw' in $path, fallback to ${fallback.name}.")
            return fallback
        }
        return material
    }

    private fun sanitizeName(component: Component): Component = component
        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
        .colorIfAbsent(NamedTextColor.WHITE)

    private fun sanitizeLoreLine(component: Component): Component = component
        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
        .colorIfAbsent(NamedTextColor.WHITE)
}
