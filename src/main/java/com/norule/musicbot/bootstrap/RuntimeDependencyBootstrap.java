package com.norule.musicbot.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
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

final class RuntimeDependencyBootstrap {
    static final String RELAUNCHED_PROPERTY = "norule.bootstrap.relaunched";
    static final String DEPENDENCY_MANIFEST_RESOURCE = "/bootstrap/runtime-dependencies.txt";
    static final String CLEANUP_OBSOLETE_PROPERTY = "norule.bootstrap.cleanup-obsolete";
    static final String VERIFY_CHECKSUMS_PROPERTY = "norule.bootstrap.verify-checksums";
    static final String FORCE_REDOWNLOAD_PROPERTY = "norule.bootstrap.force-redownload";
    static final String PROGRESS_ENABLED_PROPERTY = "norule.bootstrap.progress-enabled";
    static final String PROGRESS_INTERVAL_PROPERTY = "norule.bootstrap.progress-interval-ms";
    static final String CONNECT_TIMEOUT_PROPERTY = "norule.bootstrap.connect-timeout-ms";
    static final String READ_TIMEOUT_PROPERTY = "norule.bootstrap.read-timeout-ms";
    static final String STALL_TIMEOUT_PROPERTY = "norule.bootstrap.stall-timeout-ms";
    static final String MAX_RETRIES_PROPERTY = "norule.bootstrap.max-retries";
    static final String RUNTIME_LIB_DIRECTORY = "runtime-libs";
    private static final String ENABLE_NATIVE_ACCESS_ARG = "--enable-native-access=ALL-UNNAMED";
    private static final String SHA_256 = "SHA-256";
    private static final System.Logger LOGGER = System.getLogger(RuntimeDependencyBootstrap.class.getName());

    private static final Set<String> CONFLICTING_SLF4J_PROVIDERS = Set.of(
            "slf4j-simple",
            "log4j-slf4j2-impl",
            "slf4j-reload4j"
    );

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

        List<DependencyArtifact> artifacts = loadRuntimeDependencyManifest();
        if (artifacts.isEmpty()) {
            LOGGER.log(System.Logger.Level.INFO, "[NoRule] Runtime dependency manifest is empty, skip lib bootstrap.");
            return;
        }

        Path workingDir = Path.of(".").toAbsolutePath().normalize();
        Path libDir = workingDir.resolve(RUNTIME_LIB_DIRECTORY);
        try {
            BootstrapSettings settings = BootstrapSettings.fromConfig(workingDir.resolve("config.yml"));
            synchronizeRuntimeDependencies(libDir, artifacts, settings,
                    new RuntimeDependencyDownloader(settings,
                            message -> LOGGER.log(System.Logger.Level.INFO, message)));
            int exitCode = relaunchWithLibClasspath(launcherPath, libDir, args);
            System.exit(exitCode);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to synchronize runtime dependencies in ./" + RUNTIME_LIB_DIRECTORY, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while relaunching with lib classpath", e);
        }
    }

    static List<DependencyArtifact> parseManifestLines(List<String> lines) {
        Map<String, DependencyArtifact> unique = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            DependencyArtifact artifact = parseManifestLine(line.trim());
            String fileName = buildJarFileName(artifact);
            if (unique.putIfAbsent(fileName, artifact) != null) {
                throw new IllegalArgumentException("Duplicate runtime dependency target file: " + fileName);
            }
        }
        return new ArrayList<>(unique.values());
    }

    static String buildJarFileName(DependencyArtifact artifact) {
        if (artifact.classifier() == null || artifact.classifier().isBlank()) {
            return artifact.artifactId() + "-" + artifact.version() + "." + artifact.extension();
        }
        return artifact.artifactId() + "-" + artifact.version() + "-" + artifact.classifier()
                + "." + artifact.extension();
    }

    private static DependencyArtifact parseManifestLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 7) {
            throw new IllegalArgumentException("Invalid runtime dependency manifest entry; expected repository and "
                    + "SHA-256 fields: " + line);
        }
        String groupId = requireManifestValue(parts[0], "groupId", line);
        String artifactId = requireManifestValue(parts[1], "artifactId", line);
        String version = requireManifestValue(parts[2], "version", line);
        String classifier = parts[3].trim();
        String extension = requireManifestValue(parts[4], "extension", line).toLowerCase(Locale.ROOT);
        if (!"jar".equals(extension)) {
            throw new IllegalArgumentException("Unsupported runtime dependency extension '" + extension + "': "
                    + line);
        }
        RuntimeRepository repository = RuntimeRepository.fromId(requireManifestValue(parts[5], "repository", line));
        String sha256 = requireManifestValue(parts[6], "sha256", line).toLowerCase(Locale.ROOT);
        if (!isSha256(sha256)) {
            throw new IllegalArgumentException("Invalid SHA-256 in runtime dependency manifest: " + line);
        }
        return new DependencyArtifact(groupId, artifactId, version, classifier, extension, repository, sha256);
    }

    private static String requireManifestValue(String value, String field, String line) {
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Missing " + field + " in runtime dependency manifest: " + line);
        }
        return trimmed;
    }

    private static List<DependencyArtifact> loadRuntimeDependencyManifest() {
        InputStream input = RuntimeDependencyBootstrap.class.getResourceAsStream(DEPENDENCY_MANIFEST_RESOURCE);
        if (input == null) {
            return List.of();
        }
        try (input) {
            List<String> lines = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            return parseManifestLines(lines);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read runtime dependency manifest: "
                    + DEPENDENCY_MANIFEST_RESOURCE, e);
        }
    }

    static void synchronizeRuntimeDependencies(Path libDir, List<DependencyArtifact> artifacts,
            BootstrapSettings settings, ArtifactDownloader downloader) throws IOException {
        synchronizeRuntimeDependencies(libDir, artifacts, settings,
                (artifact, destination, index, total) -> downloader.download(artifact, destination));
    }

    static void synchronizeRuntimeDependencies(Path libDir, List<DependencyArtifact> artifacts,
            BootstrapSettings settings, ProgressArtifactDownloader downloader) throws IOException {
        List<DependencyArtifact> activeArtifacts = removeConflictingProviders(artifacts);
        validateLogbackVersions(activeArtifacts);
        validateChecksums(activeArtifacts, settings);
        Files.createDirectories(libDir);

        SyncStats stats = new SyncStats(activeArtifacts.size(), System.nanoTime());
        LOGGER.log(System.Logger.Level.INFO,
                "[NoRule] Preparing runtime dependencies: total=" + activeArtifacts.size());
        try {
            downloadRequiredArtifacts(libDir, activeArtifacts, settings, downloader, stats);
            stats.removed = cleanupRuntimeJars(libDir, activeArtifacts, settings.cleanupObsolete());
            verifySynchronizedArtifacts(libDir, activeArtifacts, settings);
        } catch (IOException e) {
            logSummary(stats);
            throw e;
        }
        logSummary(stats);
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

    private static void validateChecksums(List<DependencyArtifact> artifacts, BootstrapSettings settings)
            throws IOException {
        if (!settings.verifyChecksums()) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (DependencyArtifact artifact : artifacts) {
            String fileName = buildJarFileName(artifact);
            if (!isSha256(artifact.sha256())) {
                missing.add(fileName);
            }
        }
        if (!missing.isEmpty()) {
            throw new IOException("Missing or invalid SHA-256 checksums for runtime dependencies: " + missing);
        }
    }

    private static void downloadRequiredArtifacts(Path libDir, List<DependencyArtifact> artifacts,
            BootstrapSettings settings, ProgressArtifactDownloader downloader, SyncStats stats) throws IOException {
        for (int artifactIndex = 0; artifactIndex < artifacts.size(); artifactIndex++) {
            DependencyArtifact artifact = artifacts.get(artifactIndex);
            int displayIndex = artifactIndex + 1;
            String fileName = buildJarFileName(artifact);
            Path target = libDir.resolve(fileName);
            if (!requiresDownload(target, artifact.sha256(), settings)) {
                Files.deleteIfExists(partFile(target));
                stats.reused++;
                continue;
            }
            logReplacementIfNeeded(target, artifact, settings);
            long startedAt = System.nanoTime();
            try {
                long size = downloadAndReplace(artifact, target, artifact.sha256(), settings.verifyChecksums(),
                        downloader, displayIndex, artifacts.size());
                stats.downloaded++;
                stats.totalBytes += size;
                LOGGER.log(System.Logger.Level.INFO, String.format(Locale.ROOT,
                        "[NoRule] Downloaded %d/%d: %s repository=%s size=%.1f MB duration=%.1fs",
                        displayIndex, artifacts.size(), fileName, artifact.repository().id(), bytesToMegabytes(size),
                        elapsedSeconds(startedAt, System.nanoTime())));
            } catch (IOException e) {
                stats.failed++;
                throw e;
            }
        }
    }

    private static void logReplacementIfNeeded(Path target, DependencyArtifact artifact, BootstrapSettings settings)
            throws IOException {
        if (!settings.verifyChecksums() || !Files.isRegularFile(target)
                || artifact.sha256().equals(sha256(target))) {
            return;
        }
        LOGGER.log(System.Logger.Level.INFO,
                "[NoRule] Replacing runtime dependency with " + artifact.repository().id() + " version: "
                        + target.getFileName());
    }

    private static boolean requiresDownload(Path target, String expectedChecksum, BootstrapSettings settings)
            throws IOException {
        if (settings.forceRedownload() || !Files.isRegularFile(target)) {
            return true;
        }
        return settings.verifyChecksums() && !expectedChecksum.equals(sha256(target));
    }

    private static long downloadAndReplace(DependencyArtifact artifact, Path target, String expectedChecksum,
            boolean verifyChecksum, ProgressArtifactDownloader downloader, int index, int total) throws IOException {
        Path tempFile = partFile(target);
        Files.deleteIfExists(tempFile);
        try {
            downloader.download(artifact, tempFile, index, total);
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
            return Files.size(target);
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

    private static Path partFile(Path target) {
        return target.resolveSibling(target.getFileName().toString() + ".part");
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
            BootstrapSettings settings) throws IOException {
        for (DependencyArtifact artifact : artifacts) {
            String fileName = buildJarFileName(artifact);
            Path target = libDir.resolve(fileName);
            if (!Files.isRegularFile(target)) {
                throw new IOException("Runtime dependency is missing after synchronization: " + fileName);
            }
            if (settings.verifyChecksums() && !artifact.sha256().equals(sha256(target))) {
                throw new IOException("Runtime dependency checksum failed after synchronization: " + fileName);
            }
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

    static String toRelativeArtifactPath(DependencyArtifact artifact) {
        String groupPath = artifact.groupId().replace('.', '/');
        String fileName = buildJarFileName(artifact);
        return groupPath + "/" + artifact.artifactId() + "/" + artifact.version() + "/" + fileName;
    }

    static String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
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

    private static double bytesToMegabytes(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }

    private static double elapsedSeconds(long startedAtNanos, long completedAtNanos) {
        return Math.max(0L, completedAtNanos - startedAtNanos) / 1_000_000_000.0;
    }

    private static void logSummary(SyncStats stats) {
        LOGGER.log(System.Logger.Level.INFO, String.format(Locale.ROOT,
                "[NoRule] Runtime dependencies summary: required=%d downloaded=%d reused=%d removed=%d failed=%d "
                        + "totalBytes=%d duration=%.1fs",
                stats.required, stats.downloaded, stats.reused, stats.removed, stats.failed, stats.totalBytes,
                elapsedSeconds(stats.startedAtNanos, System.nanoTime())));
    }

    record DependencyArtifact(String groupId, String artifactId, String version, String classifier, String extension,
            RuntimeRepository repository, String sha256) {
    }

    record BootstrapSettings(boolean cleanupObsolete, boolean verifyChecksums, boolean forceRedownload,
            boolean progressEnabled, int progressIntervalMs, int connectTimeoutMs, int readTimeoutMs,
            int stallTimeoutMs, int maxRetries) {
        private static final boolean DEFAULT_PROGRESS_ENABLED = true;
        private static final int DEFAULT_PROGRESS_INTERVAL_MS = 2_000;
        private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
        private static final int DEFAULT_READ_TIMEOUT_MS = 60_000;
        private static final int DEFAULT_STALL_TIMEOUT_MS = 15_000;
        private static final int DEFAULT_MAX_RETRIES = 3;

        BootstrapSettings(boolean cleanupObsolete, boolean verifyChecksums, boolean forceRedownload) {
            this(cleanupObsolete, verifyChecksums, forceRedownload, DEFAULT_PROGRESS_ENABLED,
                    DEFAULT_PROGRESS_INTERVAL_MS, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS,
                    DEFAULT_STALL_TIMEOUT_MS, DEFAULT_MAX_RETRIES);
        }

        BootstrapSettings {
            requirePositive("progress-interval-ms", progressIntervalMs);
            requirePositive("connect-timeout-ms", connectTimeoutMs);
            requirePositive("read-timeout-ms", readTimeoutMs);
            requirePositive("stall-timeout-ms", stallTimeoutMs);
            if (maxRetries < 0) {
                throw new IllegalStateException("runtime-dependencies.max-retries must be zero or greater");
            }
        }

        static BootstrapSettings fromSystemProperties() {
            return fromValues(Map.of());
        }

        static BootstrapSettings fromConfig(Path configPath) throws IOException {
            Map<String, String> values = Files.isRegularFile(configPath)
                    ? parseRuntimeDependencySettings(Files.readAllLines(configPath, StandardCharsets.UTF_8))
                    : Map.of();
            return fromValues(values);
        }

        private static BootstrapSettings fromValues(Map<String, String> values) {
            return new BootstrapSettings(
                    readBooleanProperty(CLEANUP_OBSOLETE_PROPERTY, true),
                    readBooleanProperty(VERIFY_CHECKSUMS_PROPERTY, true),
                    readBooleanProperty(FORCE_REDOWNLOAD_PROPERTY, false),
                    readBooleanSetting(values, "progress-enabled", PROGRESS_ENABLED_PROPERTY,
                            DEFAULT_PROGRESS_ENABLED),
                    readIntSetting(values, "progress-interval-ms", PROGRESS_INTERVAL_PROPERTY,
                            DEFAULT_PROGRESS_INTERVAL_MS),
                    readIntSetting(values, "connect-timeout-ms", CONNECT_TIMEOUT_PROPERTY,
                            DEFAULT_CONNECT_TIMEOUT_MS),
                    readIntSetting(values, "read-timeout-ms", READ_TIMEOUT_PROPERTY, DEFAULT_READ_TIMEOUT_MS),
                    readIntSetting(values, "stall-timeout-ms", STALL_TIMEOUT_PROPERTY, DEFAULT_STALL_TIMEOUT_MS),
                    readIntSetting(values, "max-retries", MAX_RETRIES_PROPERTY, DEFAULT_MAX_RETRIES));
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

        private static boolean readBooleanSetting(Map<String, String> values, String key, String propertyName,
                boolean defaultValue) {
            String value = configuredValue(values, key, propertyName);
            if (value == null) {
                return defaultValue;
            }
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                return Boolean.parseBoolean(value);
            }
            throw new IllegalStateException("Invalid boolean runtime dependency setting " + key + ": " + value);
        }

        private static int readIntSetting(Map<String, String> values, String key, String propertyName,
                int defaultValue) {
            String value = configuredValue(values, key, propertyName);
            if (value == null) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid integer runtime dependency setting " + key + ": " + value,
                        e);
            }
        }

        private static String configuredValue(Map<String, String> values, String key, String propertyName) {
            String propertyValue = System.getProperty(propertyName);
            return propertyValue == null ? values.get(key) : propertyValue;
        }

        private static void requirePositive(String key, int value) {
            if (value <= 0) {
                throw new IllegalStateException("runtime-dependencies." + key + " must be greater than zero");
            }
        }
    }

    static Map<String, String> parseRuntimeDependencySettings(List<String> lines) {
        Map<String, String> values = new LinkedHashMap<>();
        boolean inSection = false;
        int sectionIndent = -1;
        for (String line : lines) {
            if (line == null || line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            int indent = leadingSpaces(line);
            String trimmed = line.trim();
            if (!inSection) {
                if (indent == 0 && trimmed.matches("runtime-dependencies\\s*:\\s*(?:#.*)?")) {
                    inSection = true;
                    sectionIndent = indent;
                }
                continue;
            }
            if (indent <= sectionIndent) {
                break;
            }
            int separator = trimmed.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = trimmed.substring(0, separator).trim();
            String value = stripInlineComment(trimmed.substring(separator + 1)).trim();
            if (!key.isBlank() && !value.isBlank()) {
                values.put(key, unquote(value));
            }
        }
        return values;
    }

    private static int leadingSpaces(String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String stripInlineComment(String value) {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
            } else if (current == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
            } else if (current == '#' && !singleQuoted && !doubleQuoted) {
                return value.substring(0, index);
            }
        }
        return value;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static final class SyncStats {
        private final int required;
        private final long startedAtNanos;
        private int downloaded;
        private int reused;
        private int removed;
        private int failed;
        private long totalBytes;

        private SyncStats(int required, long startedAtNanos) {
            this.required = required;
            this.startedAtNanos = startedAtNanos;
        }
    }

    @FunctionalInterface
    interface ArtifactDownloader {
        void download(DependencyArtifact artifact, Path destination) throws IOException;
    }

    @FunctionalInterface
    interface ProgressArtifactDownloader {
        void download(DependencyArtifact artifact, Path destination, int index, int total) throws IOException;
    }
}
