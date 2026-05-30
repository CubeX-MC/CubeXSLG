# CubeXSLG 项目架构总览

## 项目简介

**CubeXSLG** 是一个 Minecraft Paper/Folia 1.21.8 策略模拟经营（SLG）插件，将城市建造经营玩法引入 Minecraft。玩家可以创建城镇、管理虚拟资源仓库、建造升级建筑、研究科技树、招募居民、抵御入侵——全部在原版 Minecraft 世界中实现。

- **语言**: Kotlin 2.3 (Java 21)
- **构建**: Gradle (Kotlin DSL) + shadowJar
- **框架**: Paper API (Folia 兼容)
- **数据库**: H2 (开发) / MariaDB (生产) via HikariCP + Exposed ORM
- **存储库**: [github.com/CubeX-MC/CubeXSLG](https://github.com/CubeX-MC/CubeXSLG)

---

## 总体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        CubeXSLG (JavaPlugin)                     │
├─────────────────────────────────────────────────────────────────┤
│                          CubeXBootstrap                          │
│  (依赖注入 + 生命周期管理，组装所有模块)                          │
├──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┬───────┤
│ Core │Config│ DB   │Registry│ Msg │ Menu │Game- │Plat  │ Util  │
│      │      │      │        │     │      │play  │form  │       │
├──────┴──────┴──────┴──────┬─┴─────┴──┬───┴──────┴──────┴───────┤
│                           │           │                         │
│         10 个 Feature Module (功能模块)                         │
│                           │           │                         │
│ Town Building Resource Production Tech Resident Combat          │
│ Logistics RpgLink Admin                                         │
└─────────────────────────────────────────────────────────────────┘
```

### 核心设计原则

- **数据驱动**: 所有游戏定义（建筑、科技、资源、城镇等级）从 YAML 加载，无硬编码值
- **Folia 兼容**: 所有异步操作通过 `SchedulerFacade` 抽象层，支持全局/区域/实体调度器
- **模块化**: 每个功能子系统的独立 `FeatureModule`，实现统一的生命周期接口
- **虚拟经济**: 无实物物品管理——所有资源均为数据库中的数字记录
- **GUI 配置化**: 菜单系统由 `menu-items.yml` 驱动，文本渲染使用 Adventure MiniMessage
- **本地化**: 所有玩家可见文本定义于 `messages.yml`，当前为中文

---

## 源代码目录结构

```
src/main/kotlin/io/github/adlamb/cubex/
├── CubeXSLG.kt              # 主插件入口
├── bootstrap/                # 启动装配 + 依赖注入
│   ├── CubeXBootstrap.kt
│   └── PluginContext.kt
├── module/                   # 功能模块接口定义
│   └── FeatureModule.kt
├── command/                  # 命令系统
│   ├── SlgCommandRegistrar.kt
│   ├── CommandContributor.kt
│   └── CommandSuggestions.kt
├── config/                   # YAML 配置加载
│   ├── ConfigService.kt
│   ├── PluginConfigs.kt
│   ├── MenuItemConfigs.kt
│   └── MenuItemDefaults.kt
├── database/                 # 持久化层
│   └── DatabaseManager.kt
├── registry/                 # 游戏定义注册表
│   └── GameplayRegistry.kt
├── message/                  # 本地化消息服务
│   └── MessageService.kt
├── menu/                     # GUI 菜单框架
│   ├── MenuFactory.kt
│   ├── MenuId.kt
│   ├── MenuHolder.kt
│   ├── MenuListener.kt
│   └── MenuRenderContext.kt
├── gameplay/                 # 核心游戏引擎 & 数据模型
│   ├── GameplayFacade.kt
│   ├── model/GameplayModels.kt
│   └── storage/
│       ├── GameplayTables.kt
│       └── GameplayRepository.kt
├── platform/                 # Paper/Folia 调度抽象
│   ├── SchedulerFacade.kt
│   └── FoliaSupport.kt
├── audio/                    # 音效服务
│   └── SoundService.kt
├── shared/                   # 共享工具
│   ├── MarkerKeys.kt
│   └── PlaceholderResponses.kt
├── util/                     # 工具类
│   └── SchematicLoader.kt
└── feature/                  # 10 个功能模块
    ├── admin/
    ├── building/
    ├── combat/
    ├── logistics/
    ├── production/
    ├── resident/
    ├── resource/
    ├── rpglink/
    ├── tech/
    └── town/
```

---

## 模块引用

| 模块 | 文件 | 描述 |
|------|------|------|
| [核心模块](01-core.md) | `CubeXSLG.kt`, `bootstrap/`, `module/`, `command/` | 主入口、启动装配、模块接口、命令注册 |
| [配置模块](02-config.md) | `config/` | YAML 配置加载与验证 |
| [数据库模块](03-database.md) | `database/`, `gameplay/storage/` | 持久化层、ORM 表定义、数据仓库 |
| [注册表模块](04-registry.md) | `registry/` | 游戏定义（建筑/资源/科技/城镇等级） |
| [消息模块](05-message.md) | `message/` | 本地化消息服务 |
| [菜单模块](06-menu.md) | `menu/` | GUI 菜单渲染框架 |
| [游戏引擎模块](07-gameplay.md) | `gameplay/` | 核心游戏逻辑引擎与领域模型 |
| [平台抽象模块](08-platform.md) | `platform/` | 调度器抽象与 Folia 检测 |
| [工具模块](09-util.md) | `util/`, `shared/`, `audio/` | 原理图加载、标记键、音效 |
| [城镇模块](10-feature-town.md) | `feature/town/` | 城镇创建与管理 |
| [建筑模块](11-feature-building.md) | `feature/building/` | 建筑放置与交互 |
| [资源模块](12-feature-resource.md) | `feature/resource/` | 虚拟资源查看与统计 |
| [生产模块](13-feature-production.md) | `feature/production/` | 生产链总览 |
| [科技模块](14-feature-tech.md) | `feature/tech/` | 科技树系统 |
| [居民模块](15-feature-resident.md) | `feature/resident/` | 居民 AI 与招募 |
| [战斗模块](16-feature-combat.md) | `feature/combat/` | 战斗与建筑耐久度 |
| [物流模块](17-feature-logistics.md) | `feature/logistics/` | 铁路与运输网络 |
| [RPG 联动模块](18-feature-rpglink.md) | `feature/rpglink/` | 跨世界 RPG 联动 |
| [管理模块](19-feature-admin.md) | `feature/admin/` | 管理员操作指令 |

---

## 构建与运行

```bash
./gradlew build          # 编译打包
./gradlew shadowJar      # 生成 fat JAR
./gradlew runServer      # 启动本地 Paper 服务器
./gradlew clean          # 清理构建产物
```

---

## 数据流向

```
玩家操作 → 命令/事件 → FeatureModule → GameplayFacade → GameplayRepository → Database (Exposed)
                                                                   ↓
                                                            GameplayRegistry (YAML 定义)
                                                                   ↓
                                                            SchedulerFacade (异步/定时任务)
```

---

## 统计信息

| 指标 | 数量 |
|------|------|
| Kotlin 源文件 | 39 |
| YAML 配置/数据文件 | 14 |
| WorldEdit 原理图 | 4 |
| Kotlin 代码行数 | ~5,000+ |
| 功能模块 | 10 |
| 数据库表 | 11 |
| GUI 界面 | 9 |
| 命令 | 15+ |
