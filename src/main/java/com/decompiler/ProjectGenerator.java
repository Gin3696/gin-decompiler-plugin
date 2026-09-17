package com.decompiler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Generates Maven or Gradle project structure from decompiled sources.
 */
public class ProjectGenerator {

    /**
     * Configuration for project generation.
     */
    public static class ProjectConfig {
        private String groupId;
        private String artifactId;
        private String version;
        private int javaVersion;
        private String rootPackage;
        private List<LibScanner.DependencyInfo> dependencies;
        private List<JarInfoExtractor.PomDependency> pomDependencies;
        private boolean hasLibDir;
        private boolean isSpringBoot;
        private String springBootVersion;

        public ProjectConfig() {
            this.groupId = "com.example";
            this.artifactId = "decompiled-project";
            this.version = "1.0.0";
            this.javaVersion = 8;
            this.rootPackage = "";
            this.dependencies = new ArrayList<>();
            this.pomDependencies = new ArrayList<>();
            this.hasLibDir = false;
            this.isSpringBoot = false;
            this.springBootVersion = "";
        }

        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
        public String getArtifactId() { return artifactId; }
        public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public int getJavaVersion() { return javaVersion; }
        public void setJavaVersion(int javaVersion) { this.javaVersion = javaVersion; }
        public String getRootPackage() { return rootPackage; }
        public void setRootPackage(String rootPackage) { this.rootPackage = rootPackage; }
        public List<LibScanner.DependencyInfo> getDependencies() { return dependencies; }
        public void setDependencies(List<LibScanner.DependencyInfo> dependencies) { this.dependencies = dependencies; }
        public List<JarInfoExtractor.PomDependency> getPomDependencies() { return pomDependencies; }
        public void setPomDependencies(List<JarInfoExtractor.PomDependency> pomDependencies) { this.pomDependencies = pomDependencies; }
        public boolean hasLibDir() { return hasLibDir; }
        public void setHasLibDir(boolean hasLibDir) { this.hasLibDir = hasLibDir; }
        public boolean isSpringBoot() { return isSpringBoot; }
        public void setSpringBoot(boolean springBoot) { isSpringBoot = springBoot; }
        public String getSpringBootVersion() { return springBootVersion; }
        public void setSpringBootVersion(String springBootVersion) { this.springBootVersion = springBootVersion; }
    }

    /**
     * Generate a Maven project.
     */
    public static void generateMavenProject(File projectDir, ProjectConfig config,
                                            Path decompiledSourcesDir) throws IOException {
        // Create directory structure
        Path srcMainJava = projectDir.toPath().resolve("src/main/java");
        Files.createDirectories(srcMainJava);

        // Copy decompiled sources
        if (decompiledSourcesDir != null && Files.exists(decompiledSourcesDir)) {
            copyDirectory(decompiledSourcesDir, srcMainJava);
        }

        // Copy lib directory if exists
        if (config.hasLibDir()) {
            Path libDir = projectDir.toPath().resolve("lib");
            Files.createDirectories(libDir);
        }

        // Generate pom.xml
        String pomContent = generatePomXml(config);
        Files.write(projectDir.toPath().resolve("pom.xml"),
                pomContent.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate a Gradle project.
     */
    public static void generateGradleProject(File projectDir, ProjectConfig config,
                                             Path decompiledSourcesDir) throws IOException {
        // Create directory structure
        Path srcMainJava = projectDir.toPath().resolve("src/main/java");
        Files.createDirectories(srcMainJava);

        // Copy decompiled sources
        if (decompiledSourcesDir != null && Files.exists(decompiledSourcesDir)) {
            copyDirectory(decompiledSourcesDir, srcMainJava);
        }

        // Copy lib directory if exists
        if (config.hasLibDir()) {
            Path libDir = projectDir.toPath().resolve("lib");
            Files.createDirectories(libDir);
        }

        // Generate build.gradle
        String buildGradleContent = generateBuildGradle(config);
        Files.write(projectDir.toPath().resolve("build.gradle"),
                buildGradleContent.getBytes(StandardCharsets.UTF_8));

        // Generate settings.gradle
        String settingsContent = generateSettingsGradle(config);
        Files.write(projectDir.toPath().resolve("settings.gradle"),
                settingsContent.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate pom.xml content.
     */
    private static String generatePomXml(ProjectConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
        sb.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 ");
        sb.append("http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        sb.append("    <modelVersion>4.0.0</modelVersion>\n\n");

        // GAV
        sb.append("    <groupId>").append(escapeXml(config.getGroupId())).append("</groupId>\n");
        sb.append("    <artifactId>").append(escapeXml(config.getArtifactId())).append("</artifactId>\n");
        sb.append("    <version>").append(escapeXml(config.getVersion())).append("</version>\n");
        sb.append("    <packaging>jar</packaging>\n\n");

        // Properties
        sb.append("    <properties>\n");
        sb.append("        <maven.compiler.source>").append(config.getJavaVersion()).append("</maven.compiler.source>\n");
        sb.append("        <maven.compiler.target>").append(config.getJavaVersion()).append("</maven.compiler.target>\n");
        sb.append("        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n");
        sb.append("    </properties>\n\n");

        // Repositories
        sb.append("    <repositories>\n");
        sb.append("        <repository>\n");
        sb.append("            <id>central</id>\n");
        sb.append("            <url>https://repo.maven.apache.org/maven2</url>\n");
        sb.append("        </repository>\n");
        sb.append("        <repository>\n");
        sb.append("            <id>spring-releases</id>\n");
        sb.append("            <url>https://repo.spring.io/release</url>\n");
        sb.append("        </repository>\n");
        sb.append("        <repository>\n");
        sb.append("            <id>jboss-public</id>\n");
        sb.append("            <url>https://repository.jboss.org/nexus/content/repositories/releases/</url>\n");
        sb.append("        </repository>\n");
        sb.append("    </repositories>\n\n");

        // Dependencies
        List<LibScanner.DependencyInfo> deps = config.getDependencies();
        List<JarInfoExtractor.PomDependency> pomDeps = config.getPomDependencies();
        boolean hasDeps = !deps.isEmpty() || !pomDeps.isEmpty();

        if (hasDeps) {
            sb.append("    <dependencies>\n");

            // Dependencies from embedded pom.xml and import resolution
            for (JarInfoExtractor.PomDependency dep : pomDeps) {
                if (dep.getGroupId().isEmpty() || dep.getArtifactId().isEmpty()) continue;
                sb.append("        <dependency>\n");
                sb.append("            <groupId>").append(escapeXml(dep.getGroupId())).append("</groupId>\n");
                sb.append("            <artifactId>").append(escapeXml(dep.getArtifactId())).append("</artifactId>\n");
                // Ensure every dependency has a version
                String version = dep.getVersion();
                if (version == null || version.isEmpty()) {
                    version = "RELEASE";
                }
                sb.append("            <version>").append(escapeXml(version)).append("</version>\n");
                if (!dep.getScope().isEmpty()) {
                    sb.append("            <scope>").append(escapeXml(dep.getScope())).append("</scope>\n");
                }
                sb.append("        </dependency>\n");
            }

            // Dependencies from lib directory
            for (LibScanner.DependencyInfo dep : deps) {
                sb.append("        <dependency>\n");
                if (dep.hasCoordinates()) {
                    sb.append("            <groupId>").append(escapeXml(dep.getGroupId())).append("</groupId>\n");
                    sb.append("            <artifactId>").append(escapeXml(dep.getArtifactId())).append("</artifactId>\n");
                    sb.append("            <version>").append(escapeXml(dep.getVersion())).append("</version>\n");
                } else {
                    // Local JAR without Maven coordinates — use system scope
                    sb.append("            <groupId>local</groupId>\n");
                    sb.append("            <artifactId>").append(escapeXml(dep.getSafeName())).append("</artifactId>\n");
                    sb.append("            <version>1.0</version>\n");
                    sb.append("            <scope>system</scope>\n");
                    sb.append("            <systemPath>${project.basedir}/lib/").append(escapeXml(dep.getJarFile().getName())).append("</systemPath>\n");
                }
                sb.append("        </dependency>\n");
            }
            sb.append("    </dependencies>\n\n");
        }

        sb.append("</project>\n");
        return sb.toString();
    }

    /**
     * Generate build.gradle content.
     */
    private static String generateBuildGradle(ProjectConfig config) {
        StringBuilder sb = new StringBuilder();

        // Plugins
        sb.append("plugins {\n");
        sb.append("    id 'java'\n");
        sb.append("}\n\n");

        // Group and version
        sb.append("group = '").append(escapeGradle(config.getGroupId())).append("'\n");
        sb.append("version = '").append(escapeGradle(config.getVersion())).append("'\n\n");

        // Java version
        sb.append("java {\n");
        sb.append("    sourceCompatibility = JavaVersion.VERSION_").append(config.getJavaVersion()).append("\n");
        sb.append("    targetCompatibility = JavaVersion.VERSION_").append(config.getJavaVersion()).append("\n");
        sb.append("}\n\n");

        // Repositories
        sb.append("repositories {\n");
        sb.append("    mavenCentral()\n");
        if (config.hasLibDir()) {
            sb.append("    flatDir {\n");
            sb.append("        dirs 'lib'\n");
            sb.append("    }\n");
        }
        sb.append("}\n\n");

        // Dependencies
        List<LibScanner.DependencyInfo> deps = config.getDependencies();
        List<JarInfoExtractor.PomDependency> pomDeps = config.getPomDependencies();
        boolean hasDeps = !deps.isEmpty() || !pomDeps.isEmpty();

        if (hasDeps) {
            sb.append("dependencies {\n");

            // Dependencies from embedded pom.xml and import resolution
            for (JarInfoExtractor.PomDependency dep : pomDeps) {
                if (dep.getGroupId().isEmpty() || dep.getArtifactId().isEmpty()) continue;
                String scope = dep.getScope();
                String config2;
                if ("test".equals(scope)) {
                    config2 = "testImplementation";
                } else if ("provided".equals(scope) || "compile".equals(scope) || scope.isEmpty()) {
                    config2 = "implementation";
                } else {
                    config2 = "implementation";
                }
                String version = dep.getVersion();
                if (version == null || version.isEmpty()) {
                    version = "RELEASE";
                }
                sb.append("    ").append(config2).append(" '")
                        .append(escapeGradle(dep.getGroupId())).append(":")
                        .append(escapeGradle(dep.getArtifactId())).append(":").append(escapeGradle(version))
                        .append("'\n");
            }

            // Dependencies from lib directory
            for (LibScanner.DependencyInfo dep : deps) {
                if (dep.hasCoordinates()) {
                    sb.append("    implementation ").append(dep.toGradleString()).append("\n");
                } else {
                    // flatDir fallback
                    sb.append("    implementation name: '").append(escapeGradle(dep.getSafeName())).append("'\n");
                }
            }
            sb.append("}\n\n");
        }

        // Encoding
        sb.append("tasks.withType(JavaCompile) {\n");
        sb.append("    options.encoding = 'UTF-8'\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Generate settings.gradle content.
     */
    private static String generateSettingsGradle(ProjectConfig config) {
        return "rootProject.name = '" + escapeGradle(config.getArtifactId()) + "'\n";
    }

    /**
     * Copy only .java files from directory recursively.
     */
    private static void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;

        Files.walk(source).forEach(sourcePath -> {
            try {
                String fileName = sourcePath.getFileName().toString();
                // Only copy .java files, skip .class and other files
                if (Files.isDirectory(sourcePath)) {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    Files.createDirectories(targetPath);
                } else if (fileName.endsWith(".java")) {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                // Log and continue
                System.err.println("Failed to copy: " + sourcePath + " -> " + e.getMessage());
            }
        });
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String escapeGradle(String s) {
        if (s == null) return "";
        return s.replace("'", "\\'");
    }

    /**
     * Scan decompiled .java files for import statements and resolve missing Maven dependencies.
     * Returns dependencies WITH versions resolved from the embedded JAR version map.
     *
     * @param decompiledDir directory containing decompiled .java files
     * @param rootPackage   the project's own root package (imports from this package are skipped)
     * @param versionMap    artifact name → version from embedded JAR filenames (may be empty)
     * @return list of PomDependency objects inferred from imports, with versions
     */
    public static List<JarInfoExtractor.PomDependency> resolveDependenciesFromImports(
            Path decompiledDir, String rootPackage, Map<String, String> versionMap) {
        // Step 1: Collect all imported packages from .java files
        Set<String> importedPackages = new HashSet<>();
        try {
            Files.walk(decompiledDir)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            List<String> lines = Files.readAllLines(p);
                            for (String line : lines) {
                                line = line.trim();
                                if (line.startsWith("import ")) {
                                    String imp = line.substring(7).replace(";", "").trim();
                                    if (imp.startsWith("static ")) {
                                        imp = imp.substring(7).trim();
                                    }
                                    int lastDot = imp.lastIndexOf('.');
                                    if (lastDot > 0) {
                                        importedPackages.add(imp.substring(0, lastDot));
                                    }
                                }
                            }
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }

        // Step 2: Map package prefixes → Maven coordinates
        // Key: package prefix, Value: "groupId:artifactId"
        // IMPORTANT: more specific prefixes MUST come before generic ones
        String[][] packageToDep = {
                // === Spring Boot ===
                {"org.springframework.boot.autoconfigure", "org.springframework.boot:spring-boot-autoconfigure"},
                {"org.springframework.boot", "org.springframework.boot:spring-boot"},
                // === Spring Cloud ===
                {"org.springframework.cloud.openfeign", "org.springframework.cloud:spring-cloud-starter-openfeign"},
                {"org.springframework.cloud.gateway", "org.springframework.cloud:spring-cloud-starter-gateway"},
                {"org.springframework.cloud.netflix", "org.springframework.cloud:spring-cloud-starter-netflix-eureka-client"},
                {"org.springframework.cloud.client", "org.springframework.cloud:spring-cloud-commons"},
                {"org.springframework.cloud", "org.springframework.cloud:spring-cloud-context"},
                // === Spring Security ===
                {"org.springframework.security", "org.springframework.security:spring-security-core"},
                // === Spring Data ===
                {"org.springframework.data", "org.springframework.data:spring-data-commons"},
                // === Spring Framework ===
                {"org.springframework.web.servlet", "org.springframework:spring-webmvc"},
                {"org.springframework.web", "org.springframework:spring-web"},
                {"org.springframework.http", "org.springframework:spring-web"},
                {"org.springframework.stereotype", "org.springframework:spring-context"},
                {"org.springframework.context", "org.springframework:spring-context"},
                {"org.springframework.beans", "org.springframework:spring-beans"},
                {"org.springframework.aop", "org.springframework:spring-aop"},
                {"org.springframework.transaction", "org.springframework:spring-tx"},
                {"org.springframework.core", "org.springframework:spring-core"},
                {"org.springframework.cglib", "org.springframework:spring-core"},
                {"org.springframework.util", "org.springframework:spring-core"},
                {"org.springframework.expression", "org.springframework:spring-expression"},
                {"org.springframework", "org.springframework:spring-context"},
                // === HZero / Choerodon ===
                {"org.hzero", "org.hzero.boot:hzero-boot-platform"},
                {"io.choerodon", "io.choerodon:choerodon-starter-core"},
                // === Logging ===
                {"org.slf4j", "org.slf4j:slf4j-api"},
                {"ch.qos.logback", "ch.qos.logback:logback-classic"},
                {"org.apache.logging.log4j", "org.apache.logging.log4j:log4j-core"},
                {"org.apache.log4j", "log4j:log4j"},
                // === Apache Commons ===
                {"org.apache.commons.lang3", "org.apache.commons:commons-lang3"},
                {"org.apache.commons.lang", "commons-lang:commons-lang"},
                {"org.apache.commons.io", "commons-io:commons-io"},
                {"org.apache.commons.collections4", "org.apache.commons:commons-collections4"},
                {"org.apache.commons.collections", "commons-collections:commons-collections"},
                {"org.apache.commons.codec", "commons-codec:commons-codec"},
                {"org.apache.commons.fileupload", "commons-fileupload:commons-fileupload"},
                {"org.apache.commons.beanutils", "commons-beanutils:commons-beanutils"},
                {"org.apache.commons.httpclient", "commons-httpclient:commons-httpclient"},
                {"org.apache.commons", "org.apache.commons:commons-lang3"},
                // === Apache Http ===
                {"org.apache.http", "org.apache.httpcomponents:httpclient"},
                // === Jackson ===
                {"com.fasterxml.jackson", "com.fasterxml.jackson.core:jackson-databind"},
                // === Google ===
                {"com.google.common", "com.google.guava:guava"},
                {"com.google.gson", "com.google.code.gson:gson"},
                {"com.google.protobuf", "com.google.protobuf:protobuf-java"},
                // === Alibaba ===
                {"com.alibaba.fastjson", "com.alibaba:fastjson"},
                {"com.alibaba.fastjson2", "com.alibaba.fastjson2:fastjson2"},
                {"com.alibaba.druid", "com.alibaba:druid"},
                {"com.alibaba.nacos", "com.alibaba.nacos:nacos-client"},
                {"com.alibaba.cloud", "com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery"},
                {"com.alibaba.csp.sentinel", "com.alibaba.csp:sentinel-core"},
                {"com.alibaba", "com.alibaba:fastjson"},
                // === MyBatis ===
                {"org.apache.ibatis", "org.mybatis:mybatis"},
                {"org.mybatis", "org.mybatis:mybatis"},
                {"com.baomidou", "com.baomidou:mybatis-plus-boot-starter"},
                // === Database ===
                {"com.mysql", "mysql:mysql-connector-java"},
                {"org.postgresql", "org.postgresql:postgresql"},
                {"oracle.jdbc", "com.oracle.database.jdbc:ojdbc8"},
                {"com.microsoft.sqlserver", "com.microsoft.sqlserver:mssql-jdbc"},
                {"com.zaxxer.hikari", "com.zaxxer:HikariCP"},
                {"org.apache.ibatis.datasource", "org.mybatis:mybatis"},
                // === Swagger / OpenAPI ===
                {"io.swagger.annotations", "io.springfox:springfox-swagger2"},
                {"io.swagger", "io.swagger:swagger-annotations"},
                {"org.springdoc", "org.springdoc:springdoc-openapi-ui"},
                // === Netty ===
                {"io.netty", "io.netty:netty-all"},
                // === Feign ===
                {"feign", "io.github.openfeign:feign-core"},
                // === Reactor ===
                {"reactor", "io.projectreactor:reactor-core"},
                // === Hibernate ===
                {"org.hibernate.validator", "org.hibernate.validator:hibernate-validator"},
                {"org.hibernate", "org.hibernate:hibernate-core"},
                // === Java EE / Jakarta ===
                {"javax.servlet", "javax.servlet:javax.servlet-api"},
                {"javax.persistence", "javax.persistence:javax.persistence-api"},
                {"javax.validation", "javax.validation:validation-api"},
                {"javax.annotation", "javax.annotation:javax.annotation-api"},
                {"javax.xml", "javax.xml.bind:jaxb-api"},
                {"jakarta.servlet", "jakarta.servlet:jakarta.servlet-api"},
                {"jakarta.persistence", "jakarta.persistence:jakarta.persistence-api"},
                {"jakarta.validation", "jakarta.validation:jakarta.validation-api"},
                {"jakarta.annotation", "jakarta.annotation:jakarta.annotation-api"},
                // === Lombok ===
                {"lombok", "org.projectlombok:lombok"},
                // === YAML ===
                {"org.yaml.snakeyaml", "org.yaml:snakeyaml"},
                // === Redis ===
                {"io.lettuce", "io.lettuce:lettuce-core"},
                {"redis.clients.jedis", "redis.clients:jedis"},
                {"org.springframework.data.redis", "org.springframework.boot:spring-boot-starter-data-redis"},
                // === Caffeine ===
                {"com.github.benmanes.caffeine", "com.github.ben-manes.caffeine:caffeine"},
                // === Hutool ===
                {"cn.hutool", "cn.hutool:hutool-all"},
                // === MapStruct ===
                {"org.mapstruct", "org.mapstruct:mapstruct"},
                // === AOP Alliance ===
                {"org.aopalliance", "aopalliance:aopalliance"},
                // === Ehcache ===
                {"org.ehcache", "org.ehcache:ehcache"},
                // === Quartz ===
                {"org.quartz", "org.quartz-scheduler:quartz"},
                // === AspectJ ===
                {"org.aspectj", "org.aspectj:aspectjweaver"},
                // === RabbitMQ ===
                {"org.springframework.amqp", "org.springframework.boot:spring-boot-starter-amqp"},
                // === Kafka ===
                {"org.springframework.kafka", "org.springframework.kafka:spring-kafka"},
        };

        // Step 3: Match imported packages to Maven deps, resolve versions
        Map<String, String> neededDeps = new LinkedHashMap<>(); // "groupId:artifactId" → version
        for (String pkg : importedPackages) {
            // Skip project's own packages
            if (rootPackage != null && !rootPackage.isEmpty() && pkg.startsWith(rootPackage)) {
                continue;
            }
            // Skip java.* standard library
            if (pkg.startsWith("java.")) {
                continue;
            }
            // Skip most javax.* (but keep servlet, persistence, validation, annotation, xml)
            if (pkg.startsWith("javax.") && !pkg.startsWith("javax.servlet")
                    && !pkg.startsWith("javax.persistence") && !pkg.startsWith("javax.validation")
                    && !pkg.startsWith("javax.annotation") && !pkg.startsWith("javax.xml")) {
                continue;
            }
            if (pkg.startsWith("jakarta.") && !pkg.startsWith("jakarta.servlet")
                    && !pkg.startsWith("jakarta.persistence") && !pkg.startsWith("jakarta.validation")
                    && !pkg.startsWith("jakarta.annotation")) {
                continue;
            }

            for (String[] mapping : packageToDep) {
                if (pkg.equals(mapping[0]) || pkg.startsWith(mapping[0] + ".")) {
                    String ga = mapping[1];
                    if (!neededDeps.containsKey(ga)) {
                        // Resolve version from embedded JAR version map
                        String[] parts = ga.split(":");
                        String version = resolveVersion(parts[1], versionMap);
                        neededDeps.put(ga, version);
                    }
                    break;
                }
            }
        }

        // Step 4: Convert to PomDependency list
        List<JarInfoExtractor.PomDependency> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : neededDeps.entrySet()) {
            String[] parts = entry.getKey().split(":");
            if (parts.length == 2) {
                result.add(new JarInfoExtractor.PomDependency(parts[0], parts[1], entry.getValue(), ""));
            }
        }
        return result;
    }

    /**
     * Resolve version for an artifact from the embedded JAR version map.
     * Tries exact match first, then prefix match. Falls back to "RELEASE".
     */
    private static String resolveVersion(String artifactId, Map<String, String> versionMap) {
        if (versionMap == null || versionMap.isEmpty()) {
            return "RELEASE";
        }
        String key = artifactId.toLowerCase();
        // Exact match
        String ver = versionMap.get(key);
        if (ver != null && !ver.isEmpty()) return ver;
        // Prefix match (e.g., artifactId "spring-boot-starter" matches JAR "spring-boot-starter-web")
        for (Map.Entry<String, String> e : versionMap.entrySet()) {
            if (e.getKey().startsWith(key + "-") || e.getKey().startsWith(key + "_")) {
                if (e.getValue() != null && !e.getValue().isEmpty()) {
                    return e.getValue();
                }
            }
        }
        return "RELEASE";
    }
}
