package io.algernon.controlpanel;

import io.algernon.controlpanel.YamlLines.Line;
import io.algernon.controlpanel.YamlLines.NotInFormException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Writing one profile value (ADR-295 §3, §4): the key's {@code value} and {@code provenance} lines replaced where
 * they stand, every other byte of {@code profile.yaml} kept, in a file read only in the form the jar writes it and
 * the README and ADR-284 §9 show; and each value checked by the rule the jar applies to it, in {@code java.base},
 * before anything is written. A refusal is an {@link IllegalArgumentException} whose message is the one line.
 */
public final class ProfileEdit {

    public static final String FILE_NAME = "profile.yaml";

    /** What the file says of a key: {@code null} where it says {@code null} or does not say. */
    public record Stated(String value, String provenance) {
    }

    private static final String NOT_IN_FORM =
            FILE_NAME + " is not in the form the jar writes it, so the control panel does not change it";

    /** The words {@code vespera label --auto} opens the floor's provenance with, which let it write over the floor. */
    private static final String THE_AUTO_RULES_WORDS = "set by the rule that loses no documentation";

    private static final long LARGEST_WHOLE = Integer.MAX_VALUE;

    private static final long SMALLEST_WINDOW = 1282;

    private static final Pattern PIECES = Pattern.compile("(?U)[\\s,]+");

    private static final Pattern LANGUAGE = Pattern.compile("[A-Za-z]{2}");

    private static final Pattern ARRANGEMENT = Pattern.compile("[0-9a-f]{12}");

    private ProfileEdit() {
    }

    /** §4's line for a value the jar would read as unreadable, ignore or stop on, or for no provenance; else empty. */
    public static Optional<String> refusalOf(ProfileKey key, String value, String provenance) {
        String typed = value == null ? "" : value.strip();
        if (typed.chars().anyMatch(c -> c < 0x20)) {
            return said(key, "a value is one line");
        }
        if (provenance == null || provenance.isBlank()) {
            return said(key, "write how you arrived at it in provenance");
        }
        if (key == ProfileKey.RELEVANCE_SCORE_FLOOR && provenance.startsWith(THE_AUTO_RULES_WORDS)) {
            return said(key, "a provenance beginning \"" + THE_AUTO_RULES_WORDS
                    + "\" is the one vespera label --auto writes, and would let it write over this value");
        }
        if (typed.isEmpty()) {
            return Optional.empty();
        }
        return reasonForValue(key, typed).flatMap(reason -> said(key, reason));
    }

    /** §3's text of the file with {@code key} saved as {@code value} and {@code provenance}. */
    public static String rewrite(Optional<String> current, ProfileKey key, String value, String provenance) {
        Optional<String> refusal = refusalOf(key, value, provenance);
        if (refusal.isPresent()) {
            throw new IllegalArgumentException(refusal.get());
        }
        String valueLine = "  value: " + scalarOf(value.strip());
        String provenanceLine = "  provenance: " + YamlScalars.write(provenance);
        if (current.isEmpty()) {
            return "---\n" + block(key, valueLine, provenanceLine, "\n");
        }
        Parsed parsed = parse(current.get());
        Block block = parsed.blocks().get(key);
        List<Line> lines = parsed.lines();
        if (block == null) {
            String end = YamlLines.endOf(lines);
            if (!lines.isEmpty() && lines.getLast().eol().isEmpty()) {
                lines.set(lines.size() - 1, new Line(lines.getLast().text(), end));
            }
            return YamlLines.join(lines) + block(key, valueLine, provenanceLine, end);
        }
        List<String> added = new ArrayList<>();
        List<Replacement> replacements = new ArrayList<>();
        place(block.value(), valueLine, added, replacements);
        place(block.provenance(), provenanceLine, added, replacements);
        replacements.sort((a, b) -> Integer.compare(b.entry().first(), a.entry().first()));
        for (Replacement replacement : replacements) {
            Entry entry = replacement.entry();
            Line last = lines.get(entry.last());
            for (int n = entry.last(); n > entry.first(); n--) {
                lines.remove(n);
            }
            lines.set(entry.first(), new Line(replacement.text(), last.eol()));
        }
        if (!added.isEmpty()) {
            YamlLines.insertAfter(lines, block.keyLine(), added);
        }
        return YamlLines.join(lines);
    }

    /** The value and provenance the file states for each key it holds, decoded as the jar reads them (§3, §3a). */
    public static Map<ProfileKey, Stated> read(String text) {
        Parsed parsed = parse(text);
        Map<ProfileKey, Stated> stated = new LinkedHashMap<>();
        parsed.blocks().entrySet().stream()
                .sorted(Map.Entry.comparingByValue((a, b) -> Integer.compare(a.keyLine(), b.keyLine())))
                .forEach(held -> stated.put(held.getKey(), new Stated(
                        held.getValue().value() == null ? null : held.getValue().value().text(),
                        held.getValue().provenance() == null ? null : held.getValue().provenance().text())));
        return stated;
    }

    private static Optional<String> said(ProfileKey key, String reason) {
        return Optional.of(key.key() + " is not saved: " + reason);
    }

    private static Optional<String> reasonForValue(ProfileKey key, String value) {
        String quoted = "\"" + value + "\"";
        return switch (key) {
            case SEED_FOLDER -> isAFolderThatExists(value) ? Optional.empty()
                    : Optional.of(quoted + " is not the absolute path of a folder that exists");
            case EMBEDDING_MODEL, GENERATION_MODEL -> Optional.empty();
            case ARRANGEMENT_APPROVED -> ARRANGEMENT.matcher(value).matches() ? Optional.empty()
                    : Optional.of(quoted + " is not the twelve characters naming an arrangement, each 0 to 9 or a to f");
            case DEGENERATE_OUTPUT_WORD_FLOOR -> isWhole(value, 0) ? Optional.empty()
                    : Optional.of(quoted + " is not a whole number from 0 to " + LARGEST_WHOLE);
            case EXTRACTION_ATTEMPT -> isWhole(value, 1) ? Optional.empty()
                    : Optional.of(quoted + " is not a whole number from 1 to " + LARGEST_WHOLE);
            case GENERATION_CONTEXT_WINDOW -> isWhole(value, SMALLEST_WINDOW) ? Optional.empty()
                    : Optional.of(quoted + " is not a whole number of tokens from " + SMALLEST_WINDOW + " to "
                            + LARGEST_WHOLE);
            case CODE_NAME_ENDINGS, OUT_OF_SCOPE_NAME_ENDINGS -> areNameEndings(value) ? Optional.empty()
                    : Optional.of(quoted + " is not a list of name endings, each beginning with a dot");
            case PREFERRED_LANGUAGES -> areLanguages(value) ? Optional.empty()
                    : Optional.of(quoted + " is not a list of two-letter language codes");
            default -> number(value).isPresent() ? Optional.empty() : Optional.of(quoted + " is not a number");
        };
    }

    private static boolean isAFolderThatExists(String value) {
        try {
            Path path = Path.of(value);
            return path.isAbsolute() && Files.isDirectory(path);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /** The number {@code Double.parseDouble} reads from the text, when it is finite. */
    private static Optional<Double> number(String value) {
        try {
            double number = Double.parseDouble(value);
            return Double.isFinite(number) ? Optional.of(number) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static boolean isWhole(String value, long smallest) {
        return number(value).filter(number -> number % 1 == 0 && number >= smallest && number <= LARGEST_WHOLE)
                .isPresent();
    }

    private static boolean areNameEndings(String value) {
        List<String> pieces = piecesOf(value);
        return !pieces.isEmpty() && pieces.stream().allMatch(piece -> piece.startsWith(".")
                && !piece.chars().allMatch(c -> c == '.')
                && piece.chars().noneMatch(c -> "/\\*?".indexOf(c) >= 0));
    }

    private static boolean areLanguages(String value) {
        List<String> pieces = piecesOf(value);
        return !pieces.isEmpty() && pieces.stream().allMatch(piece -> LANGUAGE.matcher(piece).matches());
    }

    private static List<String> piecesOf(String value) {
        return List.of(PIECES.split(value)).stream().filter(piece -> !piece.isEmpty()).toList();
    }

    private static String scalarOf(String value) {
        return value.isEmpty() ? "null" : YamlScalars.write(value);
    }

    private static String block(ProfileKey key, String valueLine, String provenanceLine, String end) {
        return key.key() + ":" + end + valueLine + end + provenanceLine + end + "  measurement: null" + end;
    }

    private static void place(Entry entry, String line, List<String> added, List<Replacement> replacements) {
        if (entry == null) {
            added.add(line);
        } else {
            replacements.add(new Replacement(entry, line));
        }
    }

    /** An entry of a key's block: the lines it runs over, and what it reads to. */
    private record Entry(int first, int last, String text) {
    }

    private record Replacement(Entry entry, String text) {
    }

    private record Block(int keyLine, Entry value, Entry provenance) {
    }

    private record Parsed(List<Line> lines, Map<ProfileKey, Block> blocks) {
    }

    /** §3's form read, or {@link IllegalArgumentException} with §2's line. */
    private static Parsed parse(String text) {
        try {
            return parseForm(text);
        } catch (NotInFormException other) {
            throw new IllegalArgumentException(NOT_IN_FORM);
        }
    }

    private static Parsed parseForm(String text) {
        List<Line> lines = new ArrayList<>(YamlLines.split(text));
        Map<ProfileKey, Block> blocks = new EnumMap<>(ProfileKey.class);
        int i = 0;
        while (i < lines.size() && YamlLines.blank(lines.get(i).text())) {
            i++;
        }
        if (i < lines.size() && lines.get(i).text().equals("---")) {
            i++;
        }
        ProfileKey key = null;
        int keyLine = -1;
        Entry value = null;
        Entry provenance = null;
        boolean measurementSeen = false;
        boolean inMeasurement = false;
        for (; i < lines.size(); i++) {
            String line = lines.get(i).text();
            if (YamlLines.blank(line)) {
                continue;
            }
            int indent = YamlLines.indentOf(line);
            if (indent == 0) {
                if (key != null) {
                    blocks.put(key, new Block(keyLine, value, provenance));
                }
                if (!line.endsWith(":")) {
                    throw new NotInFormException();
                }
                key = ProfileKey.named(line.substring(0, line.length() - 1)).orElseThrow(NotInFormException::new);
                if (blocks.containsKey(key)) {
                    throw new NotInFormException();
                }
                keyLine = i;
                value = null;
                provenance = null;
                measurementSeen = false;
                inMeasurement = false;
            } else if (key == null || line.charAt(indent) == '\t') {
                throw new NotInFormException();
            } else if (indent == 2) {
                inMeasurement = false;
                String entry = line.substring(2);
                int colon = entry.indexOf(':');
                String name = colon < 0 ? "" : entry.substring(0, colon);
                String afterColon = colon < 0 ? "" : entry.substring(colon + 1);
                switch (name) {
                    case "value" -> {
                        if (value != null) {
                            throw new NotInFormException();
                        }
                        value = entryAt(lines, i, afterColon);
                        i = value.last();
                    }
                    case "provenance" -> {
                        if (provenance != null) {
                            throw new NotInFormException();
                        }
                        provenance = entryAt(lines, i, afterColon);
                        i = provenance.last();
                    }
                    case "measurement" -> {
                        if (measurementSeen) {
                            throw new NotInFormException();
                        }
                        measurementSeen = true;
                        if (YamlLines.blank(afterColon)) {
                            inMeasurement = true;
                        } else if (!afterColon.startsWith(" ") || !afterColon.strip().equals("null")) {
                            throw new NotInFormException();
                        }
                    }
                    default -> throw new NotInFormException();
                }
            } else if (!(indent >= 4 && inMeasurement)) {
                throw new NotInFormException();
            }
        }
        if (key != null) {
            blocks.put(key, new Block(keyLine, value, provenance));
        }
        return new Parsed(lines, blocks);
    }

    /** The {@code value} or {@code provenance} entry that opens at line {@code index}, its scalar read (§3a). */
    private static Entry entryAt(List<Line> lines, int index, String afterColon) {
        YamlScalars.Scalar scalar = YamlScalars.read(lines, index, afterColon, 4);
        boolean isNull = scalar.plain() && scalar.text().equals("null");
        return new Entry(index, scalar.lastLine(), isNull ? null : scalar.text());
    }
}
