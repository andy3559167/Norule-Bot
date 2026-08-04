package com.norule.musicbot.domain.music;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicPlayerServiceSpotifyFailureTest {
    @Test
    void explicitGeneratedPlaylistFailureWinsOverUnavailable404Inspection() {
        SpotifyPlaylistInspector.Inspection inspection = new SpotifyPlaylistInspector.Inspection(
                SpotifyPlaylistInspector.Outcome.UNAVAILABLE,
                null,
                404
        );
        FriendlyException failure = friendly(
                "Spotify load failed",
                new IllegalStateException(
                        "Spotify generated playlists are no longer accessible via anonymous tokens."
                )
        );

        AudioLoadFailureClassifier.Category finalCategory =
                MusicPlayerService.resolveLoadFailureCategory(failure, inspection);

        assertEquals(AudioLoadFailureClassifier.Category.SPOTIFY_GENERATED_PLAYLIST_UNAVAILABLE, finalCategory);
        assertEquals(404, inspection.statusCode());
        assertEquals(SpotifyPlaylistInspector.PlaylistClassification.UNKNOWN, inspection.classification());
    }

    @Test
    void usesInspectionOnlyWhenThrowableIsUnknown() {
        SpotifyPlaylistInspector.Inspection inspection = new SpotifyPlaylistInspector.Inspection(
                SpotifyPlaylistInspector.Outcome.SPOTIFY_AUTH_FAILED,
                null,
                401
        );

        assertEquals(
                AudioLoadFailureClassifier.Category.SPOTIFY_AUTH_FAILED,
                MusicPlayerService.resolveLoadFailureCategory(
                        friendly("Unexpected Spotify response", null),
                        inspection
                )
        );
    }

    private FriendlyException friendly(String message, Throwable cause) {
        return new FriendlyException(message, FriendlyException.Severity.SUSPICIOUS, cause);
    }
}
