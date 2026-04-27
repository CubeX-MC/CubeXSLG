package io.github.adlamb.cubex.message

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class MessageService(
    plugin: JavaPlugin,
    val locale: String,
) {
    private val file = File(plugin.dataFolder, "messages.yml")
    private val messages = YamlConfiguration.loadConfiguration(file)
    private val miniMessage = MiniMessage.miniMessage()

    fun component(key: String, vararg placeholders: TagResolver): Component {
        val prefix = messages.getString("prefix", "").orEmpty()
        val template = messages.getString(key, key).orEmpty().replace("<prefix>", prefix)
        return miniMessage.deserialize(template, *placeholders)
    }

    fun send(sender: CommandSender, key: String, vararg placeholders: TagResolver) {
        sender.sendMessage(component(key, *placeholders))
    }
}
