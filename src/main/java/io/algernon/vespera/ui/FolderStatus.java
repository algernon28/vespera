package io.algernon.vespera.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * What a folder field of the window says of its folder (ADR-331 §6, which ADR-342 keeps): not chosen, found with the free
 * space of its drive, to be created where the jar creates it, or no such folder.
 *
 * @param state {@code not-chosen}, {@code found}, {@code will-be-created} or {@code no-such-folder}
 * @param words what the window shows
 */
public record FolderStatus(String state, String words) {

    private static final double BYTES_IN_A_GIB = 1024d * 1024d * 1024d;

    /**
     * The status of the folder {@code value} names; {@code createdByTheJar} where the jar creates the folder that is
     * not there, as it does the working directory.
     */
    public static FolderStatus of(String value, boolean createdByTheJar) {
        if (value.isBlank()) {
            return new FolderStatus("not-chosen", "Not chosen");
        }
        Path folder;
        try {
            folder = Path.of(value.stripTrailing()).toAbsolutePath().normalize();
        } catch (RuntimeException cannotBeHeld) {
            return new FolderStatus("no-such-folder", "No such folder");
        }
        if (Files.isDirectory(folder)) {
            return usable(folder.toString()).map(bytes -> new FolderStatus("found", "Found, " + gib(bytes) + " free"))
                    .orElse(new FolderStatus("found", "Found"));
        }
        if (createdByTheJar && Files.notExists(folder)) {
            return new FolderStatus("will-be-created", "Will be created");
        }
        return new FolderStatus("no-such-folder", "No such folder");
    }

    /** {@code bytes} in GiB to one decimal, as the window shows free space (ADR-331 §6). */
    static String gib(long bytes) {
        return String.format(Locale.ROOT, "%.1f GiB", bytes / BYTES_IN_A_GIB);
    }

    /** The usable bytes of the drive of {@code folder}, measured at its nearest ancestor that exists (ADR-302 §6). */
    static Optional<Long> usable(String folder) {
        if (folder.isEmpty()) {
            return Optional.empty();
        }
        try {
            Path measured = Path.of(folder).toAbsolutePath().normalize();
            while (measured != null && Files.notExists(measured)) {
                measured = measured.getParent();
            }
            return measured == null ? Optional.empty() : Optional.of(Files.getFileStore(measured).getUsableSpace());
        } catch (IOException | RuntimeException unmeasured) {
            return Optional.empty();
        }
    }
}
