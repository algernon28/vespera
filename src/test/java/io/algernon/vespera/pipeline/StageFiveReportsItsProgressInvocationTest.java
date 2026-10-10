package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final String SEED_KEYS = "Stage 5d (relevance scoring, seed cache keys read)";
    private static final String SEED_VECTORS = "Stage 5d (relevance scoring, seed vectors read)";
    private static final String SCORING_SURVIVORS = "Stage 5d (relevance scoring, corpus survivors)";
    private static final String RELEVANCE_FLOOR = "Stage 5e (relevance floor, below-threshold verdicts)";
    private static final String PARTITIONS = "Stage 5f (clustering, seed partitions)";
    /** 5f's counter of the keys it reads, one for each partition since ADR-211 section 5. */
    private static final String KEYS_READ_OF_THE_ONE = "Stage 5f (clustering, cache keys read, partition 1 of 1)";

    private static final String KEYS_READ_OF_THE_FIRST_OF_TWO = "Stage 5f (clustering, cache keys read, partition 1 of 2)";
    private static final String KEYS_READ_OF_THE_SECOND_OF_TWO =
            "Stage 5f (clustering, cache keys read, partition 2 of 2)";
    private static final String BLOCKS = "Stage 5f (clustering, comparison blocks, partition 1 of 1)";
    private static final String MEMBERS_RECORDED = "Stage 5f (clustering, members recorded)";
    private static final String REPORT_SAMPLE = "Stage 5 (relevance report, sampled survivors)";
    private static final String REPORT_ANSWERS = "Stage 5 (relevance report, answers matched)";
    private static final String ARRANGEMENT_SCORES = "Stage 6a (arrangement, scores read)";
    private static final String ARRANGEMENT_MEMBERS = "Stage 6a (arrangement, members gathered)";
    /**
     * The two counters stage 6a opens for each seed partition since ADR-223, a partition's clusters being
     * known only once its members are read. This class's corpus has one seed, so one partition, the first of one.
     */
    private static final String ARRANGEMENT_CLUSTERS = "Stage 6a (arrangement, clusters, partition 1 of 1)";
    private static final String ARRANGEMENT_PAGE_ROWS = "Stage 6a (arrangement, page rows, partition 1 of 1)";
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

    @Autowired
    private Ledger ledger;

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
                        + " took, each line once, in the order it reads: the seed folder's files, and the"
                        + " extraction metrics of each side; nothing about reading the documents left, which it"
                        + " reads a page at a time inside its read of their metrics; and nothing about unusable"
                        + " seeds, none being recorded",
                () -> assertThat(StatementLines.withoutTotals(StatementLines.of(operatorLines(), STAGE_FIVE_B)))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(STAGE_FIVE_B, SEED_OCCURRENCES),
                                StatementLines.countedRead(STAGE_FIVE_B, "the corpus survivors' extraction metrics"),
                                StatementLines.countedRead(STAGE_FIVE_B, "the seeds' extraction metrics"))));
        claim(
                "embedding says it counts the documents left, and then reads the seed folder's files and the"
                        + " unusable seeds, each line once and in that order, before it embeds anything: it goes"
                        + " through the documents left as it reads them, and holds no list of them",
                () -> {
                    assertThat(StatementLines.of(operatorLines(), STAGE_FIVE_C))
                            .containsExactlyElementsOf(StatementLines.inOrder(
                                    StatementLines.timedCount(STAGE_FIVE_C, CORPUS_SURVIVORS),
                                    StatementLines.timedRead(STAGE_FIVE_C, SEED_OCCURRENCES),
                                    StatementLines.timedRead(STAGE_FIVE_C, UNUSABLE_SEEDS)));
                    assertThat(String.join("\n", operatorLines()))
                            .containsSubsequence(
                                    STAGE_FIVE_C + " read " + UNUSABLE_SEEDS + " in ", EMBEDDING_SURVIVORS + ": 1 of 3");
                });
    }

    @Test
    @Story("Relevance scoring says how far it has got")
    @DisplayName("Relevance scoring counts the seed keys it reads, the seed vectors it reads and the survivors it scores")
    @Issue("349")
    @Link(name = "ADR-206", url = Adr.STAGE_2_RECORDS_ITS_EXTRACTION_CACHE_KEY, type = "adr")
    void relevanceScoringCountsItsThreeLoops(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);

        cli.run("run", root.toString());

        theStageRan(RELEVANCE_SCORING_FINISHED);
        claim(
                "the one usable seed's recorded key is read and its stored vectors are read, each a counter of one of one",
                () -> {
                    assertThat(progressOf(SEED_KEYS)).containsExactlyElementsOf(ProgressLines.expected(SEED_KEYS, ONE_SEED));
                    assertThat(progressOf(SEED_VECTORS))
                            .containsExactlyElementsOf(ProgressLines.expected(SEED_VECTORS, ONE_SEED));
                });
        claim(
                "and the counter over the survivors reads one, two and three of three",
                () -> assertThat(progressOf(SCORING_SURVIVORS))
                        .containsExactlyElementsOf(ProgressLines.expected(SCORING_SURVIVORS, CORPUS_DOCUMENTS)));
        everyLineIsTheCounters(SEED_KEYS, SEED_VECTORS, SCORING_SURVIVORS);
        claim(
                "scoring says what it is reading and how long each read took, each line once, in the order it"
                        + " reads: the seed folder's files and the unusable seeds, for the seeds whose keys it reads,"
                        + " the embedder identities, for the one its run's vectors carry, and then"
                        + " that it counts the documents left, which it goes through as it reads them",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_FIVE_D))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(STAGE_FIVE_D, SEED_OCCURRENCES),
                                StatementLines.timedRead(STAGE_FIVE_D, UNUSABLE_SEEDS),
                                StatementLines.timedRead(STAGE_FIVE_D, EMBEDDER_IDENTITIES),
                                StatementLines.timedCount(STAGE_FIVE_D, CORPUS_SURVIVORS))));
    }

    @Test
    @Story("The relevance floor says how many verdicts it writes, and says nothing where it writes none")
    @DisplayName("A floor that removes every document counts its verdicts, and one that is unset has nothing to count")
    @Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
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
                "grouping says it read the seed partitions, the embedder identities, which it reads before any"
                        + " partition, and the members of the one partition, and says"
                        + " nothing of the documents left, asking only which of that partition's members are left,"
                        + " and nothing of its group sizes: it kept no member, so nothing was grouped and no sizes"
                        + " were read",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_FIVE_F))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(STAGE_FIVE_F, "the seed partitions"),
                                StatementLines.timedRead(STAGE_FIVE_F, EMBEDDER_IDENTITIES),
                                StatementLines.timedRead(STAGE_FIVE_F, "the members of partition 1 of 1"))));
        claim(
                "with a threshold that is a number and applies, the floor step makes two reads and a count and says"
                        + " so of each, in order: the embedder identities, the recorded answers the threshold is"
                        + " checked against, and a count of the scores below it, which it then goes through a page"
                        + " at a time inside the counter of its verdicts rather than reading them all first",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_FIVE_E))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(STAGE_FIVE_E, EMBEDDER_IDENTITIES),
                                StatementLines.timedRead(STAGE_FIVE_E, RECORDED_ANSWERS),
                                StatementLines.timedCount(STAGE_FIVE_E, "the scores below the floor"))));
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
    @DisplayName("Grouping counts its partitions, and the recorded keys and the pairs of blocks of each partition")
    @Issue("349")
    @Link(name = "ADR-206", url = Adr.STAGE_2_RECORDS_ITS_EXTRACTION_CACHE_KEY, type = "adr")
    @Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
    void clusteringCountsItsPartitionsKeysAndBlocks(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);

        cli.run("run", root.toString());

        theStageRan(CLUSTERING_FINISHED);
        claim(
                "the one seed won every document, so there is one partition, and its counter reads one of one",
                () -> assertThat(progressOf(PARTITIONS))
                        .containsExactlyElementsOf(ProgressLines.expected(PARTITIONS, ONE_PARTITION)));
        claim(
                "the recorded keys of all " + CORPUS_DOCUMENTS + " members are read, in a counter that names the"
                        + " partition and how many there are, its total that partition's members: one partition is"
                        + " held at a time",
                () -> assertThat(progressOf(KEYS_READ_OF_THE_ONE))
                        .containsExactlyElementsOf(ProgressLines.expected(KEYS_READ_OF_THE_ONE, CORPUS_DOCUMENTS)));
        claim(
                "the partition's three documents fit one block, so it has one pair of blocks to compare, and its"
                        + " counter names the partition and how many there are",
                () -> assertThat(progressOf(BLOCKS))
                        .containsExactlyElementsOf(ProgressLines.expected(BLOCKS, ONE_PAIR_OF_BLOCKS)));
        claim(
                "and the members whose group is recorded have no counter of their own: their inserts are left to"
                        + " the partition's counter",
                () -> assertThat(progressOf(MEMBERS_RECORDED)).isEmpty());
        everyLineIsTheCounters(PARTITIONS, KEYS_READ_OF_THE_ONE, BLOCKS);
        claim(
                "grouping says what it is reading and how long each read took, each line once, in the order it"
                        + " reads: the seed partitions, the embedder identities, for the one its run's vectors"
                        + " carry, the members of the one partition, and, once that partition"
                        + " is grouped, the sizes of its groups; and nothing of reading the documents left",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_FIVE_F))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(STAGE_FIVE_F, "the seed partitions"),
                                StatementLines.timedRead(STAGE_FIVE_F, EMBEDDER_IDENTITIES),
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
        theArrangementSaidItsReads(false);
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
        theArrangementSaidItsReads(true);
    }

    /**
     * That the arrangement said each read it makes, and how long each took, once each and in the order it
     * makes them (ADR-223 section 8, lines L1 to L4). It no longer reads the run's whole membership or every
     * group recorded. It reads which exemplars have documents under them, to know whether there is anything
     * to arrange; where the arrangement was already recorded, the partitions of that arrangement; and then,
     * for its one partition, the documents under it and the groups recorded for it.
     */
    private void theArrangementSaidItsReads(boolean alreadyRecorded) {
        List<List<String>> reads = new ArrayList<>();
        reads.add(StatementLines.timedRead(STAGE_SIX_A, "the seed partitions"));
        if (alreadyRecorded) {
            reads.add(StatementLines.timedRead(STAGE_SIX_A, "the seed partitions of the arrangement"));
        }
        reads.add(StatementLines.timedRead(STAGE_SIX_A, "the members of partition 1 of 1"));
        reads.add(StatementLines.timedRead(STAGE_SIX_A, "the recorded clusters of partition 1 of 1"));
        claim(
                "the arrangement says it is reading the exemplars that have documents under them, then"
                        + (alreadyRecorded ? " the partitions of the arrangement it finds recorded, then" : "")
                        + " its one partition's documents and that partition's recorded groups, and how long"
                        + " each took: each line once, in that order, and no read of every document or every"
                        + " group at once",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_SIX_A))
                        .containsExactlyElementsOf(reads.stream().flatMap(List::stream).toList()));
    }

    /**
     * ADR-204 section 3's five progress labels, and 5b's line before its read of the unusable seeds, which no
     * ordinary fixture reaches: a counted read writes a progress line only once SQLite has taken 100,000
     * steps, and a line about the unusable seeds only where one is recorded.
     *
     * <p>So the rows are written here, synthetic and against the files of a folder nobody walked, under the
     * runs a first invocation minted: enough extraction metrics, signatures and unusable seeds for each read
     * to pass one callback. Then the record that stage 3, 4b and 5b finished is deleted, and the next
     * invocation does those three again under the same runs. None of the rows is about a document of the
     * collection. The signature rows do enter 4b's work all the same: its read returns them and its
     * containment loop goes through each. So the test holds what that could have changed: the verdicts
     * under the redundancy run are the ones the first invocation wrote, and none is against a file of the
     * folder nobody walked. With this fixture the first invocation writes none under that run, every
     * converted document coming back alike, so what is held here is that the synthetic signatures added none;
     * {@code RedundancyResolutionReportsItsProgressInvocationTest} holds the same over a run that does hold a
     * verdict. It claims nothing about what any other stage decides.
     *
     * <p>Since ADR-211 two of the five are no longer counted by SQLite: stage 3's read of the extraction
     * metrics and 5b's of the collection's are made a page of surviving documents at a time and told their
     * rows. So the first invocation, before any synthetic row is written, is where each says how far it has
     * gone, once, over the collection's own rows (ADR-211 section 9); and in the second, over the tens of
     * thousands of rows of a folder nobody walked, each says nothing, never going through them.
     */
    @Test
    @Story("A long read inside the database reports how far it has gone")
    @DisplayName("Over tens of thousands of rows, each long read of stages 3, 4b and 5b says about how far it has gone, under its own name")
    void eachCountedReadSaysHowFarItHasGoneUnderItsOwnLabel(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        theStageRan(RELEVANCE_REPORT_FINISHED);
        String extraction = theLatestRunOf(StageModules.EXTRACTION.stage());
        String census = theLatestRunOf(StageModules.CONTENT_CENSUS.stage());
        String redundancy = theLatestRunOf(StageModules.CONTENT_REDUNDANCY.stage());
        String measurement = theLatestRunOf(StageModules.SEED_MEASUREMENT.stage());
        saidHowFarOnceOverItsOwnRows(
                "Stage 3 (content census, reading extraction metrics)", rowSpanUnder("extraction_metric", extraction));
        saidHowFarOnceOverItsOwnRows(
                "Stage 5b (seed/corpus comparison, reading corpus metrics)",
                rowSpanUnder("extraction_metric", extraction));
        List<String> verdictsOfTheFirstInvocation = verdictsUnder(redundancy);
        List<Long> nobodysFiles = filesOfAFolderNobodyWalked(ROWS_PAST_ONE_CALLBACK_AT_FIVE_STEPS);
        List<Long> fewer = nobodysFiles.subList(0, ROWS_PAST_ONE_CALLBACK_AT_SEVEN_STEPS);
        writeForEach(METRIC_ROW, fewer, extraction);
        writeForEach(METRIC_ROW, fewer, measurement);
        writeForEach(
                "INSERT INTO minhash_signature (occurrence_id, run_id, signature_identity, signature)"
                        + " VALUES (?, ?, 'synthetic', x'00')",
                nobodysFiles,
                redundancy);
        writeForEach(
                "INSERT INTO unusable_seed (occurrence_id, run_id, reason) VALUES (?, ?, 'synthetic')",
                nobodysFiles,
                measurement);
        forgetThatItFinished(census, StepNames.CONTENT_CENSUS);
        forgetThatItFinished(redundancy, StepNames.CONTENT_REDUNDANCY);
        forgetThatItFinished(measurement, StepNames.SEED_CORPUS_COMPARISON);
        logged.list.clear();

        cli.run("run", root.toString());

        claim("the second invocation reported success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "resolving redundancy again, with the synthetic signatures among the signed, wrote the "
                        + verdictsOfTheFirstInvocation.size() + " verdict(s) the first invocation wrote under"
                        + " that run and no other: the same documents, kinds and reasons",
                () -> assertThat(verdictsUnder(redundancy)).isEqualTo(verdictsOfTheFirstInvocation));
        claim(
                "and no verdict of any run is against a file of the folder nobody walked",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM verdict WHERE occurrence_id >= ? AND occurrence_id <= ?",
                                Long.class,
                                nobodysFiles.getFirst(),
                                nobodysFiles.getLast()))
                        .isZero());
        saidNothingAboutHowFar("Stage 3 (content census, reading extraction metrics)");
        saidAboutHowFar(
                "Stage 4b (redundancy resolution, reading signed occurrences)",
                rowSpanUnder("minhash_signature", redundancy));
        saidAboutHowFar(
                "Stage 5b (seed/corpus comparison, reading unusable seeds)", rowSpanUnder("unusable_seed", measurement));
        saidNothingAboutHowFar("Stage 5b (seed/corpus comparison, reading corpus metrics)");
        saidAboutHowFar(
                "Stage 5b (seed/corpus comparison, reading seed metrics)", rowSpanUnder("extraction_metric", measurement));
        claim(
                "and with unusable seeds recorded, the comparison says it is reading them, over up to the rows its"
                        + " run holds, and how long that took, between its read of the seed folder's files and its"
                        + " reads of the metrics",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_FIVE_B))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(STAGE_FIVE_B, SEED_OCCURRENCES),
                                StatementLines.countedRead(
                                        STAGE_FIVE_B, UNUSABLE_SEEDS, rowSpanUnder("unusable_seed", measurement)),
                                StatementLines.countedRead(
                                        STAGE_FIVE_B,
                                        "the corpus survivors' extraction metrics",
                                        rowSpanUnder("extraction_metric", extraction)),
                                StatementLines.countedRead(
                                        STAGE_FIVE_B,
                                        "the seeds' extraction metrics",
                                        rowSpanUnder("extraction_metric", measurement)))));
    }

    /**
     * ADR-204 section 3: the read of the scores that finds none has still ended, so its line after is
     * written, and then the step says it is gated. The corpus is one file the scripted converter fails on
     * while blaming itself, which stage 2 removes at its end, so stage 5 has a usable seed and nothing to
     * score.
     */
    @Test
    @Story("The relevance report says what it is reading")
    @DisplayName("Where no document carries a score, the report still says it read the scores and how long that took, and reads nothing more")
    void theReportSaysItReadTheScoresWhereItFoundNone(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve(SeedScriptedExtractionBeans.CONVERTER_FAULT), "a file the converter faults on");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds, null);

        cli.run("run", root.toString());

        claim(
                "the report found no score and said it was gated for that reason",
                () -> assertThat(operatorLines())
                        .anyMatch(line -> line.startsWith("stage 5's relevance-report step is gated: no survivor"
                                + " carries a relevance score under ")));
        claim(
                "it said it was reading the scores, and then that it had read them and how long that took: the"
                        + " read ended, having found nothing. It made no other read",
                () -> assertThat(StatementLines.of(operatorLines(), THE_REPORT))
                        .containsExactlyElementsOf(StatementLines.timedRead(THE_REPORT, "the scores")));
        claim(
                "and the line that the read ended comes before the line that the step is gated",
                () -> assertThat(String.join("\n", operatorLines()))
                        .containsSubsequence(
                                THE_REPORT + " read the scores in ", "stage 5's relevance-report step is gated"));
    }

    /**
     * ADR-204 section 3, for 5f over two partitions, as ADR-211 section 5 changes it: each partition's members
     * are read just before that partition is grouped, each read naming its partition and how many there are,
     * and the sizes of a partition's groups once that partition is grouped. One partition is held at a time, so
     * the keys read are counted for each partition, as the pairs of blocks are.
     *
     * <p>The scripted embedder answers every chunk alike, so one seed wins every document and no corpus here
     * makes two partitions of its own. So a first invocation scores the collection against two seeds, the
     * score row of one document is then rewritten to name the other seed as the one that won it, and the
     * record that grouping finished is deleted: the next invocation groups again, under the same run, over
     * two partitions. Which seed is the first partition is the order of their ids and is not claimed.
     */
    @Test
    @Story("Grouping says what it is reading")
    @DisplayName("Over two partitions, grouping reads each partition's members just before it groups that partition, and counts each partition's keys on its own")
    @Issue("456")
    @Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
    void clusteringOverTwoPartitionsReadsEachPartitionsMembersJustBeforeItClustersIt(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        Files.writeString(seeds.resolve("second-seed.txt"), "a second seed document, about something else");
        profile(seeds, null);
        cli.run("run", root.toString());
        theStageRan(CLUSTERING_FINISHED);
        String scoring = theLatestRunOf(StageModules.EMBEDDING_SCORING.stage());
        List<Long> winners = jdbcTemplate.queryForList(
                "SELECT DISTINCT winning_seed_occurrence_id FROM relevance_score WHERE run_id = ?", Long.class, scoring);
        claim(
                "the first invocation scored every document against one winning seed: one partition",
                () -> assertThat(winners).hasSize(ONE_PARTITION));
        Long theOtherSeed = jdbcTemplate.queryForObject(
                "SELECT id FROM file_occurrence WHERE id <> ?"
                        + " AND walk_id = (SELECT walk_id FROM file_occurrence WHERE id = ?)",
                Long.class,
                winners.getFirst(),
                winners.getFirst());
        jdbcTemplate.update(
                "UPDATE relevance_score SET winning_seed_occurrence_id = ? WHERE run_id = ? AND occurrence_id ="
                        + " (SELECT MIN(occurrence_id) FROM relevance_score WHERE run_id = ?)",
                theOtherSeed,
                scoring,
                scoring);
        forgetThatItFinished(scoring, StepNames.CLUSTERING);
        logged.list.clear();

        cli.run("run", root.toString());

        claim("the second invocation reported success", () -> assertThat(cli.getExitCode()).isZero());
        theStageRan(CLUSTERING_FINISHED);
        claim(
                "grouping counted " + TWO_PARTITIONS + " partitions",
                () -> assertThat(progressOf(PARTITIONS))
                        .containsExactlyElementsOf(ProgressLines.expected(PARTITIONS, TWO_PARTITIONS)));
        claim(
                "the recorded keys read are one counter for each partition, naming it and how many there are,"
                        + " each over that partition's own members, " + CORPUS_DOCUMENTS + " in all between them",
                () -> {
                    int first = progressOf(KEYS_READ_OF_THE_FIRST_OF_TWO).size();
                    int second = progressOf(KEYS_READ_OF_THE_SECOND_OF_TWO).size();
                    assertThat(first).isPositive();
                    assertThat(second).isPositive();
                    assertThat(first + second).isEqualTo(CORPUS_DOCUMENTS);
                    assertThat(progressOf(KEYS_READ_OF_THE_FIRST_OF_TWO))
                            .containsExactlyElementsOf(ProgressLines.expected(KEYS_READ_OF_THE_FIRST_OF_TWO, first));
                    assertThat(progressOf(KEYS_READ_OF_THE_SECOND_OF_TWO))
                            .containsExactlyElementsOf(ProgressLines.expected(KEYS_READ_OF_THE_SECOND_OF_TWO, second));
                });
        claim(
                "each partition's counter over its pairs of blocks names the partition and how many there are:"
                        + " partition 1 of 2 and partition 2 of 2, one pair of blocks each",
                () -> {
                    assertThat(progressOf(BLOCKS_OF_THE_FIRST_OF_TWO))
                            .containsExactlyElementsOf(
                                    ProgressLines.expected(BLOCKS_OF_THE_FIRST_OF_TWO, ONE_PAIR_OF_BLOCKS));
                    assertThat(progressOf(BLOCKS_OF_THE_SECOND_OF_TWO))
                            .containsExactlyElementsOf(
                                    ProgressLines.expected(BLOCKS_OF_THE_SECOND_OF_TWO, ONE_PAIR_OF_BLOCKS));
                });
        claim(
                "it says what it is reading and how long each read took, each line once, in the order it reads:"
                        + " the seed partitions; the embedder identities, once for both partitions; the members of"
                        + " partition 1 of 2 and, once it is grouped, its group"
                        + " sizes; then the same of partition 2 of 2; and nothing of reading the documents left",
                () -> assertThat(StatementLines.of(operatorLines(), STAGE_FIVE_F))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedRead(STAGE_FIVE_F, "the seed partitions"),
                                StatementLines.timedRead(STAGE_FIVE_F, EMBEDDER_IDENTITIES),
                                StatementLines.timedRead(STAGE_FIVE_F, "the members of partition 1 of 2"),
                                StatementLines.timedRead(STAGE_FIVE_F, "the cluster sizes of partition 1 of 2"),
                                StatementLines.timedRead(STAGE_FIVE_F, "the members of partition 2 of 2"),
                                StatementLines.timedRead(STAGE_FIVE_F, "the cluster sizes of partition 2 of 2"))));
        claim(
                "and each partition is gone through whole before the next one's members are read: its members,"
                        + " its keys, its group sizes and its being counted as done, the first partition before the"
                        + " second",
                () -> assertThat(String.join("\n", operatorLines()))
                        .containsSubsequence(
                                STAGE_FIVE_F + " read the members of partition 1 of 2 in ",
                                KEYS_READ_OF_THE_FIRST_OF_TWO + ": 1 of ",
                                STAGE_FIVE_F + " read the cluster sizes of partition 1 of 2 in ",
                                PARTITIONS + ": 1 of 2",
                                STAGE_FIVE_F + " read the members of partition 2 of 2 in ",
                                KEYS_READ_OF_THE_SECOND_OF_TWO + ": 1 of ",
                                STAGE_FIVE_F + " read the cluster sizes of partition 2 of 2 in ",
                                PARTITIONS + ": 2 of 2"));
    }

    /**
     * ADR-204 section 3, the other half of what it says of the report's read of the scores: a read that fails
     * for any reason but finding none is a statement that failed, and writes no line after it. The table of
     * scores is renamed away between two invocations, so the second's read of it fails inside the database,
     * and renamed back whatever happens, one database serving the whole class.
     */
    @Test
    @Story("The relevance report says what it is reading")
    @DisplayName("Where the read of the scores fails, the report says it began reading them and never that it read them")
    void theReportDoesNotSayItReadTheScoresWhereTheReadFails(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        theStageRan(RELEVANCE_REPORT_FINISHED);
        logged.list.clear();

        jdbcTemplate.execute("ALTER TABLE relevance_score RENAME TO relevance_score_renamed_away");
        try {
            cli.run("run", root.toString());
        } finally {
            jdbcTemplate.execute("ALTER TABLE relevance_score_renamed_away RENAME TO relevance_score");
        }

        claim(
                "the invocation failed, and the report did not finish",
                () -> {
                    assertThat(cli.getExitCode()).isNotZero();
                    assertThat(operatorLines()).noneMatch(line -> line.startsWith(RELEVANCE_REPORT_FINISHED));
                });
        claim(
                "the report said it was reading the scores, and no line says it read them: the one line about a"
                        + " read of its is the line before",
                () -> assertThat(StatementLines.of(operatorLines(), THE_REPORT))
                        .containsExactly(THE_REPORT + " is reading the scores"));
    }

    /** The two partitions the rewritten score row makes of one. */
    private static final int TWO_PARTITIONS = 2;

    private static final String BLOCKS_OF_THE_FIRST_OF_TWO = "Stage 5f (clustering, comparison blocks, partition 1 of 2)";
    private static final String BLOCKS_OF_THE_SECOND_OF_TWO =
            "Stage 5f (clustering, comparison blocks, partition 2 of 2)";

    /** Enough rows for a read that takes five steps a row to pass the 100,000 at which SQLite first calls back. */
    private static final int ROWS_PAST_ONE_CALLBACK_AT_FIVE_STEPS = 21_000;

    /** Enough for one that takes seven steps a row, and so for one that takes twelve. */
    private static final int ROWS_PAST_ONE_CALLBACK_AT_SEVEN_STEPS = 15_000;

    private static final String METRIC_ROW = "INSERT INTO extraction_metric (occurrence_id, run_id, status,"
            + " processing_time, character_count, alphanumeric_char_count, word_count,"
            + " word_character_length_total, vowelless_word_count, single_character_word_count)"
            + " VALUES (?, ?, 'success', 1.0, 1, 1, 1, 1, 0, 0)";

    /**
     * ADR-211 section 9: a read made a page of surviving documents at a time goes through their rows alone, so
     * the rows of a folder nobody walked bring it no nearer its first progress line, which the collection's own
     * few rows are far short of. Its line before and its line after are still written, over up to every row of
     * the run.
     */
    private void saidNothingAboutHowFar(String label) {
        claim(
                label + " says nothing about how far it has gone: it reads only the rows of the documents left, a"
                        + " page at a time, and never goes through the tens of thousands of rows of files no walk of"
                        + " this collection holds",
                () -> assertThat(operatorLines()).noneMatch(line -> line.startsWith(label + ": ")));
    }

    /**
     * ADR-211 section 9, the other side of the same rule: such a read is told its rows after each page and
     * waits for no hundred thousand steps, so over a run that holds the collection's own rows and no other, its
     * one page writes one line, at a hundred percent, every document having survived.
     */
    private void saidHowFarOnceOverItsOwnRows(String label, long rowsUpTo) {
        String total = String.format(Locale.ROOT, "%,d", rowsUpTo);
        claim(
                label + " says how far it has gone once, after its one page: all of the " + total + " rows its"
                        + " run holds, which are the collection's own documents' and every one of them read",
                () -> assertThat(operatorLines())
                        .filteredOn(line -> line.startsWith(label + ": "))
                        .containsExactly(label + ": about 100% of " + total + " rows"));
    }

    /** That the read labelled {@code label} wrote a progress line, and every one over {@code rowsUpTo} rows. */
    private void saidAboutHowFar(String label, long rowsUpTo) {
        String total = String.format(Locale.ROOT, "%,d", rowsUpTo);
        claim(
                label + " says about how far it has gone at least once, each time as a share of the " + total
                        + " rows its run holds from first to last",
                () -> assertThat(operatorLines())
                        .filteredOn(line -> line.startsWith(label + ": "))
                        .isNotEmpty()
                        .allMatch(line -> line.matches(".*: about \\d+% of " + total + " rows$")));
    }

    /** The run of {@code stage} minted last: this test's, read straight after its first invocation. */
    private String theLatestRunOf(String stage) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM run WHERE stage = ? ORDER BY rowid DESC LIMIT 1", String.class, stage);
    }

    /** {@code files} recorded under a walk of a folder that does not exist: in no run's survivors. */
    private List<Long> filesOfAFolderNobodyWalked(int files) {
        WalkId walk = ledger.walks().startWalk(Path.of("C:/synthetic-" + System.nanoTime()));
        List<Object[]> rows = new ArrayList<>();
        for (int file = 0; file < files; file++) {
            rows.add(new Object[] {walk.value(), "synthetic-" + file + ".txt"});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                        + " VALUES (?, ?, 1, 1, 1)",
                rows);
        return jdbcTemplate.queryForList(
                "SELECT id FROM file_occurrence WHERE walk_id = ? ORDER BY id", Long.class, walk.value());
    }

    private void writeForEach(String insertRow, List<Long> occurrences, String run) {
        List<Object[]> rows = new ArrayList<>();
        for (Long occurrence : occurrences) {
            rows.add(new Object[] {occurrence, run});
        }
        jdbcTemplate.batchUpdate(insertRow, rows);
    }

    /** Every verdict under {@code run}, as the occurrence, the kind and the reason, in a fixed order. */
    private List<String> verdictsUnder(String run) {
        return jdbcTemplate.queryForList(
                "SELECT occurrence_id || ' ' || kind || ' ' || reason FROM verdict WHERE run_id = ?"
                        + " ORDER BY occurrence_id, kind, reason",
                String.class,
                run);
    }

    private void forgetThatItFinished(String run, String step) {
        jdbcTemplate.update("DELETE FROM finished_step WHERE run_id = ? AND step = ?", run, step);
    }

    /** The span of {@code run}'s rowids in {@code table}: greatest less least plus one, zero where it holds none. */
    private long rowSpanUnder(String table, String run) {
        Long span = jdbcTemplate.queryForObject(
                "SELECT MAX(rowid) - MIN(rowid) + 1 FROM " + table + " WHERE run_id = ?", Long.class, run);
        return span == null ? 0 : span;
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
        String identity = jdbcTemplate.queryForObject("SELECT DISTINCT embedder_identity FROM vector", String.class);
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
