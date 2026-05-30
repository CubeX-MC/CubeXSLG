# 工具模块 (Utility / Shared / Audio)

## 包路径
`io.github.adlamb.cubex.util` — `SchematicLoader.kt`
`io.github.adlamb.cubex.shared` — `MarkerKeys.kt`, `PlaceholderResponses.kt`
`io.github.adlamb.cubex.audio` — `SoundService.kt`

---

## 概述

工具模块包含插件的辅助性基础设施：WorldEdit 原理图加载与粘贴引擎、全插件的命名空间标记键注册表、占位符工具和音效播放服务。

---

## SchematicLoader.kt — 原理图加载器

```
src/main/kotlin/io/github/adlamb/cubex/util/SchematicLoader.kt
```

### 功能

- 从 `schematics/` 目录加载 `.schem` 文件
- 将原理图异步粘贴到世界
- 扫描原理图中 `[SLG]` 标记的告示牌以识别功能方块
- 统计非空气方块计数（用于计算建筑耐久度）
- 提供建筑拆除功能

### 方块标记约定

| 告示牌文本 | 功能 | 实际方块 |
|-----------|------|----------|
| `[SLG] CORE` | 建筑核心 | 木桶 (Barrel) |
| `[SLG] INPUT` | 输入口 | 投掷器 (Dropper) |
| `[SLG] OUTPUT` | 输出口 | 分发器 (Dispenser) |
| `[SLG] POWER` | 能源接口 | 树脂砖墙 (Resin Brick Wall) |

### 关键方法

| 方法 | 功能 |
|------|------|
| `loadSchematic(name)` | 加载原理图文件，返回 `SchematicDimensions` |
| `pasteSchematic(world, location, name, rotation)` | 异步粘贴原理图 |
| `removeSchematicFromWorld(world, origin, dimensions)` | 拆除已粘贴的原理图方块 |
| `scanForMarkers(world, origin, dimensions)` | 扫描功能方块标记 |

### 缓存策略

- 原理图尺寸缓存：加速后续加载
- 异步加载+区域调度器粘贴：兼容 Folia

---

## MarkerKeys.kt — 命名空间标记键

```
src/main/kotlin/io/github/adlamb/cubex/shared/MarkerKeys.kt
```

所有 `NamespacedKey` 常量注册表，用于 `PersistentDataContainer` 标记：

| 键 | 用途 |
|---|------|
| `buildingType` | 建筑类型标记 |
| `buildingId` | 建筑 UUID 标记 |
| `townId` | 城镇 UUID 标记 |
| `residentId` | 居民 UUID 标记 |
| `pendingAction` | 待处理操作标记 |
| `menuId` | 菜单标识标记 |
| `schemOriginX/Y/Z` | 原理图原点坐标 |
| `schemWidth/Height/Length` | 原理图尺寸 |
| `schemRotation` | 原理图旋转角度 |

---

## SoundService.kt — 音效服务

```
src/main/kotlin/io/github/adlamb/cubex/audio/SoundService.kt
```

```kotlin
object SoundService {
    fun playAt(location: Location, sound: Sound)     // 在世界位置播放
    fun playTo(player: Player, sound: Sound)          // 向玩家播放
    fun playError(player: Player)                     // 播放错误提示音
}
```

---

## PlaceholderResponses.kt — 占位符回复

```
src/main/kotlin/io/github/adlamb/cubex/shared/PlaceholderResponses.kt
```

提供尚未实现的功能的占位回复文本，用于物流（logistics）和 RPG 联动（rpglink）等尚在规划中的模块。
