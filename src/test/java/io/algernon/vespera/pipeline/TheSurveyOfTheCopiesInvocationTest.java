package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What whole invocations say of the tree's copies under ADR-280 §3, which amends ADR-274 §2.5: the count before the
 * first copy is every survivor listed and the bytes their originals hold, a copy that already stands ticks the counter
 * of survivors copied, and the copies standing at a path longer than 259 characters are counted in a line of their
 * own, written only where there is one, whose every argument is a number.
 *
 * <p><b>The working directory is made deep</b>, at {@code <base>/<w...>} of {@value #A_DEEP_WORKING_DIRECTORY}
 * characters, so that beneath it, below {@code deliverable/}, a run id of 64 characters and the group's two folders, a
 * copy whose name is 100 characters long passes 259 and one of 10 does not. One static working directory serves the
 * class, as in {@code TheTreeInHtmlInvocationTest}, and every claim is scoped to the generation run this test's corpus
 * minted.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Issue("570")
@Link(name = "ADR-280", url = Adr.A_CLUSTERS_FOLDER_HOLDS_ITS_PAGE_AND_ITS_SURVIVORS_COPIES, type = "adr")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
class TheSurveyOfTheCopiesInvocationTest {

    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    private static final String BOILERPLATE_FLOOR = "1.0";

    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    private static final String A_FLOOR_THAT_REMOVES_NOTHING = "0.0";

    private static final String THE_READING_WINDOW = "4096";

    /** How long the working directory's absolute path is made, in characters. */
    private static final int A_DEEP_WORKING_DIRECTORY = 95;

    /** The corpus's document of a short name, whose copy stays within the limit. */
    private static final String THE_SHORT_ONE = "corpus.txt";

    /** The corpus's document of a name of 100 characters, the longest a copy's name is, whose copy passes it. */
    private static final String THE_LONG_ONE = "l".repeat(96) + ".txt";

    /** What a path may be at most for every program on Windows to open it: one less than the 260 of its limit. */
    private static final int WINDOWS_LONGEST_PATH = 259;

    /** What each line the generation step writes of the tree's copies opens with. */
    private static final String COPY_LINES = "Stage 6b (generation, copies)";

    /** The setting the line about long paths points the operator at. */
    private static final String THE_ROOTS_SETTING = "vespera.deliverable-root";

    /** What the counter of copies made or kept opens with. */
    private static final String THE_COPIES_COUNTER = "Stage 6b (generation, survivors copied): ";

    /** The one copy past the limit. */
    private static final long ONE_COPY = 1;

    /** The line before the first copy, the line after the last, and the line about long paths. */
    private static final int THREE_COPY_LINES = 3;

    /** How many counts the line before the first copy carries: documents, their bytes, the drive's free bytes. */
    private static final int THREE_COUNTS = 3;

    /** How many counts the line after the last copy carries: six outcomes and the bytes copied. */
    private static final int SEVEN_COUNTS = 7;

    /** Both of the corpus's documents. */
    private static final int BOTH = 2;

    @TempDir
    static Path base;

    @DynamicPropertySource
    static void aDeepWorkingDirectory(DynamicPropertyRegistry registry) {
        Path workingDirectory = workingDirectory();
        try {
            Files.createDirectories(workingDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    @BeforeEach
    void forgetWhatAnEarlierClassScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("Every file of the tree has a name of bounded length")
    @DisplayName("One copy past the length some Windows programs open is counted in a line of its own, which carries numbers only")
    void aCopyPastTheWindowsLimitIsCountedInALineOfItsOwnInNumbersOnly(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        logged.list.clear();

        cli.run("run", root.toString());

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        int theLongPath = absoluteLengthOfTheCopyOf(root, THE_LONG_ONE);
        int theShortPath = absoluteLengthOfTheCopyOf(root, THE_SHORT_ONE);
        assumeTrue(
                theLongPath > WINDOWS_LONGEST_PATH && theShortPath <= WINDOWS_LONGEST_PATH,
                "the temporary folder's own path, " + base.toAbsolutePath().toString().length() + " characters, puts"
                        + " both copies on one side of " + WINDOWS_LONGEST_PATH + " characters: " + theLongPath
                        + " and " + theShortPath);
        List<ILoggingEvent> copyLines = linesOpeningWith(COPY_LINES);
        List<ILoggingEvent> longPathLines = copyLines.stream()
                .filter(line -> line.getFormattedMessage().contains(THE_ROOTS_SETTING))
                .toList();
        claim(
                "exactly one line of the copies points at " + THE_ROOTS_SETTING + ", and every argument it carries"
                        + " is a number: the count of copies past " + WINDOWS_LONGEST_PATH + " characters, "
                        + ONE_COPY + ", of the long name's copy alone",
                () -> {
                    assertThat(longPathLines).hasSize(1);
                    assertThat(longPathLines.getFirst().getArgumentArray()).allMatch(Number.class::isInstance);
                    assertThat(longPathLines.getFirst().getArgumentArray())
                            .extracting(argument -> ((Number) argument).longValue())
                            .containsExactly(ONE_COPY);
                });
        claim(
                "it is a line of its own: beside it the line before the first copy and the line after the last"
                        + " are written as ever, with their " + THREE_COUNTS + " and " + SEVEN_COUNTS + " counts",
                () -> {
                    assertThat(copyLines).hasSize(THREE_COPY_LINES);
                    assertThat(copyLines.stream()
                                    .filter(line -> !longPathLines.contains(line))
                                    .map(line -> line.getArgumentArray().length))
                            .containsExactlyInAnyOrder(THREE_COUNTS, SEVEN_COUNTS);
                });
        claim(
                "and it names no document, the archive or the working directory",
                () -> assertThat(longPathLines.getFirst().getFormattedMessage())
                        .doesNotContain(THE_LONG_ONE)
                        .doesNotContain(THE_SHORT_ONE)
                        .doesNotContain(root.toString())
                        .doesNotContain(workingDirectory().toString()));
    }

    @Test
    @Story("Drawing the tree again never writes it again")
    @DisplayName("Drawing the tree again counts every document listed before the first copy, and each copy already there moves the counter on")
    void aRedrawingCountsEverySurvivorListedAndACopyThatStandsMovesTheCounter(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        cli.run("run", root.toString());
        claim(
                "the precondition: the first drawing copied both documents, so both copies stand",
                () -> assertThat(columnByPath(root, "copy_outcome"))
                        .containsExactlyInAnyOrderEntriesOf(Map.of(THE_SHORT_ONE, "copied", THE_LONG_ONE, "copied")));
        long bothDocumentsBytes = Files.size(root.resolve(THE_SHORT_ONE)) + Files.size(root.resolve(THE_LONG_ONE));
        logged.list.clear();

        cli.run("run", root.toString());

        List<ILoggingEvent> copyLines = linesOpeningWith(COPY_LINES).stream()
                .filter(line -> !line.getFormattedMessage().contains(THE_ROOTS_SETTING))
                .toList();
        claim("the second invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the line before the first copy counts the " + BOTH + " documents listed and the bytes they hold,"
                        + " though the copy of each already stands: a copy's name is known only when its group is"
                        + " reached",
                () -> {
                    Object[] before = copyLines.getFirst().getArgumentArray();
                    assertThat(((Number) before[0]).longValue()).isEqualTo(BOTH);
                    assertThat(((Number) before[1]).longValue()).isEqualTo(bothDocumentsBytes);
                });
        claim(
                "the line after the last counts none copied and the " + BOTH + " already standing",
                () -> assertThat(copyLines.getLast().getArgumentArray())
                        .extracting(argument -> ((Number) argument).longValue())
                        .startsWith(0L, (long) BOTH));
        claim(
                "and the counter of documents copied reaches " + BOTH + " of " + BOTH + ", each copy that stands"
                        + " moving it on, so it does not stop short",
                () -> assertThat(linesOpeningWith(THE_COPIES_COUNTER).getLast().getFormattedMessage())
                        .isEqualTo(THE_COPIES_COUNTER + BOTH + " of " + BOTH + " (100%)"));
    }

    /** {@code <base>/<w...>}, padded so its absolute path is {@value #A_DEEP_WORKING_DIRECTORY} characters long. */
    private static Path workingDirectory() {
        int padding = Math.max(1, A_DEEP_WORKING_DIRECTORY - base.toAbsolutePath().toString().length() - 1);
        return base.toAbsolutePath().resolve("w".repeat(padding));
    }

    /** The length of the absolute path of the copy the listing names for {@code document}. */
    private int absoluteLengthOfTheCopyOf(Path root, String document) throws IOException {
        String copy = columnByPath(root, "copy").get(document);
        return theTreeOf(root).toAbsolutePath().normalize().resolve(copy).toString().length();
    }

    private List<ILoggingEvent> linesOpeningWith(String opening) {
        return logged.list.stream()
                .filter(event -> event.getFormattedMessage().startsWith(opening))
                .toList();
    }

    /** The tree this corpus's one piece of writing drew. */
    private Path theTreeOf(Path root) {
        return workingDirectory().resolve("deliverable").resolve(generationRuns(root).getLast());
    }

    /** The listing's cells of one column, keyed by each row's path, the columns found by the header's names. */
    private Map<String, String> columnByPath(Path root, String column) throws IOException {
        List<String> lines = Files.readAllLines(theTreeOf(root).resolve("documents.csv")).stream()
                .filter(line -> !line.isBlank())
                .toList();
        List<String> header = cellsOf(lines.getFirst());
        int path = header.indexOf("path");
        int wanted = header.indexOf(column);
        if (wanted < 0) {
            throw new IllegalStateException("documents.csv has no column " + column + ": " + header);
        }
        Map<String, String> byPath = new LinkedHashMap<>();
        for (String row : lines.subList(1, lines.size())) {
            List<String> cells = cellsOf(row);
            byPath.put(cells.get(path), cells.get(wanted));
        }
        return byPath;
    }

    /** One CSV line's cells, with quotes stripped and a doubled quote read as one. */
    private static List<String> cellsOf(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int at = 0; at < line.length(); at++) {
            char c = line.charAt(at);
            if (c == '"' && quoted && at + 1 < line.length() && line.charAt(at + 1) == '"') {
                cell.append('"');
                at++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    /** A corpus of a short name and a long one, walked once, with the arrangement it produced approved. */
    private void anApprovedCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve(THE_SHORT_ONE), "a corpus document");
        Files.writeString(root.resolve(THE_LONG_ONE), "a corpus document with a long name");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .relevanceScoreFloor(A_FLOOR_THAT_REMOVES_NOTHING, "set by this test, so nothing earlier is asked for")
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every test here reads alike")
                .build());
        cli.run("run", root.toString());
        String approval = ArrangementGate.shortNameOf(theLatestArrangement(root));
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(approval, "read by this test")
                .build());
    }

    private RunId theLatestArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString()));
    }

    /** Every generation run over {@code root}'s walks, oldest first. */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                "generation",
                Walk.canonicalRoot(root).toString());
    }
}
