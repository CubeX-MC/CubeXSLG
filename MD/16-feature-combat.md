# 战斗模块 (Combat)

## 包路径
`io.github.adlamb.cubex.feature.combat` — `CombatModule.kt`

---

## 概述

战斗模块处理建筑防御与耐久度系统。建筑受到方块破坏和爆炸时会产生损伤，核心方块受到特殊保护。玩家可以通过战斗 GUI 查看建筑状态。

---

## CombatModule.kt

### 命令

| 命令 | 权限 | 功能 |
|------|------|------|
| `/slg combat` | 无 | 打开战斗信息 GUI，显示建筑耐久度、防御状态等 |

### 事件监听

| 事件 | 处理逻辑 |
|------|----------|
| `BlockBreakEvent` | 检测破坏的是否为建筑方块 → 调用 `GameplayFacade.hitBuildingBlock()` 造成伤害 |
| `EntityExplodeEvent` | 检测爆炸是否涉及建筑 → 调用 `applyExplosionDamageToBuilding()` |
| `BlockExplodeEvent` | 同上，处理非实体爆炸源 |

### 功能细节

- **方块伤害**: 破坏建筑方块会降低建筑整体耐久度
- **爆炸伤害**: 爆炸对建筑产生范围伤害
- **核心保护**: 建筑核心桶 (Barrel) 不能被破坏
- **耐久度归零**: 耐久度归零时建筑被视为摧毁
- **修复系统**: 玩家可通过 `/slg repair` 和资源修复建筑
- **战斗冷却**: `CombatStateTable` 记录每个建筑的伤害冷却时间，防止短时间过量伤害
