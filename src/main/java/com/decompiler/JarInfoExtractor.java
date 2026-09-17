package com.decompiler;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.regex.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/**
 * Extracts project metadata (groupId, artifactId, version) from a JAR file.
 * Priority: pom.properties > MANIFEST.MF > filename parsing.
 */
public class JarInfoExtractor {

    /**
     * Holds Maven-style project coordinates.
     */
    public static class JarInfo {
        private String groupId;
        private String artifactId;
        private String version;

        public JarInfo() {
            this.groupId = "";
            this.artifactId = "";
            this.version = "";
        }

        public JarInfo(String groupId, String artifactId, String version) {
            this.groupId = groupId != null ? groupId : "";
            this.artifactId = artifactId != null ? artifactId : "";
            this.version = version != null ? version : "";
        }

        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId != null ? groupId : ""; }
        public String getArtifactId() { return artifactId; }
        public void setArtifactId(String artifactId) { this.artifactId = artifactId != null ? artifactId : ""; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version != null ? version : ""; }

        public boolean hasGroupId() { return !groupId.isEmpty(); }
        public boolean hasArtifactId() { return !artifactId.isEmpty(); }
        public boolean hasVersion() { return !version.isEmpty(); }
        public boolean isComplete() { return hasGroupId() && hasArtifactId() && hasVersion(); }

        /**
         * Returns a safe directory name based on the info.
         */
        public String toDirName() {
            if (hasArtifactId() && hasVersion()) {
                return artifactId + "-" + version;
            } else if (hasArtifactId()) {
                return artifactId;
            }
            return "unknown-project";
        }

        @Override
        public String toString() {
            return groupId + ":" + artifactId + ":" + version;
        }
    }

    /**
     * Extract project info from a JAR file using multiple strategies.
     */
    public static JarInfo extract(File jarFile) {
        JarInfo info = new JarInfo();

        // Strategy 1: pom.properties
        tryExtractFromPomProperties(jarFile, info);
        if (info.isComplete()) return info;

        // Strategy 2: MANIFEST.MF
        tryExtractFromManifest(jarFile, info);
        if (info.isComplete()) return info;

        // Strategy 3: filename parsing
        tryExtractFromFilename(jarFile, info);

        return info;
    }

    /**
     * Represents a Maven dependency extracted from pom.xml.
     */
    public static class PomDependency {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String scope;

        public PomDependency(String groupId, String artifactId, String version, String scope) {
            this.groupId = groupId != null ? groupId : "";
            this.artifactId = artifactId != null ? artifactId : "";
            this.version = version != null ? version : "";
            this.scope = scope != null ? scope : "";
        }

        public String getGroupId() { return groupId; }
        public String getArtifactId() { return artifactId; }
        public String getVersion() { return version; }
        public String getScope() { return scope; }

        /**
         * Check if this dependency has resolved Maven coordinates (no placeholders).
         */
        public boolean isResolved() {
            return !groupId.isEmpty() && !artifactId.isEmpty()
                    && !groupId.contains("${") && !version.contains("${");
        }
    }

    /**
     * Extract dependencies from the JAR's embedded pom.xml
     * (META-INF/maven/{groupId}/{artifactId}/pom.xml).
     */
    public static List<PomDependency> extractPomDependencies(File jarFile) {
        List<PomDependency> deps = new ArrayList<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("META-INF/maven/") && name.endsWith("/pom.xml")) {
                    try (InputStream is = jar.getInputStream(entry)) {
                        deps.addAll(parsePomXml(is));
                    }
                    break; // Only use the first pom.xml found
                }
            }
        } catch (Exception ignored) {
        }
        return deps;
    }

    /**
     * Parse a pom.xml input stream and extract &lt;dependencies&gt; entries.
     */
    private static List<PomDependency> parsePomXml(InputStream is) {
        List<PomDependency> deps = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            // Disable external entities to prevent XXE
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);
            doc.getDocumentElement().normalize();

            NodeList depNodes = doc.getElementsByTagName("dependency");
            for (int i = 0; i < depNodes.getLength(); i++) {
                Node node = depNodes.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                Element elem = (Element) node;

                String groupId = getChildText(elem, "groupId");
                String artifactId = getChildText(elem, "artifactId");
                String version = getChildText(elem, "version");
                String scope = getChildText(elem, "scope");

                if (!groupId.isEmpty() && !artifactId.isEmpty()) {
                    deps.add(new PomDependency(groupId, artifactId, version, scope));
                }
            }
        } catch (Exception ignored) {
        }
        return deps;
    }

    /**
     * Get text content of a child element.
     */
    private static String getChildText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            Node node = nodes.item(0);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                String text = node.getTextContent();
                return text != null ? text.trim() : "";
            }
        }
        return "";
    }

    // Try to extract GAV from META-INF/maven/{groupId}/{artifactId}/pom.properties
    private static void tryExtractFromPomProperties(File jarFile, JarInfo info) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("META-INF/maven/") && name.endsWith("/pom.properties")) {
                    Properties props = new Properties();
                    try (InputStream is = jar.getInputStream(entry)) {
                        props.load(is);
                    }
                    if (!info.hasGroupId()) {
                        info.setGroupId(props.getProperty("groupId"));
                    }
                    if (!info.hasArtifactId()) {
                        info.setArtifactId(props.getProperty("artifactId"));
                    }
                    if (!info.hasVersion()) {
                        info.setVersion(props.getProperty("version"));
                    }
                    if (info.isComplete()) return;
                }
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Try to extract info from MANIFEST.MF
     */
    private static void tryExtractFromManifest(File jarFile, JarInfo info) {
        try (JarFile jar = new JarFile(jarFile)) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) return;

            java.util.jar.Attributes attrs = manifest.getMainAttributes();

            // Try Bundle-SymbolicName (OSGi)
            if (!info.hasGroupId()) {
                String symbolicName = attrs.getValue("Bundle-SymbolicName");
                if (symbolicName != null && symbolicName.contains(".")) {
                    int lastDot = symbolicName.lastIndexOf('.');
                    info.setGroupId(symbolicName.substring(0, lastDot));
                    if (!info.hasArtifactId()) {
                        info.setArtifactId(symbolicName.substring(lastDot + 1));
                    }
                }
            }

            // Try Implementation-Title / Implementation-Version
            if (!info.hasArtifactId()) {
                String title = attrs.getValue("Implementation-Title");
                if (title != null && !title.isEmpty()) {
                    info.setArtifactId(title.replaceAll("\\s+", "-").toLowerCase());
                }
            }
            if (!info.hasVersion()) {
                String ver = attrs.getValue("Implementation-Version");
                if (ver != null && !ver.isEmpty()) {
                    info.setVersion(ver);
                }
            }

            // Try Specification-Title / Specification-Version
            if (!info.hasArtifactId()) {
                String title = attrs.getValue("Specification-Title");
                if (title != null && !title.isEmpty()) {
                    info.setArtifactId(title.replaceAll("\\s+", "-").toLowerCase());
                }
            }
            if (!info.hasVersion()) {
                String ver = attrs.getValue("Specification-Version");
                if (ver != null && !ver.isEmpty()) {
                    info.setVersion(ver);
                }
            }

        } catch (IOException ignored) {
        }
    }

    /**
     * Parse groupId/artifactId/version from filename like: gson-2.10.1.jar, commons-lang3-3.12.0.jar
     */
    private static void tryExtractFromFilename(File jarFile, JarInfo info) {
        String name = jarFile.getName();
        if (name.toLowerCase().endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }

        // Pattern: name-version where version starts with a digit
        Pattern pattern = Pattern.compile("^(.+?)[-_](\\d+.*)$");
        Matcher matcher = pattern.matcher(name);
        if (matcher.matches()) {
            String artifactPart = matcher.group(1);
            String versionPart = matcher.group(2);

            if (!info.hasArtifactId()) {
                info.setArtifactId(artifactPart);
            }
            if (!info.hasVersion()) {
                info.setVersion(versionPart);
            }

            // Try to guess groupId from artifact name patterns
            if (!info.hasGroupId()) {
                info.setGroupId(guessGroupId(artifactPart));
            }
        } else {
            if (!info.hasArtifactId()) {
                info.setArtifactId(name);
            }
        }
    }

    /**
     * Guess common groupId from artifact name.
     * Uses ordered prefix matching — more specific prefixes first.
     */
    private static String guessGroupId(String artifactName) {
        // Ordered list: more specific prefixes must come before generic ones
        // e.g. "spring-boot" before "spring-", "jackson-databind" before "jackson-"
        String[][] mappings = {
                // Spring ecosystem
                {"spring-boot-starter", "org.springframework.boot"},
                {"spring-boot", "org.springframework.boot"},
                {"spring-cloud", "org.springframework.cloud"},
                {"spring-security", "org.springframework.security"},
                {"spring-data", "org.springframework.data"},
                {"spring-web", "org.springframework"},
                {"spring-core", "org.springframework"},
                {"spring-context", "org.springframework"},
                {"spring-beans", "org.springframework"},
                {"spring-aop", "org.springframework"},
                {"spring-tx", "org.springframework"},
                {"spring-jdbc", "org.springframework"},
                {"spring-orm", "org.springframework"},
                {"spring-expression", "org.springframework"},
                {"spring-messaging", "org.springframework"},
                {"spring-webflux", "org.springframework"},
                {"spring-test", "org.springframework"},
                {"spring-", "org.springframework"},

                // Logging
                {"slf4j-", "org.slf4j"},
                {"log4j-", "org.apache.logging.log4j"},
                {"logback-", "ch.qos.logback"},
                {"commons-logging", "commons-logging"},

                // Jackson
                {"jackson-core", "com.fasterxml.jackson.core"},
                {"jackson-databind", "com.fasterxml.jackson.core"},
                {"jackson-annotations", "com.fasterxml.jackson.core"},
                {"jackson-dataformat", "com.fasterxml.jackson.dataformat"},
                {"jackson-datatype", "com.fasterxml.jackson.datatype"},
                {"jackson-module", "com.fasterxml.jackson.module"},
                {"jackson-", "com.fasterxml.jackson.core"},

                // Apache Commons
                {"commons-lang3", "org.apache.commons"},
                {"commons-lang", "commons-lang"},
                {"commons-io", "commons-io"},
                {"commons-collections4", "org.apache.commons"},
                {"commons-collections", "commons-collections"},
                {"commons-codec", "commons-codec"},
                {"commons-beanutils", "commons-beanutils"},
                {"commons-fileupload", "commons-fileupload"},
                {"commons-pool2", "org.apache.commons"},
                {"commons-compress", "org.apache.commons"},
                {"commons-", "org.apache.commons"},

                // Apache Http
                {"httpclient", "org.apache.httpcomponents"},
                {"httpcore", "org.apache.httpcomponents"},
                {"httpmime", "org.apache.httpcomponents"},

                // Database / ORM
                {"mybatis-", "org.mybatis"},
                {"mybatis", "org.mybatis"},
                {"mysql-connector", "mysql"},
                {"postgresql", "org.postgresql"},
                {"hikaricp", "com.zaxxer"},
                {"druid", "com.alibaba"},
                {"HikariCP", "com.zaxxer"},

                // Google
                {"guava", "com.google.guava"},
                {"gson", "com.google.code.gson"},
                {"protobuf", "com.google.protobuf"},

                // Alibaba
                {"fastjson", "com.alibaba"},
                {"druid", "com.alibaba"},
                {"nacos-", "com.alibaba.nacos"},
                {"sentinel-", "com.alibaba.csp"},

                // Netty
                {"netty-", "io.netty"},

                // Square
                {"okhttp", "com.squareup.okhttp3"},
                {"retrofit", "com.squareup.retrofit2"},

                // Swagger / OpenAPI
                {"swagger-", "io.springfox"},
                {"springdoc-", "org.springdoc"},

                // Validation
                {"hibernate-validator", "org.hibernate.validator"},
                {"validation-api", "javax.validation"},

                // Lombok
                {"lombok", "org.projectlombok"},

                // MapStruct
                {"mapstruct", "org.mapstruct"},

                // Feign
                {"feign-", "io.github.openfeign"},

                // Reactor
                {"reactor-", "io.projectreactor"},

                // Hibernate
                {"hibernate-", "org.hibernate"},

                // YAML
                {"snakeyaml", "org.yaml"},

                // Caffeine
                {"caffeine", "com.github.ben-manes.caffeine"},

                // Hutool
                {"hutool-", "cn.hutool"},
        };

        String lower = artifactName.toLowerCase();
        for (String[] mapping : mappings) {
            if (lower.contains(mapping[0])) {
                return mapping[1];
            }
        }
        return "";
    }

    /**
     * Detect the root package from class files in a JAR.
     */
    public static String detectRootPackage(File jarFile) {
        Set<String> packages = new HashSet<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class") && !name.contains("$") && !name.startsWith("META-INF/")) {
                    int lastSlash = name.lastIndexOf('/');
                    if (lastSlash > 0) {
                        String pkg = name.substring(0, lastSlash).replace('/', '.');
                        packages.add(pkg);
                    }
                }
            }
        } catch (IOException ignored) {
        }

        if (packages.isEmpty()) return "";

        // Find common prefix (at least 2 levels)
        String common = null;
        for (String pkg : packages) {
            if (common == null) {
                common = pkg;
            } else {
                common = commonPrefix(common, pkg);
            }
        }

        if (common == null || common.isEmpty()) return "";

        // Ensure at least 2 levels (e.g., com.example)
        String[] parts = common.split("\\.");
        if (parts.length < 2) {
            // Try to find the most common 2-level prefix
            Map<String, Integer> prefixCount = new HashMap<>();
            for (String pkg : packages) {
                String[] pkgParts = pkg.split("\\.");
                if (pkgParts.length >= 2) {
                    String prefix = pkgParts[0] + "." + pkgParts[1];
                    prefixCount.merge(prefix, 1, Integer::sum);
                }
            }
            if (!prefixCount.isEmpty()) {
                return prefixCount.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("");
            }
            return "";
        }

        return common;
    }

    /**
     * Known third-party package prefixes to exclude when detecting project-owned packages.
     */
    private static final Set<String> KNOWN_THIRD_PARTY = new HashSet<>(Arrays.asList(
            "org/springframework/", "org/apache/", "com/google/", "com/fasterxml/",
            "com/alibaba/", "com/baomidou/", "org/slf4j/", "ch/qos/", "io/netty/",
            "io/lettuce/", "reactor/", "org/mybatis/", "org/hibernate/", "javax/",
            "jakarta/", "com/sun/", "org/eclipse/", "com/zaxxer/", "org/aopalliance/",
            "org/yaml/", "org/codehaus/", "org/jboss/", "io/undertow/", "com/zhongan/"
    ));

    /**
     * Detect the project's own top-level package prefixes (using '/' separator)
     * by scanning the JAR and filtering out known third-party packages.
     * Returns prefixes like {"org/hzero/", "com/example/"} that can be used
     * to filter which classes to decompile.
     */
    public static Set<String> detectProjectPrefixes(File jarFile) {
        // Collect all top-level 2-segment prefixes and their class counts
        Map<String, Integer> prefixCounts = new HashMap<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class") || name.contains("$") || name.startsWith("META-INF/")) continue;

                // Skip known third-party
                boolean isThirdParty = false;
                for (String tp : KNOWN_THIRD_PARTY) {
                    if (name.startsWith(tp)) { isThirdParty = true; break; }
                }
                if (isThirdParty) continue;

                // Extract top-level 2-segment prefix (e.g. "org/hzero" from "org/hzero/boot/...")
                int firstSlash = name.indexOf('/');
                if (firstSlash <= 0) continue;
                int secondSlash = name.indexOf('/', firstSlash + 1);
                if (secondSlash <= 0) continue;
                String prefix = name.substring(0, secondSlash + 1); // e.g. "org/hzero/"
                prefixCounts.merge(prefix, 1, Integer::sum);
            }
        } catch (IOException ignored) {
        }

        // Return prefixes that have at least 2 classes (to filter out noise)
        Set<String> result = new HashSet<>();
        for (Map.Entry<String, Integer> e : prefixCounts.entrySet()) {
            if (e.getValue() >= 2) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    /**
     * Detect Java version from class file major version.
     */
    public static int detectJavaVersion(File jarFile) {
        int maxVersion = 0;
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class") && !entry.isDirectory()) {
                    try (InputStream is = jar.getInputStream(entry)) {
                        byte[] header = new byte[8];
                        int read = is.read(header);
                        if (read == 8) {
                            // Major version is at offset 6-7 (big-endian)
                            int major = ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
                            if (major > maxVersion) {
                                maxVersion = major;
                            }
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }

        return classVersionToJavaVersion(maxVersion);
    }

    /**
     * Convert class file major version to Java version number.
     */
    public static int classVersionToJavaVersion(int classVersion) {
        // Java 1.1 = 45, Java 1.2 = 46, ..., Java 8 = 52, Java 9 = 53, ...
        if (classVersion <= 45) return 1;
        if (classVersion <= 52) return 8;
        return classVersion - 44; // Java 9 = 53 - 44 = 9
    }

    private static String commonPrefix(String a, String b) {
        String[] partsA = a.split("\\.");
        String[] partsB = b.split("\\.");
        int len = Math.min(partsA.length, partsB.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (partsA[i].equals(partsB[i])) {
                if (sb.length() > 0) sb.append(".");
                sb.append(partsA[i]);
            } else {
                break;
            }
        }
        return sb.toString();
    }
}
