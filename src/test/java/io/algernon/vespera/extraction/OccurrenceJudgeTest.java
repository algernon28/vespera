package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
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

    /** How many timeouts in a row the application ships as the converter's rather than the file's (ADR-330 §8). */
    private static final int TIMEOUTS_THAT_READ_AS_A_DEAD_SIDECAR = 3;

    /** One measurement row per answer a document really came back for; a set-aside answer earns none. */
    private static final int METRIC_ROWS_PER_JUDGED_RESPONSE = 1;

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

    private Judging judging(int occurrences) {
        return new Judging(OccurrencesUnderARun.of(jdbcTemplate, occurrences));
    }

    /** One judge over fresh occurrences, with a control conversion that is never answered. */
    private final class Judging {

        final OccurrencesUnderARun occurrences;
        final OccurrenceJudge judge;

        Judging(OccurrencesUnderARun occurrences) {
            this(occurrences, TIMEOUTS_THAT_READ_AS_A_DEAD_SIDECAR);
        }

        Judging(OccurrencesUnderARun occurrences, int timeoutsInARow) {
            this.occurrences = occurrences;
            this.judge = new OccurrenceJudge(
                    new ExtractionMetrics(jdbcTemplate, new LanguageDetection()),
                    new FailuresInARow(ControlConversion.never()),
                    occurrences.run(),
                    null,
                    null,
                    timeoutsInARow);
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
