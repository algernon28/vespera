package io.algernon.vespera.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What the code-sets page checks of a folder outside the corpus root before it adds it to the profile's
 * {@code codeSetFolders} (ADR-334 §5.2), in the order of the start of {@code vespera run} (§5.1): it must be the whole
 * path of a folder that is there, and may not be equal to, inside or above the corpus root, the working directory, the
 * seed folder, the output folder or a folder already stated. Paths are compared by their real paths and name by name,
 * without regard to case on Windows. The control panel links no class of the jar (ADR-285 §3), so this holds the
 * comparison the jar's own start makes, for the page to refuse early with its own words.
 */
final class CodeSetFolderChecks {

    private static final boolean NAMES_IGNORE_CASE =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    private CodeSetFolderChecks() {
    }

    /** One folder the new one must stay apart from, by what the refusal calls it. */
    record Apart(String what, String folder) {
    }

    /** The folders a profile's {@code codeSetFolders} value states, in its order; none for an empty value. */
    static List<String> statedIn(String value) {
        List<String> folders = new ArrayList<>();
        if (value != null) {
            for (String piece : value.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                if (!piece.strip().isEmpty()) {
                    folders.add(piece.strip());
                }
            }
        }
        return folders;
    }

    /**
     * The line refusing {@code folder} as one to add, or none: {@code the folder <folder> is not added: ...}.
     *
     * @param apart the folders it must stay apart from, in the order they are compared
     */
    static Optional<String> refusal(String folder, List<Apart> apart) {
        String refused = "the folder " + folder + " is not added: ";
        Path path;
        try {
            path = Path.of(folder);
        } catch (InvalidPathException cannotBeHeld) {
            return Optional.of(refused + "it is not the whole path of a folder");
        }
        if (!path.isAbsolute()) {
            return Optional.of(refused + "it is not the whole path of a folder");
        }
        if (!Files.isDirectory(path)) {
            return Optional.of(refused + "there is no such folder");
        }
        Path real = realPathOf(path);
        for (Apart other : apart) {
            if (other.folder() == null || other.folder().isBlank()) {
                continue;
            }
            Path otherReal;
            try {
                otherReal = realPathOf(Path.of(other.folder().stripTrailing()));
            } catch (InvalidPathException cannotBeHeld) {
                continue;
            }
            Optional<String> relation = relationOf(real, other.what(), otherReal);
            if (relation.isPresent()) {
                return Optional.of(refused + "it is " + relation.get() + " " + otherReal);
            }
        }
        return Optional.empty();
    }

    /** The real path of {@code folder} spelt as occurrences' paths spell it: a slash between its elements (ADR-334 §5.4). */
    static String spelt(String folder) {
        try {
            return realPathOf(Path.of(folder)).toString().replace(File.separatorChar, '/');
        } catch (InvalidPathException cannotBeHeld) {
            return folder.replace('\\', '/');
        }
    }

    private static Optional<String> relationOf(Path folder, String what, Path other) {
        if (lies(folder, other) && lies(other, folder)) {
            return Optional.of("the " + what);
        }
        if (lies(folder, other)) {
            return Optional.of("inside the " + what);
        }
        if (lies(other, folder)) {
            return Optional.of("above the " + what);
        }
        return Optional.empty();
    }

    private static boolean lies(Path path, Path folder) {
        if (!sameName(String.valueOf(path.getRoot()), String.valueOf(folder.getRoot()))
                || path.getNameCount() < folder.getNameCount()) {
            return false;
        }
        for (int i = 0; i < folder.getNameCount(); i++) {
            if (!sameName(path.getName(i).toString(), folder.getName(i).toString())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameName(String a, String b) {
        return NAMES_IGNORE_CASE ? a.equalsIgnoreCase(b) : a.equals(b);
    }

    private static Path realPathOf(Path path) {
        Path absolute = path.toAbsolutePath();
        try {
            if (Files.exists(absolute)) {
                return absolute.toRealPath();
            }
            Path above = absolute.normalize();
            Path below = null;
            while (above != null && !Files.exists(above)) {
                below = below == null ? above.getFileName() : above.getFileName().resolve(below);
                above = above.getParent();
            }
            if (above == null) {
                return absolute.normalize();
            }
            Path real = above.toRealPath();
            return below == null ? real : real.resolve(below);
        } catch (IOException | RuntimeException unreadable) {
            return absolute.normalize();
        }
    }
}
