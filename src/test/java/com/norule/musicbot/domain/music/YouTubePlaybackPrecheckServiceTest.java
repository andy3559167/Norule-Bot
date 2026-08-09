package com.norule.musicbot.domain.music;

import com.norule.musicbot.config.domain.MusicConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YouTubePlaybackPrecheckServiceTest {
    private static final String VIDEO_ID = "dQw4w9WgXcQ";
    private static final Instant START = Instant.parse("2026-06-15T00:00:00Z");

    @Test
    void disabledConfigDoesNotCallStreamEndpoint() {
        MutableClock clock = new MutableClock(START);
        FakeStreamClient client = new FakeStreamClient();
        YouTubePlaybackPrecheckService service = service(strictPrecheck(false), client, clock);

        YouTubePlaybackPrecheckResult result = service.check("https://www.youtube.com/watch?v=" + VIDEO_ID);

        assertEquals(YouTubePlaybackPrecheckStatus.CONFIG_DISABLED, result.status());
        assertEquals(0, client.calls());
    }

    @Test
    void cachesOkResultUntilConfiguredTtlExpires() {
        MutableClock clock = new MutableClock(START);
        FakeStreamClient client = new FakeStreamClient();
        client.statusCode = 200;
        YouTubePlaybackPrecheckService service = service(strictPrecheck(true), client, clock);

        assertEquals(YouTubePlaybackPrecheckStatus.OK, service.check(VIDEO_ID).status());
        assertEquals(YouTubePlaybackPrecheckStatus.OK, service.check(VIDEO_ID).status());
        assertEquals(1, client.calls());

        clock.advance(Duration.ofHours(24).plusMillis(1));
        assertEquals(YouTubePlaybackPrecheckStatus.OK, service.check(VIDEO_ID).status());
        assertEquals(2, client.calls());
    }

    @Test
    void status200IsOk() {
        FakeStreamClient client = new FakeStreamClient();
        client.statusCode = 200;
        YouTubePlaybackPrecheckResult result = service(strictPrecheck(true), client, new MutableClock(START)).check(VIDEO_ID);

        assertEquals(YouTubePlaybackPrecheckStatus.OK, result.status());
        assertEquals(200, result.httpStatus());
    }

    @Test
    void status400IsPermanentAndCached() {
        FakeStreamClient client = new FakeStreamClient();
        client.statusCode = 400;
        MutableClock clock = new MutableClock(START);
        YouTubePlaybackPrecheckService service = service(strictPrecheck(true), client, clock);

        assertEquals(YouTubePlaybackPrecheckStatus.PERMANENT_FAILURE, service.check(VIDEO_ID).status());
        clock.advance(Duration.ofHours(24));
        assertEquals(YouTubePlaybackPrecheckStatus.PERMANENT_FAILURE, service.check(VIDEO_ID).status());
        assertEquals(1, client.calls());
    }

    @Test
    void connectionFailureIsUnavailableAndCachedForTheTemporaryTtl() {
        FakeStreamClient client = new FakeStreamClient();
        client.failure = new IOException("connection refused");
        YouTubePlaybackPrecheckService service = service(strictPrecheck(true), client, new MutableClock(START));

        assertEquals(YouTubePlaybackPrecheckStatus.LAVALINK_UNAVAILABLE, service.check(VIDEO_ID).status());
        assertEquals(YouTubePlaybackPrecheckStatus.LAVALINK_UNAVAILABLE, service.check(VIDEO_ID).status());
        assertEquals(1, client.calls());
    }

    @Test
    void timeoutIsClassifiedAndCachedForTheTemporaryTtl() {
        FakeStreamClient client = new FakeStreamClient();
        client.failure = new HttpTimeoutException("request timed out");
        YouTubePlaybackPrecheckService service = service(strictPrecheck(true), client, new MutableClock(START));

        assertEquals(YouTubePlaybackPrecheckStatus.TIMEOUT, service.check(VIDEO_ID).status());
        assertEquals(YouTubePlaybackPrecheckStatus.TIMEOUT, service.check(VIDEO_ID).status());
        assertEquals(1, client.calls());
    }

    @Test
    void temporaryFailureExpiresBeforePlayableOrPermanentResults() {
        MutableClock clock = new MutableClock(START);
        FakeStreamClient client = new FakeStreamClient();
        client.statusCode = 500;
        YouTubePlaybackPrecheckService service = service(strictPrecheck(true), client, clock);

        assertEquals(YouTubePlaybackPrecheckStatus.TEMPORARY_FAILURE, service.check(VIDEO_ID).status());
        clock.advance(Duration.ofMinutes(9));
        assertEquals(YouTubePlaybackPrecheckStatus.TEMPORARY_FAILURE, service.check(VIDEO_ID).status());
        assertEquals(1, client.calls());

        clock.advance(Duration.ofMinutes(1).plusMillis(1));
        client.statusCode = 200;
        assertEquals(YouTubePlaybackPrecheckStatus.OK, service.check(VIDEO_ID).status());
        assertEquals(2, client.calls());
    }

    @Test
    void authFailureUsesTheShortTtl() {
        MutableClock clock = new MutableClock(START);
        FakeStreamClient client = new FakeStreamClient();
        client.statusCode = 403;
        YouTubePlaybackPrecheckService service = service(strictPrecheck(true), client, clock);

        assertEquals(YouTubePlaybackPrecheckStatus.AUTH_REQUIRED, service.check(VIDEO_ID).status());
        clock.advance(Duration.ofMinutes(10).plusMillis(1));
        client.statusCode = 200;
        assertEquals(YouTubePlaybackPrecheckStatus.OK, service.check(VIDEO_ID).status());
        assertEquals(2, client.calls());
    }

    @Test
    void playbackFailureInvalidatesPlayableCacheAndStoresTemporaryFailure() {
        MutableClock clock = new MutableClock(START);
        FakeStreamClient client = new FakeStreamClient();
        client.statusCode = 200;
        YouTubePlaybackPrecheckService service = service(strictPrecheck(true), client, clock);

        assertEquals(YouTubePlaybackPrecheckStatus.OK, service.check(VIDEO_ID).status());
        service.recordPlaybackFailure(
                VIDEO_ID,
                new YoutubeFailureReport(
                        YoutubeFailureCategory.HTTP_FORBIDDEN,
                        YoutubeRecoveryClass.CLIENT_FALLBACK_MAY_HELP,
                        403,
                        java.util.List.of(),
                        false
                )
        );

        assertEquals(YouTubePlaybackPrecheckStatus.TEMPORARY_FAILURE, service.check(VIDEO_ID).status());
        assertEquals(1, client.calls());

        clock.advance(Duration.ofMinutes(10).plusMillis(1));
        assertEquals(YouTubePlaybackPrecheckStatus.OK, service.check(VIDEO_ID).status());
        assertEquals(2, client.calls());
    }

    @Test
    void nonYoutubeOrPlaylistInputSkipsStrictPrecheck() {
        FakeStreamClient client = new FakeStreamClient();
        YouTubePlaybackPrecheckService service = service(strictPrecheck(true), client, new MutableClock(START));

        assertEquals(YouTubePlaybackPrecheckStatus.SKIPPED, service.check("spotify:track:123").status());
        assertEquals(YouTubePlaybackPrecheckStatus.SKIPPED,
                service.check("https://www.youtube.com/playlist?list=PL1234567890").status());
        assertEquals(0, client.calls());
    }

    private static YouTubePlaybackPrecheckService service(MusicConfig.Youtube.StrictPrecheck config,
                                                          FakeStreamClient client,
                                                          Clock clock) {
        return new YouTubePlaybackPrecheckService(() -> config, client, clock);
    }

    private static MusicConfig.Youtube.StrictPrecheck strictPrecheck(boolean enabled) {
        return new MusicConfig.Youtube.StrictPrecheck(
                enabled,
                24,
                24,
                10,
                48,
                5000,
                "http://localhost:2333",
                "test-password"
        );
    }

    private static final class FakeStreamClient implements YouTubePlaybackPrecheckService.StreamStatusClient {
        private final AtomicInteger calls = new AtomicInteger();
        private int statusCode = 200;
        private IOException failure;

        int calls() {
            return calls.get();
        }

        @Override
        public int fetchStreamStatus(String baseUrl, String password, String videoId, Duration timeout) throws IOException {
            calls.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return statusCode;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
