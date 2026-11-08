package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A text file over the converter's ceiling is cut into parts, each part converted by a call of its
 * own, and the answers merged into one answer that every reader reads as it reads a whole file's
 * (ADR-178, #371).
 *
 * <p>The real {@link DoclingExtractor} over a stub {@link DoclingClient}: a subclass overriding the
 * package-private {@code convert(Path, DetectedFormat, DetectedSubtype)} that every part, like every
 * whole file, is posted through. It records the name and the bytes of each file it is handed, and
 * answers a part as the Markdown backend was measured to answer one, with a text item for every
 * non-blank line. Its call count lives in the instance each test builds, so nothing carries over from
 * one test to the next.
 *
 * <p>{@link #theMergedAnswerReadsAsTheWholeFileDoes} uses answers recorded from the real sidecar for
 * ADR-178, under {@code text-in-parts/}: a small Markdown text, each of its three parts' answers, the
 * whole text's answer, and docling-core's own concatenation of the three.

 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Converting a large text in parts")
@Issue("371")
@Link(name = "ADR-178", url = Adr.TEXT_OVER_THE_CEILING_IS_CONVERTED_IN_PARTS, type = "adr")
class TextInPartsTest {

    /** The OCR preset the application ships (vespera.docling.ocr-preset). */
    private static final String OCR_PRESET = "rapidocr";

    /** The largest part sent, in bytes: half the converter's ceiling. Written out so the test pins it. */
    private static final long PART_BYTES = 8_000_000L;

    /** The size of the text the first test cuts: over the ceiling, and more than two parts. */
    private static final long A_TEXT_OVER_THE_CEILING_BYTES = 17_000_000L;

    /**
     * A text of lines of exactly {@link #LINE_BYTES} bytes and no blank line, this many bytes long, is
     * cut into two parts of exactly 8,000,000 bytes and a third of 100.
     */
    private static final long THREE_PART_TEXT_BYTES = 16_000_100L;

    /** The length of each line of {@link #THREE_PART_TEXT_BYTES}' text, its line end included. */
    private static final int LINE_BYTES = 100;

    /** What the stub's Docling says when it cannot read a part. */
    private static final String UNREADABLE_PART = "the converter could not read this part";

    /** How the second of three parts of the three-part text is named in an answer. */
    private static final String SECOND_OF_THREE = "part 2 of 3 (bytes 8,000,001 to 16,000,000): ";

    /** The small limit the fixture text was cut at for the sidecar, in bytes. */
    private static final long FIXTURE_LIMIT = 4_096L;

    /** Where the fixture's first part ends: the blank line before a fenced block that holds three. */
    private static final long FIXTURE_FIRST_END = 3_485L;

    /** Where a cut that ignored fences would have ended the fixture's first part: inside the block. */
    private static final long FIXTURE_CUT_INSIDE_THE_FENCE = 4_077L;

    /** Where the fixture's second part ends: a line end in a stretch with no blank line. */
    private static final long FIXTURE_SECOND_END = 7_502L;

    /** The fixture text's size in bytes. */
    private static final long FIXTURE_BYTES = 10_398L;

    private static final JsonMapper JSON = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A text over the ceiling is converted in parts")
    @DisplayName("A text over the ceiling is posted in parts cut at line ends, and answered as one document")
    void aTextOverTheCeilingIsConvertedInPartsAndAnsweredAsOne(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("export.txt");
        writeParagraphsWithAStretchOfLines(file, A_TEXT_OVER_THE_CEILING_BYTES);
        RecordingClient client = new RecordingClient((part, number) -> answerFor(part, ConversionStatus.SUCCESS, List.of()));

        DoclingResponse answer = new DoclingExtractor(client, null).convertUncached(file, DetectedFormat.PLAIN_TEXT, null);

        claim(
                "a text of 17,000,000 bytes is posted in more than two calls, since no part is over 8,000,000"
                        + " bytes",
                () -> assertThat(client.posted).hasSizeGreaterThan(2));
        claim(
                "every part is at most 8,000,000 bytes and ends at a line end, never inside a line",
                () -> assertThat(client.posted).allSatisfy(part -> {
                    assertThat((long) part.bytes().length).isLessThanOrEqualTo(PART_BYTES);
                    assertThat(part.bytes()[part.bytes().length - 1]).isEqualTo((byte) '\n');
                }));
        claim(
                "the parts, joined in the order they were posted, are the file byte for byte: nothing lost"
                        + " and nothing repeated at a boundary",
                () -> assertThat(joined(client.posted)).isEqualTo(Files.readAllBytes(file)));
        claim(
                "each part is posted as the file's own format and subtype, so its name is the whole file's",
                () -> assertThat(client.posted).allSatisfy(part -> {
                    assertThat(part.format()).isEqualTo(DetectedFormat.PLAIN_TEXT);
                    assertThat(part.subtype()).isNull();
                }));
        int count = client.posted.size();
        List<String> expectedNames = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            expectedNames.add("export.txt.part-" + number + "-of-" + count);
        }
        claim(
                "from a file named for the file and the part, so any message naming the posted file names"
                        + " the part",
                () -> assertThat(client.posted.stream().map(Posted::name).toList()).isEqualTo(expectedNames));
        claim(
                "and none of those files is left behind once the answer is in",
                () -> assertThat(client.posted).allSatisfy(part -> assertThat(part.path()).doesNotExist()));
        claim(
                "the caller gets one answer, a success, whose time is the parts' times added up",
                () -> {
                    assertThat(answer.status()).isEqualTo(ConversionStatus.SUCCESS);
                    assertThat(answer.processingTimeSeconds()).isEqualTo(count * PART_PROCESSING_TIME);
                });
        claim(
                "and its text, read as the near-duplicate check reads it, is every non-blank line of the file, in order,"
                        + " none lost and none repeated",
                () -> assertThat(DoclingDocumentTexts.lines(answer.rawResponse()))
                        .isEqualTo(String.join("\n", nonBlankLines(Files.readAllBytes(file)))));
    }

    @Test
    @Story("One part that does not convert fails the whole file")
    @DisplayName("A part the converter cannot read fails the file, names the part, and no later part is sent")
    void oneFailingPartFailsTheWholeFileAndNamesThePart(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("export.txt");
        writeLinesOfExactly(file, THREE_PART_TEXT_BYTES);
        RecordingClient client = new RecordingClient((part, number) -> number == 2
                ? answerFor(part, ConversionStatus.FAILURE, List.of(error(FailureCategory.BACKEND_FAILURE, UNREADABLE_PART)))
                : answerFor(part, ConversionStatus.SUCCESS, List.of()));

        DoclingResponse answer = new DoclingExtractor(client, null).convertUncached(file, DetectedFormat.PLAIN_TEXT, null);

        claim(
                "the second of the three parts is the last one sent: once a part fails, the rest are not",
                () -> assertThat(client.posted).hasSize(2));
        claim(
                "the file's answer is a failure",
                () -> assertThat(answer.status()).isEqualTo(ConversionStatus.FAILURE));
        claim(
                "whose one error keeps the converter's category and its words, after the part they are about",
                () -> assertThat(answer.errors()).singleElement().satisfies(only -> {
                    assertThat(only.category()).isEqualTo(FailureCategory.BACKEND_FAILURE);
                    assertThat(only.errorMessage()).isEqualTo(SECOND_OF_THREE + UNREADABLE_PART);
                }));
        claim(
                "and the body kept in the cache says the same, so what is stored and what is read agree",
                () -> assertThat(JSON.readTree(answer.rawResponse()).path("errors").path(0).path("error_message").asString())
                        .isEqualTo(SECOND_OF_THREE + UNREADABLE_PART));
        claim(
                "it is read as a failure about the document, so stage 2 removes the file, as it would the part",
                () -> assertThat(ResponseScope.of(answer)).isInstanceOf(ResponseScope.DocumentScope.class));
        claim(
                "and no part's file is left behind",
                () -> assertThat(client.posted).allSatisfy(part -> assertThat(part.path()).doesNotExist()));
    }

    @Test
    @Story("The answers are merged into one document")
    @DisplayName("The parts' answers, merged, read as the whole text's answer does, and as docling-core's own concatenation")
    void theMergedAnswerReadsAsTheWholeFileDoes(@TempDir Path dir) throws IOException {
        Path text = dir.resolve("document.md");
        // Normalised to the line ends the sidecar was sent, whatever a checkout did to them.
        Files.writeString(text, fixture("document.md").replace("\r\n", "\n"), StandardCharsets.UTF_8);
        DoclingResponse whole = answerFrom(fixture("whole.json"));
        List<DoclingResponse> partAnswers =
                List.of(answerFrom(fixture("part-1.json")), answerFrom(fixture("part-2.json")), answerFrom(fixture("part-3.json")));

        List<TextParts.Part> parts = TextParts.cut(text, FIXTURE_LIMIT);
        DoclingResponse merged = TextParts.merged(partAnswers);

        claim(
                "the fixture text of " + FIXTURE_BYTES + " bytes, cut at " + FIXTURE_LIMIT + " bytes, ends its parts"
                        + " at byte " + FIXTURE_FIRST_END + ", before a fenced block holding blank lines (a cut that"
                        + " ignored the fence would end it at " + FIXTURE_CUT_INSIDE_THE_FENCE + "), and at byte "
                        + FIXTURE_SECOND_END + ", a line end in a stretch with no blank line: the parts the sidecar"
                        + " was sent",
                () -> assertThat(parts.stream().map(part -> List.of(part.start(), part.end())).toList())
                        .isEqualTo(List.of(
                                List.of(0L, FIXTURE_FIRST_END),
                                List.of(FIXTURE_FIRST_END, FIXTURE_SECOND_END),
                                List.of(FIXTURE_SECOND_END, FIXTURE_BYTES))));
        claim(
                "the merged answer holds the same text items and table rows, in the same order and under the"
                        + " same labels, as the answer for the whole text",
                () -> assertThat(DoclingDocumentTexts.parse(merged.rawResponse()))
                        .isEqualTo(DoclingDocumentTexts.parse(whole.rawResponse())));
        claim(
                "so the lines the near-duplicate check reads are the same",
                () -> assertThat(DoclingDocumentTexts.lines(merged.rawResponse()))
                        .isEqualTo(DoclingDocumentTexts.lines(whole.rawResponse())));
        claim(
                "the text and page count the metrics are taken from are the same",
                () -> {
                    ExtractedText fromMerged = ExtractedText.from(merged.rawResponse());
                    ExtractedText fromWhole = ExtractedText.from(whole.rawResponse());
                    assertThat(fromMerged.text()).isEqualTo(fromWhole.text());
                    assertThat(fromMerged.pageCount()).isEqualTo(fromWhole.pageCount());
                });
        ExtractionMetrics metrics = new ExtractionMetrics(jdbcTemplate, new LanguageDetection());
        claim(
                "the metric row is the same in every column but the conversion time, which is the parts' added up",
                () -> {
                    assertThat(metrics.measure(merged).metric())
                            .usingRecursiveComparison()
                            .ignoringFields("processingTimeSeconds")
                            .isEqualTo(metrics.measure(whole).metric());
                    assertThat(merged.processingTimeSeconds()).isEqualTo(partAnswers.stream()
                            .mapToDouble(DoclingResponse::processingTimeSeconds)
                            .sum());
                });
        HybridChunker chunker = new HybridChunker(new ChunkCache(jdbcTemplate));
        claim(
                "and the chunks are the same",
                () -> assertThat(chunker.chunk(merged.rawResponse(), "0".repeat(63) + "1", ChunkingRule.DEFAULT))
                        .isEqualTo(chunker.chunk(whole.rawResponse(), "0".repeat(63) + "2", ChunkingRule.DEFAULT)));
        JsonNode mergedContent = JSON.readTree(merged.rawResponse()).path("document").path("json_content");
        JsonNode concatenated = JSON.readTree(fixture("concatenated.json"));
        claim(
                "its document's items, body, furniture and pages are exactly what docling-core's own"
                        + " concatenation makes of the same three answers",
                () -> {
                    for (String key : List.of("texts", "groups", "tables", "pictures", "body", "furniture", "pages")) {
                        assertThat(mergedContent.path(key)).as(key).isEqualTo(concatenated.path(key));
                    }
                });
        claim(
                "and every reference in it leads to the item that names itself by that reference",
                () -> assertThat(unresolvedReferences(mergedContent)).isEmpty());
        claim(
                "the merged answer is a success, as every part's was",
                () -> assertThat(merged.status()).isEqualTo(ConversionStatus.SUCCESS));
    }

    /** What the stub's Docling reports it spent on each part, in seconds. */
    private static final double PART_PROCESSING_TIME = 0.5;

    /** One file the stub was handed: where it was, its name, its bytes, and what it was posted as. */
    private record Posted(Path path, String name, byte[] bytes, DetectedFormat format, DetectedSubtype subtype) {}

    /**
     * A {@link DoclingClient} that records each file it is asked to convert and answers through {@code
     * answers}, given the file and its position among the calls, counted from 1.
     */
    private static final class RecordingClient extends DoclingClient {

        private final List<Posted> posted = new ArrayList<>();
        private final BiFunction<Path, Integer, DoclingResponse> answers;

        RecordingClient(BiFunction<Path, Integer, DoclingResponse> answers) {
            super(RestClient.create(), OCR_PRESET);
            this.answers = answers;
        }

        @Override
        DoclingResponse convert(Path file, DetectedFormat format, DetectedSubtype subtype) {
            try {
                // A whole file is not read: some are tens of megabytes, and only parts are looked into.
                byte[] bytes = file.getFileName().toString().contains(".part-") ? Files.readAllBytes(file) : new byte[0];
                posted.add(new Posted(file, file.getFileName().toString(), bytes, format, subtype));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return answers.apply(file, posted.size());
        }
    }

    /**
     * An answer for the part at {@code part}, shaped as the Markdown backend answers: one text item for
     * each non-blank line, each a child of the body.
     */
    private static DoclingResponse answerFor(Path part, ConversionStatus status, List<DoclingError> errors) {
        try {
            List<String> lines = nonBlankLines(Files.readAllBytes(part));
            ObjectNode root = document(lines);
            root.put("status", status.name().toLowerCase(Locale.ROOT));
            ArrayNode wireErrors = root.putArray("errors");
            for (DoclingError error : errors) {
                ObjectNode wire = wireErrors.addObject();
                wire.put("component_type", error.componentType());
                wire.put("module_name", error.moduleName());
                wire.put("error_message", error.errorMessage());
                wire.put("category", error.category().name().toLowerCase(Locale.ROOT));
                wire.putNull("page_no");
            }
            return new DoclingResponse(status, errors, PART_PROCESSING_TIME, null, root.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A response body whose document holds one text item per entry of {@code lines}. */
    private static ObjectNode document(List<String> lines) {
        ObjectNode root = JSON.createObjectNode();
        ObjectNode document = root.putObject("document");
        document.put("filename", "document.md");
        ObjectNode content = document.putObject("json_content");
        content.put("schema_name", "DoclingDocument");
        content.put("version", "1.10.0");
        content.put("name", "document");
        ObjectNode furniture = content.putObject("furniture");
        furniture.put("self_ref", "#/furniture");
        furniture.putArray("children");
        ObjectNode body = content.putObject("body");
        body.put("self_ref", "#/body");
        ArrayNode children = body.putArray("children");
        content.putArray("groups");
        ArrayNode texts = content.putArray("texts");
        for (int index = 0; index < lines.size(); index++) {
            String ref = "#/texts/" + index;
            children.addObject().put("$ref", ref);
            ObjectNode item = texts.addObject();
            item.put("self_ref", ref);
            item.putObject("parent").put("$ref", "#/body");
            item.putArray("children");
            item.put("label", "text");
            item.put("orig", lines.get(index));
            item.put("text", lines.get(index));
        }
        content.putArray("pictures");
        content.putArray("tables");
        content.putArray("key_value_items");
        content.putArray("form_items");
        content.putObject("pages");
        root.put("status", "success");
        root.putArray("errors");
        root.put("processing_time", PART_PROCESSING_TIME);
        root.putObject("timings");
        root.putNull("confidence");
        return root;
    }

    /** One error as Docling reports it, from its document backend. */
    private static DoclingError error(FailureCategory category, String message) {
        return new DoclingError("document_backend", "docling", message, category, null);
    }

    /** The lines of {@code bytes} that hold something other than spaces and tabs, in order. */
    private static List<String> nonBlankLines(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1).lines().filter(line -> !line.isBlank()).toList();
    }

    /** The posted parts' bytes, end to end. */
    private static byte[] joined(List<Posted> parts) {
        int total = parts.stream().mapToInt(part -> part.bytes().length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(total);
        parts.forEach(part -> buffer.put(part.bytes()));
        return buffer.array();
    }

    /** Every {@code $ref} in {@code content} that does not lead to an item whose {@code self_ref} is that reference. */
    private static List<String> unresolvedReferences(JsonNode content) {
        List<String> unresolved = new ArrayList<>();
        collectReferences(content, content, unresolved);
        return unresolved;
    }

    private static void collectReferences(JsonNode content, JsonNode node, List<String> unresolved) {
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isString()) {
                String value = ref.asString();
                String[] parts = value.split("/");
                JsonNode target = parts.length == 2 ? content.path(parts[1]) : content.path(parts[1]).path(Integer.parseInt(parts[2]));
                if (!value.equals(target.path("self_ref").asString(""))) {
                    unresolved.add(value);
                }
            }
            node.forEach(child -> collectReferences(content, child, unresolved));
        } else if (node.isArray()) {
            node.forEach(child -> collectReferences(content, child, unresolved));
        }
    }

    /** One of the recorded answers or texts under {@code text-in-parts/}. */
    private static String fixture(String name) throws IOException {
        try (InputStream in = TextInPartsTest.class.getResourceAsStream("text-in-parts/" + name)) {
            if (in == null) {
                throw new IOException("no fixture text-in-parts/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** A recorded answer as the client reads one off the wire. */
    private static DoclingResponse answerFrom(String raw) {
        WireAnswer wire = JSON.readValue(raw, WireAnswer.class);
        return new DoclingResponse(
                wire.status(), wire.errors() == null ? List.of() : wire.errors(), wire.processingTime(), wire.confidence(), raw);
    }

    /** The fields of an answer the client reads, as {@link DoclingClient} reads them. */
    private record WireAnswer(
            ConversionStatus status, List<DoclingError> errors, double processingTime, ConfidenceScores confidence) {}

    /** Line {@code index}, exactly {@link #LINE_BYTES} bytes, its line end included, and never blank. */
    private static byte[] line(long index) {
        String start = String.format(Locale.ROOT, "row %09d of the export, a line of no consequence ", index);
        return (start + "x".repeat(LINE_BYTES - 1 - start.length()) + "\n").getBytes(StandardCharsets.US_ASCII);
    }

    /** Writes whole lines of {@link #LINE_BYTES} bytes, with no blank line, to exactly {@code size} bytes. */
    private static void writeLinesOfExactly(Path file, long size) throws IOException {
        if (size % LINE_BYTES != 0) {
            throw new IllegalArgumentException("a size in whole lines of " + LINE_BYTES + " bytes");
        }
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file), 1 << 16)) {
            for (long index = 0; index < size / LINE_BYTES; index++) {
                out.write(line(index));
            }
        }
    }

    /**
     * Writes a text of exactly {@code size} bytes: paragraphs of three lines with a blank line after
     * each, for its first and last stretches, and between them a stretch of lines with no blank line,
     * longer than a part, so both ways a part can end are met.
     */
    private static void writeParagraphsWithAStretchOfLines(Path file, long size) throws IOException {
        long stretchFrom = size / 4;
        long stretchTo = stretchFrom + PART_BYTES + PART_BYTES / 4;
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file), 1 << 16)) {
            long written = 0;
            long index = 0;
            while (written < size) {
                byte[] next = line(index++);
                boolean inStretch = written >= stretchFrom && written < stretchTo;
                if (!inStretch && index % 3 == 0) {
                    next = (new String(next, StandardCharsets.US_ASCII) + "\n").getBytes(StandardCharsets.US_ASCII);
                }
                int length = (int) Math.min(next.length, size - written);
                out.write(next, 0, length);
                written += length;
            }
        }
        byte[] all = Files.readAllBytes(file);
        if (all[all.length - 1] != '\n') {
            all[all.length - 1] = '\n';
            Files.write(file, all);
        }
    }
}
