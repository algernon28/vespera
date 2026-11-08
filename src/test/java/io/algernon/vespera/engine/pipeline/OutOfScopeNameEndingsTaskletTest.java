package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.AnomalyLog;
import io.algernon.vespera.engine.corpus.ContentIdentity;
import io.algernon.vespera.engine.corpus.DetectedFormats;
import io.algernon.vespera.engine.corpus.WalkRecorder;
import io.algernon.vespera.engine.ledger.ImplementationVersions;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.WalkId;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
 * Stage 1 leaves out a file whose bytes matched no signature and whose name ends with one of the endings the
 * profile's {@code outOfScopeNameEndings} lists (ADR-256): within the two classes no signature named, of no
 * known kind and text, and within no other, with a reason that names the ending, a count of its own on the
 * format-mix page, and the list in stage 1's configuration consumed in one canonical form.
 *
 * <p>The profile is written as YAML through {@link OutOfScopeNameEndingsInProfile}, so this compiles before the
 * key exists; until it does, {@code ProfileStore} refuses the file and every test here fails there, except the
 * one with no profile, which fails on what stage 1 records and writes.
 *
 * <p>The files of no known kind are fixed-width records of digits and letters carrying one byte of an older
 * single-byte encoding, the shape the measured terminal files have: no signature, no zero byte, and not UTF-8.
 * Each has a length of its own unless two are meant to be identical, so nothing kept is ever hashed.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Byte-level reduction")
@Feature("Files left out for the ending of their name")
@Link(name = "ADR-256", url = Adr.NAME_ENDINGS_THE_PROFILE_LISTS_ARE_OUT_OF_SCOPE, type = "adr")
class OutOfScopeNameEndingsTaskletTest {

    /** Why each profile here was written, as the operator would say it in {@code provenance}. */
    private static final String SET_BY_THIS_TEST = "set by this test";

    /** The reason a file left out for the ending of its name carries, with the ending to fill in. */
    private static final String REASON =
            "a file whose name ends with %s, an ending outOfScopeNameEndings in profile.yaml lists as out of scope";

    /** The page's label for the count of files left out for the ending of their name. */
    private static final String LEFT_OUT_BY_NAME = "files left out for the ending of their name";

    /** A log floor of 90%, so that a file of timestamped lines is a log unless something leaves it out first. */
    private static final String A_LOG_FLOOR = "0.9";

    /** More timestamped lines than the ten the log rule needs. */
    private static final int LOG_LINES = 12;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("A file of no known kind is left out for the ending of its name")
    @DisplayName("A listed ending never leaves out a PDF, a Word file, an older Office file or a picture; a text file is left out before it is read as a log")
    @Link(name = "ADR-277", url = Adr.CODE_IS_RECOGNISED_BY_A_NAME_ENDING_OR_A_GENERATORS_MARK, type = "adr")
    @Link(name = "ADR-287", url = Adr.LOGS_AND_CONFIGURATION_FILES_ARE_TOLD_BY_THEIR_BYTES, type = "adr")
    void aListedEndingNeverLeavesOutAFileWhoseBytesNameItsKind(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        profileStore.save(ProfileFixture.profile().logTimestampShareFloor(A_LOG_FLOOR, SET_BY_THIS_TEST).build());
        OutOfScopeNameEndingsInProfile.write(profileStore, ".DGN .P3A .P3L .SGN .LGN .PGN", SET_BY_THIS_TEST);
        Files.writeString(root.resolve("contratto.DGN"), "%PDF-1.4\n1 0 obj\n<< >>\nendobj\ntrailer\n<< >>\n%%EOF\n");
        zipHolding(root.resolve("manuale.P3A"), "word/document.xml");
        Files.write(root.resolve("verbale.P3L"), oleCompoundFile());
        Files.write(root.resolve("schermata.SGN"), PNG_FILE);
        Files.writeString(root.resolve("note.LGN"), "prima riga\nseconda riga\nterza riga\n");
        Files.writeString(root.resolve("export.PGN"), timestampedLines());
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        stageOne(ledger, root, workingDirectory);

        claim(
                "a PDF, a Word file, an older Office file and a picture named with listed endings are recorded as"
                        + " what their bytes are",
                () -> assertThat(List.of(
                                storedFormatOf(ledger, walkId, "contratto.DGN"),
                                storedFormatOf(ledger, walkId, "manuale.P3A"),
                                storedFormatOf(ledger, walkId, "verbale.P3L"),
                                storedFormatOf(ledger, walkId, "schermata.SGN")))
                        .containsExactly("PDF", "WORDPROCESSING", "OLE_COMPOUND", "IMAGE"));
        claim(
                "and each is kept, whatever its name ends with: none carries a verdict",
                () -> assertThat(List.of(
                                verdictKindsFor(ledger, walkId, "contratto.DGN"),
                                verdictKindsFor(ledger, walkId, "manuale.P3A"),
                                verdictKindsFor(ledger, walkId, "verbale.P3L"),
                                verdictKindsFor(ledger, walkId, "schermata.SGN")))
                        .containsOnly(List.of()));
        claim(
                "the two text files named with listed endings are left out for the ending of their name, the one"
                        + " whose every line begins with a timestamp included, though the floor set would make it a log",
                () -> {
                    assertThat(verdictReasonsFor(ledger, walkId, "note.LGN")).containsExactly(REASON.formatted(".LGN"));
                    assertThat(verdictReasonsFor(ledger, walkId, "export.PGN")).containsExactly(REASON.formatted(".PGN"));
                });
        String html = Files.readString(workingDirectory.resolve(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME));
        claim(
                "the page counts both as left out for the ending of their name and neither as a log",
                () -> assertThat(List.of(countIn(html, LEFT_OUT_BY_NAME), countIn(html, "Of those, logs")))
                        .containsExactly(2, 0));
        claim(
                "neither was read for timestamps: the three-line note is not counted among the text files of fewer"
                        + " than ten lines",
                () -> assertThat(countIn(html, "<td>fewer than ten lines</td>")).isZero());
        claim(
                "and the file whose every line begins with a timestamp is not counted in the band from 90% to 100%,"
                        + " nor among the logs",
                () -> assertThat(countIn(html, "<td>90% to 100%</td>")).isZero());
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

    /** {@link #LOG_LINES} lines, each beginning with a timestamp of its own. */
    private static String timestampedLines() {
        StringBuilder log = new StringBuilder();
        for (int line = 0; line < LOG_LINES; line++) {
            log.append("[2023-02-08 12:00:").append(String.format(Locale.ROOT, "%02d", line))
                    .append(",096] DEBUG -- a transaction was handled\n");
        }
        return log.toString();
    }

    /** A well-formed archive whose single entry is named {@code entryName}. */
    private static void zipHolding(Path path, String entryName) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write("<part/>".getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
        }
    }

    /** The eight bytes every OLE compound file begins with, then padding to 64 bytes. */
    private static byte[] oleCompoundFile() {
        byte[] bytes = new byte[64];
        byte[] signature = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
    }

    /** The eight bytes every PNG begins with, then enough to give it a length of its own. */
    private static final byte[] PNG_FILE = {
        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1
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
