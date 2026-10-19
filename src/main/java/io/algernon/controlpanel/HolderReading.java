package io.algernon.controlpanel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * What a lock file says of its holder (ADR-278 §4 as amended by ADR-285 §5), read without taking any lock: absent,
 * empty or blank means held by nobody; a non-blank first line that does not parse means held, shown whole; a line that
 * parses is held as {@link HolderLine#held()} decides.
 */
public final class HolderReading {

    private final Optional<String> text;
    private final Optional<HolderLine> line;
    private final boolean held;

    private HolderReading(Optional<String> text, Optional<HolderLine> line, boolean held) {
        this.text = text;
        this.line = line;
        this.held = held;
    }

    /** Reads the first line of the lock file; an absent file is read as empty. */
    public static HolderReading read(Path lockFile) throws IOException {
        String first;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(lockFile), StandardCharsets.UTF_8))) {
            first = reader.readLine();
        } catch (NoSuchFileException e) {
            first = null;
        }
        if (first == null || first.isBlank()) {
            return new HolderReading(Optional.empty(), Optional.empty(), false);
        }
        Optional<HolderLine> parsed = HolderLine.parse(first);
        return new HolderReading(Optional.of(first), parsed, parsed.map(HolderLine::held).orElse(true));
    }

    public boolean held() {
        return held;
    }

    public Optional<String> text() {
        return text;
    }

    public Optional<HolderLine> line() {
        return line;
    }
}
