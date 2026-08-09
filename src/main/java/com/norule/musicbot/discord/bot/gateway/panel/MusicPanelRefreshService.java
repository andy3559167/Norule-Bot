package com.norule.musicbot.discord.bot.gateway.panel;

import com.norule.musicbot.discord.bot.app.MusicCommandService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class MusicPanelRefreshService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MusicPanelRefreshService.class);

    private final MusicCommandService owner;
    private final MusicPanelStateStore stateStore;
    private final MusicPanelRenderer panelRenderer;
    private final ScheduledExecutorService scheduler;
    private final long panelPeriodicRefreshMs;
    private final long panelMinEditIntervalMs;
    private final PanelRefreshFailurePolicy failurePolicy;

    public MusicPanelRefreshService(MusicCommandService owner,
                                    MusicPanelStateStore stateStore,
                                    MusicPanelRenderer panelRenderer,
                                    ScheduledExecutorService scheduler,
                                    long panelPeriodicRefreshMs,
                                    long panelMinEditIntervalMs) {
        this.owner = owner;
        this.stateStore = stateStore;
        this.panelRenderer = panelRenderer;
        this.scheduler = scheduler;
        this.panelPeriodicRefreshMs = panelPeriodicRefreshMs;
        this.panelMinEditIntervalMs = panelMinEditIntervalMs;
        this.failurePolicy = new PanelRefreshFailurePolicy();
    }

    public void createPanelMessageWithFeedback(Guild guild, TextChannel channel, String lang, Runnable onSuccess, Consumer<String> onError) {
        Permission missingPermission = missingRefreshPermission(guild, channel);
        if (missingPermission != null) {
            logOperationalFailure(guild.getIdLong(), channel.getIdLong(), 0L,
                    "MISSING_PERMISSION", missingPermission);
            String missing = owner.formatMissingPermissionsForPanel(guild.getSelfMember(), channel,
                    Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS);
            onError.accept(owner.i18nService().t(lang, "general.missing_permissions", java.util.Map.of("permissions", missing)));
            return;
        }
        try {
            channel.sendMessageEmbeds(panelRenderer.panelEmbed(guild, lang).build())
                    .setComponents(panelRenderer.panelRows(lang, guild.getIdLong()))
                    .queue(message -> {
                        long guildId = guild.getIdLong();
                        stateStore.putPanelRef(guildId, new MusicPanelStateStore.PanelRef(channel.getIdLong(), message.getIdLong()));
                        stateStore.putLastSignature(guildId, owner.panelSignature(guild));
                        stateStore.putLastRefreshAt(guildId, System.currentTimeMillis());
                        owner.musicService().setGuildStateListener(guildId, () -> refreshPanel(guildId));
                        failurePolicy.clearChannel(guildId, channel.getIdLong());
                        onSuccess.run();
                    }, error -> {
                        handlePanelFailure(guild.getIdLong(), channel.getIdLong(), 0L, error, false);
                        onError.accept(safeErrorMessage(error));
                    });
        } catch (RuntimeException failure) {
            handlePanelFailure(guild.getIdLong(), channel.getIdLong(), 0L, failure, false);
            onError.accept(safeErrorMessage(failure));
        }
    }

    public void refreshPanel(long guildId) {
        runRefreshSafely(guildId, () -> refreshPanelInternal(guildId, true));
    }

    public void refreshPanelPeriodic(long guildId) {
        runRefreshSafely(guildId, () -> refreshPanelInternal(guildId, false));
    }

    public void refreshPanelMessage(Guild guild, TextChannel channel, long messageId, boolean force) {
        refreshPanelMessage(guild, channel, messageId, force, false);
    }

    public void refreshPanelMessage(Guild guild, TextChannel channel, long messageId, boolean force, boolean immediate) {
        runRefreshSafely(guild.getIdLong(),
                () -> refreshPanelMessageInternal(guild, channel, messageId, force, immediate));
    }

    private void refreshPanelMessageInternal(Guild guild, TextChannel channel, long messageId, boolean force, boolean immediate) {
        long guildId = guild.getIdLong();
        MusicPanelStateStore.PanelRef active = stateStore.getPanelRef(guildId);
        if (active == null || active.channelId != channel.getIdLong() || active.messageId != messageId) {
            return;
        }
        Permission missingPermission = missingRefreshPermission(guild, channel);
        if (missingPermission != null) {
            logOperationalFailure(guildId, channel.getIdLong(), messageId,
                    "MISSING_PERMISSION", missingPermission);
            return;
        }
        long now = System.currentTimeMillis();
        long lastRefresh = stateStore.getLastRefreshAt(guildId);
        if (!immediate && now - lastRefresh < panelMinEditIntervalMs) {
            scheduleDelayedPanelRefresh(guildId, panelMinEditIntervalMs - (now - lastRefresh));
            return;
        }
        String signature = owner.panelSignature(guild);
        if (!force && signature.equals(stateStore.getLastSignature(guildId))) {
            return;
        }
        String lang = owner.lang(guildId);
        channel.editMessageEmbedsById(messageId, panelRenderer.panelEmbed(guild, lang).build())
                .setComponents(panelRenderer.panelRows(lang, guildId))
                .queue(success -> {
                    stateStore.putLastSignature(guildId, signature);
                    stateStore.putLastRefreshAt(guildId, System.currentTimeMillis());
                    failurePolicy.clearChannel(guildId, channel.getIdLong());
                }, error -> handlePanelFailure(
                        guildId,
                        channel.getIdLong(),
                        messageId,
                        error,
                        true
                ));
    }

    private void refreshPanelInternal(long guildId, boolean force) {
        MusicPanelStateStore.PanelRef ref = stateStore.getPanelRef(guildId);
        JDA currentJda = owner.currentJda();
        if (ref == null || currentJda == null) {
            return;
        }
        if (!stateStore.startRefreshing(guildId)) {
            return;
        }
        try {
            Guild guild = currentJda.getGuildById(guildId);
            if (guild == null) {
                stateStore.compareAndClearPanelState(guildId, ref.channelId, ref.messageId);
                return;
            }
            if (!force) {
                if (owner.musicService().getCurrentTitle(guild) == null) {
                    return;
                }
                long now = System.currentTimeMillis();
                long last = stateStore.getLastRefreshAt(guildId);
                if (now - last < panelPeriodicRefreshMs) {
                    return;
                }
            }
            long now = System.currentTimeMillis();
            long lastRefresh = stateStore.getLastRefreshAt(guildId);
            if (now - lastRefresh < panelMinEditIntervalMs) {
                scheduleDelayedPanelRefresh(guildId, panelMinEditIntervalMs - (now - lastRefresh));
                return;
            }
            TextChannel channel = guild.getTextChannelById(ref.channelId);
            if (channel == null) {
                logOperationalFailure(guildId, ref.channelId, ref.messageId, "UNKNOWN_CHANNEL", null);
                stateStore.compareAndClearPanelState(guildId, ref.channelId, ref.messageId);
                return;
            }
            Permission missingPermission = missingRefreshPermission(guild, channel);
            if (missingPermission != null) {
                logOperationalFailure(guildId, channel.getIdLong(), ref.messageId,
                        "MISSING_PERMISSION", missingPermission);
                return;
            }
            String signature = owner.panelSignature(guild);
            if (signature.equals(stateStore.getLastSignature(guildId))) {
                return;
            }
            String lang = owner.lang(guildId);
            channel.editMessageEmbedsById(ref.messageId, panelRenderer.panelEmbed(guild, lang).build())
                    .setComponents(panelRenderer.panelRows(lang, guildId))
                    .queue(success -> {
                        stateStore.putLastSignature(guildId, signature);
                        stateStore.putLastRefreshAt(guildId, System.currentTimeMillis());
                        failurePolicy.clearChannel(guildId, channel.getIdLong());
                    }, error -> handlePanelFailure(
                            guildId,
                            channel.getIdLong(),
                            ref.messageId,
                            error,
                            true
                    ));
        } finally {
            stateStore.finishRefreshing(guildId);
        }
    }

    private void scheduleDelayedPanelRefresh(long guildId, long delayMs) {
        if (delayMs <= 0L) {
            scheduler.execute(() -> refreshPanel(guildId));
            return;
        }
        ScheduledFuture<?> existing = stateStore.getDelayedRefreshTask(guildId);
        if (existing != null && !existing.isDone()) {
            return;
        }
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            stateStore.removeDelayedRefreshTask(guildId);
            refreshPanel(guildId);
        }, delayMs, TimeUnit.MILLISECONDS);
        stateStore.putDelayedRefreshTask(guildId, future);
    }

    private Permission missingRefreshPermission(Guild guild, TextChannel channel) {
        return failurePolicy.firstMissingPermission(
                permission -> guild.getSelfMember().hasPermission(channel, permission)
        );
    }

    private void runRefreshSafely(long guildId, Runnable refreshAction) {
        try {
            refreshAction.run();
        } catch (RuntimeException failure) {
            MusicPanelStateStore.PanelRef ref = stateStore.getPanelRef(guildId);
            long channelId = ref == null ? 0L : ref.channelId;
            long messageId = ref == null ? 0L : ref.messageId;
            handlePanelFailure(guildId, channelId, messageId, failure, true);
        }
    }

    private void handlePanelFailure(long guildId,
                                    long channelId,
                                    long messageId,
                                    Throwable failure,
                                    boolean clearStaleState) {
        PanelRefreshFailurePolicy.PanelFailure classified = failurePolicy.classify(failure);
        if (classified.disposition() == PanelRefreshFailurePolicy.FailureDisposition.UNEXPECTED) {
            LOGGER.error(
                    "[NoRule] Music panel refresh failed unexpectedly: guildId={} channelId={} messageId={}",
                    guildId,
                    channelId,
                    messageId,
                    failure
            );
            return;
        }

        logOperationalFailure(guildId, channelId, messageId, classified.reason(), classified.permission());
        if (clearStaleState
                && classified.disposition() == PanelRefreshFailurePolicy.FailureDisposition.CLEAR_STATE) {
            stateStore.compareAndClearPanelState(guildId, channelId, messageId);
        }
    }

    private void logOperationalFailure(long guildId,
                                       long channelId,
                                       long messageId,
                                       String reason,
                                       Permission permission) {
        if (!failurePolicy.shouldLog(guildId, channelId, messageId, reason, System.currentTimeMillis())) {
            return;
        }
        if (permission != null) {
            LOGGER.warn(
                    "[NoRule] Music panel refresh skipped: guildId={} channelId={} messageId={} reason={} permission={}",
                    guildId,
                    channelId,
                    messageId,
                    reason,
                    permission
            );
            return;
        }
        LOGGER.warn(
                "[NoRule] Music panel refresh skipped: guildId={} channelId={} messageId={} reason={}",
                guildId,
                channelId,
                messageId,
                reason
        );
    }

    private String safeErrorMessage(Throwable failure) {
        return failure.getMessage() == null ? "unknown error" : failure.getMessage();
    }
}
