package com.norule.musicbot.discord.bot.gateway.listener;

import com.norule.musicbot.discord.bot.service.logs.ServerLogService;

import com.norule.musicbot.config.GuildSettingsService;
import com.norule.musicbot.i18n.I18nService;
import com.norule.musicbot.ModerationService;
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
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerLogListener extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerLogListener.class);
    private final ServerLogService service;

    public ServerLogListener(GuildSettingsService settingsService, I18nService i18n, ModerationService moderationService) {
        this.service = new ServerLogService(settingsService, i18n, moderationService);
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        runSafely("GuildMemberRoleAdd", event.getGuild().getIdLong(), () -> service.onGuildMemberRoleAdd(event));
    }
    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        runSafely("GuildMemberRoleRemove", event.getGuild().getIdLong(), () -> service.onGuildMemberRoleRemove(event));
    }
    @Override
    public void onChannelCreate(ChannelCreateEvent event) {
        runSafely("ChannelCreate", event.getGuild().getIdLong(), () -> service.onChannelCreate(event));
    }
    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        runSafely("ChannelDelete", event.getGuild().getIdLong(), () -> service.onChannelDelete(event));
    }
    @Override
    public void onChannelUpdateName(ChannelUpdateNameEvent event) {
        runSafely("ChannelUpdateName", event.getGuild().getIdLong(), () -> service.onChannelUpdateName(event));
    }
    @Override
    public void onGuildBan(GuildBanEvent event) {
        runSafely("GuildBan", event.getGuild().getIdLong(), () -> service.onGuildBan(event));
    }
    @Override
    public void onGuildUnban(GuildUnbanEvent event) {
        runSafely("GuildUnban", event.getGuild().getIdLong(), () -> service.onGuildUnban(event));
    }
    @Override
    public void onGuildAuditLogEntryCreate(GuildAuditLogEntryCreateEvent event) {
        runSafely("GuildAuditLogEntryCreate", event.getGuild().getIdLong(), () -> service.onGuildAuditLogEntryCreate(event));
    }

    private void runSafely(String eventName, long guildId, Runnable action) {
        try {
            action.run();
        } catch (InsufficientPermissionException | ErrorResponseException | IllegalArgumentException operational) {
            LOGGER.warn(
                    "[NoRule] Server log event skipped: event={} guildId={} reason={}",
                    eventName,
                    guildId,
                    operational.getClass().getSimpleName()
            );
        } catch (RuntimeException unexpected) {
            LOGGER.error(
                    "[NoRule] Server log event failed: event={} guildId={}",
                    eventName,
                    guildId,
                    unexpected
            );
        }
    }
}
