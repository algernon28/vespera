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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.step.item.ChunkOrientedStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Stage 2's fault row is written in the chunk its occurrence was set aside in, on the shipped step, and stands
 * or falls with that chunk (ADR-220 section 14).
 *
 * <p>{@code ASetAsideIsHeardInsideItsChunksTransactionTest} holds the library to hearing a set-aside inside the
 * chunk's transaction, on a step built to stage 2's shape. This class holds the step {@link
 * ExtractionJobConfiguration} builds, driven through the whole command: {@link SetAsideProbe} listens beside the
 * fault recorder on the {@code extractionStep} bean itself, so a change to how that step is built that moved
 * the set-aside out of the chunk's transaction, onto another thread or into a transaction of its own fails here.
 *
 * <p>The converter is {@link ConverterStopsPartwayBeans}: it blames itself for the second document it is asked
 * about, and in the second test stops answering partway through the one chunk the corpus makes, which fails
 * the step and rolls that chunk back. Stopping the step logs the cause at {@code ERROR}, as it does in
 * {@code ExtractionResumeInvocationTest}; the claim it buys, that a fault row goes with its chunk, has no
 * quieter way to be made.
 */
@CascadeSliceTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import({ConverterStopsPartwayBeans.class, SetAsideProbe.class})
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("458")
@Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
@Link(name = "ADR-139", url = Adr.A_REFUSED_CONVERSION_LEAVES_A_FAULT_ROW, type = "adr")
@Link(name = "ADR-181", url = Adr.A_STOPPED_STAGE_2_RESUMES_FROM_WHAT_ITS_COMMITTED_CHUNKS_RECORDED, type = "adr")
class StageTwoWritesEachFaultInItsChunkInvocationTest {

    /** How many occurrences one commit of stage 2 holds. */
    private static final int CHUNK = ExtractionJobConfiguration.CHUNK_SIZE;

    /** A few documents, all inside one chunk, for the stage that completes. */
    private static final int A_FEW = 3;

    /** The second document the converter is asked about is the one it blames itself for. */
    private static final List<Integer> CONVERTER_FAULT_AT = List.of(2);

    private static final List<Integer> NOWHERE = List.of();

    /** Half the one chunk is answered, the fault among it, and every conversion after it is refused. */
    private static final int ANSWERED_BEFORE_THE_STOP = CHUNK / 2;

    private static final String THE_WHOLE_FAULT_REASON = "internal: " + ConverterStopsPartwayBeans.FAULT_MESSAGE;

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

    /** A profile naming nothing shuts stage 4's gate, so each invocation ends after stage 3. */
    @BeforeEach
    void startClean() {
        ConverterStopsPartwayBeans.script(NOWHERE, NOWHERE, CONVERTER_FAULT_AT);
        SetAsideProbe.forget();
        jdbcTemplate.update("DELETE FROM extraction_cache");
        profileStore.save(ProfileFixture.profile().build());
    }

    @AfterEach
    void bringTheConverterBack() {
        ConverterStopsPartwayBeans.keepAnswering();
    }

    @Test
    @Story("A file set aside is recorded when it is set aside, and resolved at the end of the stage")
    @DisplayName("The shipped stage 2 hears a file set aside inside its chunk's transaction, which already holds the file's fault row and commits it")
    void theShippedStepHearsASetAsideInsideTheChunkThatWritesItsRow(@TempDir Path root) throws IOException {
        writeCorpus(root, A_FEW, "completed");
        String invokedOn = Thread.currentThread().getName();

        cli.run("run", root.toString());

        claim(
                "the probe listened on the step the job configuration built, a chunk-oriented step, and nothing"
                        + " replaced it",
                () -> assertThat(SetAsideProbe.STEP_PROBED.get()).isEqualTo(ChunkOrientedStep.class));
        claim(
                "the step heard one file set aside, the one the converter blamed itself for",
                () -> assertThat(SetAsideProbe.HEARD).hasSize(1));
        SetAsideProbe.Heard heard = SetAsideProbe.HEARD.getFirst();
        claim(
                "it heard it on the thread the command was invoked on, inside a transaction, and that"
                        + " transaction already held the file's fault row, written where the set-aside was heard",
                () -> {
                    assertThat(heard.thread()).isEqualTo(invokedOn);
                    assertThat(heard.inATransaction()).isTrue();
                    assertThat(heard.faultRowsTheTransactionSaw()).isEqualTo(1);
                });
        claim(
                "the transaction is the chunk's own, the one its other files were written in, and it committed",
                () -> {
                    assertThat(SetAsideProbe.WRITTEN_IN).anySatisfy(written -> assertThat(written)
                            .isSameAs(heard.transaction()));
                    assertThat(heard.howTheTransactionEnded().get()).isEqualTo("committed");
                });
        claim(
                "and the stage having completed, the row stands and is resolved into extraction-failed, its reason"
                        + " the category and the converter's message",
                () -> {
                    assertThat(faultRowsAgainst(heard)).isEqualTo(1);
                    assertThat(verdictsAgainst(heard))
                            .containsExactly(Map.of("kind", "EXTRACTION_FAILED", "reason", THE_WHOLE_FAULT_REASON));
                });
    }

    @Test
    @Story("A file set aside is recorded when it is set aside, and resolved at the end of the stage")
    @DisplayName("A chunk that rolls back takes its fault rows with it, and the next invocation sets the file aside again and records it once")
    void aChunkThatRollsBackTakesItsFaultRowsWithIt(@TempDir Path root) throws IOException {
        writeCorpus(root, CHUNK, "rolled back");

        ConverterStopsPartwayBeans.stopAnsweringAfter(ANSWERED_BEFORE_THE_STOP);
        cli.run("run", root.toString());
        String run = onlyExtractionRunOf(root);

        claim(
                "the step heard the file set aside before the converter stopped, so the fault was reached",
                () -> assertThat(SetAsideProbe.HEARD).hasSize(1));
        SetAsideProbe.Heard heard = SetAsideProbe.HEARD.getFirst();
        claim(
                "when it was heard, its chunk's transaction held its fault row",
                () -> assertThat(heard.faultRowsTheTransactionSaw()).isEqualTo(1));
        claim(
                "the converter stopping later in the same chunk rolled that transaction back",
                () -> assertThat(heard.howTheTransactionEnded().get()).isEqualTo("rolled back"));
        claim(
                "and the fault row went with it: the run holds no fault row, no measurement and no verdict, the one"
                        + " chunk having left nothing, and the file is neither judged nor recorded as faulted",
                () -> {
                    assertThat(countUnder("extraction_fault", run)).isZero();
                    assertThat(countUnder("extraction_metric", run)).isZero();
                    assertThat(countUnder("verdict", run)).isZero();
                });

        SetAsideProbe.forget();
        jdbcTemplate.update("DELETE FROM extraction_cache");
        ConverterStopsPartwayBeans.keepAnswering();
        cli.run("run", root.toString());

        claim(
                "the next invocation read the file again and set it aside again, the converter blaming itself for"
                        + " the same bytes",
                () -> assertThat(SetAsideProbe.HEARD)
                        .extracting(SetAsideProbe.Heard::occurrence)
                        .containsExactly(heard.occurrence()));
        claim(
                "and, completing, recorded it once: one fault row under the run and one extraction-failed verdict",
                () -> {
                    assertThat(faultRowsAgainst(heard)).isEqualTo(1);
                    assertThat(verdictsAgainst(heard))
                            .containsExactly(Map.of("kind", "EXTRACTION_FAILED", "reason", THE_WHOLE_FAULT_REASON));
                    assertThat(countUnder("extraction_metric", run)).isEqualTo(CHUNK - 1);
                });
    }

    /** {@code count} files with bytes of their own, so stage 1 removes none as a copy of another. */
    private static void writeCorpus(Path root, int count, String salt) throws IOException {
        for (int position = 1; position <= count; position++) {
            Files.writeString(
                    root.resolve(String.format("document-%02d.txt", position)),
                    "Document " + position + " of the " + salt + " corpus lists the lighthouses of the coast"
                            + " and the years each was first lit, with notes on its keepers.");
        }
    }

    private String onlyExtractionRunOf(Path root) {
        List<String> runs = jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE w.root = ? AND r.stage = ?",
                String.class,
                Walk.canonicalRoot(root).toString(),
                StageModules.EXTRACTION.stage());
        claim(
                "the extraction over this corpus has one run, so every count below is about that run",
                () -> assertThat(runs).hasSize(1));
        return runs.getFirst();
    }

    private long countUnder(String table, String run) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE run_id = ?", Long.class, run);
        return count == null ? 0 : count;
    }

    private long faultRowsAgainst(SetAsideProbe.Heard heard) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM extraction_fault WHERE occurrence_id = ?", Long.class, heard.occurrence().value());
        return count == null ? 0 : count;
    }

    private List<Map<String, Object>> verdictsAgainst(SetAsideProbe.Heard heard) {
        return jdbcTemplate.queryForList(
                "SELECT kind, reason FROM verdict WHERE occurrence_id = ?", heard.occurrence().value());
    }
}
