package com.norule.musicbot.domain.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicPlayerServiceSpotifyInputTest {
    private static final String PLAYLIST_URL =
            "https://open.spotify.com/playlist/37i9dQZF1E4xOzG7Aq85ma?si=E8VaplDSSqizP4LoQU3eDg";

    @Test
    void collapsesImmediatelyRepeatedSpotifyUrl() {
        assertEquals(PLAYLIST_URL, MusicPlayerService.normalizeRepeatedSpotifyUrl(PLAYLIST_URL + PLAYLIST_URL));
    }

    @Test
    void collapsesSameSpotifyResourceWithDifferentShareParameters() {
        String repeated = PLAYLIST_URL
                + "https://open.spotify.com/playlist/37i9dQZF1E4xOzG7Aq85ma?si=anotherShareId";

        assertEquals(PLAYLIST_URL, MusicPlayerService.normalizeRepeatedSpotifyUrl(repeated));
    }

    @Test
    void preservesDifferentSpotifyResources() {
        String different = PLAYLIST_URL
                + "https://open.spotify.com/playlist/6M4A0P8zMiT4cSbsONxukV?si=anotherShareId";

        assertEquals(different, MusicPlayerService.normalizeRepeatedSpotifyUrl(different));
    }

    @Test
    void preservesSingleSpotifyUrlAndSearchText() {
        assertEquals(PLAYLIST_URL, MusicPlayerService.normalizeRepeatedSpotifyUrl(PLAYLIST_URL));
        assertEquals("spotify playlist name", MusicPlayerService.normalizeRepeatedSpotifyUrl(" spotify playlist name "));
    }
}
