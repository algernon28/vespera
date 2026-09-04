package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DoclingError;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.FailureCategory;
import io.algernon.vespera.extraction.HybridChunkerBeans;
import io.algernon.vespera.extraction.LanguageDetection;
import io.algernon.vespera.extraction.ScriptedExtractor;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.similarity.Shingler;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
 * What one Docling response earns an occurrence (ADR-070, ADR-071): whether it is a fact about the
 * document, and so a verdict, or a fact about the sidecar, and so no row at all.
 *
 * <p>The responses are scripted rather than served, because most of what is claimed here is a
 * sequence — three timeouts in a row read differently from three timeouts apart — and no single
 * response can express one. The real client's parsing and the real cache are pinned by
 * {@code DoclingClientTest} and {@code DoclingExtractorTest}; what is worth proving here is the
 * judgement over an already-parsed response.
 *
 * <p>The ledger is real, over the test database, and stage 1 really runs first, because stage 2's run
 * names stage 1's as upstream and a foreign key enforces that the row exists. Faking either would
 * make the thing this class most wants to be sure of — that the processor is judging the occurrences
 * of the walk it was given — an assumption.
 *
 * <p><b>{@code policy} and {@code source_unavailable} are the conditional pair.</b> ADR-070 resolves
 * them "per occurrence", and issue #47's resolution comment reads that as a per-response conditional:
 * document scope unless a genuine service-scope category ({@code capacity},
 * {@code target_unavailable}, {@code internal} — not {@code unknown}) sits anywhere else in the same
 * response's {@code errors[]}, in which case the whole response is read as being about the sidecar.
 * The four cases that reading produces are claimed below.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("47")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
@Link(name = "ADR-071", url = Adr.DOCLING_INVOCATION_CONTRACT_IS_ONE_SYNC_CALL, type = "adr")
class Stage2ItemProcessorTest {

    /** The engine every response in this class is attributed to; which engine it is does not matter here. */
    private static final ExtractorIdentity IDENTITY = new ExtractorIdentity("docling-serve;scripted");

    /** Stands in for whatever free text Docling put in an {@code errors[]} entry. */
    private static final String ERROR_MESSAGE = "the message Docling reported";

    /**
     * How many timeouts in a row stop being a fact about any one document (ADR-071), read off the
     * counter rather than repeated, so this class cannot disagree with the rule it is claiming.
     */
    private static final int TIMEOUTS_THAT_READ_AS_A_DEAD_SIDECAR = Stage2TimeoutStreak.CONSECUTIVE_TIMEOUT_COUNT;

    /** The timeouts before that one, each still a fact about its own document. */
    private static final int TIMEOUTS_STILL_READ_AS_THE_DOCUMENTS = TIMEOUTS_THAT_READ_AS_A_DEAD_SIDECAR - 1;

    /**
     * The responses ADR-070 reads as facts about the document: its two document-blamed failure kinds,
     * plus one where the converter reported skipping the document for the same reason.
     */
    private static final int RESPONSES_BLAMED_ON_THE_DOCUMENT = 3;

    /**
     * How many measurement rows one judged response earns: {@code ExtractionMetrics} writes exactly
     * once per response a document actually came back for, so a document-scoped reading leaves one row
     * and a service-scoped one leaves none.
     */
    private static final int METRIC_ROWS_PER_JUDGED_RESPONSE = 1;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("A failure that is a fact about the document")
    @DisplayName("A failure the converter blames on the document itself is recorded against that document")
    void verdictsADocumentScopedFailure(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 1);
        ScriptedExtractor docling =
                new ScriptedExtractor().answering(failing(FailureCategory.BACKEND_FAILURE));

        Stage2Outcome outcome = processorOver(corpus, docling).process(corpus.occurrence(0));

        claim(
                "the document is recorded as one extraction could not read, which is the only verdict this"
                        + " step writes",
                () -> assertThat(outcome.kind()).isEqualTo(VerdictKind.EXTRACTION_FAILED));
        claim(
                "and the row says which kind of failure it was and what the converter said about it, so the"
                        + " reason stands on its own without the response it came from",
                () -> assertThat(outcome.reason()).contains("backend_failure").contains(ERROR_MESSAGE));
    }

    @Test
    @Story("A failure that is a fact about the document")
    @DisplayName("Both document-blamed failure kinds are recorded, and a converter that reports it skipped the document counts too")
    void verdictsEveryDocumentScopedCategory(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, RESPONSES_BLAMED_ON_THE_DOCUMENT);
        ScriptedExtractor docling = new ScriptedExtractor()
                .answering(failing(FailureCategory.BACKEND_FAILURE))
                .answering(failing(FailureCategory.INFERENCE_FAILURE))
                .answering(new DoclingResponse(
                        ConversionStatus.SKIPPED, List.of(error(FailureCategory.BACKEND_FAILURE)), 0d, null, "{}"));
        Stage2ItemProcessor processor = processorOver(corpus, docling);

        List<VerdictKind> kinds = new ArrayList<>();
        for (int i = 0; i < corpus.size(); i++) {
            kinds.add(processor.process(corpus.occurrence(i)).kind());
        }

        claim(
                "each of the " + RESPONSES_BLAMED_ON_THE_DOCUMENT + " responses is recorded against its own"
                        + " document: the two failure kinds the converter blames on the document, and one"
                        + " document it reported skipping for the same reason",
                () -> assertThat(kinds)
                        .containsExactly(
                                VerdictKind.EXTRACTION_FAILED,
                                VerdictKind.EXTRACTION_FAILED,
                                VerdictKind.EXTRACTION_FAILED));
    }

    @Test
    @Story("A failure that is a fact about the service")
    @DisplayName("A failure the converter blames on itself leaves the document unjudged rather than condemned")
    void skipsEveryServiceScopedCategory(@TempDir Path root) throws Exception {
        List<FailureCategory> blamedOnTheService = List.of(
                FailureCategory.CAPACITY,
                FailureCategory.TARGET_UNAVAILABLE,
                FailureCategory.INTERNAL,
                FailureCategory.UNKNOWN);
        Corpus corpus = corpusOf(root, blamedOnTheService.size());
        ScriptedExtractor docling = new ScriptedExtractor();
        blamedOnTheService.forEach(category -> docling.answering(failing(category)));
        Stage2ItemProcessor processor = processorOver(corpus, docling);

        for (int i = 0; i < blamedOnTheService.size(); i++) {
            FailureCategory category = blamedOnTheService.get(i);
            OccurrenceId occurrence = corpus.occurrence(i);
            claim(
                    "a failure reported as " + category.name().toLowerCase(Locale.ROOT)
                            + " says nothing about the document, so the document is set aside for a later run"
                            + " instead of being judged on it",
                    () -> assertThatThrownBy(() -> processor.process(occurrence))
                            .isInstanceOf(ServiceScopeFailure.class));
        }
    }

    @Test
    @Story("A failure that is a fact about the service")
    @DisplayName("A failure with no kind reported at all is treated as saying nothing, not as saying the worst")
    void skipsAFailureThatReportedNoCategoryAtAll(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 1);
        ScriptedExtractor docling = new ScriptedExtractor()
                .answering(new DoclingResponse(ConversionStatus.FAILURE, List.of(), 0d, null, "{}"));
        Stage2ItemProcessor processor = processorOver(corpus, docling);

        claim(
                "a failure carrying no reported kind is no evidence about the document either, and the safe"
                        + " reading of no evidence is that nobody has judged it yet",
                () -> assertThatThrownBy(() -> processor.process(corpus.occurrence(0)))
                        .isInstanceOf(ServiceScopeFailure.class));
    }

    @Test
    @Story("A failure whose scope depends on the rest of the response")
    @DisplayName("A refusal, or an unreadable source, reported on its own is recorded against the document")
    void verdictsARefusalReportedOnItsOwn(@TempDir Path root) throws Exception {
        List<FailureCategory> aboutThisFileUnlessTheResponseSaysOtherwise =
                List.of(FailureCategory.POLICY, FailureCategory.SOURCE_UNAVAILABLE);
        Corpus corpus = corpusOf(root, aboutThisFileUnlessTheResponseSaysOtherwise.size());
        ScriptedExtractor docling = new ScriptedExtractor();
        aboutThisFileUnlessTheResponseSaysOtherwise.forEach(category -> docling.answering(failing(category)));
        Stage2ItemProcessor processor = processorOver(corpus, docling);

        for (int i = 0; i < aboutThisFileUnlessTheResponseSaysOtherwise.size(); i++) {
            FailureCategory category = aboutThisFileUnlessTheResponseSaysOtherwise.get(i);
            OccurrenceId occurrence = corpus.occurrence(i);
            claim(
                    "a converter that reported only " + category.name().toLowerCase(Locale.ROOT)
                            + " is saying something about the file it was handed -- it refused this one, or"
                            + " could not read this one -- so the document is recorded as one extraction"
                            + " could not read",
                    () -> assertThat(processor.process(occurrence).kind()).isEqualTo(VerdictKind.EXTRACTION_FAILED));
            claim(
                    "and the measurement is filed against it as well, because a reading about the document is"
                            + " a reading of a response the document really came back for -- "
                            + METRIC_ROWS_PER_JUDGED_RESPONSE + " row recorded for it",
                    () -> assertThat(metricRowsFor(occurrence)).isEqualTo(METRIC_ROWS_PER_JUDGED_RESPONSE));
        }
    }

    @Test
    @Story("A failure whose scope depends on the rest of the response")
    @DisplayName("A refusal reported alongside a failure the converter blames on itself is read as being about the converter")
    void skipsARefusalReportedAlongsideAConverterProblem(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 1);
        ScriptedExtractor docling =
                new ScriptedExtractor().answering(failing(FailureCategory.POLICY, FailureCategory.CAPACITY));
        Stage2ItemProcessor processor = processorOver(corpus, docling);

        claim(
                "a response that reports a refusal and, in the same breath, that the converter had no room"
                        + " to work is a response about the converter's own state, so the refusal cannot be"
                        + " taken as a fact about this document and the document is set aside unjudged",
                () -> assertThatThrownBy(() -> processor.process(corpus.occurrence(0)))
                        .isInstanceOf(ServiceScopeFailure.class));
        claim(
                "and no measurement is filed against it either, so a later run finds the document exactly"
                        + " as untouched as it was before -- zero rows recorded for it",
                () -> assertThat(metricRowsFor(corpus.occurrence(0))).isZero());
    }

    @Test
    @Story("A failure whose scope depends on the rest of the response")
    @DisplayName("A refusal reported alongside an unexplained error is still recorded against the document")
    void verdictsARefusalReportedAlongsideAnUnexplainedError(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 1);
        ScriptedExtractor docling =
                new ScriptedExtractor().answering(failing(FailureCategory.POLICY, FailureCategory.UNKNOWN));

        Stage2Outcome outcome = processorOver(corpus, docling).process(corpus.occurrence(0));

        claim(
                "an error the converter gave no kind for is no evidence about anything, so it cannot turn a"
                        + " refusal of this file into a statement about the converter: the document is still"
                        + " recorded as one extraction could not read",
                () -> assertThat(outcome.kind()).isEqualTo(VerdictKind.EXTRACTION_FAILED));
        claim(
                "and the row still names the refusal and what the converter said about it, rather than the"
                        + " unexplained error that sat beside it",
                () -> assertThat(outcome.reason()).contains("policy").contains(ERROR_MESSAGE));
    }

    @Test
    @Story("A conversion that partly worked")
    @DisplayName("A document some of whose pages failed is not condemned for that alone")
    void doesNotJudgeAPartlyConvertedDocumentOnItsFailuresAlone(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 1);
        ScriptedExtractor docling = new ScriptedExtractor()
                .answering(new DoclingResponse(
                        ConversionStatus.PARTIAL_SUCCESS,
                        List.of(error(FailureCategory.BACKEND_FAILURE), error(FailureCategory.INFERENCE_FAILURE)),
                        0d,
                        null,
                        "{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"the pages that did convert carried"
                                + " real content\"}]}}}"));

        Stage2Outcome outcome = processorOver(corpus, docling).process(corpus.occurrence(0));

        claim(
                "a document that partly converted is judged on what it produced, not on the fact that"
                        + " something failed inside it -- so this step decides nothing about it and leaves it"
                        + " to whatever measures the text",
                () -> assertThat(outcome).isNull());
    }

    @Test
    @Story("A conversion that succeeded")
    @DisplayName("A converted document's text reaches both the chunk cache and the shingle table")
    @Issue("49")
    void aConvertedDocumentIsChunkedAndShingled(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 1);
        ScriptedExtractor docling = new ScriptedExtractor()
                .answering(new DoclingResponse(
                        ConversionStatus.SUCCESS,
                        List.of(),
                        0d,
                        null,
                        "{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"real content the chunker and"
                                + " the shingler both read\"}]}}}"));

        processorOver(corpus, docling).process(corpus.occurrence(0));

        claim(
                "the chunker's own cache carries at least one chunk for the document, proving the"
                        + " processor actually calls it rather than only building it unused",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chunk_cache", Integer.class))
                        .isPositive());
        claim(
                "and the shingle table carries rows against this occurrence's own run, proving the"
                        + " shingler is called with the same run stage2Run minted",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM shingle WHERE occurrence_id = ? AND run_id = ?",
                                Integer.class,
                                corpus.occurrence(0).value(),
                                corpus.stage2Run().runId().value()))
                        .isPositive());
    }

    @Test
    @Story("Tier 1 — the hard zero-content floor")
    @DisplayName("A document that converts to no usable text earns a degenerate-output verdict")
    void aConversionWithNoUsableTextEarnsADegenerateOutputVerdict(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 1);
        ScriptedExtractor docling = new ScriptedExtractor()
                .answering(new DoclingResponse(
                        ConversionStatus.SUCCESS, List.of(), 0d, null, "{\"document\":{\"json_content\":{\"texts\":[]}}}"));

        Stage2Outcome outcome = processorOver(corpus, docling).process(corpus.occurrence(0));

        claim(
                "a clean conversion carrying no extracted text at all trips tier 1, so it is condemned"
                        + " here rather than reaching later stages as if it were real content",
                () -> assertThat(outcome.kind()).isEqualTo(VerdictKind.DEGENERATE_OUTPUT));
    }

    @Test
    @Story("A converter that stops answering")
    @DisplayName("Silence about one document is a fact about that document; silence about several in a row is a fact about the converter")
    void flipsAConsecutiveRunOfUnansweredCallsToTheConverter(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, TIMEOUTS_THAT_READ_AS_A_DEAD_SIDECAR);
        // The two readings ADR-071 folds together: a response reporting its own timeout, and a call
        // that came back with nothing at all. Mixed on purpose, because they share the one counter.
        ScriptedExtractor docling = new ScriptedExtractor()
                .answering(failing(FailureCategory.TIMEOUT))
                .timingOut()
                .answering(failing(FailureCategory.TIMEOUT));
        Stage2ItemProcessor processor = processorOver(corpus, docling);

        Stage2Outcome first = processor.process(corpus.occurrence(0));
        Stage2Outcome second = processor.process(corpus.occurrence(1));

        claim(
                "a converter that ran out of time on one document says that document was too much for the"
                        + " time it is given, and the document is recorded as one extraction could not read",
                () -> assertThat(first.kind()).isEqualTo(VerdictKind.EXTRACTION_FAILED));
        claim(
                "so does a call that came back with nothing at all, which is the same reading of the same"
                        + " situation and counts against the same run",
                () -> assertThat(second.kind()).isEqualTo(VerdictKind.EXTRACTION_FAILED));
        claim(
                "but once " + TIMEOUTS_THAT_READ_AS_A_DEAD_SIDECAR + " land in a row, that is a statement"
                        + " about the converter rather than about any one document, so this document is set"
                        + " aside unjudged instead",
                () -> assertThatThrownBy(() -> processor.process(
                                corpus.occurrence(TIMEOUTS_THAT_READ_AS_A_DEAD_SIDECAR - 1)))
                        .isInstanceOf(ServiceScopeFailure.class));
    }

    @Test
    @Story("A converter that stops answering")
    @DisplayName("A document that converts between two slow ones means the slow ones were not a pattern")
    void oneConversionEndsTheRun(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 2 * TIMEOUTS_STILL_READ_AS_THE_DOCUMENTS + 1);
        ScriptedExtractor docling = new ScriptedExtractor()
                .timingOut(TIMEOUTS_STILL_READ_AS_THE_DOCUMENTS)
                .answering(converted())
                .timingOut(TIMEOUTS_STILL_READ_AS_THE_DOCUMENTS);
        Stage2ItemProcessor processor = processorOver(corpus, docling);

        claim(
                "with " + TIMEOUTS_STILL_READ_AS_THE_DOCUMENTS + " slow documents, one that converted, and"
                        + " then " + TIMEOUTS_STILL_READ_AS_THE_DOCUMENTS + " more slow ones, nothing is ever"
                        + " set aside: a document that converted in the middle proves the converter was"
                        + " answering, so the run before it stopped counting",
                () -> assertThatCode(() -> {
                            for (int i = 0; i < corpus.size(); i++) {
                                processor.process(corpus.occurrence(i));
                            }
                        })
                        .doesNotThrowAnyException());
        claim(
                "and every document was actually put to the converter, so the claim above is not the"
                        + " absence of work",
                () -> assertThat(docling.conversions()).isEqualTo(corpus.size()));
    }

    /**
     * The measurement rows standing against one occurrence — the presence-or-absence side of the
     * conditional pair, since a service-scoped reading has to leave the occurrence with none.
     */
    private int metricRowsFor(OccurrenceId occurrence) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM extraction_metric WHERE occurrence_id = ?", Integer.class, occurrence.value());
    }

    /** A response Docling answered cleanly with, carrying nothing for this step to judge. */
    private static DoclingResponse converted() {
        return new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 0d, null, "{}");
    }

    /**
     * A response reporting one failure per {@code categories}, in the order given — several of them
     * because the reading of a reported refusal depends on what else the same response reported.
     */
    private static DoclingResponse failing(FailureCategory... categories) {
        List<DoclingError> errors = Arrays.stream(categories).map(Stage2ItemProcessorTest::error).toList();
        return new DoclingResponse(ConversionStatus.FAILURE, errors, 0d, null, "{}");
    }

    private static DoclingError error(FailureCategory category) {
        return new DoclingError("document_backend", "docling", ERROR_MESSAGE, category, null);
    }

    /**
     * The processor as the step builds it: over the real ledger, over the run stage 2 mints for this
     * walk, and over the scripted converter this test wants it to read.
     */
    private Stage2ItemProcessor processorOver(Corpus corpus, ScriptedExtractor docling) {
        return new Stage2ItemProcessor(
                corpus.ledger(),
                new ContentIdentity(jdbcTemplate),
                docling,
                IDENTITY,
                new Stage2TimeoutStreak(),
                corpus.stage2Run(),
                new ExtractionMetrics(jdbcTemplate, new LanguageDetection()),
                new DegenerateOutputConfidenceFloor(null),
                HybridChunkerBeans.real(jdbcTemplate),
                new WordCountTokenizer(),
                new Shingler(jdbcTemplate));
    }

    /**
     * A walked corpus of {@code files} distinct documents, with stage 1 already run over it and
     * stage 2's run minted.
     *
     * <p>Distinct contents, not repeated ones: stage 1 resolves byte-identical documents to a single
     * representative (ADR-069), which would leave this class with fewer occurrences to judge than it
     * asked for.
     */
    private Corpus corpusOf(Path root, int files) throws Exception {
        List<OccurrencePath> paths = new ArrayList<>();
        for (int i = 0; i < files; i++) {
            String name = "document-" + i + ".txt";
            Files.writeString(root.resolve(name), "the content of document " + i);
            paths.add(new OccurrencePath(name));
        }
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);
        ImplementationVersions versions = new ImplementationVersions();
        new Stage1Tasklet(ledger, new ContentIdentity(jdbcTemplate), versions, root).execute(null, null);
        Stage2Run stage2Run = new Stage2Run(ledger, versions, IDENTITY, new DegenerateOutputConfidenceFloor(null), root);
        List<OccurrenceId> occurrences = paths.stream()
                .map(path -> ledger.occurrenceId(walkId, path).orElseThrow())
                .toList();
        return new Corpus(ledger, stage2Run, occurrences);
    }

    private WalkRecorder walkRecorder(Ledger ledger) {
        return new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource));
    }

    /** One walked corpus and the run stage 2 judges it under. */
    private record Corpus(Ledger ledger, Stage2Run stage2Run, List<OccurrenceId> occurrences) {

        OccurrenceId occurrence(int index) {
            return occurrences.get(index);
        }

        int size() {
            return occurrences.size();
        }
    }
}
