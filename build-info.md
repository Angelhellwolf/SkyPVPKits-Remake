# SkyPVPKits 构建信息

## 🔨 构建要求

### 环境要求
- **Java**: JDK 8 或更高版本
- **Maven**: 3.6.0 或更高版本
- **Minecraft**: 1.8.x 版本

### 依赖检查
在构建之前，请确保以下依赖可用：
1. **Spigot API 1.8.8** - 从 SpigotMC 仓库获取
2. **PlaceholderAPI** - 变量解析支持
3. **Vault API** - 经济系统集成
4. **SQL Server JDBC 驱动** - 数据库连接

## 📦 构建步骤

### 1. 克隆/下载项目
```bash
# 如果是Git仓库
git clone <repository-url>
cd SkyPVPKits

# 或者直接下载并解压源代码
```

### 2. 编译插件
```bash
mvn clean compile
```

### 3. 运行测试（可选）
```bash
mvn test
```

### 4. 打包插件
```bash
mvn package
```

### 5. 构建成功
编译完成后，在 `target` 目录中找到：
- `SkyPVPKits-1.0.0.jar` - 最终的插件文件

## 🚀 部署说明

### 服务器部署
1. 停止 Minecraft 服务器
2. 将 `SkyPVPKits-1.0.0.jar` 复制到服务器的 `plugins` 文件夹
3. 确保安装了必要的前置插件：
   - PlaceholderAPI
   - Vault
   - 任意经济插件（如 EssentialsX）
4. 配置数据库连接（见下文）
5. 启动服务器

### 数据库配置
在 `plugins/SkyPVPKits/config.yml` 中配置数据库：

```yaml
database:
  host: "localhost"
  port: 1433
  database: "skypvpkits"
  username: "sa"
  password: "your_password"
```

### SQL Server 设置
1. 安装 SQL Server 或 SQL Server Express
2. 创建数据库 `skypvpkits`
3. 确保用户有足够权限创建表
4. 插件会自动创建所需表结构

## 🐛 故障排除

### 常见问题

#### 1. 编译失败 - 找不到依赖
**解决方案：**
```bash
# 清理Maven缓存
mvn dependency:purge-local-repository

# 重新下载依赖
mvn dependency:resolve
```

#### 2. Spigot API 无法下载
**解决方案：**
手动构建 Spigot：
```bash
# 下载 BuildTools
wget https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar

# 构建 Spigot 1.8.8
java -jar BuildTools.jar --rev 1.8.8

# 安装到本地Maven仓库
mvn install:install-file -Dfile=spigot-1.8.8-R0.1-SNAPSHOT.jar \
  -DgroupId=org.spigotmc -DartifactId=spigot-api \
  -Dversion=1.8.8-R0.1-SNAPSHOT -Dpackaging=jar
```

#### 3. 数据库连接失败
**检查清单：**
- [ ] SQL Server 服务是否运行
- [ ] 防火墙是否允许 1433 端口
- [ ] 用户名密码是否正确
- [ ] 数据库是否存在

#### 4. 插件加载失败
**可能原因：**
- 缺少前置插件
- Java 版本不兼容
- 配置文件格式错误

## 📊 性能优化

### 生产环境建议
1. **JVM 参数优化：**
   ```bash
   -Xms2G -Xmx4G -XX:+UseG1GC -XX:G1HeapRegionSize=4m
   ```

2. **数据库连接池：**
   ```yaml
   database:
     max_connections: 10      # 调整连接池大小
     connection_timeout: 30   # 连接超时时间
   ```

3. **缓存设置：**
   插件已内置玩家数据缓存，无需额外配置

## 🔧 开发环境

### IDE 配置
推荐使用 **IntelliJ IDEA** 或 **Eclipse**：

1. 导入 Maven 项目
2. 设置 JDK 为 Java 8
3. 配置代码风格（可选）
4. 安装 Maven 插件

### 调试设置
```bash
# 使用调试模式启动服务器
java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 \
  -jar spigot-1.8.8.jar

# 在IDE中连接到端口5005进行调试
```

## 📋 版本信息

- **当前版本**: 1.0.0
- **最后更新**: 2024年
- **作者**: AngelHell
- **兼容性**: Minecraft 1.8.x

## 📞 技术支持

如在构建过程中遇到问题，请联系：
- **QQ**: 2076049239
- **作者**: AngelHell

---

**祝您构建成功！** 🎉 