package com.norule.musicbot.discord.bot.gateway.listener;

import com.norule.musicbot.config.GuildSettingsService;
import com.norule.musicbot.discord.bot.service.quest.QuestNotificationService;
import com.norule.musicbot.i18n.I18nService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Relays messages from an administrator-selected, trusted Quest announcement channel.
 */
public final class QuestNotificationListener extends ListenerAdapter {
    private static final Color QUEST_COLOR = new Color(0x5865F2);

    private final GuildSettingsService guildSettingsService;
    private final QuestNotificationService questNotificationService;
    private final Supplier<I18nService> i18nServiceSupplier;

    public QuestNotificationListener(GuildSettingsService guildSettingsService,
                                     Supplier<I18nService> i18nServiceSupplier) {
        this.guildSettingsService = guildSettingsService;
        this.questNotificationService = new QuestNotificationService(guildSettingsService);
        this.i18nServiceSupplier = i18nServiceSupplier;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null
                || event.getAuthor().getIdLong() == event.getJDA().getSelfUser().getIdLong()) {
            return;
        }

        Long notificationChannelId = questNotificationService.findNotificationChannelId(
                event.getGuild().getIdLong(), event.getChannel().getIdLong());
        if (notificationChannelId == null) {
            return;
        }

        TextChannel notificationChannel = event.getGuild().getTextChannelById(notificationChannelId);
        if (notificationChannel == null || !notificationChannel.canTalk()) {
            return;
        }

        String lang = guildSettingsService.getLanguage(event.getGuild().getIdLong());
        I18nService i18n = i18nServiceSupplier.get();
        notificationChannel.sendMessageEmbeds(new EmbedBuilder()
                        .setColor(QUEST_COLOR)
                        .setTitle(i18n.t(lang, "quest_notifications.new_quest_title"))
                        .setDescription(i18n.t(lang, "quest_notifications.new_quest_description", Map.of(
                                "sourceChannel", "<#" + event.getChannel().getId() + ">",
                                "messageUrl", event.getMessage().getJumpUrl()
                        )))
                        .setTimestamp(Instant.now())
                        .build())
                .queue();
    }
}
