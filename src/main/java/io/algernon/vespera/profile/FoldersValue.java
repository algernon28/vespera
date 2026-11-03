package io.algernon.vespera.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A profile key whose answer is a list of folders, written as one string (ADR-334 §5.1): {@code codeSetFolders}. The
 * string stays one under {@code value}, as every key's does (ADR-061), so {@code keysAsWritten} and census read it as
 * they read any other.
 *
 * <p>Folders are separated by the system's path separator, {@code ;} on Windows and {@code :} elsewhere, as a
 * {@code PATH} holds them. Each piece is stripped and an empty piece is passed over. Each piece must be an absolute
 * path the system can hold; one that is not makes the whole value unreadable (ADR-120), and none of it is read.
 *
 * <p>The reading is computed rather than stored, for the reason {@link NumericValue}'s is (ADR-061).
 *
 * @param value what the profile says, exactly as the operator wrote it
 * @param provenance how the operator arrived at it
 * @param measurement census's pointer at the data that should inform it, or {@code null}
 */
public record FoldersValue(String value, String provenance, Measurement measurement) implements ProfileValue {

    /** A key census has created and nobody has answered. */
    public static FoldersValue unset() {
        return new FoldersValue(null, null, null);
    }

    /** What this key says, as one of ADR-120's three states. */
    @JsonIgnore
    public Reading reading() {
        if (!isSet()) {
            return new Unset();
        }
        String text = value().strip();
        List<Path> folders = new ArrayList<>();
        for (String piece : text.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            String stripped = piece.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            try {
                Path folder = Path.of(stripped);
                if (!folder.isAbsolute()) {
                    return new Unreadable(text);
                }
                folders.add(folder);
            } catch (InvalidPathException cannotBeHeld) {
                return new Unreadable(text);
            }
        }
        return folders.isEmpty() ? new Unreadable(text) : new Answered(List.copyOf(folders));
    }

    /** Nobody has answered the key, somebody answered it wrongly, or here are the folders. */
    public sealed interface Reading permits Unset, Unreadable, Answered {}

    /** Nobody has answered the key. */
    public record Unset() implements Reading {}

    /**
     * The operator wrote something no list of absolute folders can be read from.
     *
     * @param text what they wrote, stripped, so that it can be quoted back
     */
    public record Unreadable(String text) implements Reading {}

    /**
     * The folders the operator meant.
     *
     * @param folders each as written, absolute, in the order written
     */
    public record Answered(List<Path> folders) implements Reading {}
}
