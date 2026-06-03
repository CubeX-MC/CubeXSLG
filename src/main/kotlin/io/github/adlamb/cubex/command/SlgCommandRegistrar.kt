package io.github.adlamb.cubex.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.github.adlamb.cubex.bootstrap.PluginRuntime
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent

class SlgCommandRegistrar(
    private val runtime: PluginRuntime,
) {
    fun register(event: ReloadableRegistrarEvent<Commands>) {
        event.registrar().register(createRootCommand().build())
    }

    private fun createRootCommand(): LiteralArgumentBuilder<CommandSourceStack> {
        val root = Commands.literal("slg")
            .requires { source -> source.sender.hasPermission("cubexslg.command") }
            .executes { context -> sendHelp(context.source) }

        runtime.commandContributors.forEach { it.contribute(root) }
        root.then(Commands.literal("help").executes { context -> sendHelp(context.source) })
        return root
    }

    private fun sendHelp(source: CommandSourceStack): Int {
        val sender = source.sender
        runtime.context.messages.send(sender, "command.help.header")
        runtime.context.messages.send(sender, "command.help.line.create")
        runtime.context.messages.send(sender, "command.help.line.wand")
        runtime.context.messages.send(sender, "command.help.line.resources")
        runtime.context.messages.send(sender, "command.help.line.repair")
        runtime.context.messages.send(sender, "command.help.line.confirm")
        runtime.context.messages.send(sender, "command.help.line.residents")
        runtime.context.messages.send(sender, "command.help.line.recruit")
        runtime.context.messages.send(sender, "command.help.line.power")
        runtime.context.messages.send(sender, "command.help.line.border")
        return 1
    }
}
