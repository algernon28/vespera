package io.algernon.vespera.ui;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A stage's chart as the jar keeps it beside {@code report.json}, {@code chart-stage-<stage>.json} (ADR-375 §4), as the
 * window reads it: the stage's lines, each with its own seconds and values, and the instant the stage finished once its
 * chart is frozen (ADR-375 §6). The window draws what the file holds and decides none of it: the series, the freezing
 * and the key are the jar's. It reads with {@link JsonReader}, in {@code java.base} alone, and links no class of the jar
 * (ADR-285 §3). A field the window has no use for, the key among them, is not read.
 */
final class StageChartFile {

    /** The format this window reads; a file of another is drawn as no file (ADR-375 §4). */
    static final int FORMAT = 1;

    /** One line of a chart: its name, and its points' seconds and values, oldest first. */
    record Line(String name, List<Double> seconds, List<Double> values) {
    }

    /** What a stage's file holds: {@code finishedAt} is null until the stage's chart is frozen. */
    record Chart(String stage, Instant finishedAt, List<Line> lines) {
    }

    private StageChartFile() {
    }

    /** The name of the file the jar keeps stage {@code stage}'s chart in, at the top of the working directory. */
    static String nameOf(String stage) {
        return "chart-stage-" + stage + ".json";
    }

    /**
     * What {@code json} holds; empty where it is not that JSON, is of another {@code format}, or is not a stage's chart
     * (ADR-375 §4): such a file is drawn as no file, and nothing is said.
     */
    static Optional<Chart> read(String json) {
        try {
            if (!(JsonReader.read(json) instanceof Map<?, ?> root)
                    || !(root.get("format") instanceof BigDecimal format)
                    || format.compareTo(BigDecimal.valueOf(FORMAT)) != 0
                    || !(root.get("stage") instanceof String stage)) {
                return Optional.empty();
            }
            Instant finishedAt = root.get("finishedAt") instanceof String text ? Instant.parse(text) : null;
            List<Line> lines = new ArrayList<>();
            if (root.get("lines") instanceof List<?> listed) {
                for (Object each : listed) {
                    Map<?, ?> line = (Map<?, ?>) each;
                    lines.add(new Line((String) line.get("name"), numbers(line.get("seconds")),
                            numbers(line.get("values"))));
                }
            }
            return Optional.of(new Chart(stage, finishedAt, List.copyOf(lines)));
        } catch (IllegalArgumentException | ClassCastException | DateTimeParseException | NullPointerException unreadable) {
            return Optional.empty();
        }
    }

    private static List<Double> numbers(Object array) {
        List<Double> numbers = new ArrayList<>();
        for (Object each : (List<?>) array) {
            numbers.add(((BigDecimal) each).doubleValue());
        }
        return List.copyOf(numbers);
    }
}
