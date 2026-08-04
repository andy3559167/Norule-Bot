package com.norule.musicbot.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RuntimeDependencyBootstrap {
    static final String RELAUNCHED_PROPERTY = "norule.bootstrap.relaunched";
    static final String DEPENDENCY_INDEX_RESOURCE = "/bootstrap/runtime-dependencies.txt";
    static final String DEPENDENCY_CHECKSUM_RESOURCE = "/bootstrap/runtime-dependency-checksums.txt";
    static final String CLEANUP_OBSOLETE_PROPERTY = "norule.bootstrap.cleanup-obsolete";
    static final String VERIFY_CHECKSUMS_PROPERTY = "norule.bootstrap.verify-checksums";
    static final String FORCE_REDOWNLOAD_PROPERTY = "norule.bootstrap.force-redownload";
    static final String RUNTIME_LIB_DIRECTORY = "runtime-libs";
    private static final String ENABLE_NATIVE_ACCESS_ARG = "--enable-native-access=ALL-UNNAMED";
    private static final String SHA_256 = "SHA-256";
    private static final Pattern CHECKSUM_LINE = Pattern.compile("^([0-9a-fA-F]{64})\\s+[* ]?(.+)$");
    private static final System.Logger LOGGER = System.getLogger(RuntimeDependencyBootstrap.class.getName());

    private static final Set<String> CONFLICTING_SLF4J_PROVIDERS = Set.of(
            "slf4j-simple",
            "log4j-slf4j2-impl",
            "slf4j-reload4j"
    );

    private static final List<String> REMOTE_REPOSITORIES = List.of(
            "https://repo.maven.apache.org/maven2",
            "https://maven.lavalink.dev/releases",
            "https://maven.topi.wtf/releases"
    );

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;

    private RuntimeDependencyBootstrap() {
    }

    // S3516: changed from boolean to void — return true was dead code after System.exit()
    static void ensureDependenciesAndRelaunchIfNeeded(String[] args) {
        if (Boolean.getBoolean(RELAUNCHED_PROPERTY)) {
            return;
        }

        Path launcherPath = findLauncherPath();
        if (launcherPath == null || !Files.isRegularFile(launcherPath)) {
            return;
        }

        List<DependencyArtifact> artifacts = loadRuntimeDependencies();
        if (artifacts.isEmpty()) {
            LOGGER.log(System.Logger.Level.INFO, "[NoRule] Runtime dependency index is empty, skip lib bootstrap.");
            return;
        }

        Path workingDir = Path.of(".").toAbsolutePath().normalize();
        Path libDir = workingDir.resolve(RUNTIME_LIB_DIRECTORY);
        BootstrapSettings settings = BootstrapSettings.fromSystemProperties();
        try {
            Map<String, String> checksums = loadRuntimeDependencyChecksums();
            synchronizeRuntimeDependencies(libDir, artifacts, checksums, settings,
                    RuntimeDependencyBootstrap::downloadArtifact);
            int exitCode = relaunchWithLibClasspath(launcherPath, libDir, args);
            System.exit(exitCode);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to synchronize runtime dependencies in ./" + RUNTIME_LIB_DIRECTORY, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while relaunching with lib classpath", e);
        }
    }

    static List<DependencyArtifact> parseDependencyLines(List<String> lines) {
        Map<String, DependencyArtifact> unique = new LinkedHashMap<>();
        for (String line : lines) {
            // S135: extracted parseDependencyLine helper to eliminate multiple continues
            DependencyArtifact artifact = parseDependencyLine(line);
            if (artifact != null) {
                unique.putIfAbsent(buildJarFileName(artifact), artifact);
            }
        }
        return new ArrayList<>(unique.values());
    }

    static String buildJarFileName(DependencyArtifact artifact) {
        if (artifact.classifier() == null || artifact.classifier().isBlank()) {
            return artifact.artifactId() + "-" + artifact.version() + ".jar";
        }
        return artifact.artifactId() + "-" + artifact.version() + "-" + artifact.classifier() + ".jar";
    }

    static Map<String, String> parseChecksumLines(List<String> lines) {
        Map<String, String> checksums = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            Matcher matcher = CHECKSUM_LINE.matcher(line.trim());
            if (!matcher.matches()) {
                continue;
            }
            String fileName = Path.of(matcher.group(2).trim()).getFileName().toString();
            if (isJarFileName(fileName)) {
                checksums.put(fileName, matcher.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return checksums;
    }

    // S135: helper extracted from parseDependencyLines to replace the 3-continue loop
    private static DependencyArtifact parseDependencyLine(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = stripAnsi(line).trim();
        if (trimmed.isBlank() || !trimmed.contains(":")) {
            return null;
        }
        return parseLine(trimmed);
    }

    private static List<DependencyArtifact> loadRuntimeDependencies() {
        InputStream input = RuntimeDependencyBootstrap.class.getResourceAsStream(DEPENDENCY_INDEX_RESOURCE);
        if (input == null) {
            return List.of();
        }
        try (input) {
            List<String> lines = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            return parseDependencyLines(lines);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read runtime dependency index: " + DEPENDENCY_INDEX_RESOURCE, e);
        }
    }

    private static Map<String, String> loadRuntimeDependencyChecksums() {
        InputStream input = RuntimeDependencyBootstrap.class.getResourceAsStream(DEPENDENCY_CHECKSUM_RESOURCE);
        if (input == null) {
            return Map.of();
        }
        try (input) {
            List<String> lines = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            return parseChecksumLines(lines);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read runtime dependency checksums: "
                    + DEPENDENCY_CHECKSUM_RESOURCE, e);
        }
    }

    private static DependencyArtifact parseLine(String line) {
        String[] parts = line.split(":");
        if (parts.length == 5) {
            String groupId = parts[0].trim();
            String artifactId = parts[1].trim();
            String type = parts[2].trim();
            String version = parts[3].trim();
            if (!"jar".equalsIgnoreCase(type) || groupId.isBlank() || artifactId.isBlank() || version.isBlank()) {
                return null;
            }
            return new DependencyArtifact(groupId, artifactId, version, "");
        }
        if (parts.length == 6) {
            String groupId = parts[0].trim();
            String artifactId = parts[1].trim();
            String type = parts[2].trim();
            String classifier = parts[3].trim();
            String version = parts[4].trim();
            if (!"jar".equalsIgnoreCase(type) || groupId.isBlank() || artifactId.isBlank() || version.isBlank()) {
                return null;
            }
            return new DependencyArtifact(groupId, artifactId, version, classifier);
        }
        return null;
    }

    static void synchronizeRuntimeDependencies(Path libDir, List<DependencyArtifact> artifacts,
            Map<String, String> checksums, BootstrapSettings settings, ArtifactDownloader downloader) throws IOException {
        List<DependencyArtifact> activeArtifacts = removeConflictingProviders(artifacts);
        validateLogbackVersions(activeArtifacts);
        validateChecksums(activeArtifacts, checksums, settings);
        Files.createDirectories(libDir);

        int downloaded = downloadRequiredArtifacts(libDir, activeArtifacts, checksums, settings, downloader);
        int deleted = cleanupRuntimeJars(libDir, activeArtifacts, settings.cleanupObsolete());
        verifySynchronizedArtifacts(libDir, activeArtifacts, checksums, settings);

        if (downloaded > 0 || deleted > 0) {
            LOGGER.log(System.Logger.Level.INFO, "[NoRule] Runtime dependencies synchronized in: "
                    + libDir.toAbsolutePath() + " (downloaded=" + downloaded + ", deleted=" + deleted + ")");
        }
    }

    private static List<DependencyArtifact> removeConflictingProviders(List<DependencyArtifact> artifacts) {
        List<DependencyArtifact> active = new ArrayList<>();
        for (DependencyArtifact artifact : artifacts) {
            if (isConflictingProviderArtifact(artifact.artifactId())) {
                LOGGER.log(System.Logger.Level.WARNING, "[NoRule] Ignoring conflicting SLF4J provider from runtime index: "
                        + buildJarFileName(artifact));
            } else {
                active.add(artifact);
            }
        }
        return active;
    }

    private static void validateLogbackVersions(List<DependencyArtifact> artifacts) throws IOException {
        Set<String> classicVersions = versionsOf(artifacts, "logback-classic");
        Set<String> coreVersions = versionsOf(artifacts, "logback-core");
        if (classicVersions.isEmpty() && coreVersions.isEmpty()) {
            return;
        }
        if (classicVersions.size() != 1 || coreVersions.size() != 1
                || !classicVersions.iterator().next().equals(coreVersions.iterator().next())) {
            throw new IOException("Runtime dependency index must define exactly one matching version of "
                    + "logback-classic and logback-core (classic=" + classicVersions + ", core=" + coreVersions + ")");
        }
    }

    private static Set<String> versionsOf(List<DependencyArtifact> artifacts, String artifactId) {
        Set<String> versions = new HashSet<>();
        for (DependencyArtifact artifact : artifacts) {
            if (artifactId.equals(artifact.artifactId())) {
                versions.add(artifact.version());
            }
        }
        return versions;
    }

    private static void validateChecksums(List<DependencyArtifact> artifacts, Map<String, String> checksums,
            BootstrapSettings settings) throws IOException {
        if (!settings.verifyChecksums()) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (DependencyArtifact artifact : artifacts) {
            String fileName = buildJarFileName(artifact);
            if (!isSha256(checksums.get(fileName))) {
                missing.add(fileName);
            }
        }
        if (!missing.isEmpty()) {
            throw new IOException("Missing or invalid SHA-256 checksums for runtime dependencies: " + missing);
        }
    }

    private static int downloadRequiredArtifacts(Path libDir, List<DependencyArtifact> artifacts,
            Map<String, String> checksums, BootstrapSettings settings, ArtifactDownloader downloader) throws IOException {
        int downloaded = 0;
        for (DependencyArtifact artifact : artifacts) {
            String fileName = buildJarFileName(artifact);
            Path target = libDir.resolve(fileName);
            if (!requiresDownload(target, checksums.get(fileName), settings)) {
                continue;
            }
            downloadAndReplace(artifact, target, checksums.get(fileName), settings.verifyChecksums(), downloader);
            downloaded++;
            LOGGER.log(System.Logger.Level.INFO, "[NoRule] Downloaded dependency: " + fileName);
        }
        return downloaded;
    }

    private static boolean requiresDownload(Path target, String expectedChecksum, BootstrapSettings settings)
            throws IOException {
        if (settings.forceRedownload() || !Files.isRegularFile(target)) {
            return true;
        }
        return settings.verifyChecksums() && !expectedChecksum.equals(sha256(target));
    }

    private static void downloadAndReplace(DependencyArtifact artifact, Path target, String expectedChecksum,
            boolean verifyChecksum, ArtifactDownloader downloader) throws IOException {
        Path tempFile = target.resolveSibling(target.getFileName().toString() + ".part");
        Files.deleteIfExists(tempFile);
        try {
            downloader.download(artifact, tempFile);
            if (!Files.isRegularFile(tempFile)) {
                throw new IOException("Dependency downloader did not create a file for " + artifact);
            }
            if (verifyChecksum) {
                String actualChecksum = sha256(tempFile);
                if (!expectedChecksum.equals(actualChecksum)) {
                    throw new IOException("SHA-256 mismatch for downloaded dependency " + buildJarFileName(artifact)
                            + " (expected=" + expectedChecksum + ", actual=" + actualChecksum + ")");
                }
            }
            moveAtomically(tempFile, target);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            throw new IOException("Atomic replacement is not supported for runtime dependency: " + target, e);
        }
    }

    private static int cleanupRuntimeJars(Path libDir, List<DependencyArtifact> artifacts, boolean cleanupObsolete)
            throws IOException {
        Set<String> expectedNames = new HashSet<>();
        for (DependencyArtifact artifact : artifacts) {
            expectedNames.add(normalizeFileName(buildJarFileName(artifact)));
        }

        int deleted = 0;
        try (var entries = Files.list(libDir)) {
            for (Path entry : entries.toList()) {
                String fileName = entry.getFileName().toString();
                if (!Files.isRegularFile(entry) || !isJarFileName(fileName)) {
                    continue;
                }
                boolean conflictingProvider = isConflictingProviderJar(fileName);
                boolean obsoleteLogback = isObsoleteLogbackJar(fileName, expectedNames);
                boolean obsolete = cleanupObsolete && !expectedNames.contains(normalizeFileName(fileName));
                if (conflictingProvider || obsoleteLogback || obsolete) {
                    Files.delete(entry);
                    deleted++;
                    LOGGER.log(System.Logger.Level.INFO, "[NoRule] Deleted obsolete runtime dependency: " + fileName);
                }
            }
        }
        return deleted;
    }

    private static boolean isObsoleteLogbackJar(String fileName, Set<String> expectedNames) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        boolean logbackJar = lowerName.startsWith("logback-classic-") || lowerName.startsWith("logback-core-");
        return logbackJar && !expectedNames.contains(normalizeFileName(fileName));
    }

    private static void verifySynchronizedArtifacts(Path libDir, List<DependencyArtifact> artifacts,
            Map<String, String> checksums, BootstrapSettings settings) throws IOException {
        for (DependencyArtifact artifact : artifacts) {
            String fileName = buildJarFileName(artifact);
            Path target = libDir.resolve(fileName);
            if (!Files.isRegularFile(target)) {
                throw new IOException("Runtime dependency is missing after synchronization: " + fileName);
            }
            if (settings.verifyChecksums() && !checksums.get(fileName).equals(sha256(target))) {
                throw new IOException("Runtime dependency checksum failed after synchronization: " + fileName);
            }
        }
    }

    private static void downloadArtifact(DependencyArtifact artifact, Path destination) throws IOException {
        List<String> attemptedUrls = new ArrayList<>();
        IOException lastException = null;
        for (String repo : REMOTE_REPOSITORIES) {
            String relativePath = toRelativeArtifactPath(artifact);
            String url = trimTrailingSlash(repo) + "/" + relativePath;
            attemptedUrls.add(url);
            try {
                if (downloadFrom(url, destination)) {
                    return;
                }
            } catch (IOException e) {
                lastException = e;
            }
        }
        IOException failure = new IOException("Unable to download artifact " + artifact + " from repositories: " + attemptedUrls);
        if (lastException != null) {
            failure.addSuppressed(lastException);
        }
        throw failure;
    }

    private static boolean downloadFrom(String url, Path destination) throws IOException {
        // S1874: replaced deprecated new URL(String) with URI.create(...).toURL()
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            return false;
        }

        try (InputStream in = connection.getInputStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } finally {
            connection.disconnect();
        }
    }

    private static int relaunchWithLibClasspath(Path launcherJar, Path libDir, String[] appArgs) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(resolveJavaExecutable().toString());
        command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
        if (shouldAppendNativeAccessArg(command)) {
            command.add(ENABLE_NATIVE_ACCESS_ARG);
        }
        command.add("-D" + RELAUNCHED_PROPERTY + "=true");
        command.add("-cp");
        command.add(launcherJar.toAbsolutePath() + java.io.File.pathSeparator + libDir.toAbsolutePath() + java.io.File.separator + "*");
        command.add(Main.class.getName());
        // S3012: replaced manual for-loop copy with Collections.addAll
        Collections.addAll(command, appArgs);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.inheritIO();
        Process child = builder.start();
        return child.waitFor();
    }

    private static boolean shouldAppendNativeAccessArg(List<String> jvmArgs) {
        if (Runtime.version().feature() < 22) {
            return false;
        }
        for (String arg : jvmArgs) {
            if (arg == null) {
                continue;
            }
            if (arg.startsWith("--enable-native-access")) {
                return false;
            }
        }
        return true;
    }

    private static Path resolveJavaExecutable() {
        Path javaHome = Path.of(System.getProperty("java.home"));
        String executable = isWindows() ? "java.exe" : "java";
        return javaHome.resolve("bin").resolve(executable);
    }

    private static Path findLauncherPath() {
        try {
            ProtectionDomain domain = Main.class.getProtectionDomain();
            if (domain == null) {
                return null;
            }
            CodeSource source = domain.getCodeSource();
            if (source == null) {
                return null;
            }
            URI uri = source.getLocation().toURI();
            return Path.of(uri).toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
    }

    private static String toRelativeArtifactPath(DependencyArtifact artifact) {
        String groupPath = artifact.groupId().replace('.', '/');
        String fileName = buildJarFileName(artifact);
        return groupPath + "/" + artifact.artifactId() + "/" + artifact.version() + "/" + fileName;
    }

    private static String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String stripAnsi(String value) {
        return value
                .replaceAll("\\u001B\\[[;\\d]*m", "")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not supported by this Java runtime", e);
        }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return toHex(digest.digest());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            value.append(Character.forDigit((current >>> 4) & 0x0f, 16));
            value.append(Character.forDigit(current & 0x0f, 16));
        }
        return value.toString();
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    private static boolean isJarFileName(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static boolean isConflictingProviderArtifact(String artifactId) {
        return CONFLICTING_SLF4J_PROVIDERS.contains(artifactId.toLowerCase(Locale.ROOT));
    }

    private static boolean isConflictingProviderJar(String fileName) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        for (String artifactId : CONFLICTING_SLF4J_PROVIDERS) {
            if ((lowerName.equals(artifactId + ".jar") || lowerName.startsWith(artifactId + "-"))
                    && lowerName.endsWith(".jar")) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeFileName(String fileName) {
        return isWindows() ? fileName.toLowerCase(Locale.ROOT) : fileName;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase().contains("win");
    }

    record DependencyArtifact(String groupId, String artifactId, String version, String classifier) {
    }

    record BootstrapSettings(boolean cleanupObsolete, boolean verifyChecksums, boolean forceRedownload) {
        static BootstrapSettings fromSystemProperties() {
            return new BootstrapSettings(
                    readBooleanProperty(CLEANUP_OBSOLETE_PROPERTY, true),
                    readBooleanProperty(VERIFY_CHECKSUMS_PROPERTY, true),
                    readBooleanProperty(FORCE_REDOWNLOAD_PROPERTY, false));
        }

        private static boolean readBooleanProperty(String propertyName, boolean defaultValue) {
            String value = System.getProperty(propertyName);
            if (value == null) {
                return defaultValue;
            }
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                return Boolean.parseBoolean(value);
            }
            throw new IllegalStateException("Invalid boolean JVM property " + propertyName + ": " + value);
        }
    }

    @FunctionalInterface
    interface ArtifactDownloader {
        void download(DependencyArtifact artifact, Path destination) throws IOException;
    }
}
