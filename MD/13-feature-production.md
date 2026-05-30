# 生产模块 (Production)

## 包路径
`io.github.adlamb.cubex.feature.production` — `ProductionModule.kt`

---

## 概述

生产模块提供生产系统的总览界面。显示城镇的资源余额和建筑生产状态。实际的生产计算逻辑在 `GameplayFacade.tickWorld()` 中实现。

---

## ProductionModule.kt

### 命令

| 命令 | 权限 | 功能 |
|------|------|------|
| `/slg production` | 无 | 打开生产总览 GUI，显示当前资源余额和建筑生产/消耗情况 |

### 事件监听

| 事件 | 处理逻辑 |
|------|----------|
| 无 | 纯信息展示模块 |

### 功能细节

- **生产总览 GUI**: 展示城镇所有资源和建筑的生产/消耗明细
- **每秒产能**: 显示每种资源的每秒净产量
- **建筑贡献**: 列出每个建筑的生产贡献和消耗需求

---

## 生产系统原理

详见 [游戏引擎模块](07-gameplay.md) 中的 `tickWorld()` 方法描述。
