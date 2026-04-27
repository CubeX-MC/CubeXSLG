package io.github.adlamb.cubex.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack

fun interface CommandContributor {
    fun contribute(root: LiteralArgumentBuilder<CommandSourceStack>)
}
