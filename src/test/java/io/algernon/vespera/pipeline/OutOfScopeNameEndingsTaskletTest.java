package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
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
 * profile's {@code outOfScopeNameEndings} lists (ADR-254): within the two classes no signature named, of no
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
@Link(name = "ADR-254", url = Adr.NAME_ENDINGS_THE_PROFILE_LISTS_ARE_OUT_OF_SCOPE, type = "adr")
class OutOfScopeNameEndingsTaskletTest {

    /** Why each profile here was written, as the operator would say it in {@code provenance}. */
    private static final String SET_BY_THIS_TEST = "set by this test";

    /** The reason a file left out for the ending of its name carries, with the ending to fill in. */
    private static final String REASON =
            "a file whose name ends with %s, an ending outOfScopeNameEndings in profile.yaml lists as out of scope";

    /** The page's label for the count of files left out for the ending of their name. */
    private static final String LEFT_OUT_BY_NAME = "files left out for the ending of their name";

    /** The four bytes every record here begins with, as the largest group on the measured page began. */
    private static final String RECORD_OPENING = "9990";

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
    @DisplayName("Files of no known kind whose names end as listed are left out, in any case, and counted on the page; other endings are kept")
    void leavesOutAFileOfNoKnownKindWhoseNameEndsAsListed(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        OutOfScopeNameEndingsInProfile.write(profileStore, ".P3A .dgn .TAR.GZ", SET_BY_THIS_TEST);
        Files.write(root.resolve("TERM0001.P3A"), terminalRecord(40));
        Files.write(root.resolve("TERM0001 copia.P3A"), terminalRecord(40));
        Files.write(root.resolve("term0002.p3a"), terminalRecord(41));
        Files.write(root.resolve("FIRMA.DGN"), terminalRecord(42));
        Files.write(root.resolve("dump.tar.gz"), terminalRecord(43));
        Files.write(root.resolve("TERM0003.P3L"), terminalRecord(44));
        Files.write(root.resolve("TERM0001.P3A.bak"), terminalRecord(45));
        Files.write(root.resolve("dump.gz"), terminalRecord(46));
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        stageOne(ledger, root, workingDirectory);

        claim(
                "each file whose name ends with a listed ending, in either case, is removed as out of scope, with a"
                        + " reason naming the ending as its own name spells it",
                () -> {
                    assertThat(verdictReasonsFor(ledger, walkId, "TERM0001.P3A")).containsExactly(REASON.formatted(".P3A"));
                    assertThat(verdictReasonsFor(ledger, walkId, "TERM0001 copia.P3A"))
                            .containsExactly(REASON.formatted(".P3A"));
                    assertThat(verdictReasonsFor(ledger, walkId, "term0002.p3a")).containsExactly(REASON.formatted(".p3a"));
                    assertThat(verdictReasonsFor(ledger, walkId, "FIRMA.DGN")).containsExactly(REASON.formatted(".DGN"));
                    assertThat(verdictReasonsFor(ledger, walkId, "dump.tar.gz")).containsExactly(REASON.formatted(".tar.gz"));
                    assertThat(List.of(
                                    verdictKindsFor(ledger, walkId, "TERM0001.P3A"),
                                    verdictKindsFor(ledger, walkId, "FIRMA.DGN"),
                                    verdictKindsFor(ledger, walkId, "dump.tar.gz")))
                            .containsOnly(List.of("OUT_OF_SCOPE"));
                });
        claim(
                "an ending not listed, a listed ending followed by another, and the last part alone of a listed"
                        + " two-part ending are kept: none carries a verdict",
                () -> assertThat(List.of(
                                verdictKindsFor(ledger, walkId, "TERM0003.P3L"),
                                verdictKindsFor(ledger, walkId, "TERM0001.P3A.bak"),
                                verdictKindsFor(ledger, walkId, "dump.gz")))
                        .containsOnly(List.of()));
        claim(
                "what each file was found to be is still what its bytes said, of no known kind, whether or not its"
                        + " name left it out",
                () -> assertThat(List.of(
                                storedFormatOf(ledger, walkId, "TERM0001.P3A"),
                                storedFormatOf(ledger, walkId, "dump.tar.gz"),
                                storedFormatOf(ledger, walkId, "TERM0003.P3L")))
                        .containsOnly("UNRECOGNISED"));
        claim(
                "the two identical files left out were never hashed, because nothing out of scope reaches the"
                        + " duplicate pass, and no file kept shares a size with another",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM content_hash", Integer.class))
                        .isZero());
        claim(
                "the settings stage 1 records carry the listed endings once each, in lower case and in order",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT config_consumed FROM run", String.class))
                        .contains("\"outOfScopeNameEndings\":[\".dgn\",\".p3a\",\".tar.gz\"]"));
        String html = Files.readString(workingDirectory.resolve(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME));
        claim(
                "the page counts the five left out among the files left out as out of scope, and apart as left out"
                        + " for the ending of their name, none of them as a log or for its size",
                () -> assertThat(List.of(
                                countIn(html, "left out as out of scope"),
                                countIn(html, LEFT_OUT_BY_NAME),
                                countIn(html, "Of those, logs"),
                                countIn(html, "text files left out for their size")))
                        .containsExactly(5, 5, 0, 0));
        claim(
                "the page still counts all eight under what their bytes were, of no known kind",
                () -> assertThat(countIn(html, "<td>Of no known kind</td>")).isEqualTo(8));
        claim(
                "and says which endings the profile lists, in the form the run used",
                () -> assertThat(html).contains("lists these name endings: .dgn, .p3a, .tar.gz"));
        claim(
                "the key's measurement in the profile points at the page, as the log floor's does",
                () -> assertThat(String.valueOf(OutOfScopeNameEndingsInProfile.measurementSource(profileStore)))
                        .endsWith(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME));
    }

    @Test
    @Story("A file of no known kind is left out for the ending of its name")
    @DisplayName("A listed ending never leaves out a PDF, a Word file, an older Office file or a picture; a text file is left out before it is read as a log")
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
        claim(
                "and the page says so above the timestamp table",
                () -> assertThat(html).contains("Each text file not left out for its kind or for the ending of its"
                        + " name was read from its start and its end"));
    }

    @Test
    @Story("A file of no known kind is left out for the ending of its name")
    @DisplayName("With no endings listed, a file of no known kind is kept, and the page says nothing was left out for its name")
    void withNoEndingsListedNothingIsLeftOutByName(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        Files.write(root.resolve("TERM0001.P3A"), terminalRecord(40));
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        stageOne(ledger, root, workingDirectory);

        claim(
                "the file carries no verdict: no ending is listed until the profile lists one",
                () -> assertThat(verdictKindsFor(ledger, walkId, "TERM0001.P3A")).isEmpty());
        claim(
                "the settings stage 1 records carry an empty list of endings",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT config_consumed FROM run", String.class))
                        .contains("\"outOfScopeNameEndings\":[]"));
        String html = Files.readString(workingDirectory.resolve(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME));
        claim(
                "the page says no ending is listed, and counts none left out for the ending of its name",
                () -> {
                    assertThat(html).contains("No name endings are listed");
                    assertThat(countIn(html, LEFT_OUT_BY_NAME)).isZero();
                });
    }

    @Test
    @Story("A file of no known kind is left out for the ending of its name")
    @DisplayName("With a list holding a wildcard, nothing is left out for its name and no ending is recorded")
    void anUnreadableListLeavesNothingOutAndIsRecordedAsNone(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        OutOfScopeNameEndingsInProfile.write(new ProfileStore(workingDirectory), ".P3*", SET_BY_THIS_TEST);
        Files.write(root.resolve("TERM0001.P3A"), terminalRecord(40));
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        stageOne(ledger, root, workingDirectory);

        claim(
                "the file carries no verdict: a list nothing can be read from is applied as no list",
                () -> assertThat(verdictKindsFor(ledger, walkId, "TERM0001.P3A")).isEmpty());
        claim(
                "and the settings stage 1 records carry an empty list, so the run is the one no list names",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT config_consumed FROM run", String.class))
                        .contains("\"outOfScopeNameEndings\":[]"));
    }

    @Test
    @Story("What a piece of stage 1's work is identified by")
    @DisplayName("The same endings in another order, case and spacing name the same piece of work, and one more ending names another")
    void theRunIsNamedByTheEndingsAndNotByHowTheyAreWritten(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        Files.write(root.resolve("TERM0001.P3A"), terminalRecord(40));
        Ledger ledger = new Ledger(jdbcTemplate);
        walkRecorder(ledger).walk(root);

        OutOfScopeNameEndingsInProfile.write(profileStore, ".SGN, .p3a", SET_BY_THIS_TEST);
        stageOne(ledger, root, workingDirectory);
        OutOfScopeNameEndingsInProfile.write(profileStore, " .P3A .sgn .sgn ", SET_BY_THIS_TEST);
        stageOne(ledger, root, workingDirectory);

        claim(
                "two lists holding the same two endings, written in another order, case, spacing and with one"
                        + " repeated, name one piece of stage 1's work",
                () -> assertThat(stageOneRuns()).isEqualTo(1));
        claim(
                "which records the two endings once each, in lower case and in order",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT config_consumed FROM run", String.class))
                        .contains("\"outOfScopeNameEndings\":[\".p3a\",\".sgn\"]"));

        OutOfScopeNameEndingsInProfile.write(profileStore, ".P3A .SGN .DGN", SET_BY_THIS_TEST);
        stageOne(ledger, root, workingDirectory);

        claim(
                "and a list with one ending more names a second piece of work",
                () -> assertThat(stageOneRuns()).isEqualTo(2));
    }

    /**
     * §2, §4 and §7 at their edges: the name matched is the last element of the path; of two listed endings
     * that match, the longer is named; an email is left out for its kind before the list is read; and a name
     * that is the ending alone matches it. Every name here is ASCII; the names that fold longer are {@link
     * #aNameThatFoldsLongerIsStillLeftOut}'s.
     */
    @Test
    @Story("A file of no known kind is left out for the ending of its name")
    @DisplayName("Only a file's own name is matched, the longer of two matching endings is named, an email stays an email, and a name that is only the ending is left out")
    void theNameIsTheLastElementOfThePathAndTheLongestEndingIsNamed(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        new ProfileStore(workingDirectory).save(ProfileFixture.profile()
                .outOfScopeNameEndings(".P3A .gz .TAR.GZ .eml .DGN", SET_BY_THIS_TEST)
                .build());
        Path folder = Files.createDirectory(root.resolve("ARCHIVIO.P3A"));
        Files.write(folder.resolve("nota.bin"), terminalRecord(40));
        Files.write(folder.resolve("TERM0009.P3A"), terminalRecord(41));
        Files.write(root.resolve("dump.tar.gz"), terminalRecord(42));
        Files.writeString(root.resolve("collaudo.eml"), "From: a@example.org\nSubject: collaudo\n\nRinviato.\n");
        Files.write(root.resolve(".DGN"), terminalRecord(43));
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        stageOne(ledger, root, workingDirectory);

        claim(
                "a folder named with a listed ending leaves out nothing for its own name: a file in it whose name"
                        + " ends otherwise is kept, and one whose name ends with a listed ending is left out",
                () -> {
                    assertThat(verdictKindsFor(ledger, walkId, "ARCHIVIO.P3A/nota.bin")).isEmpty();
                    assertThat(verdictReasonsFor(ledger, walkId, "ARCHIVIO.P3A/TERM0009.P3A"))
                            .containsExactly(REASON.formatted(".P3A"));
                });
        claim(
                "with both .gz and .tar.gz listed, the reason names the longer ending",
                () -> assertThat(verdictReasonsFor(ledger, walkId, "dump.tar.gz")).containsExactly(REASON.formatted(".tar.gz")));
        claim(
                "a listed .eml is left out as an email, its kind, and not for the ending of its name",
                () -> assertThat(verdictReasonsFor(ledger, walkId, "collaudo.eml"))
                        .containsExactly("an email, and emails are out of scope"));
        claim(
                "a file whose whole name is a listed ending is left out",
                () -> assertThat(verdictReasonsFor(ledger, walkId, ".DGN")).containsExactly(REASON.formatted(".DGN")));
    }

    /**
     * §7 where folding changes a name's length: {@code İ} (U+0130) folds to two characters under {@code
     * Locale.ROOT}. Where it falls inside the matched ending, the name's last characters are not the ending, so
     * the reason names the ending as the canonical list holds it; a name shorter than the folded ending does
     * not end stage 1; and where it falls earlier in the name, the ending is spelt as the name spells it.
     *
     * <p>Aborted, not failed, where Java cannot create such a name, as under a POSIX locale whose file-name
     * encoding is ASCII, as {@code WalkRecorderTest} aborts on a name its file system refuses.
     */
    @Test
    @Story("A file of no known kind is left out for the ending of its name")
    @DisplayName("A name holding a letter that folds to two characters is still left out, and the reason names its ending sensibly")
    void aNameThatFoldsLongerIsStillLeftOut(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        String endingInTheName = "A" + DOTTED_CAPITAL_I_ENDING;
        String onlyTheEnding = DOTTED_CAPITAL_I_ENDING;
        String earlierInTheName = "İSTANBUL.P3A";
        try {
            Files.write(root.resolve(endingInTheName), terminalRecord(40));
            Files.write(root.resolve(onlyTheEnding), terminalRecord(41));
            Files.write(root.resolve(earlierInTheName), terminalRecord(42));
        } catch (InvalidPathException | IOException e) {
            abort("this file system or locale will not create a name holding U+0130: " + e.getMessage());
        }
        new ProfileStore(workingDirectory).save(ProfileFixture.profile()
                .outOfScopeNameEndings(".P3A " + DOTTED_CAPITAL_I_ENDING, SET_BY_THIS_TEST)
                .build());
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);
        String foldedEnding = DOTTED_CAPITAL_I_ENDING.toLowerCase(Locale.ROOT);

        stageOne(ledger, root, workingDirectory);

        claim(
                "a name whose last characters hold the capital dotted I, and a name that is the ending alone and"
                        + " shorter than it once folded, are both left out, the reason naming the ending as the list"
                        + " holds it once folded",
                () -> {
                    assertThat(verdictReasonsFor(ledger, walkId, endingInTheName))
                            .containsExactly(REASON.formatted(foldedEnding));
                    assertThat(verdictReasonsFor(ledger, walkId, onlyTheEnding))
                            .containsExactly(REASON.formatted(foldedEnding));
                });
        claim(
                "a name holding the capital dotted I before a plain ending has the ending spelt as the name spells it",
                () -> assertThat(verdictReasonsFor(ledger, walkId, earlierInTheName))
                        .containsExactly(REASON.formatted(".P3A")));
        claim(
                "and stage 1 finishes: the page it writes at its end is there",
                () -> assertThat(Files.exists(workingDirectory.resolve(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME)))
                        .isTrue());
    }

    /**
     * §2: endings are separated by any character Unicode counts as white space, so a no-break space pasted from
     * a document separates two endings as a space does, rather than joining them into one that matches nothing.
     */
    @Test
    @Story("A file of no known kind is left out for the ending of its name")
    @DisplayName("Two endings separated by a no-break space are read as two")
    void aNoBreakSpaceSeparatesEndingsAsASpaceDoes(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        new ProfileStore(workingDirectory).save(ProfileFixture.profile()
                .outOfScopeNameEndings(".P3A .DGN", SET_BY_THIS_TEST)
                .build());
        Files.write(root.resolve("TERM0001.P3A"), terminalRecord(40));
        Files.write(root.resolve("FIRMA.DGN"), terminalRecord(41));
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        stageOne(ledger, root, workingDirectory);

        claim(
                "the settings stage 1 records carry two endings, not one joined by the no-break space",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT config_consumed FROM run", String.class))
                        .contains("\"outOfScopeNameEndings\":[\".dgn\",\".p3a\"]"));
        claim(
                "and a file named with either is left out",
                () -> {
                    assertThat(verdictReasonsFor(ledger, walkId, "TERM0001.P3A")).containsExactly(REASON.formatted(".P3A"));
                    assertThat(verdictReasonsFor(ledger, walkId, "FIRMA.DGN")).containsExactly(REASON.formatted(".DGN"));
                });
    }

    /** An ending holding a capital I with a dot above, U+0130, which {@code Locale.ROOT} folds to two characters. */
    private static final String DOTTED_CAPITAL_I_ENDING = ".İX";

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

    private int stageOneRuns() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM run WHERE stage = 'byte-level-reduction'", Integer.class);
    }

    /**
     * A record of {@code length} bytes: {@link #RECORD_OPENING} and a few digits and letters, then {@code È} in
     * ISO 8859-1, a byte UTF-8 never follows with a space, then spaces and a line end. Nothing in it is zero.
     */
    private static byte[] terminalRecord(int length) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) ' ');
        byte[] opening = (RECORD_OPENING + "0000123 CAFF").getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(opening, 0, bytes, 0, opening.length);
        bytes[opening.length] = (byte) 0xC8;
        bytes[length - 2] = '\r';
        bytes[length - 1] = '\n';
        return bytes;
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
