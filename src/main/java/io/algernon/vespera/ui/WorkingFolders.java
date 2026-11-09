package io.algernon.vespera.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The two folders the cockpit's fields name as they name them now, and what the working directory holds (ADR-344 §3).
 * Each is read when asked, so a part drawn before the fields are filled still reads what they say later.
 */
final class WorkingFolders {

    private final Supplier<Optional<Path>> workingDirectory;
    private final Supplier<Optional<Path>> corpusRoot;

    private final Supplier<Set<String>> natives;

    WorkingFolders(Supplier<Optional<Path>> workingDirectory, Supplier<Optional<Path>> corpusRoot) {
        this(workingDirectory, corpusRoot, Set::of);
    }

    /** {@code natives} gives the reports that open drawn by the window from their JSON (ADR-362 §10). */
    WorkingFolders(Supplier<Optional<Path>> workingDirectory, Supplier<Optional<Path>> corpusRoot,
            Supplier<Set<String>> natives) {
        this.workingDirectory = workingDirectory;
        this.corpusRoot = corpusRoot;
        this.natives = natives;
    }

    /** The working directory the field names; where it is empty, the folder the jar would use (ADR-345 §1). */
    Optional<Path> workingDirectory() {
        return workingDirectory.get();
    }

    /** The corpus root the field names. */
    Optional<Path> corpusRoot() {
        return corpusRoot.get();
    }

    /** The text of {@code name}, a file of the working directory, or none where there is no such regular file. */
    Optional<String> readFile(String name) throws IOException {
        Optional<Path> folder = workingDirectory();
        if (folder.isEmpty() || !Files.isRegularFile(folder.get().resolve(name))) {
            return Optional.empty();
        }
        return Optional.of(Files.readString(folder.get().resolve(name), StandardCharsets.UTF_8));
    }

    /**
     * Which of the reports, by name, the working directory holds now: a report that opens natively by its JSON, any
     * other by its page.
     */
    Map<String, Boolean> writtenReports() {
        Map<String, Boolean> written = new HashMap<>();
        Optional<Path> folder = workingDirectory();
        Set<String> drawnByTheWindow = natives.get();
        for (String report : Reports.ALL) {
            String file = drawnByTheWindow.contains(report) ? Reports.jsonOf(report) : Reports.pageOf(report);
            written.put(report, folder.isPresent() && Files.isRegularFile(folder.get().resolve(file)));
        }
        return written;
    }
}
