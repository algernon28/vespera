package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.extraction.ConverterStopsPartwayBeans;
import io.algernon.vespera.engine.ledger.SuccessiveBuildsBeans;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Raising {@code extractionAttempt} in the profile mints a new stage-2 run, which asks the converter
 * again about what it got no answer about, and every later stage runs again under a run of its own;
 * nothing under the first attempt is deleted, and putting the value back arrives at the first attempt
 * again (ADR-185, #386).
 *
 * <p>Every test runs a first invocation over {@link ConverterStopsPartwayBeans}, then scripts it afresh
 * with nothing, which is the sidecar having recovered: every occurrence it is asked about from then on
 * converts. As in {@code ServiceScopeRefusalInvocationTest}, <b>nothing empties {@code extraction_cache}
 * between invocations</b>, so the converter's count reads exactly what a later invocation could not be
 * answered about from the cache. The profile names nothing else, so stage 4's gate stays shut and every
 * invocation ends after stage 3.
 *
 * <p>The key is written through {@link ExtractionAttemptInProfile}, as YAML, so a test can write it as
 * an operator types it and take it out of the file as an operator does.
 */
@CascadeSliceTest
@Import({ConverterStopsPartwayBeans.class, SuccessiveBuildsBeans.class})
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("386")
@Link(name = "ADR-185", url = Adr.RAISING_THE_EXTRACTION_ATTEMPT_ASKS_THE_CONVERTER_AGAIN, type = "adr")
@Link(name = "ADR-183", url = Adr.THE_EXTRACTION_CACHE_KEEPS_ONLY_ANSWERS_ABOUT_THE_DOCUMENT, type = "adr")
class ExtractionAttemptInvocationTest {

    /** How many occurrences one commit of stage 2 holds. */
    private static final int CHUNK = ExtractionJobConfiguration.CHUNK_SIZE;

    /** Two chunks' worth. */
    private static final int CORPUS_SIZE = 2 * CHUNK;

    /** None of the positions: every occurrence converts. */
    private static final List<Integer> NOWHERE = List.of();

    /** The one position the converter cannot convert, as a property of what it was sent. */
    private static final List<Integer> UNCONVERTIBLE_AT = List.of(7);

    /** The one position the converter refuses while blaming itself: it reports an internal fault. */
    private static final List<Integer> CONVERTER_FAULT_AT = List.of(5);

    /** The attempt an operator writes to have the converter asked again. */
    private static final String THE_SECOND_ATTEMPT = "2";

    /** Why a test wrote the value, in the place an operator would explain themselves. */
    private static final String WHY = "set by this test";

    /** The settings the second attempt's run records after everything the first attempt's run recorded. */
    private static final String THE_SECOND_ATTEMPT_RECORDED = ",\"extractionAttempt\":2}";

    /** No row at all, or no call. */
    private static final long NONE = 0;

    /** Exactly one row, or one call. */
    private static final long ONCE = 1;

    /** One run of a stage over the corpus: the first attempt's. */
    private static final int ONE_RUN = 1;

    /** Two runs of a stage over the corpus: the first attempt's and the second's. */
    private static final int TWO_RUNS = 2;

    /** The exit code of an invocation that completed. */
    private static final int COMPLETED = 0;

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

    /**
     * Nothing scripted, the cache holding nothing from another test, the first build, and a profile
     * naming nothing. Reset before each test as well as after, because another class may have left the
     * shared converter or build in any state.
     */
    @BeforeEach
    void startClean() {
        ConverterStopsPartwayBeans.script(NOWHERE, NOWHERE, NOWHERE);
        SuccessiveBuildsBeans.theFirstBuild();
        jdbcTemplate.update("DELETE FROM extraction_cache");
        profileStore.save(ProfileFixture.profile().build());
    }

    @AfterEach
    void bringTheConverterAndTheFirstBuildBack() {
        ConverterStopsPartwayBeans.keepAnswering();
        SuccessiveBuildsBeans.theFirstBuild();
    }

    @Test
    @Story("Raising the extraction attempt asks the converter again")
    @DisplayName("A raised extraction attempt asks the converter again only about what it got no answer about, and deletes nothing")
    void aRaisedAttemptAsksAgainOnlyWhatGotNoAnswerAndDeletesNothing(@TempDir Path root) throws IOException {
        Attempts attempts = theFirstAttemptThenTheSecond(root, "asked again");

        claim(
                "the second invocation completes under a new run of the extraction",
                () -> {
                    assertThat(cli.getExitCode()).isEqualTo(COMPLETED);
                    assertThat(extractionRunsOf(root)).hasSize(TWO_RUNS).doesNotHaveDuplicates();
                });
        claim(
                "the new run records exactly the settings the first did, with the attempt, 2, after them",
                () -> assertThat(settingsOf(attempts.second())).isEqualTo(
                        withoutItsClosingBrace(settingsOf(attempts.first())) + THE_SECOND_ATTEMPT_RECORDED));
        claim(
                "it asks the converter about the " + CONVERTER_FAULT_AT.size() + " it refused while blaming"
                        + " itself and about none of the other " + (CORPUS_SIZE - CONVERTER_FAULT_AT.size())
                        + ", which it had answered about and which are answered from storage",
                () -> assertThat(ConverterStopsPartwayBeans.conversions()).isEqualTo(CONVERTER_FAULT_AT.size()));
        claim(
                "the converter, asked again, converts it, so it is measured under the new run and neither"
                        + " removed nor held as a fault there",
                () -> {
                    assertThat(metricRowsAgainst(attempts.refused(), attempts.second())).isEqualTo(ONCE);
                    assertThat(extractionFailedVerdictsAgainst(attempts.refused(), attempts.second())).isEqualTo(NONE);
                    assertThat(faultRowsAgainst(attempts.refused(), attempts.second())).isEqualTo(NONE);
                });
        claim(
                "while the one the converter could not convert is removed again under the new run, from the"
                        + " answer stored for it: asked again, the converter would now have converted it",
                () -> assertThat(extractionFailedVerdictsAgainst(attempts.unconvertible(), attempts.second()))
                        .isEqualTo(ONCE));
        claim(
                "nothing the first attempt recorded is deleted: its record of finishing, its measurements, its"
                        + " fragments, its removals and its faults are exactly as they were",
                () -> assertThat(rowsUnder(attempts.first())).isEqualTo(attempts.firstRowsBefore()));
        claim(
                "storage gains exactly one answer, the conversion the converter has now given, and loses none",
                () -> assertThat(storedAnswers()).isEqualTo(attempts.storedAnswersBefore() + ONCE));
        claim(
                "and nothing has pointed the attempt at a report: the operator chooses it, and no measurement"
                        + " informs it",
                () -> assertThat(ExtractionAttemptInProfile.measurement(profileStore)).isNull());
    }

    /** What the first two invocations of a test left, for its claims to read. */
    private record Attempts(
            String first,
            String second,
            long refused,
            long unconvertible,
            Map<String, Long> firstRowsBefore,
            long storedAnswersBefore) {}

    /** The first attempt's run, and what it removed, after one invocation. */
    private record FirstAttempt(String run, long refused, long unconvertible) {}

    /**
     * One invocation with the converter refusing one occurrence while blaming itself and unable to
     * convert another, which completes, so the refused one is removed under the first attempt.
     */
    private FirstAttempt theFirstAttempt(Path root, String salt) throws IOException {
        ConverterStopsPartwayBeans.script(NOWHERE, UNCONVERTIBLE_AT, CONVERTER_FAULT_AT);
        writeCorpus(root, salt);
        cli.run("run", root.toString());

        List<String> runs = extractionRunsOf(root);
        claim(
                "the first invocation completes under one run of the extraction",
                () -> {
                    assertThat(cli.getExitCode()).isEqualTo(COMPLETED);
                    assertThat(runs).hasSize(ONE_RUN);
                });
        String run = runs.getFirst();
        List<Long> refused = faultedOccurrencesUnder(run);
        List<Long> unconvertible = removedWithAMeasurementUnder(run);
        claim(
                "the converter blamed itself for " + CONVERTER_FAULT_AT.size() + " and blamed "
                        + UNCONVERTIBLE_AT.size() + " other on what it was sent, and since the stage completed,"
                        + " both are removed",
                () -> {
                    assertThat(refused).hasSize(CONVERTER_FAULT_AT.size());
                    assertThat(unconvertible).hasSize(UNCONVERTIBLE_AT.size());
                    assertThat(extractionFailedVerdictsAgainst(refused.getFirst(), run)).isEqualTo(ONCE);
                });
        return new FirstAttempt(run, refused.getFirst(), unconvertible.getFirst());
    }

    /**
     * {@link #theFirstAttempt}, then a second invocation with the converter converting everything and
     * the attempt raised to 2.
     */
    private Attempts theFirstAttemptThenTheSecond(Path root, String salt) throws IOException {
        FirstAttempt first = theFirstAttempt(root, salt);
        Map<String, Long> firstRowsBefore = rowsUnder(first.run());
        long storedAnswersBefore = storedAnswers();

        ExtractionAttemptInProfile.write(profileStore, THE_SECOND_ATTEMPT, WHY);
        ConverterStopsPartwayBeans.script(NOWHERE, NOWHERE, NOWHERE);
        cli.run("run", root.toString());

        List<String> runs = extractionRunsOf(root);
        claim(
                "the invocation after the attempt is raised to " + THE_SECOND_ATTEMPT + " runs the extraction"
                        + " under a second run",
                () -> assertThat(runs).hasSize(TWO_RUNS));
        return new Attempts(
                first.run(),
                runs.getLast(),
                first.refused(),
                first.unconvertible(),
                firstRowsBefore,
                storedAnswersBefore);
    }

    /**
     * Writes {@link #CORPUS_SIZE} files, each with bytes of its own. {@code salt} keeps one test's corpus
     * from sharing content, and so stored answers, with another's.
     */
    private static void writeCorpus(Path root, String salt) throws IOException {
        for (int position = 1; position <= CORPUS_SIZE; position++) {
            Files.writeString(
                    root.resolve(String.format("document-%02d.txt", position)),
                    "Entry " + position + " of the " + salt + " corpus records the lighthouse keeper's log,"
                            + " the lamp oil delivered and the ships sighted in the spring of year " + position
                            + ", with remarks on fog and the state of the lens.");
        }
    }

    /** The spelling the walk recorded for {@code root} (ADR-055), which is what {@code walk.root} holds. */
    private static String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }

    /** Every run of {@code stage} over {@code root}, in the order the runs were minted. */
    private List<String> runsOf(Path root, String stage) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE w.root = ? AND r.stage = ? ORDER BY w.id, r.rowid",
                String.class,
                walkRoot(root),
                stage);
    }

    private List<String> extractionRunsOf(Path root) {
        return runsOf(root, StageModules.EXTRACTION.stage());
    }

    private String settingsOf(String run) {
        return jdbcTemplate.queryForObject("SELECT config_consumed FROM run WHERE id = ?", String.class, run);
    }

    /** {@code json}, an object, with the brace that closes it taken off so a member can follow. */
    private static String withoutItsClosingBrace(String json) {
        assertThat(json).endsWith("}");
        return json.substring(0, json.length() - 1);
    }

    /** Every row stage 2 writes under {@code run}, counted per table, its record of finishing included. */
    private Map<String, Long> rowsUnder(String run) {
        Map<String, Long> rows = new LinkedHashMap<>();
        for (String table : List.of("finished_step", "extraction_metric", "shingle", "verdict", "extraction_fault")) {
            rows.put(table, countOf("SELECT COUNT(*) FROM " + table + " WHERE run_id = ?", run));
        }
        return rows;
    }

    private long storedAnswers() {
        return countOf("SELECT COUNT(*) FROM extraction_cache");
    }

    /** The occurrences carrying an extraction fault row under {@code run}. */
    private List<Long> faultedOccurrencesUnder(String run) {
        return jdbcTemplate.queryForList(
                "SELECT occurrence_id FROM extraction_fault WHERE run_id = ? ORDER BY occurrence_id", Long.class, run);
    }

    /**
     * The occurrences removed as a failed conversion under {@code run} that also carry a measurement
     * there: a failure the converter blamed on what it was sent, which is measured as it is removed.
     */
    private List<Long> removedWithAMeasurementUnder(String run) {
        return jdbcTemplate.queryForList(
                "SELECT v.occurrence_id FROM verdict v WHERE v.run_id = ? AND v.kind = 'EXTRACTION_FAILED'"
                        + " AND EXISTS (SELECT 1 FROM extraction_metric m"
                        + " WHERE m.occurrence_id = v.occurrence_id AND m.run_id = v.run_id)"
                        + " ORDER BY v.occurrence_id",
                Long.class,
                run);
    }

    private long metricRowsAgainst(long occurrence, String run) {
        return countOf("SELECT COUNT(*) FROM extraction_metric WHERE occurrence_id = ? AND run_id = ?", occurrence, run);
    }

    private long faultRowsAgainst(long occurrence, String run) {
        return countOf("SELECT COUNT(*) FROM extraction_fault WHERE occurrence_id = ? AND run_id = ?", occurrence, run);
    }

    private long extractionFailedVerdictsAgainst(long occurrence, String run) {
        return countOf(
                "SELECT COUNT(*) FROM verdict WHERE occurrence_id = ? AND run_id = ? AND kind = 'EXTRACTION_FAILED'",
                occurrence,
                run);
    }

    private long countOf(String sql, Object... arguments) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return count == null ? NONE : count;
    }
}
