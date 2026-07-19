package com.norule.musicbot.discord.bot.service.quest;

import com.norule.musicbot.config.GuildSettingsService;
import com.norule.musicbot.config.domain.QuestNotificationConfig;

/**
 * Stores and resolves the per-guild relay configuration for Discord Quest announcements.
 */
public final class QuestNotificationService {
    private final GuildSettingsService guildSettingsService;

    public QuestNotificationService(GuildSettingsService guildSettingsService) {
        this.guildSettingsService = guildSettingsService;
    }

    public QuestNotificationConfig configure(long guildId, long sourceChannelId, long notificationChannelId) {
        QuestNotificationConfig config = new QuestNotificationConfig(sourceChannelId, notificationChannelId);
        return guildSettingsService.updateSettings(guildId, settings -> settings.withQuestNotifications(config))
                .getQuestNotifications();
    }

    public Long findNotificationChannelId(long guildId, long sourceChannelId) {
        QuestNotificationConfig config = guildSettingsService.getQuestNotifications(guildId);
        if (!config.isConfigured() || config.getSourceChannelId() != sourceChannelId) {
            return null;
        }
        return config.getNotificationChannelId();
    }
}
