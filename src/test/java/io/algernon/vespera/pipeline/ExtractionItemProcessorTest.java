package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DoclingError;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.FailureCategory;
import io.algernon.vespera.extraction.LanguageDetection;
import io.algernon.vespera.extraction.ScriptedExtractor;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
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
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * What one Docling response earns an occurrence (ADR-070, ADR-071, ADR-139): whether it is a fact
 * about the document, and so a verdict decided here, or a fact about the sidecar, and so no judgement
 * here at all -- the occurrence is set aside instead, and what becomes of it is settled after this
 * class has finished with it. It is faulted by the step's own listener and judged only where that step
 * went on to complete; nothing about that is decided in a processor, which is why every claim below
 * about a service-scope response is a claim that this class threw rather than a claim about a row.
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
 *
 * <p><b>{@code unknown}, and a failure reporting no category at all, join that conditional
 * (ADR-143).</b> Docling answered about this file and could not convert it, which is a verdict unless
 * the same response blames the sidecar in so many words.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("47")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
@Link(name = "ADR-071", url = Adr.DOCLING_INVOCATION_CONTRACT_IS_ONE_SYNC_CALL, type = "adr")
class ExtractionItemProcessorTest {

    /** The engine every response in this class is attributed to; which engine it is does not matter here. */
    private static final ExtractorIdentity IDENTITY = new ExtractorIdentity("docling-serve;scripted");

    /** Stands in for whatever free text Docling put in an {@code errors[]} entry. */
    private static final String ERROR_MESSAGE = "the message Docling reported";

    /**
     * How many timeouts in a row stop being a fact about any one document (ADR-071), read off the
     * counter rather than repeated, so this class cannot disagree with the rule it is claiming.
     */
    private static final int TIMEOUTS_THAT_READ_AS_A_DEAD_SIDECAR = ExtractionTimeoutStreak.CONSECUTIVE_TIMEOUT_COUNT;

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

    /**
     * One more file the converter cannot open than the set-aside documents in a row that stop the step,
     * so a run of them would trip the breaker if any one of them were set aside.
     */
    private static final int UNOPENABLE_FILES_IN_A_ROW = ExtractionCircuitBreaker.CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT + 1;

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

        ExtractionOutcome outcome = processorOver(corpus, docling).process(corpus.occurrence(0));

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
        ExtractionItemProcessor processor = processorOver(corpus, docling);

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
        List<FailureCategory> blamedOnTheService =
                List.of(FailureCategory.CAPACITY, FailureCategory.TARGET_UNAVAILABLE, FailureCategory.INTERNAL);
        Corpus corpus = corpusOf(root, blamedOnTheService.size());
        ScriptedExtractor docling = new ScriptedExtractor();
        blamedOnTheService.forEach(category -> docling.answering(failing(category)));
        ExtractionItemProcessor processor = processorOver(corpus, docling);

        for (int i = 0; i < blamedOnTheService.size(); i++) {
            FailureCategory category = blamedOnTheService.get(i);
            OccurrenceId occurrence = corpus.occurrence(i);
            claim(
                    "a failure reported as " + category.name().toLowerCase(Locale.ROOT)
                            + " says nothing about the document, so the document is set aside for a later run"
                            + " instead of being judged on it",
                    () -> assertThatThrownBy(() -> processor.process(occurrence))
                            .isInstanceOf(ServiceScopeFailureException.class));
        }
    }

    @Test
    @Story("A failure the converter gave no kind for")
    @DisplayName("A file the converter could not open, and gave no kind of failure for, is recorded against that file")
    @Link(name = "ADR-143", url = Adr.AN_UNCATEGORISED_FAILURE_IS_A_VERDICT, type = "adr")
    void verdictsAnUncategorisedFailure(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 1);
        ScriptedExtractor docling = new ScriptedExtractor().answering(failing(FailureCategory.UNKNOWN));

        ExtractionOutcome outcome = processorOver(corpus, docling).process(corpus.occurrence(0));

        claim(
                "the converter answered about this file and could not convert it, so the file is recorded as"
                        + " one extraction could not read -- the same result a file the converter calls broken"
                        + " gets",
                () -> assertThat(outcome.kind()).isEqualTo(VerdictKind.EXTRACTION_FAILED));
        claim(
                "and the reason is the reported kind followed by the converter's own message, composed the"
                        + " way every other reason this step writes is",
                () -> assertThat(outcome.reason()).isEqualTo("unknown: " + ERROR_MESSAGE));
        claim(
                "and the measurement is filed against it, because a response really came back for this file"
                        + " -- " + METRIC_ROWS_PER_JUDGED_RESPONSE + " row recorded for it",
                () -> assertThat(metricRowsFor(corpus.occurrence(0))).isEqualTo(METRIC_ROWS_PER_JUDGED_RESPONSE));
    }

    @Test
    @Story("A failure the converter gave no kind for")
    @DisplayName("A failure reported with no error at all is recorded against the file too")
    @Link(name = "ADR-143", url = Adr.AN_UNCATEGORISED_FAILURE_IS_A_VERDICT, type = "adr")
    void verdictsAFailureThatReportedNoCategoryAtAll(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 1);
        ScriptedExtractor docling = new ScriptedExtractor()
                .answering(new DoclingResponse(ConversionStatus.FAILURE, List.of(), 0d, null, "{}"));

        ExtractionOutcome outcome = processorOver(corpus, docling).process(corpus.occurrence(0));

        claim(
                "a response that says the conversion failed and names no error is still the converter's"
                        + " answer about this file, so the file is recorded as one extraction could not read",
                () -> assertThat(outcome.kind()).isEqualTo(VerdictKind.EXTRACTION_FAILED));
        claim(
                "and its reason says that nothing was categorised, so a removal with no converter message"
                        + " behind it still explains itself",
                () -> assertThat(outcome.reason()).isEqualTo("unknown: no categorized error was reported"));
    }

    @Test
    @Story("A failure the converter gave no kind for")
    @DisplayName("An unexplained error reported alongside a failure the converter blames on itself is read as being about the converter")
    @Link(name = "ADR-143", url = Adr.AN_UNCATEGORISED_FAILURE_IS_A_VERDICT, type = "adr")
    void skipsAnUncategorisedFailureReportedAlongsideAConverterProblem(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 1);
        ScriptedExtractor docling =
                new ScriptedExtractor().answering(failing(FailureCategory.UNKNOWN, FailureCategory.INTERNAL));
        ExtractionItemProcessor processor = processorOver(corpus, docling);

        claim(
                "a response that also says the converter itself is at fault is about the converter, so the"
                        + " file is set aside unjudged, exactly as a refusal reported beside the same fault is",
                () -> assertThatThrownBy(() -> processor.process(corpus.occurrence(0)))
                        .isInstanceOf(ServiceScopeFailureException.class));
    }

    /**
     * The case measured on 2026-09-24: five legacy spreadsheets in one folder, each refused by a
     * converter with no LibreOffice, stopped the whole step. The breaker counts only what this class
     * throws, so a run of verdicts can never trip it.
     */
    @Test
    @Story("A failure the converter gave no kind for")
    @DisplayName("Files the converter cannot open, one after another, are each recorded and nothing is set aside")
    @Link(name = "ADR-143", url = Adr.AN_UNCATEGORISED_FAILURE_IS_A_VERDICT, type = "adr")
    void verdictsEveryUncategorisedFailureInARow(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, UNOPENABLE_FILES_IN_A_ROW);
        ScriptedExtractor docling = new ScriptedExtractor().thenAlwaysAnswering(failing(FailureCategory.UNKNOWN));
        ExtractionItemProcessor processor = processorOver(corpus, docling);

        List<VerdictKind> kinds = new ArrayList<>();
        for (int i = 0; i < corpus.size(); i++) {
            kinds.add(processor.process(corpus.occurrence(i)).kind());
        }

        claim(
                "each of the " + UNOPENABLE_FILES_IN_A_ROW + " files -- more in a row than the "
                        + ExtractionCircuitBreaker.CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT + " set-aside"
                        + " documents that stop the step -- is recorded against itself, so a folder of files"
                        + " the converter cannot open costs those files and nothing else",
                () -> assertThat(kinds).hasSize(UNOPENABLE_FILES_IN_A_ROW).containsOnly(VerdictKind.EXTRACTION_FAILED));
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
        ExtractionItemProcessor processor = processorOver(corpus, docling);

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
        ExtractionItemProcessor processor = processorOver(corpus, docling);

        claim(
                "a response that reports a refusal and, in the same breath, that the converter had no room"
                        + " to work is a response about the converter's own state, so the refusal cannot be"
                        + " taken as a fact about this document and the document is set aside unjudged",
                () -> assertThatThrownBy(() -> processor.process(corpus.occurrence(0)))
                        .isInstanceOf(ServiceScopeFailureException.class));
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

        ExtractionOutcome outcome = processorOver(corpus, docling).process(corpus.occurrence(0));

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

        ExtractionOutcome outcome = processorOver(corpus, docling).process(corpus.occurrence(0));

        claim(
                "a document that partly converted is judged on what it produced, not on the fact that"
                        + " something failed inside it -- so this step decides nothing about it and leaves it"
                        + " to whatever measures the text",
                () -> assertThat(outcome).isNull());
    }

    @Test
    @Story("A conversion that succeeded")
    @DisplayName("A converted document's text reaches the shingle table, and nothing chunks it")
    @Issue("103")
    @Link(name = "ADR-091", url = Adr.THERE_IS_NO_TOKENIZER, type = "adr")
    void aConvertedDocumentIsShingledAndNotChunked(@TempDir Path root) throws Exception {
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
                "nothing was chunked (ADR-091): a chunk cut under a budget no embedding model has"
                        + " been named for is work guaranteed to be discarded, and stage 5 re-chunks"
                        + " from the extraction cache once a model exists",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chunk_cache", Integer.class))
                        .isZero());
        claim(
                "while the shingle table carries rows against this occurrence's own run, proving the"
                        + " shingler is called with the same run extractionRun minted",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM shingle WHERE occurrence_id = ? AND run_id = ?",
                                Integer.class,
                                corpus.occurrence(0).value(),
                                corpus.extractionRunId().value()))
                        .isPositive());
    }

    /**
     * #275: every spreadsheet in the GesPOS corpus earned degenerate-output, 2.4 million characters of
     * cells among them, because only {@code texts[]} was read.
     */
    @Test
    @Story("Tier 1 — the hard zero-content floor")
    @DisplayName("A spreadsheet, which converts to a table and no text items, clears the floor and is shingled")
    @Issue("275")
    @Link(name = "ADR-145", url = Adr.TABLE_CELLS_ARE_EXTRACTED_TEXT, type = "adr")
    void aSpreadsheetClearsTheFloor(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 1);
        ScriptedExtractor docling = new ScriptedExtractor()
                .answering(new DoclingResponse(
                        ConversionStatus.SUCCESS,
                        List.of(),
                        0d,
                        null,
                        "{\"document\":{\"json_content\":{\"body\":{\"children\":[{\"$ref\":\"#/tables/0\"}]},"
                                + "\"texts\":[],\"tables\":[{\"children\":[],\"data\":{\"table_cells\":["
                                + "{\"text\":\"Merchant\",\"start_row_offset_idx\":0,\"start_col_offset_idx\":0},"
                                + "{\"text\":\"Terminal\",\"start_row_offset_idx\":0,\"start_col_offset_idx\":1},"
                                + "{\"text\":\"ACME Srl\",\"start_row_offset_idx\":1,\"start_col_offset_idx\":0},"
                                + "{\"text\":\"TID-273273\",\"start_row_offset_idx\":1,\"start_col_offset_idx\":1}"
                                + "]}}]}}}"));

        ExtractionOutcome outcome = processorOver(corpus, docling).process(corpus.occurrence(0));

        claim(
                "a document whose whole content is a table is judged on its cells, and they are text, so it"
                        + " earns no verdict here and goes on as a survivor",
                () -> assertThat(outcome).isNull());
        claim(
                "and its cells reached the shingle table, so the stages after this one compare it by the"
                        + " same text the floor measured",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM shingle WHERE occurrence_id = ?",
                                Integer.class,
                                corpus.occurrence(0).value()))
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

        ExtractionOutcome outcome = processorOver(corpus, docling).process(corpus.occurrence(0));

        claim(
                "a clean conversion carrying no extracted text at all trips tier 1, so it is condemned"
                        + " here rather than reaching later stages as if it were real content",
                () -> assertThat(outcome.kind()).isEqualTo(VerdictKind.DEGENERATE_OUTPUT));
    }

    /**
     * Two consecutive counts run through this step and only one of them is this test's: the
     * consecutive-timeout count ADR-071 fixes at 3, which is what flips the reading here. The
     * circuit breaker keeps a separate count, of service-scope skips, and the skip this flip produces
     * is that breaker's first one.
     *
     * <p>What the flip throws carries the converter's message alone (ADR-139 sections 1 and 3), and
     * the timeout count is carried nowhere else either: nothing logs it, so once the streak trips its
     * exact length is gone. That is accepted rather than overlooked. ADR-071 fixes the flip point at a
     * constant, so "at least the third consecutive timeout" follows from the category by itself, and
     * every timeout before the flip left an extraction-failed row of its own; what is lost is the
     * difference between a third consecutive timeout and a fifth, which is a measurement of our own
     * pass rather than of any document, and ADR-093 keeps those out of rows.
     */
    @Test
    @Story("A converter that stops answering")
    @DisplayName("Silence about one document is a fact about that document; silence about several in a row is a fact about the converter")
    @Link(name = "ADR-139", url = Adr.A_REFUSED_CONVERSION_LEAVES_A_FAULT_ROW, type = "adr")
    void flipsAConsecutiveRunOfUnansweredCallsToTheConverter(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, TIMEOUTS_THAT_READ_AS_A_DEAD_SIDECAR);
        // The two readings ADR-071 folds together: a response reporting its own timeout, and a call
        // that came back with nothing at all. Mixed on purpose, because they share the one counter.
        ScriptedExtractor docling = new ScriptedExtractor()
                .answering(failing(FailureCategory.TIMEOUT))
                .timingOut()
                .answering(failing(FailureCategory.TIMEOUT));
        ExtractionItemProcessor processor = processorOver(corpus, docling);

        ExtractionOutcome first = processor.process(corpus.occurrence(0));
        ExtractionOutcome second = processor.process(corpus.occurrence(1));
        // Caught once rather than thrown again inside a second claim: the counter this test is about
        // advances on every call, so a repeat call would be a fourth timeout rather than this one.
        ServiceScopeFailureException setAside = catchThrowableOfType(
                ServiceScopeFailureException.class,
                () -> processor.process(corpus.occurrence(TIMEOUTS_THAT_READ_AS_A_DEAD_SIDECAR - 1)));

        claim(
                "a converter that ran out of time on one document says that document was too much for the"
                        + " time it is given, and the document is recorded as one extraction could not read",
                () -> assertThat(first.kind()).isEqualTo(VerdictKind.EXTRACTION_FAILED));
        claim(
                "and its reason names the category once and then the message the converter reported --"
                        + " not the category twice. Every reason this class writes is composed the same way,"
                        + " here and where a refusal is turned into a judgement later, so a category written"
                        + " into the message and then prefixed again is the shape that tells an operator the"
                        + " two were composed by different hands",
                () -> assertThat(first.reason()).isEqualTo("timeout: " + ERROR_MESSAGE));
        claim(
                "so does a call that came back with nothing at all, which is the same reading of the same"
                        + " situation and counts against the same run",
                () -> assertThat(second.kind()).isEqualTo(VerdictKind.EXTRACTION_FAILED));
        claim(
                "but once " + TIMEOUTS_THAT_READ_AS_A_DEAD_SIDECAR + " land in a row, that is a statement"
                        + " about the converter rather than about any one document, so this document is set"
                        + " aside unjudged instead",
                () -> assertThat(setAside).isNotNull());
        claim(
                "and what it sets aside carries the converter's own message and nothing else -- not that"
                        + " message with this pass's own count of consecutive timeouts spliced onto it. The"
                        + " count is a measurement of how this pass was going, not anything the converter"
                        + " said about the file, and this message is what the end of the step stores against"
                        + " the occurrence and composes its reason from, so whatever is added here reaches"
                        + " an operator as the converter's own words about their document",
                () -> assertThat(setAside.detail()).isEqualTo(ERROR_MESSAGE));
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
        ExtractionItemProcessor processor = processorOver(corpus, docling);

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
     * ADR-094 narrows plain text by extension only for {@code .md}, {@code .html}, {@code .csv} and
     * {@code .adoc}, so the {@code .txt} fixture below is plain text with no subtype at all.
     */
    @Test
    @Story("The format sent is the one stage 1 read off the bytes")
    @DisplayName("The occurrence is converted as what stage 1 found it to be, not as what its path says")
    @Link(name = "ADR-100", url = Adr.DOCLING_READS_THE_BYTES_TOO, type = "adr")
    void convertsAsTheFormatStageOneRecorded(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 1);
        ScriptedExtractor docling = new ScriptedExtractor().answering(converted());

        processorOver(corpus, docling).process(corpus.occurrence(0));

        claim(
                "what reaches the converter is the format stage 1 read off the leading bytes, looked up"
                        + " under the stage-1 run this stage names upstream -- prose in a .txt file is plain"
                        + " text, and the path plays no part in saying so",
                () -> assertThat(docling.formatsAsked()).containsExactly(DetectedFormat.PLAIN_TEXT));
        claim(
                "and it carries no subtype, because .txt is not one of the extensions plain text is"
                        + " narrowed by -- an absent subtype is a legitimate answer, not a missing one",
                () -> assertThat(docling.subtypesAsked()).containsExactly(Optional.empty()));
    }

    /**
     * The second occurrence's verdict is ADR-070's tier-1 degeneracy floor — the scripted response
     * carries no text — which is what makes it evidence that the occurrence was judged rather than
     * skipped.
     */
    @Test
    @Story("The format sent is the one stage 1 read off the bytes")
    @DisplayName("An occurrence whose format was never recorded fails on its own, and the pass carries on")
    @Link(name = "ADR-100", url = Adr.DOCLING_READS_THE_BYTES_TOO, type = "adr")
    @Link(name = "ADR-057", url = Adr.VERDICT_VOCABULARY_IS_EIGHT_VALUES, type = "adr")
    void recordsAMissingFormatRowAgainstTheOccurrenceAndCarriesOn(@TempDir Path root) throws Exception {
        Corpus corpus = corpusOf(root, 2);
        jdbcTemplate.update(
                "DELETE FROM detected_format WHERE occurrence_id = ?",
                corpus.occurrence(0).value());
        ScriptedExtractor docling = new ScriptedExtractor().thenAlwaysAnswering(converted());
        ExtractionItemProcessor processor = processorOver(corpus, docling);

        ExtractionOutcome unreadable = processor.process(corpus.occurrence(0));
        ExtractionOutcome next = processor.process(corpus.occurrence(1));

        claim(
                "detection runs on every occurrence that survives stage 1's floor, so a format row that"
                        + " is not there is a broken invariant rather than a fact about the file -- and it is"
                        + " recorded as a failure of this occurrence",
                () -> assertThat(unreadable.kind()).isEqualTo(VerdictKind.EXTRACTION_FAILED));
        claim(
                "the reason names the run the row was sought under, because this is the one"
                        + " extraction-failed verdict in the system that no Docling response produced: in the"
                        + " ledger it is otherwise indistinguishable from a conversion that really failed",
                () -> assertThat(unreadable.reason())
                        .contains(corpus.byteLevelReductionRunId().value())
                        .contains(String.valueOf(corpus.occurrence(0).value())));
        claim(
                "and nothing was converted for it: a document whose format could not be read is not sent"
                        + " to Docling on a guess, so only the occurrence that kept its row was converted",
                () -> assertThat(docling.conversions()).isEqualTo(1));
        claim(
                "while the very next occurrence is put to the converter and judged on what came back --"
                        + " one unreadable row costs one occurrence, and a pass over hundreds of gigabytes"
                        + " does not abort on it",
                () -> assertThat(next.kind()).isEqualTo(VerdictKind.DEGENERATE_OUTPUT));
        claim(
                "and its verdict is the response's, not the missing row's: the scripted answer carried"
                        + " no text at all, which is the hard floor over a converted document, so this"
                        + " occurrence reached a judgement the first one never got to",
                () -> assertThat(next.reason()).doesNotContain("no detected format"));
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
        List<DoclingError> errors = Arrays.stream(categories).map(ExtractionItemProcessorTest::error).toList();
        return new DoclingResponse(ConversionStatus.FAILURE, errors, 0d, null, "{}");
    }

    private static DoclingError error(FailureCategory category) {
        return new DoclingError("document_backend", "docling", ERROR_MESSAGE, category, null);
    }

    /**
     * The processor as the step builds it: over the real ledger, over the run stage 2 mints for this
     * walk, and over the scripted converter this test wants it to read.
     */
    private ExtractionItemProcessor processorOver(Corpus corpus, ScriptedExtractor docling) {
        return new ExtractionItemProcessor(
                corpus.ledger(),
                new ContentIdentity(jdbcTemplate),
                new DetectedFormats(jdbcTemplate),
                docling,
                IDENTITY,
                new ExtractionTimeoutStreak(),
                corpus.stage2(),
                new ExtractionMetrics(jdbcTemplate, new LanguageDetection()),
                new DegenerateOutputConfidenceFloor(null),
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
        ChunkContext step = InvocationRecordFixture.aStepOfAFreshInvocation();
        new ByteLevelReductionTasklet(ledger, new ContentIdentity(jdbcTemplate), new DetectedFormats(jdbcTemplate), versions, root, root.resolveSibling("stage1-working")).execute(null, step);
        ExecutionContext invocation = InvocationRecordFixture.recordOf(step);
        ExtractionRun extractionRun = new ExtractionRun(
                ledger, versions, IDENTITY, new DegenerateOutputConfidenceFloor(null), root, invocation);
        List<OccurrenceId> occurrences = paths.stream()
                .map(path -> ledger.occurrenceId(walkId, path).orElseThrow())
                .toList();
        return new Corpus(ledger, new InvocationRuns(invocation), extractionRun, occurrences);
    }

    private WalkRecorder walkRecorder(Ledger ledger) {
        return new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource));
    }

    /**
     * One walked corpus and the run stage 2 judges it under.
     *
     * <p>The claims read the runs through {@link #extractionRunId()} and {@link
     * #byteLevelReductionRunId()}, which ask the invocation's own record, as every later stage does
     * (ADR-154). {@code stage2} is handed to the processor and read nowhere else. So the class that
     * mints stage 2's run is named only by {@link #corpusOf}, {@link #processorOver} and this record's
     * third component, and ADR-157's folding of the run classes changes those and no claim.
     */
    private record Corpus(
            Ledger ledger, InvocationRuns invocation, ExtractionRun stage2, List<OccurrenceId> occurrences) {

        RunId extractionRunId() {
            return invocation.runOf("extraction").orElseThrow();
        }

        RunId byteLevelReductionRunId() {
            return invocation.runOf("byte-level-reduction").orElseThrow();
        }

        OccurrenceId occurrence(int index) {
            return occurrences.get(index);
        }

        int size() {
            return occurrences.size();
        }
    }
}
