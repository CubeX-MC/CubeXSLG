package io.github.adlamb.cubex.gameplay.storage

import io.github.adlamb.cubex.gameplay.model.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class GameplayRepository {
    fun initializeSchema() = transaction {
        org.jetbrains.exposed.v1.jdbc.SchemaUtils.create(
            TownsTable,
            ResourceBalancesTable,
            ResourceLedgerTable,
            BuildingsTable,
            BuildingBlocksTable,
            ResidentsTable,
            TechProgressTable,
            PendingActionsTable,
            RailRoutesTable,
            CargoJobsTable,
            CombatStateTable,
        )
    }

    fun towns(): List<TownState> = transaction {
        TownsTable.selectAll().map { row -> row.toTownState() }
    }

    fun townByOwner(ownerUuid: UUID): TownState? = transaction {
        TownsTable.selectAll().firstOrNull { it[TownsTable.ownerUuid] == ownerUuid.toString() }?.toTownState()
    }

    fun townById(townId: TownId): TownState? = transaction {
        TownsTable.selectAll().firstOrNull { it[TownsTable.id] == townId.value }?.toTownState()
    }

    fun saveTown(state: TownState) = transaction {
        TownsTable.deleteWhere { TownsTable.id eq state.id.value }
        TownsTable.insert {
            it[TownsTable.id] = state.id.value
            it[TownsTable.ownerUuid] = state.ownerUuid.toString()
            it[TownsTable.name] = state.name
            it[TownsTable.world] = state.world
            it[TownsTable.x] = state.x
            it[TownsTable.y] = state.y
            it[TownsTable.z] = state.z
            it[TownsTable.yaw] = state.yaw
            it[TownsTable.pitch] = state.pitch
            it[TownsTable.level] = state.level
            it[TownsTable.radius] = state.radius
            it[TownsTable.buildingLimit] = state.buildingLimit
            it[TownsTable.residentLimit] = state.residentLimit
            it[TownsTable.createdAt] = state.createdAt
            it[TownsTable.updatedAt] = state.updatedAt
        }
    }

    fun loadBalances(townId: TownId): Map<String, Long> = transaction {
        ResourceBalancesTable.selectAll().filter { it[ResourceBalancesTable.townId] == townId.value }
            .associate { row -> row[ResourceBalancesTable.resourceKey] to row[ResourceBalancesTable.amount] }
    }

    fun balanceOf(townId: TownId, resourceKey: String): Long = transaction {
        ResourceBalancesTable.selectAll()
            .firstOrNull { it[ResourceBalancesTable.townId] == townId.value && it[ResourceBalancesTable.resourceKey] == resourceKey.lowercase() }
            ?.get(ResourceBalancesTable.amount) ?: 0L
    }

    fun setBalance(townId: TownId, resourceKey: String, amount: Long) = transaction {
        val key = resourceKey.lowercase()
        val updated = ResourceBalancesTable.update({ (ResourceBalancesTable.townId eq townId.value) and (ResourceBalancesTable.resourceKey eq key) }) {
            it[ResourceBalancesTable.amount] = amount
        }
        if (updated == 0) {
            ResourceBalancesTable.insert {
                it[ResourceBalancesTable.townId] = townId.value
                it[ResourceBalancesTable.resourceKey] = key
                it[ResourceBalancesTable.amount] = amount
            }
        }
    }

    fun adjustBalance(
        townId: TownId,
        resourceKey: String,
        delta: Long,
        reason: String,
        source: String,
        actor: String? = null,
        createdAt: Long = System.currentTimeMillis(),
    ): Long = transaction {
        val key = resourceKey.lowercase()
        val current = ResourceBalancesTable.selectAll()
            .firstOrNull { it[ResourceBalancesTable.townId] == townId.value && it[ResourceBalancesTable.resourceKey] == key }
            ?.get(ResourceBalancesTable.amount) ?: 0L
        val updated = (current + delta).coerceAtLeast(0L)
        ResourceBalancesTable.deleteWhere { (ResourceBalancesTable.townId eq townId.value) and (ResourceBalancesTable.resourceKey eq key) }
        ResourceBalancesTable.insert {
            it[ResourceBalancesTable.townId] = townId.value
            it[ResourceBalancesTable.resourceKey] = key
            it[ResourceBalancesTable.amount] = updated
        }
        ResourceLedgerTable.insert {
            it[ResourceLedgerTable.townId] = townId.value
            it[ResourceLedgerTable.resourceKey] = key
            it[ResourceLedgerTable.delta] = delta
            it[ResourceLedgerTable.balanceAfter] = updated
            it[ResourceLedgerTable.reason] = reason
            it[ResourceLedgerTable.origin] = source
            it[ResourceLedgerTable.actor] = actor
            it[ResourceLedgerTable.createdAt] = createdAt
        }
        updated
    }

    fun ledger(townId: TownId, limit: Int = 100): List<ResourceLedgerEntry> = transaction {
        ResourceLedgerTable.selectAll()
            .filter { it[ResourceLedgerTable.townId] == townId.value }
            .sortedByDescending { it[ResourceLedgerTable.createdAt] }
            .take(limit)
            .map { row ->
                ResourceLedgerEntry(
                    id = row[ResourceLedgerTable.id],
                    townId = townId,
                    resourceKey = row[ResourceLedgerTable.resourceKey],
                    delta = row[ResourceLedgerTable.delta],
                    balanceAfter = row[ResourceLedgerTable.balanceAfter],
                    reason = row[ResourceLedgerTable.reason],
                    source = row[ResourceLedgerTable.origin],
                    actor = row[ResourceLedgerTable.actor],
                    createdAt = row[ResourceLedgerTable.createdAt],
                )
            }
    }

    fun buildingsByTown(townId: TownId): List<BuildingState> = transaction {
        BuildingsTable.selectAll().filter { it[BuildingsTable.townId] == townId.value }
            .map { it.toBuildingState() }
    }

    fun buildingById(buildingId: BuildingId): BuildingState? = transaction {
        BuildingsTable.selectAll().firstOrNull { it[BuildingsTable.id] == buildingId.value }?.toBuildingState()
    }

    fun buildingAt(world: String, x: Int, y: Int, z: Int): BuildingState? = transaction {
        val buildingId = BuildingBlocksTable.selectAll().firstOrNull {
            it[BuildingBlocksTable.world] == world &&
                it[BuildingBlocksTable.x] == x &&
                it[BuildingBlocksTable.y] == y &&
                it[BuildingBlocksTable.z] == z
        }?.get(BuildingBlocksTable.buildingId)
        buildingId?.let { buildingById(BuildingId(it)) }
    }

    fun saveBuilding(state: BuildingState, blocks: List<BuildingBlockState>) = transaction {
        BuildingsTable.deleteWhere { BuildingsTable.id eq state.id.value }
        BuildingBlocksTable.deleteWhere { BuildingBlocksTable.buildingId eq state.id.value }
        BuildingsTable.insert {
            it[BuildingsTable.id] = state.id.value
            it[BuildingsTable.townId] = state.townId.value
            it[BuildingsTable.buildingKey] = state.buildingKey
            it[BuildingsTable.world] = state.world
            it[BuildingsTable.x] = state.x
            it[BuildingsTable.y] = state.y
            it[BuildingsTable.z] = state.z
            it[BuildingsTable.yaw] = state.yaw
            it[BuildingsTable.pitch] = state.pitch
            it[BuildingsTable.level] = state.level
            it[BuildingsTable.health] = state.health
            it[BuildingsTable.maxHealth] = state.maxHealth
            it[BuildingsTable.active] = state.active
            it[BuildingsTable.collapsed] = state.collapsed
            it[BuildingsTable.createdAt] = state.createdAt
            it[BuildingsTable.updatedAt] = state.updatedAt
        }
        blocks.forEach { block ->
            BuildingBlocksTable.insert {
                it[BuildingBlocksTable.buildingId] = block.buildingId.value
                it[BuildingBlocksTable.world] = block.world
                it[BuildingBlocksTable.x] = block.x
                it[BuildingBlocksTable.y] = block.y
                it[BuildingBlocksTable.z] = block.z
                it[BuildingBlocksTable.material] = block.material
            }
        }
    }

    fun updateBuilding(state: BuildingState) = transaction {
        BuildingsTable.deleteWhere { BuildingsTable.id eq state.id.value }
        BuildingsTable.insert {
            it[BuildingsTable.id] = state.id.value
            it[BuildingsTable.townId] = state.townId.value
            it[BuildingsTable.buildingKey] = state.buildingKey
            it[BuildingsTable.world] = state.world
            it[BuildingsTable.x] = state.x
            it[BuildingsTable.y] = state.y
            it[BuildingsTable.z] = state.z
            it[BuildingsTable.yaw] = state.yaw
            it[BuildingsTable.pitch] = state.pitch
            it[BuildingsTable.level] = state.level
            it[BuildingsTable.health] = state.health
            it[BuildingsTable.maxHealth] = state.maxHealth
            it[BuildingsTable.active] = state.active
            it[BuildingsTable.collapsed] = state.collapsed
            it[BuildingsTable.createdAt] = state.createdAt
            it[BuildingsTable.updatedAt] = state.updatedAt
        }
    }

    fun deleteBuilding(buildingId: BuildingId) = transaction {
        BuildingsTable.deleteWhere { BuildingsTable.id eq buildingId.value }
        BuildingBlocksTable.deleteWhere { BuildingBlocksTable.buildingId eq buildingId.value }
    }

    fun blocksForBuilding(buildingId: BuildingId): List<BuildingBlockState> = transaction {
        BuildingBlocksTable.selectAll().filter { it[BuildingBlocksTable.buildingId] == buildingId.value }
            .map {
                BuildingBlockState(
                    buildingId = buildingId,
                    world = it[BuildingBlocksTable.world],
                    x = it[BuildingBlocksTable.x],
                    y = it[BuildingBlocksTable.y],
                    z = it[BuildingBlocksTable.z],
                    material = it[BuildingBlocksTable.material],
                )
            }
    }

    fun residentsByTown(townId: TownId): List<ResidentState> = transaction {
        ResidentsTable.selectAll().filter { it[ResidentsTable.townId] == townId.value }
            .map { row -> row.toResidentState() }
    }

    fun residentByUuid(uuid: UUID): ResidentState? = transaction {
        ResidentsTable.selectAll().firstOrNull { it[ResidentsTable.uuid] == uuid.toString() }?.toResidentState()
    }

    fun saveResident(state: ResidentState) = transaction {
        ResidentsTable.deleteWhere { ResidentsTable.id eq state.id.value }
        ResidentsTable.insert {
            it[ResidentsTable.id] = state.id.value
            it[ResidentsTable.townId] = state.townId.value
            it[ResidentsTable.uuid] = state.uuid.toString()
            it[ResidentsTable.name] = state.name
            it[ResidentsTable.world] = state.world
            it[ResidentsTable.x] = state.x
            it[ResidentsTable.y] = state.y
            it[ResidentsTable.z] = state.z
            it[ResidentsTable.homeX] = state.homeX
            it[ResidentsTable.homeY] = state.homeY
            it[ResidentsTable.homeZ] = state.homeZ
            it[ResidentsTable.jobBuildingId] = state.jobBuildingId
            it[ResidentsTable.jobRole] = state.jobRole
            it[ResidentsTable.strength] = state.strength
            it[ResidentsTable.agility] = state.agility
            it[ResidentsTable.intelligence] = state.intelligence
            it[ResidentsTable.endurance] = state.endurance
            it[ResidentsTable.management] = state.management
            it[ResidentsTable.active] = state.active
            it[ResidentsTable.createdAt] = state.createdAt
            it[ResidentsTable.updatedAt] = state.updatedAt
        }
    }

    fun techProgress(townId: TownId): Set<String> = transaction {
        TechProgressTable.selectAll()
            .filter { it[TechProgressTable.townId] == townId.value }
            .map { it[TechProgressTable.techKey].lowercase() }
            .toSet()
    }

    fun markTech(townId: TownId, techKey: String, researcherUuid: UUID, researchedAt: Long = System.currentTimeMillis()) = transaction {
        val key = techKey.lowercase()
        val updated = TechProgressTable.update({ (TechProgressTable.townId eq townId.value) and (TechProgressTable.techKey eq key) }) {
            it[TechProgressTable.researchedAt] = researchedAt
            it[TechProgressTable.researcherUuid] = researcherUuid.toString()
        }
        if (updated == 0) {
            TechProgressTable.insert {
                it[TechProgressTable.townId] = townId.value
                it[TechProgressTable.techKey] = key
                it[TechProgressTable.researchedAt] = researchedAt
                it[TechProgressTable.researcherUuid] = researcherUuid.toString()
            }
        }
    }

    fun pendingActions(townId: TownId, actorUuid: UUID): List<PendingAction> = transaction {
        PendingActionsTable.selectAll()
            .filter { it[PendingActionsTable.townId] == townId.value && it[PendingActionsTable.actorUuid] == actorUuid.toString() }
            .map { row ->
                PendingAction(
                    id = row[PendingActionsTable.id],
                    townId = townId,
                    actorUuid = actorUuid.toString(),
                    actionType = row[PendingActionsTable.actionType],
                    payload = row[PendingActionsTable.payload],
                    expiresAt = row[PendingActionsTable.expiresAt],
                    createdAt = row[PendingActionsTable.createdAt],
                )
            }
    }

    fun createPendingAction(action: PendingAction): PendingAction = transaction {
        PendingActionsTable.insert {
            it[PendingActionsTable.townId] = action.townId.value
            it[PendingActionsTable.actorUuid] = action.actorUuid
            it[PendingActionsTable.actionType] = action.actionType
            it[PendingActionsTable.payload] = action.payload
            it[PendingActionsTable.expiresAt] = action.expiresAt
            it[PendingActionsTable.createdAt] = action.createdAt
        }.let { inserted ->
            action.copy(id = inserted[PendingActionsTable.id])
        }
    }

    fun deletePendingAction(id: Long) = transaction {
        PendingActionsTable.deleteWhere { PendingActionsTable.id eq id }
    }

    fun allPendingActions(): List<PendingAction> = transaction {
        PendingActionsTable.selectAll().map { row ->
            PendingAction(
                id = row[PendingActionsTable.id],
                townId = TownId(row[PendingActionsTable.townId]),
                actorUuid = row[PendingActionsTable.actorUuid],
                actionType = row[PendingActionsTable.actionType],
                payload = row[PendingActionsTable.payload],
                expiresAt = row[PendingActionsTable.expiresAt],
                createdAt = row[PendingActionsTable.createdAt],
            )
        }
    }

    fun routes(townId: TownId): List<RailRoute> = transaction {
        RailRoutesTable.selectAll().filter { it[RailRoutesTable.townId] == townId.value }
            .map {
                RailRoute(
                    id = null,
                    townId = townId,
                    name = it[RailRoutesTable.name],
                    throughput = it[RailRoutesTable.throughput],
                    saturation = it[RailRoutesTable.saturation],
                )
            }
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toTownState(): TownState = TownState(
        id = TownId(this[TownsTable.id]),
        ownerUuid = UUID.fromString(this[TownsTable.ownerUuid]),
        name = this[TownsTable.name],
        world = this[TownsTable.world],
        x = this[TownsTable.x],
        y = this[TownsTable.y],
        z = this[TownsTable.z],
        yaw = this[TownsTable.yaw],
        pitch = this[TownsTable.pitch],
        level = this[TownsTable.level],
        radius = this[TownsTable.radius],
        buildingLimit = this[TownsTable.buildingLimit],
        residentLimit = this[TownsTable.residentLimit],
        createdAt = this[TownsTable.createdAt],
        updatedAt = this[TownsTable.updatedAt],
    )

    private fun org.jetbrains.exposed.v1.core.ResultRow.toBuildingState(): BuildingState = BuildingState(
        id = BuildingId(this[BuildingsTable.id]),
        townId = TownId(this[BuildingsTable.townId]),
        buildingKey = this[BuildingsTable.buildingKey],
        world = this[BuildingsTable.world],
        x = this[BuildingsTable.x],
        y = this[BuildingsTable.y],
        z = this[BuildingsTable.z],
        yaw = this[BuildingsTable.yaw],
        pitch = this[BuildingsTable.pitch],
        level = this[BuildingsTable.level],
        health = this[BuildingsTable.health],
        maxHealth = this[BuildingsTable.maxHealth],
        active = this[BuildingsTable.active],
        collapsed = this[BuildingsTable.collapsed],
        createdAt = this[BuildingsTable.createdAt],
        updatedAt = this[BuildingsTable.updatedAt],
    )

    private fun org.jetbrains.exposed.v1.core.ResultRow.toResidentState(): ResidentState = ResidentState(
        id = ResidentId(this[ResidentsTable.id]),
        townId = TownId(this[ResidentsTable.townId]),
        uuid = UUID.fromString(this[ResidentsTable.uuid]),
        name = this[ResidentsTable.name],
        world = this[ResidentsTable.world],
        x = this[ResidentsTable.x],
        y = this[ResidentsTable.y],
        z = this[ResidentsTable.z],
        homeX = this[ResidentsTable.homeX],
        homeY = this[ResidentsTable.homeY],
        homeZ = this[ResidentsTable.homeZ],
        jobBuildingId = this[ResidentsTable.jobBuildingId],
        jobRole = this[ResidentsTable.jobRole],
        strength = this[ResidentsTable.strength],
        agility = this[ResidentsTable.agility],
        intelligence = this[ResidentsTable.intelligence],
        endurance = this[ResidentsTable.endurance],
        management = this[ResidentsTable.management],
        active = this[ResidentsTable.active],
        createdAt = this[ResidentsTable.createdAt],
        updatedAt = this[ResidentsTable.updatedAt],
    )
}
