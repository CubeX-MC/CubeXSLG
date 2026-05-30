# 消息模块 (Message)

## 包路径
`io.github.adlamb.cubex.message` — `MessageService.kt`

---

## 概述

消息模块提供本地化消息服务。所有玩家可见的文本均在 `messages.yml` 中定义，使用 Adventure API 的 MiniMessage 格式进行富文本渲染，支持占位符替换。

---

## MessageService.kt — 消息服务

```
src/main/kotlin/io/github/adlamb/cubex/message/MessageService.kt
```

### 核心方法

| 方法 | 功能 |
|------|------|
| `component(key, vararg args)` | 按消息键获取已解析的 Adventure Component，自动填充占位符 |
| `send(player, key, vararg args)` | 向玩家发送格式化消息 |
| `sendActionBar(player, key, vararg args)` | 向玩家发送 ActionBar 消息 |

### 配置来源

`src/main/resources/messages.yml` — 包含 60+ 条消息模板（当前为中文）：

```yaml
messages:
  town-created: "<green>✔ 城镇 <aqua>{0}</aqua> 创建成功！</green>"
  building-placed: "<green>✔ <aqua>{0}</aqua> 已放置。</green>"
  insufficient-resources: "<red>✘ 资源不足！需要 <yellow>{0}</yellow>。</red>"
```

### 技术要点

- 基于 Adventure 的 `MiniMessage` 解析器
- 使用 Java `MessageFormat` 风格的占位符 `{0}`、`{1}` 等
- 支持 HEX 颜色、渐变、悬浮文本等 MiniMessage 完整语法
- 可通过 `config.yml` 中的 `locale` 字段切换语言（当前仅 `zh-CN`）
