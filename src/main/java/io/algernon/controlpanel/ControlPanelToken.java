package io.algernon.controlpanel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The token a start keeps for a tab to come back holding (ADR-323 §2): one file, {@code token}, in the folder of the
 * user's own that holds the launch values (ADR-292 §3), under the same rules. Reading and writing never throw; each
 * line they have to say is handed to a consumer, and none holds the token.
 */
final class ControlPanelToken {

    private static final String FILE_NAME = "token";
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    private ControlPanelToken() {
    }

    /** The token's file for {@code userName}, beside the launch values' file under {@code tempDirectory}. */
    static Path fileIn(Path tempDirectory, String userName) {
        return LaunchValues.fileIn(tempDirectory, userName).resolveSibling(FILE_NAME);
    }

    /** The token the file holds; empty where there is none or it cannot be taken, with one line for the latter. */
    static Optional<String> read(Path file, String userName, Consumer<String> said) {
        Path folder = file.toAbsolutePath().getParent();
        if (Files.notExists(folder, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        if (!FolderOwnership.isOwn(folder, userName)) {
            said.accept(notOwnLine(folder));
            return Optional.empty();
        }
        if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            String held = Files.readString(file, StandardCharsets.UTF_8);
            if (held.endsWith("\r\n")) {
                held = held.substring(0, held.length() - 2);
            } else if (held.endsWith("\n")) {
                held = held.substring(0, held.length() - 1);
            }
            if (!TOKEN.matcher(held).matches()) {
                throw new IOException("the file holds no token of 43 characters of base64url");
            }
            return Optional.of(held);
        } catch (IOException | RuntimeException e) {
            said.accept("the control panel could not read its token at " + file + ", and draws a new one: "
                    + reason(e));
            return Optional.empty();
        }
    }

    /** Writes {@code token} whole to a temporary file and moves it over the old one; one line where it cannot. */
    static void write(Path file, String userName, String token, Consumer<String> said) {
        Path folder = file.toAbsolutePath().getParent();
        Path temporary = null;
        try {
            if (Files.exists(folder, LinkOption.NOFOLLOW_LINKS) && !FolderOwnership.isOwn(folder, userName)) {
                said.accept(notOwnLine(folder));
                return;
            }
            if (Files.notExists(folder, LinkOption.NOFOLLOW_LINKS)) {
                if (POSIX) {
                    Files.createDirectories(folder, ownerOnly("rwx------"));
                } else {
                    Files.createDirectories(folder);
                }
                if (!FolderOwnership.isOwn(folder, userName)) {
                    said.accept(notOwnLine(folder));
                    return;
                }
            }
            temporary = Files.createTempFile(folder, "token", ".tmp", ownerOnly("rw-------"));
            Files.writeString(temporary, token, StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
        } catch (IOException | RuntimeException e) {
            said.accept("the control panel could not keep its token at " + file + ": " + reason(e));
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // nothing more can be done about a temporary file
                }
            }
        }
    }

    private static String notOwnLine(Path folder) {
        return "the control panel neither reads nor keeps its token in " + folder
                + ", which is not a folder only this user can write in";
    }

    private static FileAttribute<?>[] ownerOnly(String permissions) {
        return POSIX
                ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(permissions))}
                : new FileAttribute<?>[0];
    }

    private static String reason(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getClass().getSimpleName() + ": "
                + e.getMessage();
    }
}
