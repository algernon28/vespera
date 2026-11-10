package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.AnomalyLog;
import io.algernon.vespera.engine.corpus.ContentIdentity;
import io.algernon.vespera.engine.corpus.DetectedFormats;
import io.algernon.vespera.engine.corpus.WalkRecorder;
import io.algernon.vespera.engine.ledger.ImplementationVersions;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.WalkId;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stage 1 keeps code apart in its first pass (ADR-277): a text file, or a file of no known kind whose bytes read as
 * text, whose name ends with an ending the profile's {@code codeNameEndings} lists, or that carries the mark of a
 * documentation generator stage 1 knows on one of its reference pages, earns the verdict {@code code}, is never
 * hashed, and is counted on the format-mix page by ending and by generator.
 *
 * <p>The profile is written as YAML through {@link CodeNameEndingsInProfile}, so this compiles before the key exists;
 * until it does, {@code ProfileStore} refuses the file and every test here that lists an ending fails there. The
 * tests with no profile fail on what stage 1 records and writes. The verdict is read back by its stored name,
 * {@code CODE}, so nothing here names a value {@code VerdictKind} does not hold yet.
 *
 * <p>Every file is built here, a few bytes or a page of markup made in the shape the research record found each
 * generator writing ({@code docs/research/documentation-generator-markers.md}). Each kept file has a length of its
 * own unless two are meant to be identical, so what is kept is never hashed against what is not.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Byte-level reduction")
@Feature("Code")
@Issue("558")
@Link(name = "ADR-277", url = Adr.CODE_IS_RECOGNISED_BY_A_NAME_ENDING_OR_A_GENERATORS_MARK, type = "adr")
class CodeTaskletTest {

    /** Why each profile here was written, as the operator would say it in {@code provenance}. */
    private static final String SET_BY_THIS_TEST = "set by this test";

    /** The verdict's stored name. */
    private static final String CODE = "CODE";

    /** The reason a file kept as code for the ending of its name carries, with the ending to fill in (§7). */
    private static final String ENDING_REASON =
            "a file whose name ends with %s, an ending codeNameEndings in profile.yaml lists as code";

    /** The page's labels for its counts of code (§10). */
    private static final String KEPT_AS_CODE = "Files kept as code, and not read any further";

    private static final String BY_ENDING = "kept for the ending of their name";

    private static final String BY_MARK = "kept for the mark of a documentation generator";

    /** Sixty-four kilobytes: the two windows a file is read in, at its start and at its end (§1, §3). */
    private static final int WINDOW = 64 * 1024;

    /** A size over two windows, so that a stretch in the middle is read by neither. */
    private static final int OVER_TWO_WINDOWS = 200_000;

    /** Where in such a file the stretch read by neither window lies. */
    private static final int BETWEEN_THE_WINDOWS = 100_000;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("A file whose name ends as listed is kept as code")
    @DisplayName("Text and single-byte text whose names end as listed are kept as code, never hashed, with a reason, a log line and the endings recorded; other text is kept as it was")
    void keepsTextAndSingleByteTextWhoseNameEndsAsListedAsCode(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        CodeNameEndingsInProfile.write(profileStore, ".java .C", SET_BY_THIS_TEST);
        String javaText = "public class Main {\n    // the entry point\n    public static void main(String[] a) {}\n}\n";
        Files.writeString(root.resolve("Main.java"), javaText);
        Files.createDirectory(root.resolve("copia"));
        Files.writeString(root.resolve("copia/Main.java"), javaText);
        Files.write(root.resolve("util.c"), singleByteText("/* perchè non funziona */\nint main(void) { return 0; }\n"));
        Files.write(root.resolve("UTIL2.C"), "int f(void)\t{\f return 1; }\r\n".getBytes(StandardCharsets.US_ASCII));
        Files.writeString(root.resolve("note.txt"), "a note that is a document and no code at all\n");
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        List<String> logged = stageOneLogging(ledger, root, workingDirectory);

        claim(
                "each file whose name ends with a listed ending, in either case, earns code, with a reason naming the"
                        + " ending as its own name spells it",
                () -> {
                    assertThat(verdictReasonsFor(ledger, walkId, "Main.java")).containsExactly(ENDING_REASON.formatted(".java"));
                    assertThat(verdictReasonsFor(ledger, walkId, "copia/Main.java")).containsExactly(ENDING_REASON.formatted(".java"));
                    assertThat(verdictReasonsFor(ledger, walkId, "util.c")).containsExactly(ENDING_REASON.formatted(".c"));
                    assertThat(verdictReasonsFor(ledger, walkId, "UTIL2.C")).containsExactly(ENDING_REASON.formatted(".C"));
                    assertThat(List.of(
                                    verdictKindsFor(ledger, walkId, "Main.java"),
                                    verdictKindsFor(ledger, walkId, "copia/Main.java"),
                                    verdictKindsFor(ledger, walkId, "util.c"),
                                    verdictKindsFor(ledger, walkId, "UTIL2.C")))
                            .containsOnly(List.of(CODE));
                });
        claim(
                "a text file whose name ends otherwise carries no verdict",
                () -> assertThat(verdictKindsFor(ledger, walkId, "note.txt")).isEmpty());
        claim(
                "what each was found to be is still what its bytes said: text, and of no known kind for the one in a"
                        + " single-byte encoding, which reads as text and is code all the same",
                () -> assertThat(List.of(
                                storedFormatOf(ledger, walkId, "Main.java"),
                                storedFormatOf(ledger, walkId, "util.c"),
                                storedFormatOf(ledger, walkId, "UTIL2.C")))
                        .containsExactly("PLAIN_TEXT", "UNRECOGNISED", "PLAIN_TEXT"));
        claim(
                "the two identical files kept as code were never hashed, and neither supersedes the other: code"
                        + " never reaches the pass that resolves content identity",
                () -> {
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM content_hash", Integer.class)).isZero();
                    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM superseded_by", Integer.class)).isZero();
                });
        claim(
                "stage 1's log says of each, by its occurrence, that it was checked and is code, and why",
                () -> assertThat(logged).contains(
                        damageCheckLine(ledger, walkId, "Main.java", ENDING_REASON.formatted(".java")),
                        damageCheckLine(ledger, walkId, "copia/Main.java", ENDING_REASON.formatted(".java")),
                        damageCheckLine(ledger, walkId, "util.c", ENDING_REASON.formatted(".c")),
                        damageCheckLine(ledger, walkId, "UTIL2.C", ENDING_REASON.formatted(".C"))));
        claim(
                "the settings stage 1 records carry the listed endings once each, in lower case and in order, and the"
                        + " version of the generators' marks",
                () -> assertThat(configConsumed())
                        .contains("\"codeNameEndings\":[\".c\",\".java\"]")
                        .contains("\"generatorMarks\":\"v1\""));
        String html = formatMix(workingDirectory);
        claim(
                "the page counts the four kept as code, all four for the ending of their name, none out of scope",
                () -> assertThat(List.of(
                                countIn(html, KEPT_AS_CODE),
                                countIn(html, BY_ENDING),
                                countIn(html, BY_MARK),
                                countIn(html, "left out as out of scope")))
                        .containsExactly(4, 4, 0, 0));
        claim(
                "and says which endings the profile lists as code, in the form the run used",
                () -> assertThat(html).contains("codeNameEndings in profile.yaml lists these name endings: .c, .java"));
        claim(
                "the key's measurement in the profile points at the page, as the out-of-scope endings' does",
                () -> assertThat(String.valueOf(CodeNameEndingsInProfile.measurementSource(profileStore)))
                        .endsWith(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME));
    }

    @Test
    @Story("A file whose name ends as listed is kept as code")
    @DisplayName("A listed ending never makes code of a binary file, a PDF, an empty file, or text with a control byte in either window; a control byte between the windows is not read")
    void aListedEndingNeverMakesCodeOfBytesThatDoNotReadAsText(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        CodeNameEndingsInProfile.write(new ProfileStore(workingDirectory), ".java .c .dat", SET_BY_THIS_TEST);
        byte[] binary = new byte[64];
        for (int i = 0; i < binary.length; i++) {
            binary[i] = (byte) i;
        }
        Files.write(root.resolve("firmware.c"), binary);
        Files.writeString(root.resolve("contratto.java"), "%PDF-1.4\n1 0 obj\n<< >>\nendobj\ntrailer\n<< >>\n%%EOF\n");
        Files.write(root.resolve("vuoto.java"), new byte[0]);
        Files.write(root.resolve("tail.c"), textWithAByteAt(OVER_TWO_WINDOWS, OVER_TWO_WINDOWS - 1_000, (byte) 0x01));
        Files.write(root.resolve("middle.c"), textWithAByteAt(OVER_TWO_WINDOWS + 1, BETWEEN_THE_WINDOWS, (byte) 0x01));
        Files.write(root.resolve("head.c"), textWithAByteAt(OVER_TWO_WINDOWS + 2, WINDOW - 10, (byte) 0x1B));
        byte[] singleByteWithANulAtTheEnd = singleByteText("record perchè\n" + "x".repeat(300) + "\n");
        singleByteWithANulAtTheEnd[singleByteWithANulAtTheEnd.length - 2] = 0;
        Files.write(root.resolve("registro.dat"), singleByteWithANulAtTheEnd);
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        stageOne(ledger, root, workingDirectory);

        claim(
                "a binary file named with a listed ending carries no verdict: a byte of nought is not text",
                () -> assertThat(verdictKindsFor(ledger, walkId, "firmware.c")).isEmpty());
        claim(
                "a PDF named with a listed ending is a PDF and carries no verdict: the name is read only where no"
                        + " signature named the file's kind",
                () -> {
                    assertThat(storedFormatOf(ledger, walkId, "contratto.java")).isEqualTo("PDF");
                    assertThat(verdictKindsFor(ledger, walkId, "contratto.java")).isEmpty();
                });
        claim(
                "an empty file named with a listed ending stays broken, as every empty file is",
                () -> assertThat(verdictKindsFor(ledger, walkId, "vuoto.java")).containsExactly("BROKEN"));
        claim(
                "text with a control byte in its last 64 KB, text with an escape byte in its first 64 KB, and single-byte"
                        + " text with a byte of nought at its end are not code, and carry no verdict",
                () -> assertThat(List.of(
                                verdictKindsFor(ledger, walkId, "tail.c"),
                                verdictKindsFor(ledger, walkId, "head.c"),
                                verdictKindsFor(ledger, walkId, "registro.dat")))
                        .containsOnly(List.of()));
        claim(
                "text whose only control byte lies between its first and its last 64 KB is code: nothing between the"
                        + " two windows is read",
                () -> assertThat(verdictReasonsFor(ledger, walkId, "middle.c")).containsExactly(ENDING_REASON.formatted(".c")));
    }

    private void stageOne(Ledger ledger, Path root, Path workingDirectory) throws Exception {
        new ByteLevelReductionTasklet(
                        ledger,
                        new ContentIdentity(jdbcTemplate),
                        new DetectedFormats(jdbcTemplate),
                        new ImplementationVersions(),
                        new ProfileStore(workingDirectory),
                        root,
                        workingDirectory)
                .execute(null, InvocationRecordFixture.aStepOfAFreshInvocation());
    }

    /** Stage 1, with every line it logs kept, in order. */
    private List<String> stageOneLogging(Ledger ledger, Path root, Path workingDirectory) throws Exception {
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        ch.qos.logback.classic.Logger stageLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ByteLevelReductionTasklet.class);
        stageLogger.addAppender(logged);
        try {
            stageOne(ledger, root, workingDirectory);
        } finally {
            stageLogger.detachAppender(logged);
            logged.stop();
        }
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private String configConsumed() {
        return jdbcTemplate.queryForObject("SELECT config_consumed FROM run", String.class);
    }

    private static String formatMix(Path workingDirectory) throws IOException {
        return Files.readString(workingDirectory.resolve(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME));
    }

    /** Lines of ASCII prose, exactly {@code length} characters long. */
    private static String prose(int length) {
        StringBuilder text = new StringBuilder();
        int line = 0;
        while (text.length() < length) {
            text.append("<p>A line of the page, number ").append(line++).append(".</p>\n");
        }
        text.setLength(length);
        if (length > 0) {
            text.setCharAt(length - 1, '\n');
        }
        return text.toString();
    }

    /** ASCII text of exactly {@code size} bytes holding {@code b} at {@code at}. */
    private static byte[] textWithAByteAt(int size, int at, byte b) {
        byte[] bytes = prose(size).getBytes(StandardCharsets.US_ASCII);
        bytes[at] = b;
        return bytes;
    }

    /** {@code text} in ISO 8859-1, so that a letter such as {@code è} is one byte UTF-8 never follows with a space. */
    private static byte[] singleByteText(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    };

    /** The count the page prints on the line for {@code label}. */
    private static int countIn(String html, String label) {
        Matcher matcher = Pattern.compile(Pattern.quote(label) + "[^0-9]{0,80}?([0-9]+)").matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException("the page has no line for " + label + ": " + html);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private WalkRecorder walkRecorder(Ledger ledger) {
        return new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource));
    }

    /** The whole line stage 1 logs for {@code fileName} once its check for damage finds it code for {@code reason}. */
    private String damageCheckLine(Ledger ledger, WalkId walkId, String fileName, String reason) {
        return "[byte-level-reduction] checked " + occurrenceId(ledger, walkId, fileName) + " for damage -> code: " + reason;
    }

    private long occurrenceId(Ledger ledger, WalkId walkId, String fileName) {
        return ledger.occurrences().occurrenceId(walkId, new OccurrencePath(fileName)).orElseThrow().value();
    }

    private String storedFormatOf(Ledger ledger, WalkId walkId, String fileName) {
        return jdbcTemplate.queryForObject(
                "SELECT format FROM detected_format WHERE occurrence_id = ?",
                String.class,
                occurrenceId(ledger, walkId, fileName));
    }

    private List<String> verdictReasonsFor(Ledger ledger, WalkId walkId, String fileName) {
        return jdbcTemplate.query(
                "SELECT reason FROM verdict WHERE occurrence_id = ?",
                (resultSet, rowNumber) -> resultSet.getString("reason"),
                occurrenceId(ledger, walkId, fileName));
    }

    private List<String> verdictKindsFor(Ledger ledger, WalkId walkId, String fileName) {
        return jdbcTemplate.query(
                "SELECT kind FROM verdict WHERE occurrence_id = ?",
                (resultSet, rowNumber) -> resultSet.getString("kind"),
                occurrenceId(ledger, walkId, fileName));
    }
}
