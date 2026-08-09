package com.norule.musicbot.shorturl;

import com.norule.musicbot.domain.shorturl.MediaPasswordAttemptLock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InMemoryMediaSecurityRepository implements MediaSecurityRepository {
    private record Identity(String type, String hash) {
    }

    private record Link(String quotaGroupId, String discordUserId, long linkedAt, long lastSeenAt) {
    }

    private record UploadEvent(String quotaGroupId, long createdAt, long sizeBytes, boolean success) {
    }

    private final Map<String, MediaPasswordAttemptLock> locks = new HashMap<>();
    private final Map<Identity, Link> links = new HashMap<>();
    private final List<UploadEvent> events = new ArrayList<>();

    @Override
    public synchronized MediaPasswordAttemptLock findPasswordAttemptLock(String shareCode, String ipHash) {
        return locks.get(lockKey(shareCode, ipHash));
    }

    @Override
    public synchronized void savePasswordAttemptLock(MediaPasswordAttemptLock lock) {
        locks.put(lockKey(lock.shareCode(), lock.ipHash()), lock);
    }

    @Override
    public synchronized void deletePasswordAttemptLock(String shareCode, String ipHash) {
        locks.remove(lockKey(shareCode, ipHash));
    }

    @Override
    public synchronized String resolveOrCreateQuotaGroup(String identityType, String identityHash,
                                                         String linkedDiscordUserId, long nowMillis) {
        Identity identity = new Identity(identityType, identityHash);
        Link existing = links.get(identity);
        if (existing != null) {
            links.put(identity, new Link(existing.quotaGroupId(), existing.discordUserId(),
                    existing.linkedAt(), nowMillis));
            return existing.quotaGroupId();
        }
        String groupId = "Q" + UUID.randomUUID().toString().replace("-", "");
        links.put(identity, new Link(groupId, safe(linkedDiscordUserId), nowMillis, nowMillis));
        return groupId;
    }

    @Override
    public synchronized IdentityMergeResult mergeAnonymousDeviceIntoDiscord(String deviceHash,
                                                                            String discordIdentityHash,
                                                                            String discordUserId,
                                                                            long nowMillis,
                                                                            long recentActivityCutoffMillis,
                                                                            long accountSwitchCooldownMillis) {
        Identity deviceIdentity = new Identity("ANON_DEVICE", deviceHash);
        Link device = links.get(deviceIdentity);
        if (device == null || device.lastSeenAt() < recentActivityCutoffMillis) {
            return new IdentityMergeResult(IdentityMergeStatus.NO_RECENT_DEVICE_ACTIVITY, "");
        }
        if (!device.discordUserId().isBlank() && !device.discordUserId().equals(discordUserId)
                && nowMillis - device.linkedAt() < accountSwitchCooldownMillis) {
            return new IdentityMergeResult(IdentityMergeStatus.ACCOUNT_SWITCH_BLOCKED, device.quotaGroupId());
        }
        Identity discordIdentity = new Identity("DISCORD", discordIdentityHash);
        Link discord = links.get(discordIdentity);
        if (discord != null && discord.quotaGroupId().equals(device.quotaGroupId())
                && discordUserId.equals(device.discordUserId())) {
            return new IdentityMergeResult(IdentityMergeStatus.ALREADY_MERGED, device.quotaGroupId());
        }
        String targetGroup = discord == null ? device.quotaGroupId() : discord.quotaGroupId();
        if (!targetGroup.equals(device.quotaGroupId())) {
            String sourceGroup = device.quotaGroupId();
            links.replaceAll((identity, link) -> sourceGroup.equals(link.quotaGroupId())
                    ? new Link(targetGroup, link.discordUserId(), link.linkedAt(), link.lastSeenAt()) : link);
            for (int i = 0; i < events.size(); i++) {
                UploadEvent event = events.get(i);
                if (sourceGroup.equals(event.quotaGroupId())) {
                    events.set(i, new UploadEvent(targetGroup, event.createdAt(), event.sizeBytes(), event.success()));
                }
            }
        }
        links.put(deviceIdentity, new Link(targetGroup, discordUserId, nowMillis, nowMillis));
        links.put(discordIdentity, new Link(targetGroup, discordUserId,
                discord == null ? nowMillis : discord.linkedAt(), nowMillis));
        return new IdentityMergeResult(IdentityMergeStatus.MERGED, targetGroup);
    }

    @Override
    public synchronized void recordUploadEvent(String quotaGroupId, String ipHash, long createdAt,
                                               long sizeBytes, boolean success) {
        events.add(new UploadEvent(quotaGroupId, createdAt, sizeBytes, success));
    }

    @Override
    public synchronized long countSuccessfulUploads(String quotaGroupId, long sinceMillis) {
        return events.stream().filter(event -> event.success()
                        && event.quotaGroupId().equals(quotaGroupId) && event.createdAt() >= sinceMillis)
                .count();
    }

    @Override
    public long activeStorageBytes(String quotaGroupId) {
        return 0L;
    }

    @Override
    public long globalManagedStorageBytes() {
        return 0L;
    }

    private String lockKey(String shareCode, String ipHash) {
        return safe(shareCode) + ':' + safe(ipHash);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
