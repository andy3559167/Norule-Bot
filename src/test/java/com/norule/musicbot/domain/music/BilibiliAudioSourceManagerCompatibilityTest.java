package com.norule.musicbot.domain.music;

import dev.lavalink.bilibili.BilibiliAudioSourceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BilibiliAudioSourceManagerCompatibilityTest {
    @Test
    void initializesWithCurrentLavaplayerRuntime() {
        BilibiliAudioSourceManager sourceManager = new BilibiliAudioSourceManager();
        try {
            assertEquals("bilibili", sourceManager.getSourceName());
        } finally {
            sourceManager.shutdown();
        }
    }
}
