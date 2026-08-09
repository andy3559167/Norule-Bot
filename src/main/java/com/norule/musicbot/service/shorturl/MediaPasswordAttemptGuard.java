package com.norule.musicbot.service.shorturl;

import com.norule.musicbot.domain.shorturl.MediaPasswordAttemptLock;
import com.norule.musicbot.shorturl.MediaSecurityRepository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

public final class MediaPasswordAttemptGuard {
    public enum Status {
        SUCCESS,
        INVALID_PASSWORD,
        RATE_LIMITED,
        LOCKED,
        BUSY
    }

    public record Result(Status status, long retryAfterSeconds) {
        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }

    public record Options(
            boolean enabled,
            int maxFailedAttempts,
            long failureWindowMillis,
            long lockMillis,
            long backoffInitialMillis,
            int backoffMultiplier,
            long backoffMaxMillis,
            int maxConcurrentVerifications,
            int maxVerificationRequestsPerMinute,
            int maxVerificationRequestsPerTenMinutes
    ) {
        public Options {
            maxFailedAttempts = Math.max(1, maxFailedAttempts);
            failureWindowMillis = Math.max(1_000L, failureWindowMillis);
            lockMillis = Math.max(1_000L, lockMillis);
            backoffInitialMillis = Math.max(0L, backoffInitialMillis);
            backoffMultiplier = Math.max(1, backoffMultiplier);
            backoffMaxMillis = Math.max(backoffInitialMillis, backoffMaxMillis);
            maxConcurrentVerifications = Math.max(1, maxConcurrentVerifications);
            maxVerificationRequestsPerMinute = Math.max(1, maxVerificationRequestsPerMinute);
            maxVerificationRequestsPerTenMinutes = Math.max(
                    maxVerificationRequestsPerMinute, maxVerificationRequestsPerTenMinutes);
        }

        public static Options defaults() {
            return new Options(true, 5, 10L * 60L * 1000L, 10L * 60L * 1000L,
                    1_000L, 2, 30_000L, 8, 20, 100);
        }
    }

    private static final long ONE_MINUTE_MILLIS = 60_000L;
    private static final long TEN_MINUTES_MILLIS = 10L * 60_000L;
    private static final int MAX_TRACKED_IPS = 20_000;
    private static final int ATTEMPT_LOCK_STRIPES = 256;

    private final MediaSecurityRepository repository;
    private final Options options;
    private final Clock clock;
    private final byte[] hmacSecret;
    private final PasswordVerificationConcurrencyLimiter concurrencyLimiter;
    private final Map<String, ArrayDeque<Long>> perIpRequests = new ConcurrentHashMap<>();
    private final ReentrantLock[] attemptLocks = new ReentrantLock[ATTEMPT_LOCK_STRIPES];

    public MediaPasswordAttemptGuard(MediaSecurityRepository repository, Options options,
                                     String hmacSecret) {
        this(repository, options, hmacSecret, Clock.systemUTC());
    }

    public MediaPasswordAttemptGuard(MediaSecurityRepository repository, Options options,
                                     String hmacSecret, Clock clock) {
        if (repository == null) {
            throw new IllegalArgumentException("repository cannot be null");
        }
        this.repository = repository;
        this.options = options == null ? Options.defaults() : options;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        String secret = hmacSecret == null || hmacSecret.isBlank()
                ? "norule-media-development-hmac-secret" : hmacSecret;
        this.hmacSecret = secret.getBytes(StandardCharsets.UTF_8);
        this.concurrencyLimiter = new PasswordVerificationConcurrencyLimiter(
                this.options.maxConcurrentVerifications());
        for (int index = 0; index < attemptLocks.length; index++) {
            attemptLocks[index] = new ReentrantLock();
        }
    }

    public Result verify(String clientIp, String shareCode, BooleanSupplier passwordVerifier) {
        if (passwordVerifier == null) {
            return new Result(Status.INVALID_PASSWORD, 0L);
        }
        if (!options.enabled()) {
            return passwordVerifier.getAsBoolean()
                    ? new Result(Status.SUCCESS, 0L)
                    : new Result(Status.INVALID_PASSWORD, 0L);
        }
        long now = clock.millis();
        String ipHash = hmac(clientIp);
        long ipRetryAfter = registerAndCheckIpRate(ipHash, now);
        if (ipRetryAfter > 0L) {
            return new Result(Status.RATE_LIMITED, secondsCeiling(ipRetryAfter));
        }

        ReentrantLock attemptLock = attemptLocks[Math.floorMod(
                (safe(shareCode) + ':' + ipHash).hashCode(), attemptLocks.length)];
        if (!attemptLock.tryLock()) {
            return new Result(Status.RATE_LIMITED, 1L);
        }
        try {
            return verifySerialized(ipHash, shareCode, passwordVerifier, now);
        } finally {
            attemptLock.unlock();
        }
    }

    private Result verifySerialized(String ipHash, String shareCode,
                                    BooleanSupplier passwordVerifier, long now) {
        MediaPasswordAttemptLock lock = repository.findPasswordAttemptLock(shareCode, ipHash);
        if (lock != null) {
            if (lock.lockedUntil() > now) {
                return new Result(Status.LOCKED, secondsCeiling(lock.lockedUntil() - now));
            }
            if (lock.nextAllowedAttemptAt() > now) {
                return new Result(Status.RATE_LIMITED,
                        secondsCeiling(lock.nextAllowedAttemptAt() - now));
            }
        }

        PasswordVerificationConcurrencyLimiter.Permit permit = concurrencyLimiter.tryAcquire();
        if (permit == null) {
            return new Result(Status.BUSY, 1L);
        }
        boolean verified;
        try (permit) {
            verified = passwordVerifier.getAsBoolean();
        }
        if (verified) {
            repository.deletePasswordAttemptLock(shareCode, ipHash);
            return new Result(Status.SUCCESS, 0L);
        }

        MediaPasswordAttemptLock updated = failedAttempt(shareCode, ipHash, lock, now);
        repository.savePasswordAttemptLock(updated);
        long retryMillis = updated.lockedUntil() > now
                ? updated.lockedUntil() - now
                : updated.nextAllowedAttemptAt() - now;
        return new Result(updated.lockedUntil() > now ? Status.LOCKED : Status.INVALID_PASSWORD,
                secondsCeiling(retryMillis));
    }

    public String hashClientIp(String clientIp) {
        return hmac(clientIp);
    }

    public int maximumConcurrency() {
        return concurrencyLimiter.maximumConcurrency();
    }

    public int peakConcurrency() {
        return concurrencyLimiter.peakVerifications();
    }

    private MediaPasswordAttemptLock failedAttempt(String shareCode, String ipHash,
                                                   MediaPasswordAttemptLock current, long now) {
        boolean withinWindow = current != null && now - current.firstFailureAt() <= options.failureWindowMillis();
        int failures = withinWindow ? current.failedAttempts() + 1 : 1;
        long firstFailureAt = withinWindow ? current.firstFailureAt() : now;
        if (failures >= options.maxFailedAttempts()) {
            long lockedUntil = now + options.lockMillis();
            return new MediaPasswordAttemptLock(shareCode, ipHash, failures, firstFailureAt,
                    now, lockedUntil, lockedUntil);
        }
        long delay = exponentialBackoff(failures);
        return new MediaPasswordAttemptLock(shareCode, ipHash, failures, firstFailureAt,
                now, now + delay, 0L);
    }

    private long exponentialBackoff(int failures) {
        long delay = options.backoffInitialMillis();
        for (int i = 1; i < failures && delay < options.backoffMaxMillis(); i++) {
            try {
                delay = Math.multiplyExact(delay, options.backoffMultiplier());
            } catch (ArithmeticException ignored) {
                return options.backoffMaxMillis();
            }
        }
        return Math.min(delay, options.backoffMaxMillis());
    }

    private long registerAndCheckIpRate(String ipHash, long now) {
        if (perIpRequests.size() >= MAX_TRACKED_IPS && !perIpRequests.containsKey(ipHash)) {
            removeStaleIpEntries(now);
            if (perIpRequests.size() >= MAX_TRACKED_IPS) {
                return ONE_MINUTE_MILLIS;
            }
        }
        ArrayDeque<Long> requests = perIpRequests.computeIfAbsent(ipHash, ignored -> new ArrayDeque<>());
        synchronized (requests) {
            long tenMinuteCutoff = now - TEN_MINUTES_MILLIS;
            while (!requests.isEmpty() && requests.peekFirst() <= tenMinuteCutoff) {
                requests.removeFirst();
            }
            int minuteCount = 0;
            long minuteCutoff = now - ONE_MINUTE_MILLIS;
            for (Long timestamp : requests) {
                if (timestamp > minuteCutoff) {
                    minuteCount++;
                }
            }
            if (minuteCount >= options.maxVerificationRequestsPerMinute()) {
                Long oldestInMinute = requests.stream().filter(timestamp -> timestamp > minuteCutoff)
                        .findFirst().orElse(now);
                return Math.max(1L, oldestInMinute + ONE_MINUTE_MILLIS - now);
            }
            if (requests.size() >= options.maxVerificationRequestsPerTenMinutes()) {
                return Math.max(1L, requests.peekFirst() + TEN_MINUTES_MILLIS - now);
            }
            requests.addLast(now);
            return 0L;
        }
    }

    private void removeStaleIpEntries(long now) {
        long cutoff = now - TEN_MINUTES_MILLIS;
        perIpRequests.entrySet().removeIf(entry -> {
            ArrayDeque<Long> requests = entry.getValue();
            synchronized (requests) {
                while (!requests.isEmpty() && requests.peekFirst() <= cutoff) {
                    requests.removeFirst();
                }
                return requests.isEmpty();
            }
        });
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            byte[] digest = mac.doFinal((value == null ? "unknown" : value)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to HMAC media request identity", e);
        }
    }

    private long secondsCeiling(long millis) {
        return Math.max(1L, (Math.max(0L, millis) + 999L) / 1_000L);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
