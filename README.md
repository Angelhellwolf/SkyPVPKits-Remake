# SkyPVPKits - Minecraft 1.8 礼包插件

## 📖 插件介绍

SkyPVPKits 是一个专为 Minecraft 1.8 设计的专业礼包系统插件，支持多种礼包购买方式和完整的用户界面。

**作者：** AngelHell  
**版本：** 1.0.0  
**支持版本：** Minecraft 1.8.x  

## ✨ 功能特色

### 🎁 礼包系统
- **7种不同等级的礼包**：天空、火星、地球、月球、太阳、挖矿、银河
- **等级递进系统**：必须按顺序购买（1→2→3→...）
- **双重购买模式**：永久购买 + 月付订阅
- **每日奖励领取**：拥有礼包的玩家可以每天领取奖励

### 💰 支付系统
- **双货币支持**：点券 + 硬币
- **智能定价**：月付价格自动计算（永久价格÷3，最低30）
- **灵活折扣**：每个礼包可单独设置折扣
- **安全期机制**：服务器困难阶段可限制某些购买方式

### 🎨 用户界面
- **精美GUI菜单**：每个礼包都有独立的完整菜单
- **直观操作**：左键永久购买，右键月付购买
- **实时状态**：显示拥有等级、剩余天数、今日领取状态
- **智能提示**：购买条件、价格信息、错误提示

### 🛠️ 管理功能  
- **完整命令系统**：支持控制台和玩家操作
- **数据库存储**：使用 SQL Server 本地数据库
- **配置热重载**：无需重启服务器即可更新设置
- **购买历史记录**：完整的交易日志

## 📦 礼包价格表

| 礼包名称 | 等级1 | 等级2 | 等级3 | 等级4 | 等级5 | 总价 | 支持月付 | 支持点券 |
|---------|-------|-------|-------|-------|-------|------|----------|----------|
| 🌟 天空  | 10    | 15    | 20    | 20    | -     | 65   | ✅       | 🆓 免费  |
| 🔥 火星  | 80    | 80    | 120   | 160   | -     | 440  | ✅       | ✅       |
| 🌍 地球  | 240   | 100   | 200   | 250   | -     | 790  | ✅       | ✅       |
| 🌙 月球  | 400   | 200   | 250   | 300   | -     | 1150 | ✅       | ✅       |
| ☀️ 太阳  | 220   | 280   | 450   | 500   | 1005  | 2455 | ✅       | ✅       |
| ⛏️ 挖矿  | 400   | 100   | 100   | 150   | -     | 750  | ✅       | ✅       |
| 🌌 银河  | 550   | 750   | -     | -     | -     | 1300 | ❌       | ❌*      |

> *银河礼包在服务器安全期不支持点券购买，需联系管理员

## 🚀 安装说明

### 📋 前置依赖
- **PlaceholderAPI** - 用于变量解析
- **Vault** - 经济系统支持
- **SQL Server** - 数据库（推荐 LocalDB）

### 📥 安装步骤
1. 将插件 jar 文件放入 `plugins` 文件夹
2. 确保安装了所有前置插件
3. 配置 SQL Server 数据库连接
4. 重启服务器
5. 根据需要修改 `config.yml` 配置文件

## ⚙️ 配置说明

### 🔧 主要配置项

```yaml
# 服务器状态
server:
  safe_period: false          # 是否度过安全期
  contact_qq: "2076049239"    # 联系QQ

# 数据库配置
database:
  host: "localhost"
  port: 1433
  database: "skypvpkits"
  username: "sa"
  password: "password"

# 礼包折扣设置
kits:
  sky:
    discount: 0.0             # 天空礼包免费（0折）
  mars:
    discount: 1.0             # 火星礼包原价（10折）
  # ... 其他礼包配置
```

### 限时全服免费体验

2026 年国庆活动使用独立配置 `promotions.national_day_2026`，北京时间 10 月 1 日 00:00 开始、10 月 8 日 00:00 结束（含 10 月 7 日全天）。默认免费等级为月球 3、地球 4、矿工 3。根据真实且未过期的权益决定奖励：月球 3 / 矿工 3 已拥有则升级至 4；该系列 4 已拥有则解锁银河 3；银河 3 也已拥有则每人整个活动共赠送一次完美淬炼宝石 ×5。活动虚拟解锁不会覆盖永久或月付记录，活动结束后自动失效；现有付费礼包仍可按原规则领取。

活动奖励入口会显示玩家实际可领的升级结果。宝石会在符合条件的玩家在线时自动登记，使用 SkyForge 的唯一免费订单和待领取奖励机制；背包满、离线、重复打开不同礼包和重启都不会生成第二份。普通活动礼包保持每日领取限制。

`promotions.all_kits_free` 可按绝对时间开启全服体验。活动只虚拟解锁未被 `excluded_kits` 排除礼包的最高等级，领取记录存入独立的 `promotion_claims` 表；它不会新增、覆盖或延长 `player_kits` 中的永久/月付权益，也不会写入购买流水。活动发出的物品在活动截止时失效。活动期间玩家仍可购买永久礼包，活动结束后自动恢复为其真实充值权益。

```yaml
promotions:
  all_kits_free:
    enabled: true
    id: "all-kits-free-2026-08-31"
    timezone: "Asia/Hong_Kong"
    starts_at: "2026-08-31T23:50:00"
    ends_at: "2026-09-07T23:50:00"
    excluded_kits: ["sun", "galaxy"]
```

## 🎮 使用指南

### 📱 玩家命令
```bash
/skypvpkits                          # 打开礼包主菜单
/skypvpkits menu [礼包名]             # 打开指定礼包菜单
/skypvpkits buy <礼包> <等级> [永久|月付] # 购买礼包
/skypvpkits claim <礼包> <等级>       # 领取今日奖励
/skypvpkits info [玩家名]             # 查看礼包信息
```

### 🔨 管理员命令
```bash
/skypvpkits reload                   # 重载配置文件
/skypvpkits give <玩家> <礼包> <等级> [类型] # 给予玩家礼包
/openkits <玩家> [礼包类型]            # 为玩家打开礼包菜单
```

### 🖱️ GUI操作
- **主菜单**：
  - 左键礼包图标 → 查看详情
  - 右键礼包图标 → 快速购买下一等级
- **礼包详情菜单**：
  - 左键等级图标 → 永久购买/领取奖励
  - 右键等级图标 → 月付购买
  - 绿色方块 → 已拥有的等级
  - 红色方块 → 未拥有的等级

## 🔗 外部集成

### 💎 点券系统
- 变量：`%remiaftvalue_Points%`
- 扣除命令：`value take <玩家> Points <数量>`
- 给予命令：`value give <玩家> Points <数量>`

### 💰 经济系统
- 变量：`%vault_eco_balance_fixed%`
- 扣除命令：`eco take <玩家> <数量>`
- 通过 Vault API 自动处理

## 📊 数据库结构

### 📋 玩家礼包表 (player_kits)
```sql
CREATE TABLE player_kits (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    player_uuid NVARCHAR(36) NOT NULL,
    player_name NVARCHAR(16) NOT NULL,
    kit_type NVARCHAR(20) NOT NULL,
    kit_level INT NOT NULL,
    is_permanent BIT NOT NULL DEFAULT 1,
    purchase_date DATETIME2 NOT NULL DEFAULT GETDATE(),
    expire_date DATETIME2 NULL,
    last_claim_date DATE NULL
);
```

### 📈 购买历史表 (purchase_history)
```sql
CREATE TABLE purchase_history (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    player_uuid NVARCHAR(36) NOT NULL,
    player_name NVARCHAR(16) NOT NULL,
    kit_type NVARCHAR(20) NOT NULL,
    kit_level INT NOT NULL,
    purchase_type NVARCHAR(10) NOT NULL,
    payment_method NVARCHAR(10) NOT NULL,
    amount INT NOT NULL,
    purchase_date DATETIME2 NOT NULL DEFAULT GETDATE()
);
```

## 🛡️ 权限节点

```yaml
skypvpkits.admin     # 管理员权限（默认：OP）
skypvpkits.use       # 使用礼包权限（默认：所有玩家）
skypvpkits.reload    # 重载配置权限（默认：OP）
```

## 🔧 开发说明

### 📁 项目结构
```
src/main/java/com/remiaft/skypvpkits/
├── SkyPVPKits.java              # 主插件类
├── commands/                    # 命令处理
│   ├── KitsCommand.java         # 主命令
│   └── OpenKitsCommand.java     # 打开菜单命令
├── data/                        # 数据模型
│   └── PlayerKitData.java       # 玩家礼包数据
├── database/                    # 数据库管理
│   └── DatabaseManager.java    # 数据库操作
├── listeners/                   # 事件监听
│   └── MenuListener.java        # 菜单点击监听
└── managers/                    # 功能管理
    ├── ConfigManager.java       # 配置管理
    ├── KitManager.java          # 礼包逻辑
    └── MenuManager.java         # GUI管理
```

### 🔌 API 示例
```java
// 获取插件实例
SkyPVPKits plugin = SkyPVPKits.getInstance();

// 检查玩家是否拥有礼包
plugin.getKitManager().hasKitLevel(player, "sky", 1)
    .thenAccept(hasKit -> {
        if (hasKit) {
            // 玩家拥有天空礼包等级1
        }
    });

// 购买礼包
plugin.getKitManager().purchaseKitPermanent(player, "mars", 2)
    .thenAccept(result -> {
        if (result.isSuccess()) {
            // 购买成功
        }
    });
```

## AngelItem 奖励提供器

`config.yml` 的 `item-provider` 默认为 `EZITEM`，保持已有服务器行为。独立测试服可显式设置为 `ANGELITEM`，依赖已加载的 AngelKits 和 AngelItem 版本化目录；商城、NPC 命令、购买资格、价格、月付与每日限领保持不变。此模式不会在缺少新版定义时回退发旧物品。

七个普通系列必须在 AngelKits 目录中具有相同系列和等级。启用的技能系列还必须在 SkyPVPItem 的 `item_sources` 中配置固定的 AngelItem ID、revision 和 fingerprint，继续使用原 grant 和技能。`kits.<系列>.enabled: false` 同样适用于内置技能系列。启动时验证全部启用的引用，失败则关闭商城并输出 `KIT-PROVIDER-01`。

普通系列先检查整套背包容量、占用当日记录，再在主线程按当前背包发放；可确认的写入回滚会条件恢复领取资格。已有物品、购买和领取历史不做批量迁移，进程崩溃仍需管理员核对。Skill/grant 系列保留原发放顺序。活动领取使用活动绝对到期时间，不将免费活动写成付费权益。

## ❓ 常见问题

### Q: 数据库连接失败怎么办？
A: 请检查 SQL Server 服务是否启动，以及配置文件中的连接信息是否正确。

### Q: 点券扣除不生效？
A: 请确认已安装对应的点券插件，并且 PlaceholderAPI 能正确解析 `%remiaftvalue_Points%` 变量。

### Q: 如何修改礼包价格？
A: 编辑 `config.yml` 文件中的 `permanent_prices` 配置项，然后使用 `/skypvpkits reload` 重载配置。

### Q: 月付礼包到期后会自动删除吗？
A: 月付礼包到期后不会自动删除，但玩家无法领取奖励，需要重新购买。

## 📞 技术支持

如有问题请联系：
- **QQ:** 2076049239
- **作者:** AngelHell

## 📜 许可证

本插件遵循 MIT 许可证，详情请查看 LICENSE 文件。

---

**感谢使用 SkyPVPKits！** 🎉
