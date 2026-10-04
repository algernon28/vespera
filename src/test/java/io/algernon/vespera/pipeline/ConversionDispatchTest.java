package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.HeldExtractor;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * How far ahead of the document it hands over stage 2's reader has already asked the converter
 * (ADR-176), claimed on the reader itself: {@link ConversionDispatch#read} is called as the step calls
 * it, and what was dispatched is read off the converter.
 *
 * <p>The converter is a {@link HeldExtractor}, whose calls do not answer until the test lets them. A
 * converter that answered at once would let a reader that dispatched only what it handed over look the
 * same as one that asked ahead a moment later. Held, what was asked about is a count the reader alone
 * decided.
 *
 * <p>The ledger is real and stage 1 really runs first, as in {@link ExtractionItemProcessorTest}: the
 * reader dispatches only a document stage 1 recorded a format for, under stage 1's run.
 *
 * <p>That the processor still meets failures in the order the documents were read is {@link
 * ExtractionConcurrencyTest}'s claim and {@code ExtractionCircuitBreakerTest}'s, and neither is changed
 * by this record.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("369")
@Link(name = "ADR-176", url = Adr.STAGE_2_READS_AHEAD_ACROSS_CHUNKS, type = "adr")
@Link(name = "ADR-140", url = Adr.STAGE_2_CONVERTS_EIGHT_AT_A_TIME, type = "adr")
class ConversionDispatchTest {

    /**
     * Stage 1's working directory, where its {@code profile.yaml} is written: a temporary folder of
     * this test's own, outside the corpus root so the walk never reads it, and shared with no other
     * test, class, run or branch. A shared one let a stale {@code profile.yaml} from elsewhere change
     * what stages 1 and 2 do here.
     */
    @TempDir
    Path stage1Working;

    /** The engine every dispatched call is attributed to; which engine it is does not matter here. */
    private static final ExtractorIdentity IDENTITY = new ExtractorIdentity("docling-serve;held");

    /** How many conversions stage 2 runs at once (ADR-140). */
    private static final int CONVERSIONS_AT_ONCE = 8;

    /**
     * How many documents beyond the one being handed over have already been put to the converter: two
     * whole waves of the eight that convert at once (ADR-176).
     */
    private static final int ASKED_AHEAD = 2 * CONVERSIONS_AT_ONCE;

    /** How many documents the step reads before it processes and commits any of them. */
    private static final int READ_BEFORE_A_COMMIT = ExtractionJobConfiguration.CHUNK_SIZE;

    /**
     * More documents than two commits' worth and what is asked ahead of the second, so the reader is
     * still asking ahead after the second commit's documents have been read.
     */
    private static final int DOCUMENTS = 2 * READ_BEFORE_A_COMMIT + ASKED_AHEAD + CONVERSIONS_AT_ONCE;

    /** How many times the reader is asked again after its last document, to see that it stays ended. */
    private static final int READS_PAST_THE_END = 3;

    /**
     * How long a claim waits for a conversion to begin, for an interrupted one to end, or for a
     * question that should be answered at once. Generous, because it bounds a thread being scheduled
     * and nothing else.
     */
    private static final Duration UNTIL_AN_INTERRUPTED_CONVERSION_ENDS = Duration.ofSeconds(10);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    private final HeldExtractor converter = new HeldExtractor();

    private ConversionDispatch reader;

    /** Closed first, so the worker threads end, and let go second, in case a claim failed before either. */
    @AfterEach
    void endTheConversions() {
        if (reader != null) {
            reader.close();
        }
        converter.release();
    }

    @Test
    @Story("How far ahead of the document being handed over the converter has been asked")
    @DisplayName("After each document is handed over, the converter has been asked about the next sixteen as well, across the point where the step commits")
    void asksAheadAcrossTheCommit(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, DOCUMENTS);
        reader = readerOver(corpus, new InOrder(corpus.occurrences()));

        List<Integer> askedAboutAfterEachRead = new ArrayList<>();
        for (int read = 1; read <= DOCUMENTS; read++) {
            reader.read();
            askedAboutAfterEachRead.add(converter.lookedUp().size());
        }

        claim(
                "once the first document is handed over, the converter has been asked about it and about the"
                        + " " + ASKED_AHEAD + " after it, although none of them has answered: two waves of"
                        + " the " + CONVERSIONS_AT_ONCE + " that convert at once are always waiting behind"
                        + " the document in hand",
                () -> assertThat(askedAboutAfterEachRead.getFirst()).isEqualTo(1 + ASKED_AHEAD));
        claim(
                "once the " + READ_BEFORE_A_COMMIT + " documents of the first commit are handed over, and"
                        + " before any of them is processed or committed, the converter has already been"
                        + " asked about the " + ASKED_AHEAD + " that follow them, which belong to later"
                        + " commits: it is not left idle while the first one drains and commits",
                () -> assertThat(askedAboutAfterEachRead.get(READ_BEFORE_A_COMMIT - 1))
                        .isEqualTo(READ_BEFORE_A_COMMIT + ASKED_AHEAD));
        claim(
                "after every document handed over, the converter has been asked about that many and the"
                        + " " + ASKED_AHEAD + " after them, and never about more than the " + DOCUMENTS
                        + " there are",
                () -> assertThat(askedAboutAfterEachRead).containsExactlyElementsOf(expectedAskedAbout()));
    }

    @Test
    @Story("The order documents are handed over in")
    @DisplayName("Documents are handed over in the order they were listed, whatever order the converter answers in")
    void handsOverInTheOrderListedWhateverOrderTheAnswersArriveIn(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, DOCUMENTS);
        reader = readerOver(corpus, new InOrder(corpus.occurrences()));

        List<OccurrenceId> handedOver = new ArrayList<>();
        for (int read = 1; read <= READ_BEFORE_A_COMMIT; read++) {
            handedOver.add(reader.read());
        }
        converter.release();
        for (int read = READ_BEFORE_A_COMMIT + 1; read <= DOCUMENTS; read++) {
            handedOver.add(reader.read());
        }

        claim(
                "every document was handed over once, in the order the step was given them, although the"
                        + " converter was let go part-way, the conversions it was holding being let go latest"
                        + " first: the order answers arrive in decides nothing about the order"
                        + " documents are judged in, which is the order failures in a row are counted over",
                () -> assertThat(handedOver).containsExactlyElementsOf(corpus.occurrences()));
    }

    @Test
    @Story("The end of the documents")
    @DisplayName("After the last document is listed, the ones already asked about are still handed over, then nothing, and the list is not asked again")
    void handsOverWhatWasAskedAheadThenNothing(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, DOCUMENTS);
        InOrder listed = new InOrder(corpus.occurrences());
        reader = readerOver(corpus, listed);

        List<OccurrenceId> handedOver = new ArrayList<>();
        for (int read = 1; read <= DOCUMENTS; read++) {
            handedOver.add(reader.read());
        }
        List<OccurrenceId> afterTheLast = new ArrayList<>();
        for (int read = 1; read <= READS_PAST_THE_END; read++) {
            afterTheLast.add(reader.read());
        }

        claim(
                "all " + DOCUMENTS + " documents were handed over, the last " + ASKED_AHEAD + " of them after"
                        + " the list had already said it held no more: what was asked ahead is not lost when"
                        + " the list ends",
                () -> assertThat(handedOver).containsExactlyElementsOf(corpus.occurrences()));
        claim(
                "every read after the last document hands over nothing, which is how the step learns it is done",
                () -> assertThat(afterTheLast).hasSize(READS_PAST_THE_END).containsOnlyNulls());
        claim(
                "the list was asked once for each of the " + DOCUMENTS + " documents and once more, when it said"
                        + " it held no more, and never again after that: a list that has ended is not asked twice",
                () -> assertThat(listed.timesAsked()).isEqualTo(DOCUMENTS + 1));
    }

    @Test
    @Story("A step that ends with documents asked ahead")
    @DisplayName("When the step ends with documents asked ahead and not yet handed over, their conversions are dropped and nothing is kept for them")
    void closingDropsWhatWasAskedAheadAndKeepsNothingForIt(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, DOCUMENTS);
        reader = readerOver(corpus, new InOrder(corpus.occurrences()));
        for (int read = 1; read <= READ_BEFORE_A_COMMIT; read++) {
            reader.read();
        }
        List<OccurrenceId> askedAheadAndNotHandedOver =
                corpus.occurrences().subList(READ_BEFORE_A_COMMIT, READ_BEFORE_A_COMMIT + ASKED_AHEAD);

        claim(
                "before the step ends, the converter is working on " + CONVERSIONS_AT_ONCE + " documents, as"
                        + " many as convert at once, and has answered none of them",
                () -> assertThat(converter.holdingWithin(CONVERSIONS_AT_ONCE, UNTIL_AN_INTERRUPTED_CONVERSION_ENDS))
                        .isTrue());

        reader.close();

        claim(
                "no conversion is still under way once the step has ended: the ones the converter was working"
                        + " on were interrupted, and the ones waiting behind them never start",
                () -> assertThat(converter.noneConvertingWithin(UNTIL_AN_INTERRUPTED_CONVERSION_ENDS))
                        .isTrue());
        claim(
                "the " + CONVERSIONS_AT_ONCE + " conversions under way when the step ended were each interrupted,"
                        + " and none of them was left to run to its answer",
                () -> assertThat(converter.interrupted()).isEqualTo(CONVERSIONS_AT_ONCE));
        claim(
                "none of the " + ASKED_AHEAD + " documents asked ahead has an answer waiting for it any more: asking"
                        + " for one returns at once with nothing, where an answer still filed for a conversion"
                        + " that will never run would be waited on for ever",
                () -> assertThat(CompletableFuture.supplyAsync(() -> askedAheadAndNotHandedOver.stream()
                                .map(corpus.pending()::take)
                                .toList()))
                        .succeedsWithin(UNTIL_AN_INTERRUPTED_CONVERSION_ENDS)
                        .asInstanceOf(InstanceOfAssertFactories.LIST)
                        .hasSize(ASKED_AHEAD)
                        .containsOnly(Optional.empty()));
        claim(
                "and nothing was kept in the conversion cache for any document: an answer is kept only when a"
                        + " document is handed on to be judged, and none was",
                () -> assertThat(converter.remembered()).isEmpty());
    }

    /** For each document handed over, in order: that many plus what is asked ahead, up to the total. */
    private static List<Integer> expectedAskedAbout() {
        List<Integer> expected = new ArrayList<>();
        for (int read = 1; read <= DOCUMENTS; read++) {
            expected.add(Math.min(DOCUMENTS, read + ASKED_AHEAD));
        }
        return expected;
    }

    /** The reader as the step builds it, over the real ledger, the held converter and {@code delegate}. */
    private ConversionDispatch readerOver(Corpus corpus, ItemStreamReader<OccurrenceId> delegate) {
        ConversionDispatch dispatch = new ConversionDispatch(
                delegate,
                corpus.ledger(),
                new ContentIdentity(jdbcTemplate),
                new DetectedFormats(jdbcTemplate),
                converter,
                () -> IDENTITY,
                corpus.stage2(),
                corpus.pending(),
                CONVERSIONS_AT_ONCE);
        dispatch.open(new ExecutionContext());
        return dispatch;
    }

    /**
     * A walked corpus of {@code files} distinct documents, with stage 1 already run over it and stage
     * 2's run minted, as {@link ExtractionItemProcessorTest} builds one.
     */
    private Corpus corpusOf(Path root, int files) throws Exception {
        List<OccurrencePath> paths = new ArrayList<>();
        for (int i = 0; i < files; i++) {
            String name = "document-" + i + ".txt";
            Files.writeString(root.resolve(name), "the content of document " + i);
            paths.add(new OccurrencePath(name));
        }
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource))
                .walk(root);
        ImplementationVersions versions = new ImplementationVersions();
        ChunkContext step = InvocationRecordFixture.aStepOfAFreshInvocation();
        Path workingDirectory = stage1Working;
        io.algernon.vespera.profile.ProfileStore profileStore =
                new io.algernon.vespera.profile.ProfileStore(workingDirectory);
        new ByteLevelReductionTasklet(
                        ledger,
                        new ContentIdentity(jdbcTemplate),
                        new DetectedFormats(jdbcTemplate),
                        versions,
                        profileStore,
                        root,
                        workingDirectory)
                .execute(null, step);
        // Stage 2 passes no gate, so the gates and the later stages' collaborators are left out. The
        // profile is the one stage 1 read, because stage 2 reads extractionAttempt from it fresh when its
        // run is minted (ADR-185 §1); nothing here writes that key, so the run minted is the first attempt's.
        StageRuns stageRuns = new StageRuns(
                ledger,
                versions,
                new StaticListableBeanFactory(Map.of("extractorIdentity", IDENTITY))
                        .getBeanProvider(ExtractorIdentity.class),
                new DegenerateOutputConfidenceFloor(null),
                null,
                null,
                null,
                profileStore,
                null,
                null,
                null,
                null,
                root,
                InvocationRecordFixture.recordOf(step));
        stageRuns.extraction();
        List<OccurrenceId> occurrences = paths.stream()
                .map(path -> ledger.occurrenceId(walkId, path).orElseThrow())
                .toList();
        return new Corpus(ledger, stageRuns, new PendingConversions(), occurrences);
    }

    /** One walked corpus, the run stage 2 reads it under, and where dispatched answers are filed. */
    private record Corpus(
            Ledger ledger, StageRuns stage2, PendingConversions pending, List<OccurrenceId> occurrences) {}

    /**
     * The survivors reader's stand-in: the occurrences in the order given, then nothing, counting how
     * often it was asked.
     */
    private static final class InOrder implements ItemStreamReader<OccurrenceId> {

        private final Iterator<OccurrenceId> remaining;
        private int timesAsked;

        InOrder(List<OccurrenceId> occurrences) {
            this.remaining = occurrences.iterator();
        }

        int timesAsked() {
            return timesAsked;
        }

        @Override
        public OccurrenceId read() {
            timesAsked++;
            return remaining.hasNext() ? remaining.next() : null;
        }
    }
}
