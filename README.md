# Gin-Decompiler

IntelliJ IDEA plugin for decompiling Java JAR files. Right-click any `.jar` file to generate a `-sources.jar` for source browsing, or generate a complete Maven/Gradle project with auto-detected dependencies.

## Features

### Gin-Decompiler (Decompile to sources.jar)

- Right-click any `.jar` file in the Project view and select **Gin-Decompiler**
- Automatically generates `{name}-sources.jar` in the same directory containing decompiled Java source code
- Add the generated `-sources.jar` as a project dependency to search and browse class/method names in the IDE

### Generate Project from JAR

- Right-click any `.jar` file and select **Generate Project from JAR**
- Choose between **Maven** or **Gradle** build system
- Generates a complete, ready-to-open project with:
  - Decompiled Java source code
  - `pom.xml` or `build.gradle` with auto-detected dependencies
  - Correct Java version based on class file analysis
  - Proper package structure detection

### Smart Dependency Resolution

- Extracts dependencies from the JAR's embedded `pom.xml`
- Scans `lib/` directory for sibling JAR dependencies
- Resolves `${}` placeholders and missing versions from embedded JAR filenames
- Scans decompiled source imports to discover additional missing dependencies
- Maps 80+ common library package prefixes to Maven coordinates (Spring, Jackson, MyBatis, etc.)

### Spring Boot Fat JAR Support

- Automatically detects Spring Boot fat JARs (`BOOT-INF/classes/`, `BOOT-INF/lib/`)
- Extracts application classes from `BOOT-INF/classes/`
- Extracts embedded library JARs from `BOOT-INF/lib/`
- Resolves dependencies from both embedded pom.xml and embedded JARs

### Auto-Detection

- **Java version**: Reads class file major version numbers to determine the required Java version
- **Root package**: Scans class files to detect the project's top-level package structure
- **Maven coordinates**: Extracts `groupId`, `artifactId`, `version` from `pom.properties`, `MANIFEST.MF`, or filename parsing

## Compatibility

- IntelliJ IDEA 2023.3 ~ 2026.1.*
- Requires JDK 17+ for building

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
build/distributions/gin-decompiler-plugin-1.1.0.zip
```

## Installation

### Option 1: Install from Disk (Recommended for Local Development)

1. Open IntelliJ IDEA
2. Go to **Settings** → **Plugins**
3. Click the **⚙ gear icon** in the top right → **Install Plugin from Disk...**
4. Select `build/distributions/gin-decompiler-plugin-1.1.0.zip`
5. Restart the IDE to activate the plugin

### Option 2: Install from JetBrains Marketplace

Once published to the Marketplace, you can search for **Gin-Decompiler** in the IDE's Plugins marketplace and install it directly.

## Usage

### Decompile JAR to sources.jar

1. In the **Project** view, right-click any `.jar` file
2. Select **Gin-Decompiler**
3. Wait for decompilation to complete — a `{name}-sources.jar` will be generated in the same directory
4. Add the generated `-sources.jar` as a project dependency to search class names and method names in the IDE

### Generate Project from JAR

1. In the **Project** view, right-click any `.jar` file
2. Select **Generate Project from JAR**
3. Choose **Maven** or **Gradle** as the build system
4. The plugin will:
   - Analyze the JAR (detect Java version, root package, Spring Boot type)
   - Extract and decompile all classes
   - Scan for dependencies (embedded pom.xml, lib directory, import statements)
   - Generate a complete project in `{artifactId}-project/` next to the JAR
5. Open the generated project folder in IntelliJ IDEA

## Project Structure

```
├── src/main/
│   ├── java/com/decompiler/
│   │   ├── DecompilerAction.java          # Decompile JAR to sources.jar
│   │   ├── GenerateProjectAction.java     # Generate Maven/Gradle project from JAR
│   │   ├── DecompilerHelper.java          # Shared decompilation utilities
│   │   ├── ProjectGenerator.java          # Maven/Gradle project file generation
│   │   ├── JarInfoExtractor.java          # JAR metadata & dependency extraction
│   │   └── LibScanner.java               # Lib directory scanning
│   └── resources/META-INF/
│       ├── plugin.xml                     # Plugin descriptor
│       └── pluginIcon.svg                 # Plugin icon
├── build.gradle                           # Gradle build configuration
├── gradle.properties                      # Gradle properties
└── settings.gradle                        # Gradle project settings
```

## Changelog

### 1.1.0
- New: Generate Maven/Gradle project from JAR
- New: Auto-detect Java version from class files
- New: Spring Boot fat JAR support
- New: Dependency resolution from imports and embedded pom.xml
- New: Lib directory scanning for sibling JAR dependencies
- Refactored shared decompilation logic

### 1.0.0
- Initial release
- Decompile JAR files to `-sources.jar`

## License

MIT
