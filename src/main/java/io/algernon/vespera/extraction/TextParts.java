package io.algernon.vespera.extraction;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A text over {@link DoclingClient#TEXT_SIZE_CEILING_BYTES} converted in parts and answered as one
 * (ADR-178): where a part ends, which files are cut, how the parts' answers become one {@link
 * DoclingResponse}, and what happens when a part does not convert.
 *
 * <p>Whether a file is cut is decided in one place, {@link #convertedInParts}, which stage 1 asks and
 * {@link DoclingExtractor} asks, so the two cannot disagree. {@link #RULE} names everything here that
 * changes what comes back for a file, and rides in {@link DoclingClient#sentOptions()}: whoever
 * changes the cut, the subtypes and encodings that are cut, the merge or the failure rules bumps its
 * version, and a change to any of the three sizes changes it by itself.
 *
 * <p>The merge is made in Java and not by docling-core's {@code concatenate}, which docling-serve
 * exposes no route to (ADR-178 section 1). It does for these answers what {@code concatenate} does,
 * and a fixture pins it to that output.
 */
public final class TextParts {

    private TextParts() {}

    /** The longest a part is, and the window its end is looked for in (ADR-178 section 2): half the ceiling. */
    public static final long PART_BYTES = 8_000_000L;

    /**
     * The largest text converted in parts, in bytes (ADR-178 section 6): eight parts. The merged answer
     * is one string, parsed whole by every reader and stored as one SQLite value, so a text over this
     * is out of scope, however it is shaped.
     */
    public static final long LARGEST_TEXT_BYTES = 64_000_000L;

    /**
     * What this class does to a text, as the string {@link DoclingClient#sentOptions()} ends with and
     * stage 1's configuration consumed carries (ADR-178 sections 3 and 5). {@code v1} names the version
     * of the cut, the files that are cut, the merge and the failure rules: bump it when any changes.
     */
    public static final String RULE = "v1,over=" + DoclingClient.TEXT_SIZE_CEILING_BYTES + ",upto="
            + LARGEST_TEXT_BYTES + ",part=" + PART_BYTES;

    /**
     * The lists of a {@code DoclingDocument} whose items are numbered by position and referred to as
     * {@code #/<list>/<n>}: the ones a merge concatenates and renumbers.
     */
    private static final List<String> ITEM_LISTS = List.of(
            "texts", "groups", "tables", "pictures", "key_value_items", "form_items", "field_regions", "field_items");

    private static final Pattern ITEM_REFERENCE = Pattern.compile("#/([a-z_]+)/(\\d+)");

    private static final Logger log = LoggerFactory.getLogger(TextParts.class);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * One part of a file: the bytes from {@code start}, counted from 0, up to but not including
     * {@code end}.
     */
    public record Part(long start, long end) {}

    /**
     * No line end lies in the window a part would end in, so there is nowhere to cut (ADR-178 section 2,
     * step 5). Unchecked so that {@link #cut} keeps one signature; {@link #convertInParts} is its only
     * production caller and answers it.
     */
    static final class LineLongerThanAPartException extends RuntimeException {

        private final long lineStart;

        LineLongerThanAPartException(long lineStart) {
            super("the line beginning at byte " + lineStart + " is longer than a part");
            this.lineStart = lineStart;
        }

        /** The byte, counted from 1, where the line that cannot be cut begins. */
        long lineStart() {
            return lineStart;
        }
    }

    /**
     * Whether {@code file}, of {@code size} bytes, is converted in parts (ADR-178 section 3): plain
     * text with no subtype or a Markdown one, over {@link DoclingClient#TEXT_SIZE_CEILING_BYTES} and at
     * most {@link #LARGEST_TEXT_BYTES}, that does not begin with a UTF-16 or UTF-32 byte-order mark.
     * HTML, CSV and AsciiDoc are not cut: a cut can break their structure.
     *
     * <p>A file whose first bytes cannot be read has no byte-order mark as far as this is concerned,
     * and stage 2 meets it as it meets any unreadable file.
     */
    public static boolean convertedInParts(Path file, DetectedFormat format, DetectedSubtype subtype, long size) {
        if (!isCutKind(format, subtype)
                || size <= DoclingClient.TEXT_SIZE_CEILING_BYTES
                || size > LARGEST_TEXT_BYTES) {
            return false;
        }
        return !beginsWithAWideByteOrderMark(file);
    }

    /**
     * {@link #convertedInParts(Path, DetectedFormat, DetectedSubtype, long)} for a caller that has no size
     * yet: the file is measured only where its kind could be cut, so no other file is touched. A file
     * whose size cannot be read is not cut, and is sent as it always was.
     */
    static boolean convertedInParts(Path file, DetectedFormat format, DetectedSubtype subtype) {
        if (!isCutKind(format, subtype)) {
            return false;
        }
        try {
            return convertedInParts(file, format, subtype, Files.size(file));
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isCutKind(DetectedFormat format, DetectedSubtype subtype) {
        return format == DetectedFormat.PLAIN_TEXT && (subtype == null || subtype == DetectedSubtype.MARKDOWN);
    }

    /** {@code FE FF}, {@code FF FE} (which opens UTF-32 little-endian too) or {@code 00 00 FE FF}. */
    private static boolean beginsWithAWideByteOrderMark(Path file) {
        try (var in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(4);
            boolean utf16 = head.length >= 2
                    && ((head[0] == (byte) 0xFE && head[1] == (byte) 0xFF)
                            || (head[0] == (byte) 0xFF && head[1] == (byte) 0xFE));
            boolean utf32BigEndian = head.length == 4
                    && head[0] == 0
                    && head[1] == 0
                    && head[2] == (byte) 0xFE
                    && head[3] == (byte) 0xFF;
            return utf16 || utf32BigEndian;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Where each part of {@code file} ends when no part may be longer than {@code partBytes} (ADR-178
     * section 2): each part starts where the one before ended, and the parts, joined, are the file byte
     * for byte. A file no longer than {@code partBytes} is one part.
     *
     * @throws LineLongerThanAPartException where a part's window holds no line end at all
     */
    static List<Part> cut(Path file, long partBytes) throws IOException {
        return cut(Files.readAllBytes(file), partBytes);
    }

    /**
     * The cut, over bytes already read. A line end is a {@code \n} byte, or a {@code \r} byte not
     * followed by {@code \n}, so a part never ends inside a line, a {@code \r\n} pair or a UTF-8
     * sequence. A part ends just after the last blank line in its window that is outside a fence and
     * ends at or after the window's midpoint; failing that, just after the last line end in the
     * window. Fences are tracked from the start of the file, not of the part.
     */
    static List<Part> cut(byte[] bytes, long partBytes) {
        List<Part> parts = new ArrayList<>();
        long from = 0;
        // The fence character the lines from `from` on are inside of, or 0 where they are outside any.
        byte fenceAtFrom = 0;
        while (bytes.length - from > partBytes) {
            long windowEnd = from + partBytes;
            long midpoint = from + partBytes / 2;
            long blankCut = -1;
            long lineCut = -1;
            byte fenceAtLineCut = 0;
            byte fence = fenceAtFrom;
            int position = (int) from;
            while (position < bytes.length) {
                int next = endOfLine(bytes, position);
                if (next > windowEnd) {
                    break;
                }
                boolean terminated = bytes[next - 1] == '\n' || bytes[next - 1] == '\r';
                int contentEnd = next;
                if (terminated) {
                    contentEnd = bytes[next - 1] == '\n' && next - 1 > position && bytes[next - 2] == '\r'
                            ? next - 2
                            : next - 1;
                }
                boolean blank = isBlank(bytes, position, contentEnd);
                boolean insideFence = fence != 0;
                byte fenceChar = fenceCharOf(bytes, position, contentEnd);
                if (fenceChar != 0 && (fence == 0 || fenceChar == fence)) {
                    fence = fence == 0 ? fenceChar : 0;
                    insideFence = true;
                }
                if (terminated) {
                    lineCut = next;
                    fenceAtLineCut = fence;
                    if (blank && !insideFence && next >= midpoint) {
                        blankCut = next;
                    }
                }
                position = next;
            }
            if (lineCut < 0) {
                throw new LineLongerThanAPartException(from + 1);
            }
            long end = blankCut >= 0 ? blankCut : lineCut;
            // A blank line outside a fence leaves the state as it found it: outside.
            fenceAtFrom = blankCut >= 0 ? 0 : fenceAtLineCut;
            parts.add(new Part(from, end));
            from = end;
        }
        parts.add(new Part(from, bytes.length));
        return parts;
    }

    /** The index just after the line that begins at {@code start}: its line end, or the end of the bytes. */
    private static int endOfLine(byte[] bytes, int start) {
        for (int i = start; i < bytes.length; i++) {
            if (bytes[i] == '\n' || (bytes[i] == '\r' && (i + 1 == bytes.length || bytes[i + 1] != '\n'))) {
                return i + 1;
            }
        }
        return bytes.length;
    }

    /** Whether {@code [start, end)} holds nothing but spaces and tabs. */
    private static boolean isBlank(byte[] bytes, int start, int end) {
        for (int i = start; i < end; i++) {
            if (bytes[i] != ' ' && bytes[i] != '\t') {
                return false;
            }
        }
        return true;
    }

    /**
     * The character of the fence this line is, or 0 where it is none: a run of three or more backticks
     * or tildes, after at most three spaces, beginning the line's first non-space character.
     */
    private static byte fenceCharOf(byte[] bytes, int start, int end) {
        int i = start;
        while (i < end && i - start < 3 && bytes[i] == ' ') {
            i++;
        }
        if (i + 2 >= end) {
            return 0;
        }
        byte c = bytes[i];
        return (c == '`' || c == '~') && bytes[i + 1] == c && bytes[i + 2] == c ? c : 0;
    }

    /**
     * Converts {@code file} in parts, one after another, and answers as one (ADR-178 sections 2, 4 and
     * 7). Each part is posted through {@code client}, with the file's own {@code format} and
     * {@code subtype}, from a temporary file named {@code <the file's name>.part-<k>-of-<n>}; no
     * temporary file is left behind, however the call ends.
     *
     * <p>The first part that does not convert ends the file: no later part is sent, and the answer is
     * that part's, its errors named by part. Whatever a call throws is rethrown as it is. A file with a
     * line longer than a part is never sent, and answers a failure about the document.
     */
    static DoclingResponse convertInParts(DoclingClient client, Path file, DetectedFormat format, DetectedSubtype subtype) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Part> parts;
        try {
            parts = cut(bytes, PART_BYTES);
        } catch (LineLongerThanAPartException cannotCut) {
            return cannotBeCut(cannotCut.lineStart());
        }
        Path directory;
        try {
            directory = Files.createTempDirectory("vespera-text-parts-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            List<DoclingResponse> answers = new ArrayList<>();
            for (int k = 1; k <= parts.size(); k++) {
                Part part = parts.get(k - 1);
                Path posted = directory.resolve(file.getFileName() + ".part-" + k + "-of-" + parts.size());
                DoclingResponse answer;
                try {
                    try (OutputStream out = Files.newOutputStream(posted)) {
                        out.write(bytes, (int) part.start(), (int) (part.end() - part.start()));
                    }
                    answer = client.convert(posted, format, subtype);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } finally {
                    deleteQuietly(posted);
                }
                String label = String.format(
                        Locale.ROOT, "part %d of %d (bytes %,d to %,d): ", k, parts.size(), part.start() + 1, part.end());
                if (answer.status() != ConversionStatus.SUCCESS && answer.status() != ConversionStatus.PARTIAL_SUCCESS) {
                    return failedPart(answer, label);
                }
                if (!(JSON.readTree(answer.rawResponse()).path("document").path("json_content") instanceof ObjectNode)) {
                    return noDocument(label);
                }
                answers.add(new DoclingResponse(
                        answer.status(),
                        prefixed(answer.errors(), label),
                        answer.processingTimeSeconds(),
                        answer.confidence(),
                        answer.rawResponse()));
            }
            return merged(answers);
        } finally {
            deleteQuietly(directory);
        }
    }

    /**
     * The answer for a part that said {@code success} or {@code partial_success} and carried no
     * document: a failure about the document, so that the file fails and the run goes on.
     */
    private static DoclingResponse noDocument(String label) {
        DoclingError error = new DoclingError(
                "vespera", "text-parts", label + "the converter answered with no document", FailureCategory.UNKNOWN, null);
        ObjectNode raw = JSON.createObjectNode();
        raw.putNull("document");
        raw.put("status", ConversionStatus.FAILURE.toWire());
        raw.set("errors", errorsOf(List.of(error)));
        raw.put("processing_time", 0.0);
        raw.putObject("timings");
        raw.putNull("confidence");
        return new DoclingResponse(ConversionStatus.FAILURE, List.of(error), 0.0, null, raw.toString());
    }

    /** The answer for a text with a line longer than a part (ADR-178 section 4): a failure, with no document. */
    private static DoclingResponse cannotBeCut(long lineStart) {
        DoclingError error = new DoclingError(
                "vespera",
                "text-parts",
                String.format(
                        Locale.ROOT,
                        "the text cannot be cut into parts: the line beginning at byte %,d is longer than %,d bytes",
                        lineStart,
                        PART_BYTES),
                FailureCategory.UNKNOWN,
                null);
        ObjectNode raw = JSON.createObjectNode();
        raw.putNull("document");
        raw.put("status", ConversionStatus.FAILURE.toWire());
        raw.set("errors", errorsOf(List.of(error)));
        raw.put("processing_time", 0.0);
        raw.putObject("timings");
        raw.putNull("confidence");
        return new DoclingResponse(ConversionStatus.FAILURE, List.of(error), 0.0, null, raw.toString());
    }

    /**
     * The file's answer when a part did not convert (ADR-178 section 4): that part's own, with each
     * error named by part, in the record and in the raw body alike. Where the part reported no error one
     * is supplied, of the category {@code unknown}, which {@link ResponseScope} reads as it reads a
     * failure reporting none.
     */
    private static DoclingResponse failedPart(DoclingResponse answer, String label) {
        List<DoclingError> errors = answer.errors().isEmpty()
                ? List.of(new DoclingError(
                        "vespera", "text-parts", label + "no categorized error was reported", FailureCategory.UNKNOWN, null))
                : prefixed(answer.errors(), label);
        ObjectNode raw = (ObjectNode) JSON.readTree(answer.rawResponse());
        raw.set("errors", errorsOf(errors));
        return new DoclingResponse(
                answer.status(), errors, answer.processingTimeSeconds(), answer.confidence(), raw.toString());
    }

    /**
     * One response from the answers of a text's parts, in part order, each {@code success} or {@code
     * partial_success} (ADR-178 section 2): one {@code DoclingDocument} whose items are the parts' in
     * order, with every reference renumbered and page numbers shifted as docling-core's {@code
     * concatenate} does, the worst status, every error, the summed {@code processing_time} and the
     * lowest {@code confidence}.
     *
     * <p>The errors are taken as the answers carry them. Which bytes a part covers is not in its
     * answer, so {@link #convertInParts} names each error by part before the merge.
     */
    static DoclingResponse merged(List<DoclingResponse> answers) {
        ObjectNode root = (ObjectNode) JSON.readTree(answers.getFirst().rawResponse());
        ObjectNode merged = contentOf(root).deepCopy();
        for (String list : ITEM_LISTS) {
            if (merged.has(list)) {
                merged.putArray(list);
            }
        }
        childrenOf(merged, "body").removeAll();
        if (merged.has("furniture")) {
            childrenOf(merged, "furniture").removeAll();
        }
        ObjectNode mergedPages = merged.putObject("pages");
        int highestPage = 0;

        for (DoclingResponse answer : answers) {
            ObjectNode part = contentOf((ObjectNode) JSON.readTree(answer.rawResponse()));
            Map<String, Integer> offsets = new HashMap<>();
            for (String list : ITEM_LISTS) {
                offsets.put(list, merged.path(list).size());
            }
            renumber(part, offsets);

            JsonNode partPages = part.remove("pages");
            int pageShift = pageShift(partPages, highestPage);
            if (pageShift != 0) {
                shiftProvenance(part, pageShift);
            }
            if (partPages instanceof ObjectNode pages) {
                for (String key : List.copyOf(pages.propertyNames())) {
                    JsonNode page = pages.get(key);
                    if (pageShift != 0 && page instanceof ObjectNode pageObject && pageObject.has("page_no")) {
                        pageObject.put("page_no", pageObject.get("page_no").asInt() + pageShift);
                    }
                    int shifted = Integer.parseInt(key) + pageShift;
                    mergedPages.set(String.valueOf(shifted), page);
                    highestPage = Math.max(highestPage, shifted);
                }
            }

            for (String list : ITEM_LISTS) {
                if (part.has(list)) {
                    if (!merged.has(list)) {
                        merged.putArray(list);
                    }
                    ((ArrayNode) merged.get(list)).addAll((ArrayNode) part.get(list));
                }
            }
            childrenOf(merged, "body").addAll(childrenOf(part, "body"));
            if (part.path("furniture").has("children")) {
                childrenOf(merged, "furniture").addAll(childrenOf(part, "furniture"));
            }
        }

        ConversionStatus status = answers.stream().anyMatch(a -> a.status() == ConversionStatus.PARTIAL_SUCCESS)
                ? ConversionStatus.PARTIAL_SUCCESS
                : ConversionStatus.SUCCESS;
        List<DoclingError> errors =
                answers.stream().flatMap(a -> a.errors().stream()).toList();
        double processingTime =
                answers.stream().mapToDouble(DoclingResponse::processingTimeSeconds).sum();
        ConfidenceScores confidence = lowestConfidence(answers);

        ((ObjectNode) root.get("document")).set("json_content", merged);
        root.put("status", status.toWire());
        root.set("errors", errorsOf(errors));
        root.put("processing_time", processingTime);
        root.putObject("timings");
        root.set("confidence", confidenceNode(root.get("confidence"), confidence));
        return new DoclingResponse(status, errors, processingTime, confidence, root.toString());
    }

    private static ObjectNode contentOf(ObjectNode body) {
        if (body.path("document").path("json_content") instanceof ObjectNode content) {
            return content;
        }
        // Defensive only: convertInParts ends the file on a part with no document before it merges.
        throw new IllegalStateException("a part's answer carries no document to merge");
    }

    /** The {@code children} array of the {@code body} or {@code furniture} of {@code content}. */
    private static ArrayNode childrenOf(ObjectNode content, String root) {
        return (ArrayNode) content.path(root).path("children");
    }

    /**
     * Moves every {@code $ref} and {@code self_ref} of the form {@code #/<list>/<n>} in {@code node}, at
     * any depth, to {@code n} plus {@code offsets}' count for that list. {@code #/body} and {@code
     * #/furniture} do not match, and stay.
     */
    private static void renumber(JsonNode node, Map<String, Integer> offsets) {
        if (node instanceof ObjectNode object) {
            for (String key : List.copyOf(object.propertyNames())) {
                JsonNode value = object.get(key);
                if (("$ref".equals(key) || "self_ref".equals(key)) && value.isString()) {
                    Matcher reference = ITEM_REFERENCE.matcher(value.asString());
                    if (reference.matches() && offsets.containsKey(reference.group(1))) {
                        int moved = Integer.parseInt(reference.group(2)) + offsets.get(reference.group(1));
                        object.put(key, "#/" + reference.group(1) + "/" + moved);
                    }
                } else {
                    renumber(value, offsets);
                }
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(child -> renumber(child, offsets));
        }
    }

    /**
     * How far a part's page numbers move: the highest page number before it, less its own lowest, plus
     * one, as {@code concatenate} shifts them. Zero for a part with no pages, as every text answer
     * measured had.
     */
    private static int pageShift(JsonNode pages, int highestPageBefore) {
        if (!(pages instanceof ObjectNode object) || object.isEmpty()) {
            return 0;
        }
        int lowest = Integer.MAX_VALUE;
        for (String key : object.propertyNames()) {
            lowest = Math.min(lowest, Integer.parseInt(key));
        }
        return highestPageBefore - lowest + 1;
    }

    /** Adds {@code shift} to the {@code page_no} of every {@code prov} entry under {@code node}. */
    private static void shiftProvenance(JsonNode node, int shift) {
        if (node instanceof ObjectNode object) {
            for (String key : List.copyOf(object.propertyNames())) {
                JsonNode value = object.get(key);
                if ("prov".equals(key) && value instanceof ArrayNode entries) {
                    for (JsonNode entry : entries) {
                        if (entry instanceof ObjectNode provenance && provenance.has("page_no")) {
                            provenance.put("page_no", provenance.get("page_no").asInt() + shift);
                        }
                    }
                }
                shiftProvenance(value, shift);
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(child -> shiftProvenance(child, shift));
        }
    }

    /**
     * Each score the lowest any part reports, or null where none does; each grade the worst any part
     * reports, {@code unspecified} only where every part's is. Null where no part reports a confidence at all.
     */
    private static ConfidenceScores lowestConfidence(List<DoclingResponse> answers) {
        List<ConfidenceScores> reported =
                answers.stream().map(DoclingResponse::confidence).filter(c -> c != null).toList();
        if (reported.isEmpty()) {
            return null;
        }
        return new ConfidenceScores(
                lowest(reported, ConfidenceScores::parseScore),
                lowest(reported, ConfidenceScores::layoutScore),
                lowest(reported, ConfidenceScores::tableScore),
                lowest(reported, ConfidenceScores::ocrScore),
                lowest(reported, ConfidenceScores::meanScore),
                lowest(reported, ConfidenceScores::lowScore),
                worst(reported, ConfidenceScores::meanGrade),
                worst(reported, ConfidenceScores::lowGrade));
    }

    private static Double lowest(List<ConfidenceScores> reported, Function<ConfidenceScores, Double> score) {
        return reported.stream().map(score).filter(s -> s != null).min(Double::compare).orElse(null);
    }

    /** The lowest-ranked scored grade, the scale running {@code poor} up to {@code excellent}. */
    private static QualityGrade worst(List<ConfidenceScores> reported, Function<ConfidenceScores, QualityGrade> grade) {
        return reported.stream()
                .map(grade)
                .filter(g -> g != null && g != QualityGrade.UNSPECIFIED)
                .min(Comparator.naturalOrder())
                .orElse(QualityGrade.UNSPECIFIED);
    }

    /**
     * The raw body's {@code confidence}: the first answer's own, with the merged scores and grades laid
     * over it, so that the body and the record agree.
     */
    private static JsonNode confidenceNode(JsonNode first, ConfidenceScores confidence) {
        if (confidence == null) {
            return JSON.nullNode();
        }
        ObjectNode node = first instanceof ObjectNode object ? object : JSON.createObjectNode();
        putScore(node, "parse_score", confidence.parseScore());
        putScore(node, "layout_score", confidence.layoutScore());
        putScore(node, "table_score", confidence.tableScore());
        putScore(node, "ocr_score", confidence.ocrScore());
        putScore(node, "mean_score", confidence.meanScore());
        putScore(node, "low_score", confidence.lowScore());
        node.put("mean_grade", confidence.meanGrade().toWire());
        node.put("low_grade", confidence.lowGrade().toWire());
        return node;
    }

    private static void putScore(ObjectNode node, String name, Double score) {
        if (score == null) {
            node.putNull(name);
        } else {
            node.put(name, score);
        }
    }

    /** {@code errors} with {@code label} in front of each message. */
    private static List<DoclingError> prefixed(List<DoclingError> errors, String label) {
        return errors.stream()
                .map(error -> new DoclingError(
                        error.componentType(),
                        error.moduleName(),
                        label + error.errorMessage(),
                        error.category(),
                        error.pageNo()))
                .toList();
    }

    /** {@code errors} as a response body's {@code errors} array. */
    private static ArrayNode errorsOf(List<DoclingError> errors) {
        ArrayNode array = JSON.createArrayNode();
        for (DoclingError error : errors) {
            ObjectNode wire = array.addObject();
            wire.put("component_type", error.componentType());
            wire.put("module_name", error.moduleName());
            wire.put("error_message", error.errorMessage());
            wire.put("category", error.category().toWire());
            if (error.pageNo() == null) {
                wire.putNull("page_no");
            } else {
                wire.put("page_no", error.pageNo());
            }
        }
        return array;
    }

    /** Removes {@code path}, a file or an empty directory; a temporary file that stays is a warning, never a failure. */
    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("a temporary file of a text converted in parts could not be removed: {}: {}", path, e.toString());
        }
    }
}
