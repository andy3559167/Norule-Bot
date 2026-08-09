package com.norule.musicbot.service.shorturl;

import com.norule.musicbot.domain.shorturl.AccessTier;
import com.norule.musicbot.domain.shorturl.MediaOwnerType;
import com.norule.musicbot.domain.shorturl.QuotaSubject;
import com.norule.musicbot.shorturl.MediaSecurityRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;

public final class AnonymousDeviceIdentityService {
    public record Options(boolean enabled, long mergeWindowMillis, long deviceLinkTtlMillis,
                          long accountSwitchCooldownMillis) {
        public Options {
            mergeWindowMillis = Math.max(60_000L, mergeWindowMillis);
            deviceLinkTtlMillis = Math.max(24L * 60L * 60L * 1000L, deviceLinkTtlMillis);
            accountSwitchCooldownMillis = Math.max(0L, accountSwitchCooldownMillis);
        }

        public static Options defaults() {
            return new Options(true, 120L * 60L * 1000L, 30L * 24L * 60L * 60L * 1000L,
                    24L * 60L * 60L * 1000L);
        }
    }

    public record DeviceIdentity(String token, boolean newlyCreated, QuotaSubject quotaSubject) {
    }

    public record AuthenticationResult(QuotaSubject quotaSubject,
                                       MediaSecurityRepository.IdentityMergeStatus mergeStatus) {
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int DEVICE_TOKEN_BYTES = 32;

    private final MediaSecurityRepository repository;
    private final Options options;
    private final Clock clock;
    private final byte[] quotaHmacSecret;
    private final byte[] deviceHmacSecret;

    public AnonymousDeviceIdentityService(MediaSecurityRepository repository, Options options,
                                          String quotaHmacSecret, String deviceHmacSecret) {
        this(repository, options, quotaHmacSecret, deviceHmacSecret, Clock.systemUTC());
    }

    public AnonymousDeviceIdentityService(MediaSecurityRepository repository, Options options,
                                          String quotaHmacSecret, String deviceHmacSecret, Clock clock) {
        if (repository == null) {
            throw new IllegalArgumentException("repository cannot be null");
        }
        this.repository = repository;
        this.options = options == null ? Options.defaults() : options;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.quotaHmacSecret = secretBytes(quotaHmacSecret, "norule-quota-development-secret");
        this.deviceHmacSecret = secretBytes(deviceHmacSecret, "norule-device-development-secret");
    }

    public DeviceIdentity resolveAnonymous(String rawDeviceToken, String clientIp) {
        boolean valid = isValidToken(rawDeviceToken);
        String token = valid ? rawDeviceToken : generateToken();
        String deviceHash = hmac(deviceHmacSecret, token);
        String ipHash = hmac(quotaHmacSecret, clientIp == null ? "unknown" : clientIp);
        long now = clock.millis();
        String quotaGroupId = repository.resolveOrCreateQuotaGroup(
                "ANON_DEVICE", deviceHash, "", now);
        return new DeviceIdentity(token, !valid,
                new QuotaSubject(AccessTier.ANONYMOUS, quotaGroupId,
                        MediaOwnerType.ANONYMOUS_DEVICE, deviceHash, deviceHash, ipHash));
    }

    public AuthenticationResult authenticate(String rawDeviceToken, String discordUserId,
                                             String clientIp) {
        if (discordUserId == null || discordUserId.isBlank()) {
            return new AuthenticationResult(resolveAnonymous(rawDeviceToken, clientIp).quotaSubject(),
                    MediaSecurityRepository.IdentityMergeStatus.NO_RECENT_DEVICE_ACTIVITY);
        }
        long now = clock.millis();
        String discordIdentityHash = hmac(quotaHmacSecret, discordUserId);
        String ipHash = hmac(quotaHmacSecret, clientIp == null ? "unknown" : clientIp);
        MediaSecurityRepository.IdentityMergeResult merge = null;
        String deviceHash = "";
        if (options.enabled() && isValidToken(rawDeviceToken)) {
            deviceHash = hmac(deviceHmacSecret, rawDeviceToken);
            merge = repository.mergeAnonymousDeviceIntoDiscord(
                    deviceHash, discordIdentityHash, discordUserId, now,
                    now - options.mergeWindowMillis(), options.accountSwitchCooldownMillis());
        }
        String quotaGroupId;
        MediaSecurityRepository.IdentityMergeStatus status;
        if (merge != null && !merge.quotaGroupId().isBlank()) {
            quotaGroupId = merge.quotaGroupId();
            status = merge.status();
        } else {
            quotaGroupId = repository.resolveOrCreateQuotaGroup(
                    "DISCORD", discordIdentityHash, discordUserId, now);
            status = merge == null
                    ? MediaSecurityRepository.IdentityMergeStatus.NO_RECENT_DEVICE_ACTIVITY
                    : merge.status();
        }
        return new AuthenticationResult(
                new QuotaSubject(AccessTier.AUTHENTICATED, quotaGroupId,
                        MediaOwnerType.DISCORD_USER, discordUserId, deviceHash, ipHash), status);
    }

    public String hashDeviceToken(String rawDeviceToken) {
        return isValidToken(rawDeviceToken) ? hmac(deviceHmacSecret, rawDeviceToken) : "";
    }

    public long deviceCookieMaxAgeSeconds() {
        return options.deviceLinkTtlMillis() / 1_000L;
    }

    private String generateToken() {
        byte[] token = new byte[DEVICE_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private boolean isValidToken(String token) {
        if (token == null || token.length() < 40 || token.length() > 48) {
            return false;
        }
        try {
            return Base64.getUrlDecoder().decode(token).length == DEVICE_TOKEN_BYTES;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String hmac(byte[] secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to HMAC media identity", e);
        }
    }

    private static byte[] secretBytes(String value, String fallback) {
        return (value == null || value.isBlank() ? fallback : value).getBytes(StandardCharsets.UTF_8);
    }
}
