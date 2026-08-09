package com.norule.musicbot.shorturl;

import com.norule.musicbot.domain.shorturl.ImageShare;

import java.io.IOException;
import java.io.InputStream;

public interface ImageShareStorage {
    void save(ImageShare imageShare, byte[] content) throws IOException;

    InputStream open(ImageShare imageShare) throws IOException;

    boolean exists(ImageShare imageShare);

    void delete(ImageShare imageShare) throws IOException;

    default String archive(ImageShare imageShare) throws IOException {
        throw new IOException("Archive storage is not configured");
    }

    default boolean existsArchived(ImageShare imageShare) {
        return false;
    }

    default void deleteArchived(ImageShare imageShare) throws IOException {
        // Nothing to delete for storage implementations without archive support.
    }

    default int filesystemUsagePercent() {
        return 0;
    }
}
