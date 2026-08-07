package com.norule.musicbot.bootstrap;

import com.norule.musicbot.config.BotConfig;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpRetryException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

final class DiscordLoginRetryExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscordLoginRetryExecutor.class);
    private static final Set<Integer> RETRYABLE_HTTP_STATUSES = Set.of(500, 502, 503, 504);

    private final Sleeper sleeper;

    DiscordLoginRetryExecutor() {
        this(Thread::sleep);
    }

    DiscordLoginRetryExecutor(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    <T> T execute(Supplier<T> loginAttempt, BotConfig.Discord.LoginRetry config)
            throws InterruptedException {
        int maxAttempts = config.isEnabled() ? config.getMaxAttempts() : 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                LOGGER.info("[NoRule] Retrying Discord login: attempt={}/{}", attempt, maxAttempts);
            }
            try {
                T result = loginAttempt.get();
                if (attempt > 1) {
                    LOGGER.info("[NoRule] Discord login recovered after retry: attempt={}", attempt);
                }
                return result;
            } catch (RuntimeException failure) {
                boolean retryable = config.isEnabled() && isRetryable(failure);
                if (!retryable) {
                    LOGGER.error(
                            "[NoRule] Discord login failed permanently: reason={} attempt={}/{}",
                            describe(failure),
                            attempt,
                            maxAttempts
                    );
                    throw failure;
                }
                if (attempt >= maxAttempts) {
                    LOGGER.error(
                            "[NoRule] Discord login failed permanently after {} attempts: reason={}",
                            maxAttempts,
                            describe(failure)
                    );
                    throw failure;
                }

                long delaySeconds = retryDelaySeconds(config, attempt);
                LOGGER.warn(
                        "[NoRule] Discord login failed temporarily: {} attempt={}/{} retryIn={}s",
                        describe(failure),
                        attempt,
                        maxAttempts,
                        delaySeconds
                );
                sleep(delaySeconds);
            }
        }
        throw new IllegalStateException("Discord login retry loop ended unexpectedly");
    }

    static boolean isRetryable(Throwable failure) {
        Set<Throwable> visited = new HashSet<>();
        Throwable current = failure;
        while (current != null && visited.add(current)) {
            if (current instanceof InvalidTokenException) {
                return false;
            }
            if (current instanceof ErrorResponseException errorResponseException) {
                ErrorResponse errorResponse = errorResponseException.getErrorResponse();
                if (errorResponse == ErrorResponse.INVALID_TOKEN
                        || errorResponse == ErrorResponse.UNAUTHORIZED
                        || errorResponse == ErrorResponse.UNKNOWN_TOKEN) {
                    return false;
                }
                Response response = errorResponseException.getResponse();
                if (response != null && response.code > 0) {
                    return isRetryableHttpStatus(response.code);
                }
                if (response != null && isRetryableNetworkFailure(response.getException())) {
                    return true;
                }
            }
            if (current instanceof HttpRetryException httpRetryException) {
                return isRetryableHttpStatus(httpRetryException.responseCode());
            }
            if (isRetryableNetworkFailure(current) || hasRetryableNetworkMessage(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static boolean isRetryableHttpStatus(int status) {
        return RETRYABLE_HTTP_STATUSES.contains(status);
    }

    static long retryDelaySeconds(BotConfig.Discord.LoginRetry config, int failedAttempt) {
        long delay = config.getInitialDelaySeconds();
        for (int exponent = 1; exponent < failedAttempt && delay < config.getMaxDelaySeconds(); exponent++) {
            delay = Math.min(delay * 2L, config.getMaxDelaySeconds());
        }
        return Math.min(delay, config.getMaxDelaySeconds());
    }

    private static boolean isRetryableNetworkFailure(Throwable failure) {
        if (failure == null || failure instanceof SSLHandshakeException) {
            return false;
        }
        return failure instanceof SocketTimeoutException
                || failure instanceof HttpTimeoutException
                || failure instanceof ConnectException
                || failure instanceof NoRouteToHostException
                || failure instanceof UnknownHostException
                || failure instanceof SocketException
                || failure instanceof IOException;
    }

    private static boolean hasRetryableNetworkMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("timed out")
                || normalized.contains("timeout")
                || normalized.contains("connection reset")
                || normalized.contains("connection refused")
                || normalized.contains("temporary failure in name resolution")
                || normalized.contains("name or service not known")
                || normalized.contains("network is unreachable")
                || normalized.contains("unexpected end of stream")
                || normalized.contains("stream was reset")
                || normalized.contains("temporarily unavailable");
    }

    private static String describe(Throwable failure) {
        Throwable current = failure;
        Set<Throwable> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            if (current instanceof ErrorResponseException errorResponseException) {
                Response response = errorResponseException.getResponse();
                if (response != null && response.code > 0) {
                    return "status=" + response.code;
                }
            }
            if (isRetryableNetworkFailure(current)) {
                return "network=" + current.getClass().getSimpleName();
            }
            current = current.getCause();
        }
        return "type=" + failure.getClass().getSimpleName();
    }

    private void sleep(long delaySeconds) throws InterruptedException {
        try {
            sleeper.sleep(Math.multiplyExact(delaySeconds, 1_000L));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
