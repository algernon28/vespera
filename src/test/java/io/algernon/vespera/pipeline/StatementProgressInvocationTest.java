package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ConverterStopsPartwayBeans;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What a whole invocation says while SQLite builds stage 4b's index and reads stage 3's rows, and what it
 * says while it removes the index (ADR-193, #411).
 *
 * <p>On 2026-10-04 stage 4b's build of {@code shingle_by_hash} wrote one line before it and one after, 39
 * minutes apart, and nothing between. ADR-193 has SQLite's progress callback count the build's steps and
 * a line state {@code about X% of N rows} on ADR-192's cadence; once the rows are gone through, one line
 * says that writing the index says nothing more until it ends. Stage 3's read of its shingle rows reported
 * the same way, between ADR-191's two lines; since ADR-211 the database groups them in one statement, which
 * reports the same way under a name of its own, its share counted at the most steps a row takes. The removal
 * of the index gives SQLite no callback, and keeps ADR-187's two lines with nothing between.
 *
 * <p><b>The corpus is large on purpose</b>: four texts of {@link #WORDS_A_TEXT} words each, about 60,000
 * shingle rows, so that the build and the read each pass SQLite's callback interval of 100,000 steps
 * several times. Fewer rows would give no callback, and so no line to pin.
 *
 * <p>Its claims about the progress lines, the line that the rows are gone through, stage 4b's reworded line
 * and stage 4's two lines around the boilerplate read failed before part (a) of ADR-193 was built, and
 * have passed since. The claims about the drop and about the handler left on the connection passed before
 * it too, and are what stop an implementation from writing a line where SQLite gives no count, or leaving a
 * handler on a pooled connection.
 */
@CascadeSliceTest
@Import(ConverterStopsPartwayBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("411")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
class StatementProgressInvocationTest {

    /** How many texts the large corpus holds. */
    private static final int TEXTS = 4;

    /** How many words each of them holds: one shingle row a word, near enough. */
    private static final int WORDS_A_TEXT = 15_000;

    /** Fewer rows than this and the read would pass the callback interval too few times to say anything. */
    private static final long AT_LEAST_THIS_MANY_SHINGLE_ROWS = 50_000;

    /** The most progress lines one statement may write (ADR-192 section 8, ADR-193 section 5). */
    private static final int AT_MOST_A_HUNDRED_LINES = 100;

    /** What stage 4's gate is opened with, so the build and the resolution run. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** Stage 4b's line before its build, up to its total. */
    private static final String BUILDING = "Stage 4b (redundancy resolution) is building shingle_by_hash over up to ";

    /** What that line now says about the worst measured and about a stop (ADR-193 section 4.2). */
    private static final String THE_WORST_MEASURED_AND_A_STOP =
            "that took 39 minutes for 42833917 rows on a USB spinning disk, and stopping before it ends undoes it";

    /** What it said before, which understated the build of 2026-10-04. */
    private static final String THE_OLD_UNDERSTATEMENT = "on a large database this takes minutes";

    /** Stage 4b's line after its build. */
    private static final String BUILT = "Stage 4b (redundancy resolution) built shingle_by_hash in";

    /** The label of the build's progress lines. */
    private static final String BUILD_LABEL = "Stage 4b (redundancy resolution, building shingle_by_hash)";

    /** What the line that the rows are gone through says after the label and the total. */
    private static final String SAYS_NOTHING_MORE = "rows gone through; writing the index says nothing more until it ends";

    /** Stage 3's line before its read, and the line after its measurement (ADR-191). */
    private static final String READING = "Stage 3 (content census) is reading up to ";

    private static final String MEASURED = "Stage 3 (content census) measured shingle document frequency";

    /** The label of stage 3's progress lines until ADR-211, when the application read the rows itself. */
    private static final String READ_LABEL = "Stage 3 (content census, reading shingle rows)";

    /** Their label since: the database reads, sorts and counts the rows in one statement (ADR-211 section 9). */
    private static final String GROUPING_LABEL = "Stage 3 (content census, grouping shingle rows)";

    /** Stage 4's line before its one read of the boilerplate shingles (ADR-193 section 6). */
    private static final String READING_BOILERPLATE = "Stage 4 (content redundancy) is reading the boilerplate shingles";

    /** Stage 4's line after that read, up to the seconds it took. */
    private static final String READ_BOILERPLATE = "Stage 4 (content redundancy) read the boilerplate shingles in ";

    /** That line with the seconds: one decimal place. */
    private static final String READ_BOILERPLATE_IN_SECONDS_TO_ONE_DECIMAL =
            "Stage 4 \\(content redundancy\\) read the boilerplate shingles in \\d+\\.\\d s";

    /** Stage 4b's line once its resolution has finished. */
    private static final String RESOLUTION_FINISHED = "Stage 4b (redundancy resolution) finished";

    /** Stage 2's two lines around the removal of the index (ADR-187 section 1). */
    private static final String REMOVING = "Stage 2 (extraction) is removing shingle_by_hash";

    private static final String REMOVED = "Stage 2 (extraction) removed shingle_by_hash in";

    /** A progress line, wherever it sits in a line of the log: its percentage and its total. */
    private static final Pattern ABOUT = Pattern.compile("(?:about|at least) (\\d+)% of ([\\d,]+) rows");

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    /**
     * The converter forgets every scripted and pinned outcome and answers, and stage 4's gate is open. The
     * script and the count are static and shared with every other class importing the converter.
     */
    @BeforeEach
    void startClean() {
        ConverterStopsPartwayBeans.script(List.of(), List.of(), List.of());
        ConverterStopsPartwayBeans.keepAnswering();
        profileStore.save(ProfileFixture.profile()
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .build());
    }

    @AfterEach
    void bringTheConverterBack() {
        ConverterStopsPartwayBeans.keepAnswering();
    }

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("Building the hash index and reading the word sequences each say how far they have gone, and the build says when only its silent end is left")
    void theBuildAndTheReadSayHowFarTheyHaveGone(CapturedOutput output, @TempDir Path root)
            throws IOException, SQLException {
        writeLargeCorpus(root);

        int before = output.getAll().length();
        cli.run("run", root.toString());
        List<String> lines = linesOf(output.getAll().substring(before));

        String extractionRun = onlyRunOf(root, StageModules.EXTRACTION);
        long runRows = shingleRowsUnder(extractionRun);
        long tableRows = greatestShingleRow();
        claim(
                "the extraction saved " + runRows + " word sequences, at least " + AT_LEAST_THIS_MANY_SHINGLE_ROWS
                        + ", enough for SQLite to call back several times while it goes through them",
                () -> assertThat(runRows).isGreaterThanOrEqualTo(AT_LEAST_THIS_MANY_SHINGLE_ROWS));

        String building = only(lines, BUILDING);
        claim(
                "the line before the build says the worst build measured and that a stop undoes it, and no"
                        + " longer that it takes minutes",
                () -> assertThat(building)
                        .contains(THE_WORST_MEASURED_AND_A_STOP)
                        .doesNotContain(THE_OLD_UNDERSTATEMENT));

        int buildingAt = indexOf(lines, BUILDING);
        int builtAt = indexOf(lines, BUILT);
        List<String> buildProgress = between(lines, buildingAt, builtAt, BUILD_LABEL + ": about ");
        claim(
                "between its two lines the build writes progress lines, because SQLite calls back while it goes"
                        + " through the rows",
                () -> assertThat(buildProgress).isNotEmpty());
        claim(
                "each states about what share of the " + grouped(tableRows) + " rows the table holds at most",
                () -> assertThat(buildProgress).allSatisfy(line -> assertThat(totalOf(line)).isEqualTo(grouped(tableRows))));
        claim(
                "the shares rise from line to line and stay below a hundred",
                () -> assertThat(percentagesOf(buildProgress)).isSorted().doesNotHaveDuplicates().allSatisfy(
                        percentage -> assertThat(percentage).isLessThan(100)));
        claim(
                "and there are no more than " + AT_MOST_A_HUNDRED_LINES + " of them",
                () -> assertThat(buildProgress.size()).isLessThanOrEqualTo(AT_MOST_A_HUNDRED_LINES));

        List<String> goneThrough = between(lines, buildingAt, builtAt, SAYS_NOTHING_MORE);
        claim(
                "once the rows are gone through, one line says so, and that writing the index says nothing"
                        + " more until it ends",
                () -> assertThat(goneThrough)
                        .singleElement()
                        .asString()
                        .contains(BUILD_LABEL + ": all " + grouped(tableRows) + " " + SAYS_NOTHING_MORE));
        claim(
                "it comes after the last progress line, and nothing of the build's comes between it and the"
                        + " line that the index is built",
                () -> assertThat(indexOf(lines, SAYS_NOTHING_MORE))
                        .isGreaterThan(lastIndexOf(lines, BUILD_LABEL + ": about "))
                        .isLessThan(builtAt));

        int readingAt = indexOf(lines, READING);
        int measuredAt = indexOf(lines, MEASURED);
        List<String> groupingProgress = between(lines, readingAt, measuredAt, GROUPING_LABEL + ": at least ");
        claim(
                "no line of the grouping says `about` a share, as a counted read's does: its share is a floor,"
                        + " and the line says `at least`",
                () -> assertThat(between(lines, readingAt, measuredAt, GROUPING_LABEL + ": about ")).isEmpty());
        claim(
                "the content census still announces its read of the extraction's word sequences, over up to the"
                        + " rows of the run, and says when it has measured them",
                () -> assertThat(readingAt).isNotNegative().isLessThan(measuredAt));
        claim(
                "between the two it says how far the database has got in grouping them, because SQLite calls back"
                        + " all through the one statement that reads, sorts and counts them",
                () -> assertThat(groupingProgress).isNotEmpty());
        claim(
                "each line states at least what share of the " + grouped(runRows) + " rows the run holds is done",
                () -> assertThat(groupingProgress)
                        .allSatisfy(line -> assertThat(totalOf(line)).isEqualTo(grouped(runRows))));
        claim(
                "the shares rise from line to line and stay below a hundred: the share is SQLite's steps counted"
                        + " at the most it takes for a row, so the last line falls short of the whole",
                () -> assertThat(percentagesOf(groupingProgress)).isSorted().doesNotHaveDuplicates().allSatisfy(
                        percentage -> assertThat(percentage).isLessThan(100)));
        claim(
                "there are no more than " + AT_MOST_A_HUNDRED_LINES + " of them, and none under the name the"
                        + " lines had while the application read the rows itself",
                () -> {
                    assertThat(groupingProgress.size()).isLessThanOrEqualTo(AT_MOST_A_HUNDRED_LINES);
                    assertThat(lines).noneMatch(line -> line.contains(READ_LABEL + ": "));
                });

        String invocation = String.join("\n", lines);
        int readingBoilerplateAt = indexOf(lines, READING_BOILERPLATE);
        int readBoilerplateAt = indexOf(lines, READ_BOILERPLATE);
        int resolutionFinishedAt = indexOf(lines, RESOLUTION_FINISHED);
        claim(
                "with stage 4's gate open, the boilerplate shingles are read once in the invocation, and the line"
                        + " before that read is written once, naming stage 4 without a letter, since the read falls"
                        + " in whichever of its two steps first asks for them",
                () -> assertThat(invocation).containsOnlyOnce(READING_BOILERPLATE));
        claim(
                "the line after it is written once too, and states the time the read took in seconds, to one"
                        + " decimal place",
                () -> assertThat(invocation)
                        .containsOnlyOnce(READ_BOILERPLATE)
                        .containsPattern(READ_BOILERPLATE_IN_SECONDS_TO_ONE_DECIMAL));
        claim(
                "the two come in that order, and both before the redundancy resolution says it has finished",
                () -> assertThat(readingBoilerplateAt)
                        .isNotNegative()
                        .isLessThan(readBoilerplateAt)
                        .isLessThan(resolutionFinishedAt));
        claim(
                "the line after the read comes before the resolution's finishing line as well",
                () -> assertThat(readBoilerplateAt).isLessThan(resolutionFinishedAt));

        boolean handlerLeft = aHandlerIsLeftOnTheConnection();
        claim(
                "a handler was set while the statements ran, since they reported their progress, and none is"
                        + " left on the connection the test context's pool hands out",
                () -> {
                    assertThat(buildProgress).isNotEmpty();
                    assertThat(handlerLeft).isFalse();
                });
    }

    @Test
    @Story("A statement SQLite gives no count for says nothing between its two lines")
    @DisplayName("Removing the hash index says when it starts and when it has ended, and nothing between")
    void theRemovalSaysNothingBetweenItsTwoLines(CapturedOutput output, @TempDir Path root) throws IOException {
        writeSmallCorpus(root);
        cli.run("run", root.toString());
        claim(
                "the first invocation ends with the hash index in place, built by its redundancy check",
                () -> assertThat(indexExists()).isTrue());

        Files.writeString(root.resolve("99-added-later.txt"), "A document added after the first invocation"
                + " finished, about harbour pilots and the tide tables they kept.");
        int before = output.getAll().length();
        cli.run("run", root.toString());
        List<String> lines = linesOf(output.getAll().substring(before));

        int removingAt = indexOf(lines, REMOVING);
        int removedAt = indexOf(lines, REMOVED);
        claim(
                "the second invocation extracts under a run of its own, and removes the index before it writes,"
                        + " saying so before and after",
                () -> assertThat(removingAt).isNotNegative().isLessThan(removedAt));
        claim(
                "and writes nothing at all between those two lines: SQLite gives no count while it removes an"
                        + " index, and no line is written on a clock",
                () -> assertThat(removedAt - removingAt).isEqualTo(1));
    }

    /** Four texts of {@link #WORDS_A_TEXT} words each, on one line, drawn from made-up words. */
    private static void writeLargeCorpus(Path root) throws IOException {
        String[] syllables = {"ka", "lo", "mi", "ter", "san", "vu", "pel", "dor", "ri", "nas", "ob", "ef", "gal", "tu"};
        for (int text = 1; text <= TEXTS; text++) {
            Random random = new Random(411L * text);
            StringBuilder words = new StringBuilder();
            for (int word = 0; word < WORDS_A_TEXT; word++) {
                if (word > 0) {
                    words.append(' ');
                }
                int length = 2 + random.nextInt(3);
                for (int syllable = 0; syllable < length; syllable++) {
                    words.append(syllables[random.nextInt(syllables.length)]);
                }
            }
            Files.writeString(root.resolve(String.format("%02d-long.txt", text)), words.toString());
        }
    }

    private static void writeSmallCorpus(Path root) throws IOException {
        for (int position = 1; position <= 3; position++) {
            Files.writeString(
                    root.resolve(String.format("%02d-plain.txt", position)),
                    "Entry " + position + " of the removal corpus describes lock keepers, sluice gates and the"
                            + " order in which the gates were greased during the spring of year " + position
                            + ", with notes on floods and barges.");
        }
    }

    private boolean aHandlerIsLeftOnTheConnection() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            Object database = connection.unwrap(SQLiteConnection.class).getDatabase();
            Method handler = database.getClass().getDeclaredMethod("getProgressHandler");
            handler.setAccessible(true);
            return ((Long) handler.invoke(database)) != 0L;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the driver no longer says whether a progress handler is set", e);
        }
    }

    private static List<String> linesOf(String output) {
        return List.of(output.split("\\R"));
    }

    private static String only(List<String> lines, String fragment) {
        List<String> found = lines.stream().filter(line -> line.contains(fragment)).toList();
        claim("the invocation writes exactly one line containing `" + fragment + "`",
                () -> assertThat(found).hasSize(1));
        return found.getFirst();
    }

    private static int indexOf(List<String> lines, String fragment) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(fragment)) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexOf(List<String> lines, String fragment) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).contains(fragment)) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> between(List<String> lines, int after, int before, String fragment) {
        List<String> found = new ArrayList<>();
        if (after < 0 || before < 0) {
            return found;
        }
        for (int i = after + 1; i < before; i++) {
            if (lines.get(i).contains(fragment)) {
                found.add(lines.get(i));
            }
        }
        return found;
    }

    private static String totalOf(String line) {
        Matcher about = ABOUT.matcher(line);
        return about.find() ? about.group(2) : "";
    }

    private static List<Integer> percentagesOf(List<String> lines) {
        List<Integer> percentages = new ArrayList<>();
        for (String line : lines) {
            Matcher about = ABOUT.matcher(line);
            if (about.find()) {
                percentages.add(Integer.parseInt(about.group(1)));
            }
        }
        return percentages;
    }

    private static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private String onlyRunOf(Path root, StageModules stage) {
        List<String> runs = jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE w.root = ? AND r.stage = ? ORDER BY w.id",
                String.class,
                Walk.canonicalRoot(root).toString(),
                stage.stage());
        return runs.getLast();
    }

    private long shingleRowsUnder(String run) {
        Long rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shingle WHERE run_id = ?", Long.class, run);
        return rows == null ? 0 : rows;
    }

    private long greatestShingleRow() {
        Long greatest = jdbcTemplate.queryForObject("SELECT MAX(rowid) FROM shingle", Long.class);
        return greatest == null ? 0 : greatest;
    }

    private boolean indexExists() {
        Long found = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'shingle_by_hash'", Long.class);
        return found != null && found > 0;
    }
}
