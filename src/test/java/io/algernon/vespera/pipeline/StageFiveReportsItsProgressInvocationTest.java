package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
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
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What stage 5 and stage 6a say while they go through their loops (ADR-192 section 4, #412), over a corpus
 * small enough that every item is a line.
 *
 * <p>On 2026-10-04 stage 5c ran for 30 minutes less a second over 4,275 corpus survivors and 19 usable seeds,
 * until the operator stopped it, with nothing in the log to say how far it had got. ADR-192 gives every loop
 * of stage 5 and 6a that reads or writes the database or a file, or calls a model, a counter of its own on
 * ADR-093's line, named for what it counts. Under a total of 40 the interval is one item, so no claim here
 * depends on a count crossing a fraction of the total, or on a clock.
 *
 * <p>Every claim reads the lines the invocation wrote, through a list appender on the application's own
 * logger, and every test asserts that every line of every counter it names was written by {@code
 * StageProgress}'s logger. The corpus is {@value #CORPUS_DOCUMENTS} documents and one seed, and the scripted
 * embedder answers every chunk with the same vector, so every document lands in the one partition the one
 * seed wins: these fixtures cannot reach two partitions (ADR-192, Tests). 5c's two running counters over
 * chunks write their first line at the 1,000th chunk, which this corpus does not reach, and are not pinned
 * here.
 *
 * <p><b>Part (c) of ADR-192.</b> This compiles against main and is red there at the claims that the counters'
 * lines are there, each after a claim that the stage ran; part (c) moves it into {@code src/test} and turns it
 * green. The claims that a loop which did not run writes no counter pass on main and have to go on passing.
 *
 * <p><b>The claims about statements came with part (b) of ADR-193</b> (ADR-193 section 6, ADR-204 section 3,
 * #411): every statement stage 5b to stage 6a issues outside a loop has a line before it and a line after it
 * with the seconds it took, once each, in the order the step issues them. 5b's two reads of the extraction
 * metrics are counted, and their totals are left out of the comparison here, being pinned where {@code
 * embedding} hands them over ({@code EmbeddingStatementProgressOrderTest}).
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
@Link(name = "ADR-093", url = Adr.LOGGING_IS_EXPLICIT_AND_PROCESS_SCOPED, type = "adr")
class StageFiveReportsItsProgressInvocationTest {

    /** How many corpus documents each test writes, so a counter over them reads one, two and three of three. */
    static final int CORPUS_DOCUMENTS = 3;

    /** The one seed every test writes. */
    private static final int ONE_SEED = 1;

    /** The one partition the one seed wins. */
    private static final int ONE_PARTITION = 1;

    /** Three documents fit one block of the 4,096 a block holds: one block, compared with itself, one pair. */
    private static final int ONE_PAIR_OF_BLOCKS = 1;

    /** More than the twelve a band is sampled from, so a sample is smaller than the documents scored. */
    private static final int MORE_DOCUMENTS_THAN_ONE_BAND_IS_SAMPLED_FROM = 15;

    /** One answer recorded for the seed set, in the floor test. */
    private static final int ONE_ANSWER = 1;

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** Above anything the scripted embedder can produce, so every scored document falls under it. */
    private static final String A_FLOOR_ABOVE_EVERY_SCORE = "1.5";

    private static final String SEEDS_RECORDED = "Stage 5a (seed extraction, seeds recorded)";
    private static final String EMBEDDING_SURVIVORS = "Stage 5c (embedding scoring, corpus survivors)";
    private static final String EMBEDDING_SEEDS = "Stage 5c (embedding scoring, seeds)";
    private static final String SEED_FILES = "Stage 5d (relevance scoring, seed files hashed)";
    private static final String SEED_VECTORS = "Stage 5d (relevance scoring, seed vectors read)";
    private static final String SCORING_SURVIVORS = "Stage 5d (relevance scoring, corpus survivors)";
    private static final String RELEVANCE_FLOOR = "Stage 5e (relevance floor, below-threshold verdicts)";
    private static final String PARTITIONS = "Stage 5f (clustering, seed partitions)";
    private static final String FILES_HASHED = "Stage 5f (clustering, files hashed)";
    private static final String BLOCKS = "Stage 5f (clustering, comparison blocks, partition 1 of 1)";
    private static final String MEMBERS_RECORDED = "Stage 5f (clustering, members recorded)";
    private static final String REPORT_SAMPLE = "Stage 5 (relevance report, sampled survivors)";
    private static final String REPORT_ANSWERS = "Stage 5 (relevance report, answers matched)";
    private static final String ARRANGEMENT_SCORES = "Stage 6a (arrangement, scores read)";
    private static final String ARRANGEMENT_MEMBERS = "Stage 6a (arrangement, members gathered)";
    private static final String ARRANGEMENT_CLUSTERS = "Stage 6a (arrangement, clusters)";
    private static final String ARRANGEMENT_PAGE_ROWS = "Stage 6a (arrangement, page rows)";
    private static final String ARRANGEMENT_PAGE_PARTITIONS = "Stage 6a (arrangement, page partitions)";

    /** Each stage's own name, which its statement lines open with. */
    private static final String STAGE_FIVE_B = "Stage 5b (seed/corpus comparison)";

    private static final String STAGE_FIVE_C = "Stage 5c (embedding scoring)";
    private static final String STAGE_FIVE_D = "Stage 5d (relevance scoring)";
    private static final String STAGE_FIVE_E = "Stage 5e (relevance floor)";
    private static final String STAGE_FIVE_F = "Stage 5f (clustering)";
    private static final String THE_REPORT = "Stage 5 (relevance report)";
    private static final String STAGE_SIX_A = "Stage 6a (arrangement)";

    /** What the statements read, as their lines name it (ADR-193 section 6, ADR-204 section 3). */
    private static final String CORPUS_SURVIVORS = "the corpus survivors";

    private static final String SEED_OCCURRENCES = "the seed walk's occurrences";
    private static final String UNUSABLE_SEEDS = "the unusable seeds";
    private static final String EMBEDDER_IDENTITIES = "the embedder identities";
    private static final String RECORDED_ANSWERS = "the recorded answers";

    private static final String EMBEDDING_STARTING = "Stage 5c (embedding scoring) starting under scoring run ";
    private static final String EMBEDDING_FINISHED = "Stage 5c (embedding scoring) finished under scoring run ";
    private static final String SEED_EXTRACTION_FINISHED = "Stage 5 extracted the seed set under run ";
    private static final String RELEVANCE_SCORING_FINISHED = "Stage 5d (relevance scoring) finished under scoring run ";
    private static final String RELEVANCE_FLOOR_FINISHED = "Stage 5e (relevance floor) finished under scoring run ";
    private static final String CLUSTERING_FINISHED = "Stage 5f (clustering) finished under scoring run ";
    private static final String RELEVANCE_REPORT_FINISHED = "Stage 5 (relevance report) finished under scoring run ";
    private static final String ARRANGEMENT_FINISHED = "The arrangement step finished under ";
    private static final String ARRANGEMENT_ALREADY_RECORDED = "the arrangement step was already recorded under run ";

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
    private RelevanceDistribution relevanceDistribution;

    @Autowired
    private RelevanceLabels relevanceLabels;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    /** Disarms the seed fixture's move before as well as after each method: the hook is static. */
    @BeforeEach
    void captureOperatorLines() {
        SeedScriptedExtractionBeans.stopMovingAway();
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("io.algernon.vespera");
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseOperatorLines() {
        SeedScriptedExtractionBeans.stopMovingAway();
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("Seed extraction says how many seeds it has recorded")
    @DisplayName("Seed extraction counts the seeds whose rows it writes once the folder is read")
    void seedExtractionCountsTheSeedsItRecords(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);

        cli.run("run", root.toString());

        theStageRan(SEED_EXTRACTION_FINISHED);
        claim(
                "the one seed's rows are written after the step, and the counter reads one of one",
                () -> assertThat(progressOf(SEEDS_RECORDED))
                        .containsExactlyElementsOf(ProgressLines.expected(SEEDS_RECORDED, ONE_SEED)));
        everyLineIsTheCounters(SEEDS_RECORDED);
    }

    @Test
    @Story("Embedding scoring says how far it has got")
    @DisplayName("Embedding scoring counts its corpus survivors and then its seeds, each against its own total")
    void embeddingScoringCountsItsSurvivorsAndThenItsSeeds(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);

        cli.run("run", root.toString());

        claim("the invocation reported success", () -> assertThat(cli.getExitCode()).isZero());
        theStageRan(EMBEDDING_FINISHED);
        claim(
                "the first counter reads one, two and three of the " + CORPUS_DOCUMENTS + " corpus survivors,"
                        + " each line written when that survivor was finished",
                () -> assertThat(progressOf(EMBEDDING_SURVIVORS))
                        .containsExactly(
                                EMBEDDING_SURVIVORS + ": 1 of 3 (33%)",
                                EMBEDDING_SURVIVORS + ": 2 of 3 (66%)",
                                EMBEDDING_SURVIVORS + ": 3 of 3 (100%)"));
        claim(
                "the second counter reads one of one for the one usable seed: the seeds are their own loop with"
                        + " their own total",
                () -> assertThat(progressOf(EMBEDDING_SEEDS)).containsExactly(EMBEDDING_SEEDS + ": 1 of 1 (100%)"));
        claim(
                "every one of those lines lies between the stage's starting line and its finishing line",
                () -> assertThat(String.join("\n", operatorLines()))
                        .containsSubsequence(
                                EMBEDDING_STARTING,
                                EMBEDDING_SURVIVORS + ": 1 of 3",
                                EMBEDDING_SURVIVORS + ": 3 of 3",
                                EMBEDDING_SEEDS + ": 1 of 1",
                                EMBEDDING_FINISHED));
        everyLineIsTheCounters(EMBEDDING_SURVIVORS, EMBEDDING_SEEDS);
        claim(
                "the comparison of the seeds with the collection says what it is reading and how long each read"
                        + " took, each line once, in the order it reads: the documents left, the seed folder's"
                        + " files, and the extraction metrics of each side; and nothing about unusable seeds,"
                        + " none being recorded",
                () -> assertThat(StatementLines.withoutTotals(StatementLines.of(operatorLines(), STAGE_FIVE_B)))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(STAGE_FIVE_B, CORPUS_SURVIVORS),
                                StatementLines.timedRead(STAGE_FIVE_B, SEED_OCCURRENCES),
                                StatementLines.countedRead(STAGE_FIVE_B, "the corpus survivors' extraction metrics"),
                                StatementLines.countedRead(STAGE_FIVE_B, "the seeds' extraction metrics"))));
        claim(
                "embedding says the same of its three reads, in the order it makes them, before it embeds"
                        + " anything",
                () -> {
                    assertThat(StatementLines.of(operatorLines(), STAGE_FIVE_C))
                            .containsExactlyElementsOf(StatementLines.inOrder(
                                    StatementLines.timedRead(STAGE_FIVE_C, CORPUS_SURVIVORS),
                                    StatementLines.timedRead(STAGE_FIVE_C, SEED_OCCURRENCES),
                                    StatementLines.timedRead(STAGE_FIVE_C, UNUSABLE_SEEDS)));
                    assertThat(String.join("\n", operatorLines()))
                            .containsSubsequence(
                                    STAGE_FIVE_C + " read " + UNUSABLE_SEEDS + " in ", EMBEDDING_SURVIVORS + ": 1 of 3");
                });
    }

    @Test
    @Story("Relevance scoring says how far it has got")
    @DisplayName("Relevance scoring counts the seed files it hashes, the seed vectors it reads and the survivors it scores")
    void relevanceScoringCountsItsThreeLoops(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);

        cli.run("run", root.toString());

        theStageRan(RELEVANCE_SCORING_FINISHED);
        claim(
                "the one usable seed is hashed and its stored vectors are read, each a counter of one of one",
                () -> {
                    assertThat(progressOf(SEED_FILES)).containsExactlyElementsOf(ProgressLines.expected(SEED_FILES, ONE_SEED));
                    assertThat(progressOf(SEED_VECTORS))
                            .containsExactlyElementsOf(ProgressLines.expected(SEED_VECTORS, ONE_SEED));
                });
        claim(
                "and the counter over the survivors reads one, two and three of three",
                () -> assertThat(progressOf(SCORING_SURVIVORS))
                        .containsExactlyElementsOf(ProgressLines.expected(SCORING_SURVIVORS, CORPUS_DOCUMENTS)));
        everyLineIsTheCounters(SEED_FILES, SEED_VECTORS, SCORING_SURVIVORS);
        claim(
                "scoring says what it is reading and how long each read took, each line once, in the order it"
                        + " reads: the seed folder's files and the unusable seeds, for the seeds it hashes, and"
                        + " then the documents left",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_FIVE_D))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(STAGE_FIVE_D, SEED_OCCURRENCES),
                                StatementLines.timedRead(STAGE_FIVE_D, UNUSABLE_SEEDS),
                                StatementLines.timedRead(STAGE_FIVE_D, CORPUS_SURVIVORS))));
    }

    @Test
    @Story("The relevance floor says how many verdicts it writes, and says nothing where it writes none")
    @DisplayName("A floor that removes every document counts its verdicts, and one that is unset has nothing to count")
    void theRelevanceFloorCountsTheVerdictsItWrites(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        claim(
                "an unset threshold removes nothing, so the first invocation has no verdict to count and writes"
                        + " no counter",
                () -> assertThat(progressOf(RELEVANCE_FLOOR)).isEmpty());
        claim(
                "no answer has been recorded for this seed set, so the relevance report has none to look up and"
                        + " writes no answers counter",
                () -> assertThat(progressOf(REPORT_ANSWERS)).isEmpty());
        claim(
                "with no threshold set, the floor step makes one read, of the embedder identities, and says so"
                        + " before and after: it asks for no answers and for no scores",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_FIVE_E))
                        .containsExactlyElementsOf(StatementLines.timedRead(STAGE_FIVE_E, EMBEDDER_IDENTITIES)));
        claim(
                "and the report says what it is reading and how long each read took, each line once, in the"
                        + " order it reads: the scores, the recorded answers, the scores against the answers, the"
                        + " embedder identities twice, once for each question it asks of them, and the answers a"
                        + " model gave",
                () -> assertThat(StatementLines.of(operatorLines(), THE_REPORT))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(THE_REPORT, "the scores"),
                                StatementLines.timedRead(THE_REPORT, RECORDED_ANSWERS),
                                StatementLines.timedRead(THE_REPORT, "the scores against the answers"),
                                StatementLines.timedRead(THE_REPORT, EMBEDDER_IDENTITIES),
                                StatementLines.timedRead(THE_REPORT, EMBEDDER_IDENTITIES),
                                StatementLines.timedRead(THE_REPORT, "the answers a model gave"))));

        anAnswerGivenUnderThisRunsIdentity(root, seeds);
        profile(seeds, A_FLOOR_ABOVE_EVERY_SCORE);
        logged.list.clear();
        cli.run("run", root.toString());

        theStageRan(RELEVANCE_FLOOR_FINISHED);
        claim(
                "a threshold above every score, on this run's own scale, removes all " + CORPUS_DOCUMENTS
                        + " documents, and the counter reads one, two and three of three",
                () -> assertThat(progressOf(RELEVANCE_FLOOR))
                        .containsExactlyElementsOf(ProgressLines.expected(RELEVANCE_FLOOR, CORPUS_DOCUMENTS)));
        theStageRan(RELEVANCE_REPORT_FINISHED);
        claim(
                "one answer is recorded for the seed set, and the relevance report looks it up: one of one",
                () -> assertThat(progressOf(REPORT_ANSWERS))
                        .containsExactlyElementsOf(ProgressLines.expected(REPORT_ANSWERS, ONE_ANSWER)));
        theStageRan(CLUSTERING_FINISHED);
        claim(
                "the one partition lost every member to the floor, and it is counted all the same: the stage has"
                        + " been through it, though it grouped nothing in it",
                () -> assertThat(progressOf(PARTITIONS))
                        .containsExactlyElementsOf(ProgressLines.expected(PARTITIONS, ONE_PARTITION)));
        claim(
                "and an emptied partition opens no counter over its blocks",
                () -> assertThat(progressOf(BLOCKS)).isEmpty());
        everyLineIsTheCounters(RELEVANCE_FLOOR, REPORT_ANSWERS, PARTITIONS);
        claim(
                "with a threshold that is a number and applies, the floor step makes three reads and says so of"
                        + " each, in order: the embedder identities, the recorded answers the threshold is"
                        + " checked against, and the scores below it",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_FIVE_E))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(STAGE_FIVE_E, EMBEDDER_IDENTITIES),
                                StatementLines.timedRead(STAGE_FIVE_E, RECORDED_ANSWERS),
                                StatementLines.timedRead(STAGE_FIVE_E, "the scores below the floor"))));
        claim(
                "and the report reads the recorded answers a second time, for the same check of the threshold,"
                        + " between its two reads of the embedder identities, and says so in the report's own"
                        + " name",
                () -> assertThat(StatementLines.of(operatorLines(), THE_REPORT))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(THE_REPORT, "the scores"),
                                StatementLines.timedRead(THE_REPORT, RECORDED_ANSWERS),
                                StatementLines.timedRead(THE_REPORT, "the scores against the answers"),
                                StatementLines.timedRead(THE_REPORT, EMBEDDER_IDENTITIES),
                                StatementLines.timedRead(THE_REPORT, RECORDED_ANSWERS),
                                StatementLines.timedRead(THE_REPORT, EMBEDDER_IDENTITIES),
                                StatementLines.timedRead(THE_REPORT, "the answers a model gave"))));
    }

    @Test
    @Story("Grouping says how far it has got")
    @DisplayName("Grouping counts its partitions, the files it hashes across them, and the pairs of blocks of each")
    void clusteringCountsItsPartitionsFilesAndBlocks(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);

        cli.run("run", root.toString());

        theStageRan(CLUSTERING_FINISHED);
        claim(
                "the one seed won every document, so there is one partition, and its counter reads one of one",
                () -> assertThat(progressOf(PARTITIONS))
                        .containsExactlyElementsOf(ProgressLines.expected(PARTITIONS, ONE_PARTITION)));
        claim(
                "the files of all " + CORPUS_DOCUMENTS + " members are hashed, in one counter whose total is every"
                        + " partition's members",
                () -> assertThat(progressOf(FILES_HASHED))
                        .containsExactlyElementsOf(ProgressLines.expected(FILES_HASHED, CORPUS_DOCUMENTS)));
        claim(
                "the partition's three documents fit one block, so it has one pair of blocks to compare, and its"
                        + " counter names the partition and how many there are",
                () -> assertThat(progressOf(BLOCKS))
                        .containsExactlyElementsOf(ProgressLines.expected(BLOCKS, ONE_PAIR_OF_BLOCKS)));
        claim(
                "and the members whose group is recorded have no counter of their own: their inserts are left to"
                        + " the partition's counter",
                () -> assertThat(progressOf(MEMBERS_RECORDED)).isEmpty());
        everyLineIsTheCounters(PARTITIONS, FILES_HASHED, BLOCKS);
        claim(
                "grouping says what it is reading and how long each read took, each line once, in the order it"
                        + " reads: the seed partitions, the documents left, the members of the one partition,"
                        + " and, once that partition is grouped, the sizes of its groups",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_FIVE_F))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(STAGE_FIVE_F, "the seed partitions"),
                                StatementLines.timedRead(STAGE_FIVE_F, CORPUS_SURVIVORS),
                                StatementLines.timedRead(STAGE_FIVE_F, "the members of partition 1 of 1"),
                                StatementLines.timedRead(STAGE_FIVE_F, "the cluster sizes of partition 1 of 1"))));
    }

    @Test
    @Story("The relevance report says how many sampled documents it has opened")
    @DisplayName("The relevance report counts the documents it samples for a person to read")
    void theRelevanceReportCountsTheDocumentsItSamples(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds, MORE_DOCUMENTS_THAN_ONE_BAND_IS_SAMPLED_FROM);
        profile(seeds, null);

        cli.run("run", root.toString());

        theStageRan(RELEVANCE_REPORT_FINISHED);
        int sampled = putToAPerson();
        claim(
                "the report put fewer documents to a person than were scored, " + sampled + " of "
                        + MORE_DOCUMENTS_THAN_ONE_BAND_IS_SAMPLED_FROM + ", because every document here has one"
                        + " score and so one band, and a band is sampled from at most twelve: this tells a counter"
                        + " over the sample from one over the survivors",
                () -> assertThat(sampled).isPositive().isLessThan(MORE_DOCUMENTS_THAN_ONE_BAND_IS_SAMPLED_FROM));
        claim(
                "the counter has one line for each document sampled, against that number",
                () -> assertThat(progressOf(REPORT_SAMPLE))
                        .containsExactlyElementsOf(ProgressLines.expected(REPORT_SAMPLE, sampled)));
        everyLineIsTheCounters(REPORT_SAMPLE);
    }

    @Test
    @Story("The arrangement says how far it has got")
    @DisplayName("The arrangement counts the scores and members it reads, the groups it records and the rows and partitions of its page")
    void theArrangementCountsItsFiveLoops(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);

        cli.run("run", root.toString());

        theStageRan(ARRANGEMENT_FINISHED);
        String arrangement = theArrangementRun(root);
        long membership = membershipBehind(arrangement);
        long recorded = clustersRecordedUnder(arrangement);
        claim(
                "the arrangement read a membership and recorded at least one group, which are what its counters"
                        + " are over",
                () -> {
                    assertThat(membership).isPositive();
                    assertThat(recorded).isPositive();
                });
        claim(
                "the scores of the " + membership + " members are read and the members gathered, one counter each",
                () -> {
                    assertThat(progressOf(ARRANGEMENT_SCORES))
                            .containsExactlyElementsOf(ProgressLines.expected(ARRANGEMENT_SCORES, membership));
                    assertThat(progressOf(ARRANGEMENT_MEMBERS))
                            .containsExactlyElementsOf(ProgressLines.expected(ARRANGEMENT_MEMBERS, membership));
                });
        claim(
                "the " + recorded + " groups are labelled and recorded, and then drawn as the page's rows",
                () -> {
                    assertThat(progressOf(ARRANGEMENT_CLUSTERS))
                            .containsExactlyElementsOf(ProgressLines.expected(ARRANGEMENT_CLUSTERS, recorded));
                    assertThat(progressOf(ARRANGEMENT_PAGE_ROWS))
                            .containsExactlyElementsOf(ProgressLines.expected(ARRANGEMENT_PAGE_ROWS, recorded));
                });
        claim(
                "and the page's one partition has its seed's path read",
                () -> assertThat(progressOf(ARRANGEMENT_PAGE_PARTITIONS))
                        .containsExactlyElementsOf(ProgressLines.expected(ARRANGEMENT_PAGE_PARTITIONS, ONE_PARTITION)));
        everyLineIsTheCounters(
                ARRANGEMENT_SCORES, ARRANGEMENT_MEMBERS, ARRANGEMENT_CLUSTERS, ARRANGEMENT_PAGE_ROWS,
                ARRANGEMENT_PAGE_PARTITIONS);
        theArrangementSaidItsTwoReads();
    }

    /**
     * ADR-192 section 9: an arrangement already recorded still reads the scores and the membership and draws
     * its page, because ADR-154 section 2 has the page written on both branches, so four of its counters
     * write on that branch too. The one over the groups it records does not, since nothing is recorded.
     */
    @Test
    @Story("The arrangement says how far it has got")
    @DisplayName("An arrangement already recorded counts the scores and members it reads and the page it draws, and records no group")
    void anArrangementAlreadyRecordedCountsWhatItReadsForItsPage(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        logged.list.clear();

        cli.run("run", root.toString());

        theStageRan(ARRANGEMENT_ALREADY_RECORDED);
        String arrangement = theArrangementRun(root);
        long membership = membershipBehind(arrangement);
        long recorded = clustersRecordedUnder(arrangement);
        claim(
                "the scores and members are read again, and the page drawn again from the " + recorded
                        + " groups recorded earlier and its one partition",
                () -> {
                    assertThat(progressOf(ARRANGEMENT_SCORES))
                            .containsExactlyElementsOf(ProgressLines.expected(ARRANGEMENT_SCORES, membership));
                    assertThat(progressOf(ARRANGEMENT_MEMBERS))
                            .containsExactlyElementsOf(ProgressLines.expected(ARRANGEMENT_MEMBERS, membership));
                    assertThat(progressOf(ARRANGEMENT_PAGE_ROWS))
                            .containsExactlyElementsOf(ProgressLines.expected(ARRANGEMENT_PAGE_ROWS, recorded));
                    assertThat(progressOf(ARRANGEMENT_PAGE_PARTITIONS))
                            .containsExactlyElementsOf(ProgressLines.expected(ARRANGEMENT_PAGE_PARTITIONS, ONE_PARTITION));
                });
        claim(
                "and no group is recorded, so that counter writes nothing",
                () -> assertThat(progressOf(ARRANGEMENT_CLUSTERS)).isEmpty());
        everyLineIsTheCounters(
                ARRANGEMENT_SCORES, ARRANGEMENT_MEMBERS, ARRANGEMENT_PAGE_ROWS, ARRANGEMENT_PAGE_PARTITIONS);
        theArrangementSaidItsTwoReads();
    }

    /**
     * That the arrangement said it read the membership and then the groups recorded, and how long each took,
     * once each: on the branch that records them and on the branch that finds them recorded, which reads
     * them for the page it writes again.
     */
    private void theArrangementSaidItsTwoReads() {
        claim(
                "the arrangement says it is reading which document is in which group, and how long that took,"
                        + " and then the same of the groups recorded: each line once, in that order",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_SIX_A))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(STAGE_SIX_A, "the cluster membership"),
                                StatementLines.timedRead(STAGE_SIX_A, "the recorded clusters"))));
    }

    /** That every line of each named counter came from {@code StageProgress}'s logger, and that there was one. */
    private void everyLineIsTheCounters(String... counters) {
        for (String counter : counters) {
            claim(
                    "every line of " + counter + " was written by the progress counter itself",
                    () -> assertThat(ProgressLines.loggersOf(logged.list, counter))
                            .isNotEmpty()
                            .containsOnly(ProgressLines.theCountersLogger()));
        }
    }

    /** That the stage did its work in this invocation, so a claim about its counter fails for the counter alone. */
    private void theStageRan(String line) {
        claim(
                "the stage ran, which its own line says (" + line.strip() + " ...)",
                () -> assertThat(operatorLines()).anyMatch(written -> written.startsWith(line)));
    }

    /** What the relevance report's own finishing line says it put to a person. */
    private int putToAPerson() {
        String finishing = operatorLines().stream()
                .filter(line -> line.startsWith(RELEVANCE_REPORT_FINISHED))
                .findFirst()
                .orElseThrow();
        String before = finishing.substring(0, finishing.indexOf(" put to a person"));
        return Integer.parseInt(before.substring(before.lastIndexOf(' ') + 1));
    }

    private List<String> progressOf(String label) {
        return ProgressLines.of(logged.list, label);
    }

    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private void aCorpus(Path root, Path seeds) throws IOException {
        aCorpus(root, seeds, CORPUS_DOCUMENTS);
    }

    private void aCorpus(Path root, Path seeds, int documents) throws IOException {
        for (int i = 0; i < documents; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /** The seed folder named, stage 4's gate open, gate 3 open, and the threshold as given. */
    private void profile(Path seeds, String floor) {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .relevanceScoreFloor(floor, floor == null ? null : "set by this test")
                .build());
    }

    /**
     * One answer about one of this corpus's documents, recorded as though a person had given it while the
     * embedder this run used was in use, which is what makes a threshold calibrated to this scale.
     */
    private void anAnswerGivenUnderThisRunsIdentity(Path root, Path seeds) {
        String identity = relevanceDistribution.embedderIdentityFor(MODEL_NAME).orElseThrow();
        String aDocument = jdbcTemplate.queryForObject(
                "SELECT f.path FROM file_occurrence f JOIN walk w ON w.id = f.walk_id"
                        + " WHERE w.root = ? ORDER BY f.path LIMIT 1",
                String.class,
                Walk.canonicalRoot(root).toString());
        String firstScoringRun = jdbcTemplate
                .queryForList(
                        "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                                + " WHERE run.stage = 'embedding-scoring' AND w.root = ? ORDER BY run.id",
                        String.class,
                        Walk.canonicalRoot(root).toString())
                .getFirst();
        relevanceLabels.record(
                new OccurrencePath(aDocument),
                Walk.canonicalRoot(seeds).toString(),
                true,
                new RunId(firstScoringRun),
                1.0,
                identity);
    }

    /** The one arrangement run minted over {@code root}. */
    private String theArrangementRun(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = 'arrangement' AND w.root = ?",
                String.class,
                Walk.canonicalRoot(root).toString());
    }

    /** How many membership rows the scoring run behind {@code arrangement} holds: what 6a reads. */
    private long membershipBehind(String arrangement) {
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_cluster WHERE run_id ="
                        + " (SELECT upstream_run_id FROM run_upstream WHERE run_id = ?)",
                Long.class,
                arrangement);
        return rows == null ? 0 : rows;
    }

    private long clustersRecordedUnder(String arrangement) {
        Long rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cluster WHERE run_id = ?", Long.class, arrangement);
        return rows == null ? 0 : rows;
    }
}
