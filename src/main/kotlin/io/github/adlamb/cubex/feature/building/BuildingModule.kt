package io.github.adlamb.cubex.feature.building

import com.mojang.brigadier.arguments.StringArgumentType
import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.menu.MenuId
import io.github.adlamb.cubex.message.MessageService
import io.github.adlamb.cubex.module.FeatureModule
import io.github.adlamb.cubex.registry.BuildingDescriptor
import io.github.adlamb.cubex.shared.MarkerKeys
import io.github.adlamb.cubex.shared.PlaceholderResponses
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

data class BuildingPlacementIntent(val buildingKey: String)

class BuildingRepository

class BuildingToolFactory(
    private val keys: MarkerKeys,
) {
    fun createWand(building: BuildingDescriptor): ItemStack = ItemStack(building.wandMaterial).apply {
        editMeta { meta ->
            meta.displayName(Component.text("${building.displayName}建筑核心"))
            meta.lore(listOf(Component.text("占位物品，用于后续建筑放置与预览。")))
            meta.persistentDataContainer.set(keys.buildingType, PersistentDataType.STRING, building.key)
        }
    }
}

class BuildingService(
    private val context: PluginContext,
    private val keys: MarkerKeys,
    private val toolFactory: BuildingToolFactory,
    messages: MessageService,
) {
    private val placeholders = PlaceholderResponses(messages, "建筑系统")

    fun giveWand(player: Player, buildingKey: String): Boolean {
        val descriptor = context.registry.findBuilding(buildingKey) ?: return false
        player.inventory.addItem(toolFactory.createWand(descriptor))
        context.messages.send(player, "command.wand.given", "building" to descriptor.displayName)
        return true
    }

    fun buildingFrom(item: ItemStack?): String? {
        val meta = item?.itemMeta ?: return null
        return meta.persistentDataContainer.get(keys.buildingType, PersistentDataType.STRING)
    }

    fun notifyPlaceholder(sender: CommandSender, action: String) {
        sender.sendMessage(placeholders.action(action))
    }
}

class BuildingUi(private val context: PluginContext) {
    fun open(player: Player, buildingName: String) {
        context.menuFactory.openPlaceholder(player, MenuId.BUILDING, "建筑管理", "$buildingName 的升级、移动、删除、修复入口已预留。")
    }
}

class BuildingListener(
    private val service: BuildingService,
    private val ui: BuildingUi,
) : Listener {
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }

        val buildingKey = service.buildingFrom(event.item) ?: return
        event.isCancelled = true
        service.notifyPlaceholder(event.player, "放置建筑: $buildingKey")
        ui.open(event.player, buildingKey)
    }
}

class BuildingCommands(
    private val context: PluginContext,
    private val service: BuildingService,
) : CommandContributor {
    override fun contribute(root: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("wand")
                .requires { source -> source.sender.hasPermission("cubexslg.command.wand") }
                .then(
                    Commands.argument("building", StringArgumentType.word())
                        .executes { command ->
                            val sender = command.source.sender
                            val player = sender as? Player
                            if (player == null) {
                                context.messages.send(sender, "error.player-only")
                                return@executes 0
                            }

                            val building = StringArgumentType.getString(command, "building")
                            if (!service.giveWand(player, building)) {
                                context.messages.send(sender, "error.unknown-building", "building" to building)
                                return@executes 0
                            }
                            1
                        },
                ),
        )

        root.then(
            Commands.literal("repair").executes { command ->
                service.notifyPlaceholder(command.source.sender, "修复建筑")
                1
            },
        )

        root.then(
            Commands.literal("confirm").executes { command ->
                service.notifyPlaceholder(command.source.sender, "确认删除或移动建筑")
                1
            },
        )
    }
}

class BuildingModule(context: PluginContext) : FeatureModule {
    override val id: String = "building"
    override val listeners: List<Listener>
    override val commandContributors: List<CommandContributor>

    private val repository = BuildingRepository()
    private val keys = MarkerKeys(context.plugin)
    private val toolFactory = BuildingToolFactory(keys)
    private val service = BuildingService(context, keys, toolFactory, context.messages)
    private val ui = BuildingUi(context)

    init {
        listeners = listOf(BuildingListener(service, ui))
        commandContributors = listOf(BuildingCommands(context, service))
    }
}
