package com.norule.musicbot.discord.bot.service.logs;

import com.norule.musicbot.config.*;
import com.norule.musicbot.domain.discord.DiscordEmbedSanitizer;
import com.norule.musicbot.i18n.*;
import com.norule.musicbot.ModerationService;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ServerLogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerLogService.class);

    private final GuildSettingsService settingsService;
    private final I18nService i18n;
    private final ModerationService moderationService;

    public ServerLogService(GuildSettingsService settingsService, I18nService i18n, ModerationService moderationService) {
        this.settingsService = settingsService;
        this.i18n = i18n;
        this.moderationService = moderationService;
    }
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        var logs = settingsService.getMessageLogs(event.getGuild().getIdLong());
        if (!logs.isEnabled() || !logs.isRoleLogEnabled() || event.getRoles().isEmpty()) {
            return;
        }
        String roles = formatRoleList(event.getGuild(), event.getRoles().stream()
                .map(role -> role.getAsMention())
                .toList());
        EmbedBuilder eb = base(event.getGuild(), "\u2795 " + t(event.getGuild(), "logs.role_added"), new Color(46, 204, 113));
        addField(eb, t(event.getGuild(), "logs.user"), event.getMember().getAsMention(), false);
        addField(eb, t(event.getGuild(), "logs.roles"), roles, false);
        sendRoleChangeToConfiguredLogs(event.getGuild(), logs.getRoleLogChannelId(), logs.getChannelLifecycleChannelId(), logs.getChannelId(), logs.isChannelLifecycleLogEnabled(), eb);
    }
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        var logs = settingsService.getMessageLogs(event.getGuild().getIdLong());
        if (!logs.isEnabled() || !logs.isRoleLogEnabled() || event.getRoles().isEmpty()) {
            return;
        }
        String roles = formatRoleList(event.getGuild(), event.getRoles().stream()
                .map(role -> role.getAsMention())
                .toList());
        EmbedBuilder eb = base(event.getGuild(), "\u2796 " + t(event.getGuild(), "logs.role_removed"), new Color(231, 76, 60));
        addField(eb, t(event.getGuild(), "logs.user"), event.getMember().getAsMention(), false);
        addField(eb, t(event.getGuild(), "logs.roles"), roles, false);
        sendRoleChangeToConfiguredLogs(event.getGuild(), logs.getRoleLogChannelId(), logs.getChannelLifecycleChannelId(), logs.getChannelId(), logs.isChannelLifecycleLogEnabled(), eb);
    }
    public void onChannelCreate(ChannelCreateEvent event) {
        if (event.getGuild() == null) {
            return;
        }
        var logs = settingsService.getMessageLogs(event.getGuild().getIdLong());
        if (!logs.isEnabled() || !logs.isChannelLifecycleLogEnabled()) {
            return;
        }
        Channel channel = event.getChannel();
        EmbedBuilder eb = base(event.getGuild(), "\uD83C\uDD95 " + t(event.getGuild(), "logs.channel_created"), new Color(52, 152, 219));
        addField(eb, t(event.getGuild(), "logs.channel"), channel.getAsMention() + " (`" + channel.getType().name() + "`)", false);
        send(event.getGuild(), logs.getChannelLifecycleChannelId(), eb);
    }
    public void onChannelDelete(ChannelDeleteEvent event) {
        if (event.getGuild() == null) {
            return;
        }
        var logs = settingsService.getMessageLogs(event.getGuild().getIdLong());
        if (!logs.isEnabled() || !logs.isChannelLifecycleLogEnabled()) {
            return;
        }
        Channel channel = event.getChannel();
        EmbedBuilder eb = base(event.getGuild(), "\uD83D\uDDD1\uFE0F " + t(event.getGuild(), "logs.channel_deleted"), new Color(231, 76, 60));
        addField(eb, t(event.getGuild(), "logs.channel"), "`" + channel.getName() + "` (`" + channel.getType().name() + "`)", false);
        send(event.getGuild(), logs.getChannelLifecycleChannelId(), eb);
    }
    public void onChannelUpdateName(ChannelUpdateNameEvent event) {
        if (event.getGuild() == null) {
            return;
        }
        var logs = settingsService.getMessageLogs(event.getGuild().getIdLong());
        if (!logs.isEnabled() || !logs.isChannelLifecycleLogEnabled()) {
            return;
        }
        Channel channel = event.getChannel();
        String before = event.getOldValue() == null ? "-" : event.getOldValue();
        String after = event.getNewValue() == null ? "-" : event.getNewValue();
        EmbedBuilder eb = base(event.getGuild(), "\u270F\uFE0F " + t(event.getGuild(), "logs.channel_renamed"), new Color(155, 89, 182));
        addField(eb, t(event.getGuild(), "logs.channel"), channel.getAsMention() + " (`" + channel.getType().name() + "`)", false);
        addField(eb, t(event.getGuild(), "logs.before"), "`" + before.replace("`", "") + "`", true);
        addField(eb, t(event.getGuild(), "logs.after"), "`" + after.replace("`", "") + "`", true);
        send(event.getGuild(), logs.getChannelLifecycleChannelId(), eb);
    }
    public void onGuildBan(GuildBanEvent event) {
        var logs = settingsService.getMessageLogs(event.getGuild().getIdLong());
        if (!logs.isEnabled() || !logs.isModerationLogEnabled()) {
            return;
        }
        EmbedBuilder eb = base(event.getGuild(), "\u26D4 " + t(event.getGuild(), "logs.user_banned"), new Color(192, 57, 43));
        addField(eb, t(event.getGuild(), "logs.user"), event.getUser().getAsMention() + " (`" + event.getUser().getAsTag() + "`)", false);
        send(event.getGuild(), logs.getModerationLogChannelId(), eb);
        moderationService.recordModerationAction(
                event.getGuild().getIdLong(),
                event.getUser().getIdLong(),
                null,
                "BAN",
                "",
                "event"
        );
    }
    public void onGuildUnban(GuildUnbanEvent event) {
        var logs = settingsService.getMessageLogs(event.getGuild().getIdLong());
        if (!logs.isEnabled() || !logs.isModerationLogEnabled()) {
            return;
        }
        EmbedBuilder eb = base(event.getGuild(), "\u2705 " + t(event.getGuild(), "logs.user_unbanned"), new Color(39, 174, 96));
        addField(eb, t(event.getGuild(), "logs.user"), event.getUser().getAsMention() + " (`" + event.getUser().getAsTag() + "`)", false);
        send(event.getGuild(), logs.getModerationLogChannelId(), eb);
        moderationService.recordModerationAction(
                event.getGuild().getIdLong(),
                event.getUser().getIdLong(),
                null,
                "UNBAN",
                "",
                "event"
        );
    }
    public void onGuildAuditLogEntryCreate(GuildAuditLogEntryCreateEvent event) {
        if (event.getEntry().getType() != ActionType.KICK) {
            return;
        }
        var logs = settingsService.getMessageLogs(event.getGuild().getIdLong());
        if (!logs.isEnabled() || !logs.isModerationLogEnabled()) {
            return;
        }
        User target = event.getJDA().getUserById(event.getEntry().getTargetIdLong());
        String targetText = target == null ? "`" + event.getEntry().getTargetId() + "`"
                : target.getAsMention() + " (`" + target.getAsTag() + "`)";
        User actor = event.getEntry().getUser();
        String actorText = actor == null ? "-" : actor.getAsMention() + " (`" + actor.getAsTag() + "`)";
        EmbedBuilder eb = base(event.getGuild(), "\uD83D\uDC62 " + t(event.getGuild(), "logs.user_kicked"), new Color(230, 126, 34));
        addField(eb, t(event.getGuild(), "logs.target"), targetText, false);
        addField(eb, t(event.getGuild(), "logs.moderator"), actorText, false);
        send(event.getGuild(), logs.getModerationLogChannelId(), eb);
        long userId = event.getEntry().getTargetIdLong();
        moderationService.recordModerationAction(
                event.getGuild().getIdLong(),
                userId,
                actor == null ? null : actor.getIdLong(),
                "KICK",
                "",
                "audit"
        );
    }

    private EmbedBuilder base(Guild guild, String title, Color color) {
        return new EmbedBuilder()
                .setTitle(DiscordEmbedSanitizer.sanitizeTitle(title))
                .setColor(color)
                .setTimestamp(Instant.now())
                .setFooter(DiscordEmbedSanitizer.sanitizeFooter(guild.getName()), guild.getIconUrl());
    }

    private void send(Guild guild, Long preferredChannelId, EmbedBuilder eb) {
        var logs = settingsService.getMessageLogs(guild.getIdLong());
        Long channelId = preferredChannelId != null ? preferredChannelId : logs.getChannelId();
        if (channelId == null) {
            return;
        }
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            return;
        }
        var selfMember = guild.getSelfMember();
        if (selfMember == null
                || !selfMember.hasPermission(
                        channel,
                        Permission.VIEW_CHANNEL,
                        Permission.MESSAGE_SEND,
                        Permission.MESSAGE_EMBED_LINKS
                )) {
            return;
        }
        try {
            channel.sendMessageEmbeds(eb.build()).queue(
                    success -> {
                        // Successful delivery needs no follow-up work.
                    },
                    error -> logSendFailure(guild.getIdLong(), channel.getIdLong(), error)
            );
        } catch (InsufficientPermissionException insufficientPermission) {
            LOGGER.warn(
                    "[NoRule] Server log delivery skipped: guildId={} channelId={} reason=MISSING_PERMISSION permission={}",
                    guild.getIdLong(),
                    channel.getIdLong(),
                    insufficientPermission.getPermission()
            );
        } catch (IllegalArgumentException invalidEmbed) {
            LOGGER.error(
                    "[NoRule] Server log embed validation failed: guildId={} channelId={}",
                    guild.getIdLong(),
                    channel.getIdLong(),
                    invalidEmbed
            );
        }
    }

    private void addField(EmbedBuilder embed, String name, String value, boolean inline) {
        embed.addField(
                DiscordEmbedSanitizer.sanitizeFieldName(name),
                DiscordEmbedSanitizer.sanitizeFieldValue(value),
                inline
        );
    }

    private String formatRoleList(Guild guild, List<String> roleMentions) {
        String lang = settingsService.getLanguage(guild.getIdLong());
        return DiscordEmbedSanitizer.joinWithinLimit(
                roleMentions,
                DiscordEmbedSanitizer.FIELD_VALUE_MAX_LENGTH,
                "\n",
                omitted -> i18n.t(lang, "logs.roles_more", Map.of("count", String.valueOf(omitted)))
        );
    }

    private void logSendFailure(long guildId, long channelId, Throwable error) {
        if (error instanceof ErrorResponseException responseException
                && isExpectedLogDeliveryFailure(responseException.getErrorResponse())) {
            LOGGER.warn(
                    "[NoRule] Server log delivery skipped: guildId={} channelId={} reason={}",
                    guildId,
                    channelId,
                    responseException.getErrorResponse()
            );
            return;
        }
        if (error instanceof InsufficientPermissionException insufficientPermission) {
            LOGGER.warn(
                    "[NoRule] Server log delivery skipped: guildId={} channelId={} reason=MISSING_PERMISSION permission={}",
                    guildId,
                    channelId,
                    insufficientPermission.getPermission()
            );
            return;
        }
        LOGGER.error(
                "[NoRule] Server log delivery failed unexpectedly: guildId={} channelId={}",
                guildId,
                channelId,
                error
        );
    }

    private boolean isExpectedLogDeliveryFailure(ErrorResponse response) {
        return response == ErrorResponse.UNKNOWN_CHANNEL
                || response == ErrorResponse.MISSING_ACCESS
                || response == ErrorResponse.MISSING_PERMISSIONS;
    }

    private String t(Guild guild, String key) {
        return i18n.t(settingsService.getLanguage(guild.getIdLong()), key);
    }

    private void sendRoleChangeToConfiguredLogs(Guild guild,
                                                Long roleLogChannelId,
                                                Long channelLifecycleChannelId,
                                                Long fallbackChannelId,
                                                boolean channelLifecycleEnabled,
                                                EmbedBuilder eb) {
        Long roleTarget = resolveTargetChannelId(roleLogChannelId, fallbackChannelId);
        send(guild, roleTarget, eb);

        if (!channelLifecycleEnabled) {
            return;
        }
        Long channelTarget = resolveTargetChannelId(channelLifecycleChannelId, fallbackChannelId);
        if (channelTarget == null || channelTarget.equals(roleTarget)) {
            return;
        }
        send(guild, channelTarget, eb);
    }

    private Long resolveTargetChannelId(Long preferred, Long fallback) {
        return preferred != null ? preferred : fallback;
    }
}





