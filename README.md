<div align="center">
  <h1>CubeXSLG 🏰</h1>
  <p><strong>Minecraft Paper & Folia 1.21.8 策略模拟经营（SLG）插件</strong></p>
  <p>
    <img src="https://img.shields.io/badge/Minecraft-1.21.8-blue?style=flat-square" alt="Minecraft 1.21.8">
    <img src="https://img.shields.io/badge/Kotlin-2.3-purple?style=flat-square" alt="Kotlin 2.3">
    <img src="https://img.shields.io/badge/Java-21-orange?style=flat-square" alt="Java 21">
    <img src="https://img.shields.io/badge/Folia--supported-yellow?style=flat-square" alt="Folia Supported">
  </p>
</div>

---

## 概述

CubeXSLG 将 **城市建造经营** 玩法带入 Minecraft。创建城镇、管理虚拟仓库、建造升级建筑、研究科技、招募居民、抵御入侵 —— 全部在原版 Minecraft 世界中实现。

> **开发状态:** 活跃开发中，核心系统已就绪。

---

## 功能特性

### 🏘️ 城镇系统

使用 `/slg create <名称>` 创建城镇，自动成为市长。通过城府 GUI 全方位管理：

- **城府管理界面** — 包含建筑、仓储、科技、居民、设置五个标签页
- **城府升级** — 最高 5 级，提升领地范围、建筑上限、解锁新科技
- **城镇边界** — 粒子效果可视化边界（绿色 = 可建造，红色 = 受限）

### 📦 虚拟仓库

无需箱子、木桶等实体容器，所有资源以数字形式存储在数据库中：

| 分类 | 资源 |
|------|------|
| 基础资源 | 木材、石头、矿石、粮食 |
| 加工资源 | 木板、石材、金属锭 |
| 特殊资源 | 科技点、居民口粮 |

- 建筑产出自动存入仓库
- 建造/研究自动从仓库扣除资源
- 实时统计、搜索筛选、变动记录
- 每秒净产量概览

### 🏗️ 建筑系统

使用 `/slg wand <建筑>` 获取建筑工具，右键放置：

| 建筑 | 功能 |
|------|------|
| 矿场 | 产出矿石 |
| 农场 | 产出粮食 |
| 伐木场 | 产出木材 |
| 采石场 | 产出石头 |
| 木材加工厂 | 木材 → 木板 |
| 石材加工厂 | 石头 → 石材 |
| 炼金厂 | 矿石 → 金属锭 |
| 兵营 | 训练士兵 |
| 哨塔 | 自动防御 |
| 学院 | 培养居民 |
| 仓库 | 扩展虚拟仓储 |

- **建造预览** — 绿色/红色粒子指示可建造区域
- **升级** — 提升生产效率
- **修复 / 移动 / 删除** — 灵活管理

### 👥 居民系统

在城府招募居民，分配工作岗位：

- **属性** — 力量、敏捷、智力、耐力、管理
- **AI 行为** — 居民在城镇中活动，早出晚归
- **学院培养** — 消耗资源永久提升属性
- **岗位分配** — 指派到对应建筑提升产量

### 🔬 科技树

五大分支，多层解锁：

| 分支 | 侧重 |
|------|------|
| 生产 | 资源采集与加工 |
| 军事 | 防御与战斗 |
| 居民 | 人口与培养 |
| 物流 | 运输与仓储 |
| 城镇 | 城镇发展 |

在城府科技界面研究，每项科技有前置条件和资源消耗。

### ⚔️ 战斗系统

- **建筑生命值** — 受击实时更新，低于 50% 坍塌
- **哨塔防御** — 自动检测敌人，召唤守卫
- **兵营训练** — 消耗时间与资源训练士兵

### 🚂 物流系统

- **铁路运输** — 矿车自动在建筑间运输货物
- **自动装卸** — 卸货站与虚拟仓库自动对接
- **智能导航** — 岔路自动选择方向

### 🔗 RPG 联动（规划中）

- 双世界传送，数据互通
- RPG BOSS 掉落科技残卷
- SLG 科技解锁 RPG 新区域

---

## 命令

| 命令 | 说明 |
|------|------|
| `/slg create <名称>` | 创建城镇 |
| `/slg wand <建筑>` | 获取建筑工具 |
| `/slg resources` | 查看主要资源数量 |
| `/slg resources stats` | 资源统计摘要 |
| `/slg resources history` | 资源变化历史 |
| `/slg repair` | 修复瞄准的建筑 |
| `/slg confirm` | 确认删除/移动 |
| `/slg residents` | 查看居民列表 |
| `/slg recruit` | 招募新居民 |
| `/slg help` | 查看帮助 |

---

## 架构

CubeXSLG 采用 **模块化、服务导向** 架构：

```
io.github.adlamb.cubex
├── bootstrap/        # 插件生命周期 & 依赖注入
├── feature/          # 游戏功能模块
│   ├── town/         # 城镇管理
│   ├── building/     # 建筑放置 & 管理
│   ├── resource/     # 虚拟仓库 & 经济
│   ├── production/   # 生产链
│   ├── tech/         # 科技树
│   ├── resident/     # 居民 AI & 管理
│   ├── combat/       # 战斗 & 建筑生命值
│   ├── logistics/    # 铁路 & 运输网络
│   ├── rpglink/      # 跨世界 RPG 联动
│   └── admin/        # 管理工具
├── gameplay/         # 核心引擎 & 外观层
├── menu/             # 自定义物品栏 GUI 框架
├── database/         # 持久化 (Exposed ORM + HikariCP)
├── command/          # 命令注册 & 补全
├── config/           # YAML 配置加载
├── message/          # 本地化 (MiniMessage)
├── registry/         # 游戏定义注册 (YAML)
└── platform/         # Paper/Folia 抽象层
```

### 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Kotlin 2.3 |
| 运行环境 | Java 21 |
| 平台 | Paper / Folia 1.21.8 |
| ORM | Exposed + HikariCP |
| 数据库 | H2（本地）/ MariaDB（生产） |
| 文本渲染 | Adventure MiniMessage |
| 构建工具 | Gradle (Kotlin DSL) + shadowJar |
| 建筑蓝图 | WorldEdit（软依赖） |

---

## 快速开始

### 前置要求

- Java 21+
- Paper 1.21.8 服务器

### 安装

1. 从 [Releases](https://github.com/AdLamb/CubeXSLG/releases) 下载最新 JAR
2. 放入 `plugins/` 目录
3. 重启服务器
4. 编辑 `plugins/CubeXSLG/config.yml` 和 `database.yml`

### 自行构建

```bash
git clone https://github.com/AdLamb/CubeXSLG.git
cd CubeXSLG
./gradlew shadowJar
```

构建产物位于 `build/libs/CubeXSLG-<version>-all.jar`。

### 本地开发

```bash
./gradlew runServer
```

---

## 配置

游戏内容通过 YAML **数据驱动**：

- `gameplay/buildings.yml` — 建筑类型、消耗、产量
- `gameplay/resources.yml` — 资源定义与分类
- `gameplay/tech.yml` — 科技树结构与消耗
- `gameplay/town.yml` — 城镇配置与等级

玩家可见文本集中在 `messages.yml`，便于本地化。

---

## 开发指南

### 项目约定

- 每个功能子系统是独立 `FeatureModule`，置于 `feature/` 包下
- 使用 `SchedulerFacade` 进行任务调度（兼容 Folia）
- 新增功能需实现 `FeatureModule` 接口
- 所有玩家可见文本写入 `messages.yml`

### 代码风格

- 4 空格缩进，类 `PascalCase`，成员 `camelCase`
- 显式导入，禁用通配符
- YAML 键名使用 kebab-case

---

## 路线图

- [x] 城镇创建与管理
- [x] 虚拟仓库及完整统计
- [x] 建筑放置、升级、修复
- [x] 科技树框架
- [x] 居民招募与属性
- [ ] 居民 AI（寻路、日常行为）
- [ ] 战斗系统（哨塔、兵营）
- [ ] 物流系统（铁路、矿车、导航）
- [ ] RPG 世界联动
- [ ] 多语言支持
- [ ] 玩家交易与联盟

---

<div align="center">
  <sub>Made by AdLamb</sub>
</div>
