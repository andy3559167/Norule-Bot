package com.norule.musicbot.domain.shorturl;

import java.nio.charset.StandardCharsets;

final class WebmVideoMetadataParser {
    private static final long INFO_ID = 0x1549A966L;
    private static final long TRACKS_ID = 0x1654AE6BL;
    private static final long TRACK_ENTRY_ID = 0xAEL;
    private static final long TRACK_TYPE_ID = 0x83L;
    private static final long TIMECODE_SCALE_ID = 0x2AD7B1L;
    private static final long DURATION_ID = 0x4489L;

    private WebmVideoMetadataParser() {
    }

    static long readDurationMillis(byte[] content) {
        if (!isWebm(content) || !hasVideoTrack(content)) {
            return -1L;
        }
        EbmlElement info = findElement(content, INFO_ID);
        if (info == null) {
            return -1L;
        }
        long timecodeScale = 1_000_000L;
        double duration = -1.0;
        int position = info.dataOffset();
        while (position < info.endOffset()) {
            EbmlElement child = readElement(content, position, info.endOffset());
            if (child == null) {
                return -1L;
            }
            if (child.id() == TIMECODE_SCALE_ID) {
                timecodeScale = readUnsignedInteger(content, child.dataOffset(), child.dataLength());
            } else if (child.id() == DURATION_ID) {
                duration = readFloat(content, child.dataOffset(), child.dataLength());
            }
            position = child.endOffset();
        }
        if (!Double.isFinite(duration) || duration <= 0.0 || timecodeScale <= 0L) {
            return -1L;
        }
        double durationMillis = duration * timecodeScale / 1_000_000.0;
        return durationMillis > 0.0 && durationMillis <= Long.MAX_VALUE
                ? Math.round(durationMillis)
                : -1L;
    }

    private static boolean hasVideoTrack(byte[] content) {
        EbmlElement tracks = findElement(content, TRACKS_ID);
        if (tracks == null) {
            return false;
        }
        int position = tracks.dataOffset();
        while (position < tracks.endOffset()) {
            EbmlElement entry = readElement(content, position, tracks.endOffset());
            if (entry == null) {
                return false;
            }
            if (entry.id() == TRACK_ENTRY_ID && isVideoTrackEntry(content, entry)) {
                return true;
            }
            position = entry.endOffset();
        }
        return false;
    }

    private static boolean isVideoTrackEntry(byte[] content, EbmlElement entry) {
        int position = entry.dataOffset();
        while (position < entry.endOffset()) {
            EbmlElement child = readElement(content, position, entry.endOffset());
            if (child == null) {
                return false;
            }
            if (child.id() == TRACK_TYPE_ID
                    && readUnsignedInteger(content, child.dataOffset(), child.dataLength()) == 1L) {
                return true;
            }
            position = child.endOffset();
        }
        return false;
    }

    private static boolean isWebm(byte[] content) {
        return content != null
                && content.length >= 4
                && content[0] == 0x1A
                && content[1] == 0x45
                && content[2] == (byte) 0xDF
                && content[3] == (byte) 0xA3
                && indexOfAscii(content, "webm", Math.min(content.length, 4096)) >= 0;
    }

    private static EbmlElement findElement(byte[] content, long targetId) {
        for (int offset = 0; offset < content.length; offset++) {
            EbmlElement element = readElement(content, offset, content.length);
            if (element != null && element.id() == targetId) {
                return element;
            }
        }
        return null;
    }

    private static EbmlElement readElement(byte[] content, int offset, int parentEnd) {
        EbmlVint id = readVint(content, offset, false);
        if (id == null) {
            return null;
        }
        EbmlVint size = readVint(content, offset + id.length(), true);
        if (size == null || size.unknownSize() || size.value() > Integer.MAX_VALUE) {
            return null;
        }
        int dataOffset = offset + id.length() + size.length();
        long end = dataOffset + size.value();
        if (dataOffset > parentEnd || end > parentEnd) {
            return null;
        }
        return new EbmlElement(id.value(), dataOffset, (int) end);
    }

    private static EbmlVint readVint(byte[] content, int offset, boolean stripMarker) {
        if (offset < 0 || offset >= content.length) {
            return null;
        }
        int first = content[offset] & 0xFF;
        int marker = 0x80;
        int length = 1;
        while (length <= 8 && (first & marker) == 0) {
            marker >>>= 1;
            length++;
        }
        if (length > 8 || offset + length > content.length) {
            return null;
        }
        long value = stripMarker ? first & (marker - 1L) : first;
        for (int index = 1; index < length; index++) {
            value = (value << 8) | (content[offset + index] & 0xFFL);
        }
        long unknownValue = stripMarker && length < 8 ? (1L << (7 * length)) - 1L : -1L;
        return new EbmlVint(value, length, stripMarker && value == unknownValue);
    }

    private static double readFloat(byte[] content, int offset, int length) {
        if (length == 4) {
            return Float.intBitsToFloat((int) readUnsignedInteger(content, offset, length));
        }
        if (length == 8) {
            long bits = readUnsignedInteger(content, offset, length);
            return bits < 0L ? Double.NaN : Double.longBitsToDouble(bits);
        }
        return Double.NaN;
    }

    private static long readUnsignedInteger(byte[] content, int offset, int length) {
        if (length <= 0 || length > 8 || offset < 0 || offset + length > content.length) {
            return -1L;
        }
        long value = 0L;
        for (int index = 0; index < length; index++) {
            if (length == 8 && index == 0 && (content[offset] & 0x80) != 0) {
                return -1L;
            }
            value = (value << 8) | (content[offset + index] & 0xFFL);
        }
        return value;
    }

    private static int indexOfAscii(byte[] content, String value, int limit) {
        byte[] target = value.getBytes(StandardCharsets.US_ASCII);
        for (int offset = 0; offset + target.length <= limit; offset++) {
            boolean matched = true;
            for (int index = 0; index < target.length; index++) {
                if (content[offset + index] != target[index]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return offset;
            }
        }
        return -1;
    }

    private record EbmlVint(long value, int length, boolean unknownSize) {
    }

    private record EbmlElement(long id, int dataOffset, int endOffset) {
        private int dataLength() {
            return endOffset - dataOffset;
        }
    }
}
