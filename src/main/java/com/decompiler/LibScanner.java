package com.decompiler;

import java.io.*;
import java.util.*;

/**
 * Scans a lib directory for dependency JAR files and extracts their Maven coordinates.
 */
public class LibScanner {

    /**
     * Represents a dependency with Maven coordinates.
     */
    public static class DependencyInfo {
        private String groupId;
        private String artifactId;
        private String version;
        private File jarFile;
        private boolean hasCoordinates; // true if GAV could be determined

        public DependencyInfo(String groupId, String artifactId, String version, File jarFile) {
            this.groupId = groupId != null ? groupId : "";
            this.artifactId = artifactId != null ? artifactId : "";
            this.version = version != null ? version : "";
            this.jarFile = jarFile;
            this.hasCoordinates = !this.groupId.isEmpty() && !this.artifactId.isEmpty() && !this.version.isEmpty();
        }

        public String getGroupId() { return groupId; }
        public String getArtifactId() { return artifactId; }
        public String getVersion() { return version; }
        public File getJarFile() { return jarFile; }
        public boolean hasCoordinates() { return hasCoordinates; }

        /**
         * Maven-style dependency string: groupId:artifactId:version
         */
        public String toMavenString() {
            return groupId + ":" + artifactId + ":" + version;
        }

        /**
         * Gradle-style dependency string: 'groupId:artifactId:version'
         */
        public String toGradleString() {
            return "'" + groupId + ":" + artifactId + ":" + version + "'";
        }

        /**
         * Returns a safe name for flatDir fallback.
         */
        public String getSafeName() {
            String name = jarFile.getName();
            if (name.toLowerCase().endsWith(".jar")) {
                name = name.substring(0, name.length() - 4);
            }
            return name.replaceAll("[^a-zA-Z0-9._-]", "-");
        }
    }

    /**
     * Scan for lib directory relative to the JAR file.
     * Looks for:
     * - {parentDir}/lib/
     * - {parentDir}/{jarName}/lib/ (inside exploded directory)
     *
     * @param jarFile the main JAR file
     * @return list of dependency JARs found in the lib directory
     */
    public static List<DependencyInfo> scanLibDirectory(File jarFile) {
        List<DependencyInfo> deps = new ArrayList<>();
        File parentDir = jarFile.getParentFile();
        if (parentDir == null) return deps;

        // Check for lib directory
        File libDir = new File(parentDir, "lib");
        if (!libDir.exists() || !libDir.isDirectory()) {
            return deps;
        }

        File[] jarFiles = libDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null) return deps;

        // Sort by name for consistent output
        Arrays.sort(jarFiles, Comparator.comparing(File::getName));

        for (File libJar : jarFiles) {
            JarInfoExtractor.JarInfo info = JarInfoExtractor.extract(libJar);
            DependencyInfo dep = new DependencyInfo(
                    info.getGroupId(),
                    info.getArtifactId(),
                    info.getVersion(),
                    libJar
            );
            deps.add(dep);
        }

        return deps;
    }

    /**
     * Scan a list of JAR files and extract Maven coordinates from each.
     * Used for Spring Boot embedded JARs extracted from BOOT-INF/lib/.
     *
     * @param jarFiles list of JAR files to scan
     * @return list of dependency info extracted from the JARs
     */
    public static List<DependencyInfo> scanJarFiles(List<File> jarFiles) {
        List<DependencyInfo> deps = new ArrayList<>();
        if (jarFiles == null) return deps;

        // Sort by name for consistent output
        List<File> sorted = new ArrayList<>(jarFiles);
        sorted.sort(Comparator.comparing(File::getName));

        for (File libJar : sorted) {
            JarInfoExtractor.JarInfo info = JarInfoExtractor.extract(libJar);
            DependencyInfo dep = new DependencyInfo(
                    info.getGroupId(),
                    info.getArtifactId(),
                    info.getVersion(),
                    libJar
            );
            deps.add(dep);
        }
        return deps;
    }

    /**
     * Copy lib directory to target project.
     *
     * @param sourceLibDir source lib directory
     * @param targetLibDir target lib directory in the project
     */
    public static void copyLibDirectory(File sourceLibDir, File targetLibDir) throws IOException {
        if (!sourceLibDir.exists() || !sourceLibDir.isDirectory()) return;

        if (!targetLibDir.exists()) {
            targetLibDir.mkdirs();
        }

        File[] jarFiles = sourceLibDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null) return;

        for (File jar : jarFiles) {
            File target = new File(targetLibDir, jar.getName());
            copyFile(jar, target);
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        try (InputStream is = new FileInputStream(source);
             OutputStream os = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        }
    }
}
