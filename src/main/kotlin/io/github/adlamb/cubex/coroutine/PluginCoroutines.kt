package io.github.adlamb.cubex.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.bukkit.plugin.java.JavaPlugin

interface PluginCoroutines {
    val scope: CoroutineScope
    val ioDispatcher: CoroutineDispatcher

    suspend fun <T> io(block: () -> T): T

    fun close()
}

class DefaultPluginCoroutines(plugin: JavaPlugin) : PluginCoroutines {
    override val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + CoroutineName("${plugin.name}-scope") + Dispatchers.Default,
    )

    override val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    override suspend fun <T> io(block: () -> T): T = withContext(ioDispatcher) {
        block()
    }

    override fun close() {
        scope.cancel()
    }
}
