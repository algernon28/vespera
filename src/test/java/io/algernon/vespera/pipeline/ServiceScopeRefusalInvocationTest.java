package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ConverterStopsPartwayBeans;
import io.algernon.vespera.ledger.SuccessiveBuildsBeans;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
 * Stage 2 run again over content the converter refused while blaming itself asks the converter again,
 * and is not served the stored refusal (ADR-183, #383); content the converter answered about is still
 * served from the extraction cache.
 *
 * <p>Every test runs a first invocation over {@link ConverterStopsPartwayBeans} with outcomes scripted
 * by the order documents are first asked about, then scripts it afresh with nothing, which is the
 * sidecar having recovered: every document it is asked about from then on converts. <b>Unlike {@code
 * ExtractionResumeInvocationTest}, nothing here empties {@code extraction_cache} between
 * invocations</b>, because what the cache keeps is what these tests are about. The converter's count
 * therefore reads exactly the documents the second invocation could not be answered about from the
 * cache.
 *
 * <p>Each test fails where the cache keeps the refusal, so that the second invocation never asks the
 * converter: in the first the refused document is faulted again; in the second the five stored refusals
 * reach the drain one after another and stop the step, with the converter never asked; in the third the
 * refusal an earlier build stored is served, and the document is faulted (#681).
 */
@CascadeSliceTest
@Import({ConverterStopsPartwayBeans.class, SuccessiveBuildsBeans.class})
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("383")
@Link(name = "ADR-183", url = Adr.THE_EXTRACTION_CACHE_KEEPS_ONLY_ANSWERS_ABOUT_THE_DOCUMENT, type = "adr")
@Link(name = "ADR-181", url = Adr.A_STOPPED_STAGE_2_RESUMES_FROM_WHAT_ITS_COMMITTED_CHUNKS_RECORDED, type = "adr")
class ServiceScopeRefusalInvocationTest {

    /** How many occurrences one commit of stage 2 holds. */
    private static final int CHUNK = ExtractionJobConfiguration.CHUNK_SIZE;

    /** Three chunks' worth. */
    private static final int CORPUS_SIZE = 3 * CHUNK;

    /** None of the positions: every document converts. */
    private static final List<Integer> NOWHERE = List.of();

    /** The one position the converter cannot convert, as a property of the document. */
    private static final List<Integer> UNCONVERTIBLE_AT = List.of(7);

    /** The one position the converter refuses while blaming itself. */
    private static final List<Integer> CONVERTER_FAULT_AT = List.of(5);

    /** No row at all. */
    private static final long NONE = 0;

    /** Exactly one row. */
    private static final long ONCE = 1;

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
     * naming nothing, so stage 4's gate stays shut and each invocation ends after stage 3.
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

    /**
     * A commit to {@code extraction} mints a new stage-2 run over the same walk, so every survivor is
     * read again: the shape of ADR-140 section 5's "run it again" that reads the whole stage. The
     * converter, scripted afresh, would convert the document it could not convert the first time, so
     * that document's removal under the new run shows it was answered from the cache, not asked again.
     */
    @Test
    @Story("A refusal the converter blamed on itself is asked about again")
    @DisplayName("Extraction run again asks the converter again about a document it refused while blaming itself, and nothing else")
    void aNewRunAsksAgainAboutTheRefusalAndIsServedEveryOtherAnswer(@TempDir Path root) throws IOException {
        ConverterStopsPartwayBeans.script(NOWHERE, UNCONVERTIBLE_AT, CONVERTER_FAULT_AT);
        writeCorpus(root, "run again");

        cli.run("run", root.toString());
        String first = onlyExtractionRunOf(root);
        List<Long> refused = faultedOccurrencesUnder(first);
        List<Long> unconvertible = removedWithAMeasurementUnder(first);

        claim(
                "the first invocation completes, the converter having blamed itself for "
                        + CONVERTER_FAULT_AT.size() + " document and blamed " + UNCONVERTIBLE_AT.size()
                        + " other on the document itself",
                () -> {
                    assertThat(cli.getExitCode()).isEqualTo(COMPLETED);
                    assertThat(refused).hasSize(CONVERTER_FAULT_AT.size());
                    assertThat(unconvertible).hasSize(UNCONVERTIBLE_AT.size());
                });
        claim(
                "and since the stage completed, the document the converter blamed itself for is removed as a"
                        + " failed conversion",
                () -> assertThat(extractionFailedVerdictsAgainst(refused.getFirst(), first)).isEqualTo(ONCE));

        ConverterStopsPartwayBeans.script(NOWHERE, NOWHERE, NOWHERE);
        SuccessiveBuildsBeans.aCommitTo("extraction");
        cli.run("run", root.toString());
        List<String> runs = extractionRunsOf(root);
        String second = runs.getLast();

        claim(
                "the second invocation is under a new run of the extraction, which reads every document again",
                () -> assertThat(runs).hasSize(2).doesNotHaveDuplicates());
        claim(
                "it asks the converter about the " + CONVERTER_FAULT_AT.size() + " document it refused while"
                        + " blaming itself and about none of the other " + (CORPUS_SIZE - CONVERTER_FAULT_AT.size())
                        + ", which it had answered about and which are answered from storage",
                () -> assertThat(ConverterStopsPartwayBeans.conversions()).isEqualTo(CONVERTER_FAULT_AT.size()));
        claim(
                "the converter, asked again, converts that document, so it is measured under the new run and"
                        + " neither removed nor held as a fault there",
                () -> {
                    assertThat(metricRowsAgainst(refused.getFirst(), second)).isEqualTo(ONCE);
                    assertThat(extractionFailedVerdictsAgainst(refused.getFirst(), second)).isEqualTo(NONE);
                    assertThat(faultRowsAgainst(refused.getFirst(), second)).isEqualTo(NONE);
                });
        claim(
                "while the document the converter could not convert is removed again under the new run, from"
                        + " the answer stored for it: asked again, the converter would now have converted it",
                () -> assertThat(extractionFailedVerdictsAgainst(unconvertible.getFirst(), second)).isEqualTo(ONCE));
    }

    /**
     * Writes {@link #CORPUS_SIZE} files, each with bytes of its own. {@code salt} keeps one test's corpus
     * from sharing content, and so cache rows, with another's.
     */
    private static void writeCorpus(Path root, String salt) throws IOException {
        for (int position = 1; position <= CORPUS_SIZE; position++) {
            Files.writeString(
                    root.resolve(String.format("document-%02d.txt", position)),
                    "Document " + position + " of the " + salt + " corpus records the lighthouse keeper's log,"
                            + " the lamp oil delivered and the ships sighted in the spring of year " + position
                            + ", with remarks on fog and the state of the lens.");
        }
    }

    /** The spelling the walk recorded for {@code root} (ADR-055), which is what {@code walk.root} holds. */
    private static String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }

    /** Every stage-2 run over {@code root}, oldest walk first, then in the order the runs were minted. */
    private List<String> extractionRunsOf(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE w.root = ? AND r.stage = ? ORDER BY w.id, r.rowid",
                String.class,
                walkRoot(root),
                StageModules.EXTRACTION.stage());
    }

    private String onlyExtractionRunOf(Path root) {
        List<String> runs = extractionRunsOf(root);
        claim(
                "the extraction over this corpus has one run so far, so every count below is about that run",
                () -> assertThat(runs).hasSize(1));
        return runs.getFirst();
    }

    /** The occurrences carrying an extraction fault row under {@code run}. */
    private List<Long> faultedOccurrencesUnder(String run) {
        return jdbcTemplate.queryForList(
                "SELECT occurrence_id FROM extraction_fault WHERE run_id = ? ORDER BY occurrence_id", Long.class, run);
    }

    /**
     * The occurrences removed as a failed conversion under {@code run} that also carry a measurement
     * there: a failure the converter blamed on the document, which is measured as it is removed. One
     * the converter blamed on itself carries no measurement.
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

    private List<String> storedStatusesFor(String contentHash) {
        return jdbcTemplate.queryForList(
                "SELECT status FROM extraction_cache WHERE content_hash = ?", String.class, contentHash);
    }

    private long metricRowsUnder(String run) {
        return countOf("SELECT COUNT(*) FROM extraction_metric WHERE run_id = ?", run);
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

    private List<String> verdictKindsUnder(String run) {
        return jdbcTemplate.queryForList("SELECT kind FROM verdict WHERE run_id = ? ORDER BY kind", String.class, run);
    }

    private boolean stageTwoFinished(String run) {
        return countOf("SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?", run, StepNames.EXTRACTION)
                > NONE;
    }

    private long countOf(String sql, Object... arguments) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return count == null ? NONE : count;
    }
}
