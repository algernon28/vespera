package io.algernon.vespera.ui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What the jar writes for the window beside the progress page, {@code progress.json} (ADR-344 §2): whether the
 * invocation has ended, each step of the job in order with its state and how far it has got, and the charts the page
 * draws now with the series the page's own rules picked. The window draws them and decides none; it reads the file
 * with {@link JsonReader}, in {@code java.base} alone.
 */
public final class ProgressFile {

    /** The file's name, in the working directory beside the progress page. */
    public static final String FILE_NAME = "progress.json";

    /** {@code done} and {@code total} are null where the step's progress line carries no counter. */
    public record Step(String name, String state, Long done, Long total) {
    }

    public record Line(String name, List<Double> values) {
    }

    public record Chart(String id, String title, String axis, List<Long> seconds, List<Line> lines) {
    }

    public record Progress(boolean ended, List<Step> steps, List<Chart> charts) {
    }

    private ProgressFile() {
    }

    /** What the file holds; empty steps and charts are lists, never null. */
    public static Progress read(String json) {
        try {
            Map<?, ?> root = (Map<?, ?>) JsonReader.read(json);
            List<Step> steps = new ArrayList<>();
            for (Object each : (List<?>) root.get("steps")) {
                Map<?, ?> step = (Map<?, ?>) each;
                steps.add(new Step((String) step.get("name"), (String) step.get("state"), whole(step.get("done")),
                        whole(step.get("total"))));
            }
            List<Chart> charts = new ArrayList<>();
            for (Object each : (List<?>) root.get("charts")) {
                Map<?, ?> chart = (Map<?, ?>) each;
                List<Long> seconds = new ArrayList<>();
                for (Object second : (List<?>) chart.get("seconds")) {
                    seconds.add(((BigDecimal) second).longValue());
                }
                List<Line> lines = new ArrayList<>();
                for (Object one : (List<?>) chart.get("lines")) {
                    Map<?, ?> line = (Map<?, ?>) one;
                    List<Double> values = new ArrayList<>();
                    for (Object value : (List<?>) line.get("values")) {
                        values.add(((BigDecimal) value).doubleValue());
                    }
                    lines.add(new Line((String) line.get("name"), List.copyOf(values)));
                }
                charts.add(new Chart((String) chart.get("id"), (String) chart.get("title"), (String) chart.get("axis"),
                        List.copyOf(seconds), List.copyOf(lines)));
            }
            return new Progress(Boolean.TRUE.equals(root.get("ended")), List.copyOf(steps), List.copyOf(charts));
        } catch (ClassCastException | NullPointerException notThatJson) {
            throw new IllegalArgumentException(FILE_NAME + " is not in the shape the jar writes it", notThatJson);
        }
    }

    private static Long whole(Object number) {
        return number == null ? null : ((BigDecimal) number).longValue();
    }
}
