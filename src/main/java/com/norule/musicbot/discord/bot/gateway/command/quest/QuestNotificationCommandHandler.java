package com.norule.musicbot.discord.bot.gateway.command.quest;

import com.norule.musicbot.discord.bot.gateway.command.CommandOptions;
import com.norule.musicbot.discord.bot.service.quest.QuestNotificationService;
import com.norule.musicbot.i18n.I18nService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Handles registration of a trusted Discord Quest announcement source and its relay channel.
 */
public final class QuestNotificationCommandHandler {
    private final QuestNotificationService questNotificationService;
    private final Supplier<I18nService> i18nServiceSupplier;

    public QuestNotificationCommandHandler(QuestNotificationService questNotificationService,
                                           Supplier<I18nService> i18nServiceSupplier) {
        this.questNotificationService = questNotificationService;
        this.i18nServiceSupplier = i18nServiceSupplier;
    }

    public void handleQuestNotificationsSlash(SlashCommandInteractionEvent event, String lang) {
        I18nService i18n = i18nServiceSupplier.get();
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply(i18n.t(lang, "general.no_permission")).setEphemeral(true).queue();
            return;
        }

        long sourceChannelId = event.getOption(CommandOptions.SOURCE_CHANNEL).getAsChannel().getIdLong();
        long notificationChannelId = event.getOption(CommandOptions.NOTIFICATION_CHANNEL).getAsChannel().getIdLong();
        if (sourceChannelId == notificationChannelId) {
            event.reply(i18n.t(lang, "quest_notifications.same_channel")).setEphemeral(true).queue();
            return;
        }

        questNotificationService.configure(event.getGuild().getIdLong(), sourceChannelId, notificationChannelId);
        event.reply(i18n.t(lang, "quest_notifications.configured", Map.of(
                        "sourceChannel", "<#" + sourceChannelId + ">",
                        "sourceChannelId", String.valueOf(sourceChannelId),
                        "notificationChannel", "<#" + notificationChannelId + ">",
                        "notificationChannelId", String.valueOf(notificationChannelId)
                )))
                .setEphemeral(true)
                .queue();
    }
}
