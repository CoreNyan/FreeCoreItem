# FreeCoreItem

FreeCoreItem 是一个面向 Paper 26.2 的轻量自定义物品插件。每件物品以完整 Bukkit `ItemStack` 模板保存，因此可以保留原版物品组件和其他插件写入的兼容元数据。

主要命令：

- `/fitem editor [id]`
- `/fitem create <id> [material|hand]`
- `/fitem delete <id>`
- `/fitem give <id> [player] [amount]`
- `/fitem set <id> <property> <value>`
- `/fitem lore|enchant|flag|action ...`
- `/fitem attribute <id> add <attribute> <amount> <operation> [slot]`
- `/fitem reload`

物品文件保存在 `plugins/FreeCoreItem/items/<id>.yml`，语言文件为 `plugins/FreeCoreItem/lang/zh_CN.yml`。

## 点击动作与条件

`/fitem action <ID> <right-click|left-click> add <命令>` 可逐条添加命令，例如：

```text
/fitem action wand right-click add [if permission:wand.use] [player] say hello
/fitem action wand right-click add [if placeholder:%freecore_title%=勇者] [op] gamemode creative
/fitem action wand right-click add [if permission:wand.use] [if title:勇者] [message] <green>已触发</green>
```

每条命令开头的 `[if 条件]` 独立生效，多个 `[if ...]` 为“且”；支持 `server:子服标识`、`permission:节点`、`title:文本`、`placeholder:%变量%=值`（后两种需要 PlaceholderAPI）。不满足时只跳过该条命令。原有 `condition-add` 添加的是整个动作的共同前置条件（仍为“且”），旧物品 YAML 不必迁移。命令前缀可选 `[console]`（默认）、`[player]`、`[op]`、`[message]`；`[op]` 临时授予玩家 OP 执行同步命令，并在 `finally` 中恢复原状态。只允许可信管理员编辑带 `[op]` 的动作；不应把它用于延迟执行或让玩家构造命令参数。每次触发的 `actions.max-commands-per-trigger` 限制按命令条目计数，包含未满足条件而跳过的条目。

### 子服条件

每台 Paper 子服在其 **`plugins/FreeCoreItem/config.yml`** 内设置不同的 `server-id`，例如 Survival 使用 `server-id: survival`，Technical 使用 `server-id: technical`。这是本插件独立配置的标识，不是世界名、MCSManager 实例名或 PlaceholderAPI 变量；大小写比较不敏感。不填写 `server-id` 时 `server:` 条件始终不匹配，以防错误地跨服执行。修改配置后需在**部署新插件代码并重启对应子服**后才生效；仅编辑配置不会使正在运行的旧 JAR 识别该条件。

```text
/fitem action wand right-click add [if server:survival] [message] <green>生存服提示</green>
/fitem action wand right-click add [if server:technical] [message] <aqua>技术服提示</aqua>
/fitem action wand right-click condition-add server:survival
```

前两条是逐条命令条件；最后一条是**整个右键动作**的前置条件，设置后技术服的所有右键命令都不会执行。不要把全局条件与希望在另一服执行的命令加到同一动作上。无 `[if ...]` 的命令仍无条件执行（在整组条件已通过时），不是 else 分支。

GUI 中左键点击动作图标追加命令（可以直接输入 `[if ...] [op] ...`），Shift 点击添加整组共同条件，右键清空命令。编辑输入应由插件消费而非在公共聊天广播。

构建：在项目目录运行 `gradle clean build`（需要 JDK 25 和 Paper API 依赖）。构建产物仅供离线检查；生产服部署、更换 JAR 和重启须单独维护许可并通过 MCSManager 进行。
