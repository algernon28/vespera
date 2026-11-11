package io.algernon.vespera.engine.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads {@code report.json} and its copy as the tests of the invocation report look at them (ADR-362 section 3). */
final class TheReportFile {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final long LOOK_EVERY_MILLIS = 20L;

    private TheReportFile() {}

    /** The text of {@code report.json} in {@code workingDirectory}, or null where there is none or it is being replaced. */
    static String textIn(Path workingDirectory) {
        try {
            Path file = workingDirectory.resolve(InvocationReport.FILE_NAME);
            return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
        } catch (IOException beingReplaced) {
            return null;
        }
    }

    static JsonNode parse(String text) {
        return MAPPER.readTree(text);
    }

    /** {@code report.json} parsed; fails where there is none. */
    static JsonNode in(Path workingDirectory) {
        String text = textIn(workingDirectory);
        if (text == null) {
            throw new AssertionError("the invocation left no " + InvocationReport.FILE_NAME + " in " + workingDirectory);
        }
        return parse(text);
    }

    /** The files of the {@code logs} folder that are copies of a report, oldest name first. */
    static List<Path> copiesIn(Path workingDirectory) throws IOException {
        Path logs = workingDirectory.resolve("logs");
        if (!Files.isDirectory(logs)) {
            return List.of();
        }
        try (var inside = Files.list(logs)) {
            return new ArrayList<>(inside
                    .filter(file -> file.getFileName().toString().startsWith("report-")
                            && file.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList());
        }
    }

    /** The copy of the report {@code report} is, named by its invocation id. */
    static Path copyOf(Path workingDirectory, JsonNode report) {
        return workingDirectory.resolve("logs").resolve("report-" + report.get("invocation").get("id").asString() + ".json");
    }

    /** The first reading of the report that satisfies {@code wanted} and equals its copy, or null after ten seconds. */
    static JsonNode awaitWithItsCopy(Path workingDirectory, Predicate<JsonNode> wanted) {
        long until = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < until) {
            String text = textIn(workingDirectory);
            if (text != null) {
                try {
                    JsonNode report = parse(text);
                    Path copy = copyOf(workingDirectory, report);
                    if (wanted.test(report) && Files.isRegularFile(copy)
                            && text.equals(Files.readString(copy, StandardCharsets.UTF_8))) {
                        return report;
                    }
                } catch (IOException | RuntimeException beingReplaced) {
                    // the report is moved over in one move; a read that meets the move reads again
                }
            }
            try {
                Thread.sleep(LOOK_EVERY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /** Steps of the report by name. */
    static JsonNode step(JsonNode report, String name) {
        for (JsonNode step : report.get("steps")) {
            if (name.equals(step.get("name").asString())) {
                return step;
            }
        }
        throw new AssertionError("no step " + name + " in the report");
    }
}
