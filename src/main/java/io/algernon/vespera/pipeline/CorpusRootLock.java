package io.algernon.vespera.pipeline;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds a corpus root for the length of the invocation, so a second {@code vespera run} walking the same
 * root is refused whatever working directory it names (ADR-278).
 *
 * <p>The same shape as {@link WorkingDirectoryLock}: an operating-system lock on one byte far past the end of
 * a file, so the holder's line stays readable while it is held; released by the operating system however the
 * process ends; the file never deleted. The file lies in one folder per user, named for the root's real path.
 */
public class CorpusRootLock {

    /** The property that moves the folder; tests use it. */
    public static final String FOLDER_PROPERTY = "vespera.corpus-root-lock-folder";

    private static final String DEFAULT_FOLDER = ".vespera-root-locks";

    /** Sixteen hexadecimal digits are 64 bits of the key's SHA-256. */
    private static final int DIGITS = 16;

    private static final long LOCKED_BYTE = Long.MAX_VALUE - 1;

    private static final long LOCKED_LENGTH = 1;

    /** What this JVM holds, for the life of the JVM; a lock lives only as long as its channel does. */
    private static final List<Held> HELD = new CopyOnWriteArrayList<>();

    private record Held(FileChannel channel, FileLock lock) {
    }

    private final Path folder;

    public CorpusRootLock(Path folder) {
        this.folder = folder;
    }

    /** The folder the property names; unset or blank, one in the user's home. */
    public static Path folderNamedBy(String configured) {
        if (configured == null || configured.isBlank()) {
            return Path.of(System.getProperty("user.home")).resolve(DEFAULT_FOLDER);
        }
        return Path.of(configured);
    }

    /** The root's real path, or its absolute normalised path when it cannot be resolved (ADR-278 §2). */
    public static Path key(Path corpusRoot) {
        try {
            return corpusRoot.toRealPath();
        } catch (IOException notThere) {
            return corpusRoot.toAbsolutePath().normalize();
        }
    }

    /** The lock file's name for a key (ADR-278 §3). */
    public static String fileName(Path key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, DIGITS) + ".lock";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java platform", e);
        }
    }

    /**
     * Holds the corpus root until the process ends.
     *
     * @throws CorpusRootInUseException when another invocation, or this JVM, holds it already
     * @throws CorpusRootLockUnavailableException when the folder or file cannot be created or opened
     */
    public void take(Path corpusRoot, Path workingDirectory, String[] args) {
        Path key = key(corpusRoot);
        Path lockFile = folder.resolve(fileName(key));
        FileChannel channel;
        try {
            Files.createDirectories(folder);
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException | RuntimeException e) {
            throw new CorpusRootLockUnavailableException(folder, e);
        }
        FileLock lock;
        try {
            lock = channel.tryLock(LOCKED_BYTE, LOCKED_LENGTH, false);
        } catch (OverlappingFileLockException heldByThisProcess) {
            // Kept open, not closed: closing may release every lock this JVM holds on the file (FileLock javadoc).
            HELD.add(new Held(channel, null));
            throw new CorpusRootInUseException(corpusRoot, holderLine(lockFile));
        } catch (IOException e) {
            closeQuietly(channel);
            throw new CorpusRootLockUnavailableException(folder, e);
        }
        if (lock == null) {
            closeQuietly(channel);
            throw new CorpusRootInUseException(corpusRoot, holderLine(lockFile));
        }
        HELD.add(new Held(channel, lock));
        try {
            String line = "pid=" + ProcessHandle.current().pid()
                    + " started=" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                            OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS))
                    + " command=" + String.join(" ", args).replaceAll("\\R", " ")
                    + " root=" + key
                    + " working-directory=" + workingDirectory.toAbsolutePath();
            channel.truncate(0);
            ByteBuffer bytes = ByteBuffer.wrap((line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            long position = 0;
            while (bytes.hasRemaining()) {
                position += channel.write(bytes, position);
            }
        } catch (IOException e) {
            throw new CorpusRootLockUnavailableException(folder, e);
        }
    }

    /** Lets go of every lock this JVM holds, for a test to hand the folder back. */
    static void release() {
        for (Held held : HELD) {
            try {
                if (held.lock() != null && held.lock().isValid()) {
                    held.lock().release();
                }
            } catch (IOException ignored) {
                // Closing the channel below releases it all the same.
            }
            closeQuietly(held.channel());
        }
        HELD.clear();
    }

    private static String holderLine(Path lockFile) {
        try {
            return Files.readString(lockFile, StandardCharsets.UTF_8).strip();
        } catch (IOException unreadable) {
            return null;
        }
    }

    private static void closeQuietly(FileChannel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
            // Nothing is left to do for a file that will not close.
        }
    }
}
