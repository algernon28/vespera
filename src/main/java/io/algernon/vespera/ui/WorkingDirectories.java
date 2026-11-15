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
 * The working directories the window lists (ADR-393 §4): the machine's folder first, then the folders the file
 * {@link WorkingDirectoryList} keeps, most recently started first, a folder the launch values file names and the file
 * does not added second. A folder named twice is listed once, compared as {@link ControlPanelState#namesTheSameFolder}
 * compares. Nothing here deletes, moves or archives a working directory.
 */
final class WorkingDirectories {

    private final Path file;
    private final String userName;
    private final Consumer<String> said;
    private final Path theDefault;
    private final List<Path> rows = new ArrayList<>();
    /** The folders New… added that no Save has made yet: the file is written once their folder exists. */
    private final Set<Path> toBeCreated = new HashSet<>();

    /**
     * The list as the file and the launch value give it; where the file cannot be read, the machine's folder and the
     * launch value's alone, with one line to {@code said}.
     *
     * @param launchValue the launch value {@code vespera.working-dir}, empty where it is
     */
    WorkingDirectories(Path file, String userName, Path theDefault, String launchValue, Consumer<String> said) {
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

    /** The machine's folder, then the others, as listed. */
    synchronized List<Path> rows() {
        List<Path> all = new ArrayList<>();
        all.add(theDefault);
        all.addAll(rows);
        return List.copyOf(all);
    }

    /** Whether {@code folder} is the machine's folder, the list's first row. */
    boolean isTheDefault(Path folder) {
        return same(theDefault, folder);
    }

    /** Adds {@code folder} as the first row after the machine's; {@code notThereYet} where New… adds it and no Save made it. */
    synchronized void add(Path folder, boolean notThereYet) {
        if (isTheDefault(folder)) {
            return;
        }
        rows.removeIf(each -> same(each, folder));
        rows.addFirst(folder);
        if (notThereYet && Files.notExists(folder)) {
            toBeCreated.add(folder);
        }
    }

    /** Takes {@code folder} off the list, the machine's folder excepted, and writes the file. */
    synchronized void remove(Path folder) {
        if (isTheDefault(folder)) {
            return;
        }
        rows.removeIf(each -> same(each, folder));
        toBeCreated.removeIf(each -> same(each, folder));
        keep();
    }

    /** As an invocation starts in {@code folder}: it moves to the top, the machine's folder excepted, and the file is written. */
    synchronized void started(Path folder) {
        if (!isTheDefault(folder)) {
            add(folder, false);
        }
        keep();
    }

    /** Writes the file, leaving out the folders New… added that are not there yet, and the machine's own. */
    synchronized void keep() {
        List<Path> written = new ArrayList<>();
        for (Path each : rows) {
            if (toBeCreated.stream().anyMatch(pending -> same(pending, each)) && Files.notExists(each)) {
                continue;
            }
            written.add(each);
        }
        toBeCreated.removeIf(Files::exists);
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
