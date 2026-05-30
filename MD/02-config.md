# 配置模块 (Config)

## 包路径
`io.github.adlamb.cubex.config` — `config/`

---

## 概述

配置模块负责从 YAML 文件加载和解析所有插件配置，包含插件基础配置、数据库配置、菜单配置、消息配置，以及 YAML 定义的游戏数据。提供默认值和数据验证。

---

## 文件结构

| 文件 | 行数 | 职责 |
|------|------|------|
| `ConfigService.kt` | 218 | 加载和解析所有 YAML 配置的核心服务 |
| `PluginConfigs.kt` | 41 | 插件/数据库配置数据类 |
| `MenuItemConfigs.kt` | 48 | 菜单配置数据类 |
| `MenuItemDefaults.kt` | 200 | 内置菜单定义回退方案 |

---

## ConfigService.kt — 配置加载服务

```
src/main/kotlin/io/github/adlamb/cubex/config/ConfigService.kt
```

加载以下 YAML 文件并映射为类型安全的数据类：

| 源文件 | 目标类型 | 用途 |
|--------|----------|------|
| `config.yml` | `PluginConfigs` | 语言、调试、菜单行数、启动验证 |
| `database.yml` | `DatabaseConfig` | H2/MariaDB 连接参数、连接池配置 |
| `messages.yml` | `Map<String, String>` | 玩家可见消息模板 (MiniMessage) |
| `menu-items.yml` | `MenuViewConfig` | GUI 菜单布局定义 |
| `gameplay/*.yml` | — | 委托给 `GameplayRegistry` 加载游戏定义 |
| `buildings.yml` | — | 建筑类型定义与配方 |
| `resources.yml` | — | 资源定义与分类 |
| `tech.yml` | — | 科技树节点定义 |
| `town.yml` | — | 城镇等级规则 |

对缺失的必要配置项提供合理的默认值。

---

## PluginConfigs.kt — 配置数据类

```
src/main/kotlin/io/github/adlamb/cubex/config/PluginConfigs.kt
```

```kotlin
data class PluginConfigs(
    val locale: String = "zh-CN",
    val debug: Boolean = false,
    val menuPlaceholderRows: Int = 4,
    val validateDatabase: Boolean = true
)

data class DatabaseConfig(
    val mode: DatabaseMode = DatabaseMode.H2,
    val pool: PoolSettings = PoolSettings(),
    val h2: H2Settings = H2Settings(),
    val mariadb: MariaDbSettings = MariaDbSettings()
)

enum class DatabaseMode { H2, MARIADB }
```

- `DatabaseMode`: 数据库模式枚举（H2 开发 / MariaDB 生产）
- `PoolSettings`: 连接池大小、超时等参数
- `H2Settings`: H2 文件路径、兼容模式
- `MariaDbSettings`: 主机、端口、数据库名、用户、密码、SSL

---

## MenuItemConfigs.kt — 菜单配置数据类

```
src/main/kotlin/io/github/adlamb/cubex/config/MenuItemConfigs.kt
```

```kotlin
data class MenuViewConfig(
    val title: Component,
    val glass: ItemStack?,
    val body: MenuBodyConfig?,
    val buttons: Map<String, MenuButtonTemplate>,
    val dynamicLists: Map<String, MenuDynamicListTemplate>
)
```

支持组件：
- `MenuBodyConfig`: 主体内容槽位定义
- `MenuItemTemplate`: 物品模板（材质、名称、Lore、数量）
- `MenuButtonTemplate`: 可点击按钮定义
- `MenuDynamicListTemplate`: 动态列表定义（滚动物品）

---

## MenuItemDefaults.kt — 内置菜单回退定义

```
src/main/kotlin/io/github/adlamb/cubex/config/MenuItemDefaults.kt
```

当 `menu-items.yml` 缺失或不完整时，为所有 9 个 GUI 界面提供内置回退定义：

| 菜单 | 用途 |
|------|------|
| `TOWN_HALL` | 城镇管理（城府） |
| `STORAGE` | 仓库（资源查看） |
| `BUILDING` | 建筑管理 |
| `RESIDENT` | 居民管理 |
| `TECH` | 科技树 |
| `PRODUCTION` | 生产总览 |
| `COMBAT` | 战斗信息 |
| `LOGISTICS` | 物流总览 |
| `RPG_LINK` | RPG 联动信息 |

---

## 配置文件位置

所有配置文件位于 `src/main/resources/` 目录：

```
resources/
├── config.yml           # 插件基础配置
├── database.yml         # 数据库连接配置
├── messages.yml         # 本地化消息模板
├── menu-items.yml       # GUI 菜单布局定义
└── gameplay/            # 游戏数据定义
    ├── buildings.yml    # 建筑定义
    ├── resources.yml    # 资源定义
    ├── tech.yml         # 科技树定义
    └── town.yml         # 城镇等级定义
```
