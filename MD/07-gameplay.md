# 游戏引擎模块 (Gameplay)

## 包路径
`io.github.adlamb.cubex.gameplay` — `GameplayFacade.kt`, `model/GameplayModels.kt`, `storage/`

---

## 概述

游戏引擎模块是 CubeXSLG 的核心大脑。包含 1500+ 行的 `GameplayFacade` 集中编排所有游戏逻辑，以及一组领域模型数据类。它协调城镇管理、建筑放置、资源平衡、生产滴答、科技研究、居民招募、战斗伤害等所有子系统。

---

## GameplayFacade.kt — 核心游戏引擎

```
src/main/kotlin/io/github/adlamb/cubex/gameplay/GameplayFacade.kt
```

### 维护的组件

| 组件 | 类型 |
|------|------|
| `GameplayRepository` | 数据访问层 |
| `SchematicLoader` | 原理图加载器 |
| `buildingBoundsMap` | 内存中的建筑边界映射 |
| `GameplayRegistry` | 游戏定义注册表 |

### 城镇管理

| 方法 | 功能 |
|------|------|
| `createTown(player, name)` | 创建城镇：验证名称、检测重叠、设置初始资源、放置城府原理图 |
| `deleteTown(townId)` | 删除城镇及其所有关联数据 |
| `upgradeTown(townId)` | 升级城镇等级 |
| `getTownBorder(location)` | 获取城镇边界粒子效果预览 |
| `findTownAt(location)` | 查找坐标所在的城镇 |

### 建筑操作

| 方法 | 功能 |
|------|------|
| `previewBuilding(player, buildingType)` | 建筑魔杖放置预览（绿色/红色粒子盒） |
| `placeBuilding(player, buildingType, location)` | 放置建筑：验证成本、扣除资源、粘贴原理图 |
| `upgradeBuilding(buildingId)` | 升级建筑 |
| `repairBuilding(buildingId)` | 修复建筑耐久度 |
| `moveBuilding(buildingId, newLocation)` | 移动建筑 |
| `deleteBuilding(buildingId)` | 拆除建筑（清除原理图块 + 数据库） |

### 资源管理

| 方法 | 功能 |
|------|------|
| `getBalance(townId, resource)` | 获取资源余额 |
| `adjustBalance(townId, resource, delta, reason)` | 调整资源并记录流水 |
| `hasSufficientResources(townId, costs)` | 检查资源是否足够 |
| `deductCosts(townId, costs, reason)` | 扣除建造成本 |
| `getNetProduction(townId)` | 获取每秒净产量 |

### 生产系统

| 方法 | 功能 |
|------|------|
| `tickWorld()` | 20 游戏刻为周期的生产滴答：遍历所有建筑，消耗输入资源，产出输出资源 |
| `getProductionRate(building, townId)` | 计算含科技倍率的实际生产率 |

### 科技系统

| 方法 | 功能 |
|------|------|
| `research(townId, techKey)` | 研究科技：验证前置条件、城镇等级、成本；解锁并应用效果 |
| `getUnlockedTechs(townId)` | 获取已解锁科技列表 |
| `getAvailableTechs(townId)` | 获取可研究的科技列表 |

### 居民系统

| 方法 | 功能 |
|------|------|
| `recruit(player, townId)` | 招募居民：生成 Villager 实体，赋予随机属性 |
| `getResidents(townId)` | 获取城镇居民列表 |
| `assignResident(residentId, buildingId)` | 分配居民到建筑 |

### 战斗系统

| 方法 | 功能 |
|------|------|
| `hitBuildingBlock(location, damage)` | 对方的攻击对建筑方块造成伤害 |
| `applyExplosionDamageToBuilding(location, explosion)` | 处理爆炸对建筑的结构伤害 |
| `getBuildingHealth(buildingId)` | 获取建筑当前耐久度 |

---

## GameplayModels.kt — 领域模型

```
src/main/kotlin/io/github/adlamb/cubex/gameplay/model/GameplayModels.kt
```

### 核心数据类

```kotlin
data class TownState(
    val id: TownId,
    val name: String,
    val owner: UUID,
    val level: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class BuildingState(
    val id: BuildingId,
    val townId: TownId,
    val buildingType: String,
    val level: Int,
    val location: Location,
    val health: Double,
    val createdAt: Instant
)

data class ResidentState(
    val id: ResidentId,
    val townId: TownId,
    val villagerUuid: UUID,
    val name: String,
    val attributes: Map<String, Double>,
    val assignedBuildingId: BuildingId?,
    val hiredAt: Instant
)

data class TownResourceBalance(
    val resourceKey: String,
    val balance: Double
)

data class ResourceLedgerEntry(
    val id: Long,
    val townId: TownId,
    val resourceKey: String,
    val delta: Double,
    val balanceAfter: Double,
    val reason: String,
    val timestamp: Instant
)

data class TechProgressState(
    val techKey: String,
    val researched: Boolean
)

data class PendingAction(
    val id: Long,
    val townId: TownId,
    val actionType: String,
    val actionData: String,
    val startedAt: Instant,
    val durationTicks: Long
)

data class RailRoute(
    val id: Long,
    val townId: TownId,
    val from: Location,
    val to: Location,
    val throughput: Double
)

data class BuildingBlockState(
    val buildingId: BuildingId,
    val x: Int, val y: Int, val z: Int
)
```

### 值类型

```kotlin
@JvmInline value class TownId(val value: UUID)
@JvmInline value class BuildingId(val value: UUID)
@JvmInline value class ResidentId(val value: UUID)
```

---

## 生产滴答流程

```
每 20 游戏刻 (1 秒):
  GameplayFacade.tickWorld()
    ├── 遍历所有城镇
    │   ├── 遍历城镇中所有 producer 建筑
    │   │   ├── 检查消耗资源是否充足
    │   │   ├── 扣除消耗
    │   │   └── 增加产出 (含科技倍率)
    │   └── 更新流水账
    └── 检查进行中的 PendingAction
```
