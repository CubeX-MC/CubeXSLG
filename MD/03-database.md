# 数据库模块 (Database)

## 包路径
`io.github.adlamb.cubex.database` — `DatabaseManager.kt`
`io.github.adlamb.cubex.gameplay.storage` — `GameplayTables.kt`, `GameplayRepository.kt`

---

## 概述

持久化层负责所有游戏数据的存储与读取。使用 HikariCP 连接池 + Exposed ORM 框架，支持 H2（本地开发）和 MariaDB（生产环境）两种模式。包含 11 张数据库表，覆盖城镇、建筑、资源、居民、科技等全部游戏状态。

---

## DatabaseManager.kt — 数据库管理器

```
src/main/kotlin/io/github/adlamb/cubex/database/DatabaseManager.kt
```

- 管理 HikariCP 连接池生命周期
- 支持两种模式:
  - **H2**: 本地文件存储，MySQL 兼容模式，适合开发测试
  - **MariaDB**: 生产级 MySQL 兼容数据库
- 初始化时自动创建 `cubexslg_metadata` 元数据表，记录数据库版本

**关键方法**:

| 方法 | 功能 |
|------|------|
| `init()` | 启动连接池，注册 Exposed 数据库，创建元数据表 |
| `shutdown()` | 关闭连接池 |
| `transaction(block)` | 执行 Exposed 事务操作 |

---

## GameplayTables.kt — ORM 表定义

```
src/main/kotlin/io/github/adlamb/cubex/gameplay/storage/GameplayTables.kt
```

所有表均使用 `cubexslg_` 前缀：

| 表 | 字段 | 用途 |
|----|------|------|
| `TownsTable` | id, name, owner_uuid, level, created_at, updated_at | 城镇记录 |
| `ResourceBalancesTable` | town_id, resource_key, balance | 每种资源的当前数量 |
| `ResourceLedgerTable` | id, town_id, resource_key, delta, balance_after, reason, timestamp | 资源变动流水账 |
| `BuildingsTable` | id, town_id, building_type, level, world, x, y, z, health, created_at | 已建造的建筑 |
| `BuildingBlocksTable` | building_id, x, y, z | 建筑占用的方块位置 |
| `ResidentsTable` | id, town_id, villager_uuid, name, attributes_json, assigned_building_id, hired_at | 招募的居民 |
| `TechProgressTable` | town_id, tech_key, researched | 科技解锁状态 |
| `PendingActionsTable` | id, town_id, action_type, action_data_json, started_at, duration_ticks | 进行中的操作队列 |
| `RailRoutesTable` | id, town_id, from_x, from_y, from_z, to_x, to_y, to_z, throughput | 铁路路线记录 |
| `CargoJobsTable` | id, route_id, resource_key, amount, status, created_at | 货运任务队列 |
| `CombatStateTable` | building_id, last_damage_time, damage_cooldown | 战斗状态与冷却 |

---

## GameplayRepository.kt — 数据仓库

```
src/main/kotlin/io/github/adlamb/cubex/gameplay/storage/GameplayRepository.kt
```

封装所有 CRUD 操作，通过 Exposed 事务执行。

**核心方法**:

| 方法 | 功能 |
|------|------|
| `towns()` | 获取所有城镇 |
| `townByOwner(uuid)` | 获取玩家拥有的城镇 |
| `townByName(name)` | 按名称查找城镇 |
| `createTown(...)` | 创建新城镇 |
| `deleteTown(id)` | 删除城镇及关联数据 |
| `loadBalances(townId)` | 加载城镇资源余额 |
| `adjustBalance(townId, key, delta, reason)` | 调整资源 + 记录流水 |
| `saveBuilding(state)` | 保存/更新建筑 |
| `deleteBuilding(id)` | 删除建筑 |
| `residentsByTown(townId)` | 获取城镇居民列表 |
| `techProgress(townId)` | 获取科技解锁状态 |
| `pendingActions(townId)` | 获取进行中的操作 |
| `routes(townId)` | 获取铁路路线列表 |

---

## 数据模型

详见 [游戏引擎模块](07-gameplay.md) 中的 `GameplayModels.kt` 定义。
