# Gin-Decompiler

IntelliJ IDEA plugin: Right-click a JAR file to decompile it and generate a `-sources.jar`, making it easy to search and browse third-party library source code in the IDE.

## Features

- Right-click any `.jar` file in the Project view and select **Gin-Decompiler**
- Automatically generates `{name}-sources.jar` in the same directory, containing decompiled Java source code
- Supports IntelliJ IDEA 2023.3 ~ 2026.1.*

## Build Plugin

### Prerequisites

- JDK 17+
- Local IntelliJ IDEA installation (for platform dependencies)

### Build Command

```bash
./gradlew buildPlugin
```

> On Windows, use `gradle buildPlugin` if `gradlew` is not available.

After building, the plugin zip file is generated at:

```
build/distributions/gin-decompiler-plugin-1.0.0.zip
```

## Installation

### Option 1: Install from Disk (Recommended for Local Development)

1. Open IntelliJ IDEA
2. Go to **Settings** → **Plugins**
3. Click the **⚙ gear icon** in the top right → **Install Plugin from Disk...**
4. Select `build/distributions/gin-decompiler-plugin-1.0.0.zip`
5. Restart the IDE to activate the plugin

### Option 2: Install from JetBrains Marketplace

Once published to the Marketplace, you can search for **Gin-Decompiler** in the IDE's Plugins marketplace and install it directly.

## Usage

1. In the **Project** view, right-click any `.jar` file
2. Select **Gin-Decompiler**
3. Wait for decompilation to complete. A `{name}-sources.jar` will be generated in the same directory
4. Add the generated `-sources.jar` as a project dependency to search class names and method names in the IDE

## Project Structure

```
├── src/main/
│   ├── java/com/decompiler/
│   │   └── DecompilerAction.java      # Core plugin logic
│   └── resources/META-INF/
│       ├── plugin.xml                  # Plugin descriptor
│       └── pluginIcon.svg              # Plugin icon
├── build.gradle                        # Gradle build configuration
├── gradle.properties                   # Gradle properties
└── settings.gradle                     # Gradle project settings
```

## License

MIT
