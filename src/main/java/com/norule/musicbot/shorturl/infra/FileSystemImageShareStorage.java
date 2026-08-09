package com.norule.musicbot.shorturl.infra;

import com.norule.musicbot.domain.shorturl.ImageShare;
import com.norule.musicbot.shorturl.ImageShareStorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

public final class FileSystemImageShareStorage implements ImageShareStorage {
    private final Path storageDirectory;
    private final Path temporaryDirectory;
    private final Path archiveDirectory;

    public FileSystemImageShareStorage(Path storageDirectory) {
        this(storageDirectory,
                storageDirectory == null ? null : storageDirectory.resolveSibling("tmp/uploads"),
                storageDirectory == null ? null : storageDirectory.resolveSibling("short-url-expired"));
    }

    public FileSystemImageShareStorage(Path storageDirectory, Path temporaryDirectory,
                                       Path archiveDirectory) {
        if (storageDirectory == null) {
            throw new IllegalArgumentException("storageDirectory cannot be null");
        }
        this.storageDirectory = storageDirectory.toAbsolutePath().normalize();
        this.temporaryDirectory = (temporaryDirectory == null
                ? this.storageDirectory.resolveSibling("tmp/uploads") : temporaryDirectory)
                .toAbsolutePath().normalize();
        this.archiveDirectory = (archiveDirectory == null
                ? this.storageDirectory.resolveSibling("short-url-expired") : archiveDirectory)
                .toAbsolutePath().normalize();
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

    @Override
    public String archive(ImageShare imageShare) throws IOException {
        Path source = resolve(imageShare);
        Path target = resolveArchive(imageShare);
        Files.createDirectories(archiveDirectory);
        if (!Files.isRegularFile(source)) {
            if (Files.isRegularFile(target)) {
                return imageShare.storageName();
            }
            throw new IOException("Active media file is missing");
        }
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            return imageShare.storageName();
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            copyVerifyAndDelete(source, target);
            return imageShare.storageName();
        } catch (java.nio.file.FileSystemException exception) {
            if (Files.exists(target)) {
                throw exception;
            }
            copyVerifyAndDelete(source, target);
            return imageShare.storageName();
        }
    }

    @Override
    public boolean existsArchived(ImageShare imageShare) {
        return Files.isRegularFile(resolveArchive(imageShare));
    }

    @Override
    public void deleteArchived(ImageShare imageShare) throws IOException {
        Files.deleteIfExists(resolveArchive(imageShare));
    }

    @Override
    public int filesystemUsagePercent() {
        try {
            Files.createDirectories(storageDirectory);
            var store = Files.getFileStore(storageDirectory);
            long total = store.getTotalSpace();
            long usable = store.getUsableSpace();
            if (total <= 0L) {
                return 0;
            }
            return (int) Math.max(0L, Math.min(100L, ((total - usable) * 100L) / total));
        } catch (IOException ignored) {
            return 100;
        }
    }

    public Path activeDirectory() {
        return storageDirectory;
    }

    public Path temporaryDirectory() {
        return temporaryDirectory;
    }

    public Path archiveDirectory() {
        return archiveDirectory;
    }

    private Path resolve(ImageShare imageShare) {
        String storageName = validateStorageName(imageShare == null ? null : imageShare.storageName());
        Path resolved = storageDirectory.resolve(storageName).normalize();
        if (!resolved.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("Image-share storage path escapes configured directory");
        }
        return resolved;
    }

    private Path resolveArchive(ImageShare imageShare) {
        String requestedName = imageShare == null || imageShare.archiveStorageName().isBlank()
                ? imageShare == null ? null : imageShare.storageName()
                : imageShare.archiveStorageName();
        String storageName = validateStorageName(requestedName);
        Path resolved = archiveDirectory.resolve(storageName).normalize();
        if (!resolved.startsWith(archiveDirectory)) {
            throw new IllegalArgumentException("Image-share archive path escapes configured directory");
        }
        return resolved;
    }

    private String validateStorageName(String storageName) {
        if (storageName == null
                || !storageName.matches("[A-Za-z0-9_-]{1,96}\\.(?:png|jpg|gif|webp|mp4|webm)")) {
            throw new IllegalArgumentException("Invalid image-share storage name");
        }
        return storageName;
    }

    private void copyVerifyAndDelete(Path source, Path target) throws IOException {
        Path archiveTemp = archiveDirectory.resolve("." + target.getFileName() + "."
                + UUID.randomUUID().toString().replace("-", "") + ".tmp").normalize();
        if (!archiveTemp.startsWith(archiveDirectory)) {
            throw new IOException("Archive temporary path escapes configured directory");
        }
        try {
            Files.copy(source, archiveTemp, StandardCopyOption.COPY_ATTRIBUTES);
            try (FileChannel channel = FileChannel.open(archiveTemp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            if (Files.size(source) != Files.size(archiveTemp)
                    || !sha256(source).equals(sha256(archiveTemp))) {
                throw new IOException("Archived media verification failed");
            }
            try {
                Files.move(archiveTemp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(archiveTemp, target);
            }
            Files.delete(source);
        } finally {
            Files.deleteIfExists(archiveTemp);
        }
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
