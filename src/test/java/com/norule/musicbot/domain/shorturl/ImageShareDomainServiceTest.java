package com.norule.musicbot.domain.shorturl;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageShareDomainServiceTest {
    private final ImageShareDomainService service = new ImageShareDomainService();

    @Test
    void detectsMp4VideoAndReadsDuration() throws Exception {
        ImageShareDomainService.MediaType media = service.detectMediaType(mp4(300_000L));

        assertNotNull(media);
        assertEquals("video/mp4", media.contentType());
        assertEquals("mp4", media.extension());
        assertTrue(media.video());
        assertEquals(300_000L, media.durationMillis());
    }

    @Test
    void detectsWebmVideoAndReadsDuration() throws Exception {
        ImageShareDomainService.MediaType media = service.detectMediaType(webm(300_000.0));

        assertNotNull(media);
        assertEquals("video/webm", media.contentType());
        assertEquals("webm", media.extension());
        assertTrue(media.video());
        assertEquals(300_000L, media.durationMillis());
    }

    private byte[] mp4(long durationMillis) throws Exception {
        byte[] movieHeader = atom("mvhd", output -> {
            output.writeInt(0);
            output.writeInt(0);
            output.writeInt(0);
            output.writeInt(1000);
            output.writeInt((int) durationMillis);
        });
        byte[] handler = atom("hdlr", output -> {
            output.writeInt(0);
            output.writeInt(0);
            output.writeBytes("vide");
        });
        byte[] media = atom("mdia", output -> output.write(handler));
        byte[] track = atom("trak", output -> output.write(media));
        byte[] movie = atom("moov", output -> {
            output.write(movieHeader);
            output.write(track);
        });
        byte[] fileType = atom("ftyp", output -> output.writeBytes("isom"));
        return concat(fileType, movie);
    }

    private byte[] webm(double durationMillis) throws Exception {
        byte[] header = ebml(new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3},
                "webm".getBytes(StandardCharsets.US_ASCII));
        byte[] timecodeScale = ebml(new byte[]{0x2A, (byte) 0xD7, (byte) 0xB1},
                new byte[]{0x00, 0x0F, 0x42, 0x40});
        ByteArrayOutputStream durationBytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(durationBytes)) {
            output.writeDouble(durationMillis);
        }
        byte[] duration = ebml(new byte[]{0x44, (byte) 0x89}, durationBytes.toByteArray());
        byte[] info = ebml(new byte[]{0x15, 0x49, (byte) 0xA9, 0x66}, concat(timecodeScale, duration));
        byte[] trackType = ebml(new byte[]{(byte) 0x83}, new byte[]{0x01});
        byte[] trackEntry = ebml(new byte[]{(byte) 0xAE}, trackType);
        byte[] tracks = ebml(new byte[]{0x16, 0x54, (byte) 0xAE, 0x6B}, trackEntry);
        return concat(header, info, tracks);
    }

    private byte[] atom(String type, AtomPayloadWriter writer) throws Exception {
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream payload = new DataOutputStream(payloadBytes)) {
            writer.write(payload);
        }
        ByteArrayOutputStream atomBytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(atomBytes)) {
            output.writeInt(payloadBytes.size() + 8);
            output.write(type.getBytes(StandardCharsets.US_ASCII));
            output.write(payloadBytes.toByteArray());
        }
        return atomBytes.toByteArray();
    }

    private byte[] ebml(byte[] id, byte[] payload) {
        if (payload.length >= 127) {
            throw new IllegalArgumentException("test EBML payload is too large");
        }
        return concat(id, new byte[]{(byte) (0x80 | payload.length)}, payload);
    }

    private byte[] concat(byte[]... values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] value : values) {
            output.writeBytes(value);
        }
        return output.toByteArray();
    }

    @FunctionalInterface
    private interface AtomPayloadWriter {
        void write(DataOutputStream output) throws Exception;
    }
}
