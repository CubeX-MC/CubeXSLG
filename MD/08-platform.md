# 平台抽象模块 (Platform)

## 包路径
`io.github.adlamb.cubex.platform` — `SchedulerFacade.kt`, `FoliaSupport.kt`

---

## 概述

平台抽象模块提供 Paper 与 Folia 两种服务器环境之间的兼容层，确保插件在不修改代码的情况下同时在 Paper 和 Folia 上运行。核心是封装了 Folia 的 Regional Scheduler API 的调度器抽象。

---

## SchedulerFacade.kt — 调度器抽象

```
src/main/kotlin/io/github/adlamb/cubex/platform/SchedulerFacade.kt
```

### 接口层次

```kotlin
interface SchedulerFacade {
    fun global(): TickScheduler          // 全局调度器
    fun region(location: Location): TickScheduler  // 区域调度器（Folia 分区分片）
    fun entity(entity: Entity): TickScheduler      // 实体调度器
    fun async(): AsyncScheduler          // 异步调度器
}

interface TickScheduler {
    fun execute(task: Runnable)          // 立即执行
    fun runLater(task: Runnable, delayTicks: Long): TaskHandle  // 延迟执行
    fun runTimer(task: Runnable, delayTicks: Long, periodTicks: Long): TaskHandle  // 定时执行
}
```

### 实现类 — PaperSchedulerFacade

- 使用 Paper 的 `Bukkit.getGlobalRegionScheduler()`, `Bukkit.getRegionScheduler()`, `Bukkit.getAsyncScheduler()`
- 在纯 Paper 环境中，区域调度器退化为全局行为
- 在 Folia 环境中，区域调度器按世界分片管理线程

### 设计目的

Folia 将世界划分为多个独立区域（Region），每个区域拥有独立的游戏刻线程。直接使用 BukkitRunnable 或 Scheduler 在 Folia 上会报错。通过此抽象：

- 方块操作 → 使用 `region(location)` 确保在正确的区域线程执行
- 实体操作 → 使用 `entity(entity)` 在实体所属区域执行
- 全局定时任务 → 使用 `global()`
- 数据库/IO 操作 → 使用 `async()`

---

## FoliaSupport.kt — Folia 检测工具

```
src/main/kotlin/io/github/adlamb/cubex/platform/FoliaSupport.kt
```

```kotlin
object FoliaSupport {
    val isFolia: Boolean by lazy {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}
```

通过反射检测 Folia 特有类判断服务器环境，供插件根据运行环境调整行为。
