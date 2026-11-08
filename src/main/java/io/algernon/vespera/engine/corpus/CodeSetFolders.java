package io.algernon.vespera.engine.corpus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The check of a code set's folder outside the corpus root, made at the start of {@code vespera run} (ADR-334 §5.1):
 * each folder the profile's {@code codeSetFolders} names must be a folder that is there, and may not be equal to,
 * inside or above the corpus root, the working directory, the seed folder, the deliverable root's folder or a folder
 * listed before it. Paths are compared by their real paths and name by name, without regard to case on Windows, as
 * ADR-276 §2 compares the deliverable root's. Nothing here makes a folder, writes a file or opens a document.
 */
public final class CodeSetFolders {

    private static final boolean NAMES_IGNORE_CASE =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    private CodeSetFolders() {}

    /**
     * The line refusing the first of {@code folders} that is no folder there or overlaps a folder it must stay apart
     * from, or none.
     *
     * @param folders the folders the profile names, in its order
     * @param seedFolder the profile's seed folder, where it names one
     * @param deliverableFolder the folder the deliverable root draws in: the root named, or the working directory's
     *     {@code deliverable} folder
     */
    public static Optional<String> refusal(
            List<Path> folders,
            Path corpusRoot,
            Path workingDirectory,
            Optional<Path> seedFolder,
            Path deliverableFolder) {
        for (int i = 0; i < folders.size(); i++) {
            Path folder = realPathOf(folders.get(i));
            String refused = "vespera run refused the code set folder " + folder + ": ";
            if (!Files.isDirectory(folder)) {
                return Optional.of(refused + "it is no folder that is there. Name a folder that is there in"
                        + " codeSetFolders.");
            }
            List<Apart> apart = new ArrayList<>();
            apart.add(new Apart("the corpus root", realPathOf(corpusRoot)));
            apart.add(new Apart("the working directory", realPathOf(workingDirectory)));
            seedFolder.ifPresent(seed -> apart.add(new Apart("the seed folder", realPathOf(seed))));
            apart.add(new Apart("the deliverable root", realPathOf(deliverableFolder)));
            for (int before = 0; before < i; before++) {
                apart.add(new Apart("the code set folder", realPathOf(folders.get(before))));
            }
            for (Apart other : apart) {
                Optional<String> relation = relationOf(folder, other.what(), other.folder());
                if (relation.isPresent()) {
                    return Optional.of(refused + "it is " + relation.get() + " " + other.folder()
                            + ". Name a folder outside the corpus root, the working directory, the seed folder, the"
                            + " deliverable root and the other code set folders in codeSetFolders.");
                }
            }
        }
        return Optional.empty();
    }

    private record Apart(String what, Path folder) {}

    /** The folder standing in a relation to {@code other}: equal, then inside, then above; none where apart. */
    private static Optional<String> relationOf(Path folder, String what, Path other) {
        if (lies(folder, other) && lies(other, folder)) {
            return Optional.of(what);
        }
        if (lies(folder, other)) {
            return Optional.of("inside " + what);
        }
        if (lies(other, folder)) {
            return Optional.of("above " + what);
        }
        return Optional.empty();
    }

    /** Whether {@code path} is {@code folder} or lies inside it, compared name by name. */
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

    /**
     * {@code path} by its real path where it exists, and where it does not the real path of the nearest folder above
     * it that does, with the names below it as written.
     */
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
