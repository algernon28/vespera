package io.algernon.vespera.ui;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A step's chart as the jar keeps it beside {@code report.json}, {@code chart-step-<step>.json} (ADR-432 §4), as the
 * window reads it: the step's lines, each with its own seconds and values, whether the chart has been started (its key is
 * given), the instant the step finished once its chart is frozen (ADR-432 §6) and whether it finished in an earlier
 * invocation (ADR-432 §5). The window draws what the file holds and decides none of it: the series, the freezing and the
 * key are the jar's. It reads with {@link JsonReader}, in {@code java.base} alone, and links no class of the jar but
 * {@code DockerEngine} (ADR-390 §4; ADR-285 §3). A stage's file an earlier version wrote is read by nothing.
 */
final class StepChartFile {

    /** The format this window reads; a file of another is drawn as no file (ADR-432 §4). */
    static final int FORMAT = 1;

    /** One line of a chart: its name, and its points' seconds and values, oldest first. */
    record Line(String name, List<Double> seconds, List<Double> values) {
    }

    /**
     * What a step's file holds: {@code started} is whether its key is given, {@code finishedAt} is null until the
     * step's chart is frozen in this invocation, {@code finishedEarlier} whether the step finished in an earlier one.
     */
    record Chart(String step, boolean started, Instant finishedAt, boolean finishedEarlier, List<Line> lines) {
    }

    private StepChartFile() {
    }

    /** The name of the file the jar keeps step {@code step}'s chart in, at the top of the working directory. */
    static String nameOf(String step) {
        return "chart-step-" + step + ".json";
    }

    /**
     * What {@code json} holds, of the step {@code step}; empty where it is not that JSON, is of another {@code format},
     * or is not that step's chart (ADR-432 §4): such a file is drawn as no file, and nothing is said.
     */
    static Optional<Chart> read(String step, String json) {
        try {
            if (!(JsonReader.read(json) instanceof Map<?, ?> root)
                    || !(root.get("format") instanceof BigDecimal format)
                    || format.compareTo(BigDecimal.valueOf(FORMAT)) != 0
                    || !(root.get("step") instanceof String named)
                    || !named.equals(step)) {
                return Optional.empty();
            }
            boolean started = root.get("key") instanceof Map<?, ?> key && !key.isEmpty();
            Instant finishedAt = root.get("finishedAt") instanceof String text ? Instant.parse(text) : null;
            boolean finishedEarlier = Boolean.TRUE.equals(root.get("finishedEarlier"));
            List<Line> lines = new ArrayList<>();
            if (root.get("lines") instanceof List<?> listed) {
                for (Object each : listed) {
                    Map<?, ?> line = (Map<?, ?>) each;
                    lines.add(new Line((String) line.get("name"), numbers(line.get("seconds")),
                            numbers(line.get("values"))));
                }
            }
            return Optional.of(new Chart(named, started, finishedAt, finishedEarlier, List.copyOf(lines)));
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
