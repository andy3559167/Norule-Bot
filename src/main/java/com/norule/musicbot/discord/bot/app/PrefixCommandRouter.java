package com.norule.musicbot.discord.bot.app;

import com.norule.musicbot.config.domain.RuntimeConfigSnapshot;
import com.norule.musicbot.discord.bot.gateway.command.CommandNames;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Locale;
import java.util.Map;

class PrefixCommandRouter {
    private static final String KEY_UNKNOWN_COMMAND = "general.unknown_command";

    private final MusicCommandService service;
    private final CommandCooldownService cooldownService;

    PrefixCommandRouter(MusicCommandService service, CommandCooldownService cooldownService) {
        this.service = service;
        this.cooldownService = cooldownService;
    }

    void route(MessageReceivedEvent event) {
        String raw = event.getMessage().getContentRaw();
        if (!event.isFromGuild()) {
            return;
        }
        Guild guild = event.getGuild();
        if (service.honeypotService().isHoneypotChannel(guild.getIdLong(), event.getChannel().getIdLong())) {
            return;
        }
        RuntimeConfigSnapshot snapshot = service.runtimeConfigSnapshot();
        PrefixInvocation invocation = parseInvocation(raw, snapshot.getPrefix());
        if (invocation == null) {
            return;
        }
        String cmd = invocation.command();
        String arg = invocation.argument();
        String lang = service.lang(guild.getIdLong());

        if (isKnownPrefixCommand(cmd)) {
            long remaining = cooldownService.acquireCooldown(event.getAuthor().getIdLong());
            if (remaining > 0) {
                event.getChannel().sendMessage(service.i18nService().t(lang, "general.command_cooldown",
                        Map.of("seconds", String.valueOf(cooldownService.toCooldownSeconds(remaining)))))
                        .queue();
                return;
            }
        }

        if (isPrefixMusicCommand(cmd) && !service.isMusicCommandChannelAllowed(guild, event.getChannel().getIdLong())) {
            event.getChannel().sendMessage(service.i18nService().t(lang, "music.command_channel_restricted")).queue();
            return;
        }

        switch (cmd) {
            case "help" -> service.helpCommandHandler().handleTextHelp(event.getChannel().asTextChannel(), guild, lang);
            case CommandNames.CMD_VOLUME -> service.playbackCommandHandler().handleTextCommand(event, guild, cmd, arg, lang);
            case CommandNames.CMD_HISTORY -> event.getChannel().sendMessageEmbeds(service.historyCommandHandler().historyEmbed(guild, lang).build()).queue();
            case CommandNames.CMD_PLAYLIST -> service.playlistCommandHandler().handlePlaylistPrefix(event, guild, arg, lang);
            case "join", "play", "skip", "stop", CommandNames.CMD_LEAVE, CommandNames.CMD_REPEAT -> service.playbackCommandHandler().handleTextCommand(event, guild, cmd, arg, lang);
            case CommandNames.CMD_MUSIC -> event.getChannel().sendMessageEmbeds(service.musicStatsCommandHandler().musicStatsEmbed(guild, lang).build()).queue();
            default -> event.getChannel().sendMessage(service.i18nService().t(lang, KEY_UNKNOWN_COMMAND)).queue();
        }
        if (isKnownPrefixCommand(cmd)) {
            service.logCommandUsage(guild, event.getMember(), raw.trim(), event.getChannel().getIdLong());
        }
    }

    static PrefixInvocation parseInvocation(String raw, String configuredPrefix) {
        if (raw == null) {
            return null;
        }
        String commandBody;
        if (configuredPrefix != null && !configuredPrefix.isEmpty() && raw.startsWith(configuredPrefix)) {
            commandBody = raw.substring(configuredPrefix.length());
        } else if (isDollarPlayInvocation(raw)) {
            commandBody = raw.substring(1);
        } else {
            return null;
        }
        String[] split = commandBody.trim().split("\\s+", 2);
        String command = split.length > 0 ? split[0].toLowerCase(Locale.ROOT) : "";
        if ("p".equals(command)) {
            command = CommandNames.CMD_PLAY;
        }
        String argument = split.length > 1 ? split[1].trim() : "";
        return new PrefixInvocation(command, argument);
    }

    private static boolean isDollarPlayInvocation(String raw) {
        return raw.length() >= 2
                && raw.charAt(0) == '$'
                && Character.toLowerCase(raw.charAt(1)) == 'p'
                && (raw.length() == 2 || Character.isWhitespace(raw.charAt(2)));
    }

    record PrefixInvocation(String command, String argument) {
    }

    private boolean isKnownPrefixCommand(String cmd) {
        return "help".equals(cmd)
                || CommandNames.CMD_VOLUME.equals(cmd)
                || CommandNames.CMD_HISTORY.equals(cmd)
                || CommandNames.CMD_MUSIC.equals(cmd)
                || CommandNames.CMD_PLAYLIST.equals(cmd)
                || "join".equals(cmd)
                || "play".equals(cmd)
                || "skip".equals(cmd)
                || "stop".equals(cmd)
                || CommandNames.CMD_LEAVE.equals(cmd)
                || CommandNames.CMD_REPEAT.equals(cmd);
    }

    private boolean isPrefixMusicCommand(String cmd) {
        return "join".equals(cmd)
                || "play".equals(cmd)
                || "skip".equals(cmd)
                || "stop".equals(cmd)
                || CommandNames.CMD_LEAVE.equals(cmd)
                || CommandNames.CMD_REPEAT.equals(cmd)
                || CommandNames.CMD_VOLUME.equals(cmd)
                || CommandNames.CMD_HISTORY.equals(cmd)
                || CommandNames.CMD_MUSIC.equals(cmd)
                || CommandNames.CMD_PLAYLIST.equals(cmd);
    }
}
