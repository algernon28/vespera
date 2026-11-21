package io.algernon.vespera.engine.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the copy {@code logs/report-<id>.json} of the invocation a {@code vespera publish --invocation <id>} names:
 * its {@code format}, {@code invocation.command}, the generation run its {@code arrivedAt} names and
 * {@code places.deliverableRoot}, the rest ignored (ADR-439 §1), beside {@link LastRunInvocationReader}. A copy that
 * cannot be read, is not JSON or is of a {@code format} not known is no report. It holds no rule of any stage: it
 * reads a file the jar wrote, and the publish's refusals are the command's.
 */
final class PublishedInvocation {

    /** The form of an invocation's id (ADR-439 §1). */
    static final Pattern ID = Pattern.compile("^[0-9]{8}T[0-9]{6}Z(-[0-9]+)?$");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * What the copy holds of what a publish reads.
     *
     * @param command the copy's {@code invocation.command} as it holds it, empty where it holds none
     * @param generationRun the run id {@code arrivedAt} names under the stage {@code generation}, or null
     * @param deliverableRoot {@code places.deliverableRoot}, or null where the copy holds none
     */
    record Report(String command, String generationRun, String deliverableRoot) {}

    private PublishedInvocation() {}

    /** The copy of {@code id}, or empty where none is kept or the one kept cannot be read. */
    static Optional<Report> of(Path workingDirectory, String id, String generationStage) {
        Path file = workingDirectory.resolve("logs").resolve("report-" + id + ".json");
        try {
            JsonNode report = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
            if (!report.path("format").isIntegralNumber() || report.path("format").asLong() != InvocationReport.FORMAT) {
                return Optional.empty();
            }
            String generation = null;
            for (JsonNode reached : report.path("arrivedAt")) {
                if (generationStage.equals(reached.path("stage").asString(""))) {
                    generation = reached.path(InvocationReport.RUN).asString(null);
                }
            }
            JsonNode root = report.path("places").path("deliverableRoot");
            return Optional.of(new Report(report.path("invocation").path("command").asString(""), generation,
                    root.isString() ? root.asString() : null));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }
}
