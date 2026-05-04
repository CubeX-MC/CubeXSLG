package io.github.adlamb.cubex.feature.admin

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.github.adlamb.cubex.bootstrap.PluginContext
import io.github.adlamb.cubex.command.CommandContributor
import io.github.adlamb.cubex.command.suggestMatching
import io.github.adlamb.cubex.gameplay.model.BuildingId
import io.github.adlamb.cubex.gameplay.model.TownId
import io.github.adlamb.cubex.module.FeatureModule
import io.github.adlamb.cubex.registry.ResidentAttribute
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import java.util.UUID

class AdminCommands(
    private val context: PluginContext,
) : CommandContributor {
    override fun contribute(root: LiteralArgumentBuilder<CommandSourceStack>) {
        root.then(
            Commands.literal("admin")
                .requires { source -> source.sender.hasPermission("cubexslg.admin") }
                .then(townCommands())
                .then(buildingCommands())
                .then(residentCommands())
                .then(techCommands())
                .then(dataCommands())
                .then(playerCommands())
        )
    }

    private fun townCommands(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("town")
            .then(Commands.literal("list").executes { cmd ->
                listTowns(cmd.source)
            })
            .then(Commands.literal("info")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests { _, builder ->
                        suggestMatching(builder, context.gameplay.getAllTownNames())
                    }
                    .executes { cmd ->
                        val name = StringArgumentType.getString(cmd, "name")
                        showTownInfo(cmd.source, name)
                    }
                )
            )
            .then(Commands.literal("delete")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests { _, builder ->
                        suggestMatching(builder, context.gameplay.getAllTownNames())
                    }
                    .executes { cmd ->
                        val name = StringArgumentType.getString(cmd, "name")
                        deleteTown(cmd.source, name)
                    }
                )
            )
            .then(Commands.literal("transfer")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("newOwner", StringArgumentType.word())
                        .suggests { _, builder ->
                            Bukkit.getOnlinePlayers().forEach { p -> builder.suggest(p.name) }
                            builder.buildFuture()
                        }
                        .executes { cmd ->
                            val name = StringArgumentType.getString(cmd, "name")
                            val newOwner = StringArgumentType.getString(cmd, "newOwner")
                            transferTown(cmd.source, name, newOwner)
                        }
                    )
                )
            )
            .then(Commands.literal("setlevel")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests { _, builder ->
                        suggestMatching(builder, context.gameplay.getAllTownNames())
                    }
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                        .executes { cmd ->
                            val name = StringArgumentType.getString(cmd, "name")
                            val level = IntegerArgumentType.getInteger(cmd, "level")
                            setTownLevel(cmd.source, name, level)
                        }
                    )
                )
            )
            .then(Commands.literal("addresource")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests { _, builder ->
                        suggestMatching(builder, context.gameplay.getAllTownNames())
                    }
                    .then(Commands.argument("resource", StringArgumentType.word())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                            .executes { cmd ->
                                val name = StringArgumentType.getString(cmd, "name")
                                val resource = StringArgumentType.getString(cmd, "resource")
                                val amount = IntegerArgumentType.getInteger(cmd, "amount")
                                addResource(cmd.source, name, resource, amount.toLong())
                            }
                        )
                    )
                )
            )
    }

    private fun buildingCommands(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("building")
            .then(Commands.literal("list").executes { cmd ->
                listBuildings(cmd.source)
            })
            .then(Commands.literal("delete")
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes { cmd ->
                        val id = StringArgumentType.getString(cmd, "id")
                        deleteBuilding(cmd.source, id)
                    }
                )
            )
            .then(Commands.literal("setlevel")
                .then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                        .executes { cmd ->
                            val id = StringArgumentType.getString(cmd, "id")
                            val level = IntegerArgumentType.getInteger(cmd, "level")
                            setBuildingLevel(cmd.source, id, level)
                        }
                    )
                )
            )
    }

    private fun residentCommands(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("resident")
            .then(Commands.literal("list")
                .then(Commands.argument("townName", StringArgumentType.word())
                    .suggests { _, builder ->
                        suggestMatching(builder, context.gameplay.getAllTownNames())
                    }
                    .executes { cmd ->
                        val townName = StringArgumentType.getString(cmd, "townName")
                        listResidents(cmd.source, townName)
                    }
                )
                .executes { cmd ->
                    listResidents(cmd.source, null)
                }
            )
            .then(Commands.literal("addattr")
                .then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.argument("attribute", StringArgumentType.word())
                        .suggests { _, builder ->
                            ResidentAttribute.entries.forEach { attr ->
                                builder.suggest(attr.name.lowercase())
                            }
                            builder.buildFuture()
                        }
                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 100))
                            .executes { cmd ->
                                val id = StringArgumentType.getString(cmd, "id")
                                val attr = StringArgumentType.getString(cmd, "attribute")
                                val value = IntegerArgumentType.getInteger(cmd, "value")
                                addResidentAttr(cmd.source, id, attr, value)
                            }
                        )
                    )
                )
            )
    }

    private fun techCommands(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("tech")
            .then(Commands.literal("grant")
                .then(Commands.argument("townName", StringArgumentType.word())
                    .suggests { _, builder ->
                        suggestMatching(builder, context.gameplay.getAllTownNames())
                    }
                    .then(Commands.argument("techKey", StringArgumentType.word())
                        .executes { cmd ->
                            val townName = StringArgumentType.getString(cmd, "townName")
                            val techKey = StringArgumentType.getString(cmd, "techKey")
                            grantTech(cmd.source, townName, techKey)
                        }
                    )
                )
            )
            .then(Commands.literal("reset")
                .then(Commands.argument("townName", StringArgumentType.word())
                    .suggests { _, builder ->
                        suggestMatching(builder, context.gameplay.getAllTownNames())
                    }
                    .executes { cmd ->
                        val townName = StringArgumentType.getString(cmd, "townName")
                        resetTech(cmd.source, townName)
                    }
                )
            )
    }

    private fun dataCommands(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("data")
            .then(Commands.literal("reload").executes { cmd ->
                reloadData(cmd.source)
            })
            .then(Commands.literal("stats").executes { cmd ->
                showStats(cmd.source)
            })
    }

    private fun playerCommands(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("player")
            .then(Commands.literal("town")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests { _, builder ->
                        Bukkit.getOnlinePlayers().forEach { p -> builder.suggest(p.name) }
                        builder.buildFuture()
                    }
                    .executes { cmd ->
                        val playerName = StringArgumentType.getString(cmd, "player")
                        showPlayerTown(cmd.source, playerName)
                    }
                )
            )
            .then(Commands.literal("reset")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests { _, builder ->
                        Bukkit.getOnlinePlayers().forEach { p -> builder.suggest(p.name) }
                        builder.buildFuture()
                    }
                    .executes { cmd ->
                        val playerName = StringArgumentType.getString(cmd, "player")
                        resetPlayer(cmd.source, playerName)
                    }
                )
            )
    }

    // ========== Town Commands Implementation ==========

    private fun listTowns(source: CommandSourceStack): Int {
        val towns = context.gameplay.repository.towns()
        if (towns.isEmpty()) {
            context.messages.send(source.sender, "admin.town.list.empty")
            return 1
        }

        context.messages.send(source.sender, "admin.town.list.header", Placeholder.unparsed("count", towns.size.toString()))
        towns.forEach { town ->
            val owner = Bukkit.getOfflinePlayer(town.ownerUuid).name ?: "Unknown"
            context.messages.send(
                source.sender,
                "admin.town.list.entry",
                Placeholder.unparsed("name", town.name),
                Placeholder.unparsed("owner", owner),
                Placeholder.unparsed("level", town.level.toString()),
                Placeholder.unparsed("buildings", context.gameplay.repository.buildingsByTown(town.id).size.toString()),
            )
        }
        return 1
    }

    private fun showTownInfo(source: CommandSourceStack, townName: String): Int {
        val town = context.gameplay.getTownByName(townName) ?: run {
            context.messages.send(source.sender, "admin.town.not_found", Placeholder.unparsed("name", townName))
            return 0
        }

        val owner = Bukkit.getOfflinePlayer(town.ownerUuid).name ?: "Unknown"
        val buildings = context.gameplay.repository.buildingsByTown(town.id)
        val residents = context.gameplay.repository.residentsByTown(town.id)
        val balances = context.gameplay.repository.loadBalances(town.id)

        context.messages.send(source.sender, "admin.town.info.header", Placeholder.unparsed("name", town.name))
        context.messages.send(source.sender, "admin.town.info.owner", Placeholder.unparsed("owner", owner))
        context.messages.send(source.sender, "admin.town.info.level", Placeholder.unparsed("level", town.level.toString()))
        context.messages.send(source.sender, "admin.town.info.location",
            Placeholder.unparsed("world", town.world),
            Placeholder.unparsed("x", town.x.toInt().toString()),
            Placeholder.unparsed("y", town.y.toInt().toString()),
            Placeholder.unparsed("z", town.z.toInt().toString()),
        )
        context.messages.send(source.sender, "admin.town.info.radius", Placeholder.unparsed("radius", town.radius.toString()))
        context.messages.send(source.sender, "admin.town.info.buildings",
            Placeholder.unparsed("count", buildings.size.toString()),
            Placeholder.unparsed("limit", town.buildingLimit.toString()),
        )
        context.messages.send(source.sender, "admin.town.info.residents",
            Placeholder.unparsed("count", residents.size.toString()),
            Placeholder.unparsed("limit", town.residentLimit.toString()),
        )

        context.messages.send(source.sender, "admin.town.info.resources_header")
        balances.forEach { (resource, amount) ->
            context.messages.send(source.sender, "admin.town.info.resource",
                Placeholder.unparsed("resource", resource),
                Placeholder.unparsed("amount", amount.toString()),
            )
        }

        return 1
    }

    private fun deleteTown(source: CommandSourceStack, townName: String): Int {
        val success = context.gameplay.deleteTownByName(townName)
        if (success) {
            context.messages.send(source.sender, "admin.town.deleted", Placeholder.unparsed("name", townName))
        } else {
            context.messages.send(source.sender, "admin.town.not_found", Placeholder.unparsed("name", townName))
        }
        return if (success) 1 else 0
    }

    private fun transferTown(source: CommandSourceStack, townName: String, newOwnerName: String): Int {
        val newOwner = Bukkit.getOfflinePlayer(newOwnerName)
        if (!newOwner.hasPlayedBefore() && !newOwner.isOnline) {
            context.messages.send(source.sender, "admin.player.not_found", Placeholder.unparsed("name", newOwnerName))
            return 0
        }

        val success = context.gameplay.transferTown(townName, newOwner.uniqueId)
        if (success) {
            context.messages.send(source.sender, "admin.town.transferred",
                Placeholder.unparsed("name", townName),
                Placeholder.unparsed("owner", newOwnerName),
            )
        } else {
            context.messages.send(source.sender, "admin.town.not_found", Placeholder.unparsed("name", townName))
        }
        return if (success) 1 else 0
    }

    private fun setTownLevel(source: CommandSourceStack, townName: String, level: Int): Int {
        val success = context.gameplay.setTownLevel(townName, level)
        if (success) {
            context.messages.send(source.sender, "admin.town.level_set",
                Placeholder.unparsed("name", townName),
                Placeholder.unparsed("level", level.toString()),
            )
        } else {
            context.messages.send(source.sender, "admin.town.not_found", Placeholder.unparsed("name", townName))
        }
        return if (success) 1 else 0
    }

    private fun addResource(source: CommandSourceStack, townName: String, resource: String, amount: Long): Int {
        val success = context.gameplay.addResourceToTown(townName, resource, amount)
        if (success) {
            context.messages.send(source.sender, "admin.resource.added",
                Placeholder.unparsed("amount", amount.toString()),
                Placeholder.unparsed("resource", resource),
                Placeholder.unparsed("town", townName),
            )
        } else {
            context.messages.send(source.sender, "admin.town.not_found", Placeholder.unparsed("name", townName))
        }
        return if (success) 1 else 0
    }

    // ========== Building Commands Implementation ==========

    private fun listBuildings(source: CommandSourceStack): Int {
        val buildings = context.gameplay.repository.towns().flatMap { town ->
            context.gameplay.repository.buildingsByTown(town.id)
        }

        if (buildings.isEmpty()) {
            context.messages.send(source.sender, "admin.building.list.empty")
            return 1
        }

        context.messages.send(source.sender, "admin.building.list.header", Placeholder.unparsed("count", buildings.size.toString()))
        buildings.take(50).forEach { building ->
            val descriptor = context.registry.findBuilding(building.buildingKey)
            val name = descriptor?.displayName ?: building.buildingKey
            context.messages.send(
                source.sender,
                "admin.building.list.entry",
                Placeholder.unparsed("id", building.id.value.take(8)),
                Placeholder.unparsed("name", name),
                Placeholder.unparsed("level", building.level.toString()),
                Placeholder.unparsed("health", "${building.health}/${building.maxHealth}"),
            )
        }
        return 1
    }

    private fun deleteBuilding(source: CommandSourceStack, buildingId: String): Int {
        val building = context.gameplay.repository.buildingById(BuildingId(buildingId))
        if (building == null) {
            context.messages.send(source.sender, "admin.building.not_found", Placeholder.unparsed("id", buildingId))
            return 0
        }

        context.gameplay.removeBuildingProjection(building)
        context.gameplay.repository.deleteBuilding(BuildingId(buildingId))
        context.messages.send(source.sender, "admin.building.deleted", Placeholder.unparsed("id", buildingId))
        return 1
    }

    private fun setBuildingLevel(source: CommandSourceStack, buildingId: String, level: Int): Int {
        val building = context.gameplay.repository.buildingById(BuildingId(buildingId))
        if (building == null) {
            context.messages.send(source.sender, "admin.building.not_found", Placeholder.unparsed("id", buildingId))
            return 0
        }

        val descriptor = context.registry.findBuilding(building.buildingKey)
        if (descriptor == null) {
            context.messages.send(source.sender, "admin.building.invalid_type")
            return 0
        }

        // 计算最大生命值: footprint.size * 10 * levelMultiplier
        val levelMultiplier = 1.0 + 0.2 * (level - 1)
        val maxHealth = kotlin.math.ceil(descriptor.footprint.size * 10.0 * levelMultiplier).toInt().coerceAtLeast(20)
        val updated = building.copy(level = level, maxHealth = maxHealth, health = maxHealth)
        context.gameplay.repository.updateBuilding(updated)

        context.messages.send(source.sender, "admin.building.level_set",
            Placeholder.unparsed("id", buildingId),
            Placeholder.unparsed("level", level.toString()),
        )
        return 1
    }

    // ========== Resident Commands Implementation ==========

    private fun listResidents(source: CommandSourceStack, townName: String?): Int {
        val residents = if (townName != null) {
            val town = context.gameplay.getTownByName(townName) ?: run {
                context.messages.send(source.sender, "admin.town.not_found", Placeholder.unparsed("name", townName))
                return 0
            }
            context.gameplay.repository.residentsByTown(town.id)
        } else {
            context.gameplay.repository.towns().flatMap { town ->
                context.gameplay.repository.residentsByTown(town.id)
            }
        }

        if (residents.isEmpty()) {
            context.messages.send(source.sender, "admin.resident.list.empty")
            return 1
        }

        context.messages.send(source.sender, "admin.resident.list.header", Placeholder.unparsed("count", residents.size.toString()))
        residents.take(50).forEach { resident ->
            context.messages.send(
                source.sender,
                "admin.resident.list.entry",
                Placeholder.unparsed("id", resident.id.value.take(8)),
                Placeholder.unparsed("name", resident.name),
                Placeholder.unparsed("str", resident.strength.toString()),
                Placeholder.unparsed("agi", resident.agility.toString()),
                Placeholder.unparsed("int", resident.intelligence.toString()),
            )
        }
        return 1
    }

    private fun addResidentAttr(source: CommandSourceStack, residentId: String, attrName: String, value: Int): Int {
        val attr = ResidentAttribute.entries.find { it.name.equals(attrName, ignoreCase = true) }
        if (attr == null) {
            context.messages.send(source.sender, "admin.resident.invalid_attr", Placeholder.unparsed("attr", attrName))
            return 0
        }

        val success = context.gameplay.modifyResidentAttribute(residentId, attr, value)
        if (success) {
            context.messages.send(source.sender, "admin.resident.attr_added",
                Placeholder.unparsed("id", residentId),
                Placeholder.unparsed("attr", attr.displayName),
                Placeholder.unparsed("value", value.toString()),
            )
        } else {
            context.messages.send(source.sender, "admin.resident.not_found", Placeholder.unparsed("id", residentId))
        }
        return if (success) 1 else 0
    }

    // ========== Tech Commands Implementation ==========

    private fun grantTech(source: CommandSourceStack, townName: String, techKey: String): Int {
        val town = context.gameplay.getTownByName(townName) ?: run {
            context.messages.send(source.sender, "admin.town.not_found", Placeholder.unparsed("name", townName))
            return 0
        }

        val node = context.registry.tech(techKey)
        if (node == null) {
            context.messages.send(source.sender, "admin.tech.not_found", Placeholder.unparsed("key", techKey))
            return 0
        }

        context.gameplay.repository.markTech(town.id, techKey, UUID.randomUUID())
        context.messages.send(source.sender, "admin.tech.granted",
            Placeholder.unparsed("tech", node.displayName),
            Placeholder.unparsed("town", townName),
        )
        return 1
    }

    private fun resetTech(source: CommandSourceStack, townName: String): Int {
        val town = context.gameplay.getTownByName(townName) ?: run {
            context.messages.send(source.sender, "admin.town.not_found", Placeholder.unparsed("name", townName))
            return 0
        }

        context.gameplay.repository.resetTechForTown(town.id)
        context.messages.send(source.sender, "admin.tech.reset", Placeholder.unparsed("name", townName))
        return 1
    }

    // ========== Data Commands Implementation ==========

    private fun reloadData(source: CommandSourceStack): Int {
        context.messages.send(source.sender, "admin.data.reloading")
        // Reload configs
        context.messages.send(source.sender, "admin.data.reloaded")
        return 1
    }

    private fun showStats(source: CommandSourceStack): Int {
        val towns = context.gameplay.repository.towns()
        val buildings = towns.sumOf { town ->
            context.gameplay.repository.buildingsByTown(town.id).size
        }
        val residents = towns.sumOf { town ->
            context.gameplay.repository.residentsByTown(town.id).size
        }

        context.messages.send(source.sender, "admin.stats.header")
        context.messages.send(source.sender, "admin.stats.towns", Placeholder.unparsed("count", towns.size.toString()))
        context.messages.send(source.sender, "admin.stats.buildings", Placeholder.unparsed("count", buildings.toString()))
        context.messages.send(source.sender, "admin.stats.residents", Placeholder.unparsed("count", residents.toString()))
        return 1
    }

    // ========== Player Commands Implementation ==========

    private fun showPlayerTown(source: CommandSourceStack, playerName: String): Int {
        val player = Bukkit.getOfflinePlayer(playerName)
        val town = context.gameplay.repository.townByOwner(player.uniqueId)

        if (town == null) {
            context.messages.send(source.sender, "admin.player.no_town", Placeholder.unparsed("name", playerName))
            return 0
        }

        context.messages.send(source.sender, "admin.player.town_info",
            Placeholder.unparsed("player", playerName),
            Placeholder.unparsed("town", town.name),
            Placeholder.unparsed("level", town.level.toString()),
        )
        return 1
    }

    private fun resetPlayer(source: CommandSourceStack, playerName: String): Int {
        val player = Bukkit.getOfflinePlayer(playerName)
        val town = context.gameplay.repository.townByOwner(player.uniqueId)

        if (town == null) {
            context.messages.send(source.sender, "admin.player.no_town", Placeholder.unparsed("name", playerName))
            return 0
        }

        context.gameplay.deleteTown(town.id)
        context.messages.send(source.sender, "admin.player.reset", Placeholder.unparsed("name", playerName))
        return 1
    }
}

class AdminModule(context: PluginContext) : FeatureModule {
    override val id: String = "admin"
    override val listeners = emptyList<org.bukkit.event.Listener>()
    override val commandContributors = listOf(AdminCommands(context))
}
