package io.algernon.controlpanel;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A holder line as the jar writes it, {@code pid=<n> started=<instant> ...} (ADR-285 §5, held against the jar's writers
 * by a contract test), and the rule that says whether it is held (ADR-278 §4): its pid is alive and that process started
 * no later than {@code started} plus one second; a live process giving no start instant counts as held.
 */
public record HolderLine(long pid, Instant started, String text) {

    private static final Pattern FORM = Pattern.compile("pid=(\\d+) started=(\\S+)(?: .*)?", Pattern.DOTALL);

    private static final Duration SLACK = Duration.ofSeconds(1);

    /** The line read into its parts, empty when it does not have the form. */
    public static Optional<HolderLine> parse(String line) {
        Matcher matcher = FORM.matcher(line);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new HolderLine(
                    Long.parseLong(matcher.group(1)), OffsetDateTime.parse(matcher.group(2)).toInstant(), line));
        } catch (NumberFormatException | DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** Whether the line is held while its pid is the given process. */
    public boolean heldWhile(HolderProcess process) {
        return process.alive()
                && process.startInstant().map(start -> !start.isAfter(started.plus(SLACK))).orElse(true);
    }

    /** Whether the line is held by the real process of its pid; a process that is gone or has ended holds nothing. */
    public boolean held() {
        return ProcessHandle.of(pid)
                .map(handle -> heldWhile(new HolderProcess(handle.isAlive(), handle.info().startInstant())))
                .orElse(false);
    }
}
