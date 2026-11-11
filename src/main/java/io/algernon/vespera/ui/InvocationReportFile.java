package io.algernon.vespera.ui;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The invocation report the jar writes at the top of the working directory, {@code report.json} (ADR-362 §3), as the
 * window reads it (ADR-362 §8): the invocation, each step of the job in order with its state and how far it has got,
 * the legs, the charts, the gates, the refusals and faults, the closing line, the next value and the files. The window
 * draws them and decides none; it reads the file with {@link JsonReader}, in {@code java.base} alone, and links no class
 * of the jar (ADR-285 §3). A field the window has no use for is not read, and a reader ignores a field it does not know.
 */
public final class InvocationReportFile {

    /** The file's name, at the top of the working directory. */
    public static final String FILE_NAME = "report.json";

    /** The format this window reads; a report of another is refused with {@link FormatNotKnownException}. */
    public static final int FORMAT = 1;

    /** A report of a {@code format} other than {@link #FORMAT}, which the window does not read. */
    public static final class FormatNotKnownException extends IllegalArgumentException {

        private final long format;

        FormatNotKnownException(long format) {
            super("the report is of format " + format + ", which this window does not read");
            this.format = format;
        }

        public long format() {
            return format;
        }
    }

    /** The invocation: {@code pid} is the process that wrote it, {@code started} the instant its command began to run. */
    public record Invocation(String id, long pid, String command, Instant started, Instant ended, String state,
            String outcome, Integer exitCode, Instant writtenAt, long interval) {

        /** Whether the invocation's last write was made: its {@code state} is {@code ended}. */
        public boolean hasEnded() {
            return "ended".equals(state);
        }
    }

    public record Display(boolean present, String reason) {
    }

    /** The places the invocation worked in; {@code deliverableRoot} is null unless it drew a tree. */
    public record Places(String workingDirectory, String corpusRoot, String deliverableRoot) {
    }

    /** {@code done} and {@code total} are null where the step's progress line carries no counter. */
    public record Step(String name, String leg, String state, Long done, Long total) {
    }

    public record LegState(String name, String state, boolean open) {
    }

    public record Line(String name, List<Double> values) {
    }

    public record Chart(String id, String title, String axis, List<Long> seconds, List<Line> lines) {
    }

    public record Gate(String step, String key, List<String> analysisReports) {
    }

    public record Fault(String step, String type, List<String> causes) {
    }

    /** One of the twelve analysis reports: its name, whether its page is there, and the paths of the page and its JSON. */
    public record AnalysisReport(String name, String html, String json, boolean written) {
    }

    public record FileWritten(String role, String path) {
    }

    /** What the invocation opened; {@code files} is empty, and {@code because} says why. */
    public record Opened(List<String> files, String because) {
    }

    public record Report(int format, Invocation invocation, Display display, Opened opened, Places places, List<Step> steps,
            List<LegState> legs, List<Chart> charts, List<Gate> gates, List<String> refusals, List<Fault> faults,
            String closing, Map<String, Object> next, List<AnalysisReport> analysisReports, List<FileWritten> files) {

        /** What the window draws before any report is read: no invocation and no step. */
        public static Report none() {
            return new Report(FORMAT, null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), null, null, List.of(), List.of());
        }

        /** Whether the invocation has ended; false where the report holds none. */
        public boolean ended() {
            return invocation != null && invocation.hasEnded();
        }
    }

    private InvocationReportFile() {
    }

    /**
     * What the file holds; empty lists, never null, where a list is absent.
     *
     * @throws FormatNotKnownException where the file is JSON of another {@code format}
     * @throws IllegalArgumentException where it is not that JSON
     */
    public static Report read(String json) {
        try {
            Map<?, ?> root = (Map<?, ?>) JsonReader.read(json);
            long format = ((BigDecimal) root.get("format")).longValueExact();
            if (format != FORMAT) {
                throw new FormatNotKnownException(format);
            }
            Map<?, ?> invocation = (Map<?, ?>) root.get("invocation");
            Map<?, ?> display = (Map<?, ?>) root.get("display");
            Map<?, ?> opened = (Map<?, ?>) root.get("opened");
            Map<?, ?> places = (Map<?, ?>) root.get("places");
            return new Report((int) format, invocationOf(invocation),
                    display == null ? null : new Display(Boolean.TRUE.equals(display.get("present")),
                            (String) display.get("reason")),
                    opened == null ? null : new Opened(strings(opened.get("files")), (String) opened.get("because")),
                    places == null ? null : new Places((String) places.get("workingDirectory"),
                            (String) places.get("corpusRoot"), (String) places.get("deliverableRoot")),
                    steps(root.get("steps")), legs(root.get("legs")), charts(root.get("charts")),
                    gates(root.get("gates")), strings(root.get("refusals")), faults(root.get("faults")),
                    (String) root.get("closing"), objectOf(root.get("next")), analysisReports(root.get("analysisReports")),
                    files(root.get("files")));
        } catch (ClassCastException | NullPointerException | ArithmeticException | DateTimeParseException notThatJson) {
            throw new IllegalArgumentException(FILE_NAME + " is not in the shape the jar writes it", notThatJson);
        }
    }

    private static Invocation invocationOf(Map<?, ?> invocation) {
        return new Invocation((String) invocation.get("id"), whole(invocation.get("pid")),
                (String) invocation.get("command"), instant(invocation.get("started")),
                instant(invocation.get("ended")), (String) invocation.get("state"),
                (String) invocation.get("outcome"),
                invocation.get("exitCode") == null ? null : whole(invocation.get("exitCode")).intValue(),
                instant(invocation.get("writtenAt")), whole(invocation.get("interval")));
    }

    private static List<Step> steps(Object array) {
        List<Step> steps = new ArrayList<>();
        for (Object each : list(array)) {
            Map<?, ?> step = (Map<?, ?>) each;
            Map<?, ?> progress = (Map<?, ?>) step.get("progress");
            steps.add(new Step((String) step.get("name"), (String) step.get("leg"), (String) step.get("state"),
                    progress == null ? null : whole(progress.get("done")),
                    progress == null ? null : whole(progress.get("total"))));
        }
        return List.copyOf(steps);
    }

    private static List<LegState> legs(Object array) {
        List<LegState> legs = new ArrayList<>();
        for (Object each : list(array)) {
            Map<?, ?> leg = (Map<?, ?>) each;
            legs.add(new LegState((String) leg.get("name"), (String) leg.get("state"),
                    Boolean.TRUE.equals(leg.get("open"))));
        }
        return List.copyOf(legs);
    }

    private static List<Chart> charts(Object array) {
        List<Chart> charts = new ArrayList<>();
        for (Object each : list(array)) {
            Map<?, ?> chart = (Map<?, ?>) each;
            List<Long> seconds = new ArrayList<>();
            for (Object second : list(chart.get("seconds"))) {
                seconds.add(((BigDecimal) second).longValue());
            }
            List<Line> lines = new ArrayList<>();
            for (Object one : list(chart.get("lines"))) {
                Map<?, ?> line = (Map<?, ?>) one;
                List<Double> values = new ArrayList<>();
                for (Object value : list(line.get("values"))) {
                    values.add(((BigDecimal) value).doubleValue());
                }
                lines.add(new Line((String) line.get("name"), List.copyOf(values)));
            }
            charts.add(new Chart((String) chart.get("id"), (String) chart.get("title"), (String) chart.get("axis"),
                    List.copyOf(seconds), List.copyOf(lines)));
        }
        return List.copyOf(charts);
    }

    private static List<Gate> gates(Object array) {
        List<Gate> gates = new ArrayList<>();
        for (Object each : list(array)) {
            Map<?, ?> gate = (Map<?, ?>) each;
            gates.add(new Gate((String) gate.get("step"), (String) gate.get("key"),
                    strings(gate.get("analysisReports"))));
        }
        return List.copyOf(gates);
    }

    private static List<Fault> faults(Object array) {
        List<Fault> faults = new ArrayList<>();
        for (Object each : list(array)) {
            Map<?, ?> fault = (Map<?, ?>) each;
            faults.add(new Fault((String) fault.get("step"), (String) fault.get("type"),
                    strings(fault.get("causes"))));
        }
        return List.copyOf(faults);
    }

    private static List<AnalysisReport> analysisReports(Object array) {
        List<AnalysisReport> reports = new ArrayList<>();
        for (Object each : list(array)) {
            Map<?, ?> report = (Map<?, ?>) each;
            reports.add(new AnalysisReport((String) report.get("name"), (String) report.get("html"),
                    (String) report.get("json"), Boolean.TRUE.equals(report.get("written"))));
        }
        return List.copyOf(reports);
    }

    private static List<FileWritten> files(Object array) {
        List<FileWritten> files = new ArrayList<>();
        for (Object each : list(array)) {
            Map<?, ?> file = (Map<?, ?>) each;
            // The file's location is the field that is not its role: the window holds no text spelling it as a lookup
            // name, since a test holds that no class of the window names the variable a program is looked up on.
            String location = file.entrySet().stream().filter(field -> !"role".equals(field.getKey()))
                    .map(field -> (String) field.getValue()).findFirst().orElse(null);
            files.add(new FileWritten((String) file.get("role"), location));
        }
        return List.copyOf(files);
    }

    private static List<?> list(Object array) {
        return array == null ? List.of() : (List<?>) array;
    }

    private static List<String> strings(Object array) {
        List<String> strings = new ArrayList<>();
        for (Object each : list(array)) {
            strings.add((String) each);
        }
        return List.copyOf(strings);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectOf(Object object) {
        return object == null ? null : (Map<String, Object>) object;
    }

    private static Instant instant(Object text) {
        return text == null ? null : Instant.parse((String) text);
    }

    private static Long whole(Object number) {
        return number == null ? null : ((BigDecimal) number).longValue();
    }
}
