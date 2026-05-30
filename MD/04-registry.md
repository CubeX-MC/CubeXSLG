# 注册表模块 (Registry)

## 包路径
`io.github.adlamb.cubex.registry` — `GameplayRegistry.kt`

---

## 概述

注册表模块负责从 YAML 数据文件加载所有游戏定义，提供类型安全的查找 API。所有游戏规则（建筑类型、资源定义、科技树节点、城镇等级）均在此集中管理，实现数据驱动设计。

---

## GameplayRegistry.kt — 游戏定义注册表

```
src/main/kotlin/io/github/adlamb/cubex/registry/GameplayRegistry.kt
```

### 加载来源

| YAML 文件 | 加载内容 |
|-----------|----------|
| `gameplay/resources.yml` | 资源定义与分类 |
| `gameplay/buildings.yml` | 建筑类型与配方 |
| `gameplay/town.yml` | 城镇等级规则 |
| `gameplay/tech.yml` | 科技树节点 |

### 核心类型

#### 资源定义

```kotlin
data class ResourceDescriptor(
    val key: String,              // 资源标识符
    val displayName: String,      // 显示名称
    val category: ResourceCategory, // 分类
    val icon: ItemStack           // 图标物品
)

enum class ResourceCategory { BASIC, PROCESSED, SPECIAL }
```

- **BASIC**（基础）: 木材、石头、矿石、粮食
- **PROCESSED**（加工）: 木板、石材、金属锭
- **SPECIAL**（特殊）: 科技点、居民口粮

#### 建筑定义

```kotlin
data class BuildingDescriptor(
    val key: String,                  // 建筑标识符
    val displayName: String,          // 显示名称
    val kind: BuildingKind,           // 建筑类别（枚举）
    val cost: Map<String, Double>,    // 建造成本（资源映射）
    val production: Map<String, Double>, // 每秒产出
    val consumption: Map<String, Double>, // 每秒消耗
    val maxLevel: Int,                // 最大等级
    val upgradeCost: Map<String, Double>, // 每级升级成本
    val wandMaterial: Material,       // 建筑魔杖材质
    val schemFile: String,            // 原理图文件名
    val blockSpecs: List<BlockSpec>   // 功能方块规格
)

enum class BuildingKind { PRODUCER, STORAGE, DEFENSE, UTILITY, MUNICIPAL }
```

内置 11 种建筑类型，每种有独立的建造/升级消耗、产出配方和原理图。

#### 科技树节点

```kotlin
data class TechNode(
    val key: String,                  // 科技标识符
    val displayName: String,          // 显示名称
    val branch: TechBranch,           // 科技分支（枚举）
    val prerequisites: List<String>,  // 前置科技列表
    val cost: Map<String, Double>,    // 研究消耗
    val requiredTownLevel: Int,       // 所需城镇等级
    val unlocks: List<String>,        // 解锁内容
    val productionMultiplier: Double, // 生产倍率加成
    val icon: ItemStack               // 图标物品
)

enum class TechBranch { AGRICULTURE, ENGINEERING, MAGIC, MILITARY, CIVICS }
```

内置 11 个科技节点，分布于 5 个分支。

### 查找方法

| 方法 | 功能 |
|------|------|
| `findResource(key)` | 按标识符查找资源定义 |
| `findBuilding(key)` | 按标识符查找建筑定义 |
| `resourcesByCategory(cat)` | 按分类获取资源列表 |
| `levelFor(townLevel)` | 获取城镇等级定义 |
| `tech(key)` | 获取科技节点 |
| `unlocksBuilding(techKey)` | 获取科技解锁的建筑 |
| `productionMultiplier(townId)` | 计算城镇的生产倍率 |
