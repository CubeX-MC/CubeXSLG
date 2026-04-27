package io.github.adlamb.cubex.feature.combat

import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.gameplay.model.BuildingId
import io.github.adlamb.cubex.module.FeatureModule
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.block.TileState
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.persistence.PersistentDataType

class CombatListener(
    private val context: PluginContext,
) : Listener {
    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val state = event.block.state
        val buildingId = (state as? TileState)
            ?.persistentDataContainer
            ?.get(context.keys.buildingId, PersistentDataType.STRING)
            ?: return
        event.isCancelled = true
        val record = context.gameplay.repository.buildingById(BuildingId(buildingId)) ?: return
        buildingDamage(record.id.value, 20)
    }

    @EventHandler
    fun onExplode(event: EntityExplodeEvent) {
        event.blockList().forEach { block ->
            val buildingId = (block.state as? TileState)
                ?.persistentDataContainer
                ?.get(context.keys.buildingId, PersistentDataType.STRING)
                ?: return@forEach
            val record = context.gameplay.repository.buildingById(BuildingId(buildingId)) ?: return@forEach
            buildingDamage(record.id.value, 15)
        }
    }

    private fun buildingDamage(buildingId: String, amount: Int) {
        val building = context.gameplay.repository.buildingById(BuildingId(buildingId)) ?: return
        val nextHealth = (building.health - amount).coerceAtLeast(0)
        val updated = building.copy(
            health = nextHealth,
            collapsed = nextHealth <= building.maxHealth / 2,
            active = nextHealth > building.maxHealth / 2,
            updatedAt = System.currentTimeMillis(),
        )
        context.gameplay.repository.updateBuilding(updated)
    }
}

class CombatCommands(
    private val context: PluginContext,
) : CommandContributor {
    override fun contribute(root: LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("combat").executes { command ->
                val player = command.source.sender as? Player ?: return@executes 0
                context.gameplay.openCombat(player)
                1
            },
        )
    }
}

class CombatModule(context: PluginContext) : FeatureModule {
    override val id: String = "combat"
    override val listeners: List<Listener> = listOf(CombatListener(context))
    override val commandContributors: List<CommandContributor> = listOf(CombatCommands(context))
}
