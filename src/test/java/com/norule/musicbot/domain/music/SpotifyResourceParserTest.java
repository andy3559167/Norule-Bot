package com.norule.musicbot.domain.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpotifyResourceParserTest {
    private final SpotifyResourceParser parser = new SpotifyResourceParser();

    @Test
    void parsesSupportedResourceTypesWithoutConsideringShareQuery() {
        assertResource("https://open.spotify.com/track/abc", SpotifyResourceParser.ResourceType.TRACK);
        assertResource("https://open.spotify.com/album/abc", SpotifyResourceParser.ResourceType.ALBUM);
        assertResource("https://open.spotify.com/playlist/abc", SpotifyResourceParser.ResourceType.PLAYLIST);
        assertResource("https://open.spotify.com/artist/abc", SpotifyResourceParser.ResourceType.ARTIST);
        assertResource("https://open.spotify.com/episode/abc", SpotifyResourceParser.ResourceType.EPISODE);
        assertResource("https://open.spotify.com/show/abc", SpotifyResourceParser.ResourceType.SHOW);
        assertResource(
                "https://www.open.spotify.com/show/abc?si=test&utm_source=copy-link",
                SpotifyResourceParser.ResourceType.SHOW
        );
    }

    @Test
    void supportsSpotifyInternationalizedPathPrefix() {
        SpotifyResourceParser.SpotifyResource resource = parser
                .parse("https://open.spotify.com/intl-zh-TW/track/abc?si=test")
                .orElseThrow();

        assertEquals(SpotifyResourceParser.ResourceType.TRACK, resource.type());
        assertEquals("abc", resource.id());
    }

    @Test
    void rejectsLookalikeSpotifyHosts() {
        assertTrue(parser.parse("https://open.spotify.com.example.com/show/abc").isEmpty());
        assertTrue(parser.parse("https://evil.com/open.spotify.com/show/abc").isEmpty());
    }

    @Test
    void reportsUnknownSpotifyPathsWithoutTreatingThemAsSupported() {
        SpotifyResourceParser.SpotifyResource resource = parser
                .parse("https://open.spotify.com/user/abc")
                .orElseThrow();

        assertEquals(SpotifyResourceParser.ResourceType.UNKNOWN, resource.type());
    }

    private void assertResource(String input, SpotifyResourceParser.ResourceType type) {
        SpotifyResourceParser.SpotifyResource resource = parser.parse(input).orElseThrow();
        assertEquals(type, resource.type());
        assertEquals("abc", resource.id());
    }
}
