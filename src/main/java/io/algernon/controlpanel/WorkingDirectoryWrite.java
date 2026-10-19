package io.algernon.controlpanel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Writes one of the three files of a working directory the control panel changes, holding the working-directory lock
 * as the jar takes it, never waiting for it, and only in a folder of the user's own (ADR-295 §1, §2; ADR-177 §1,
 * ADR-291 §8). The file is written whole to a {@code .part} file and moved over the target. Every refusal is one line
 * returned, none thrown; what the change returns is the text, and its own refusal is an
 * {@link IllegalArgumentException} whose message is the line. Taking the lock links no class of the jar (ADR-285 §3).
 */
public final class WorkingDirectoryWrite {

    /** The file the lock is taken on, which the jar's invocations take too. */
    public static final String LOCK_FILE_NAME = "vespera.lock";

    /** The three files of a working directory the control panel writes, and no other. */
    public static final Set<String> FILES = Set.of("profile.yaml", "relevance-labels.yaml", "code-set-names.yaml");

    /** The byte the jar locks, far past the end of the holder line. */
    private static final long LOCKED_BYTE = Long.MAX_VALUE - 1;

    private static final long LOCKED_LENGTH = 1;

    /**
     * The channels a write could not lock because another channel of this JVM holds the lock. They are kept open, as
     * the jar keeps its own, because closing a channel may release a lock another channel of the JVM holds
     * ({@link FileLock}); only a test holds the lock in the JVM the control panel writes from.
     */
    private static final List<FileChannel> KEPT_OPEN = new CopyOnWriteArrayList<>();

    private final BooleanSupplier anInvocationItStartedRuns;
    private final String userName;
    private volatile boolean holdsTheLock;

    public WorkingDirectoryWrite(BooleanSupplier anInvocationItStartedRuns, String userName) {
        this.anInvocationItStartedRuns = anInvocationItStartedRuns;
        this.userName = userName;
    }

    /**
     * Empty when {@code fileName} was written; otherwise the one line. {@code change} is given the file's text, empty
     * where it is absent, and returns the new text or throws an {@link IllegalArgumentException} whose message is the
     * line.
     *
     * @throws IllegalArgumentException for a name outside {@link #FILES}
     */
    public Optional<String> write(Path workingDirectory, String fileName, Function<Optional<String>, String> change) {
        if (!FILES.contains(fileName)) {
            throw new IllegalArgumentException(fileName + " is not a file the control panel writes");
        }
        if (anInvocationItStartedRuns.getAsBoolean()) {
            return Optional.of("the control panel writes nothing into a working directory while the invocation it"
                    + " started runs, so " + fileName + " was not written");
        }
        Optional<String> refusal = refusalOfFolder(workingDirectory, userName, fileName);
        if (refusal.isPresent()) {
            return refusal;
        }
        Path folder = realOrGiven(workingDirectory);
        Path lockFile = folder.resolve(LOCK_FILE_NAME);
        FileChannel channel;
        try {
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException e) {
            return couldNotWrite(fileName, e);
        }
        FileLock lock;
        try {
            lock = channel.tryLock(LOCKED_BYTE, LOCKED_LENGTH, false);
        } catch (OverlappingFileLockException heldInThisJvm) {
            KEPT_OPEN.add(channel);
            return Optional.of(held(folder, lockFile, fileName));
        } catch (IOException e) {
            closeQuietly(channel);
            return couldNotWrite(fileName, e);
        }
        if (lock == null) {
            String line = held(folder, lockFile, fileName);
            closeQuietly(channel);
            return Optional.of(line);
        }
        holdsTheLock = true;
        try {
            return writeHolding(channel, folder, fileName, change);
        } finally {
            holdsTheLock = false;
            try {
                lock.release();
            } catch (IOException ignored) {
                // closing the channel releases it all the same
            }
            closeQuietly(channel);
        }
    }

    /** Whether a write is holding the lock now. */
    public boolean holdsTheLock() {
        return holdsTheLock;
    }

    /**
     * §1's line for a working directory that is missing, is not a folder, or is not one only this user can write in,
     * checked by its real path, or as given where it has none; empty for one that is.
     */
    public static Optional<String> refusalOfFolder(Path workingDirectory, String userName, String fileName) {
        Path folder = realOrGiven(workingDirectory);
        if (Files.exists(folder, LinkOption.NOFOLLOW_LINKS) && FolderOwnership.isOwn(folder, userName)) {
            return Optional.empty();
        }
        return Optional.of("the control panel writes nothing in " + folder
                + ", which is not a folder only this user can write in, so " + fileName + " was not written");
    }

    /** §1's holder line, ended by the caller: ADR-177 §1's form, with the control panel's write as the command. */
    public static String holderLine(long pid, OffsetDateTime started, String fileName) {
        return "pid=" + pid + " started=" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                started.truncatedTo(ChronoUnit.SECONDS)) + " command=control-panel write " + fileName;
    }

    private Optional<String> writeHolding(FileChannel channel, Path folder, String fileName,
            Function<Optional<String>, String> change) {
        Path part = folder.resolve(fileName + ".part");
        try {
            writeHolderLine(channel, holderLine(ProcessHandle.current().pid(), OffsetDateTime.now(), fileName));
            Path file = folder.resolve(fileName);
            Optional<String> current = Files.exists(file) ? Optional.of(Files.readString(file, UTF_8)) : Optional.empty();
            String text;
            try {
                text = change.apply(current);
            } catch (IllegalArgumentException refused) {
                return Optional.of(refused.getMessage());
            }
            Files.writeString(part, text, UTF_8);
            Files.move(part, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
                // nothing more can be done about a part file; the next write writes over it
            }
            return couldNotWrite(fileName, e);
        }
    }

    private static void writeHolderLine(FileChannel channel, String line) throws IOException {
        channel.truncate(0);
        ByteBuffer bytes = ByteBuffer.wrap((line + System.lineSeparator()).getBytes(UTF_8));
        long position = 0;
        while (bytes.hasRemaining()) {
            position += channel.write(bytes, position);
        }
    }

    private static String held(Path folder, Path lockFile, String fileName) {
        String holder;
        try {
            holder = Files.readString(lockFile, UTF_8).strip();
        } catch (IOException unreadable) {
            holder = "";
        }
        return "another Vespera invocation is using the working directory " + folder + " ("
                + (holder.isEmpty() ? "its details could not be read" : holder) + "), so " + fileName
                + " was not written; save again once it has ended";
    }

    private static Optional<String> couldNotWrite(String fileName, Exception e) {
        return Optional.of("the control panel could not write " + fileName + ": " + (e.getMessage() == null
                ? e.getClass().getSimpleName() : e.getClass().getSimpleName() + ": " + e.getMessage()));
    }

    private static Path realOrGiven(Path workingDirectory) {
        try {
            return workingDirectory.toRealPath();
        } catch (IOException | RuntimeException hasNone) {
            return workingDirectory;
        }
    }

    private static void closeQuietly(FileChannel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
            // nothing is left to do for a channel that will not close
        }
    }
}
