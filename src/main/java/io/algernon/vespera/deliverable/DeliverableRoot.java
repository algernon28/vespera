package io.algernon.vespera.deliverable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Where the tree is drawn, and which places it may not be drawn in (ADR-276).
 *
 * <p>{@link #UNNAMED} draws it at {@code <working directory>/deliverable/<generation run>}, as before a root could be
 * named; a root the operator names is the tree itself (§1). This is the one class that answers where the tree is, so
 * the generation step and the closing line ask it and the path is composed once. It keys no run (§1, §8).
 *
 * <p>It refuses two ways, each in the one line the operator reads. {@link #refusalAtTheStart} is asked before the job
 * and compares paths, by their real paths and name by name, with the corpus root and the seed folder, so no copy is
 * ever walked back in as a file occurrence (§2); it also asks that a named root be empty or hold a tree of this
 * working directory (§3). {@link #refusalAtTheDrawing} is asked once the generation run is known (§3). Neither makes
 * a folder, writes a file or opens a document.
 *
 * <p>Every tree carries a {@link #MARKER_FILE_NAME marker} naming the working directory and the generation run that
 * drew it. This class writes it and reads it, one class for the rule (ADR-213); a marker that cannot be read, or
 * lacks either line, is read as no marker.
 */
public final class DeliverableRoot {

    /** The marker at the top of every tree: two lines, the working directory and the generation run (ADR-276 §3). */
    public static final String MARKER_FILE_NAME = "vespera-deliverable.txt";

    /** No root named: the tree lies in the working directory's {@code deliverable/} folder. */
    public static final DeliverableRoot UNNAMED = new DeliverableRoot(null);

    private static final String WORKING_DIRECTORY_KEY = "working-directory=";

    private static final String GENERATION_RUN_KEY = "generation-run=";

    private static final String NAME_A_FOLDER_OUTSIDE =
            ". Name a folder outside the corpus root and the seed folder in vespera.deliverable-root.";

    private static final boolean NAMES_IGNORE_CASE =
            System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");

    /** The marker as it was read: the working directory and the generation run it names. */
    private record Marker(String workingDirectory, String generationRun) {}

    /** The root as named, absolute and normalised, or {@code null} where none is. */
    private final Path named;

    private DeliverableRoot(Path named) {
        this.named = named;
    }

    /** The tree is {@code root} itself. */
    public static DeliverableRoot named(Path root) {
        return new DeliverableRoot(root.toAbsolutePath().normalize());
    }

    /** {@link #UNNAMED} for {@code null} or a blank value, otherwise the value stripped and {@linkplain #named named}. */
    public static DeliverableRoot of(String value) {
        return value == null || value.isBlank() ? UNNAMED : named(Path.of(value.strip()));
    }

    /** The tree of {@code generationRun}: the named root, or {@code <workingDirectory>/deliverable/<generationRun>}. */
    public Path treeOf(Path workingDirectory, String generationRun) {
        return named != null
                ? named
                : workingDirectory.resolve(Deliverable.DIRECTORY_NAME).resolve(generationRun);
    }

    /**
     * The line refusing this root before the job, or none (ADR-276 §2, §3): the root, or for {@link #UNNAMED} the
     * working directory's {@code deliverable/} folder, may not be the corpus root or the seed folder, inside either
     * or above either; and a named root must be absent, an empty folder, or hold a tree of this working directory.
     */
    public Optional<String> refusalAtTheStart(Path workingDirectory, Path corpusRoot, Optional<Path> seedFolder) {
        Path root = realPathOf(named != null ? named : workingDirectory.resolve(Deliverable.DIRECTORY_NAME));
        Optional<String> overlap = overlapWith(root, "corpus root", realPathOf(corpusRoot));
        if (overlap.isEmpty() && seedFolder.isPresent()) {
            overlap = overlapWith(root, "seed folder", realPathOf(seedFolder.get()));
        }
        if (overlap.isPresent()) {
            return overlap;
        }
        if (named == null || !Files.exists(root)) {
            return Optional.empty();
        }
        String refused = "vespera run refused the deliverable root " + root + ": ";
        if (!Files.isDirectory(root)) {
            return Optional.of(refused + "it is not a folder. Name an empty folder in vespera.deliverable-root.");
        }
        if (isEmpty(root) || markerOf(root).filter(marker -> isWorkingDirectory(marker, workingDirectory)).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(refused + "it is not empty and holds no tree of this working directory. Name an empty folder"
                + " in vespera.deliverable-root.");
    }

    /**
     * The line refusing a drawing of {@code generationRun}'s tree here, or none (ADR-276 §3): a named root must be
     * absent, empty, or hold the marker of this working directory and this generation run, and is then drawn again
     * in place. {@link #UNNAMED} is never refused: its folder is named for the generation run.
     */
    public Optional<String> refusalAtTheDrawing(Path workingDirectory, String generationRun) {
        if (named == null) {
            return Optional.empty();
        }
        Path root = realPathOf(named);
        if (!Files.exists(root) || isEmpty(root)) {
            return Optional.empty();
        }
        Optional<Marker> ofThisWorkingDirectory =
                markerOf(root).filter(marker -> isWorkingDirectory(marker, workingDirectory));
        if (ofThisWorkingDirectory.isEmpty()) {
            return Optional.of("the deliverable root " + root + " is not empty and holds no tree of this working"
                    + " directory, so no tree was drawn. Name an empty folder in vespera.deliverable-root, and run"
                    + " again.");
        }
        if (!ofThisWorkingDirectory.get().generationRun().equals(generationRun)) {
            return Optional.of("the deliverable root " + root + " holds the tree of another generation run, so no tree"
                    + " was drawn. Name an empty folder in vespera.deliverable-root, and run again.");
        }
        return Optional.empty();
    }

    /** Writes the marker at the top of {@code tree}, in place and over itself (ADR-276 §3). */
    static void markTree(Path tree, Path workingDirectory, String generationRun) throws IOException {
        Path recorded;
        try {
            recorded = workingDirectory.toRealPath();
        } catch (IOException | RuntimeException noRealPath) {
            recorded = workingDirectory.toAbsolutePath().normalize();
        }
        Files.writeString(
                tree.resolve(MARKER_FILE_NAME),
                WORKING_DIRECTORY_KEY + recorded + "\n" + GENERATION_RUN_KEY + generationRun + "\n",
                StandardCharsets.UTF_8);
    }

    private static Optional<Marker> markerOf(Path root) {
        String workingDirectory = null;
        String generationRun = null;
        try {
            for (String line : Files.readAllLines(root.resolve(MARKER_FILE_NAME), StandardCharsets.UTF_8)) {
                if (line.startsWith(WORKING_DIRECTORY_KEY)) {
                    workingDirectory = line.substring(WORKING_DIRECTORY_KEY.length());
                } else if (line.startsWith(GENERATION_RUN_KEY)) {
                    generationRun = line.substring(GENERATION_RUN_KEY.length());
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
        return workingDirectory == null || generationRun == null
                ? Optional.empty()
                : Optional.of(new Marker(workingDirectory, generationRun));
    }

    private static boolean isWorkingDirectory(Marker marker, Path workingDirectory) {
        try {
            return sameName(
                    realPathOf(Path.of(marker.workingDirectory())).toString(),
                    realPathOf(workingDirectory).toString());
        } catch (RuntimeException notAPath) {
            return false;
        }
    }

    private static boolean isEmpty(Path folder) {
        try (Stream<Path> entries = Files.list(folder)) {
            return entries.findAny().isEmpty();
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    /** The line for {@code root} standing in a relation to {@code folder}, equal, then inside, then above. */
    private static Optional<String> overlapWith(Path root, String what, Path folder) {
        String relation;
        if (lies(root, folder) && lies(folder, root)) {
            relation = "the " + what;
        } else if (lies(root, folder)) {
            relation = "inside the " + what;
        } else if (lies(folder, root)) {
            relation = "above the " + what;
        } else {
            return Optional.empty();
        }
        return Optional.of("vespera run refused the deliverable root " + root + ": it is " + relation + " " + folder
                + NAME_A_FOLDER_OUTSIDE);
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
     * {@code path} as {@code Walk.canonicalRoot} reads a corpus root (ADR-051): its real path where it exists, and
     * where it does not the real path of the nearest folder above it that does, with the names below it as written.
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
