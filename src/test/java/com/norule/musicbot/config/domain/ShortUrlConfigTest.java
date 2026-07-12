package com.norule.musicbot.config.domain;

import com.norule.musicbot.service.shorturl.ImageShareService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShortUrlConfigTest {
    @Test
    void defaultsVideoUploadsToOneHundredMegabytesAndFiveMinutes() {
        ImageShareService.Options options = new ShortUrlConfig(null).toImageShareOptions();

        assertEquals(100L * 1024L * 1024L, options.maxVideoFileSizeBytes());
        assertEquals(5L * 60L * 1000L, options.maxVideoDurationMillis());
        assertEquals(20L * 1024L * 1024L, options.maxFileSizeBytes());
        assertEquals(30L * 24L * 60L * 60L * 1000L, options.expiredShareRetentionMillis());
    }
}
