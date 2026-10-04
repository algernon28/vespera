package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * What one answer from the converter earns a file occurrence (ADR-070, ADR-071, ADR-139, ADR-143),
 * decided in {@code extraction} since ADR-189: a verdict decided here, a conversion left for the floor to
 * judge, or an occurrence set aside unjudged because the answer is about the converter.
 *
 * <p><b>Ported from {@code pipeline}'s {@code ExtractionItemProcessorTest}</b> (#407). That class drove
 * the whole processor over a walked corpus and stage 1's run, because the rules lived inside it. They
 * live in {@link OccurrenceJudge} now, so each of its eighteen tests is carried here as a claim about the
 * judge, with the same scripted answers and the same expectations, except where a claim is about what
 * {@code pipeline} still does: writing the shingles, and choosing what to send the converter. Those stay
 * in {@code pipeline}'s {@code ExtractionItemProcessorTest}, and each test below says where its other
 * half went. A claim that the processor threw {@code ServiceScopeFailureException} is a claim here that
 * the judge set the occurrence aside, because the throwing is {@code pipeline}'s.
 *
 * <p><b>{@code policy} and {@code source_unavailable} are the conditional pair.</b> ADR-070 resolves them
 * "per occurrence", and issue #47's resolution comment reads that as a per-response conditional: the
 * document's, unless a genuine service-scope category ({@code capacity}, {@code target_unavailable},
 * {@code internal} -- not {@code unknown}) sits anywhere else in the same response, in which case the
 * whole response is about the converter. {@code unknown}, and a failure reporting no category at all,
 * join that conditional (ADR-143).
 *
 * <p>The ledger and the metric table are real, over the test database, because several claims are that
 * a measurement was or was not filed against the occurrence.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("407")
@Link(name = "ADR-189", url = Adr.STAGE_2_RULES_LIVE_IN_EXTRACTION, type = "adr")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
@Link(name = "ADR-071", url = Adr.DOCLING_INVOCATION_CONTRACT_IS_ONE_SYNC_CALL, type = "adr")
class OccurrenceJudgeTest {

    /** Stands in for whatever free text Docling put in an {@code errors[]} entry. */
    private static final String ERROR_MESSAGE = "the message Docling reported";

    /** What the client says when a call brings no answer in time. */
    private static final String NO_ANSWER_IN_TIME = "no response from docling-serve within the call timeout";

    /** How many timeouts in a row are the converter's rather than the file's, read off the counts. */
    private static final int TIMEOUTS_THAT_READ_AS_A_DEAD_SIDECAR = FailuresInARow.CONSECUTIVE_TIMEOUT_COUNT;

    /** The timeouts before that one, each still a fact about its own file. */
    private static final int TIMEOUTS_STILL_READ_AS_THE_FILES = TIMEOUTS_THAT_READ_AS_A_DEAD_SIDECAR - 1;

    /** ADR-070's two document-blamed failure kinds, plus one answer reporting it skipped the document. */
    private static final int RESPONSES_BLAMED_ON_THE_DOCUMENT = 3;

    /** One measurement row per answer a document really came back for; a set-aside answer earns none. */
    private static final int METRIC_ROWS_PER_JUDGED_RESPONSE = 1;

    /** One more file the converter cannot open than the set-aside files in a row that stop the stage. */
    private static final int UNOPENABLE_FILES_IN_A_ROW = FailuresInARow.CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT + 1;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---- Ported from ExtractionItemProcessorTest, in its order ----

    @Test
    @Story("A failure that is a fact about the document")
    @DisplayName("A failure the converter blames on the document itself is recorded against that document")
    void verdictsADocumentScopedFailure() {
        Judging judging = judging(1);

        OccurrenceDecision decision = judging.answer(0, failing(FailureCategory.BACKEND_FAILURE));

        claim(
                "the document is recorded as one extraction could not read, which is the only verdict this"
                        + " step writes",
                () -> assertThat(decision).isInstanceOf(OccurrenceDecision.Failed.class));
        claim(
                "and the row says which kind of failure it was and what the converter said about it, so the"
                        + " reason stands on its own without the response it came from",
                () -> assertThat(reasonOf(decision)).contains("backend_failure").contains(ERROR_MESSAGE));
    }

    @Test
    @Story("A failure that is a fact about the document")
    @DisplayName("Both document-blamed failure kinds are recorded, and a converter that reports it skipped the document counts too")
    void verdictsEveryDocumentScopedCategory() {
        Judging judging = judging(RESPONSES_BLAMED_ON_THE_DOCUMENT);
        List<DoclingResponse> answers = List.of(
                failing(FailureCategory.BACKEND_FAILURE),
                failing(FailureCategory.INFERENCE_FAILURE),
                new DoclingResponse(
                        ConversionStatus.SKIPPED, List.of(error(FailureCategory.BACKEND_FAILURE)), 0d, null, "{}"));

        List<Class<?>> decisions = new ArrayList<>();
        for (int i = 0; i < answers.size(); i++) {
            decisions.add(judging.answer(i, answers.get(i)).getClass());
        }

        claim(
                "each of the " + RESPONSES_BLAMED_ON_THE_DOCUMENT + " responses is recorded against its own"
                        + " document: the two failure kinds the converter blames on the document, and one"
                        + " document it reported skipping for the same reason",
                () -> assertThat(decisions)
                        .containsExactly(
                                OccurrenceDecision.Failed.class,
                                OccurrenceDecision.Failed.class,
                                OccurrenceDecision.Failed.class));
    }

    @Test
    @Story("A failure that is a fact about the service")
    @DisplayName("A failure the converter blames on itself leaves the document unjudged rather than condemned")
    void setsAsideEveryServiceScopedCategory() {
        List<FailureCategory> blamedOnTheService =
                List.of(FailureCategory.CAPACITY, FailureCategory.TARGET_UNAVAILABLE, FailureCategory.INTERNAL);
        Judging judging = judging(blamedOnTheService.size());

        for (int i = 0; i < blamedOnTheService.size(); i++) {
            FailureCategory category = blamedOnTheService.get(i);
            OccurrenceDecision decision = judging.answer(i, failing(category));
            claim(
                    "a failure reported as " + wire(category) + " says nothing about the document, so the"
                            + " document is set aside for a later run instead of being judged on it, under the"
                            + " category the converter reported and its own message",
                    () -> assertThat(decision).isEqualTo(new OccurrenceDecision.SetAside(wire(category), ERROR_MESSAGE)));
        }
    }

    @Test
    @Story("A failure the converter gave no kind for")
    @DisplayName("A file the converter could not open, and gave no kind of failure for, is recorded against that file")
    @Link(name = "ADR-143", url = Adr.AN_UNCATEGORISED_FAILURE_IS_A_VERDICT, type = "adr")
    void verdictsAnUncategorisedFailure() {
        Judging judging = judging(1);

        OccurrenceDecision decision = judging.answer(0, failing(FailureCategory.UNKNOWN));

        claim(
                "the converter answered about this file and could not convert it, so the file is recorded as"
                        + " one extraction could not read -- the same result a file the converter calls broken"
                        + " gets",
                () -> assertThat(decision).isInstanceOf(OccurrenceDecision.Failed.class));
        claim(
                "and the reason is the reported kind followed by the converter's own message, composed the"
                        + " way every other reason this step writes is",
                () -> assertThat(reasonOf(decision)).isEqualTo("unknown: " + ERROR_MESSAGE));
        claim(
                "and the measurement is filed against it, because a response really came back for this file"
                        + " -- " + METRIC_ROWS_PER_JUDGED_RESPONSE + " row recorded for it",
                () -> assertThat(judging.metricRowsFor(0)).isEqualTo(METRIC_ROWS_PER_JUDGED_RESPONSE));
    }

    @Test
    @Story("A failure the converter gave no kind for")
    @DisplayName("A failure reported with no error at all is recorded against the file too")
    @Link(name = "ADR-143", url = Adr.AN_UNCATEGORISED_FAILURE_IS_A_VERDICT, type = "adr")
    void verdictsAFailureThatReportedNoCategoryAtAll() {
        Judging judging = judging(1);

        OccurrenceDecision decision =
                judging.answer(0, new DoclingResponse(ConversionStatus.FAILURE, List.of(), 0d, null, "{}"));

        claim(
                "a response that says the conversion failed and names no error is still the converter's"
                        + " answer about this file, so the file is recorded as one extraction could not read",
                () -> assertThat(decision).isInstanceOf(OccurrenceDecision.Failed.class));
        claim(
                "and its reason says that nothing was categorised, so a removal with no converter message"
                        + " behind it still explains itself",
                () -> assertThat(reasonOf(decision)).isEqualTo("unknown: no categorized error was reported"));
    }

    @Test
    @Story("A failure the converter gave no kind for")
    @DisplayName("An unexplained error reported alongside a failure the converter blames on itself is read as being about the converter")
    @Link(name = "ADR-143", url = Adr.AN_UNCATEGORISED_FAILURE_IS_A_VERDICT, type = "adr")
    void setsAsideAnUncategorisedFailureReportedAlongsideAConverterProblem() {
        Judging judging = judging(1);

        OccurrenceDecision decision = judging.answer(0, failing(FailureCategory.UNKNOWN, FailureCategory.INTERNAL));

        claim(
                "a response that also says the converter itself is at fault is about the converter, so the"
                        + " file is set aside unjudged, exactly as a refusal reported beside the same fault is",
                () -> assertThat(decision).isInstanceOf(OccurrenceDecision.SetAside.class));
    }

    /**
     * The case measured on 2026-09-24: five legacy spreadsheets in one folder, each refused by a
     * converter with no LibreOffice, stopped the whole step. Only a set-aside occurrence is counted
     * toward the stop, so a run of verdicts can never reach it.
     */
    @Test
    @Story("A failure the converter gave no kind for")
    @DisplayName("Files the converter cannot open, one after another, are each recorded and nothing is set aside")
    @Link(name = "ADR-143", url = Adr.AN_UNCATEGORISED_FAILURE_IS_A_VERDICT, type = "adr")
    void verdictsEveryUncategorisedFailureInARow() {
        Judging judging = judging(UNOPENABLE_FILES_IN_A_ROW);

        List<Class<?>> decisions = new ArrayList<>();
        for (int i = 0; i < UNOPENABLE_FILES_IN_A_ROW; i++) {
            decisions.add(judging.answer(i, failing(FailureCategory.UNKNOWN)).getClass());
        }

        claim(
                "each of the " + UNOPENABLE_FILES_IN_A_ROW + " files -- more in a row than the "
                        + FailuresInARow.CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT + " set-aside"
                        + " documents that stop the step -- is recorded against itself, so a folder of files"
                        + " the converter cannot open costs those files and nothing else",
                () -> assertThat(decisions)
                        .hasSize(UNOPENABLE_FILES_IN_A_ROW)
                        .containsOnly(OccurrenceDecision.Failed.class));
    }

    @Test
    @Story("A failure whose scope depends on the rest of the response")
    @DisplayName("A refusal, or an unreadable source, reported on its own is recorded against the document")
    void verdictsARefusalReportedOnItsOwn() {
        List<FailureCategory> aboutThisFileUnlessTheResponseSaysOtherwise =
                List.of(FailureCategory.POLICY, FailureCategory.SOURCE_UNAVAILABLE);
        Judging judging = judging(aboutThisFileUnlessTheResponseSaysOtherwise.size());

        for (int i = 0; i < aboutThisFileUnlessTheResponseSaysOtherwise.size(); i++) {
            FailureCategory category = aboutThisFileUnlessTheResponseSaysOtherwise.get(i);
            int index = i;
            OccurrenceDecision decision = judging.answer(i, failing(category));
            claim(
                    "a converter that reported only " + wire(category) + " is saying something about the file it"
                            + " was handed -- it refused this one, or could not read this one -- so the document"
                            + " is recorded as one extraction could not read",
                    () -> assertThat(decision).isInstanceOf(OccurrenceDecision.Failed.class));
            claim(
                    "and the measurement is filed against it as well, because a reading about the document is"
                            + " a reading of a response the document really came back for -- "
                            + METRIC_ROWS_PER_JUDGED_RESPONSE + " row recorded for it",
                    () -> assertThat(judging.metricRowsFor(index)).isEqualTo(METRIC_ROWS_PER_JUDGED_RESPONSE));
        }
    }

    @Test
    @Story("A failure whose scope depends on the rest of the response")
    @DisplayName("A refusal reported alongside a failure the converter blames on itself is read as being about the converter")
    void setsAsideARefusalReportedAlongsideAConverterProblem() {
        Judging judging = judging(1);

        OccurrenceDecision decision = judging.answer(0, failing(FailureCategory.POLICY, FailureCategory.CAPACITY));

        claim(
                "a response that reports a refusal and, in the same breath, that the converter had no room"
                        + " to work is a response about the converter's own state, so the refusal cannot be"
                        + " taken as a fact about this document and the document is set aside unjudged",
                () -> assertThat(decision).isEqualTo(new OccurrenceDecision.SetAside("capacity", ERROR_MESSAGE)));
        claim(
                "and no measurement is filed against it either, so a later run finds the document exactly"
                        + " as untouched as it was before -- zero rows recorded for it",
                () -> assertThat(judging.metricRowsFor(0)).isZero());
    }

    @Test
    @Story("A failure whose scope depends on the rest of the response")
    @DisplayName("A refusal reported alongside an unexplained error is still recorded against the document")
    void verdictsARefusalReportedAlongsideAnUnexplainedError() {
        Judging judging = judging(1);

        OccurrenceDecision decision = judging.answer(0, failing(FailureCategory.POLICY, FailureCategory.UNKNOWN));

        claim(
                "an error the converter gave no kind for is no evidence about anything, so it cannot turn a"
                        + " refusal of this file into a statement about the converter: the document is still"
                        + " recorded as one extraction could not read",
                () -> assertThat(decision).isInstanceOf(OccurrenceDecision.Failed.class));
        claim(
                "and the row still names the refusal and what the converter said about it, rather than the"
                        + " unexplained error that sat beside it",
                () -> assertThat(reasonOf(decision)).contains("policy").contains(ERROR_MESSAGE));
    }

    @Test
    @Story("A conversion that partly worked")
    @DisplayName("A document some of whose pages failed is not condemned for that alone")
    void doesNotJudgeAPartlyConvertedDocumentOnItsFailuresAlone() {
        Judging judging = judging(1);

        OccurrenceDecision decision = judging.answer(
                0,
                new DoclingResponse(
                        ConversionStatus.PARTIAL_SUCCESS,
                        List.of(error(FailureCategory.BACKEND_FAILURE), error(FailureCategory.INFERENCE_FAILURE)),
                        0d,
                        null,
                        "{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"the pages that did convert carried"
                                + " real content\"}]}}}"));

        claim(
                "a document that partly converted is judged on what it produced, not on the fact that"
                        + " something failed inside it -- so it is a conversion, and with real text in it the"
                        + " floor finds nothing to condemn",
                () -> assertThat(decision).isEqualTo(new OccurrenceDecision.Converted(Optional.empty())));
    }

    /** The shingle half of this test stays in {@code pipeline}'s {@code ExtractionItemProcessorTest}. */
    @Test
    @Story("A conversion that succeeded")
    @DisplayName("A converted document is not chunked")
    @Issue("103")
    @Link(name = "ADR-091", url = Adr.THERE_IS_NO_TOKENIZER, type = "adr")
    void aConvertedDocumentIsNotChunked() {
        Judging judging = judging(1);

        OccurrenceDecision decision = judging.answer(
                0,
                new DoclingResponse(
                        ConversionStatus.SUCCESS,
                        List.of(),
                        0d,
                        null,
                        "{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"real content the chunker and"
                                + " the shingler both read\"}]}}}"));

        claim(
                "the answer is a conversion, which is what tells the step to shingle its text",
                () -> assertThat(decision).isInstanceOf(OccurrenceDecision.Converted.class));
        claim(
                "and nothing was chunked: a chunk cut under a budget no embedding model has been named for is"
                        + " work guaranteed to be discarded, and stage 5 re-chunks from the extraction cache once a"
                        + " model exists",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chunk_cache", Integer.class))
                        .isZero());
    }

    /**
     * #275: every spreadsheet in the GesPOS corpus earned degenerate-output, 2.4 million characters of
     * cells among them, because only {@code texts[]} was read. The shingle half stays in {@code
     * pipeline}'s {@code ExtractionItemProcessorTest}.
     */
    @Test
    @Story("Tier 1 — the hard zero-content floor")
    @DisplayName("A spreadsheet, which converts to a table and no text items, clears the floor")
    @Issue("275")
    @Link(name = "ADR-145", url = Adr.TABLE_CELLS_ARE_EXTRACTED_TEXT, type = "adr")
    void aSpreadsheetClearsTheFloor() {
        Judging judging = judging(1);

        OccurrenceDecision decision = judging.answer(
                0,
                new DoclingResponse(
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

        claim(
                "a document whose whole content is a table is judged on its cells, and they are text, so it"
                        + " earns no verdict here and goes on as a survivor",
                () -> assertThat(decision).isEqualTo(new OccurrenceDecision.Converted(Optional.empty())));
    }

    @Test
    @Story("Tier 1 — the hard zero-content floor")
    @DisplayName("A document that converts to no usable text earns a degenerate-output verdict")
    void aConversionWithNoUsableTextEarnsADegenerateOutputVerdict() {
        Judging judging = judging(1);

        OccurrenceDecision decision = judging.answer(
                0,
                new DoclingResponse(
                        ConversionStatus.SUCCESS, List.of(), 0d, null, "{\"document\":{\"json_content\":{\"texts\":[]}}}"));

        claim(
                "a clean conversion carrying no extracted text at all trips tier 1, so it is condemned"
                        + " here rather than reaching later stages as if it were real content",
                () -> assertThat(((OccurrenceDecision.Converted) decision).degenerateReason()).isPresent());
    }

    /**
     * The timeout count is not carried in what the flip sets aside (ADR-139 sections 1 and 3): the
     * detail is the converter's message alone, and nothing logs the count, so once the row flips its
     * exact length is gone. That is accepted rather than overlooked: ADR-071 fixes the flip at a
     * constant, and every timeout before it left an extraction-failed verdict of its own.
     */
    @Test
    @Story("A converter that stops answering")
    @DisplayName("Silence about one document is a fact about that document; silence about several in a row is a fact about the converter")
    @Link(name = "ADR-139", url = Adr.A_REFUSED_CONVERSION_LEAVES_A_FAULT_ROW, type = "adr")
    void flipsAConsecutiveRunOfUnansweredCallsToTheConverter() {
        Judging judging = judging(TIMEOUTS_THAT_READ_AS_A_DEAD_SIDECAR);

        // The two readings ADR-071 folds together, mixed on purpose because they share one count.
        OccurrenceDecision first = judging.answer(0, failing(FailureCategory.TIMEOUT));
        OccurrenceDecision second = judging.judge.timedOut(judging.occurrence(1), NO_ANSWER_IN_TIME);
        OccurrenceDecision third = judging.answer(TIMEOUTS_THAT_READ_AS_A_DEAD_SIDECAR - 1, failing(FailureCategory.TIMEOUT));

        claim(
                "a converter that ran out of time on one document says that document was too much for the"
                        + " time it is given, and the document is recorded as one extraction could not read",
                () -> assertThat(first).isInstanceOf(OccurrenceDecision.Failed.class));
        claim(
                "and its reason names the category once and then the message the converter reported --"
                        + " not the category twice, which is the shape that tells an operator two hands composed it",
                () -> assertThat(reasonOf(first)).isEqualTo("timeout: " + ERROR_MESSAGE));
        claim(
                "so does a call that came back with nothing at all, which is the same reading of the same"
                        + " situation and counts against the same run",
                () -> assertThat(second).isEqualTo(new OccurrenceDecision.Failed("timeout: " + NO_ANSWER_IN_TIME)));
        claim(
                "but once " + TIMEOUTS_THAT_READ_AS_A_DEAD_SIDECAR + " land in a row, that is a statement"
                        + " about the converter rather than about any one document, so this document is set"
                        + " aside unjudged instead, carrying the converter's own message and nothing else --"
                        + " not that message with this pass's count of timeouts spliced onto it",
                () -> assertThat(third).isEqualTo(new OccurrenceDecision.SetAside("timeout", ERROR_MESSAGE)));
    }

    @Test
    @Story("A converter that stops answering")
    @DisplayName("A document that converts between two slow ones means the slow ones were not a pattern")
    void oneConversionEndsTheRun() {
        Judging judging = judging(2 * TIMEOUTS_STILL_READ_AS_THE_FILES + 1);
        List<OccurrenceDecision> decisions = new ArrayList<>();
        int next = 0;
        for (int i = 0; i < TIMEOUTS_STILL_READ_AS_THE_FILES; i++) {
            decisions.add(judging.judge.timedOut(judging.occurrence(next++), NO_ANSWER_IN_TIME));
        }
        decisions.add(judging.answer(next++, converted()));
        for (int i = 0; i < TIMEOUTS_STILL_READ_AS_THE_FILES; i++) {
            decisions.add(judging.judge.timedOut(judging.occurrence(next++), NO_ANSWER_IN_TIME));
        }

        claim(
                "with " + TIMEOUTS_STILL_READ_AS_THE_FILES + " slow documents, one that converted, and then "
                        + TIMEOUTS_STILL_READ_AS_THE_FILES + " more slow ones, nothing is ever set aside: a document"
                        + " that converted in the middle proves the converter was answering, so the run before it"
                        + " stopped counting",
                () -> assertThat(decisions)
                        .hasSize(2 * TIMEOUTS_STILL_READ_AS_THE_FILES + 1)
                        .noneMatch(decision -> decision instanceof OccurrenceDecision.SetAside));
    }

    /**
     * Ported from {@code recordsAMissingFormatRowAgainstTheOccurrenceAndCarriesOn}. Its other half --
     * that nothing is sent to the converter for the occurrence and the next one is judged -- stays in
     * {@code pipeline}'s {@code ExtractionItemProcessorTest}, with {@code convertsAsTheFormatStageOneRecorded},
     * because what is sent is {@code pipeline}'s.
     */
    @Test
    @Story("The format sent is the one stage 1 read off the bytes")
    @DisplayName("An occurrence whose format was never recorded fails on its own, and its reason names where the format was sought")
    @Link(name = "ADR-100", url = Adr.DOCLING_READS_THE_BYTES_TOO, type = "adr")
    @Link(name = "ADR-057", url = Adr.VERDICT_VOCABULARY_IS_EIGHT_VALUES, type = "adr")
    void recordsAMissingFormatAgainstTheOccurrence() {
        Judging judging = judging(1);
        RunId stageOneRun = judging.occurrences.run();

        OccurrenceDecision decision = judging.judge.noDetectedFormat(judging.occurrence(0), stageOneRun);

        claim(
                "a format that is not recorded is a broken invariant rather than a fact about the file, and it"
                        + " is recorded as a failure of this occurrence",
                () -> assertThat(decision).isInstanceOf(OccurrenceDecision.Failed.class));
        claim(
                "the reason names the occurrence and the run the format was sought under, because this is the"
                        + " one such verdict no converter answer produced: in the ledger it is otherwise"
                        + " indistinguishable from a conversion that really failed",
                () -> assertThat(reasonOf(decision))
                        .isEqualTo("no detected format is recorded for occurrence " + judging.occurrence(0).value()
                                + " under run " + stageOneRun.value()));
        claim(
                "and nothing is measured for it, because nothing came back",
                () -> assertThat(judging.metricRowsFor(0)).isZero());
    }

    // ---- The moved rules that had no test of their own in pipeline ----

    @Test
    @Story("A failure that is a fact about the document")
    @DisplayName("A file the converter refused with an error status is recorded against it, and nothing is measured")
    @Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
    void verdictsARejection() {
        Judging judging = judging(1);

        OccurrenceDecision decision = judging.judge.rejected(judging.occurrence(0), "docling-serve answered HTTP 500");

        claim(
                "an error status is the converter's answer about this file, so the file is recorded as one"
                        + " extraction could not read, the reason saying it was rejected and how",
                () -> assertThat(decision)
                        .isEqualTo(new OccurrenceDecision.Failed("rejected: docling-serve answered HTTP 500")));
        claim(
                "and nothing is measured for it, because no converted document came back",
                () -> assertThat(judging.metricRowsFor(0)).isZero());
    }

    @Test
    @Story("A converter that drops the connection")
    @DisplayName("A file that dropped the connection twice is recorded as having crashed the converter")
    @Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
    void verdictsAnOccurrenceThatDroppedTheConnectionTwice() {
        Judging judging = judging(1);

        OccurrenceDecision decision =
                judging.judge.droppedTwice(judging.occurrence(0), OccurrencesUnderARun.pathOf(0), "Connection reset");

        claim(
                "the reason says the converter crashed on this file, and carries what the connection said when"
                        + " it was lost the second time",
                () -> assertThat(decision)
                        .isEqualTo(new OccurrenceDecision.DroppedTwice(
                                "crashed the converter: docling-serve dropped the connection twice while converting"
                                        + " this file: Connection reset",
                                new InARow.Counted(1))));
        claim(
                "and nothing is measured for it",
                () -> assertThat(judging.metricRowsFor(0)).isZero());
    }

    @Test
    @Story("A converter that stops answering")
    @DisplayName("A timeout the converter reports is measured, a call that brought nothing is not, and neither is one set aside")
    void onlyATimeoutTheConverterReportedBelowTheCountIsMeasured() {
        Judging judging = judging(TIMEOUTS_THAT_READ_AS_A_DEAD_SIDECAR);

        judging.answer(0, failing(FailureCategory.TIMEOUT));
        judging.judge.timedOut(judging.occurrence(1), NO_ANSWER_IN_TIME);
        judging.answer(2, failing(FailureCategory.TIMEOUT));

        claim(
                "a timeout the converter reported came back as a response about the file, so it is measured: "
                        + METRIC_ROWS_PER_JUDGED_RESPONSE + " row",
                () -> assertThat(judging.metricRowsFor(0)).isEqualTo(METRIC_ROWS_PER_JUDGED_RESPONSE));
        claim(
                "a call that brought nothing has nothing to measure: no row",
                () -> assertThat(judging.metricRowsFor(1)).isZero());
        claim(
                "and the reported timeout that made the third in a row is set aside, so it is not measured"
                        + " either, and a later run finds it untouched",
                () -> assertThat(judging.metricRowsFor(2)).isZero());
    }

    private Judging judging(int occurrences) {
        return new Judging(OccurrencesUnderARun.of(jdbcTemplate, occurrences));
    }

    /** One judge over fresh occurrences, with a control conversion that is never answered. */
    private final class Judging {

        final OccurrencesUnderARun occurrences;
        final OccurrenceJudge judge;

        Judging(OccurrencesUnderARun occurrences) {
            this.occurrences = occurrences;
            this.judge = new OccurrenceJudge(
                    new ExtractionMetrics(jdbcTemplate, new LanguageDetection()),
                    new FailuresInARow(ControlConversion.never()),
                    occurrences.run(),
                    null);
        }

        OccurrenceId occurrence(int index) {
            return occurrences.get(index);
        }

        /** An answer given in this invocation, never one read from earlier work. */
        OccurrenceDecision answer(int index, DoclingResponse response) {
            return judge.answered(occurrences.get(index), response, false);
        }

        int metricRowsFor(int index) {
            return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM extraction_metric WHERE occurrence_id = ?",
                    Integer.class,
                    occurrences.get(index).value());
        }
    }

    private static String reasonOf(OccurrenceDecision decision) {
        return ((OccurrenceDecision.Failed) decision).reason();
    }

    private static String wire(FailureCategory category) {
        return category.name().toLowerCase(Locale.ROOT);
    }

    private static DoclingResponse converted() {
        return new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 0d, null, "{}");
    }

    private static DoclingResponse failing(FailureCategory... categories) {
        List<DoclingError> errors = Arrays.stream(categories).map(OccurrenceJudgeTest::error).toList();
        return new DoclingResponse(ConversionStatus.FAILURE, errors, 0d, null, "{}");
    }

    private static DoclingError error(FailureCategory category) {
        return new DoclingError("document_backend", "docling", ERROR_MESSAGE, category, null);
    }
}
