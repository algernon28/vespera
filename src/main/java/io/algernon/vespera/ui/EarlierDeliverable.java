package io.algernon.vespera.ui;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One row of the window's <i>Earlier deliverables</i>: a generation run that a kept {@code vespera run} reached and
 * whose tree still stands, shown by the date of the invocation that last reached it and never by a run id (ADR-439 §6;
 * ADR-098). Read from the copies in {@code logs} and, of each tree, its marker and whether each destination's list is
 * there: no database is opened, no lock taken and no other file of a tree read (ADR-285 §3, ADR-429 §5). In
 * {@code java.base} alone.
 *
 * @param invocation the id of the newest copy that reached the generation run, which a publish names (ADR-439 §1)
 * @param started that invocation's {@code invocation.started}, as {@code yyyy-MM-dd HH:mm} in the zone given
 * @param corpusRoot that copy's {@code places.corpusRoot} as it holds it
 * @param tree the tree, absolute and normalised, as ADR-439 §3 places it
 * @param destinations the destinations of {@link ChildCommand#DESTINATIONS}, in its order, whose list lies in the tree
 * @param latest whether this is the deliverable the newest {@code vespera run}'s copy reached, the one a publish naming
 *     no invocation delivers
 */
public record EarlierDeliverable(String invocation, String started, String corpusRoot, Path tree,
        List<String> destinations, boolean latest) {

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Pattern COPY = Pattern.compile("^report-([0-9]{8}T[0-9]{6}Z)(?:-([0-9]+))?\\.json$");

    private static final String MARKER_FILE_NAME = "vespera-deliverable.txt";

    private static final String WORKING_DIRECTORY_KEY = "working-directory=";

    private static final String GENERATION_RUN_KEY = "generation-run=";

    /** What a kept {@code vespera run}'s copy holds of what this reads. */
    private record Run(String id, String started, String corpusRoot, String generationRun, String deliverableRoot) {
    }

    public EarlierDeliverable {
        destinations = List.copyOf(destinations);
    }

    /**
     * The deliverables of {@code workingDirectory}'s kept {@code vespera run}s that still stand, newest first, one for
     * each generation run, from the newest copy that reached it (ADR-439 §6). Never throws: what cannot be read is not
     * listed.
     */
    public static List<EarlierDeliverable> of(Path workingDirectory, ZoneId zone) {
        return read(workingDirectory, zone).rows();
    }

    /**
     * What one reading of {@code workingDirectory}'s copies gives the window (ADR-439 §6, §7): the rows {@link #of}
     * gives, and whether a copy of a {@code vespera run}'s report is kept at all, each copy read once.
     *
     * @param rows the deliverables that still stand, newest first
     * @param keepsARun whether a copy of a {@code vespera run}'s report that can be read is kept
     */
    record Listing(List<EarlierDeliverable> rows, boolean keepsARun) {

        /** What a window with no working directory chosen reads. */
        static final Listing NONE = new Listing(List.of(), false);

        Listing {
            rows = List.copyOf(rows);
        }
    }

    /** The rows and whether a run's copy is kept, from one reading of the copies; never throws. */
    static Listing read(Path workingDirectory, ZoneId zone) {
        List<Run> runs;
        try {
            runs = runsOf(workingDirectory);
        } catch (RuntimeException unreadable) {
            return Listing.NONE;
        }
        return new Listing(rowsOf(workingDirectory, runs, zone), !runs.isEmpty());
    }

    private static List<EarlierDeliverable> rowsOf(Path workingDirectory, List<Run> runs, ZoneId zone) {
        try {
            if (runs.isEmpty()) {
                return List.of();
            }
            String latest = runs.getFirst().generationRun();
            Path folder = workingDirectory.toAbsolutePath().normalize();
            Map<String, EarlierDeliverable> rows = new LinkedHashMap<>();
            for (Run run : runs) {
                if (run.generationRun() == null || run.deliverableRoot() == null
                        || rows.containsKey(run.generationRun())) {
                    continue;
                }
                Optional<Path> tree = standing(folder, run.deliverableRoot(), run.generationRun());
                if (tree.isEmpty()) {
                    continue;
                }
                List<String> drawn = ChildCommand.DESTINATIONS.stream()
                        .filter(each -> Files.isRegularFile(
                                tree.get().resolve(each).resolve(ChildCommand.DESTINATION_LISTS.get(each))))
                        .toList();
                if (!drawn.isEmpty()) {
                    rows.put(run.generationRun(), new EarlierDeliverable(run.id(), startedIn(run.started(), zone),
                            run.corpusRoot() == null ? "" : run.corpusRoot(), tree.get(), drawn,
                            run.generationRun().equals(latest)));
                }
            }
            return List.copyOf(rows.values());
        } catch (RuntimeException unreadable) {
            return List.of();
        }
    }

    /** What the row shows: its date, {@code (latest)} where it is, its corpus root, its output folder and what is drawn. */
    public String words() {
        return started + (latest ? " (latest)" : "") + " — corpus root: " + corpusRoot + "; output folder: " + tree
                + "; drawn: " + String.join(", ", destinations);
    }

    private static String startedIn(String started, ZoneId zone) {
        try {
            return MINUTE.format(Instant.parse(started).atZone(zone));
        } catch (RuntimeException notAnInstant) {
            return started == null ? "" : started;
        }
    }

    /**
     * The tree of {@code generationRun} where the copy's output folder {@code root} places it, or empty where it does
     * not stand: at the working directory's own {@code deliverable} folder its folder, at a named root its marker
     * naming this working directory and the run (ADR-439 §3).
     */
    private static Optional<Path> standing(Path workingDirectory, String root, String generationRun) {
        Path trees = workingDirectory.resolve("deliverable");
        if (root.isBlank() || ControlPanelState.namesTheSameFolder(root, trees.toString())) {
            Path tree = trees.resolve(generationRun);
            return Files.isDirectory(tree) ? Optional.of(tree) : Optional.empty();
        }
        Path tree = Path.of(root.strip()).toAbsolutePath().normalize();
        String markedFor = null;
        String markedRun = null;
        try {
            for (String line : Files.readAllLines(tree.resolve(MARKER_FILE_NAME), StandardCharsets.UTF_8)) {
                if (line.startsWith(WORKING_DIRECTORY_KEY)) {
                    markedFor = line.substring(WORKING_DIRECTORY_KEY.length());
                } else if (line.startsWith(GENERATION_RUN_KEY)) {
                    markedRun = line.substring(GENERATION_RUN_KEY.length());
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
        return markedFor != null && generationRun.equals(markedRun)
                && ControlPanelState.namesTheSameFolder(markedFor, workingDirectory.toString())
                        ? Optional.of(tree)
                        : Optional.empty();
    }

    /** The kept copies of {@code vespera run}s, newest first, as ADR-393 §7 reads and orders them. */
    private static List<Run> runsOf(Path workingDirectory) {
        record Copy(Path file, String stamp, long number) {
        }
        List<Copy> copies = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(workingDirectory.resolve("logs"), "report-*.json")) {
            for (Path file : files) {
                Matcher id = COPY.matcher(file.getFileName().toString());
                if (id.matches()) {
                    long number;
                    try {
                        number = id.group(2) == null ? 0 : Long.parseLong(id.group(2));
                    } catch (NumberFormatException tooLong) {
                        number = Long.MAX_VALUE;
                    }
                    copies.add(new Copy(file, id.group(1) + (id.group(2) == null ? "" : "-" + id.group(2)), number));
                }
            }
        } catch (IOException | RuntimeException noLogs) {
            return List.of();
        }
        copies.sort(Comparator.comparing((Copy copy) -> copy.stamp().substring(0, 16))
                .thenComparingLong(Copy::number).reversed());
        List<Run> runs = new ArrayList<>();
        for (Copy copy : copies) {
            try {
                Map<?, ?> report = (Map<?, ?>) JsonReader.read(Files.readString(copy.file(), StandardCharsets.UTF_8));
                if (!(report.get("format") instanceof BigDecimal format)
                        || format.longValueExact() != InvocationReportFile.FORMAT) {
                    continue;
                }
                Map<?, ?> invocation = (Map<?, ?>) report.get("invocation");
                if (invocation == null || !"run".equals(invocation.get("command"))) {
                    continue;
                }
                Map<?, ?> places = report.get("places") instanceof Map<?, ?> held ? held : Map.of();
                String generation = null;
                if (report.get("arrivedAt") instanceof List<?> arrived) {
                    for (Object reached : arrived) {
                        if (reached instanceof Map<?, ?> each && "generation".equals(each.get("stage"))
                                && each.get("run") instanceof String run) {
                            generation = run;
                        }
                    }
                }
                runs.add(new Run(copy.stamp(), (String) invocation.get("started"), (String) places.get("corpusRoot"),
                        generation, (String) places.get("deliverableRoot")));
            } catch (IOException | RuntimeException passedOver) {
                // a copy that cannot be read is passed over
            }
        }
        return runs;
    }
}
