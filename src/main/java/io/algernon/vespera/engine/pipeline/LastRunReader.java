package io.algernon.vespera.engine.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the copy in {@code logs} of the working directory's last {@code vespera run}'s report, and the generation run
 * it {@code arrivedAt} (ADR-393 §7). The last is the copy {@code logs/report-<id>.json} of the greatest id whose
 * {@code invocation.command} is {@code run}: ids are ordered by their sixteen characters, then by the number after the
 * {@code -}, an id with none being the first of its second. A copy that cannot be read, is not JSON or is of a
 * {@code format} not known is passed over. Reads {@code format}, {@code invocation.command} and {@code arrivedAt}, and
 * ignores the rest. It holds no rule of any stage: it reads a file the jar wrote, and the publish's choice is the
 * command's (ADR-393 §8).
 */
final class LastRunReader {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String PREFIX = "report-";

    private static final String SUFFIX = ".json";

    private static final Pattern ID = Pattern.compile("^([0-9]{8}T[0-9]{6}Z)(?:-([0-9]+))?$");

    /**
     * What the last {@code vespera run} arrived at.
     *
     * @param generationRun the run id {@code arrivedAt} names under the stage {@code generation}, or null where it
     *         names none
     */
    record Arrival(String generationRun) {}

    private record Copy(Path file, String stamp, long number) {}

    private LastRunReader() {}

    /** The last run's arrival, or empty where no readable copy of a {@code vespera run}'s report is kept. */
    static Optional<Arrival> of(Path workingDirectory, String generationStage) {
        Path logs = workingDirectory.resolve("logs");
        if (!Files.isDirectory(logs)) {
            return Optional.empty();
        }
        List<Copy> copies = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(logs, PREFIX + "*" + SUFFIX)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                Matcher id = ID.matcher(name.substring(PREFIX.length(), name.length() - SUFFIX.length()));
                if (id.matches()) {
                    copies.add(new Copy(file, id.group(1), id.group(2) == null ? 0 : parsed(id.group(2))));
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
        copies.sort(Comparator.comparing(Copy::stamp).thenComparingLong(Copy::number).reversed());
        for (Copy copy : copies) {
            Optional<Arrival> arrival = arrivalOf(copy.file(), generationStage);
            if (arrival.isPresent()) {
                return arrival;
            }
        }
        return Optional.empty();
    }

    private static long parsed(String number) {
        try {
            return Long.parseLong(number);
        } catch (NumberFormatException tooLong) {
            return Long.MAX_VALUE;
        }
    }

    /** The arrival the copy holds where it is readable, of the known format and a {@code run}'s; else empty. */
    private static Optional<Arrival> arrivalOf(Path file, String generationStage) {
        try {
            JsonNode report = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
            if (!report.path("format").isIntegralNumber() || report.path("format").asLong() != InvocationReport.FORMAT
                    || !"run".equals(report.path("invocation").path("command").asString(""))) {
                return Optional.empty();
            }
            String generation = null;
            for (JsonNode reached : report.path("arrivedAt")) {
                if (generationStage.equals(reached.path("stage").asString(""))) {
                    generation = reached.path("run").asString(null);
                }
            }
            return Optional.of(new Arrival(generation));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }
}
