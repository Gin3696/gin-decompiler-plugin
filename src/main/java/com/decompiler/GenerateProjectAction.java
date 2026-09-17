package com.decompiler;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Action to generate a Maven or Gradle project from a JAR file.
 * Decompiles classes, scans lib directory, and generates build files.
 */
public class GenerateProjectAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean visible = file != null && "jar".equalsIgnoreCase(file.getExtension());
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null || !"jar".equalsIgnoreCase(file.getExtension())) {
            return;
        }

        Project project = e.getProject();
        String rawPath = file.getPath();
        final String jarPath;
        if (rawPath.endsWith("!/")) {
            jarPath = rawPath.substring(0, rawPath.length() - 2);
        } else if (rawPath.endsWith("!")) {
            jarPath = rawPath.substring(0, rawPath.length() - 1);
        } else {
            jarPath = rawPath;
        }

        // Ask user to choose build system
        String[] buildTypes = {"Maven", "Gradle"};
        String selected = Messages.showEditableChooseDialog(
                "Select build system for the generated project:",
                "Generate Project from JAR",
                Messages.getQuestionIcon(),
                buildTypes,
                buildTypes[0],
                null
        );

        if (selected == null) return;
        final boolean useGradle = "Gradle".equals(selected);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating Project...", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setIndeterminate(false);
                    generateProject(jarPath, useGradle, indicator);
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            Messages.showErrorDialog(
                                    "Project generation failed:\n" + ex.getClass().getName() + ": " +
                                            (ex.getMessage() != null ? ex.getMessage() : "null"),
                                    "Generate Project Error"
                            )
                    );
                }
            }
        });
    }

    private void generateProject(String jarPath, boolean useGradle, ProgressIndicator indicator) throws Exception {
        File jarFile = new File(jarPath);
        String jarName = jarFile.getName();
        String baseName = jarName.substring(0, jarName.lastIndexOf('.'));
        File parentDir = jarFile.getParentFile();

        // Step 1: Extract JAR info
        indicator.setText("Analyzing JAR: " + jarName);
        indicator.setFraction(0.05);
        JarInfoExtractor.JarInfo jarInfo = JarInfoExtractor.extract(jarFile);

        // Step 2: Detect root package & Java version
        indicator.setText("Detecting package structure...");
        indicator.setFraction(0.10);
        String rootPackage = JarInfoExtractor.detectRootPackage(jarFile);

        indicator.setText("Detecting Java version...");
        indicator.setFraction(0.15);
        int javaVersion = JarInfoExtractor.detectJavaVersion(jarFile);

        // Step 3: Detect Spring Boot and extract classes + embedded libs
        indicator.setText("Extracting classes...");
        indicator.setFraction(0.20);
        Path tempDir = Files.createTempDirectory("gen-project-");
        Path decompiledDir = tempDir.resolve("decompiled");
        Path embeddedLibDir = tempDir.resolve("embedded-lib");
        Files.createDirectories(decompiledDir);
        Files.createDirectories(embeddedLibDir);

        try {
            // Extract classes (handles BOOT-INF/classes/ for Spring Boot JARs)
            DecompilerHelper.ExtractionResult extraction =
                    DecompilerHelper.extractJarClasses(jarFile, decompiledDir, embeddedLibDir);

            boolean isSpringBoot = extraction.isSpringBootJar();
            indicator.setText("Extracted " + extraction.getClassCount() + " classes"
                    + (isSpringBoot ? " (Spring Boot JAR)" : "") + "...");

            // Step 4: Collect dependencies
            indicator.setText("Scanning dependencies...");
            indicator.setFraction(0.30);
            List<LibScanner.DependencyInfo> dependencies = new ArrayList<>();
            List<JarInfoExtractor.PomDependency> pomDependencies = new ArrayList<>();

            if (isSpringBoot) {
                // Spring Boot: use main JAR's pom.xml dependencies (direct deps only)
                pomDependencies = JarInfoExtractor.extractPomDependencies(jarFile);
                // DO NOT scanJarFiles or extractPomDependencies from embedded JARs
                // (that would create 375 broken entries with wrong groupIds)
            } else {
                // Regular JAR: scan lib directory next to the JAR
                dependencies = LibScanner.scanLibDirectory(jarFile);
                // Extract pom dependencies from the main JAR
                pomDependencies = JarInfoExtractor.extractPomDependencies(jarFile);
            }

            // Step 5: Decompile with Fernflower
            indicator.setText("Decompiling " + extraction.getClassCount() + " classes...");
            indicator.setFraction(0.40);
            Map<String, Object> options = new HashMap<>();
            options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
            options.put(IFernflowerPreferences.REMOVE_BRIDGE, "0");
            options.put(IFernflowerPreferences.LOG_LEVEL, "warn");

            Fernflower fernflower = new Fernflower(
                    new DecompilerHelper.FileBytecodeProvider(decompiledDir),
                    new DecompilerHelper.DirectoryResultSaver(decompiledDir),
                    options,
                    new DecompilerHelper.DecompilerLogger()
            );
            fernflower.addSource(decompiledDir.toFile());
            fernflower.decompileContext();

            // Step 5.5: Scan imports to find any missing dependencies
            indicator.setText("Resolving dependencies from imports...");
            indicator.setFraction(0.65);

            // Build a version map from embedded JAR filenames (e.g. "spring-web-5.3.20.jar" → version "5.3.20")
            Map<String, String> versionFromJar = new HashMap<>();
            for (File embeddedJar : extraction.getEmbeddedLibJars()) {
                String name = embeddedJar.getName();
                if (name.toLowerCase().endsWith(".jar")) {
                    name = name.substring(0, name.length() - 4);
                }
                // Parse artifact-version pattern
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.+?)[-_](\\d+.*)$").matcher(name);
                if (m.matches()) {
                    versionFromJar.put(m.group(1).toLowerCase(), m.group(2));
                }
            }

            // Resolve ${} placeholders and empty versions in pom dependencies using embedded JAR versions
            List<JarInfoExtractor.PomDependency> resolvedPomDeps = new ArrayList<>();
            for (JarInfoExtractor.PomDependency d : pomDependencies) {
                String version = d.getVersion();
                if (version.contains("${") || version.isEmpty()) {
                    // Try to resolve from embedded JAR filenames
                    String resolved = versionFromJar.getOrDefault(d.getArtifactId().toLowerCase(), "");
                    if (resolved.isEmpty()) {
                        // Try prefix match
                        for (Map.Entry<String, String> e : versionFromJar.entrySet()) {
                            if (e.getKey().startsWith(d.getArtifactId().toLowerCase() + "-")
                                    || e.getKey().startsWith(d.getArtifactId().toLowerCase() + "_")) {
                                resolved = e.getValue();
                                break;
                            }
                        }
                    }
                    if (resolved.isEmpty()) resolved = "RELEASE";
                    resolvedPomDeps.add(new JarInfoExtractor.PomDependency(
                            d.getGroupId(), d.getArtifactId(), resolved, d.getScope()));
                } else {
                    resolvedPomDeps.add(d);
                }
            }
            pomDependencies = resolvedPomDeps;

            // Resolve dependencies from imports (with versions from embedded JAR map)
            List<JarInfoExtractor.PomDependency> importDeps =
                    ProjectGenerator.resolveDependenciesFromImports(decompiledDir, rootPackage, versionFromJar);

            // Merge import-derived deps with existing deps, avoiding duplicates
            Set<String> existingGA = new HashSet<>();
            for (LibScanner.DependencyInfo d : dependencies) {
                if (d.hasCoordinates()) {
                    existingGA.add(d.getGroupId() + ":" + d.getArtifactId());
                }
            }
            for (JarInfoExtractor.PomDependency d : pomDependencies) {
                existingGA.add(d.getGroupId() + ":" + d.getArtifactId());
            }
            for (JarInfoExtractor.PomDependency d : importDeps) {
                String ga = d.getGroupId() + ":" + d.getArtifactId();
                if (!existingGA.contains(ga)) {
                    pomDependencies.add(d);
                    existingGA.add(ga);
                }
            }

            // Step 6: Generate project
            indicator.setText("Generating project structure...");
            indicator.setFraction(0.75);

            ProjectGenerator.ProjectConfig config = new ProjectGenerator.ProjectConfig();
            config.setGroupId(jarInfo.hasGroupId() ? jarInfo.getGroupId() : "com.decompiled");
            config.setArtifactId(jarInfo.hasArtifactId() ? jarInfo.getArtifactId() : baseName);
            config.setVersion(jarInfo.hasVersion() ? jarInfo.getVersion() : "1.0.0");
            config.setJavaVersion(javaVersion);
            config.setRootPackage(rootPackage);
            config.setDependencies(dependencies);
            config.setPomDependencies(pomDependencies);
            config.setHasLibDir(false); // No need for lib dir - all deps go to pom
            config.setSpringBoot(isSpringBoot);

            // Create project directory
            String projectDirName = config.getArtifactId() + "-project";
            File tempProjectDir = new File(parentDir, projectDirName);
            if (tempProjectDir.exists()) {
                projectDirName = config.getArtifactId() + "-project-" + System.currentTimeMillis();
                tempProjectDir = new File(parentDir, projectDirName);
            }
            tempProjectDir.mkdirs();
            final File projectDir = tempProjectDir;

            // Generate project (only .java files are copied, no .class files)
            if (useGradle) {
                ProjectGenerator.generateGradleProject(projectDir, config, decompiledDir);
            } else {
                ProjectGenerator.generateMavenProject(projectDir, config, decompiledDir);
            }

            indicator.setFraction(1.0);

            // Show success message
            String buildSystem = useGradle ? "Gradle" : "Maven";
            final String projectPath = projectDir.getAbsolutePath();
            final int depCount = dependencies.size();
            final int pomDepCount = pomDependencies.size();
            final boolean springBoot = isSpringBoot;
            ApplicationManager.getApplication().invokeLater(() -> {
                String msg = "Project generated successfully!\n\n"
                        + "Location: " + projectPath + "\n"
                        + "Build system: " + buildSystem + "\n"
                        + "Java version: " + javaVersion + "\n"
                        + "JAR type: " + (springBoot ? "Spring Boot Fat JAR" : "Regular JAR") + "\n"
                        + "Classes decompiled: " + extraction.getClassCount() + "\n"
                        + "Dependencies: " + depCount + "\n"
                        + "Pom dependencies: " + pomDepCount;
                Messages.showInfoMessage(msg, "Generate Project - Done");
                openProjectFolder(projectDir);
            });

        } finally {
            deleteRecursively(tempDir.toFile());
        }
    }

    private void deleteRecursively(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        dir.delete();
    }

    /**
     * Open the project folder in the system file explorer.
     */
    private void openProjectFolder(File dir) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(dir);
            }
        } catch (Exception e) {
            // Silently ignore if the folder cannot be opened
        }
    }
}
