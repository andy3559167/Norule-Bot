package com.norule.musicbot.shorturl;

import com.norule.musicbot.domain.shorturl.ImageShare;

import java.io.IOException;
import java.io.InputStream;

public interface ImageShareStorage {
    void save(ImageShare imageShare, byte[] content) throws IOException;

    InputStream open(ImageShare imageShare) throws IOException;

    boolean exists(ImageShare imageShare);

    void delete(ImageShare imageShare) throws IOException;
}
