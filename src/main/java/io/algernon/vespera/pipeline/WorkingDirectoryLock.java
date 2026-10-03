package io.algernon.vespera.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

/**
 * Holds the working directory for the length of the invocation, and refuses a second invocation on it
 * before it opens the database file (ADR-177 §1, #364).
 *
 * <p>An environment listener, like {@link WorkingDirectoryPreparer} and {@link ProfileShapeCheck},
 * because of when it has to happen: before the datasource opens {@code vespera.db}, which is before any
 * bean exists. {@code VesperaApplication.main} registers it after the preparer, since the directory
 * must exist, and before the profile check.
 *
 * <p>The lock is an operating-system lock on one byte of {@code vespera.lock}, far past the end of
 * anything written, so the holder's line stays readable to every other program while it is held. The
 * operating system releases it when the process ends, however it ends, so a crashed holder never
 * blocks. The file is never deleted, and its existence means nothing; only the lock does.
 */
public class WorkingDirectoryLock implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /** The file the lock is taken on, in the working directory beside the database file. */
    public static final String FILE_NAME = "vespera.lock";

    /** The locked byte: past the end of the file, so that locking it does not make the line unreadable. */
    private static final long LOCKED_BYTE = Long.MAX_VALUE - 1;

    private static final long LOCKED_LENGTH = 1;

    /**
     * What this JVM holds, for the life of the JVM. A lock lives only as long as its channel is open
     * and reachable, so nothing in the application lets go of these.
     */
    private static final List<Held> HELD = new CopyOnWriteArrayList<>();

    private record Held(FileChannel channel, FileLock lock) {
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        String configured = event.getEnvironment().getProperty(WorkingDirectoryPreparer.PROPERTY);
        if (configured == null || configured.isBlank()) {
            return;
        }
        Path workingDirectory = Path.of(configured);
        Path lockFile = workingDirectory.resolve(FILE_NAME);
        FileChannel channel;
        try {
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("could not open the lock file at " + lockFile, e);
        }
        FileLock lock;
        try {
            lock = channel.tryLock(LOCKED_BYTE, LOCKED_LENGTH, false);
        } catch (OverlappingFileLockException heldByThisProcess) {
            // Kept open rather than closed: on some systems closing a channel releases every lock this
            // JVM holds on the file, the holder's included. Held for the life of the JVM, as it is.
            // This path is reached only when one JVM starts the application twice (tests). The risk is
            // the one java.nio.channels.FileLock's javadoc names: closing a channel may release all
            // locks held by the Java virtual machine on the underlying file.
            HELD.add(new Held(channel, null));
            throw new WorkingDirectoryInUseException(workingDirectory, holderLine(lockFile));
        } catch (IOException e) {
            closeQuietly(channel);
            throw new UncheckedIOException("could not lock the working directory at " + workingDirectory, e);
        }
        if (lock == null) {
            closeQuietly(channel);
            throw new WorkingDirectoryInUseException(workingDirectory, holderLine(lockFile));
        }
        HELD.add(new Held(channel, lock));
        try {
            writeHolderLine(channel, event.getArgs());
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the holder's line to " + lockFile, e);
        }
    }

    /** Lets go of every lock this JVM holds, for a test to hand the directory back. No-op when none. */
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

    private static void writeHolderLine(FileChannel channel, String[] args) throws IOException {
        String line = "pid=" + ProcessHandle.current().pid()
                + " started=" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                        OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS))
                + " command=" + String.join(" ", args).replaceAll("\\R", " ");
        channel.truncate(0);
        ByteBuffer bytes = ByteBuffer.wrap((line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        long position = 0;
        while (bytes.hasRemaining()) {
            position += channel.write(bytes, position);
        }
    }

    /** What the holder wrote, read without the lock; {@code null} when it cannot be read. */
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
