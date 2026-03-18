package com.norule.musicbot;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class BotConfig {
    private final String token;
    private final String prefix;
    private final Long commandGuildId;
    private final String guildSettingsDir;
    private final String languageDir;
    private final String defaultLanguage;
    private final int commandCooldownSeconds;
    private final BotProfile botProfile;
    private final Map<String, String> commandDescriptions;
    private final Notifications notifications;
    private final MessageLogs messageLogs;
    private final Music music;
    private final PrivateRoom privateRoom;

    private BotConfig(String token,
                      String prefix,
                      Long commandGuildId,
                      String guildSettingsDir,
                      String languageDir,
                      String defaultLanguage,
                      int commandCooldownSeconds,
                      BotProfile botProfile,
                      Map<String, String> commandDescriptions,
                      Notifications notifications,
                      MessageLogs messageLogs,
                      Music music,
                      PrivateRoom privateRoom) {
        this.token = token;
        this.prefix = prefix;
        this.commandGuildId = commandGuildId;
        this.guildSettingsDir = guildSettingsDir;
        this.languageDir = languageDir;
        this.defaultLanguage = defaultLanguage;
        this.commandCooldownSeconds = Math.max(0, commandCooldownSeconds);
        this.botProfile = botProfile;
        this.commandDescriptions = commandDescriptions;
        this.notifications = notifications;
        this.messageLogs = messageLogs;
        this.music = music;
        this.privateRoom = privateRoom;
    }

    public static BotConfig load(Path path) {
        initializeConfigAndLang(path);
        if (!Files.exists(path)) {
            throw new IllegalStateException("config.yml not found: " + path.toAbsolutePath());
        }

        try (InputStream inputStream = Files.newInputStream(path)) {
            Yaml yaml = new Yaml();
            Object rootObject = yaml.load(inputStream);
            Map<String, Object> root = asMap(rootObject);

            String tokenFromConfig = getString(root, "token", "");
            String tokenFromEnv = System.getenv("DISCORD_TOKEN");
            String token = !tokenFromConfig.isBlank() ? tokenFromConfig : nullToEmpty(tokenFromEnv);
            if (token.isBlank()) {
                throw new IllegalStateException("Token missing. Set token in config.yml or DISCORD_TOKEN.");
            }

            String prefix = getString(root, "prefix", "!");
            Long commandGuildId = toLong(root.get("commandGuildId"));
            String guildSettingsDir = getString(root, "guildSettingsDir", "guild-configs");
            String languageDir = getString(root, "languageDir", "lang");
            String defaultLanguage = getString(root, "defaultLanguage", "en");
            int commandCooldownSeconds = Math.max(0, getInt(root, "commandCooldownSeconds", 3));
            BotProfile botProfile = BotProfile.fromMap(asMap(root.get("bot")), null);
            Map<String, String> commandDescriptions = resolveCommandDescriptions(asMap(root.get("commandDescriptions")));
            Notifications notifications = Notifications.fromMap(asMap(root.get("notifications")), null);
            MessageLogs messageLogs = MessageLogs.fromMap(asMap(root.get("messageLogs")), null);
            Music music = Music.fromMap(asMap(root.get("music")), null);
            PrivateRoom privateRoom = PrivateRoom.fromMap(asMap(root.get("privateRoom")), null);

            return new BotConfig(token, prefix, commandGuildId, guildSettingsDir, languageDir, defaultLanguage, commandCooldownSeconds, botProfile,
                    commandDescriptions,
                    notifications, messageLogs, music, privateRoom);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read config.yml: " + path.toAbsolutePath(), e);
        }
    }

    private static void initializeConfigAndLang(Path configPath) {
        Map<String, Object> defaultConfig = readDefaultConfigMap();
        if (defaultConfig.isEmpty()) {
            return;
        }

        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            if (!Files.exists(configPath)) {
                writeYaml(configPath, defaultConfig);
            }

            Map<String, Object> currentConfig = readYamlMap(configPath);
            if (currentConfig == null) {
                return;
            }
            if (currentConfig.isEmpty()) {
                currentConfig = new LinkedHashMap<>();
            }

            String languageDir = getString(currentConfig, "languageDir", getString(defaultConfig, "languageDir", "lang"));
            Path baseDir = parent == null ? Path.of(".") : parent;
            ensureDefaultLanguageFiles(baseDir.resolve(languageDir));
            mergeLanguageDefaults(baseDir.resolve(languageDir));

            Map<String, Object> merged = deepMerge(defaultConfig, currentConfig);
            if (!merged.equals(currentConfig)) {
                backupConfig(configPath);
                writeYaml(configPath, merged);
            }
        } catch (Exception ignored) {
        }
    }

    private static Map<String, Object> readDefaultConfigMap() {
        try (InputStream in = BotConfig.class.getClassLoader().getResourceAsStream("defaults/config.yml")) {
            if (in == null) {
                return Map.of();
            }
            Object obj = new Yaml().load(in);
            return asMap(obj);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static Map<String, Object> readYamlMap(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Object obj = new Yaml().load(reader);
            return asMap(obj);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void ensureDefaultLanguageFiles(Path languageDir) {
        try {
            Files.createDirectories(languageDir);
            writeResourceIfMissing("defaults/lang/zh-TW.yml", languageDir.resolve("zh-TW.yml"));
            writeResourceIfMissing("defaults/lang/en.yml", languageDir.resolve("en.yml"));
        } catch (Exception ignored) {
        }
    }

    private static void mergeLanguageDefaults(Path languageDir) {
        try {
            mergeLanguageFile(languageDir.resolve("zh-TW.yml"), "defaults/lang/zh-TW.yml");
            mergeLanguageFile(languageDir.resolve("en.yml"), "defaults/lang/en.yml");
        } catch (Exception ignored) {
        }
    }

    private static void mergeLanguageFile(Path targetFile, String resourcePath) {
        Map<String, Object> defaults = readYamlResourceMap(resourcePath);
        if (defaults.isEmpty()) {
            return;
        }
        Map<String, Object> existing = readYamlMap(targetFile);
        if (existing == null || existing.isEmpty()) {
            writeQuietly(targetFile, defaults);
            return;
        }
        Map<String, Object> merged = deepMerge(defaults, existing);
        if (!merged.equals(existing)) {
            writeQuietly(targetFile, merged);
        }
    }

    private static Map<String, Object> readYamlResourceMap(String resourcePath) {
        try (InputStream in = BotConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return Map.of();
            }
            Object obj = new Yaml().load(in);
            return asMap(obj);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static void writeQuietly(Path file, Map<String, Object> root) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            writeYaml(file, root);
        } catch (Exception ignored) {
        }
    }

    private static void writeResourceIfMissing(String resourcePath, Path target) {
        if (Files.exists(target)) {
            return;
        }
        try (InputStream in = BotConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return;
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepMerge(Map<String, Object> defaults, Map<String, Object> existing) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String key = entry.getKey();
            Object defaultValue = entry.getValue();
            if (!existing.containsKey(key)) {
                result.put(key, defaultValue);
                continue;
            }
            Object existingValue = existing.get(key);
            if (defaultValue instanceof Map<?, ?> defaultMap && existingValue instanceof Map<?, ?> existingMap) {
                result.put(key, deepMerge((Map<String, Object>) defaultMap, (Map<String, Object>) existingMap));
            } else {
                result.put(key, existingValue);
            }
        }
        for (Map.Entry<String, Object> entry : existing.entrySet()) {
            if (!result.containsKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static void backupConfig(Path configPath) {
        if (!Files.exists(configPath)) {
            return;
        }
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String fileName = configPath.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String base = dot > 0 ? fileName.substring(0, dot) : fileName;
            String ext = dot > 0 ? fileName.substring(dot) : "";
            Path backup = configPath.resolveSibling(base + ".backup-" + timestamp + ext);
            Files.copy(configPath, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    private static void writeYaml(Path file, Map<String, Object> root) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        Yaml yaml = new Yaml(options);
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
            yaml.dump(root, writer);
        }
    }

    public String getToken() {
        return token;
    }

    public String getPrefix() {
        return prefix;
    }

    public Long getCommandGuildId() {
        return commandGuildId;
    }

    public String getGuildSettingsDir() {
        return guildSettingsDir;
    }

    public String getLanguageDir() {
        return languageDir;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public int getCommandCooldownSeconds() {
        return commandCooldownSeconds;
    }

    public BotProfile getBotProfile() {
        return botProfile;
    }

    public String getCommandDescription(String key, String fallback) {
        String value = commandDescriptions.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    public Notifications getNotifications() {
        return notifications;
    }

    public MessageLogs getMessageLogs() {
        return messageLogs;
    }

    public Music getMusic() {
        return music;
    }

    public PrivateRoom getPrivateRoom() {
        return privateRoom;
    }

    public static class Notifications {
        private final boolean enabled;
        private final boolean memberJoinEnabled;
        private final boolean memberLeaveEnabled;
        private final boolean voiceLogEnabled;
        private final Long memberChannelId;
        private final String memberJoinMessage;
        private final String memberLeaveMessage;
        private final int memberJoinColor;
        private final int memberLeaveColor;
        private final Long voiceChannelId;
        private final String voiceJoinMessage;
        private final String voiceLeaveMessage;
        private final String voiceMoveMessage;

        private Notifications(boolean enabled,
                              boolean memberJoinEnabled,
                              boolean memberLeaveEnabled,
                              boolean voiceLogEnabled,
                              Long memberChannelId,
                              String memberJoinMessage,
                              String memberLeaveMessage,
                              int memberJoinColor,
                              int memberLeaveColor,
                              Long voiceChannelId,
                              String voiceJoinMessage,
                              String voiceLeaveMessage,
                              String voiceMoveMessage) {
            this.enabled = enabled;
            this.memberJoinEnabled = memberJoinEnabled;
            this.memberLeaveEnabled = memberLeaveEnabled;
            this.voiceLogEnabled = voiceLogEnabled;
            this.memberChannelId = memberChannelId;
            this.memberJoinMessage = memberJoinMessage;
            this.memberLeaveMessage = memberLeaveMessage;
            this.memberJoinColor = memberJoinColor;
            this.memberLeaveColor = memberLeaveColor;
            this.voiceChannelId = voiceChannelId;
            this.voiceJoinMessage = voiceJoinMessage;
            this.voiceLeaveMessage = voiceLeaveMessage;
            this.voiceMoveMessage = voiceMoveMessage;
        }

        public static Notifications fromMap(Map<String, Object> map, Notifications fallback) {
            Notifications defaults = fallback == null ? defaultValues() : fallback;
            return new Notifications(
                    getBoolean(map, "enabled", defaults.isEnabled()),
                    getBoolean(map, "memberJoinEnabled", defaults.isMemberJoinEnabled()),
                    getBoolean(map, "memberLeaveEnabled", defaults.isMemberLeaveEnabled()),
                    getBoolean(map, "voiceLogEnabled", defaults.isVoiceLogEnabled()),
                    getLong(map, "memberChannelId", defaults.getMemberChannelId()),
                    getString(map, "memberJoinMessage", defaults.getMemberJoinMessage()),
                    getString(map, "memberLeaveMessage", defaults.getMemberLeaveMessage()),
                    getColor(map, "memberJoinColor", defaults.getMemberJoinColor()),
                    getColor(map, "memberLeaveColor", defaults.getMemberLeaveColor()),
                    getLong(map, "voiceChannelId", defaults.getVoiceChannelId()),
                    getString(map, "voiceJoinMessage", defaults.getVoiceJoinMessage()),
                    getString(map, "voiceLeaveMessage", defaults.getVoiceLeaveMessage()),
                    getString(map, "voiceMoveMessage", defaults.getVoiceMoveMessage())
            );
        }

        public static Notifications defaultValues() {
            return new Notifications(
                    true,
                    true,
                    true,
                    true,
                    null,
                    "{user} joined the server. Account created: {createdAt} ({accountAgeDays} days ago). ID: {id}",
                    "{user} left the server. Account created: {createdAt} ({accountAgeDays} days ago). ID: {id}",
                    0x2ECC71,
                    0xE74C3C,
                    null,
                    "{user} joined voice channel {channel}.",
                    "{user} left voice channel {channel}.",
                    "{user} moved voice channel from {from} to {to}."
            );
        }

        public Notifications withEnabled(boolean enabled) {
            return new Notifications(enabled, memberJoinEnabled, memberLeaveEnabled, voiceLogEnabled, memberChannelId, memberJoinMessage, memberLeaveMessage, memberJoinColor, memberLeaveColor, voiceChannelId,
                    voiceJoinMessage, voiceLeaveMessage, voiceMoveMessage);
        }

        public Notifications withMemberJoinEnabled(boolean memberJoinEnabled) {
            return new Notifications(enabled, memberJoinEnabled, memberLeaveEnabled, voiceLogEnabled, memberChannelId, memberJoinMessage, memberLeaveMessage, memberJoinColor, memberLeaveColor, voiceChannelId,
                    voiceJoinMessage, voiceLeaveMessage, voiceMoveMessage);
        }

        public Notifications withMemberLeaveEnabled(boolean memberLeaveEnabled) {
            return new Notifications(enabled, memberJoinEnabled, memberLeaveEnabled, voiceLogEnabled, memberChannelId, memberJoinMessage, memberLeaveMessage, memberJoinColor, memberLeaveColor, voiceChannelId,
                    voiceJoinMessage, voiceLeaveMessage, voiceMoveMessage);
        }

        public Notifications withVoiceLogEnabled(boolean voiceLogEnabled) {
            return new Notifications(enabled, memberJoinEnabled, memberLeaveEnabled, voiceLogEnabled, memberChannelId, memberJoinMessage, memberLeaveMessage, memberJoinColor, memberLeaveColor, voiceChannelId,
                    voiceJoinMessage, voiceLeaveMessage, voiceMoveMessage);
        }

        public Notifications withMemberChannelId(Long memberChannelId) {
            return new Notifications(enabled, memberJoinEnabled, memberLeaveEnabled, voiceLogEnabled, memberChannelId, memberJoinMessage, memberLeaveMessage, memberJoinColor, memberLeaveColor, voiceChannelId,
                    voiceJoinMessage, voiceLeaveMessage, voiceMoveMessage);
        }

        public Notifications withVoiceChannelId(Long voiceChannelId) {
            return new Notifications(enabled, memberJoinEnabled, memberLeaveEnabled, voiceLogEnabled, memberChannelId, memberJoinMessage, memberLeaveMessage, memberJoinColor, memberLeaveColor, voiceChannelId,
                    voiceJoinMessage, voiceLeaveMessage, voiceMoveMessage);
        }

        public Notifications withMemberJoinMessage(String memberJoinMessage) {
            return new Notifications(enabled, memberJoinEnabled, memberLeaveEnabled, voiceLogEnabled, memberChannelId, memberJoinMessage, memberLeaveMessage, memberJoinColor, memberLeaveColor, voiceChannelId,
                    voiceJoinMessage, voiceLeaveMessage, voiceMoveMessage);
        }

        public Notifications withMemberLeaveMessage(String memberLeaveMessage) {
            return new Notifications(enabled, memberJoinEnabled, memberLeaveEnabled, voiceLogEnabled, memberChannelId, memberJoinMessage, memberLeaveMessage, memberJoinColor, memberLeaveColor, voiceChannelId,
                    voiceJoinMessage, voiceLeaveMessage, voiceMoveMessage);
        }

        public Notifications withVoiceJoinMessage(String voiceJoinMessage) {
            return new Notifications(enabled, memberJoinEnabled, memberLeaveEnabled, voiceLogEnabled, memberChannelId, memberJoinMessage, memberLeaveMessage, memberJoinColor, memberLeaveColor, voiceChannelId,
                    voiceJoinMessage, voiceLeaveMessage, voiceMoveMessage);
        }

        public Notifications withVoiceLeaveMessage(String voiceLeaveMessage) {
            return new Notifications(enabled, memberJoinEnabled, memberLeaveEnabled, voiceLogEnabled, memberChannelId, memberJoinMessage, memberLeaveMessage, memberJoinColor, memberLeaveColor, voiceChannelId,
                    voiceJoinMessage, voiceLeaveMessage, voiceMoveMessage);
        }

        public Notifications withVoiceMoveMessage(String voiceMoveMessage) {
            return new Notifications(enabled, memberJoinEnabled, memberLeaveEnabled, voiceLogEnabled, memberChannelId, memberJoinMessage, memberLeaveMessage, memberJoinColor, memberLeaveColor, voiceChannelId,
                    voiceJoinMessage, voiceLeaveMessage, voiceMoveMessage);
        }

        public Notifications withMemberJoinColor(int memberJoinColor) {
            return new Notifications(enabled, memberJoinEnabled, memberLeaveEnabled, voiceLogEnabled, memberChannelId, memberJoinMessage, memberLeaveMessage, normalizeColor(memberJoinColor), memberLeaveColor, voiceChannelId,
                    voiceJoinMessage, voiceLeaveMessage, voiceMoveMessage);
        }

        public Notifications withMemberLeaveColor(int memberLeaveColor) {
            return new Notifications(enabled, memberJoinEnabled, memberLeaveEnabled, voiceLogEnabled, memberChannelId, memberJoinMessage, memberLeaveMessage, memberJoinColor, normalizeColor(memberLeaveColor), voiceChannelId,
                    voiceJoinMessage, voiceLeaveMessage, voiceMoveMessage);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isMemberJoinEnabled() {
            return memberJoinEnabled;
        }

        public boolean isMemberLeaveEnabled() {
            return memberLeaveEnabled;
        }

        public boolean isVoiceLogEnabled() {
            return voiceLogEnabled;
        }

        public Long getMemberChannelId() {
            return memberChannelId;
        }

        public String getMemberJoinMessage() {
            return memberJoinMessage;
        }

        public String getMemberLeaveMessage() {
            return memberLeaveMessage;
        }

        public int getMemberJoinColor() {
            return memberJoinColor;
        }

        public int getMemberLeaveColor() {
            return memberLeaveColor;
        }

        public Long getVoiceChannelId() {
            return voiceChannelId;
        }

        public String getVoiceJoinMessage() {
            return voiceJoinMessage;
        }

        public String getVoiceLeaveMessage() {
            return voiceLeaveMessage;
        }

        public String getVoiceMoveMessage() {
            return voiceMoveMessage;
        }

        private static int normalizeColor(int value) {
            return value & 0xFFFFFF;
        }
    }

    public static class MessageLogs {
        private final boolean enabled;
        private final Long channelId;
        private final Long commandUsageChannelId;
        private final Long channelLifecycleChannelId;
        private final Long roleLogChannelId;
        private final Long moderationLogChannelId;
        private final boolean roleLogEnabled;
        private final boolean channelLifecycleLogEnabled;
        private final boolean moderationLogEnabled;
        private final boolean commandUsageLogEnabled;

        private MessageLogs(boolean enabled,
                            Long channelId,
                            Long commandUsageChannelId,
                            Long channelLifecycleChannelId,
                            Long roleLogChannelId,
                            Long moderationLogChannelId,
                            boolean roleLogEnabled,
                            boolean channelLifecycleLogEnabled,
                            boolean moderationLogEnabled,
                            boolean commandUsageLogEnabled) {
            this.enabled = enabled;
            this.channelId = channelId;
            this.commandUsageChannelId = commandUsageChannelId;
            this.channelLifecycleChannelId = channelLifecycleChannelId;
            this.roleLogChannelId = roleLogChannelId;
            this.moderationLogChannelId = moderationLogChannelId;
            this.roleLogEnabled = roleLogEnabled;
            this.channelLifecycleLogEnabled = channelLifecycleLogEnabled;
            this.moderationLogEnabled = moderationLogEnabled;
            this.commandUsageLogEnabled = commandUsageLogEnabled;
        }

        public static MessageLogs fromMap(Map<String, Object> map, MessageLogs fallback) {
            MessageLogs defaults = fallback == null ? defaultValues() : fallback;
            return new MessageLogs(
                    getBoolean(map, "enabled", defaults.isEnabled()),
                    getLong(map, "channelId", defaults.getChannelId()),
                    getLong(map, "commandUsageChannelId", defaults.getCommandUsageChannelId()),
                    getLong(map, "channelLifecycleChannelId", defaults.getChannelLifecycleChannelId()),
                    getLong(map, "roleLogChannelId", defaults.getRoleLogChannelId()),
                    getLong(map, "moderationLogChannelId", defaults.getModerationLogChannelId()),
                    getBoolean(map, "roleLogEnabled", defaults.isRoleLogEnabled()),
                    getBoolean(map, "channelLifecycleLogEnabled", defaults.isChannelLifecycleLogEnabled()),
                    getBoolean(map, "moderationLogEnabled", defaults.isModerationLogEnabled()),
                    getBoolean(map, "commandUsageLogEnabled", defaults.isCommandUsageLogEnabled())
            );
        }

        public static MessageLogs defaultValues() {
            return new MessageLogs(true, null, null, null, null, null, true, true, true, true);
        }

        public MessageLogs withEnabled(boolean enabled) {
            return new MessageLogs(enabled, channelId, commandUsageChannelId, channelLifecycleChannelId, roleLogChannelId, moderationLogChannelId, roleLogEnabled, channelLifecycleLogEnabled, moderationLogEnabled, commandUsageLogEnabled);
        }

        public MessageLogs withChannelId(Long channelId) {
            return new MessageLogs(enabled, channelId, commandUsageChannelId, channelLifecycleChannelId, roleLogChannelId, moderationLogChannelId, roleLogEnabled, channelLifecycleLogEnabled, moderationLogEnabled, commandUsageLogEnabled);
        }

        public MessageLogs withCommandUsageChannelId(Long commandUsageChannelId) {
            return new MessageLogs(enabled, channelId, commandUsageChannelId, channelLifecycleChannelId, roleLogChannelId, moderationLogChannelId, roleLogEnabled, channelLifecycleLogEnabled, moderationLogEnabled, commandUsageLogEnabled);
        }

        public MessageLogs withChannelLifecycleChannelId(Long channelLifecycleChannelId) {
            return new MessageLogs(enabled, channelId, commandUsageChannelId, channelLifecycleChannelId, roleLogChannelId, moderationLogChannelId, roleLogEnabled, channelLifecycleLogEnabled, moderationLogEnabled, commandUsageLogEnabled);
        }

        public MessageLogs withRoleLogChannelId(Long roleLogChannelId) {
            return new MessageLogs(enabled, channelId, commandUsageChannelId, channelLifecycleChannelId, roleLogChannelId, moderationLogChannelId, roleLogEnabled, channelLifecycleLogEnabled, moderationLogEnabled, commandUsageLogEnabled);
        }

        public MessageLogs withModerationLogChannelId(Long moderationLogChannelId) {
            return new MessageLogs(enabled, channelId, commandUsageChannelId, channelLifecycleChannelId, roleLogChannelId, moderationLogChannelId, roleLogEnabled, channelLifecycleLogEnabled, moderationLogEnabled, commandUsageLogEnabled);
        }

        public MessageLogs withRoleLogEnabled(boolean value) {
            return new MessageLogs(enabled, channelId, commandUsageChannelId, channelLifecycleChannelId, roleLogChannelId, moderationLogChannelId, value, channelLifecycleLogEnabled, moderationLogEnabled, commandUsageLogEnabled);
        }

        public MessageLogs withChannelLifecycleLogEnabled(boolean value) {
            return new MessageLogs(enabled, channelId, commandUsageChannelId, channelLifecycleChannelId, roleLogChannelId, moderationLogChannelId, roleLogEnabled, value, moderationLogEnabled, commandUsageLogEnabled);
        }

        public MessageLogs withModerationLogEnabled(boolean value) {
            return new MessageLogs(enabled, channelId, commandUsageChannelId, channelLifecycleChannelId, roleLogChannelId, moderationLogChannelId, roleLogEnabled, channelLifecycleLogEnabled, value, commandUsageLogEnabled);
        }

        public MessageLogs withCommandUsageLogEnabled(boolean value) {
            return new MessageLogs(enabled, channelId, commandUsageChannelId, channelLifecycleChannelId, roleLogChannelId, moderationLogChannelId, roleLogEnabled, channelLifecycleLogEnabled, moderationLogEnabled, value);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public Long getChannelId() {
            return channelId;
        }

        public Long getCommandUsageChannelId() {
            return commandUsageChannelId;
        }

        public Long getChannelLifecycleChannelId() {
            return channelLifecycleChannelId;
        }

        public Long getRoleLogChannelId() {
            return roleLogChannelId;
        }

        public Long getModerationLogChannelId() {
            return moderationLogChannelId;
        }

        public boolean isRoleLogEnabled() {
            return roleLogEnabled;
        }

        public boolean isChannelLifecycleLogEnabled() {
            return channelLifecycleLogEnabled;
        }

        public boolean isModerationLogEnabled() {
            return moderationLogEnabled;
        }

        public boolean isCommandUsageLogEnabled() {
            return commandUsageLogEnabled;
        }
    }

    public static class Music {
        public enum RepeatMode {
            OFF, SINGLE, ALL
        }

        private final boolean autoLeaveEnabled;
        private final int autoLeaveMinutes;
        private final boolean autoplayEnabled;
        private final RepeatMode defaultRepeatMode;
        private final Long commandChannelId;

        private Music(boolean autoLeaveEnabled, int autoLeaveMinutes, boolean autoplayEnabled, RepeatMode defaultRepeatMode, Long commandChannelId) {
            this.autoLeaveEnabled = autoLeaveEnabled;
            this.autoLeaveMinutes = autoLeaveMinutes;
            this.autoplayEnabled = autoplayEnabled;
            this.defaultRepeatMode = defaultRepeatMode;
            this.commandChannelId = commandChannelId;
        }

        public static Music fromMap(Map<String, Object> map, Music fallback) {
            Music defaults = fallback == null ? defaultValues() : fallback;
            return new Music(
                    getBoolean(map, "autoLeaveEnabled", defaults.isAutoLeaveEnabled()),
                    getInt(map, "autoLeaveMinutes", defaults.getAutoLeaveMinutes()),
                    getBoolean(map, "autoplayEnabled", defaults.isAutoplayEnabled()),
                    parseRepeatMode(getString(map, "defaultRepeatMode", defaults.getDefaultRepeatMode().name())),
                    getLong(map, "commandChannelId", defaults.getCommandChannelId())
            );
        }

        public static Music defaultValues() {
            return new Music(true, 5, true, RepeatMode.OFF, null);
        }

        public Music withAutoLeaveEnabled(boolean enabled) {
            return new Music(enabled, autoLeaveMinutes, autoplayEnabled, defaultRepeatMode, commandChannelId);
        }

        public Music withAutoLeaveMinutes(int minutes) {
            return new Music(autoLeaveEnabled, Math.max(1, minutes), autoplayEnabled, defaultRepeatMode, commandChannelId);
        }

        public Music withAutoplayEnabled(boolean enabled) {
            return new Music(autoLeaveEnabled, autoLeaveMinutes, enabled, defaultRepeatMode, commandChannelId);
        }

        public Music withDefaultRepeatMode(RepeatMode mode) {
            return new Music(autoLeaveEnabled, autoLeaveMinutes, autoplayEnabled, mode, commandChannelId);
        }

        public Music withCommandChannelId(Long commandChannelId) {
            return new Music(autoLeaveEnabled, autoLeaveMinutes, autoplayEnabled, defaultRepeatMode, commandChannelId);
        }

        public boolean isAutoLeaveEnabled() {
            return autoLeaveEnabled;
        }

        public int getAutoLeaveMinutes() {
            return autoLeaveMinutes;
        }

        public boolean isAutoplayEnabled() {
            return autoplayEnabled;
        }

        public RepeatMode getDefaultRepeatMode() {
            return defaultRepeatMode;
        }

        public Long getCommandChannelId() {
            return commandChannelId;
        }

        private static RepeatMode parseRepeatMode(String value) {
            try {
                return RepeatMode.valueOf(value.trim().toUpperCase());
            } catch (Exception ignored) {
                return RepeatMode.OFF;
            }
        }
    }

    public static class PrivateRoom {
        private final boolean enabled;
        private final Long triggerVoiceChannelId;
        private final Long categoryId;
        private final int userLimit;

        private PrivateRoom(boolean enabled, Long triggerVoiceChannelId, Long categoryId, int userLimit) {
            this.enabled = enabled;
            this.triggerVoiceChannelId = triggerVoiceChannelId;
            this.categoryId = categoryId;
            this.userLimit = userLimit;
        }

        public static PrivateRoom fromMap(Map<String, Object> map, PrivateRoom fallback) {
            PrivateRoom defaults = fallback == null ? defaultValues() : fallback;
            return new PrivateRoom(
                    getBoolean(map, "enabled", defaults.isEnabled()),
                    getLong(map, "triggerVoiceChannelId", defaults.getTriggerVoiceChannelId()),
                    getLong(map, "categoryId", defaults.getCategoryId()),
                    Math.max(0, getInt(map, "userLimit", defaults.getUserLimit()))
            );
        }

        public static PrivateRoom defaultValues() {
            return new PrivateRoom(true, null, null, 0);
        }

        public PrivateRoom withEnabled(boolean enabled) {
            return new PrivateRoom(enabled, triggerVoiceChannelId, categoryId, userLimit);
        }

        public PrivateRoom withTriggerVoiceChannelId(Long triggerVoiceChannelId) {
            return new PrivateRoom(enabled, triggerVoiceChannelId, categoryId, userLimit);
        }

        public PrivateRoom withCategoryId(Long categoryId) {
            return new PrivateRoom(enabled, triggerVoiceChannelId, categoryId, userLimit);
        }

        public PrivateRoom withUserLimit(int userLimit) {
            return new PrivateRoom(enabled, triggerVoiceChannelId, categoryId, Math.max(0, userLimit));
        }

        public boolean isEnabled() {
            return enabled;
        }

        public Long getTriggerVoiceChannelId() {
            return triggerVoiceChannelId;
        }

        public Long getCategoryId() {
            return categoryId;
        }

        public int getUserLimit() {
            return userLimit;
        }
    }

    public static class BotProfile {
        private final String description;
        private final String presenceStatus;
        private final String activityType;
        private final String activityText;

        private BotProfile(String description, String presenceStatus, String activityType, String activityText) {
            this.description = description;
            this.presenceStatus = presenceStatus;
            this.activityType = activityType;
            this.activityText = activityText;
        }

        public static BotProfile fromMap(Map<String, Object> map, BotProfile fallback) {
            BotProfile defaults = fallback == null ? defaultValues() : fallback;
            return new BotProfile(
                    getString(map, "description", defaults.getDescription()),
                    getString(map, "presenceStatus", defaults.getPresenceStatus()),
                    getString(map, "activityType", defaults.getActivityType()),
                    getString(map, "activityText", defaults.getActivityText())
            );
        }

        public static BotProfile defaultValues() {
            return new BotProfile("NoRule 多功能機器人", "ONLINE", "PLAYING", "/help");
        }

        public String getDescription() {
            return description;
        }

        public String getPresenceStatus() {
            return presenceStatus;
        }

        public String getActivityType() {
            return activityType;
        }

        public String getActivityText() {
            return activityText;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object object) {
        if (object instanceof Map) {
            return (Map<String, Object>) object;
        }
        return Map.of();
    }

    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return String.valueOf(value).trim();
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Long getLong(Map<String, Object> map, String key, Long defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        Long parsed = toLong(value);
        return parsed == null ? defaultValue : parsed;
    }

    private static int getColor(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() & 0xFFFFFF;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        if (text.startsWith("0x") || text.startsWith("0X")) {
            text = text.substring(2);
        }
        try {
            return Integer.parseInt(text, 16) & 0xFFFFFF;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text.trim();
    }

    private static Map<String, String> resolveCommandDescriptions(Map<String, Object> source) {
        Map<String, String> resolved = new LinkedHashMap<>(defaultCommandDescriptions());
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            String value = String.valueOf(entry.getValue()).trim();
            if (!key.isBlank() && !value.isBlank()) {
                resolved.put(key, value);
            }
        }
        return resolved;
    }

    private static Map<String, String> defaultCommandDescriptions() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("help", "顯示機器人說明");
        map.put("join", "讓機器人加入你的語音頻道");
        map.put("play", "播放音樂（關鍵字或連結）");
        map.put("play.query", "輸入關鍵字、YouTube 或 Spotify 連結");
        map.put("skip", "跳過目前歌曲");
        map.put("stop", "停止播放並清空佇列");
        map.put("leave", "讓機器人離開語音頻道");
        map.put("music-panel", "建立音樂控制面板");
        map.put("repeat", "設定循環模式");
        map.put("repeat.mode", "off / single / all");
        map.put("delete", "刪除訊息");
        map.put("delete.channel", "刪除指定頻道的訊息");
        map.put("delete.channel.channel", "選擇文字頻道");
        map.put("delete.channel.amount", "刪除數量 1-99（可省略）");
        map.put("delete.user", "刪除指定使用者的訊息");
        map.put("delete.user.user", "選擇使用者");
        map.put("delete.user.amount", "刪除數量 1-99（可省略）");
        map.put("delete-en", "Delete messages");
        map.put("settings", "伺服器設定");
        map.put("settings.info", "查看目前伺服器設定");
        map.put("settings.reload", "重新載入伺服器設定");
        map.put("settings.language", "設定語言");
        map.put("settings.language.code", "語言代碼（en 或 zh-TW）");
        map.put("settings.group.template", "通知模板設定");
        map.put("settings.template.voice-join", "設定語音加入模板");
        map.put("settings.template.voice-leave", "設定語音離開模板");
        map.put("settings.template.voice-move", "設定語音移動模板");
        map.put("settings.template.member-leave", "設定成員離開模板");
        map.put("settings.template.member-join", "設定成員加入模板");
        map.put("settings.group.logs", "通知與日誌頻道設定");
        map.put("settings.logs.member-channel", "設定成員通知頻道");
        map.put("settings.logs.member-channel.channel", "選擇文字頻道");
        map.put("settings.logs.voice-channel", "設定語音通知頻道");
        map.put("settings.logs.voice-channel.channel", "選擇文字頻道");
        map.put("settings.logs.messages-channel", "設定訊息日誌頻道");
        map.put("settings.logs.messages-channel.channel", "選擇文字頻道");
        map.put("settings.logs.command-usage-channel", "設定指令使用日誌頻道");
        map.put("settings.logs.command-usage-channel.channel", "選擇文字頻道");
        map.put("settings.logs.channel-events-channel", "設定頻道事件日誌頻道");
        map.put("settings.logs.channel-events-channel.channel", "選擇文字頻道");
        map.put("settings.logs.role-events-channel", "設定身分組變更日誌頻道");
        map.put("settings.logs.role-events-channel.channel", "選擇文字頻道");
        map.put("settings.logs.moderation-channel", "設定封禁/踢出管理日誌頻道");
        map.put("settings.logs.moderation-channel.channel", "選擇文字頻道");
        map.put("settings.group.music", "音樂與私人包廂設定");
        map.put("settings.music.auto-leave-enabled", "啟用或停用音樂自動離開");
        map.put("settings.music.auto-leave-enabled.value", "true / false");
        map.put("settings.music.auto-leave-minutes", "設定音樂自動離開分鐘數");
        map.put("settings.music.auto-leave-minutes.minutes", "分鐘數 1-60");
        map.put("settings.music.private-room-channel", "設定私人包廂觸發語音頻道");
        map.put("settings.music.private-room-channel.channel", "選擇語音頻道");
        map.put("settings.music.private-room-user-limit", "設定私人包廂人數上限");
        map.put("settings.music.private-room-user-limit.limit", "人數上限 0-99");
        map.put("settings.music.command-channel", "設定音樂指令專用頻道");
        map.put("settings.music.command-channel.channel", "選擇文字頻道");
        map.put("settings.group.module", "模組開關設定");
        map.put("settings.module.log-enabled", "啟用或停用訊息日誌模組");
        map.put("settings.module.log-enabled.value", "true / false");
        map.put("settings.module.private-room-enabled", "啟用或停用私人包廂模組");
        map.put("settings.module.private-room-enabled.value", "true / false");
        map.put("settings.module.voice-log", "啟用或停用語音日誌");
        map.put("settings.module.voice-log.value", "true / false");
        map.put("settings.module.message-log", "啟用或停用訊息日誌通知");
        map.put("settings.module.message-log.value", "true / false");
        map.put("settings.module.member-leave", "啟用或停用成員離開通知");
        map.put("settings.module.member-leave.value", "true / false");
        map.put("settings.module.member-join", "啟用或停用成員加入通知");
        map.put("settings.module.member-join.value", "true / false");
        return map;
    }
}
