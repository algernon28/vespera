package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
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
 * Stage 6a over two seed partitions, through a whole invocation (ADR-223 section 3): it reads one
 * partition's members, arranges and records its clusters and reads them back before it reads anything of the
 * next; it writes {@code arrangement.html} beside its target and moves it into place; and a step that fails
 * at its second partition leaves the page that was there.
 *
 * <p><b>Why an invocation.</b> What 6a holds in memory cannot be asserted, and what {@code ArrangementReport}
 * writes is held without a tasklet ({@code ArrangementPageIsWrittenAsItsPartitionsComeTest}). What only the
 * built tasklet can show is the order of its reads, which its timed lines state, and what is on disk when it
 * stops.
 *
 * <p><b>The second partition is made by hand.</b> Every document this fixture converts comes back alike and
 * there is one seed, so a corpus of its own yields one partition. After a first invocation has scored and
 * clustered, the last two documents under the scoring run are moved under a second winning seed, in the
 * membership and in the scores alike, the record that 6a finished is deleted, and the next invocation arranges
 * again under the same runs. The second seed is the corpus's first document: 6a asks a seed for nothing but
 * its path. The first partition keeps at least three documents, so it is the larger and comes first.
 *
 * <p><b>The failure is a member with no score</b>, in the second partition: 6a refuses to arrange a document
 * it cannot place (ADR-105), and by then it has written the first partition's part of the page.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Arrangement")
@Feature("Arranging the documents")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
class ArrangementGoesThroughTwoPartitionsInvocationTest {

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    /** Six documents: two to move under the second seed, and at least three left under the first. */
    private static final int SIX_DOCUMENTS = 6;

    /** How many documents the second partition is given. */
    private static final int UNDER_THE_SECOND_SEED = 2;

    private static final String STAGE_SIX_A = "Stage 6a (arrangement)";
    private static final String ARRANGEMENT_FINISHED = "The arrangement step finished under ";

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

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    @BeforeEach
    void captureOperatorLines() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        SeedScriptedExtractionBeans.stopMovingAway();
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("io.algernon.vespera");
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseOperatorLines() {
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("The arrangement is made one exemplar at a time")
    @DisplayName("With two exemplars, everything of the first is read, arranged and written to the page before anything of the second is read")
    void arrangesOnePartitionThenTheNext(@TempDir Path root, @TempDir Path seeds) throws IOException {
        TwoPartitions two = twoPartitionsToArrange(root, seeds);
        logged.list.clear();

        cli.run("run", root.toString());

        claim("the arrangement was made again, which its own line says", () -> assertThat(operatorLines())
                .anyMatch(line -> line.startsWith(ARRANGEMENT_FINISHED)));
        claim(
                "it says each read it makes, once and in this order: the exemplars that have documents under"
                        + " them; the first exemplar's documents and then the groups recorded for it; and only"
                        + " then the second exemplar's documents and its groups. No read of every document, or"
                        + " of every group, is made, and nothing of the second exemplar is read while the first"
                        + " is in hand",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_SIX_A))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(STAGE_SIX_A, "the seed partitions"),
                                StatementLines.timedRead(STAGE_SIX_A, "the members of partition 1 of 2"),
                                StatementLines.timedRead(STAGE_SIX_A, "the recorded clusters of partition 1 of 2"),
                                StatementLines.timedRead(STAGE_SIX_A, "the members of partition 2 of 2"),
                                StatementLines.timedRead(STAGE_SIX_A, "the recorded clusters of partition 2 of 2"))));
        claim(
                "the larger exemplar is placed first and the one given two documents second, each with every"
                        + " document under it counted once in its groups: the sizes the arrangement stored for"
                        + " an exemplar's groups add up to the documents grouped under that exemplar, which is"
                        + " what the final documents later take as how many documents they list",
                () -> {
                    assertThat(placeOf(two.arrangement(), two.firstSeed())).isEqualTo(1);
                    assertThat(placeOf(two.arrangement(), two.secondSeed())).isEqualTo(2);
                    assertThat(documentsStoredUnder(two.arrangement(), two.firstSeed()))
                            .isEqualTo(documentsGroupedUnder(two.scoring(), two.firstSeed()))
                            .isGreaterThan(UNDER_THE_SECOND_SEED);
                    assertThat(documentsStoredUnder(two.arrangement(), two.secondSeed()))
                            .isEqualTo(documentsGroupedUnder(two.scoring(), two.secondSeed()))
                            .isEqualTo(UNDER_THE_SECOND_SEED);
                });
        String page = thePage();
        claim(
                "the page the operator approves is whole and in its place: it heads a table for each exemplar,"
                        + " the second after the first, and ends as a finished page does",
                () -> assertThat(page)
                        .containsSubsequence(
                                ReportPage.heading(2, two.firstSeedPath()), ReportPage.heading(2, two.secondSeedPath()))
                        .endsWith(ReportPage.tail()));
        claim(
                "and the file it was written to before it was moved into place is gone",
                () -> assertThat(thePartFile()).doesNotExist());
    }

    @Test
    @Story("A page that cannot be finished does not replace the page that was there")
    @DisplayName("When the arrangement fails at its second exemplar, the page the operator had is still there, whole and unchanged")
    void aStepThatFailsPartwayLeavesThePageThatWasThere(@TempDir Path root, @TempDir Path seeds) throws IOException {
        TwoPartitions two = twoPartitionsToArrange(root, seeds);
        cli.run("run", root.toString());
        String thePageBefore = thePage();
        aDocumentOfTheSecondPartitionLosesItsScore(two);
        forgetThatTheArrangementFinished(two.arrangement());
        logged.list.clear();

        Throwable thrown = catchThrowable(() -> cli.run("run", root.toString()));

        claim(
                "the page that was there names both exemplars and is a finished page, so there is something to"
                        + " lose",
                () -> assertThat(thePageBefore)
                        .contains(ReportPage.heading(2, two.secondSeedPath()))
                        .endsWith(ReportPage.tail()));
        claim(
                "the invocation failed: a document with no score cannot be placed, and the arrangement stops"
                        + " on it without finishing",
                () -> {
                    assertThat(thrown != null || cli.getExitCode() != 0)
                            .as("whether the invocation threw or ended with a failure's exit code")
                            .isTrue();
                    assertThat(operatorLines()).noneMatch(line -> line.startsWith(ARRANGEMENT_FINISHED));
                });
        claim(
                "it had read the first exemplar and gone on to the second before it stopped, so its page was"
                        + " part written when it failed",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_SIX_A))
                        .containsSubsequence(
                                StatementLines.timedRead(STAGE_SIX_A, "the recorded clusters of partition 1 of 2").getFirst(),
                                StatementLines.timedRead(STAGE_SIX_A, "the members of partition 2 of 2").getFirst()));
        claim(
                "the page the operator had is still there, character for character: the name it asks to be"
                        + " approved under still heads a whole page, and not the first exemplar's table alone",
                () -> assertThat(thePage()).isEqualTo(thePageBefore));
    }

    /** The two seeds of the partitions made by hand, each with its path, and the runs they sit under. */
    private record TwoPartitions(
            String scoring, String arrangement, long firstSeed, String firstSeedPath, long secondSeed, String secondSeedPath) {}

    /**
     * A corpus arranged once under one seed, then split: the last two documents under the scoring run are
     * moved under a second winning seed and 6a's finish is forgotten, so the next invocation arranges two
     * partitions under the same runs.
     */
    private TwoPartitions twoPartitionsToArrange(Path root, Path seeds) throws IOException {
        for (int document = 1; document <= SIX_DOCUMENTS; document++) {
            Files.writeString(root.resolve("corpus-" + document + ".txt"), "corpus document " + document);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
        cli.run("run", root.toString());

        String arrangement = jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString());
        String scoring = jdbcTemplate.queryForObject(
                "SELECT upstream_run_id FROM run_upstream WHERE run_id = ?", String.class, arrangement);
        List<Long> documents = jdbcTemplate.queryForList(
                "SELECT occurrence_id FROM document_cluster WHERE run_id = ? ORDER BY occurrence_id", Long.class, scoring);
        if (documents.size() < UNDER_THE_SECOND_SEED + 3) {
            throw new IllegalStateException("this fixture needs five documents grouped under the scoring run, to"
                    + " leave three under the first seed, and this walk produced " + documents.size());
        }
        long firstSeed = jdbcTemplate.queryForObject(
                "SELECT winning_seed_occurrence_id FROM document_cluster WHERE run_id = ? AND occurrence_id = ?",
                Long.class,
                scoring,
                documents.getFirst());
        long secondSeed = documents.getFirst();
        for (Long moved : documents.subList(documents.size() - UNDER_THE_SECOND_SEED, documents.size())) {
            jdbcTemplate.update(
                    "UPDATE document_cluster SET winning_seed_occurrence_id = ? WHERE run_id = ? AND occurrence_id = ?",
                    secondSeed,
                    scoring,
                    moved);
            jdbcTemplate.update(
                    "UPDATE relevance_score SET winning_seed_occurrence_id = ? WHERE run_id = ? AND occurrence_id = ?",
                    secondSeed,
                    scoring,
                    moved);
        }
        forgetThatTheArrangementFinished(arrangement);
        return new TwoPartitions(scoring, arrangement, firstSeed, pathOf(firstSeed), secondSeed, pathOf(secondSeed));
    }

    /** The last document under the second seed keeps its place in the membership and loses its score. */
    private void aDocumentOfTheSecondPartitionLosesItsScore(TwoPartitions two) {
        Long last = jdbcTemplate.queryForObject(
                "SELECT MAX(occurrence_id) FROM document_cluster WHERE run_id = ? AND winning_seed_occurrence_id = ?",
                Long.class,
                two.scoring(),
                two.secondSeed());
        jdbcTemplate.update("DELETE FROM relevance_score WHERE run_id = ? AND occurrence_id = ?", two.scoring(), last);
    }

    private void forgetThatTheArrangementFinished(String arrangement) {
        jdbcTemplate.update(
                "DELETE FROM finished_step WHERE run_id = ? AND step = ?", arrangement, StepNames.ARRANGEMENT);
    }

    private int placeOf(String arrangement, long seed) {
        Integer place = jdbcTemplate.queryForObject(
                "SELECT MIN(partition_order) FROM cluster WHERE run_id = ? AND winning_seed_occurrence_id = ?",
                Integer.class,
                arrangement,
                seed);
        return place == null ? 0 : place;
    }

    private int documentsStoredUnder(String arrangement, long seed) {
        Integer documents = jdbcTemplate.queryForObject(
                "SELECT SUM(document_count) FROM cluster WHERE run_id = ? AND winning_seed_occurrence_id = ?",
                Integer.class,
                arrangement,
                seed);
        return documents == null ? 0 : documents;
    }

    private int documentsGroupedUnder(String scoring, long seed) {
        Integer documents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_cluster WHERE run_id = ? AND winning_seed_occurrence_id = ?",
                Integer.class,
                scoring,
                seed);
        return documents == null ? 0 : documents;
    }

    private String pathOf(long occurrence) {
        return jdbcTemplate.queryForObject("SELECT path FROM file_occurrence WHERE id = ?", String.class, occurrence);
    }

    private static String thePage() throws IOException {
        return Files.readString(workingDirectory.resolve(ArrangementTasklet.ARRANGEMENT_FILE_NAME), StandardCharsets.UTF_8);
    }

    private static Path thePartFile() {
        return workingDirectory.resolve(ArrangementTasklet.ARRANGEMENT_FILE_NAME + ".part");
    }

    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
