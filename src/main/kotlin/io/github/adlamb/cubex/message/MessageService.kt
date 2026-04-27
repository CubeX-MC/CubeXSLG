package io.github.adlamb.cubex.message

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
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

    fun component(key: String, vararg placeholders: Pair<String, String>): Component {
        val prefix = messages.getString("prefix", "").orEmpty()
        val template = messages.getString(key, key).orEmpty().replace("<prefix>", prefix)
        val resolvers = placeholders.map { Placeholder.unparsed(it.first, it.second) }.toTypedArray<TagResolver>()
        return miniMessage.deserialize(template, *resolvers)
    }

    fun send(sender: CommandSender, key: String, vararg placeholders: Pair<String, String>) {
        sender.sendMessage(component(key, *placeholders))
    }
}
