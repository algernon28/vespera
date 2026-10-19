package io.algernon.vespera.deliverable;

import io.algernon.vespera.ledger.OccurrenceId;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The name each survivor's copy is given in its cluster's folder (ADR-280 §1, §3): the original's own file name, made
 * safe on every operating system, cut to {@value #LONGEST_NAME} UTF-16 code units, and suffixed with {@code
 * ~<occurrence id>} before its extension only where it collides, without regard to case, with another name in the
 * folder or with a name the folder reserves.
 *
 * <p>Every name of one cluster is worked out together, from the members' occurrence ids and file names and the names
 * of the pictures the cluster's page shows, and no file is read (§2): a name worked out again from the same members is
 * the same name, whatever order the members arrive in.
 *
 * <p><b>Where repeated suffixes leave no room for any of the original's stem</b>, the stem is empty and the suffixes
 * stand alone; the name may then pass {@value #LONGEST_NAME} units, and past {@value #LONGEST_NAME_IN_BYTES} bytes
 * with {@code .part}. A name that with {@code .part} holds more than {@value #LONGEST_NAME_IN_BYTES} UTF-8 bytes is
 * not made (ADR-280 §1.6), and that copy is not-copied. It still ends: while
 * any of the stem is left, an attempt may repeat the one before, but once the stem is empty each attempt is strictly
 * longer than the one before, and the names in a folder are finite.
 */
final class CopyNames {

    /** The most UTF-16 code units a copy's whole name may hold (ADR-280 §3). */
    static final int LONGEST_NAME = 100;

    /** The longest extension kept whole, in code units, the dot before it not counted (ADR-280 §3). */
    private static final int LONGEST_EXTENSION = 16;

    /** The most UTF-8 bytes a copy's name and {@code .part} may hold together (ADR-280 §3). */
    static final int LONGEST_NAME_IN_BYTES = 255;

    private static final String PART = ".part";

    private static final Pattern A_FURTHER_PAGE = Pattern.compile("INDEX-[0-9]+\\.HTML");

    private static final String THE_PAGE = "INDEX.HTML";

    private static final Set<String> DEVICE_NAMES = deviceNames();

    private CopyNames() {}

    /**
     * Every member's copy name in one cluster's folder.
     *
     * @param members the cluster's survivors, in any order
     * @param pictureNames the names of the pictures written into the folder for the cluster's page
     * @return each member's name, in ascending occurrence id
     */
    static Map<OccurrenceId, String> of(List<ListedSurvivor> members, Set<String> pictureNames) {
        List<ListedSurvivor> ascending = new ArrayList<>(members);
        ascending.sort(Comparator.comparingLong(member -> member.occurrence().value()));

        List<Shape> shapes = new ArrayList<>();
        Set<String> reserved = new HashSet<>();
        for (String picture : pictureNames) {
            reserved.add(keyOf(picture));
        }
        for (ListedSurvivor member : ascending) {
            Shape shape = shapeOf(member);
            shapes.add(shape);
            reserved.add(keyOf(shape.name() + PART));
        }

        // Within each set of colliding names, the lowest occurrence id keeps the plain name, unless that name meets
        // a reserved one, in which case none does.
        Map<String, Integer> firstWithKey = new LinkedHashMap<>();
        Set<String> given = new HashSet<>();
        boolean[] keepsItsName = new boolean[shapes.size()];
        for (int at = 0; at < shapes.size(); at++) {
            String key = keyOf(shapes.get(at).name());
            if (firstWithKey.putIfAbsent(key, at) == null && !isReserved(key, reserved)) {
                keepsItsName[at] = true;
                given.add(key);
            }
        }
        Set<String> givenParts = new HashSet<>();
        for (String key : given) {
            givenParts.add(keyOf(key + PART));
        }

        // The plain names kept count as given before the first suffix is, so each suffixed name meets every one.
        Map<OccurrenceId, String> inOrder = new LinkedHashMap<>();
        for (int at = 0; at < shapes.size(); at++) {
            OccurrenceId occurrence = ascending.get(at).occurrence();
            if (keepsItsName[at]) {
                inOrder.put(occurrence, shapes.get(at).name());
                continue;
            }
            Shape original = shapes.get(at);
            String suffix = "~" + occurrence.value();
            String candidate = original.withSuffixes(suffix, 1);
            int attempts = 1;
            while (isTaken(candidate, reserved, given, givenParts)) {
                attempts++;
                candidate = original.withSuffixes(suffix, attempts);
            }
            given.add(keyOf(candidate));
            givenParts.add(keyOf(candidate + PART));
            inOrder.put(occurrence, candidate);
        }
        return inOrder;
    }

    private static boolean isTaken(String name, Set<String> reserved, Set<String> given, Set<String> givenParts) {
        String key = keyOf(name);
        return isReserved(key, reserved) || given.contains(key) || givenParts.contains(key)
                || given.contains(keyOf(name + PART));
    }

    private static boolean isReserved(String key, Set<String> reserved) {
        return reserved.contains(key) || THE_PAGE.equals(key) || A_FURTHER_PAGE.matcher(key).matches();
    }

    /**
     * The collision key (ADR-280 §1.4): normalisation form C, lower-cased and then upper-cased without regard to the
     * reader's locale so that letters which share a lower case (θ and ϴ, ß and ẞ) share a key, and composed again.
     */
    static String keyOf(String name) {
        String folded = Normalizer.normalize(name, Normalizer.Form.NFC).toLowerCase(Locale.ROOT).toUpperCase(Locale.ROOT);
        return Normalizer.normalize(folded, Normalizer.Form.NFC);
    }

    /**
     * The name {@code member}'s copy has where nothing in its folder collides with it: the original's file name made
     * safe (§1.2) and cut (§3), before any suffix. A function of the survivor alone.
     */
    static String plainName(ListedSurvivor member) {
        return shapeOf(member).name();
    }

    /**
     * The original's file name made safe and cut, and made safe again where the cut undid it: a cut can trim a name to
     * nothing, or to a device name, which §1.2's rules did not see on the uncut name. A suffixed name carries a
     * {@code ~} in its stem and so is neither empty nor a device name.
     */
    private static Shape shapeOf(ListedSurvivor member) {
        Shape cut = Shape.of(safeName(member));
        String name = cut.name();
        if (name.isEmpty()) {
            return new Shape(String.valueOf(member.occurrence().value()), "");
        }
        if (isDevice(name)) {
            return new Shape("_" + cut.body(), cut.extension()).cut(0);
        }
        return cut;
    }

    /** The original's file name made safe (§1.2), before it is cut. */
    private static String safeName(ListedSurvivor member) {
        String path = member.path().value();
        String name = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder replaced = new StringBuilder(name.length());
        for (int at = 0; at < name.length(); at++) {
            char c = name.charAt(at);
            replaced.append(isRefused(c) ? '_' : c);
        }
        int end = replaced.length();
        while (end > 0 && (replaced.charAt(end - 1) == '.' || replaced.charAt(end - 1) == ' ')) {
            end--;
        }
        String trimmed = replaced.substring(0, end);
        if (isDevice(trimmed)) {
            trimmed = "_" + trimmed;
        }
        return trimmed.isEmpty() ? String.valueOf(member.occurrence().value()) : trimmed;
    }

    /** Whether the part of {@code name} before its first dot, trailing spaces ignored, is a Windows device name. */
    private static boolean isDevice(String name) {
        int firstDot = name.indexOf('.');
        String beforeTheDot = firstDot < 0 ? name : name.substring(0, firstDot);
        int end = beforeTheDot.length();
        while (end > 0 && beforeTheDot.charAt(end - 1) == ' ') {
            end--;
        }
        return DEVICE_NAMES.contains(beforeTheDot.substring(0, end).toUpperCase(Locale.ROOT));
    }

    /** Whether Windows refuses {@code c} in a name: a control character or one of nine others (ADR-280 §1.2). */
    private static boolean isRefused(char c) {
        return switch (c) {
            case '<', '>', ':', '"', '/', '|', '?', '*', '\\' -> true;
            default -> c < 0x20;
        };
    }

    private static Set<String> deviceNames() {
        Set<String> names = new HashSet<>(List.of("CON", "PRN", "AUX", "NUL"));
        for (int digit = 0; digit <= 9; digit++) {
            names.add("COM" + digit);
            names.add("LPT" + digit);
        }
        for (char superscript : new char[] {'\u00B9', '\u00B2', '\u00B3'}) {
            names.add("COM" + superscript);
            names.add("LPT" + superscript);
        }
        return Set.copyOf(names);
    }

    /** A name taken apart into its stem and its extension, the dot with the extension, so a suffix can go between. */
    private record Shape(String body, String extension) {

        static Shape of(String name) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && name.length() - dot - 1 <= LONGEST_EXTENSION) {
                return new Shape(name.substring(0, dot), name.substring(dot)).cut(0);
            }
            return new Shape(name, "").cut(0);
        }

        String name() {
            return body + extension;
        }

        private static int utf8Length(String text) {
            return text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }

        /** This name cut so that {@code suffixLength} more units fit within {@value #LONGEST_NAME}. */
        private Shape cut(int suffixLength) {
            int room = Math.max(0, LONGEST_NAME - extension.length() - suffixLength);
            int fixedBytes = utf8Length(extension) + suffixLength + PART.length();
            if (body.length() <= room && utf8Length(body) + fixedBytes <= LONGEST_NAME_IN_BYTES) {
                return this;
            }
            int end = Math.min(room, body.length());
            if (end > 0 && end < body.length() && Character.isHighSurrogate(body.charAt(end - 1))
                    && Character.isLowSurrogate(body.charAt(end))) {
                end--;
            }
            while (end > 0 && utf8Length(body.substring(0, end)) + fixedBytes > LONGEST_NAME_IN_BYTES) {
                end -= Character.isLowSurrogate(body.charAt(end - 1)) && end > 1 ? 2 : 1;
            }
            while (end > 0 && (body.charAt(end - 1) == '.' || body.charAt(end - 1) == ' ')) {
                end--;
            }
            return new Shape(body.substring(0, end), extension);
        }

        /**
         * The name with {@code suffix} repeated {@code times} before its extension, the stem cut to leave room for
         * every one.
         */
        String withSuffixes(String suffix, int times) {
            Shape cut = cut(suffix.length() * times);
            return cut.body + suffix.repeat(times) + extension;
        }
    }
}
