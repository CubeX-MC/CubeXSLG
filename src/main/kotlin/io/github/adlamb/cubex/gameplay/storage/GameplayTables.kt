package io.github.adlamb.cubex.gameplay.storage

import org.jetbrains.exposed.v1.core.Table

object TownsTable : Table("cubexslg_towns") {
    val id = varchar("id", 36)
    val ownerUuid = varchar("owner_uuid", 36).uniqueIndex()
    val name = varchar("name", 64)
    val world = varchar("world", 64)
    val x = double("x")
    val y = double("y")
    val z = double("z")
    val yaw = float("yaw")
    val pitch = float("pitch")
    val level = integer("level")
    val radius = integer("radius")
    val buildingLimit = integer("building_limit")
    val residentLimit = integer("resident_limit")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object ResourceBalancesTable : Table("cubexslg_resource_balances") {
    val townId = varchar("town_id", 36)
    val resourceKey = varchar("resource_key", 64)
    val amount = long("amount")

    override val primaryKey = PrimaryKey(townId, resourceKey)
}

object ResourceLedgerTable : Table("cubexslg_resource_ledger") {
    val id = long("id").autoIncrement()
    val townId = varchar("town_id", 36)
    val resourceKey = varchar("resource_key", 64)
    val delta = long("delta")
    val balanceAfter = long("balance_after")
    val reason = varchar("reason", 128)
    val origin = varchar("source", 128)
    val actor = varchar("actor", 36).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object BuildingsTable : Table("cubexslg_buildings") {
    val id = varchar("id", 36)
    val townId = varchar("town_id", 36)
    val buildingKey = varchar("building_key", 64)
    val world = varchar("world", 64)
    val x = double("x")
    val y = double("y")
    val z = double("z")
    val yaw = float("yaw")
    val pitch = float("pitch")
    val level = integer("level")
    val health = integer("health")
    val maxHealth = integer("max_health")
    val active = bool("active")
    val collapsed = bool("collapsed")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object BuildingBlocksTable : Table("cubexslg_building_blocks") {
    val buildingId = varchar("building_id", 36)
    val world = varchar("world", 64)
    val x = integer("x")
    val y = integer("y")
    val z = integer("z")
    val material = varchar("material", 64)

    override val primaryKey = PrimaryKey(buildingId, x, y, z)
}

object ResidentsTable : Table("cubexslg_residents") {
    val id = varchar("id", 36)
    val townId = varchar("town_id", 36)
    val uuid = varchar("uuid", 36).uniqueIndex()
    val name = varchar("name", 64)
    val world = varchar("world", 64)
    val x = double("x")
    val y = double("y")
    val z = double("z")
    val homeX = double("home_x")
    val homeY = double("home_y")
    val homeZ = double("home_z")
    val jobBuildingId = varchar("job_building_id", 36).nullable()
    val jobRole = varchar("job_role", 64).nullable()
    val strength = integer("strength")
    val agility = integer("agility")
    val intelligence = integer("intelligence")
    val endurance = integer("endurance")
    val management = integer("management")
    val active = bool("active")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object TechProgressTable : Table("cubexslg_tech_progress") {
    val townId = varchar("town_id", 36)
    val techKey = varchar("tech_key", 64)
    val researchedAt = long("researched_at")
    val researcherUuid = varchar("researcher_uuid", 36)

    override val primaryKey = PrimaryKey(townId, techKey)
}

object PendingActionsTable : Table("cubexslg_pending_actions") {
    val id = long("id").autoIncrement()
    val townId = varchar("town_id", 36)
    val actorUuid = varchar("actor_uuid", 36)
    val actionType = varchar("action_type", 32)
    val payload = text("payload")
    val expiresAt = long("expires_at")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object RailRoutesTable : Table("cubexslg_rail_routes") {
    val id = varchar("id", 36)
    val townId = varchar("town_id", 36)
    val name = varchar("name", 64)
    val throughput = double("throughput")
    val saturation = double("saturation")

    override val primaryKey = PrimaryKey(id)
}

object CargoJobsTable : Table("cubexslg_cargo_jobs") {
    val id = long("id").autoIncrement()
    val townId = varchar("town_id", 36)
    val routeId = varchar("route_id", 36)
    val buildingId = varchar("building_id", 36)
    val resourceKey = varchar("resource_key", 64)
    val amount = long("amount")
    val status = varchar("status", 32)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object CombatStateTable : Table("cubexslg_combat_state") {
    val townId = varchar("town_id", 36)
    val buildingId = varchar("building_id", 36)
    val damage = integer("damage")
    val status = varchar("status", 32)
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(townId, buildingId)
}

object PowerConnectionsTable : Table("cubexslg_power_connections") {
    val id = varchar("id", 36)
    val townId = varchar("town_id", 36)
    val sourceBuildingId = varchar("source_building_id", 36)
    val targetBuildingId = varchar("target_building_id", 36).uniqueIndex()
    val createdAt = long("created_at")

    init {
        index(isUnique = true, sourceBuildingId, targetBuildingId)
    }

    override val primaryKey = PrimaryKey(id)
}
