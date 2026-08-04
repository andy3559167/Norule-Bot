package com.norule.musicbot.domain.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioInputClassifierTest {
    private final AudioInputClassifier classifier = new AudioInputClassifier();

    @Test
    void classifiesSpotifyPodcastLinksBeforeGenericHttp() {
        assertEquals(
                AudioInputClassifier.AudioInputType.SPOTIFY_SHOW,
                classifier.classify("https://open.spotify.com/show/abc?si=test").type()
        );
        assertEquals(
                AudioInputClassifier.AudioInputType.SPOTIFY_EPISODE,
                classifier.classify("https://open.spotify.com/episode/abc").type()
        );
    }

    @Test
    void preservesSupportedMusicInputs() {
        assertEquals(AudioInputClassifier.AudioInputType.SPOTIFY_TRACK,
                classifier.classify("https://open.spotify.com/track/abc").type());
        assertEquals(AudioInputClassifier.AudioInputType.SPOTIFY_PLAYLIST,
                classifier.classify("spotify:playlist:abc").type());
        assertEquals(AudioInputClassifier.AudioInputType.YOUTUBE_URL,
                classifier.classify("https://www.youtube.com/watch?v=5MSYOqQ8dNc").type());
        assertEquals(AudioInputClassifier.AudioInputType.KNOWN_AUDIO_SERVICE_URL,
                classifier.classify("https://soundcloud.com/artist/track").type());
        assertEquals(AudioInputClassifier.AudioInputType.SEARCH_QUERY,
                classifier.classify("long form audiobook").type());
    }

    @Test
    void onlyTreatsExplicitAudioFilesAsDirectHttpCandidates() {
        assertEquals(AudioInputClassifier.AudioInputType.DIRECT_HTTP_AUDIO,
                classifier.classify("https://cdn.example.com/audio/book.MP3?token=secret").type());
        assertEquals(AudioInputClassifier.AudioInputType.UNSUPPORTED_URL,
                classifier.classify("https://example.com/page").type());
    }

    @Test
    void doesNotAcceptLookalikeKnownServiceHosts() {
        assertEquals(AudioInputClassifier.AudioInputType.UNSUPPORTED_URL,
                classifier.classify("https://youtube.com.example.com/watch?v=abc").type());
        assertEquals(AudioInputClassifier.AudioInputType.UNSUPPORTED_URL,
                classifier.classify("https://open.spotify.com.example.com/track/abc").type());
    }

    @Test
    void separatesMalformedAndUnsupportedSchemesFromSearchText() {
        assertEquals(AudioInputClassifier.AudioInputType.INVALID_URL,
                classifier.classify("https://[::1").type());
        assertEquals(AudioInputClassifier.AudioInputType.INVALID_URL,
                classifier.classify("file:///etc/passwd").type());
        assertEquals(AudioInputClassifier.AudioInputType.SEARCH_QUERY,
                classifier.classify("artist: song title").type());
    }
}
