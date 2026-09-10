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
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

public class DecompilerAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null) {
            VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
            if (files != null && files.length > 0) {
                file = files[0];
            }
        }
        boolean visible = file != null && "jar".equalsIgnoreCase(file.getExtension());
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null) {
            VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
            if (files != null && files.length > 0) {
                file = files[0];
            }
        }

        if (file == null) {
            Messages.showWarningDialog("No file selected", "Gin-Decompiler");
            return;
        }
        if (!"jar".equalsIgnoreCase(file.getExtension())) {
            Messages.showWarningDialog("Please select a .jar file", "Gin-Decompiler");
            return;
        }

        Project project = e.getProject();
        String rawPath = file.getPath();
        // Strip "!/" or "!" suffix from virtual JAR paths (e.g. from Maven Libraries)
        final String jarPath;
        if (rawPath.endsWith("!/")) {
            jarPath = rawPath.substring(0, rawPath.length() - 2);
        } else if (rawPath.endsWith("!")) {
            jarPath = rawPath.substring(0, rawPath.length() - 1);
        } else {
            jarPath = rawPath;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Gin is Decompiling...", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setIndeterminate(false);
                    decompileJar(jarPath, indicator);
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            Messages.showErrorDialog(
                                    "Decompilation failed:\n" + ex.getClass().getName() + ": " +
                                            (ex.getMessage() != null ? ex.getMessage() : "null"),
                                    "Gin-Decompiler Error"
                            )
                    );
                }
            }
        });
    }

    private void decompileJar(String jarPath, ProgressIndicator indicator) throws Exception {
        File jarFile = new File(jarPath);
        String name = jarFile.getName();
        String baseName = name.substring(0, name.lastIndexOf('.'));
        File outputJar = new File(jarFile.getParent(), baseName + "-sources.jar");

        Path tempDir = Files.createTempDirectory("decompiler-");
        try {
            // Step 1: Extract .class files from JAR
            indicator.setText("Extracting classes from " + name);
            indicator.setFraction(0.1);
            Path classesDir = tempDir.resolve("classes");
            extractJarClasses(jarFile, classesDir);

            // Step 2: Decompile using Fernflower
            indicator.setText("Decompiling...");
            indicator.setFraction(0.3);

            Map<String, Object> options = new HashMap<>();
            options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
            options.put(IFernflowerPreferences.REMOVE_BRIDGE, "0");
            options.put(IFernflowerPreferences.LOG_LEVEL, "warn");

            Fernflower fernflower = new Fernflower(
                    new FileBytecodeProvider(classesDir),
                    new DirectoryResultSaver(classesDir),
                    options,
                    new DecompilerLogger()
            );

            fernflower.addSource(classesDir.toFile());
            fernflower.decompileContext();

            // Step 3: Pack .java files into sources.jar
            indicator.setText("Creating sources.jar...");
            indicator.setFraction(0.8);
            long javaCount = Files.walk(classesDir)
                    .filter(p -> p.toString().endsWith(".java"))
                    .count();
            packJavaFiles(classesDir, outputJar);

            indicator.setFraction(1.0);
            ApplicationManager.getApplication().invokeLater(() ->
                    Messages.showInfoMessage(
                            "Gin-Decompiler decompiled successfully:\n" + outputJar.getAbsolutePath(),
                            "Gin-Decompiler - Done"
                    )
            );

        } finally {
            deleteRecursively(tempDir.toFile());
        }
    }

    /**
     * Extract all .class files from a JAR into a directory tree.
     */
    private void extractJarClasses(File jarFile, Path destDir) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class") && !entry.isDirectory()) {
                    Path target = destDir.resolve(name);
                    Files.createDirectories(target.getParent());
                    try (InputStream in = jar.getInputStream(entry)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    /**
     * Pack all .java files from a directory tree into a JAR.
     */
    private void packJavaFiles(Path sourceDir, File outputJar) throws IOException {
        Path root = sourceDir.toAbsolutePath();
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputJar))) {
            Files.walk(sourceDir)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            String entryName = root.relativize(p.toAbsolutePath())
                                    .toString()
                                    .replace(File.separatorChar, '/');
                            jos.putNextEntry(new JarEntry(entryName));
                            Files.copy(p, jos);
                            jos.closeEntry();
                        } catch (IOException ignored) {
                        }
                    });
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

    // ==================== Fernflower Support Classes ====================

    /**
     * Provides bytecode (.class file content) from the extracted classes directory.
     */
    private static class FileBytecodeProvider implements IBytecodeProvider {
        private final Path baseDir;

        FileBytecodeProvider(Path baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
            // When internalPath is null, externalPath contains the full path to the class file
            Path classFile = internalPath != null ? baseDir.resolve(internalPath) : Path.of(externalPath);
            return Files.readAllBytes(classFile);
        }
    }

    /**
     * Saves decompiled .java files alongside the original .class files.
     */
    private static class DirectoryResultSaver implements IResultSaver {
        private final Path baseDir;

        DirectoryResultSaver(Path baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public void saveFolder(String path) {
        }

        @Override
        public void copyFile(String source, String path, String entryName) {
        }

        @Override
        public void saveClassFile(String path, String qualifiedName,
                                   String entryName, String content, int[] mapping) {
            try {
                Path javaFile = baseDir.resolve(path).resolve(entryName);
                String javaFileName = javaFile.getFileName().toString();
                int dot = javaFileName.lastIndexOf('.');
                if (dot > 0) {
                    javaFileName = javaFileName.substring(0, dot) + ".java";
                }
                Path target = javaFile.resolveSibling(javaFileName);
                Files.write(target, content.getBytes("UTF-8"));
            } catch (IOException ignored) {
            }
        }

        @Override
        public void createArchive(String path, String archiveName, Manifest manifest) {
        }

        @Override
        public void saveDirEntry(String path, String archiveName, String entryName) {
        }

        @Override
        public void copyEntry(String source, String path, String archiveName, String entry) {
        }

        @Override
        public void saveClassEntry(String path, String archiveName,
                                    String qualifiedName, String entryName, String content) {
        }

        @Override
        public void closeArchive(String path, String archiveName) {
        }
    }

    /**
     * Minimal logger for Fernflower (suppresses most output).
     */
    private static class DecompilerLogger extends IFernflowerLogger {
        @Override
        public void writeMessage(String message, Severity severity) {
        }

        @Override
        public void writeMessage(String message, Severity severity, Throwable t) {
        }
    }
}
