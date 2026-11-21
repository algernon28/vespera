package io.algernon.vespera.ui;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A holder's first line said in words, {@code <who> (pid <pid>) since <time>} (ADR-440 §3): who is the first word of the
 * line's {@code command=} field that does not begin with {@code -}, and the time is the line's start at the zone of the
 * moment given, as {@code HH:mm} where its date there is that moment's date and with its date where it is another.
 */
final class HolderWords {

    /** What is said of a line that is not of the jar's form. */
    static final String UNREADABLE = "a process whose line cannot be read";

    private static final Pattern COMMAND = Pattern.compile(" command=(.*)", Pattern.DOTALL);

    private static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("HH:mm");

    private static final DateTimeFormatter ANOTHER_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private HolderWords() {
    }

    /** The words for {@code line}; {@code now} gives the time zone and the date the time is read at. */
    static String of(String line, ZonedDateTime now) {
        Optional<HolderLine> parsed = HolderLine.parse(line);
        if (parsed.isEmpty()) {
            return UNREADABLE;
        }
        ZoneId zone = now.getZone();
        ZonedDateTime started = parsed.get().started().atZone(zone);
        String time = started.toLocalDate().equals(now.toLocalDate()) ? TODAY.format(started)
                : ANOTHER_DAY.format(started);
        return who(line) + " (pid " + parsed.get().pid() + ") since " + time;
    }

    private static String who(String line) {
        Matcher matcher = COMMAND.matcher(line);
        if (!matcher.find()) {
            return "another Vespera";
        }
        for (String word : matcher.group(1).split(" ")) {
            if (word.isEmpty() || word.startsWith("-")) {
                continue;
            }
            return switch (word) {
                case "run", "label", "publish" -> "vespera " + word;
                case "control-panel" -> "another control panel";
                default -> "another Vespera";
            };
        }
        return "another Vespera";
    }
}
