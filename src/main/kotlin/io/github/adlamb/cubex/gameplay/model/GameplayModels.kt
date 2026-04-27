package io.github.adlamb.cubex.gameplay.model

import io.github.adlamb.cubex.registry.BuildingKind
import io.github.adlamb.cubex.registry.ResourceCategory
import io.github.adlamb.cubex.registry.TechBranch
import java.util.UUID

@JvmInline
value class TownId(val value: String)

@JvmInline
value class BuildingId(val value: String)

@JvmInline
value class ResidentId(val value: String)

data class ResourceLedgerEntry(
    val id: Long? = null,
    val townId: TownId,
    val resourceKey: String,
    val delta: Long,
    val balanceAfter: Long,
    val reason: String,
    val source: String,
    val actor: String? = null,
    val createdAt: Long,
)

data class RailRoute(
    val id: Long? = null,
    val townId: TownId,
    val name: String,
    val throughput: Double,
    val saturation: Double,
)

data class PendingAction(
    val id: Long? = null,
    val townId: TownId,
    val actorUuid: String,
    val actionType: String,
    val payload: String,
    val expiresAt: Long,
    val createdAt: Long,
)

data class RpgBridge(
    val enabled: Boolean,
    val bridgeState: String,
)

data class TownState(
    val id: TownId,
    val ownerUuid: UUID,
    val name: String,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val level: Int,
    val radius: Int,
    val buildingLimit: Int,
    val residentLimit: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BuildingState(
    val id: BuildingId,
    val townId: TownId,
    val buildingKey: String,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val level: Int,
    val health: Int,
    val maxHealth: Int,
    val active: Boolean,
    val collapsed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BuildingBlockState(
    val buildingId: BuildingId,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val material: String,
)

data class ResidentState(
    val id: ResidentId,
    val townId: TownId,
    val uuid: UUID,
    val name: String,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val homeX: Double,
    val homeY: Double,
    val homeZ: Double,
    val jobBuildingId: String?,
    val jobRole: String?,
    val strength: Int,
    val agility: Int,
    val intelligence: Int,
    val endurance: Int,
    val management: Int,
    val active: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class TechProgressState(
    val townId: TownId,
    val techKey: String,
    val researchedAt: Long,
    val researcherUuid: String,
)

data class TownResourceBalance(
    val townId: TownId,
    val resourceKey: String,
    val amount: Long,
)

fun UUID.asString(): String = toString()

fun String.toTownId(): TownId = TownId(this)

fun String.toBuildingId(): BuildingId = BuildingId(this)

fun String.toResidentId(): ResidentId = ResidentId(this)

fun normalizeKey(value: String): String = value.lowercase()
