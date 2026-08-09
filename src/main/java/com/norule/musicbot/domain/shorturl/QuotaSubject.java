package com.norule.musicbot.domain.shorturl;

public record QuotaSubject(
        AccessTier accessTier,
        String quotaGroupId,
        MediaOwnerType ownerType,
        String ownerId,
        String deviceIdHash,
        String ipHash
) {
    public QuotaSubject {
        accessTier = accessTier == null ? AccessTier.ANONYMOUS : accessTier;
        quotaGroupId = safe(quotaGroupId);
        ownerType = ownerType == null ? MediaOwnerType.ANONYMOUS_DEVICE : ownerType;
        ownerId = safe(ownerId);
        deviceIdHash = safe(deviceIdHash);
        ipHash = safe(ipHash);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
