# Gin-Decompiler

IntelliJ IDEA 插件：右键 JAR 文件，一键反编译生成 `-sources.jar`，方便在 IDE 中搜索和查看第三方库源码。

## 功能

- 在项目视图中右键点击任意 `.jar` 文件，选择 **Gin-Decompiler**
- 自动在同目录下生成 `{name}-sources.jar`，包含反编译的 Java 源码
- 支持 IntelliJ IDEA 2023.3 ~ 2026.1.*

## 打包插件

### 前置条件

- JDK 17+
- 本地安装 IntelliJ IDEA（用于提供平台依赖）

### 执行打包命令

```bash
./gradlew buildPlugin
```

> Windows 下如果没有 `gradlew`，可使用 `gradle buildPlugin`

打包完成后，插件 zip 文件生成在：

```
build/distributions/gin-decompiler-plugin-1.0.0.zip
```

## 安装插件

### 方式一：从磁盘安装（推荐本地开发使用）

1. 打开 IntelliJ IDEA
2. 进入 **Settings** → **Plugins**
3. 点击右上角 **⚙ 齿轮图标** → **Install Plugin from Disk...**
4. 选择 `build/distributions/gin-decompiler-plugin-1.0.0.zip`
5. 重启 IDE 即可生效

### 方式二：从 JetBrains Marketplace 安装

发布到 Marketplace 后，可直接在 IDE 的 Plugins 市场搜索 **Gin-Decompiler** 进行安装。

## 使用方式

1. 在 **Project** 视图中，右键点击任意 `.jar` 文件
2. 选择 **Gin-Decompiler**
3. 等待反编译完成，同目录下会生成 `{name}-sources.jar`
4. 将生成的 `-sources.jar` 添加为项目依赖，即可在 IDE 中正常搜索类名和方法名

## 项目结构

```
├── src/main/
│   ├── java/com/decompiler/
│   │   └── DecompilerAction.java      # 插件核心逻辑
│   └── resources/META-INF/
│       ├── plugin.xml                  # 插件描述文件
│       └── pluginIcon.svg              # 插件图标
├── build.gradle                        # Gradle 构建配置
├── gradle.properties                   # Gradle 属性配置
└── settings.gradle                     # Gradle 项目设置
```

## License

MIT
