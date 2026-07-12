package com.norule.musicbot.shorturl.infra;

import com.norule.musicbot.domain.shorturl.ImageShare;
import com.norule.musicbot.shorturl.ImageShareStorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class FileSystemImageShareStorage implements ImageShareStorage {
    private final Path storageDirectory;

    public FileSystemImageShareStorage(Path storageDirectory) {
        if (storageDirectory == null) {
            throw new IllegalArgumentException("storageDirectory cannot be null");
        }
        this.storageDirectory = storageDirectory.toAbsolutePath().normalize();
    }

    @Override
    public void save(ImageShare imageShare, byte[] content) throws IOException {
        Files.createDirectories(storageDirectory);
        Files.write(resolve(imageShare), content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    @Override
    public InputStream open(ImageShare imageShare) throws IOException {
        return Files.newInputStream(resolve(imageShare), StandardOpenOption.READ);
    }

    @Override
    public boolean exists(ImageShare imageShare) {
        return Files.isRegularFile(resolve(imageShare));
    }

    @Override
    public void delete(ImageShare imageShare) throws IOException {
        Files.deleteIfExists(resolve(imageShare));
    }

    private Path resolve(ImageShare imageShare) {
        if (imageShare == null || imageShare.storageName() == null
                || !imageShare.storageName().matches("[A-Za-z0-9_-]{1,96}\\.(?:png|jpg|gif|webp|mp4|webm)")) {
            throw new IllegalArgumentException("Invalid image-share storage name");
        }
        Path resolved = storageDirectory.resolve(imageShare.storageName()).normalize();
        if (!resolved.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("Image-share storage path escapes configured directory");
        }
        return resolved;
    }
}
