package com.norule.musicbot.bootstrap;

import com.norule.musicbot.config.BotConfig;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.Response;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordLoginRetryExecutorTest {
    @Test
    void retriesSupportedDiscordServerErrors() throws InterruptedException {
        for (int status : List.of(500, 502, 503, 504)) {
            AtomicInteger attempts = new AtomicInteger();
            List<Long> sleeps = new ArrayList<>();
            DiscordLoginRetryExecutor executor = new DiscordLoginRetryExecutor(sleeps::add);

            String result = executor.execute(() -> {
                if (attempts.incrementAndGet() == 1) {
                    throw discordError(status);
                }
                return "ready";
            }, retryConfig(true, 8, 5, 60));

            assertEquals("ready", result);
            assertEquals(2, attempts.get());
            assertEquals(List.of(5_000L), sleeps);
        }
    }

    @Test
    void retriesSocketTimeoutAndUsesExponentialBackoff() throws InterruptedException {
        AtomicInteger attempts = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();
        DiscordLoginRetryExecutor executor = new DiscordLoginRetryExecutor(sleeps::add);

        assertEquals("ready", executor.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException(new SocketTimeoutException("test timeout"));
            }
            return "ready";
        }, retryConfig(true, 8, 5, 60)));

        assertEquals(List.of(5_000L, 10_000L), sleeps);
    }

    @Test
    void doesNotRetryUnauthorizedOrInvalidToken() {
        for (RuntimeException failure : List.of(discordError(401), new InvalidTokenException())) {
            AtomicInteger attempts = new AtomicInteger();
            DiscordLoginRetryExecutor executor = new DiscordLoginRetryExecutor(millis -> {
                throw new AssertionError("sleep should not be called");
            });

            assertThrows(RuntimeException.class, () -> executor.execute(() -> {
                attempts.incrementAndGet();
                throw failure;
            }, retryConfig(true, 8, 5, 60)));

            assertEquals(1, attempts.get());
        }
    }

    @Test
    void stopsAfterMaximumAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();
        DiscordLoginRetryExecutor executor = new DiscordLoginRetryExecutor(sleeps::add);

        assertThrows(ErrorResponseException.class, () -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw discordError(503);
        }, retryConfig(true, 3, 5, 60)));

        assertEquals(3, attempts.get());
        assertEquals(List.of(5_000L, 10_000L), sleeps);
    }

    @Test
    void disabledRetryKeepsFailFastBehavior() {
        AtomicInteger attempts = new AtomicInteger();
        DiscordLoginRetryExecutor executor = new DiscordLoginRetryExecutor(millis -> {
            throw new AssertionError("sleep should not be called");
        });

        assertThrows(ErrorResponseException.class, () -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw discordError(503);
        }, retryConfig(false, 8, 5, 60)));

        assertEquals(1, attempts.get());
    }

    @Test
    void restoresInterruptFlagWhenBackoffSleepIsInterrupted() {
        DiscordLoginRetryExecutor executor = new DiscordLoginRetryExecutor(millis -> {
            throw new InterruptedException("test interrupt");
        });

        try {
            assertThrows(InterruptedException.class, () -> executor.execute(
                    () -> {
                        throw discordError(503);
                    },
                    retryConfig(true, 8, 5, 60)
            ));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void classifierRejectsOtherStatusesAndCapsDelay() {
        BotConfig.Discord.LoginRetry config = retryConfig(true, 8, 40, 60);

        assertFalse(DiscordLoginRetryExecutor.isRetryableHttpStatus(401));
        assertFalse(DiscordLoginRetryExecutor.isRetryable(discordError(401)));
        assertTrue(DiscordLoginRetryExecutor.isRetryable(discordError(503)));
        assertEquals(40, DiscordLoginRetryExecutor.retryDelaySeconds(config, 1));
        assertEquals(60, DiscordLoginRetryExecutor.retryDelaySeconds(config, 2));
        assertEquals(60, DiscordLoginRetryExecutor.retryDelaySeconds(config, 7));
    }

    private BotConfig.Discord.LoginRetry retryConfig(
            boolean enabled,
            int maxAttempts,
            int initialDelaySeconds,
            int maxDelaySeconds
    ) {
        return BotConfig.Discord.LoginRetry.fromMap(Map.of(
                "enabled", enabled,
                "maxAttempts", maxAttempts,
                "initialDelaySeconds", initialDelaySeconds,
                "maxDelaySeconds", maxDelaySeconds
        ), null);
    }

    private ErrorResponseException discordError(int status) {
        okhttp3.Response rawResponse = new okhttp3.Response.Builder()
                .request(new Request.Builder().url("https://discord.test/api/v10/users/@me").build())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("test")
                .body(ResponseBody.create("{\"message\":\"test\",\"code\":0}", null))
                .build();
        Response response = new Response(rawResponse, 0L, Set.of());
        ErrorResponse error = status == 401 ? ErrorResponse.UNAUTHORIZED : ErrorResponse.SERVER_ERROR;
        return ErrorResponseException.create(error, response);
    }
}
