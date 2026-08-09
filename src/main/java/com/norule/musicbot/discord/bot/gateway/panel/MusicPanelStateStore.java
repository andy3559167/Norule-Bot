package com.norule.musicbot.discord.bot.gateway.panel;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

public final class MusicPanelStateStore {
    private final Map<Long, PanelRef> panelByGuild = new ConcurrentHashMap<>();
    private final Map<Long, Long> panelLastRefreshAt = new ConcurrentHashMap<>();
    private final Map<Long, String> panelLastSignature = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> delayedPanelRefreshByGuild = new ConcurrentHashMap<>();
    private final Set<Long> panelRefreshingGuilds = ConcurrentHashMap.newKeySet();

    public Map<Long, PanelRef> panelRefs() {
        return panelByGuild;
    }

    public PanelRef getPanelRef(long guildId) {
        return panelByGuild.get(guildId);
    }

    public synchronized void putPanelRef(long guildId, PanelRef panelRef) {
        panelByGuild.put(guildId, panelRef);
    }

    public synchronized PanelRef removePanelRef(long guildId) {
        return panelByGuild.remove(guildId);
    }

    public synchronized void clearPanelState(long guildId) {
        panelByGuild.remove(guildId);
        panelLastSignature.remove(guildId);
        panelLastRefreshAt.remove(guildId);
    }

    public synchronized boolean compareAndClearPanelState(long guildId,
                                                          long expectedChannelId,
                                                          long expectedMessageId) {
        PanelRef active = panelByGuild.get(guildId);
        if (active == null
                || active.channelId != expectedChannelId
                || active.messageId != expectedMessageId
                || !panelByGuild.remove(guildId, active)) {
            return false;
        }
        panelLastSignature.remove(guildId);
        panelLastRefreshAt.remove(guildId);
        return true;
    }

    public long getLastRefreshAt(long guildId) {
        return panelLastRefreshAt.getOrDefault(guildId, 0L);
    }

    public void putLastRefreshAt(long guildId, long timestamp) {
        panelLastRefreshAt.put(guildId, timestamp);
    }

    public String getLastSignature(long guildId) {
        return panelLastSignature.get(guildId);
    }

    public void putLastSignature(long guildId, String signature) {
        panelLastSignature.put(guildId, signature);
    }

    public boolean startRefreshing(long guildId) {
        return panelRefreshingGuilds.add(guildId);
    }

    public void finishRefreshing(long guildId) {
        panelRefreshingGuilds.remove(guildId);
    }

    public ScheduledFuture<?> getDelayedRefreshTask(long guildId) {
        return delayedPanelRefreshByGuild.get(guildId);
    }

    public void putDelayedRefreshTask(long guildId, ScheduledFuture<?> task) {
        delayedPanelRefreshByGuild.put(guildId, task);
    }

    public void removeDelayedRefreshTask(long guildId) {
        delayedPanelRefreshByGuild.remove(guildId);
    }

    public ArrayList<Long> snapshotGuildIds() {
        return new ArrayList<>(panelByGuild.keySet());
    }

    public static final class PanelRef {
        public final long channelId;
        public final long messageId;

        public PanelRef(long channelId, long messageId) {
            this.channelId = channelId;
            this.messageId = messageId;
        }
    }
}
