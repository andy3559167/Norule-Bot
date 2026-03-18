package com.norule.musicbot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class NotificationListener extends ListenerAdapter {
    private final GuildSettingsService guildSettingsService;

    public NotificationListener(GuildSettingsService guildSettingsService) {
        this.guildSettingsService = guildSettingsService;
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        BotConfig.Notifications config = guildSettingsService.getNotifications(event.getGuild().getIdLong());
        if (!config.isEnabled() || !config.isMemberJoinEnabled() || event.getUser().isBot()) {
            return;
        }
        String lang = guildSettingsService.getLanguage(event.getGuild().getIdLong());

        sendMemberMessage(
                event.getGuild(),
                config,
                formatUserTemplate(resolveMemberTemplate(lang, config.getMemberJoinMessage(), true), event.getUser(), event.getGuild()),
                config.getMemberJoinColor(),
                event.getUser(),
                true
        );
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        BotConfig.Notifications config = guildSettingsService.getNotifications(event.getGuild().getIdLong());
        User user = event.getUser();
        if (!config.isEnabled() || !config.isMemberLeaveEnabled() || user.isBot()) {
            return;
        }
        String lang = guildSettingsService.getLanguage(event.getGuild().getIdLong());

        sendMemberMessage(
                event.getGuild(),
                config,
                formatUserTemplate(resolveMemberTemplate(lang, config.getMemberLeaveMessage(), false), user, event.getGuild()),
                config.getMemberLeaveColor(),
                user,
                false
        );
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        BotConfig.Notifications config = guildSettingsService.getNotifications(event.getGuild().getIdLong());
        if (!config.isEnabled() || !config.isVoiceLogEnabled() || event.getEntity().getUser().isBot()) {
            return;
        }
        String lang = guildSettingsService.getLanguage(event.getGuild().getIdLong());

        String message = null;
        if (event.getChannelLeft() == null && event.getChannelJoined() != null) {
            message = formatVoiceTemplate(
                    resolveVoiceTemplate(lang, config.getVoiceJoinMessage(), "join"),
                    event.getEntity().getAsMention(),
                    null,
                    event.getChannelJoined().getAsMention()
            );
        } else if (event.getChannelLeft() != null && event.getChannelJoined() == null) {
            message = formatVoiceTemplate(
                    resolveVoiceTemplate(lang, config.getVoiceLeaveMessage(), "leave"),
                    event.getEntity().getAsMention(),
                    event.getChannelLeft().getAsMention(),
                    null
            );
        } else if (event.getChannelLeft() != null && event.getChannelJoined() != null) {
            message = formatVoiceTemplate(
                    resolveVoiceTemplate(lang, config.getVoiceMoveMessage(), "move"),
                    event.getEntity().getAsMention(),
                    event.getChannelLeft().getAsMention(),
                    event.getChannelJoined().getAsMention()
            );
        }

        if (message != null && !message.isBlank()) {
            sendVoiceMessage(event.getGuild(), config, message);
        }
    }

    private void sendMemberMessage(Guild guild, BotConfig.Notifications config, String message, int color, User user, boolean joinEvent) {
        Long channelId = config.getMemberChannelId();
        if (channelId == null || message == null || message.isBlank()) {
            return;
        }

        String lang = guildSettingsService.getLanguage(guild.getIdLong());
        Instant createdAt = user.getTimeCreated().toInstant();
        Instant now = Instant.now();

        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel != null) {
            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(new Color(color & 0xFFFFFF))
                    .setTitle(joinEvent
                            ? (isZhTw(lang) ? "成員加入通知" : "Member Joined")
                            : (isZhTw(lang) ? "成員離開通知" : "Member Left"))
                    .setDescription(message)
                    .addField(isZhTw(lang) ? "使用者" : "User", user.getAsMention() + " (`" + user.getAsTag() + "`)", false)
                    .addField(isZhTw(lang) ? "ID" : "ID", user.getId(), true)
                    .addField(isZhTw(lang) ? "帳號建立時間" : "Account Created", discordCreatedAt(createdAt), true)
                    .addField(
                            joinEvent
                                    ? (isZhTw(lang) ? "加入通知時間" : "Join Notify Time")
                                    : (isZhTw(lang) ? "離開通知時間" : "Leave Notify Time"),
                            discordTimestamp(now),
                            false
                    )
                    .setThumbnail(user.getEffectiveAvatarUrl());
            channel.sendMessageEmbeds(eb.build()).queue();
        }
    }

    private void sendVoiceMessage(Guild guild, BotConfig.Notifications config, String message) {
        Long channelId = config.getVoiceChannelId();
        if (channelId == null || message == null || message.isBlank()) {
            return;
        }

        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel != null) {
            channel.sendMessage(message).queue();
        }
    }

    private String formatUserTemplate(String template, User user, Guild guild) {
        Instant createdAt = user.getTimeCreated().toInstant();
        long accountAgeDays = ChronoUnit.DAYS.between(createdAt, Instant.now());
        return template
                .replace("{user}", user.getAsMention())
                .replace("{username}", user.getName())
                .replace("{guild}", guild.getName())
                .replace("{id}", user.getId())
                .replace("{tag}", user.getAsTag())
                .replace("{isBot}", String.valueOf(user.isBot()))
                .replace("{createdAt}", discordCreatedAt(createdAt))
                .replace("{accountAgeDays}", String.valueOf(Math.max(0L, accountAgeDays)));
    }

    private String formatVoiceTemplate(String template, String userMention, String fromChannel, String toChannel) {
        String result = template.replace("{user}", userMention);
        result = result.replace("{channel}", toChannel != null ? toChannel : fromChannel != null ? fromChannel : "");
        result = result.replace("{from}", fromChannel != null ? fromChannel : "");
        result = result.replace("{to}", toChannel != null ? toChannel : "");
        return result;
    }

    private String resolveVoiceTemplate(String lang, String template, String type) {
        if (!isZhTw(lang)) {
            return template;
        }
        BotConfig.Notifications defaults = BotConfig.Notifications.defaultValues();
        return switch (type) {
            case "join" -> template.equals(defaults.getVoiceJoinMessage())
                    ? "{user} 加入了語音頻道 {channel}。"
                    : template;
            case "leave" -> template.equals(defaults.getVoiceLeaveMessage())
                    ? "{user} 離開了語音頻道 {channel}。"
                    : template;
            case "move" -> template.equals(defaults.getVoiceMoveMessage())
                    ? "{user} 從語音頻道 {from} 移動到 {to}。"
                    : template;
            default -> template;
        };
    }

    private String resolveMemberTemplate(String lang, String template, boolean join) {
        if (!isZhTw(lang)) {
            return template;
        }
        BotConfig.Notifications defaults = BotConfig.Notifications.defaultValues();
        if (join) {
            if (template.equals(defaults.getMemberJoinMessage())) {
                return "{user} 加入了伺服器。帳號建立時間：{createdAt}。ID：{id}";
            }
            return template;
        }
        if (template.equals(defaults.getMemberLeaveMessage())) {
            return "{user} 離開了伺服器。帳號建立時間：{createdAt}。ID：{id}";
        }
        return template;
    }

    private boolean isZhTw(String lang) {
        return lang != null && lang.equalsIgnoreCase("zh-TW");
    }

    private String discordTimestamp(Instant instant) {
        return "<t:" + instant.getEpochSecond() + ">";
    }

    private String discordCreatedAt(Instant instant) {
        long sec = instant.getEpochSecond();
        return "<t:" + sec + ":S> (<t:" + sec + ":R>)";
    }
}
