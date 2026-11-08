package io.algernon.vespera.ui;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The control panel's reading of {@code effective-values.properties}, for the contract test in the jar's package:
 * {@link EffectiveValues} is the control panel's own and is not public, and that test needs both it and the jar's
 * package-private writer on one class path (ADR-330 §1).
 */
public final class EffectiveValuesForTests {

    /** The name the control panel reads the file under. */
    public static final String FILE_NAME = EffectiveValues.FILE_NAME;

    /** One value as the control panel read it: what it is and where it came from. */
    public record Said(String value, String source) {
    }

    private EffectiveValuesForTests() {
    }

    /** Every value the control panel reads from the file in {@code workingDirectory}, by key; empty where it reads none. */
    public static Optional<Map<String, Said>> read(Path workingDirectory) {
        return EffectiveValues.read(Optional.of(workingDirectory), workingDirectory.resolve("no-jar-to-compare-with"))
                .map(read -> {
                    Map<String, Said> said = new LinkedHashMap<>();
                    read.values().forEach((key, value) -> said.put(key, new Said(value.value(), value.source())));
                    return said;
                });
    }
}
