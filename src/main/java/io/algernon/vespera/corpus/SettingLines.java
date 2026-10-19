package io.algernon.vespera.corpus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * How much of a text file is settings, counted from its bytes alone (ADR-287 section 3.4): two numbers and never a
 * ratio (ADR-073), the lines counted and the setting lines among them.
 *
 * <p>The windows, the decoding and the cut lines are {@link TimestampedLines}', and a line continued with a
 * backslash is joined to the next as {@code java.util.Properties.load} joins it, never across the stretch between
 * two windows. The shapes below are code here, and a change to them is a new {@link #VERSION}, which stage 1
 * records among its configuration consumed.
 */
public final class SettingLines {

    /** The version of how a line is read (ADR-287 section 10), recorded in stage 1's configuration consumed. */
    static final String VERSION = "v1";

    /** Fewer counted lines than this is not enough to call a share a share. */
    public static final int MINIMUM_COUNTED_LINES = TimestampedLines.MINIMUM_NON_BLANK_LINES;

    private static final Pattern SECTION_HEADING = Pattern.compile("\\[{1,2}[^\\[\\]]+\\]{1,2}\\s*");

    private static final Pattern KEYED_VALUE =
            Pattern.compile("(?:[A-Za-z0-9_][A-Za-z0-9_.\\-]*|\"[^\"]+\")\\s*(?:=.*|:(?:\\s.*)?)", Pattern.DOTALL);

    private SettingLines() {}

    /**
     * What was counted.
     *
     * @param settings the counted lines shaped as a setting
     * @param counted the lines read that are neither blank nor a comment, a continued line being one
     */
    public record Count(int settings, int counted) {

        /** Whether at least ten lines were counted and the share of settings reaches {@code floor}. */
        public boolean isConfiguration(double floor) {
            return counted >= MINIMUM_COUNTED_LINES && (double) settings / counted >= floor;
        }

        /** The share of settings in whole percent, rounded down. */
        public int wholePercent() {
            return counted == 0 ? 0 : (int) ((long) settings * 100 / counted);
        }
    }

    /** Counts {@code file}; the caller decides what a file that cannot be read means. */
    public static Count count(Path file) throws IOException {
        int counted = 0;
        int settings = 0;
        for (List<String> window : TimestampedLines.windowsOf(file)) {
            for (String line : joined(window)) {
                counted++;
                if (isSetting(line)) {
                    settings++;
                }
            }
        }
        return new Count(settings, counted);
    }

    /** The counted lines of one window: blank lines and comments dropped, a line ending in an odd run of backslashes joined. */
    private static List<String> joined(List<String> window) {
        List<String> counted = new ArrayList<>();
        for (int index = 0; index < window.size(); index++) {
            String line = window.get(index);
            if (line.isBlank() || isComment(line)) {
                continue;
            }
            StringBuilder whole = new StringBuilder(line);
            while (endsWithAnOddRun(whole) && index + 1 < window.size()) {
                whole.setLength(whole.length() - 1);
                whole.append(window.get(++index).stripLeading());
            }
            counted.add(whole.toString());
        }
        return counted;
    }

    private static boolean isComment(String line) {
        String text = line.stripLeading();
        return text.startsWith("#") || text.startsWith(";") || text.startsWith("!") || text.startsWith("//");
    }

    private static boolean endsWithAnOddRun(CharSequence line) {
        int backslashes = 0;
        for (int at = line.length() - 1; at >= 0 && line.charAt(at) == '\\'; at--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static boolean isSetting(String line) {
        String text = line.stripLeading();
        return SECTION_HEADING.matcher(text).matches() || KEYED_VALUE.matcher(text).matches();
    }
}
