package com.norule.musicbot.domain.shorturl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ImageShareDomainService {
    private static final DateTimeFormatter DEFAULT_PASSWORD_FORMAT = DateTimeFormatter.ofPattern("MMdd");

    public record ImageType(String contentType, String extension) {
    }

    public ImageType detectImageType(byte[] content) {
        if (content == null || content.length < 3) {
            return null;
        }
        if (isPng(content)) {
            return new ImageType("image/png", "png");
        }
        if (isJpeg(content)) {
            return new ImageType("image/jpeg", "jpg");
        }
        if (isGif(content)) {
            return new ImageType("image/gif", "gif");
        }
        if (isWebp(content)) {
            return new ImageType("image/webp", "webp");
        }
        return null;
    }

    public String normalizePassword(String password) {
        return password == null ? "" : password.trim();
    }

    public boolean isValidPassword(String password) {
        int length = password == null ? 0 : password.length();
        return length >= 4 && length <= 128;
    }

    public String defaultPassword(LocalDate date) {
        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        return DEFAULT_PASSWORD_FORMAT.format(effectiveDate);
    }

    private boolean isPng(byte[] content) {
        return content.length >= 8
                && content[0] == (byte) 0x89
                && content[1] == 0x50
                && content[2] == 0x4E
                && content[3] == 0x47
                && content[4] == 0x0D
                && content[5] == 0x0A
                && content[6] == 0x1A
                && content[7] == 0x0A;
    }

    private boolean isJpeg(byte[] content) {
        return content.length >= 3
                && content[0] == (byte) 0xFF
                && content[1] == (byte) 0xD8
                && content[2] == (byte) 0xFF;
    }

    private boolean isGif(byte[] content) {
        if (content.length < 6) {
            return false;
        }
        String header = new String(content, 0, 6, java.nio.charset.StandardCharsets.US_ASCII).toUpperCase(Locale.ROOT);
        return "GIF87A".equals(header) || "GIF89A".equals(header);
    }

    private boolean isWebp(byte[] content) {
        return content.length >= 12
                && content[0] == 'R'
                && content[1] == 'I'
                && content[2] == 'F'
                && content[3] == 'F'
                && content[8] == 'W'
                && content[9] == 'E'
                && content[10] == 'B'
                && content[11] == 'P';
    }
}
