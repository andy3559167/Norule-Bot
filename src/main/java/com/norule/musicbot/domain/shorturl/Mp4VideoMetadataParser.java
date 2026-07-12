package com.norule.musicbot.domain.shorturl;

import java.nio.charset.StandardCharsets;

final class Mp4VideoMetadataParser {
    private Mp4VideoMetadataParser() {
    }

    static long readDurationMillis(byte[] content) {
        if (content == null || !hasVideoTrack(content)) {
            return -1L;
        }
        Atom fileType = findDirectAtom(content, 0, content.length, "ftyp");
        Atom movie = findDirectAtom(content, 0, content.length, "moov");
        if (fileType == null || movie == null) {
            return -1L;
        }
        Atom movieHeader = findDirectAtom(content, movie.dataOffset(), movie.endOffset(), "mvhd");
        if (movieHeader == null || movieHeader.dataOffset() >= movieHeader.endOffset()) {
            return -1L;
        }
        int version = content[movieHeader.dataOffset()] & 0xFF;
        int timescaleOffset;
        int durationOffset;
        int durationLength;
        if (version == 0) {
            timescaleOffset = movieHeader.dataOffset() + 12;
            durationOffset = movieHeader.dataOffset() + 16;
            durationLength = 4;
        } else if (version == 1) {
            timescaleOffset = movieHeader.dataOffset() + 20;
            durationOffset = movieHeader.dataOffset() + 24;
            durationLength = 8;
        } else {
            return -1L;
        }
        long timescale = readUnsignedInteger(content, timescaleOffset, 4);
        long duration = readUnsignedInteger(content, durationOffset, durationLength);
        if (duration <= 0L || timescale <= 0L) {
            return -1L;
        }
        double millis = duration * 1000.0 / timescale;
        return Double.isFinite(millis) && millis > 0.0 && millis <= Long.MAX_VALUE
                ? Math.round(millis)
                : -1L;
    }

    private static boolean hasVideoTrack(byte[] content) {
        Atom movie = findDirectAtom(content, 0, content.length, "moov");
        if (movie == null) {
            return false;
        }
        int position = movie.dataOffset();
        while (position < movie.endOffset()) {
            Atom atom = readAtom(content, position, movie.endOffset());
            if (atom == null) {
                return false;
            }
            if ("trak".equals(atom.type())) {
                Atom media = findDirectAtom(content, atom.dataOffset(), atom.endOffset(), "mdia");
                Atom handler = media == null
                        ? null
                        : findDirectAtom(content, media.dataOffset(), media.endOffset(), "hdlr");
                int handlerTypeOffset = handler == null ? -1 : handler.dataOffset() + 8;
                if (matchesAscii(content, handlerTypeOffset, "vide")) {
                    return true;
                }
            }
            position = atom.endOffset();
        }
        return false;
    }

    private static Atom findDirectAtom(byte[] content, int start, int end, String type) {
        int position = start;
        while (position < end) {
            Atom atom = readAtom(content, position, end);
            if (atom == null) {
                return null;
            }
            if (type.equals(atom.type())) {
                return atom;
            }
            position = atom.endOffset();
        }
        return null;
    }

    private static Atom readAtom(byte[] content, int offset, int parentEnd) {
        if (offset < 0 || parentEnd > content.length || offset + 8 > parentEnd) {
            return null;
        }
        long size = readUnsignedInteger(content, offset, 4);
        int headerSize = 8;
        if (size == 1L) {
            if (offset + 16 > parentEnd) {
                return null;
            }
            size = readUnsignedInteger(content, offset + 8, 8);
            headerSize = 16;
        } else if (size == 0L) {
            size = parentEnd - (long) offset;
        }
        if (size < headerSize || size > Integer.MAX_VALUE || offset + size > parentEnd) {
            return null;
        }
        String type = new String(content, offset + 4, 4, StandardCharsets.US_ASCII);
        return new Atom(type, offset + headerSize, (int) (offset + size));
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

    private static boolean matchesAscii(byte[] content, int offset, String value) {
        if (offset < 0 || offset + value.length() > content.length) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (content[offset + index] != (byte) value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private record Atom(String type, int dataOffset, int endOffset) {
    }
}
