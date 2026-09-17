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
            DecompilerHelper.extractJarClasses(jarFile, classesDir);

            // Step 2: Decompile using Fernflower
            indicator.setText("Decompiling...");
            indicator.setFraction(0.3);

            Map<String, Object> options = new HashMap<>();
            options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
            options.put(IFernflowerPreferences.REMOVE_BRIDGE, "0");
            options.put(IFernflowerPreferences.LOG_LEVEL, "warn");

            Fernflower fernflower = new Fernflower(
                    new DecompilerHelper.FileBytecodeProvider(classesDir),
                    new DecompilerHelper.DirectoryResultSaver(classesDir),
                    options,
                    new DecompilerHelper.DecompilerLogger()
            );

            fernflower.addSource(classesDir.toFile());
            fernflower.decompileContext();

            // Step 3: Pack .java files into sources.jar
            indicator.setText("Creating sources.jar...");
            indicator.setFraction(0.8);
            DecompilerHelper.packJavaFiles(classesDir, outputJar);

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
}
