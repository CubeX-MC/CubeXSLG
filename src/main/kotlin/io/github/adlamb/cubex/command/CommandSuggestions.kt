package io.github.adlamb.cubex.command

import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.util.concurrent.CompletableFuture

fun suggestMatching(
    builder: SuggestionsBuilder,
    candidates: Iterable<String>,
): CompletableFuture<Suggestions> {
    val remaining = builder.remainingLowerCase
    candidates
        .asSequence()
        .distinct()
        .sorted()
        .filter { it.lowercase().startsWith(remaining) }
        .forEach(builder::suggest)
    return builder.buildFuture()
}
