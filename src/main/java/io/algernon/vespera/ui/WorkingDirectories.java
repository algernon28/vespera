package io.algernon.vespera.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The working directories the window lists (ADR-393 §4): the sources' folder first where a source names one (ADR-436 §3), then the folders the file
 * {@link WorkingDirectoryList} keeps, this window's folder started last on top and the others in the file's order, a folder the launch values file names and the file
 * does not added second. A folder named twice is listed once, compared as {@link ControlPanelState#namesTheSameFolder}
 * compares. Nothing here deletes, moves or archives a working directory.
 */
final class WorkingDirectories {

    private final Path file;
    private final String userName;
    private final Consumer<String> said;
    /** The sources' folder (ADR-436 §2), empty where no source names one. */
    private final Optional<Path> theDefault;
    private final List<Path> rows = new ArrayList<>();
    /** The folders New… added that no Save has made yet: the file is written once their folder exists. */
    private final Set<Path> toBeCreated = new HashSet<>();
    /** The folders this window took off the list: the file's row for one is not carried over when the file is written. */
    private final List<Path> removed = new ArrayList<>();

    /**
     * The list as the file and the launch value give it; where the file cannot be read, the sources' folder, where a
     * source names one, and the launch value's alone, with one line to {@code said}.
     *
     * @param launchValue the launch value {@code vespera.working-dir}, empty where it is
     */
    WorkingDirectories(Path file, String userName, Path theDefault, String launchValue, Consumer<String> said) {
        this(file, userName, Optional.of(theDefault), launchValue, said);
    }

    /** As above, where {@code theDefault} is empty if no source names a folder (ADR-436 §2). */
    WorkingDirectories(Path file, String userName, Optional<Path> theDefault, String launchValue, Consumer<String> said) {
        this.file = file;
        this.userName = userName;
        this.said = said;
        this.theDefault = theDefault;
        List<Path> kept = WorkingDirectoryList.load(file, userName, said).folders();
        Optional<Path> launched = launchValue.isBlank() ? Optional.empty() : ControlPanelState.pathOf(launchValue.strip());
        launched.ifPresent(folder -> {
            if (!namedIn(kept, folder)) {
                addNamed(folder);
            }
        });
        kept.forEach(this::addNamed);
    }

    /** The sources' folder where a source names one, then the others, as listed. */
    synchronized List<Path> rows() {
        List<Path> all = new ArrayList<>();
        theDefault.ifPresent(all::add);
        all.addAll(rows);
        return List.copyOf(all);
    }

    /** Whether {@code folder} is the sources' folder, the list's first row where there is one. */
    boolean isTheDefault(Path folder) {
        return theDefault.isPresent() && same(theDefault.get(), folder);
    }

    /** The sources' folder, empty where no source names one. */
    Optional<Path> theDefault() {
        return theDefault;
    }

    /** Adds {@code folder} as the first row after the machine's; {@code notThereYet} where New… adds it and no Save made it. */
    synchronized void add(Path folder, boolean notThereYet) {
        if (isTheDefault(folder)) {
            return;
        }
        rows.removeIf(each -> same(each, folder));
        removed.removeIf(each -> same(each, folder));
        rows.addFirst(folder);
        if (notThereYet && Files.notExists(folder)) {
            toBeCreated.add(folder);
        }
    }

    /** Takes {@code folder} off the list, the sources' folder excepted, and writes the file. */
    synchronized void remove(Path folder) {
        if (isTheDefault(folder)) {
            return;
        }
        rows.removeIf(each -> same(each, folder));
        toBeCreated.removeIf(each -> same(each, folder));
        removed.add(folder);
        keep();
    }

    /** As an invocation starts in {@code folder}: it moves to the top, the sources' folder excepted, and the file is written. */
    synchronized void started(Path folder) {
        if (!isTheDefault(folder)) {
            add(folder, false);
        }
        keep();
    }

    /**
     * Writes the file: this window's rows in its order, leaving out the folders New… added that are not there yet and the
     * machine's own, then the rows the file holds that this window neither holds nor removed, in the file's order, so another
     * window's rows are not lost (ADR-393 §4).
     */
    synchronized void keep() {
        List<Path> written = new ArrayList<>();
        for (Path each : rows) {
            if (toBeCreated.stream().anyMatch(pending -> same(pending, each)) && Files.notExists(each)) {
                continue;
            }
            written.add(each);
        }
        toBeCreated.removeIf(Files::exists);
        for (Path other : WorkingDirectoryList.load(file, userName, said).folders()) {
            if (!isTheDefault(other) && !namedIn(rows, other) && !namedIn(removed, other) && !namedIn(written, other)) {
                written.add(other);
            }
        }
        WorkingDirectoryList.store(file, userName, written, said);
    }

    /** Writes the file once a folder New… added is there, as a Save of {@code profile.yaml} makes it (ADR-393 §4). */
    synchronized void keepWhatWasMade() {
        if (toBeCreated.stream().anyMatch(Files::exists)) {
            keep();
        }
    }

    private void addNamed(Path folder) {
        if (!isTheDefault(folder) && !namedIn(rows, folder)) {
            rows.add(folder);
        }
    }

    private boolean namedIn(List<Path> folders, Path folder) {
        return isTheDefault(folder) || folders.stream().anyMatch(each -> same(each, folder));
    }

    private static boolean same(Path one, Path other) {
        return ControlPanelState.namesTheSameFolder(one.toString(), other.toString());
    }
}
