package io.algernon.vespera.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A profile key whose answer is a list of folders below the corpus root, written as one string (ADR-340 §8.1):
 * {@code foldersNotWalked}. The string stays one under {@code value}, as every key's does (ADR-061), so
 * {@code keysAsWritten} and census read it as they read any other.
 *
 * <p>Folders are separated by the system's path separator, as {@link FoldersValue} has them. Each piece is stripped
 * and an empty piece is passed over. <b>A piece is a folder relative to the corpus root</b>: elements separated by
 * {@code /}, none empty, none {@code .} or {@code ..}, no {@code \}, neither beginning nor ending with {@code /}, and
 * not absolute. One piece that is not makes the whole value unreadable (ADR-120), and none of it is read: the walk then
 * reads every folder.
 *
 * <p>The reading is computed rather than stored, for the reason {@link NumericValue}'s is (ADR-061).
 *
 * @param value what the profile says, exactly as the operator wrote it
 * @param provenance how the operator arrived at it
 * @param measurement census's pointer at the data that should inform it, or {@code null}
 */
public record CorpusFoldersValue(String value, String provenance, Measurement measurement) implements ProfileValue {

    private static final Pattern A_DRIVE = Pattern.compile("^[A-Za-z]:.*");

    /** A key census has created and nobody has answered. */
    public static CorpusFoldersValue unset() {
        return new CorpusFoldersValue(null, null, null);
    }

    /** What this key says, as one of ADR-120's three states. */
    @JsonIgnore
    public Reading reading() {
        if (!isSet()) {
            return new Unset();
        }
        String text = value().strip();
        List<String> folders = new ArrayList<>();
        for (String piece : text.split(Pattern.quote(File.pathSeparator))) {
            String stripped = piece.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            if (!isAFolderBelowTheCorpusRoot(stripped)) {
                return new Unreadable(text);
            }
            folders.add(stripped);
        }
        return folders.isEmpty() ? new Unreadable(text) : new Answered(List.copyOf(folders));
    }

    /** Whether {@code piece} is elements between {@code /}, none empty, {@code .} or {@code ..}, and not absolute. */
    private static boolean isAFolderBelowTheCorpusRoot(String piece) {
        if (piece.contains("\\") || piece.startsWith("/") || piece.endsWith("/") || A_DRIVE.matcher(piece).matches()) {
            return false;
        }
        for (String element : piece.split("/", -1)) {
            if (element.isEmpty() || element.equals(".") || element.equals("..")) {
                return false;
            }
        }
        return true;
    }

    /** Nobody has answered the key, somebody answered it wrongly, or here are the folders. */
    public sealed interface Reading permits Unset, Unreadable, Answered {}

    /** Nobody has answered the key. */
    public record Unset() implements Reading {}

    /**
     * The operator wrote something no list of folders below the corpus root can be read from.
     *
     * @param text what they wrote, stripped, so that it can be quoted back
     */
    public record Unreadable(String text) implements Reading {}

    /**
     * The folders the operator meant.
     *
     * @param folders each as written, relative to the corpus root with {@code /} between its elements, in the order
     *     written
     */
    public record Answered(List<String> folders) implements Reading {}
}
