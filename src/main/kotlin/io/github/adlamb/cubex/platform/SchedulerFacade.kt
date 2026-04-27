package io.github.adlamb.cubex.platform

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.TimeUnit

interface TickScheduler {
    fun execute(task: () -> Unit)

    fun runLater(delayTicks: Long, task: () -> Unit)

    fun runTimer(delayTicks: Long, periodTicks: Long, task: () -> Unit)
}

interface SchedulerFacade {
    fun global(): TickScheduler

    fun region(location: Location): TickScheduler

    fun entity(entity: Entity): TickScheduler

    fun async(): TickScheduler
}

class PaperSchedulerFacade(private val plugin: JavaPlugin) : SchedulerFacade {
    override fun global(): TickScheduler = object : TickScheduler {
        override fun execute(task: () -> Unit) {
            plugin.server.globalRegionScheduler.execute(plugin, task)
        }

        override fun runLater(delayTicks: Long, task: () -> Unit) {
            plugin.server.globalRegionScheduler.runDelayed(plugin, { task() }, delayTicks)
        }

        override fun runTimer(delayTicks: Long, periodTicks: Long, task: () -> Unit) {
            plugin.server.globalRegionScheduler.runAtFixedRate(plugin, { task() }, delayTicks, periodTicks)
        }
    }

    override fun region(location: Location): TickScheduler = object : TickScheduler {
        override fun execute(task: () -> Unit) {
            plugin.server.regionScheduler.execute(plugin, location, task)
        }

        override fun runLater(delayTicks: Long, task: () -> Unit) {
            plugin.server.regionScheduler.runDelayed(plugin, location, { task() }, delayTicks)
        }

        override fun runTimer(delayTicks: Long, periodTicks: Long, task: () -> Unit) {
            plugin.server.regionScheduler.runAtFixedRate(plugin, location, { task() }, delayTicks, periodTicks)
        }
    }

    override fun entity(entity: Entity): TickScheduler = object : TickScheduler {
        override fun execute(task: () -> Unit) {
            entity.scheduler.run(plugin, { task() }, null)
        }

        override fun runLater(delayTicks: Long, task: () -> Unit) {
            entity.scheduler.runDelayed(plugin, { task() }, null, delayTicks)
        }

        override fun runTimer(delayTicks: Long, periodTicks: Long, task: () -> Unit) {
            entity.scheduler.runAtFixedRate(plugin, { task() }, null, delayTicks, periodTicks)
        }
    }

    override fun async(): TickScheduler = object : TickScheduler {
        override fun execute(task: () -> Unit) {
            plugin.server.asyncScheduler.runNow(plugin) { task() }
        }

        override fun runLater(delayTicks: Long, task: () -> Unit) {
            plugin.server.asyncScheduler.runDelayed(plugin, { task() }, delayTicks * 50L, TimeUnit.MILLISECONDS)
        }

        override fun runTimer(delayTicks: Long, periodTicks: Long, task: () -> Unit) {
            plugin.server.asyncScheduler.runAtFixedRate(
                plugin,
                { task() },
                delayTicks * 50L,
                periodTicks * 50L,
                TimeUnit.MILLISECONDS,
            )
        }
    }
}
