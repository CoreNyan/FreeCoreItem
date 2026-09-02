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
