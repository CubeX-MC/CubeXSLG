package io.github.adlamb.cubex.feature.combat

import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.module.FeatureModule
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.Material
import org.bukkit.block.TileState
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.persistence.PersistentDataType

class CombatListener(
    private val context: PluginContext,
) : Listener {
    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val location = event.block.location
        val bounds = context.gameplay.findBuildingAt(location) ?: return
        val block = event.block
        if (block.type == Material.BARREL && block.state is TileState) {
            val pdc = (block.state as TileState).persistentDataContainer
            if (pdc.has(context.keys.buildingId, PersistentDataType.STRING)) {
                event.isCancelled = true
                return
            }
        }
        context.gameplay.hitBuildingBlock(bounds.buildingId.value)
    }

    @EventHandler
    fun onEntityExplode(event: EntityExplodeEvent) {
        handleExplosionBlocks(event.blockList())
    }

    @EventHandler
    fun onBlockExplode(event: BlockExplodeEvent) {
        handleExplosionBlocks(event.blockList())
    }

    private fun handleExplosionBlocks(blocks: MutableList<org.bukkit.block.Block>) {
        val damagedBuildings = mutableMapOf<String, Int>()
        val blocksToRemove = mutableListOf<org.bukkit.block.Block>()

        blocks.forEach { block ->
            val bounds = context.gameplay.findBuildingAt(block.location) ?: return@forEach

            if (block.type == Material.BARREL && block.state is TileState) {
                val pdc = (block.state as TileState).persistentDataContainer
                if (pdc.has(context.keys.buildingId, PersistentDataType.STRING)) {
                    blocksToRemove.add(block)
                    return@forEach
                }
            }

            damagedBuildings[bounds.buildingId.value] = (damagedBuildings[bounds.buildingId.value] ?: 0) + 1
        }

        blocksToRemove.forEach { blocks.remove(it) }

        damagedBuildings.forEach { (buildingId, hitCount) ->
            repeat(hitCount) {
                context.gameplay.applyExplosionDamageToBuilding(buildingId)
            }
        }
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
