package io.algernon.vespera.ui;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The file {@code working-directories} of ADR-292 §3's folder, the working directories this user has used (ADR-393 §4):
 * UTF-8, one absolute, normalised path a line, each ending in {@code \n}, this window's folder started last on top and the others in the file's order, the machine's
 * folder not among them. It is read and written only where the folder is the user's own (ADR-292 §3), and written whole
 * to a temporary file in its folder moved over it (ADR-292 §2). In {@code java.base} alone (ADR-285 §3).
 */
final class WorkingDirectoryList {

    /** The file's name, beside {@code launch-values.properties} and outside every working directory, so not a file of the working directory the README lists. */
    static final String LIST_NAME = "working-directories";

    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    private WorkingDirectoryList() {
    }

    /** The file beside {@code launchValuesFile}. */
    static Path besides(Path launchValuesFile) {
        return launchValuesFile.toAbsolutePath().getParent().resolve(LIST_NAME);
    }

    /** What the file listed, and whether it could not be read (its line having been said). */
    record Read(List<Path> folders, boolean unreadable) {
    }

    /**
     * The folders the file lists, in its order, none where there is no file. A blank line, or one naming no absolute
     * path, is passed over. Never throws: where the file cannot be read, or its folder is not the user's own, there is
     * none and {@code said} gets the line of ADR-393 §4.
     */
    static Read load(Path file, String userName, Consumer<String> said) {
        Path folder = file.toAbsolutePath().getParent();
        Optional<String> refusal = LaunchValues.refusalOfFolder(folder, userName);
        if (refusal.isPresent()) {
            said.accept(unreadableLine(file, refusal.get()));
            return new Read(List.of(), true);
        }
        if (Files.notExists(file)) {
            return new Read(List.of(), false);
        }
        try {
            List<Path> folders = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                Optional<Path> named = pathOf(line);
                if (named.isPresent() && !folders.contains(named.get())) {
                    folders.add(named.get());
                }
            }
            return new Read(List.copyOf(folders), false);
        } catch (IOException | RuntimeException e) {
            said.accept(unreadableLine(file, reason(e)));
            return new Read(List.of(), true);
        }
    }

    private static String unreadableLine(Path file, String reason) {
        return "the control panel could not read its list of working directories at " + file
                + ", and lists only the folders in use: " + reason;
    }

    private static Optional<Path> pathOf(String line) {
        if (line.isBlank()) {
            return Optional.empty();
        }
        try {
            Path path = Path.of(line.strip());
            return path.isAbsolute() ? Optional.of(path.normalize()) : Optional.empty();
        } catch (InvalidPathException refused) {
            return Optional.empty();
        }
    }

    /** Never throws: writes {@code folders} whole and moves the file into place, or says one line. */
    static void store(Path file, String userName, List<Path> folders, Consumer<String> said) {
        Path folder = file.toAbsolutePath().getParent();
        Optional<String> refusal = LaunchValues.refusalOfFolder(folder, userName);
        if (refusal.isPresent()) {
            said.accept(refusal.get());
            return;
        }
        Path temporary = null;
        try {
            if (Files.notExists(folder)) {
                if (POSIX) {
                    Files.createDirectories(folder, PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rwx------")));
                } else {
                    Files.createDirectories(folder);
                }
                Optional<String> refusalOnceMade = LaunchValues.refusalOfFolder(folder, userName);
                if (refusalOnceMade.isPresent()) {
                    said.accept(refusalOnceMade.get());
                    return;
                }
            }
            temporary = POSIX
                    ? Files.createTempFile(folder, "working-directories", ".tmp",
                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                    : Files.createTempFile(folder, "working-directories", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                for (Path each : folders) {
                    writer.write(each.toAbsolutePath().normalize().toString());
                    writer.write('\n');
                }
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
        } catch (IOException | RuntimeException e) {
            said.accept("the control panel could not keep its list of working directories at " + file + ": "
                    + reason(e));
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

    private static String reason(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
