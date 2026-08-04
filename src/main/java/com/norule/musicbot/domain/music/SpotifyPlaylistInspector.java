package com.norule.musicbot.domain.music;

import com.norule.musicbot.config.domain.MusicConfig;

import java.util.concurrent.CompletableFuture;

public interface SpotifyPlaylistInspector {
    CompletableFuture<Inspection> inspect(String playlistUrl,
                                          String requestContext,
                                          MusicConfig.Spotify config);

    static SpotifyPlaylistInspector noOp() {
        return (playlistUrl, requestContext, config) ->
                CompletableFuture.completedFuture(Inspection.unavailable());
    }

    enum PlaylistClassification {
        USER_PLAYLIST,
        SPOTIFY_OWNED_PLAYLIST,
        UNKNOWN
    }

    enum Outcome {
        READABLE,
        SPOTIFY_PLAYLIST_EMPTY,
        SPOTIFY_RESTRICTED_OR_PERSONALIZED,
        SPOTIFY_AUTH_FAILED,
        SPOTIFY_RATE_LIMITED,
        UNAVAILABLE
    }

    record Metadata(String ownerId,
                    Boolean publicPlaylist,
                    String name,
                    String description,
                    int declaredItemCount) {
        public PlaylistClassification classification() {
            if (ownerId == null) {
                return PlaylistClassification.USER_PLAYLIST;
            }
            return "spotify".equalsIgnoreCase(ownerId.trim())
                    ? PlaylistClassification.SPOTIFY_OWNED_PLAYLIST
                    : PlaylistClassification.USER_PLAYLIST;
        }
    }

    record Inspection(Outcome outcome, Metadata metadata) {
        public static Inspection unavailable() {
            return new Inspection(Outcome.UNAVAILABLE, null);
        }

        public PlaylistClassification classification() {
            return metadata == null ? PlaylistClassification.UNKNOWN : metadata.classification();
        }
    }
}
