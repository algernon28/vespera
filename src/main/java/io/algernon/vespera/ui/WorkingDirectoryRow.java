package io.algernon.vespera.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A row of the window's list of working directories (ADR-393 §5): the folder's name, its corpus root, its last
 * invocation's date and outcome, the furthest leg its last {@code vespera run} reached and the next value, read from
 * the folder's {@code report.json} and the copies in its {@code logs}, without opening its database or taking its lock.
 *
 * @param name the folder's last element
 * @param corpusRoot the last run's report's {@code places.corpusRoot}, or {@code no run yet}
 * @param lastInvocation {@code <command> on <yyyy-MM-dd>, <outcome>}, or what says there is none, or who holds the folder
 * @param legReached {@code <name>, <state>} of the last leg whose state is not {@code not started}, or {@code none}
 * @param next the keys wanted, {@code answers to record}, {@code run} or {@code nothing}
 */
public record WorkingDirectoryRow(String name, String corpusRoot, String lastInvocation, String legReached,
        String next) {

    private static final String NO_RUN = "no run yet";
    private static final String NONE = "none";
    private static final String NOTHING = "nothing";
    private static final String NOT_STARTED = "not started";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Pattern COPY = Pattern.compile("^report-([0-9]{8}T[0-9]{6}Z)(?:-([0-9]+))?\\.json$");

    /** The row of {@code folder}; never throws. */
    public static WorkingDirectoryRow read(Path folder, ZoneId zone) {
        String name = folder.getFileName() == null ? folder.toString() : folder.getFileName().toString();
        try {
            if (!Files.isDirectory(folder)) {
                return new WorkingDirectoryRow(name, NO_RUN, "not there", NONE, NOTHING);
            }
            Optional<InvocationReportFile.Report> lastRun = lastRun(folder);
            String corpusRoot = lastRun.map(InvocationReportFile.Report::places)
                    .map(InvocationReportFile.Places::corpusRoot).orElse(NO_RUN);
            String leg = lastRun.map(WorkingDirectoryRow::furthestLeg).orElse(NONE);
            String last;
            String next = NOTHING;
            Optional<String> heldSince = heldSince(folder, zone);
            Path file = folder.resolve(InvocationReportFile.FILE_NAME);
            if (!Files.isRegularFile(file)) {
                last = "nothing yet";
            } else {
                try {
                    InvocationReportFile.Report report = InvocationReportFile
                            .read(Files.readString(file, StandardCharsets.UTF_8));
                    last = lastInvocationOf(report, zone);
                    next = nextOf(report);
                } catch (IOException | RuntimeException cannotBeRead) {
                    last = "its report cannot be read";
                }
            }
            return new WorkingDirectoryRow(name, corpusRoot, heldSince.orElse(last), leg, next);
        } catch (RuntimeException unexpected) {
            return new WorkingDirectoryRow(name, NO_RUN, "its report cannot be read", NONE, NOTHING);
        }
    }

    private static String lastInvocationOf(InvocationReportFile.Report report, ZoneId zone) {
        InvocationReportFile.Invocation invocation = report.invocation();
        if (invocation == null || invocation.started() == null || invocation.command() == null) {
            return "its report cannot be read";
        }
        return invocation.command() + " on " + DAY.format(invocation.started().atZone(zone)) + ", "
                + (invocation.outcome() == null ? "not ended" : invocation.outcome());
    }

    private static String furthestLeg(InvocationReportFile.Report report) {
        String reached = NONE;
        for (InvocationReportFile.LegState leg : report.legs()) {
            if (!NOT_STARTED.equals(leg.state())) {
                reached = leg.name() + ", " + leg.state();
            }
        }
        return reached;
    }

    private static String nextOf(InvocationReportFile.Report report) {
        Map<String, Object> next = report.next();
        if (next == null) {
            return NOTHING;
        }
        if (next.get("wants") instanceof List<?> wants && !wants.isEmpty()) {
            List<String> keys = new ArrayList<>();
            wants.forEach(key -> keys.add(String.valueOf(key)));
            return String.join(", ", keys);
        }
        if (Boolean.TRUE.equals(next.get("answer"))) {
            return "answers to record";
        }
        return "run".equals(next.get("invocation")) ? "run" : NOTHING;
    }

    /** {@code in use by another process since <yyyy-MM-dd HH:mm>} where a process other than this holds the folder. */
    private static Optional<String> heldSince(Path folder, ZoneId zone) {
        try {
            HolderReading reading = HolderReading.read(folder.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME));
            boolean own = reading.line().map(line -> line.pid() == ProcessHandle.current().pid()).orElse(false);
            if (!reading.held() || own) {
                return Optional.empty();
            }
            return Optional.of("in use by another process" + reading.line()
                    .map(line -> " since " + MINUTE.format(line.started().atZone(zone))).orElse(""));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * The newest copy {@code logs/report-<id>.json} whose command is {@code run}: ids ordered by their sixteen
     * characters, then by the number after the {@code -}; a copy that cannot be read or is of a format not known is
     * passed over (ADR-393 §7).
     */
    private static Optional<InvocationReportFile.Report> lastRun(Path folder) {
        record Copy(Path file, String stamp, long number) {
        }
        List<Copy> copies = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder.resolve("logs"), "report-*.json")) {
            for (Path file : files) {
                Matcher id = COPY.matcher(file.getFileName().toString());
                if (id.matches()) {
                    long number;
                    try {
                        number = id.group(2) == null ? 0 : Long.parseLong(id.group(2));
                    } catch (NumberFormatException tooLong) {
                        number = Long.MAX_VALUE;
                    }
                    copies.add(new Copy(file, id.group(1), number));
                }
            }
        } catch (IOException | RuntimeException noLogs) {
            return Optional.empty();
        }
        copies.sort(Comparator.comparing(Copy::stamp).thenComparingLong(Copy::number).reversed());
        for (Copy copy : copies) {
            try {
                InvocationReportFile.Report report = InvocationReportFile
                        .read(Files.readString(copy.file(), StandardCharsets.UTF_8));
                if (report.invocation() != null && "run".equals(report.invocation().command())) {
                    return Optional.of(report);
                }
            } catch (IOException | RuntimeException passedOver) {
                // an unreadable copy is passed over
            }
        }
        return Optional.empty();
    }
}
