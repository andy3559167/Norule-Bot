package com.norule.musicbot.shorturl.infra;

import com.norule.musicbot.domain.shorturl.ImageShare;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortUrlGatewayServerTest {
    @Test
    void rendersPasswordPageWithoutTreatingCssPercentAsFormatSpecifier() {
        String html = ShortUrlGatewayServer.buildImagePasswordPage("6CxvDMr");

        assertTrue(html.contains("NoRule URL"));
        assertTrue(html.contains("/api/short/image/access/6CxvDMr"));
        assertFalse(html.contains("__IMAGE_CODE__"));
    }

    @Test
    void rendersImageInsideBrandedViewerWithViewCount() {
        ImageShare imageShare = new ImageShare(
                "6CxvDMr", "6CxvDMr.png", "image/png", 2048L,
                1_700_000_000_000L, 1_800_000_000_000L, "", "hash", 42L
        );

        String html = ShortUrlGatewayServer.buildImageViewPage(imageShare);

        assertTrue(html.contains("NoRule URL"));
        assertTrue(html.contains("/api/short/image/content/6CxvDMr"));
        assertTrue(html.contains(">42<"));
        assertFalse(html.contains("__IMAGE_VIEWS__"));
    }
}
