package io.algernon.vespera.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The two folders the cockpit's fields name as they name them now, and what the working directory holds (ADR-344 §3).
 * Each is read when asked, so a part drawn before the fields are filled still reads what they say later.
 */
final class WorkingFolders {

    private final Supplier<Optional<Path>> workingDirectory;
    private final Supplier<Optional<Path>> corpusRoot;

    WorkingFolders(Supplier<Optional<Path>> workingDirectory, Supplier<Optional<Path>> corpusRoot) {
        this.workingDirectory = workingDirectory;
        this.corpusRoot = corpusRoot;
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

    /** Which of the reports the working directory holds now. */
    Map<String, Boolean> writtenReports() {
        Map<String, Boolean> written = new HashMap<>();
        Optional<Path> folder = workingDirectory();
        for (String report : Reports.ALL) {
            written.put(report, folder.isPresent() && Files.isRegularFile(folder.get().resolve(report)));
        }
        return written;
    }
}
