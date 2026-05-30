# 菜单模块 (Menu)

## 包路径
`io.github.adlamb.cubex.menu` — `menu/`

---

## 概述

菜单模块提供完整的自定义背包 GUI 框架。所有菜单界面由 `menu-items.yml` 驱动配置，支持玻璃板背景、静态内容区域、可点击按钮、滚动动态列表等特性。使用 Adventure MiniMessage 渲染标题和物品 Lore。

---

## 文件结构

| 文件 | 行数 | 职责 |
|------|------|------|
| `MenuFactory.kt` | 207 | GUI 渲染引擎核心 |
| `MenuId.kt` | 17 | 菜单标识符枚举 |
| `MenuHolder.kt` | 14 | 存储点击回调的 InventoryHolder |
| `MenuListener.kt` | 30 | 点击/关闭事件处理 |
| `MenuRenderContext.kt` | 27 | 渲染时数据容器 |

---

## MenuId.kt — 菜单标识符

```
src/main/kotlin/io/github/adlamb/cubex/menu/MenuId.kt
```

```kotlin
enum class MenuId {
    TOWN_HALL, STORAGE, BUILDING, RESIDENT,
    TECH, PRODUCTION, COMBAT, LOGISTICS, RPG_LINK
}
```

每个枚举值映射到 `menu-items.yml` 中的 kebab-case 配置键（如 `town-hall`, `storage` 等）。

---

## MenuFactory.kt — 菜单工厂

```
src/main/kotlin/io/github/adlamb/cubex/menu/MenuFactory.kt
```

核心渲染引擎，提供 `open()` 方法创建并显示菜单界面。

### 渲染流程

1. 从 `MenuViewConfig` 获取菜单配置（标题、背景、内容、按钮、动态列表）
2. 创建 `Inventory`，设置 `MenuHolder` 存储点击回调
3. 渲染 **玻璃板背景**（占位行）
4. 渲染 **主体内容**（`MenuBodyEntry` 列表，每个条目放入指定槽位）
5. 渲染 **按钮**（`MenuButtonContext`，设置点击事件）
6. 渲染 **动态列表**（`MenuDynamicEntry`，可滚动物品列表）
7. 打开背包给玩家

### 特性

- MiniMessage 标题和 Lore 渲染
- 回退机制：配置缺失时使用 `MenuItemDefaults` 的内置定义
- 分页滚动动态列表

---

## MenuHolder.kt — 菜单持有者

```
src/main/kotlin/io/github/adlamb/cubex/menu/MenuHolder.kt
```

实现 `InventoryHolder` 接口，存储每个槽位的点击回调映射 `Map<Int, (Player) -> Unit>`。

---

## MenuListener.kt — 菜单事件监听器

```
src/main/kotlin/io/github/adlamb/cubex/menu/MenuListener.kt
```

| 事件 | 处理逻辑 |
|------|----------|
| `InventoryClickEvent` | 仅当持有者为 `MenuHolder` 时处理；取消所有点击；执行对应槽位的回调函数 |
| `InventoryCloseEvent` | 播放关闭音效 |

---

## MenuRenderContext.kt — 渲染上下文

```
src/main/kotlin/io/github/adlamb/cubex/menu/MenuRenderContext.kt
```

运行时数据容器，将调用方提供的数据传递给 `MenuFactory.open()`：

| 类 | 用途 |
|----|------|
| `MenuRenderContext` | 总体渲染上下文（占位符映射、主体行、按钮、动态列表） |
| `MenuBodyEntry` | 单条静态内容条目（槽位、物品、数量） |
| `MenuButtonContext` | 按钮定义（槽位、物品、点击回调） |
| `MenuDynamicEntry` | 动态列表条目 |

---

## 可用界面

| 菜单 ID | 用途 | 触发方式 |
|---------|------|----------|
| `TOWN_HALL` | 城镇管理 | 右键城府核心 |
| `STORAGE` | 仓库资源查看 | `/slg resources` |
| `BUILDING` | 建筑操作 | 右键建筑核心 |
| `RESIDENT` | 居民管理 | `/slg residents` |
| `TECH` | 科技树 | `/slg tech` |
| `PRODUCTION` | 生产总览 | `/slg production` |
| `COMBAT` | 战斗信息 | `/slg combat` |
| `LOGISTICS` | 物流网络 | `/slg logistics` |
| `RPG_LINK` | RPG 联动 | `/slg rpglink` |
