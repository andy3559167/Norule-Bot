package com.norule.musicbot.service.shorturl;

import com.norule.musicbot.shorturl.InMemoryMediaSecurityRepository;
import com.norule.musicbot.shorturl.SqliteMediaSecurityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaPasswordAttemptGuardTest {
    @TempDir
    Path tempDir;

    @Test
    void firstWrongPasswordCreatesBackoffWithoutSleepingWorker() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-09T12:00:00Z"));
        AtomicInteger verifications = new AtomicInteger();
        MediaPasswordAttemptGuard guard = guard(new InMemoryMediaSecurityRepository(), clock, 8, 20, 100);

        MediaPasswordAttemptGuard.Result first = guard.verify("203.0.113.5", "share1", () -> {
            verifications.incrementAndGet();
            return false;
        });
        MediaPasswordAttemptGuard.Result earlyRetry = guard.verify("203.0.113.5", "share1", () -> {
            verifications.incrementAndGet();
            return false;
        });

        assertEquals(MediaPasswordAttemptGuard.Status.INVALID_PASSWORD, first.status());
        assertEquals(1L, first.retryAfterSeconds());
        assertEquals(MediaPasswordAttemptGuard.Status.RATE_LIMITED, earlyRetry.status());
        assertEquals(1, verifications.get(), "cooldown requests must skip PBKDF2");
    }

    @Test
    void fifthFailureLocksAndRestartKeepsLock() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-09T12:00:00Z"));
        InMemoryMediaSecurityRepository repository = new InMemoryMediaSecurityRepository();
        MediaPasswordAttemptGuard guard = guard(repository, clock, 8, 20, 100);
        AtomicInteger verifications = new AtomicInteger();

        MediaPasswordAttemptGuard.Result result = null;
        long[] cooldowns = {0L, 1_000L, 2_000L, 4_000L, 8_000L};
        for (long cooldown : cooldowns) {
            clock.advanceMillis(cooldown);
            result = guard.verify("203.0.113.5", "share1", () -> {
                verifications.incrementAndGet();
                return false;
            });
        }

        assertNotNull(result);
        assertEquals(MediaPasswordAttemptGuard.Status.LOCKED, result.status());
        assertEquals(5, verifications.get());

        MediaPasswordAttemptGuard restarted = guard(repository, clock, 8, 20, 100);
        MediaPasswordAttemptGuard.Result locked = restarted.verify("203.0.113.5", "share1", () -> {
            verifications.incrementAndGet();
            return true;
        });
        assertEquals(MediaPasswordAttemptGuard.Status.LOCKED, locked.status());
        assertEquals(5, verifications.get(), "locked request must skip password hashing");
    }

    @Test
    void successfulPasswordClearsFailureSequenceAndLocksAreIndependent() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-09T12:00:00Z"));
        MediaPasswordAttemptGuard guard = guard(new InMemoryMediaSecurityRepository(), clock, 8, 20, 100);

        guard.verify("203.0.113.5", "share1", () -> false);
        clock.advanceMillis(1_000L);
        assertTrue(guard.verify("203.0.113.5", "share1", () -> true).isSuccess());
        assertEquals(MediaPasswordAttemptGuard.Status.INVALID_PASSWORD,
                guard.verify("203.0.113.5", "share1", () -> false).status());
        assertEquals(MediaPasswordAttemptGuard.Status.INVALID_PASSWORD,
                guard.verify("203.0.113.6", "share1", () -> false).status());
        assertEquals(MediaPasswordAttemptGuard.Status.INVALID_PASSWORD,
                guard.verify("203.0.113.5", "share2", () -> false).status());
    }

    @Test
    void perIpSprayLimitCoversDifferentShareCodes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-09T12:00:00Z"));
        MediaPasswordAttemptGuard guard = guard(new InMemoryMediaSecurityRepository(), clock, 8, 2, 3);

        assertTrue(guard.verify("203.0.113.5", "share1", () -> true).isSuccess());
        assertTrue(guard.verify("203.0.113.5", "share2", () -> true).isSuccess());
        assertEquals(MediaPasswordAttemptGuard.Status.RATE_LIMITED,
                guard.verify("203.0.113.5", "share3", () -> true).status());
    }

    @Test
    void globalVerificationConcurrencyNeverExceedsConfiguredLimit() throws Exception {
        MediaPasswordAttemptGuard guard = guard(new InMemoryMediaSecurityRepository(),
                new MutableClock(Instant.parse("2026-08-09T12:00:00Z")), 1, 20, 100);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> guard.verify("203.0.113.5", "share1", () -> {
                entered.countDown();
                try {
                    return release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            MediaPasswordAttemptGuard.Result busy = guard.verify("203.0.113.6", "share2", () -> true);
            assertEquals(MediaPasswordAttemptGuard.Status.BUSY, busy.status());
            release.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS).isSuccess());
        }
        assertEquals(1, guard.peakConcurrency());
    }

    @Test
    void sqliteRestartPreservesActivePasswordLock() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-09T12:00:00Z"));
        Path database = tempDir.resolve("password-lock.db");
        MediaPasswordAttemptGuard first = new MediaPasswordAttemptGuard(
                new SqliteMediaSecurityRepository(database), options(8, 20, 100),
                "test-hmac-secret", clock);
        long[] cooldowns = {0L, 1_000L, 2_000L, 4_000L, 8_000L};
        for (long cooldown : cooldowns) {
            clock.advanceMillis(cooldown);
            first.verify("203.0.113.5", "share1", () -> false);
        }

        AtomicInteger verifications = new AtomicInteger();
        MediaPasswordAttemptGuard restarted = new MediaPasswordAttemptGuard(
                new SqliteMediaSecurityRepository(database), options(8, 20, 100),
                "test-hmac-secret", clock);
        assertEquals(MediaPasswordAttemptGuard.Status.LOCKED,
                restarted.verify("203.0.113.5", "share1", () -> {
                    verifications.incrementAndGet();
                    return true;
                }).status());
        assertEquals(0, verifications.get());
    }

    private MediaPasswordAttemptGuard guard(InMemoryMediaSecurityRepository repository,
                                            Clock clock, int concurrency,
                                            int perMinute, int perTenMinutes) {
        return new MediaPasswordAttemptGuard(repository,
                options(concurrency, perMinute, perTenMinutes),
                "test-hmac-secret", clock);
    }

    private MediaPasswordAttemptGuard.Options options(int concurrency, int perMinute,
                                                       int perTenMinutes) {
        return new MediaPasswordAttemptGuard.Options(true, 5, 10L * 60_000L,
                10L * 60_000L, 1_000L, 2, 30_000L,
                concurrency, perMinute, perTenMinutes);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
        }
    }
}
