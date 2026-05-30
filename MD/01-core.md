# 核心模块 (Core)

## 包路径
`io.github.adlamb.cubex` — `CubeXSLG.kt`, `bootstrap/`, `module/`, `command/`

---

## 概述

核心模块负责插件的启动装配、生命周期管理、模块接口定义和命令系统注册。是所有其他模块的基础设施层。

---

## CubeXSLG.kt — 主插件入口

```
src/main/kotlin/io/github/adlamb/cubex/CubeXSLG.kt
```

- 继承 `JavaPlugin`，是 Bukkit 插件主类
- `onEnable()`: 调用 `CubeXBootstrap.initialize(this)` 完成全插件装配，输出启动日志
- `onDisable()`: 调用 `PluginRuntime.shutdown()` 进行清理

---

## bootstrap/CubeXBootstrap.kt — 启动装配器

```
src/main/kotlin/io/github/adlamb/cubex/bootstrap/CubeXBootstrap.kt
```

执行以下装配顺序：

1. 加载 `ConfigService` 解析所有 YAML
2. 初始化 `MessageService` 加载本地化消息
3. 创建 `PaperSchedulerFacade` (调度抽象)
4. 初始化 `DatabaseManager` (连接池 + Exposed)
5. 加载 `GameplayRegistry` (解析 gameplay YAML)
6. 初始化 `MarkerKeys`
7. 构建 `MenuFactory` + 注册 `MenuListener`
8. 实例化 `GameplayFacade` (核心引擎，注入 `GameplayRepository`)
9. 依次创建并启用 10 个 `FeatureModule`
10. 注册所有监听器和命令贡献者
11. 返回 `PluginRuntime` 封装

---

## bootstrap/PluginContext.kt — 运行时上下文

```
src/main/kotlin/io/github/adlamb/cubex/bootstrap/PluginContext.kt
```

- `PluginContext`: 包含所有共享服务的数据类（配置、消息、调度器、数据库、注册表、标记键、菜单工厂+监听器、游戏引擎）
- `PluginRuntime`: 包装 `PluginContext` 和功能模块列表，提供聚合的 `listeners`/`commandContributors` 以及 `shutdown()`

---

## module/FeatureModule.kt — 功能模块接口

```
src/main/kotlin/io/github/adlamb/cubex/module/FeatureModule.kt
```

所有功能子系统的统一接口：

```kotlin
interface FeatureModule {
    val id: String
    val commandContributors: List<CommandContributor>
    val listeners: List<Listener>
    fun onEnable()
    fun onDisable()
}
```

---

## command/ — 命令系统

```
src/main/kotlin/io/github/adlamb/cubex/command/
```

| 文件 | 职责 |
|------|------|
| `SlgCommandRegistrar.kt` | 创建根命令 `/slg` (Brigadier)，遍历 `CommandContributor` 添加子命令，注册 `/slg help` |
| `CommandContributor.kt` | 函数式接口，模块贡献子命令到 `/slg` 根 |
| `CommandSuggestions.kt` | 提供 `suggestMatching()` 工具，按前缀过滤 Tab 补全候选 |

### 注册命令列表

| 命令 | 所属模块 |
|------|----------|
| `/slg help` | 核心 |
| `/slg create <名称>` | 城镇 |
| `/slg border` | 城镇 |
| `/slg wand <建筑>` | 建筑 |
| `/slg repair` | 建筑 |
| `/slg confirm` | 建筑 |
| `/slg resources [stats\|history]` | 资源 |
| `/slg production` | 生产 |
| `/slg tech` | 科技 |
| `/slg residents` | 居民 |
| `/slg recruit` | 居民 |
| `/slg combat` | 战斗 |
| `/slg logistics` | 物流 |
| `/slg rpglink` | RPG 联动 |
| `/slg admin ...` | 管理 |
