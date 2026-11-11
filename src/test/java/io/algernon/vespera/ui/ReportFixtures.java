package io.algernon.vespera.ui;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * The {@code report.json} the window tests put in a working directory (ADR-362 section 8): a report of the invocation
 * the window watches, stamped with the child's pid and the instant it is written, since the window draws a report only
 * where it is the watched invocation's. The window tests describe a job as the steps and charts it holds, in the shape
 * {@code {"ended": false, "steps": [{"name", "state", "done", "total"}], "charts": [...]}}, with a {@code "closing"} line and
 * {@code "gates"}, {@code "faults"} and {@code "refusals"} where a test draws them, which this turns into the
 * report the jar writes of it, so that each test names the steps and charts it draws and nothing else of the report.
 */
final class ReportFixtures {

    private ReportFixtures() {
    }

    /** Writes the report of {@code job} as the one {@code pid} wrote just now, in {@code workingDirectory}. */
    static void write(Path workingDirectory, long pid, String job) throws IOException {
        write(workingDirectory, pid, job, Instant.now().truncatedTo(ChronoUnit.MILLIS), Instant.now(), 1);
    }

    /** As {@link #write(Path, long, String)}, for a report that says it started at {@code started} and was written at {@code writtenAt}, every {@code interval} seconds. */
    static void write(Path workingDirectory, long pid, String job, Instant started, Instant writtenAt, long interval)
            throws IOException {
        Files.writeString(workingDirectory.resolve(InvocationReportFile.FILE_NAME),
                reportOf(job, pid, started, writtenAt, interval), StandardCharsets.UTF_8);
    }

    static String reportOf(String job, long pid, Instant started, Instant writtenAt, long interval) {
        Map<?, ?> shape = (Map<?, ?>) JsonReader.read(job);
        boolean ended = Boolean.TRUE.equals(shape.get("ended"));
        StringBuilder steps = new StringBuilder("[");
        boolean first = true;
        for (Object each : (List<?>) shape.get("steps")) {
            Map<?, ?> step = (Map<?, ?>) each;
            steps.append(first ? "" : ",").append("{\"name\":").append(json(step.get("name"))).append(",\"state\":")
                    .append(json(step.get("state"))).append(",\"progress\":")
                    .append(step.get("done") == null ? "null"
                            : "{\"line\":\"\",\"done\":" + json(step.get("done")) + ",\"total\":"
                                    + json(step.get("total")) + "}")
                    .append('}');
            first = false;
        }
        steps.append(']');
        return "{\"format\":1,\"invocation\":{\"id\":\"20261108T000000Z\",\"pid\":" + pid
                + ",\"command\":\"run\",\"started\":\"" + started + "\",\"ended\":null,\"state\":\""
                + (ended ? "ended" : "running") + "\",\"outcome\":null,\"exitCode\":null,\"writtenAt\":\"" + writtenAt
                + "\",\"interval\":" + interval + "},\"steps\":" + steps + ",\"legs\":[],\"charts\":"
                + json(shape.get("charts")) + ",\"gates\":" + json(orEmpty(shape.get("gates")))
                + ",\"refusals\":" + json(orEmpty(shape.get("refusals"))) + ",\"faults\":"
                + json(orEmpty(shape.get("faults"))) + ",\"closing\":" + json(shape.get("closing")) + "}";
    }

    private static Object orEmpty(Object list) {
        return list == null ? List.of() : list;
    }

    private static String json(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        if (value instanceof BigDecimal number) {
            return number.toPlainString();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(ReportFixtures::json).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.append(first ? "" : ",").append(json(entry.getKey())).append(':').append(json(entry.getValue()));
                first = false;
            }
            return out.append('}').toString();
        }
        return String.valueOf(value);
    }
}
