package io.github.adlamb.cubex.shared

import io.github.adlamb.cubex.message.MessageService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder

class PlaceholderResponses(private val messages: MessageService, private val feature: String) {
    fun action(action: String): Component = messages.component(
        "command.not-implemented",
        Placeholder.unparsed("feature", feature),
        Placeholder.unparsed("action", action),
    )
}
