package com.norule.musicbot.config.domain;

import com.norule.musicbot.config.BotConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicAudioConfigTest {
    @Test
    void defaultsDisableDirectHttpAndEnableBoundedRecovery() {
        MusicConfig config = MusicConfig.defaultValues();

        assertFalse(config.getAudio().getDirectHttp().isEnabled());
        assertTrue(config.getAudio().getRecovery().isEnabled());
        assertEquals(2, config.getAudio().getRecovery().getMaxStuckRetries());
        assertEquals(2_000, config.getAudio().getRecovery().getResumeRewindMillis());
        assertEquals(20_000, config.getAudio().getRecovery().getStuckThresholdMillis());
    }

    @Test
    void mapsGlobalAudioSettingsIntoDomainConfig() {
        BotConfig.Music legacy = BotConfig.Music.fromMap(Map.of(
                "audio", Map.of(
                        "direct-http", Map.of(
                                "enabled", true,
                                "connect-timeout-ms", 2500,
                                "read-timeout-ms", 7500,
                                "max-redirects", 1,
                                "allowed-hosts", List.of("cdn.example.com")
                        ),
                        "recovery", Map.of(
                                "enabled", true,
                                "max-stuck-retries", 1,
                                "resume-rewind-ms", 1500,
                                "stuck-threshold-ms", 15000
                        )
                )
        ), null);

        MusicConfig config = MusicConfig.fromLegacy(legacy, legacy);

        assertTrue(config.getAudio().getDirectHttp().isEnabled());
        assertEquals(List.of("cdn.example.com"), config.getAudio().getDirectHttp().getAllowedHosts());
        assertEquals(1, config.getAudio().getRecovery().getMaxStuckRetries());
        assertEquals(15_000, config.getAudio().getRecovery().getStuckThresholdMillis());
    }
}
