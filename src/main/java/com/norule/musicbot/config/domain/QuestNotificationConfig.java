package com.norule.musicbot.config.domain;

import java.util.Map;

/**
 * Per-guild channels used to relay announcements from a Discord Quest source channel.
 */
public final class QuestNotificationConfig {
    private final Long sourceChannelId;
    private final Long notificationChannelId;

    public QuestNotificationConfig(Long sourceChannelId, Long notificationChannelId) {
        this.sourceChannelId = validChannelId(sourceChannelId);
        this.notificationChannelId = validChannelId(notificationChannelId);
    }

    public static QuestNotificationConfig defaultValues() {
        return new QuestNotificationConfig(null, null);
    }

    public static QuestNotificationConfig fromMap(Map<String, Object> map, QuestNotificationConfig fallback) {
        QuestNotificationConfig defaults = fallback == null ? defaultValues() : fallback;
        Map<String, Object> values = map == null ? Map.of() : map;
        return new QuestNotificationConfig(
                channelId(values.get("sourceChannelId"), defaults.getSourceChannelId()),
                channelId(values.get("notificationChannelId"), defaults.getNotificationChannelId())
        );
    }

    public Long getSourceChannelId() {
        return sourceChannelId;
    }

    public Long getNotificationChannelId() {
        return notificationChannelId;
    }

    public boolean isConfigured() {
        return sourceChannelId != null && notificationChannelId != null;
    }

    private static Long channelId(Object value, Long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return validChannelId(Long.parseLong(String.valueOf(value).trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Long validChannelId(Long value) {
        return value != null && value > 0L ? value : null;
    }
}
