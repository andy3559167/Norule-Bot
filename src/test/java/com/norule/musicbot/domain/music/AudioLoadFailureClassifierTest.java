package com.norule.musicbot.domain.music;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private FriendlyException friendly(String message, Throwable cause) {
        return new FriendlyException(message, FriendlyException.Severity.SUSPICIOUS, cause);
    }
}
