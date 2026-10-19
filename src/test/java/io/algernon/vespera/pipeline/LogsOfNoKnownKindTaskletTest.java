package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stage 1's log rule reads a file of no known kind whose bytes read as text, as well as text (ADR-287 §2): a log
 * written in a single-byte encoding, with a byte that is not UTF-8 in its first 512 bytes, is detected
 * {@code UNRECOGNISED}, and is still a log. A file of no known kind holding a byte of nought does not read as text,
 * and is not read for timestamps.
 *
 * <p>The verdicts are read by their stored names and the page as text, so this compiles before the rule changes.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Byte-level reduction")
@Feature("Stage 1 step")
@Issue("575")
@Link(name = "ADR-287", url = Adr.LOGS_AND_CONFIGURATION_FILES_ARE_TOLD_BY_THEIR_BYTES, type = "adr")
class LogsOfNoKnownKindTaskletTest {

    /** Why the profile here was written, as the operator would say it in {@code provenance}. */
    private static final String SET_BY_THIS_TEST = "set by this test";

    /** A log floor of 90%, so that a file of timestamped lines is a log. */
    private static final String A_LOG_FLOOR = "0.9";

    /** More timestamped lines than the ten the log rule needs. */
    private static final int LOG_LINES = 12;

    /** A single-byte encoding in which {@code è} is one byte, {@code E8}, which does not decode as UTF-8 here. */
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    /** The stored names of the verdict and of the detected format the claims read. */
    private static final String OUT_OF_SCOPE = "OUT_OF_SCOPE";

    private static final String UNRECOGNISED = "UNRECOGNISED";

    /** The section of the page that tables the timestamped share. */
    private static final String TIMESTAMP_SECTION = "<h2>How much of each text file begins with a timestamp</h2>";

    /** The sentence above that table, which now names the files of no known kind it reads. */
    private static final String WHAT_WAS_READ = "Each text file, and each file of no known kind whose start and end read"
            + " as text, not left out for its kind or for the ending of its name, and not kept as code, was read from"
            + " its start and its end";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("A log is out of scope, told from its content")
    @DisplayName("A log written in a single-byte encoding, recognised as no known kind, is left out as a log, and one holding bytes of nought is not read")
    void aLogInASingleByteEncodingIsALog(@TempDir Path temp, @TempDir Path workingDirectory) throws Exception {
        Path root = Files.createDirectory(temp.resolve("archivio"));
        Files.write(root.resolve("registro.txt"), timestampedLines("l'importo è stato addebitato").getBytes(WINDOWS_1252));
        Files.write(root.resolve("registro-con-nulli.txt"), withANoughtOnEachLine(timestampedLines("addebito")));
        new ProfileStore(workingDirectory).save(ProfileFixture.profile()
                .logTimestampShareFloor(A_LOG_FLOOR, SET_BY_THIS_TEST)
                .build());

        stageOne(root, workingDirectory);

        claim(
                "both files are detected as of no known kind: one holds a byte that is not UTF-8 in its first 512 bytes,"
                        + " the other bytes of nought",
                () -> {
                    assertThat(storedFormatOf("registro.txt")).isEqualTo(UNRECOGNISED);
                    assertThat(storedFormatOf("registro-con-nulli.txt")).isEqualTo(UNRECOGNISED);
                });
        claim(
                "the one whose bytes read as text is left out as a log, every one of its " + LOG_LINES + " lines carrying"
                        + " a timestamp, and its detected format stays what its bytes were",
                () -> {
                    assertThat(verdictKindsFor("registro.txt")).containsExactly(OUT_OF_SCOPE);
                    assertThat(verdictReasonsFor("registro.txt")).containsExactly(
                            "a log, and logs are out of scope: 100% of the lines read from its start and end begin with a"
                                    + " timestamp");
                });
        claim(
                "the one holding bytes of nought does not read as text, so it is not read for timestamps and carries no"
                        + " verdict",
                () -> assertThat(verdictKindsFor("registro-con-nulli.txt")).isEmpty());
        String html = Files.readString(workingDirectory.resolve(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME));
        String timestamps = section(html, TIMESTAMP_SECTION);
        claim(
                "the page counts the one log among the files left out, and its table of timestamped shares counts the"
                        + " one file of no known kind it read, in the band from 90% to 100%, and not the one it did not",
                () -> {
                    assertThat(countIn(html, "Of those, logs")).isEqualTo(1);
                    assertThat(countIn(timestamps, "<td>90% to 100%</td>")).isEqualTo(1);
                });
        claim(
                "and the sentence above that table says files of no known kind whose start and end read as text are read",
                () -> assertThat(timestamps).contains(WHAT_WAS_READ));
    }

    /** Stage 1 over {@code root}, walked first, with the profile already written. */
    private void stageOne(Path root, Path workingDirectory) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource)).walk(root);
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

    /** {@link #LOG_LINES} lines, each opening with a timestamp of its own and then {@code rest}. */
    private static String timestampedLines(String rest) {
        StringBuilder log = new StringBuilder();
        for (int line = 0; line < LOG_LINES; line++) {
            log.append(String.format(Locale.ROOT, "2023-02-08 12:00:%02d INFO ", line)).append(rest).append('\n');
        }
        return log.toString();
    }

    /** {@code text} as ASCII with a byte of nought before each line break. */
    private static byte[] withANoughtOnEachLine(String text) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (char c : text.toCharArray()) {
            if (c == '\n') {
                bytes.write(0);
            }
            bytes.write(c);
        }
        return bytes.toByteArray();
    }

    private List<String> verdictKindsFor(String path) {
        return jdbcTemplate.query(
                "SELECT v.kind FROM verdict v JOIN file_occurrence o ON o.id = v.occurrence_id WHERE o.path = ?",
                (row, n) -> row.getString("kind"),
                path);
    }

    private List<String> verdictReasonsFor(String path) {
        return jdbcTemplate.query(
                "SELECT v.reason FROM verdict v JOIN file_occurrence o ON o.id = v.occurrence_id WHERE o.path = ?",
                (row, n) -> row.getString("reason"),
                path);
    }

    private String storedFormatOf(String path) {
        return jdbcTemplate.queryForObject(
                "SELECT d.format FROM detected_format d JOIN file_occurrence o ON o.id = d.occurrence_id WHERE o.path = ?",
                String.class,
                path);
    }

    /** The page from {@code heading} to the next heading of the same level, or nothing where it has no such heading. */
    private static String section(String html, String heading) {
        int from = html.indexOf(heading);
        if (from < 0) {
            return "";
        }
        int to = html.indexOf("<h2>", from + heading.length());
        return to < 0 ? html.substring(from) : html.substring(from, to);
    }

    /** The count the page prints on the line for {@code label}. */
    private static int countIn(String html, String label) {
        Matcher matcher = Pattern.compile(Pattern.quote(label) + "[^0-9]{0,80}?([0-9]+)").matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException("the page has no line for " + label + ": " + html);
        }
        return Integer.parseInt(matcher.group(1));
    }
}
