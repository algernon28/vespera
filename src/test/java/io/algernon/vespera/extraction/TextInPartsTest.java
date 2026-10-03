package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
 *
 * <p>{@link #aTextAtTheCeilingIsSentWhole} and {@link #textThatIsNotCutIsSentWhole} pin what must not
 * change: which files are sent whole, as they always were.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Converting a large text in parts")
@Issue("371")
@Link(name = "ADR-178", url = Adr.TEXT_OVER_THE_CEILING_IS_CONVERTED_IN_PARTS, type = "adr")
class TextInPartsTest {

    /** The largest part sent, in bytes: half the converter's ceiling. Written out so the test pins it. */
    private static final long PART_BYTES = 8_000_000L;

    /** The largest text file converted in parts, in bytes: eight parts. Written out so the test pins it. */
    private static final long LARGEST_TEXT_BYTES = 64_000_000L;

    /** The converter's ceiling for text sent whole, in bytes (ADR-171). */
    private static final long CEILING_BYTES = 16_000_000L;

    /** The size of the text the first test cuts: over the ceiling, and more than two parts. */
    private static final long A_TEXT_OVER_THE_CEILING_BYTES = 17_000_000L;

    /**
     * A text of lines of exactly {@link #LINE_BYTES} bytes and no blank line, this many bytes long, is
     * cut into two parts of exactly 8,000,000 bytes and a third of 100.
     */
    private static final long THREE_PART_TEXT_BYTES = 16_000_100L;

    /** The length of each line of {@link #THREE_PART_TEXT_BYTES}' text, its line end included. */
    private static final int LINE_BYTES = 100;

    /** How many parts the three-part text is cut into. */
    private static final int THREE_PARTS = 3;

    /** The rule the extractor identity names, word for word. */
    private static final String RULE = "v1,over=16000000,upto=64000000,part=8000000";

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
    @Story("A text over the ceiling is converted in parts")
    @DisplayName("A text of exactly the ceiling is sent whole, in one call")
    void aTextAtTheCeilingIsSentWhole(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("notes.txt");
        writeLinesOfExactly(file, CEILING_BYTES);
        RecordingClient client = new RecordingClient((part, number) -> aSmallSuccess());

        new DoclingExtractor(client, null).convertUncached(file, DetectedFormat.PLAIN_TEXT, null);

        claim(
                "a text of exactly 16,000,000 bytes is posted in one call, as the file itself: only a text"
                        + " over the ceiling is cut",
                () -> assertThat(client.posted).singleElement()
                        .satisfies(part -> assertThat(part.path()).isEqualTo(file)));
    }

    @Test
    @Story("A text over the ceiling is converted in parts")
    @DisplayName("HTML, CSV and AsciiDoc, text in UTF-16 or UTF-32, and text over the largest size cut are sent whole")
    void textThatIsNotCutIsSentWhole(@TempDir Path dir) throws IOException {
        Path csv = dir.resolve("terminals.csv");
        writeLinesOfExactly(csv, THREE_PART_TEXT_BYTES);
        Path wide = dir.resolve("wide.txt");
        writeWideLinesOfExactly(wide, THREE_PART_TEXT_BYTES, UTF_16LE_MARK, StandardCharsets.UTF_16LE);
        Path wider = dir.resolve("wider.txt");
        writeWideLinesOfExactly(wider, THREE_PART_TEXT_BYTES, UTF_32BE_MARK, java.nio.charset.Charset.forName("UTF-32BE"));
        Path huge = dir.resolve("dump.txt");
        writeLinesOfExactly(huge, LARGEST_TEXT_BYTES + LINE_BYTES);

        java.util.Map<DetectedSubtype, String> kinds = java.util.Map.of(
                DetectedSubtype.CSV, "a CSV file", DetectedSubtype.HTML, "an HTML file", DetectedSubtype.ASCIIDOC, "an AsciiDoc file");
        for (DetectedSubtype structured : List.of(DetectedSubtype.CSV, DetectedSubtype.HTML, DetectedSubtype.ASCIIDOC)) {
            RecordingClient client = new RecordingClient((part, number) -> aSmallSuccess());
            new DoclingExtractor(client, null).convertUncached(csv, DetectedFormat.PLAIN_TEXT, structured);
            claim(
                    kinds.get(structured) + " over the ceiling is posted whole, in one call: a part of one would"
                            + " not read as the file does",
                    () -> assertThat(client.posted).singleElement()
                            .satisfies(part -> assertThat(part.path()).isEqualTo(csv)));
        }
        RecordingClient wideClient = new RecordingClient((part, number) -> aSmallSuccess());
        new DoclingExtractor(wideClient, null).convertUncached(wide, DetectedFormat.PLAIN_TEXT, null);
        claim(
                "a text in UTF-16 over the ceiling is posted whole, since a line end there is two bytes and a"
                        + " later part would lack the byte-order mark",
                () -> assertThat(wideClient.posted).singleElement()
                        .satisfies(part -> assertThat(part.path()).isEqualTo(wide)));
        RecordingClient widerClient = new RecordingClient((part, number) -> aSmallSuccess());
        new DoclingExtractor(widerClient, null).convertUncached(wider, DetectedFormat.PLAIN_TEXT, null);
        claim(
                "so is a text in UTF-32, big-endian behind its four-byte mark, where a line end is four bytes",
                () -> assertThat(widerClient.posted).singleElement()
                        .satisfies(part -> assertThat(part.path()).isEqualTo(wider)));
        RecordingClient hugeClient = new RecordingClient((part, number) -> aSmallSuccess());
        new DoclingExtractor(hugeClient, null).convertUncached(huge, DetectedFormat.PLAIN_TEXT, null);
        claim(
                "and a text over 64,000,000 bytes is posted whole: stage 1 leaves such a file of the corpus out,"
                        + " so only a seed arrives here, and a seed is sent as it always was",
                () -> assertThat(hugeClient.posted).singleElement()
                        .satisfies(part -> assertThat(part.path()).isEqualTo(huge)));
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
    @Story("One part that does not convert fails the whole file")
    @DisplayName("A part that fails without saying why is still named in the file's answer")
    void aFailingPartThatReportsNoErrorIsNamedToo(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("export.txt");
        writeLinesOfExactly(file, THREE_PART_TEXT_BYTES);
        RecordingClient client = new RecordingClient((part, number) -> number == 2
                ? answerFor(part, ConversionStatus.FAILURE, List.of())
                : answerFor(part, ConversionStatus.SUCCESS, List.of()));

        DoclingResponse answer = new DoclingExtractor(client, null).convertUncached(file, DetectedFormat.PLAIN_TEXT, null);

        claim(
                "the answer carries one uncategorised error, supplied so that the reason can name the part",
                () -> assertThat(answer.errors()).singleElement().satisfies(only -> {
                    assertThat(only.category()).isEqualTo(FailureCategory.UNKNOWN);
                    assertThat(only.componentType()).isEqualTo("vespera");
                    assertThat(only.moduleName()).isEqualTo("text-parts");
                    assertThat(only.errorMessage()).isEqualTo(SECOND_OF_THREE + "no categorized error was reported");
                }));
        claim(
                "and is read as a failure about the document, as a failure that names no error always is",
                () -> assertThat(ResponseScope.of(answer)).isInstanceOf(ResponseScope.DocumentScope.class));
    }

    @Test
    @Story("One part that does not convert fails the whole file")
    @DisplayName("A part the converter turned down for want of room leaves nothing in the cache")
    void aPartTheConverterRefusedForItselfIsNotKept(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("export.txt");
        writeLinesOfExactly(file, THREE_PART_TEXT_BYTES);
        RecordingClient client = new RecordingClient((part, number) -> number == 2
                ? answerFor(part, ConversionStatus.FAILURE, List.of(error(FailureCategory.CAPACITY, "no room for it")))
                : answerFor(part, ConversionStatus.SUCCESS, List.of()));
        DoclingExtractor extractor = new DoclingExtractor(client, new ExtractionCache(jdbcTemplate));
        ExtractorIdentity identity = new ExtractorIdentity("docling-serve;" + DoclingClient.sentOptions());
        String contentHash = extractor.contentHashFor(file);

        DoclingResponse answer = extractor.convertUncached(file, DetectedFormat.PLAIN_TEXT, null);
        extractor.remember(contentHash, identity, answer);

        claim(
                "the file's answer is read as the converter blaming itself, as the part's own answer was",
                () -> assertThat(ResponseScope.of(answer)).isInstanceOf(ResponseScope.ServiceScope.class));
        claim(
                "so it is not kept, and the next read of the same file asks the converter again",
                () -> assertThat(extractor.cached(contentHash, identity)).isEmpty());
    }

    @Test
    @Story("One part that does not convert fails the whole file")
    @DisplayName("A part that gets no answer in time ends the file with the same timeout, naming the part")
    void aPartThatTimesOutEndsTheFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("export.txt");
        writeLinesOfExactly(file, THREE_PART_TEXT_BYTES);
        RecordingClient client = new RecordingClient((part, number) -> {
            if (number == 2) {
                throw new DoclingCallTimeoutException(part, new java.net.http.HttpTimeoutException("no answer"));
            }
            return answerFor(part, ConversionStatus.SUCCESS, List.of());
        });
        DoclingExtractor extractor = new DoclingExtractor(client, null);

        claim(
                "the caller meets the timeout itself, unwrapped, so stage 2 decides the file as it decides any"
                        + " file that timed out, and its message names the part",
                () -> assertThatThrownBy(() -> extractor.convertUncached(file, DetectedFormat.PLAIN_TEXT, null))
                        .isInstanceOf(DoclingCallTimeoutException.class)
                        .hasMessageContaining("export.txt.part-2-of-3"));
        claim(
                "the third part is never sent",
                () -> assertThat(client.posted).hasSize(2));
        claim(
                "and no part's file is left behind",
                () -> assertThat(client.posted).allSatisfy(part -> assertThat(part.path()).doesNotExist()));
    }

    @Test
    @Story("A text over the ceiling is converted in parts")
    @DisplayName("A part converted with something missing makes the file's answer a partial conversion")
    void aPartialPartMakesAPartialAnswer(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("export.txt");
        writeLinesOfExactly(file, THREE_PART_TEXT_BYTES);
        String missing = "one stretch was not read";
        RecordingClient client = new RecordingClient((part, number) -> number == 1
                ? answerFor(part, ConversionStatus.PARTIAL_SUCCESS, List.of(error(FailureCategory.BACKEND_FAILURE, missing)))
                : answerFor(part, ConversionStatus.SUCCESS, List.of()));

        DoclingResponse answer = new DoclingExtractor(client, null).convertUncached(file, DetectedFormat.PLAIN_TEXT, null);

        claim(
                "every part is sent, since a partial conversion is a conversion",
                () -> assertThat(client.posted).hasSize(THREE_PARTS));
        claim(
                "and the file's answer is a partial conversion, carrying that part's error after its name",
                () -> {
                    assertThat(answer.status()).isEqualTo(ConversionStatus.PARTIAL_SUCCESS);
                    assertThat(answer.errors()).singleElement().satisfies(only -> assertThat(only.errorMessage())
                            .isEqualTo("part 1 of 3 (bytes 1 to 8,000,000): " + missing));
                    assertThat(ResponseScope.of(answer)).isInstanceOf(ResponseScope.Conversion.class);
                });
    }

    @Test
    @Story("One part that does not convert fails the whole file")
    @DisplayName("A text with a line longer than a part is not sent, and fails as a text that cannot be cut")
    void aLineLongerThanAPartIsNotSent(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("dump.json");
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file), 1 << 16)) {
            byte[] run = new byte[1 << 16];
            Arrays.fill(run, (byte) 'x');
            long written = 0;
            while (written < PART_BYTES + 1) {
                int length = (int) Math.min(run.length, PART_BYTES + 1 - written);
                out.write(run, 0, length);
                written += length;
            }
            out.write('\n');
            written++;
            byte[] line = line(0);
            while (written + line.length <= THREE_PART_TEXT_BYTES) {
                out.write(line);
                written += line.length;
            }
        }
        RecordingClient client = new RecordingClient((part, number) -> aSmallSuccess());
        DoclingExtractor extractor = new DoclingExtractor(client, new ExtractionCache(jdbcTemplate));
        ExtractorIdentity identity = new ExtractorIdentity("docling-serve;" + DoclingClient.sentOptions());
        String contentHash = extractor.contentHashFor(file);

        DoclingResponse answer = extractor.convertUncached(file, DetectedFormat.PLAIN_TEXT, null);
        extractor.remember(contentHash, identity, answer);

        claim(
                "nothing is sent: sending the file whole is the call that holds a converter worker long after"
                        + " it has given up",
                () -> assertThat(client.posted).isEmpty());
        claim(
                "the answer is a failure saying the text cannot be cut, and where the line that stops it begins",
                () -> {
                    assertThat(answer.status()).isEqualTo(ConversionStatus.FAILURE);
                    assertThat(answer.errors()).singleElement().satisfies(only -> {
                        assertThat(only.category()).isEqualTo(FailureCategory.UNKNOWN);
                        assertThat(only.errorMessage()).isEqualTo(
                                "the text cannot be cut into parts: the line beginning at byte 1 is longer than"
                                        + " 8,000,000 bytes");
                    });
                    assertThat(answer.processingTimeSeconds()).isZero();
                });
        claim(
                "it is read as a failure about the document, so stage 2 removes the file",
                () -> assertThat(ResponseScope.of(answer)).isInstanceOf(ResponseScope.DocumentScope.class));
        claim(
                "and it is kept: the next read of the same file finds this answer and asks the converter nothing",
                () -> assertThat(extractor.cached(contentHash, identity)).isPresent());
    }

    @Test
    @Story("One part that does not convert fails the whole file")
    @DisplayName("A part that says it converted but carries no document fails the file, and no later part is sent")
    void aPartWithNoDocumentEndsTheFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("export.txt");
        writeLinesOfExactly(file, THREE_PART_TEXT_BYTES);
        RecordingClient client = new RecordingClient((part, number) -> number == 2
                ? aSuccessWithNoDocument()
                : answerFor(part, ConversionStatus.SUCCESS, List.of()));

        DoclingResponse answer = new DoclingExtractor(client, null).convertUncached(file, DetectedFormat.PLAIN_TEXT, null);

        claim(
                "the second of the three parts is the last one sent: a part with nothing to merge ends the file",
                () -> assertThat(client.posted).hasSize(2));
        claim(
                "the file's answer is a failure, with one uncategorised error naming the part and what was missing",
                () -> {
                    assertThat(answer.status()).isEqualTo(ConversionStatus.FAILURE);
                    assertThat(answer.errors()).singleElement().satisfies(only -> {
                        assertThat(only.category()).isEqualTo(FailureCategory.UNKNOWN);
                        assertThat(only.componentType()).isEqualTo("vespera");
                        assertThat(only.moduleName()).isEqualTo("text-parts");
                        assertThat(only.errorMessage()).isEqualTo(SECOND_OF_THREE + "the converter answered with no document");
                    });
                });
        claim(
                "and it is read as a failure about the document, so stage 2 removes the file",
                () -> assertThat(ResponseScope.of(answer)).isInstanceOf(ResponseScope.DocumentScope.class));
        claim(
                "and no part's file is left behind",
                () -> assertThat(client.posted).allSatisfy(part -> assertThat(part.path()).doesNotExist()));
    }

    @Test
    @Story("Where a part ends")
    @DisplayName("A part ends after the last blank line in the back half of its window, never inside a fenced block")
    void cutsAfterABlankLineOutsideAFence(@TempDir Path dir) throws IOException {
        String opening = "first paragraph line\nfirst paragraph line\nfirst paragraph line\n\n";
        String fenced = "```\ncode line\n\ncode line\n```\n";
        String text = opening + fenced + "after the fence\n".repeat(5);
        long blankInsideTheFence = opening.length() + "```\ncode line\n\n".length();
        long limit = blankInsideTheFence + 6;
        Path file = Files.writeString(dir.resolve("fenced.md"), text, StandardCharsets.UTF_8);

        List<TextParts.Part> parts = TextParts.cut(file, limit);

        claim(
                "the first part ends after the blank line before the fence, at byte " + opening.length()
                        + ", not after the later blank line inside the fence, which also lies in the window",
                () -> assertThat(parts.getFirst().end()).isEqualTo(opening.length()));
        claim(
                "and the parts cover the text end to end, each no longer than the limit",
                () -> assertCovers(parts, text.length(), limit));
    }

    @Test
    @Story("Where a part ends")
    @DisplayName("Where the back half of the window has no blank line, a part ends at its last line end")
    void cutsAtALineEndWhereTheBackHalfHasNoBlankLine(@TempDir Path dir) throws IOException {
        String opening = "intro\n\n";
        String record = "record line 00\n";
        String text = opening + record.repeat(20);
        long limit = 100;
        long lastLineEndInTheWindow = opening.length() + ((limit - opening.length()) / record.length()) * record.length();
        Path file = Files.writeString(dir.resolve("records.txt"), text, StandardCharsets.UTF_8);

        List<TextParts.Part> parts = TextParts.cut(file, limit);

        claim(
                "the only blank line lies before the window's midpoint, so the first part ends at the last line"
                        + " end in the window, byte " + lastLineEndInTheWindow,
                () -> assertThat(parts.getFirst().end()).isEqualTo(lastLineEndInTheWindow));
        claim(
                "and the parts cover the text end to end, each no longer than the limit",
                () -> assertCovers(parts, text.length(), limit));
    }

    @Test
    @Story("Where a part ends")
    @DisplayName("A lone carriage return ends a line, and a part never ends between a carriage return and its line feed")
    void aLoneCarriageReturnEndsALine(@TempDir Path dir) throws IOException {
        Path classic = Files.writeString(dir.resolve("classic.txt"), "line one\rline two\rline three\r", StandardCharsets.UTF_8);
        Path windows = Files.writeString(dir.resolve("windows.txt"), "abc\r\nabc\r\nabc\r\n", StandardCharsets.UTF_8);
        long classicLimit = "line one\rline t".length();
        long windowsLimit = "abc\r\nabc\r".length();

        List<TextParts.Part> classicParts = TextParts.cut(classic, classicLimit);
        List<TextParts.Part> windowsParts = TextParts.cut(windows, windowsLimit);

        claim(
                "a text whose lines end in a carriage return alone is cut just after one",
                () -> assertThat(classicParts.getFirst().end()).isEqualTo("line one\r".length()));
        claim(
                "and a text whose lines end in a carriage return and a line feed is never cut between the two,"
                        + " even where the window ends there",
                () -> assertThat(windowsParts.getFirst().end()).isEqualTo("abc\r\n".length()));
    }

    @Test
    @Story("Where a part ends")
    @DisplayName("Every part of a UTF-8 text decodes on its own, because no part ends inside a character")
    void neverCutsInsideAUtf8Sequence(@TempDir Path dir) throws IOException {
        String text = "è più già perché così\n".repeat(40);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Path file = Files.write(dir.resolve("accents.txt"), bytes);
        long limit = 97;

        List<TextParts.Part> parts = TextParts.cut(file, limit);

        claim(
                "each part, decoded strictly as UTF-8 on its own, is whole lines of the text",
                () -> assertThat(parts).allSatisfy(part -> assertThat(strictUtf8(
                                Arrays.copyOfRange(bytes, (int) part.start(), (int) part.end())))
                        .endsWith("\n")));
        claim(
                "and the parts cover the text end to end, each no longer than the limit",
                () -> assertCovers(parts, bytes.length, limit));
    }

    @Test
    @Story("Where a part ends")
    @DisplayName("Whether a line is inside a fenced block is known from the start of the file, not of the part")
    void aFenceIsTrackedFromTheStartOfTheFileNotOfThePart(@TempDir Path dir) throws IOException {
        // A fence opens the file and runs past the first window with no blank line in it, so the first
        // part ends at a line end inside the fence. In the second window, past its midpoint, a blank line
        // is still inside the fence; the fence then closes and a line end follows.
        String opening = "```\n" + "code line\n".repeat(8);
        String blankInsideTheFence = "\n";
        String rest = "code line\n" + "```\n" + "after fence\n".repeat(2);
        String text = opening + blankInsideTheFence + rest;
        long limit = 60;
        long firstEnd = "```\n".length() + 5 * "code line\n".length();
        long afterTheBlankLine = opening.length() + blankInsideTheFence.length();
        long secondEnd = afterTheBlankLine + "code line\n".length() + "```\n".length() + "after fence\n".length();
        Path file = Files.writeString(dir.resolve("long-fence.md"), text, StandardCharsets.UTF_8);

        List<TextParts.Part> parts = TextParts.cut(file, limit);

        claim(
                "the first part ends at byte " + firstEnd + ", the last line end in its window, inside the fence",
                () -> assertThat(parts.getFirst().end()).isEqualTo(firstEnd));
        claim(
                "the blank line ending at byte " + afterTheBlankLine + " ends past the second window's midpoint, at byte "
                        + (firstEnd + limit / 2) + ", so only the fence keeps a part from ending after it",
                () -> assertThat(afterTheBlankLine).isGreaterThanOrEqualTo(firstEnd + limit / 2));
        claim(
                "the second part ends at byte " + secondEnd + ", the last line end in its window, and not after"
                        + " that blank line: the second part began inside the fence, and the blank line is in it",
                () -> assertThat(parts.get(1).end()).isEqualTo(secondEnd).isNotEqualTo(afterTheBlankLine));
        claim(
                "and the parts cover the text end to end, each no longer than the limit",
                () -> assertCovers(parts, text.length(), limit));
    }

    @Test
    @Story("The answers are merged into one document")
    @DisplayName("Merged pages follow on from the part before, and the merged confidence is the lowest any part reports")
    void theMergeShiftsPagesAndKeepsTheLowestConfidence() {
        DoclingResponse first = pagedAnswer(
                List.of(1, 2),
                2,
                new ConfidenceScores(0.9, null, null, null, null, null, QualityGrade.EXCELLENT, QualityGrade.UNSPECIFIED));
        DoclingResponse second = pagedAnswer(
                List.of(1),
                1,
                new ConfidenceScores(0.6, null, null, null, null, null, QualityGrade.UNSPECIFIED, QualityGrade.UNSPECIFIED));

        DoclingResponse merged = TextParts.merged(List.of(first, second));

        JsonNode root = JSON.readTree(merged.rawResponse());
        JsonNode content = root.path("document").path("json_content");
        JsonNode pages = content.path("pages");
        claim(
                "the second part's page 1 follows the first part's pages 1 and 2, as page 3",
                () -> assertThat(List.copyOf(pages.propertyNames())).containsExactlyInAnyOrder("1", "2", "3"));
        claim(
                "and every page says the number it is filed under",
                () -> {
                    for (String key : List.of("1", "2", "3")) {
                        assertThat(pages.path(key).path("page_no").asInt()).as("page " + key).isEqualTo(Integer.parseInt(key));
                    }
                });
        claim(
                "the first part's text stays on page 2, and the second part's text moves with its page to page 3",
                () -> {
                    assertThat(content.path("texts").path(0).path("prov").path(0).path("page_no").asInt()).isEqualTo(2);
                    assertThat(content.path("texts").path(1).path("prov").path(0).path("page_no").asInt()).isEqualTo(3);
                });
        claim(
                "the merged score is the lower of the two parts', and a score neither part reports stays unreported",
                () -> {
                    assertThat(merged.confidence().parseScore()).isEqualTo(0.6);
                    assertThat(merged.confidence().layoutScore()).isNull();
                });
        claim(
                "a grade is the worst any part scored, and one part's unspecified does not count against the"
                        + " other's excellent: a grade is unspecified only where both parts say so",
                () -> {
                    assertThat(merged.confidence().meanGrade()).isEqualTo(QualityGrade.EXCELLENT);
                    assertThat(merged.confidence().lowGrade()).isEqualTo(QualityGrade.UNSPECIFIED);
                });
        claim(
                "and the body kept in the cache says the same as the answer's own confidence",
                () -> {
                    JsonNode confidence = root.path("confidence");
                    assertThat(confidence.path("parse_score").asDouble()).isEqualTo(merged.confidence().parseScore());
                    assertThat(confidence.path("layout_score").isNull()).isTrue();
                    assertThat(confidence.path("mean_grade").asString()).isEqualTo(merged.confidence().meanGrade().toWire());
                    assertThat(confidence.path("low_grade").asString()).isEqualTo(merged.confidence().lowGrade().toWire());
                });
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
        HybridChunker chunker = new HybridChunker(new ChunkCache(jdbcTemplate), new WindowedStructurelessChunkingFallback());
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

    @Test
    @Story("The answers are merged into one document")
    @DisplayName("The rule for cutting text joins the options every conversion is identified by")
    void theRuleIsPartOfTheExtractorIdentity() {
        claim(
                "the options the extraction cache is keyed by end with the rule: its version, the ceiling a text"
                        + " is cut over, the largest text cut, and the largest part -- so a change to any of them"
                        + " keys new answers rather than serving ones cut another way",
                () -> assertThat(DoclingClient.sentOptions()).endsWith(";text_parts=" + RULE));
        claim(
                "and the rule is composed from the sizes it names, 8,000,000 bytes a part and 64,000,000 bytes"
                        + " at most",
                () -> {
                    assertThat(TextParts.PART_BYTES).isEqualTo(PART_BYTES);
                    assertThat(TextParts.LARGEST_TEXT_BYTES).isEqualTo(LARGEST_TEXT_BYTES);
                    assertThat(TextParts.RULE).isEqualTo(RULE);
                });
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
            super(RestClient.create());
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

    /** A small success, for a file the test only needs to see posted. */
    private static DoclingResponse aSmallSuccess() {
        return new DoclingResponse(ConversionStatus.SUCCESS, List.of(), PART_PROCESSING_TIME, null, document(List.of()).toString());
    }

    /** An answer that says it converted and carries no document under {@code document.json_content}. */
    private static DoclingResponse aSuccessWithNoDocument() {
        ObjectNode root = document(List.of());
        ((ObjectNode) root.get("document")).putNull("json_content");
        return new DoclingResponse(ConversionStatus.SUCCESS, List.of(), PART_PROCESSING_TIME, null, root.toString());
    }

    /**
     * A success whose document has the pages numbered {@code pageNumbers}, each saying its own number,
     * and one text on page {@code textPage}, with {@code confidence} in the record and in the body alike.
     */
    private static DoclingResponse pagedAnswer(List<Integer> pageNumbers, int textPage, ConfidenceScores confidence) {
        ObjectNode root = document(List.of("a line on page " + textPage));
        ObjectNode content = (ObjectNode) root.path("document").path("json_content");
        ObjectNode pages = content.putObject("pages");
        for (int number : pageNumbers) {
            ObjectNode page = pages.putObject(String.valueOf(number));
            page.put("page_no", number);
            page.putObject("size").put("width", 595.0).put("height", 842.0);
        }
        ObjectNode provenance = ((ObjectNode) content.path("texts").path(0)).putArray("prov").addObject();
        provenance.put("page_no", textPage);
        provenance.putArray("charspan").add(0).add(1);
        ObjectNode wire = root.putObject("confidence");
        putScore(wire, "parse_score", confidence.parseScore());
        putScore(wire, "layout_score", confidence.layoutScore());
        putScore(wire, "table_score", confidence.tableScore());
        putScore(wire, "ocr_score", confidence.ocrScore());
        putScore(wire, "mean_score", confidence.meanScore());
        putScore(wire, "low_score", confidence.lowScore());
        wire.put("mean_grade", confidence.meanGrade().toWire());
        wire.put("low_grade", confidence.lowGrade().toWire());
        return new DoclingResponse(ConversionStatus.SUCCESS, List.of(), PART_PROCESSING_TIME, confidence, root.toString());
    }

    private static void putScore(ObjectNode node, String name, Double score) {
        if (score == null) {
            node.putNull(name);
        } else {
            node.put(name, score);
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

    /** That {@code parts} start at 0, each where the last ended, end at {@code length}, and none is over {@code limit}. */
    private static void assertCovers(List<TextParts.Part> parts, long length, long limit) {
        long expectedStart = 0;
        for (TextParts.Part part : parts) {
            assertThat(part.start()).isEqualTo(expectedStart);
            assertThat(part.end() - part.start()).isPositive().isLessThanOrEqualTo(limit);
            expectedStart = part.end();
        }
        assertThat(expectedStart).isEqualTo(length);
    }

    private static String strictUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new AssertionError("a part does not decode as UTF-8 on its own", e);
        }
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

    /** The byte-order mark of UTF-16, little-endian. */
    private static final byte[] UTF_16LE_MARK = {(byte) 0xFF, (byte) 0xFE};

    /** The byte-order mark of UTF-32, big-endian. */
    private static final byte[] UTF_32BE_MARK = {0, 0, (byte) 0xFE, (byte) 0xFF};

    /**
     * Writes a text in {@code charset} behind its byte-order {@code mark}, of exactly {@code size} bytes,
     * padded with spaces; {@code size} less the mark is a whole number of that charset's spaces.
     */
    private static void writeWideLinesOfExactly(Path file, long size, byte[] mark, java.nio.charset.Charset charset)
            throws IOException {
        byte[] space = " ".getBytes(charset);
        if ((size - mark.length) % space.length != 0) {
            throw new IllegalArgumentException("a size the mark and whole " + charset + " spaces fill");
        }
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file), 1 << 16)) {
            out.write(mark);
            long written = mark.length;
            byte[] row = "a line of a wide text\n".getBytes(charset);
            while (written + row.length <= size) {
                out.write(row);
                written += row.length;
            }
            for (; written < size; written += space.length) {
                out.write(space);
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
