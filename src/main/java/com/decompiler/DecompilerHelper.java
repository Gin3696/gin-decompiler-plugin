package com.decompiler;

import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.stream.*;
import java.util.zip.*;

/**
 * Shared utility methods for JAR decompilation.
 * Used by both DecompilerAction and GenerateProjectAction.
 */
public class DecompilerHelper {

    /**
     * Result of extracting classes from a JAR.
     */
    public static class ExtractionResult {
        private final int classCount;
        private final boolean isSpringBootJar;
        private final List<File> embeddedLibJars;

        public ExtractionResult(int classCount, boolean isSpringBootJar, List<File> embeddedLibJars) {
            this.classCount = classCount;
            this.isSpringBootJar = isSpringBootJar;
            this.embeddedLibJars = embeddedLibJars;
        }

        public int getClassCount() { return classCount; }
        public boolean isSpringBootJar() { return isSpringBootJar; }
        public List<File> getEmbeddedLibJars() { return embeddedLibJars; }
    }

    /**
     * Extract .class files from a JAR into a directory tree.
     * Handles both regular JARs and Spring Boot fat JARs (BOOT-INF/classes/).
     *
     * For Spring Boot JARs:
     *   - Classes are extracted from BOOT-INF/classes/ (prefix stripped)
     *   - Embedded JARs from BOOT-INF/lib/ are extracted to libDir
     *
     * For regular JARs:
     *   - All .class files are extracted
     *
     * @param jarFile the source JAR
     * @param destDir destination directory for .class files
     * @param libDir  if non-null, embedded lib JARs (BOOT-INF/lib/) are extracted here
     * @return ExtractionResult with metadata
     */
    public static ExtractionResult extractJarClasses(File jarFile, Path destDir, Path libDir) throws IOException {
        boolean isSpringBoot = isSpringBootJar(jarFile);
        int[] count = {0};
        List<File> embeddedJars = new ArrayList<>();

        try (JarFile jar = new JarFile(jarFile)) {
            java.util.Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) continue;

                if (isSpringBoot) {
                    // Spring Boot fat JAR: only extract from BOOT-INF/classes/
                    if (name.startsWith("BOOT-INF/classes/") && name.endsWith(".class")) {
                        String relativePath = name.substring("BOOT-INF/classes/".length());
                        Path target = destDir.resolve(relativePath);
                        Files.createDirectories(target.getParent());
                        try (InputStream in = jar.getInputStream(entry)) {
                            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                        count[0]++;
                    }
                    // Extract embedded lib JARs from BOOT-INF/lib/
                    else if (name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar") && libDir != null) {
                        String jarName = name.substring(name.lastIndexOf('/') + 1);
                        Path targetJar = libDir.resolve(jarName);
                        Files.createDirectories(targetJar.getParent());
                        try (InputStream in = jar.getInputStream(entry)) {
                            Files.copy(in, targetJar, StandardCopyOption.REPLACE_EXISTING);
                        }
                        embeddedJars.add(targetJar.toFile());
                    }
                } else {
                    // Regular JAR: extract all .class files
                    if (name.endsWith(".class")) {
                        Path target = destDir.resolve(name);
                        Files.createDirectories(target.getParent());
                        try (InputStream in = jar.getInputStream(entry)) {
                            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                        count[0]++;
                    }
                }
            }
        }
        return new ExtractionResult(count[0], isSpringBoot, embeddedJars);
    }

    /**
     * Backward-compatible overload for DecompilerAction (no lib extraction).
     */
    public static ExtractionResult extractJarClasses(File jarFile, Path destDir) throws IOException {
        return extractJarClasses(jarFile, destDir, null);
    }

    /**
     * Check if a JAR is a Spring Boot fat JAR (contains BOOT-INF/classes/).
     */
    public static boolean isSpringBootJar(File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            if (jar.getEntry("BOOT-INF/classes/") != null) return true;
            if (jar.getEntry("BOOT-INF/") != null) return true;
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String mainClass = manifest.getMainAttributes().getValue("Start-Class");
                if (mainClass != null) return true;
                String springBootVersion = manifest.getMainAttributes().getValue("Spring-Boot-Version");
                if (springBootVersion != null) return true;
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    /**
     * Pack all .java files from a directory tree into a JAR.
     */
    public static void packJavaFiles(Path sourceDir, File outputJar) throws IOException {
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

    /**
     * Provides bytecode (.class file content) from the extracted classes directory.
     */
    public static class FileBytecodeProvider implements IBytecodeProvider {
        private final Path baseDir;

        public FileBytecodeProvider(Path baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
            Path classFile = internalPath != null ? baseDir.resolve(internalPath) : Path.of(externalPath);
            return Files.readAllBytes(classFile);
        }
    }

    /**
     * Saves decompiled .java files alongside the original .class files.
     */
    public static class DirectoryResultSaver implements IResultSaver {
        private final Path baseDir;

        public DirectoryResultSaver(Path baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public void saveFolder(String path) {
        }

        @Override
        public void copyFile(String source, String path, String entryName) {
            if (source == null || entryName == null) return;
            try {
                Path sourcePath = Path.of(source);
                if (!Files.exists(sourcePath)) return;
                byte[] content = Files.readAllBytes(sourcePath);
                saveClassFile(path != null ? path : "", null, entryName, new String(content, "UTF-8"), null);
            } catch (Exception ignored) {
            }
        }

        @Override
        public void saveClassFile(String path, String qualifiedName,
                                  String entryName, String content, int[] mapping) {
            if (entryName == null || content == null) return;
            try {
                String safePath = path != null ? path : "";
                Path javaFile = baseDir.resolve(safePath).resolve(entryName);
                String javaFileName = javaFile.getFileName().toString();
                int dot = javaFileName.lastIndexOf('.');
                if (dot > 0) {
                    javaFileName = javaFileName.substring(0, dot) + ".java";
                }
                Path target = javaFile.resolveSibling(javaFileName);
                Files.createDirectories(target.getParent());
                Files.write(target, content.getBytes("UTF-8"));
            } catch (Exception ignored) {
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
            if (content == null || entryName == null) return;
            try {
                String dirPath = "";
                if (qualifiedName != null && !qualifiedName.isEmpty()) {
                    dirPath = qualifiedName.replace('.', '/');
                    int lastSlash = dirPath.lastIndexOf('/');
                    if (lastSlash > 0) {
                        dirPath = dirPath.substring(0, lastSlash);
                    } else {
                        dirPath = "";
                    }
                } else if (path != null && !path.isEmpty()) {
                    dirPath = path;
                }

                Path target;
                if (!dirPath.isEmpty()) {
                    target = baseDir.resolve(dirPath).resolve(entryName);
                } else {
                    target = baseDir.resolve(entryName);
                }

                String fileName = target.getFileName().toString();
                int dot = fileName.lastIndexOf('.');
                if (dot > 0) {
                    fileName = fileName.substring(0, dot) + ".java";
                }
                target = target.resolveSibling(fileName);

                Files.createDirectories(target.getParent());
                Files.write(target, content.getBytes("UTF-8"));
            } catch (Exception ignored) {
            }
        }

        @Override
        public void closeArchive(String path, String archiveName) {
        }
    }

    /**
     * Minimal logger for Fernflower (suppresses most output).
     */
    public static class DecompilerLogger extends IFernflowerLogger {
        @Override
        public void writeMessage(String message, Severity severity) {
        }

        @Override
        public void writeMessage(String message, Severity severity, Throwable t) {
        }
    }
}
