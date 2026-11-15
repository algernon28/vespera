package io.algernon.vespera.engine.pipeline;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Holds the machine for the length of the process, so that one Vespera runs on a machine at a time and none stops the
 * sidecars another is using (ADR-395).
 *
 * <p>The same shape as {@link CorpusRootLock}: an operating-system lock on one byte far past the end of {@code
 * machine.lock}, so the holder's line stays readable while it is held; released by the operating system however the
 * process ends; the file never deleted. A start whose parent process holds the machine as the control panel is let
 * through, and holds {@code machine-child.lock} of its own instead (ADR-395 §3).
 */
public class MachineLock {

    /** The file of the machine lock, in the folder the corpus-root locks lie in. */
    public static final String FILE_NAME = "machine.lock";

    /** The file a control panel's own child holds. */
    public static final String CHILD_FILE_NAME = "machine-child.lock";

    /** The environment variable that moves the folder, read the way the control panel reads it (ADR-302 §2). */
    public static final String FOLDER_VARIABLE = "VESPERA_CORPUS_ROOT_LOCK_FOLDER";

    private static final String DEFAULT_FOLDER = ".vespera-root-locks";

    private static final long LOCKED_BYTE = Long.MAX_VALUE - 1;

    private static final long LOCKED_LENGTH = 1;

    /** The holder line's last field, read as its last word whatever the arguments hold. */
    private static final Pattern HOLDER = Pattern.compile("pid=(\\d+) .* kind=(\\S+)");

    /** What this JVM holds, for the life of the JVM; a lock lives only as long as its channel does. */
    private static final List<Held> HELD = new CopyOnWriteArrayList<>();

    private record Held(FileChannel channel, FileLock lock) {
    }

    /** What a start is, which says how it is quoted and what a refusal tells it to do next. */
    public enum Kind {
        CONTROL_PANEL("open the control panel again"),
        RUN("run the same command again"),
        LABEL("run the same command again"),
        PUBLISH("run the same command again");

        private final String then;

        Kind(String then) {
            this.then = then;
        }

        /** The word the holder line writes: the constant's name in lower case, hyphenated, so that none is a table's name. */
        public String word() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        /** What a refusal tells this kind to do next. */
        String then() {
            return then;
        }

        /** The kind the first argument names: {@code run}, {@code label} or {@code publish}; none for any other. */
        public static Optional<Kind> ofTheCommand(String[] arguments) {
            if (arguments.length == 0) {
                return Optional.empty();
            }
            return Stream.of(RUN, LABEL, PUBLISH).filter(kind -> kind.word().equals(arguments[0])).findFirst();
        }
    }

    /** The folder as the environment wrote it, read as a path only in {@link #take}. */
    private final String named;

    /** Where the value is a home, the name of the folder below it. */
    private final String below;

    private final Path folder;

    private final OptionalLong parentPid;

    public MachineLock(Path folder) {
        this(folder, ProcessHandle.current().parent().map(parent -> OptionalLong.of(parent.pid()))
                .orElse(OptionalLong.empty()));
    }

    MachineLock(Path folder, OptionalLong parentPid) {
        this.named = null;
        this.below = null;
        this.folder = folder;
        this.parentPid = parentPid;
    }

    private MachineLock(String named, String below, OptionalLong parentPid) {
        this.named = named;
        this.below = below;
        this.folder = null;
        this.parentPid = parentPid;
    }

    /** The lock in the folder the environment names, where it is not blank, and in the user's home otherwise. */
    public static MachineLock ofThisProcess() {
        return ofTheFolderNamed(System.getenv(FOLDER_VARIABLE), System.getProperty("user.home"));
    }

    /**
     * The lock in the folder {@code value} names, where it is not blank, and in {@code userHome} otherwise. Never
     * throws: a value that is no path is read as one only in {@link #take}, where it is refused (ADR-395 §2).
     */
    static MachineLock ofTheFolderNamed(String value, String userHome) {
        OptionalLong parent = ProcessHandle.current().parent().map(p -> OptionalLong.of(p.pid()))
                .orElse(OptionalLong.empty());
        if (value == null || value.isBlank()) {
            return new MachineLock(userHome, DEFAULT_FOLDER, parent);
        }
        return new MachineLock(value, null, parent);
    }

    /**
     * Takes the lock as {@code kind} and answers the refusal's line, empty where it was taken.
     */
    public Optional<String> refusal(Kind kind, String[] arguments) {
        try {
            take(kind, arguments);
            return Optional.empty();
        } catch (AnotherVesperaRunsException | MachineLockUnavailableException refused) {
            return Optional.of(refused.getMessage());
        }
    }

    /**
     * Holds the machine until the process ends.
     *
     * @throws AnotherVesperaRunsException when another process, or this one, holds it and this start is not let through
     * @throws MachineLockUnavailableException when the folder or a file cannot be created, opened or locked
     */
    public void take(Kind kind, String[] arguments) {
        Path folder;
        try {
            folder = this.folder != null ? this.folder
                    : below == null ? Path.of(named) : Path.of(named).resolve(below);
        } catch (InvalidPathException noPath) {
            throw new MachineLockUnavailableException(named, noPath, kind);
        }
        Path machineFile = folder.resolve(FILE_NAME);
        Path childFile = folder.resolve(CHILD_FILE_NAME);
        try {
            Files.createDirectories(folder);
            Held machine = tryLock(machineFile);
            if (machine.lock() != null) {
                takeAsTheOnlyOne(machine, kind, arguments, machineFile, childFile);
                return;
            }
            String holder = holderLine(machineFile);
            if (!isMyParent(holder)) {
                throw new AnotherVesperaRunsException(holder, kind);
            }
            Held child = tryLock(childFile);
            if (child.lock() == null) {
                throw new AnotherVesperaRunsException(holderLine(childFile), kind);
            }
            try {
                write(child.channel(), line(kind, arguments));
            } catch (IOException | RuntimeException notWritten) {
                release(child);
                throw notWritten;
            }
        } catch (IOException | RuntimeException unusable) {
            if (unusable instanceof AnotherVesperaRunsException refused) {
                throw refused;
            }
            if (unusable instanceof MachineLockUnavailableException refused) {
                throw refused;
            }
            throw new MachineLockUnavailableException(folder, unusable, kind);
        }
    }

    private void takeAsTheOnlyOne(Held machine, Kind kind, String[] arguments, Path machineFile, Path childFile)
            throws IOException {
        try {
            // A child a control panel forced to end left running still holds the machine (ADR-395 §3).
            Held child = tryLock(childFile);
            if (child.lock() == null) {
                throw new AnotherVesperaRunsException(holderLine(childFile), kind);
            }
            release(child);
            write(machine.channel(), line(kind, arguments));
        } catch (IOException | RuntimeException failed) {
            release(machine);
            throw failed;
        }
    }

    /** Opens the file and tries its byte; the lock is {@code null} where another process or this one holds it. */
    private Held tryLock(Path file) throws IOException {
        FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock(LOCKED_BYTE, LOCKED_LENGTH, false);
        } catch (OverlappingFileLockException heldByThisProcess) {
            // Kept open, not closed: closing may release every lock this JVM holds on the file (FileLock javadoc).
            Held kept = new Held(channel, null);
            HELD.add(kept);
            return kept;
        } catch (IOException | RuntimeException failed) {
            closeQuietly(channel);
            throw failed;
        }
        if (lock == null) {
            closeQuietly(channel);
            return new Held(channel, null);
        }
        Held held = new Held(channel, lock);
        HELD.add(held);
        return held;
    }

    /** True where the holder line is a control panel's and its pid is this process's parent (ADR-395 §3). */
    private boolean isMyParent(String holder) {
        if (parentPid.isEmpty() || holder == null) {
            return false;
        }
        Matcher matcher = HOLDER.matcher(holder);
        if (!matcher.matches() || !matcher.group(2).equals(Kind.CONTROL_PANEL.word())) {
            return false;
        }
        try {
            return Long.parseLong(matcher.group(1)) == parentPid.getAsLong();
        } catch (NumberFormatException overflows) {
            return false;
        }
    }

    private static String line(Kind kind, String[] arguments) {
        return "pid=" + ProcessHandle.current().pid()
                + " started=" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                        OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS))
                + " command=" + String.join(" ", arguments).replaceAll("\\R", " ")
                + " kind=" + kind.word();
    }

    private static void write(FileChannel channel, String line) throws IOException {
        channel.truncate(0);
        ByteBuffer bytes = ByteBuffer.wrap((line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        long position = 0;
        while (bytes.hasRemaining()) {
            position += channel.write(bytes, position);
        }
    }

    /** Lets go of every lock this JVM holds, for a test to hand the folder back. */
    static void release() {
        for (Held held : HELD) {
            letGo(held);
        }
        HELD.clear();
    }

    private static void release(Held held) {
        letGo(held);
        HELD.remove(held);
    }

    private static void letGo(Held held) {
        try {
            if (held.lock() != null && held.lock().isValid()) {
                held.lock().release();
            }
        } catch (IOException ignored) {
            // Closing the channel below releases it all the same.
        }
        closeQuietly(held.channel());
    }

    private static String holderLine(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).strip();
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
