package com.norule.musicbot.discord.listeners.stats;

import com.norule.musicbot.domain.stats.MessageStatsService;
import com.norule.musicbot.domain.stats.UserMessageCount;
import com.norule.musicbot.domain.stats.UserVoiceTime;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessageStatsListener extends ListenerAdapter {
    private static final int PAGE_SIZE = 10;
    private static final int QUERY_LIMIT = 100;
    private static final String CMD_STATS_ZH = "統計";
    private static final String CMD_LEADERBOARD_ZH = "排行榜";
    private static final String LEADERBOARD_SELECT_ID = "stats:leaderboard:type";
    private static final String LEADERBOARD_PAGE_BTN_PREFIX = "stats:leaderboard:page:";
    private static final String TYPE_MESSAGES = "messages";
    private static final String TYPE_VOICE = "voice";

    private final MessageStatsService statsService;
    private final Map<String, Long> voiceSessionStartEpochMs = new ConcurrentHashMap<>();

    public MessageStatsListener(MessageStatsService statsService) {
        this.statsService = statsService;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild()) {
            return;
        }
        User author = event.getAuthor();
        if (author.isBot() || event.isWebhookMessage()) {
            return;
        }
        statsService.trackMessage(event.getGuild().getIdLong(), author.getIdLong(), event.getMessageIdLong());
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (event.getEntity().getUser().isBot()) {
            return;
        }
        long guildId = event.getGuild().getIdLong();
        long userId = event.getEntity().getIdLong();
        String key = buildVoiceSessionKey(guildId, userId);

        if (event.getChannelLeft() != null) {
            Long startedAt = voiceSessionStartEpochMs.remove(key);
            if (startedAt != null) {
                long seconds = Math.max(0L, (System.currentTimeMillis() - startedAt) / 1000L);
                statsService.trackVoiceDuration(guildId, userId, seconds);
            }
        }

        if (event.getChannelJoined() != null) {
            voiceSessionStartEpochMs.put(key, System.currentTimeMillis());
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null) {
            return;
        }
        try {
            String command = event.getName();
            if ("stats".equals(command) || CMD_STATS_ZH.equals(command)) {
                handleStats(event);
                return;
            }
            if ("top".equals(command) || CMD_LEADERBOARD_ZH.equals(command)) {
                handleLeaderboard(event);
            }
        } catch (Exception e) {
            String message = "排行榜模組執行失敗：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            if (event.isAcknowledged()) {
                event.getHook().sendMessage(message).setEphemeral(true).queue();
            } else {
                event.reply(message).setEphemeral(true).queue();
            }
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!LEADERBOARD_SELECT_ID.equals(event.getComponentId()) || event.getGuild() == null) {
            return;
        }
        String selectedType = event.getValues().isEmpty() ? TYPE_MESSAGES : event.getValues().get(0);
        event.editMessageEmbeds(buildLeaderboardEmbed(event.getGuild(), selectedType, 1).build())
                .setComponents(buildLeaderboardComponents(selectedType, 1, computeTotalPages(event.getGuild(), selectedType)))
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(LEADERBOARD_PAGE_BTN_PREFIX) || event.getGuild() == null) {
            return;
        }
        String[] parts = id.split(":");
        if (parts.length != 6) {
            return;
        }
        String type = parts[4];
        int page = parsePositiveInt(parts[5], 1);
        int totalPages = computeTotalPages(event.getGuild(), type);
        int clampedPage = Math.max(1, Math.min(totalPages, page));
        event.editMessageEmbeds(buildLeaderboardEmbed(event.getGuild(), type, clampedPage).build())
                .setComponents(buildLeaderboardComponents(type, clampedPage, totalPages))
                .queue();
    }

    private void handleStats(SlashCommandInteractionEvent event) {
        User target = event.getOption("user", event.getUser(), o -> o.getAsUser());
        long guildId = event.getGuild().getIdLong();
        long messageCount = statsService.getUserMessageCount(guildId, target.getIdLong());
        long voiceSeconds = statsService.getUserVoiceSeconds(guildId, target.getIdLong());
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(0x4E7BFF))
                .setTitle("使用者統計")
                .setDescription(target.getAsMention())
                .addField("訊息數", String.valueOf(messageCount), true)
                .addField("語音時長", formatDuration(voiceSeconds), true);
        event.replyEmbeds(embed.build()).queue();
    }

    private void handleLeaderboard(SlashCommandInteractionEvent event) {
        String type = TYPE_MESSAGES;
        int page = 1;
        int totalPages = computeTotalPages(event.getGuild(), type);
        event.replyEmbeds(buildLeaderboardEmbed(event.getGuild(), type, page).build())
                .addComponents(buildLeaderboardComponents(type, page, totalPages))
                .queue();
    }

    private EmbedBuilder buildLeaderboardEmbed(Guild guild, String type, int page) {
        String normalizedType = normalizeType(type);
        List<?> rows = TYPE_VOICE.equals(normalizedType)
                ? statsService.getTopVoiceUsers(guild.getIdLong(), QUERY_LIMIT)
                : statsService.getTopUsers(guild.getIdLong(), QUERY_LIMIT);
        int totalPages = Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int clampedPage = Math.max(1, Math.min(totalPages, page));

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(0x2B9D8F))
                .setTitle("🏆 排行榜")
                .addField("類型", TYPE_VOICE.equals(normalizedType) ? "語音時長" : "訊息數", true)
                .addField("頁數", clampedPage + "/" + totalPages, true);

        if (rows.isEmpty()) {
            embed.setDescription("目前沒有資料。");
            return embed;
        }

        int startIndex = (clampedPage - 1) * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, rows.size());
        StringBuilder content = new StringBuilder();
        if (TYPE_VOICE.equals(normalizedType)) {
            appendVoicePage(content, guild, castVoiceRows(rows), startIndex, endIndex);
        } else {
            appendMessagePage(content, guild, castMessageRows(rows), startIndex, endIndex);
        }
        embed.setDescription(content.toString());
        return embed;
    }

    private List<ActionRow> buildLeaderboardComponents(String type, int page, int totalPages) {
        String normalizedType = normalizeType(type);
        StringSelectMenu selectMenu = StringSelectMenu.create(LEADERBOARD_SELECT_ID)
                .addOptions(
                        SelectOption.of("訊息排行", TYPE_MESSAGES)
                                .withEmoji(Emoji.fromUnicode("💬"))
                                .withDefault(TYPE_MESSAGES.equals(normalizedType)),
                        SelectOption.of("語音排行", TYPE_VOICE)
                                .withEmoji(Emoji.fromUnicode("🔊"))
                                .withDefault(TYPE_VOICE.equals(normalizedType))
                )
                .build();

        Button prev = Button.secondary(pageButtonId(normalizedType, page - 1), "上一頁")
                .withDisabled(page <= 1);
        Button next = Button.secondary(pageButtonId(normalizedType, page + 1), "下一頁")
                .withDisabled(page >= totalPages);
        return List.of(
                ActionRow.of(selectMenu),
                ActionRow.of(prev, next)
        );
    }

    private int computeTotalPages(Guild guild, String type) {
        String normalizedType = normalizeType(type);
        int size = TYPE_VOICE.equals(normalizedType)
                ? statsService.getTopVoiceUsers(guild.getIdLong(), QUERY_LIMIT).size()
                : statsService.getTopUsers(guild.getIdLong(), QUERY_LIMIT).size();
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static void appendMessagePage(StringBuilder builder, Guild guild, List<UserMessageCount> rows, int start, int end) {
        for (int i = start; i < end; i++) {
            UserMessageCount row = rows.get(i);
            int rank = i + 1;
            builder.append(rank)
                    .append(". ")
                    .append(resolveMention(guild, row.userId()))
                    .append(" - ")
                    .append(row.messageCount())
                    .append("\n");
        }
    }

    private static void appendVoicePage(StringBuilder builder, Guild guild, List<UserVoiceTime> rows, int start, int end) {
        for (int i = start; i < end; i++) {
            UserVoiceTime row = rows.get(i);
            int rank = i + 1;
            builder.append(rank)
                    .append(". ")
                    .append(resolveMention(guild, row.userId()))
                    .append(" - ")
                    .append(formatDuration(row.voiceSeconds()))
                    .append("\n");
        }
    }

    private static String resolveMention(Guild guild, long userId) {
        Member member = guild.getMemberById(userId);
        if (member != null) {
            return member.getAsMention();
        }
        return "<@" + userId + ">";
    }

    private static String formatDuration(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainingSeconds = seconds % 60L;
        return String.format("%dh %dm %ds", hours, minutes, remainingSeconds);
    }

    private static String buildVoiceSessionKey(long guildId, long userId) {
        return guildId + ":" + userId;
    }

    private static String normalizeType(String raw) {
        return TYPE_VOICE.equals(raw) ? TYPE_VOICE : TYPE_MESSAGES;
    }

    private static String pageButtonId(String type, int page) {
        int safePage = Math.max(1, page);
        return LEADERBOARD_PAGE_BTN_PREFIX + normalizeType(type) + ":" + safePage;
    }

    private static int parsePositiveInt(String raw, int fallback) {
        try {
            int parsed = Integer.parseInt(raw);
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<UserMessageCount> castMessageRows(List<?> rows) {
        return (List<UserMessageCount>) rows;
    }

    @SuppressWarnings("unchecked")
    private static List<UserVoiceTime> castVoiceRows(List<?> rows) {
        return (List<UserVoiceTime>) rows;
    }

}
