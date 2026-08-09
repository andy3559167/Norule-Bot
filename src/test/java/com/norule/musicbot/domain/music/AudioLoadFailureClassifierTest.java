package com.norule.musicbot.domain.music;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import dev.lavalink.youtube.AllClientsFailedException;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioLoadFailureClassifierTest {
    private final AudioLoadFailureClassifier classifier = new AudioLoadFailureClassifier();

    @Test
    void walksTheCauseChainForNetworkFailures() {
        FriendlyException dns = friendly("Connecting failed", new UnknownHostException("missing.example"));
        FriendlyException timeout = friendly("Playback failed", new SocketTimeoutException("Read timed out"));

        assertEquals(AudioLoadFailureClassifier.Category.DNS_FAILURE, classifier.classify(dns));
        assertEquals(AudioLoadFailureClassifier.Category.READ_TIMEOUT, classifier.classify(timeout));
        assertTrue(classifier.isRecoverable(timeout));
    }

    @Test
    void recognizesUnsupportedFormatsAndTrackStuck() {
        assertEquals(AudioLoadFailureClassifier.Category.UNSUPPORTED_FORMAT,
                classifier.classify(friendly("Unknown file format", null)));
        assertEquals(AudioLoadFailureClassifier.Category.TRACK_STUCK,
                classifier.classify(friendly("Track got stuck", null)));
        assertFalse(classifier.isRecoverable(friendly("Unknown file format", null)));
    }

    @Test
    void leavesUnknownRuntimeFailuresForUnexpectedErrorLogging() {
        assertEquals(AudioLoadFailureClassifier.Category.UNKNOWN,
                classifier.classify(new RuntimeException("unexpected state")));
    }

    @Test
    void classifiesSpotifyGeneratedPlaylistMessageDirectly() {
        FriendlyException failure = friendly(
                "Spotify generated playlists are no longer accessible via anonymous tokens.",
                null
        );

        AudioLoadFailureClassifier.Category category = classifier.classify(failure);

        assertEquals(AudioLoadFailureClassifier.Category.SPOTIFY_GENERATED_PLAYLIST_UNAVAILABLE, category);
        assertEquals("SPOTIFY_GENERATED_PLAYLIST_UNAVAILABLE", classifier.errorKey(category));
        assertTrue(classifier.isExpectedInputFailure(category));
    }

    @Test
    void givesNestedGeneratedPlaylistMessagePriorityOverOuter404() {
        FriendlyException failure = friendly(
                "Spotify playlist request failed with 404 Resource not found",
                new IllegalStateException(
                        "Spotify generated playlists are no longer accessible via anonymous tokens."
                )
        );

        assertEquals(
                AudioLoadFailureClassifier.Category.SPOTIFY_GENERATED_PLAYLIST_UNAVAILABLE,
                classifier.classify(failure)
        );
    }

    @Test
    void matchesGeneratedPlaylistMessageCaseInsensitively() {
        assertEquals(
                AudioLoadFailureClassifier.Category.SPOTIFY_GENERATED_PLAYLIST_UNAVAILABLE,
                classifier.classify(friendly(
                        "SPOTIFY GENERATED PLAYLISTS are unavailable with an ANONYMOUS TOKEN",
                        null
                ))
        );
    }

    @Test
    void matchesGeneratedPlaylistAndAnonymousTokensFragments() {
        assertEquals(
                AudioLoadFailureClassifier.Category.SPOTIFY_GENERATED_PLAYLIST_UNAVAILABLE,
                classifier.classify(friendly(
                        "Spotify generated playlists cannot be resolved using anonymous tokens in this environment",
                        null
                ))
        );
    }

    @Test
    void doesNotTreatOrdinarySpotifyHttpFailuresAsGeneratedPlaylists() {
        AudioLoadFailureClassifier.Category notFound = classifier.classify(
                friendly("Spotify playlist request failed: 404 Resource not found", null)
        );
        AudioLoadFailureClassifier.Category unauthorized = classifier.classify(
                friendly("Spotify request failed: 401 Unauthorized", null)
        );
        AudioLoadFailureClassifier.Category rateLimited = classifier.classify(
                friendly("Spotify request failed: 429 Too many requests", null)
        );
        AudioLoadFailureClassifier.Category missingPlaylist = classifier.classify(
                friendly("Playlist does not exist (404)", null)
        );

        assertEquals(AudioLoadFailureClassifier.Category.HTTP_NOT_FOUND, notFound);
        assertEquals(AudioLoadFailureClassifier.Category.UNKNOWN, unauthorized);
        assertEquals(AudioLoadFailureClassifier.Category.SOURCE_RATE_LIMITED, rateLimited);
        assertEquals(AudioLoadFailureClassifier.Category.HTTP_NOT_FOUND, missingPlaylist);
        assertNotEquals(AudioLoadFailureClassifier.Category.SPOTIFY_GENERATED_PLAYLIST_UNAVAILABLE, notFound);
        assertNotEquals(AudioLoadFailureClassifier.Category.SPOTIFY_GENERATED_PLAYLIST_UNAVAILABLE, unauthorized);
        assertNotEquals(AudioLoadFailureClassifier.Category.SPOTIFY_GENERATED_PLAYLIST_UNAVAILABLE, rateLimited);
        assertNotEquals(AudioLoadFailureClassifier.Category.SPOTIFY_GENERATED_PLAYLIST_UNAVAILABLE, missingPlaylist);
    }

    @Test
    void leavesYoutubeAggregateForTheDedicatedClassifier() {
        AllClientsFailedException failure = new AllClientsFailedException(List.of());

        assertEquals(AudioLoadFailureClassifier.Category.UNKNOWN, classifier.classify(failure));
    }

    private FriendlyException friendly(String message, Throwable cause) {
        return new FriendlyException(message, FriendlyException.Severity.SUSPICIOUS, cause);
    }
}
