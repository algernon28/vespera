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
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
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
 *
 * <p>A save of {@code profile.yaml} makes the working directory where it is not there, each folder born the user's
 * alone, and a folder that exists is checked by its owner on a file system with no POSIX permissions (ADR-330 §7). The
 * copy a repair of {@code profile.yaml} keeps of the file as it stood is made here too, called from inside the change
 * so that it is made under the lock and before the repaired text is written (ADR-330 §6, §13).
 */
public final class WorkingDirectoryWrite {

    /** The file the lock is taken on, which the jar's invocations take too. */
    public static final String LOCK_FILE_NAME = "vespera.lock";

    /** The three files of a working directory the control panel writes, and no other. */
    public static final Set<String> FILES = Set.of("profile.yaml", "relevance-labels.yaml", "code-set-names.yaml");

    /** The one file whose save makes a working directory that is not there (ADR-330 §7). */
    private static final String PROFILE = "profile.yaml";

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
        if (fileName.equals(PROFILE) && Files.notExists(workingDirectory, LinkOption.NOFOLLOW_LINKS)) {
            Optional<String> notMade = makeFolder(workingDirectory, fileName, change);
            if (notMade.isPresent()) {
                return notMade;
            }
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
     * checked by its real path, or as given where it has none; empty for one that is. On a file system with no POSIX
     * permissions a folder that exists is checked by its owner alone (ADR-330 §7).
     */
    public static Optional<String> refusalOfFolder(Path workingDirectory, String userName, String fileName) {
        Path folder = realOrGiven(workingDirectory);
        if (Files.exists(folder, LinkOption.NOFOLLOW_LINKS) && FolderOwnership.isOwnWorkingDirectory(folder, userName)) {
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

    /**
     * Makes the working directory a save of {@code profile.yaml} finds missing, each folder it makes born the user's
     * alone (ADR-330 §7): by {@code rwx------} on POSIX permissions, and on a volume with access control lists by one
     * entry for this user, inherited by what is made below, so that no moment lies between the folder and its rule. On a
     * volume with neither it is made as the volume makes it. The change is asked first, of an absent file, so that a
     * value it refuses makes no folder. Once made the folder is checked as an existing one is, by its real path, and its
     * entry is read back and must be that one alone.
     *
     * @return the line that stopped it, empty where the folder is there
     */
    /**
     * Copies {@code fileName} whole to {@code <fileName>.before-repair-<yyyyMMdd-HHmmss>} beside it, never over an
     * earlier copy (a number is added to the name instead). For a change that runs under the lock.
     *
     * @return the copy's name
     * @throws IllegalArgumentException with the line that stops the repair where no copy can be made
     */
    static String keepCopyBeforeRepair(Path folder, String fileName) {
        String stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        for (int n = 1; ; n++) {
            String name = fileName + ".before-repair-" + stamp + (n == 1 ? "" : "-" + n);
            try {
                Files.copy(folder.resolve(fileName), folder.resolve(name));
                return name;
            } catch (java.nio.file.FileAlreadyExistsException taken) {
                continue;
            } catch (IOException e) {
                throw new IllegalArgumentException("the control panel could not keep a copy of " + fileName
                        + " beside it, so it was not repaired: " + (e.getMessage() == null
                                ? e.getClass().getSimpleName() : e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
    }

    private Optional<String> makeFolder(Path workingDirectory, String fileName, Function<Optional<String>, String> change) {
        try {
            change.apply(Optional.empty());
        } catch (IllegalArgumentException refused) {
            return Optional.of(refused.getMessage());
        }
        Path folder = workingDirectory.toAbsolutePath();
        try {
            UserPrincipal user = null;
            FileAttribute<?>[] attributes = new FileAttribute<?>[0];
            java.nio.file.FileStore volume = Files.getFileStore(nearestThatExists(folder));
            if (volume.supportsFileAttributeView("posix")) {
                attributes = new FileAttribute<?>[] {
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))};
            } else if (volume.supportsFileAttributeView("acl")) {
                user = folder.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(userName);
                attributes = new FileAttribute<?>[] {onlyThisUser(user)};
            }
            Files.createDirectories(folder, attributes);
            if (user != null) {
                List<AclEntry> entries = Files.getFileAttributeView(folder, AclFileAttributeView.class).getAcl();
                if (entries.size() != 1 || entries.getFirst().type() != AclEntryType.ALLOW
                        || !entries.getFirst().principal().equals(user)) {
                    return Optional.of("the control panel made " + folder + " and it is not a folder only this user"
                            + " can write in, so " + fileName + " was not written");
                }
            }
        } catch (IOException | RuntimeException e) {
            return couldNotWrite(fileName, e);
        }
        return Optional.empty();
    }

    /** The one entry a folder made on a volume with access control lists holds: this user, every right, inherited. */
    private static FileAttribute<List<AclEntry>> onlyThisUser(UserPrincipal user) {
        List<AclEntry> entries = List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(user)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT).build());
        return new FileAttribute<>() {
            @Override
            public String name() {
                return "acl:acl";
            }

            @Override
            public List<AclEntry> value() {
                return entries;
            }
        };
    }

    private static Path nearestThatExists(Path folder) {
        Path at = folder;
        while (at != null && Files.notExists(at)) {
            at = at.getParent();
        }
        return at == null ? folder.getRoot() : at;
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
