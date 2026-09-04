package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The {@code extraction_metric} row {@link ExtractionMetrics} writes while a document is open
 * (ADR-070, ADR-073, hand-off spec #45): one row per response actually received, size/structure/
 * confidence/language all recorded as counts, and the two-tier degeneracy judgement over the row it
 * just wrote.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Derived metrics")
@Issue("48")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
@Link(name = "ADR-073", url = Adr.STAGE_2_WRITES_DERIVED_METRICS, type = "adr")
class ExtractionMetricsTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A row is written for every response received")
    @DisplayName("A partial_success response earns a metrics row, with its size counters filled in")
    void aPartialSuccessResponseEarnsAMetricsRow() {
        ExtractionMetrics metrics = new ExtractionMetrics(jdbcTemplate, new LanguageDetection());
        OccurrenceId occurrenceId = anOccurrence();
        RunId runId = aRun();
        DoclingResponse response = new DoclingResponse(
                ConversionStatus.PARTIAL_SUCCESS,
                List.of(new DoclingError(
                        "document_backend", "docling", "one page failed", FailureCategory.BACKEND_FAILURE, 2)),
                3.5,
                null,
                "{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"real content here\"}]}}}");

        metrics.writeAndJudge(occurrenceId, runId, response, null);

        Map<String, Object> row = rowFor(occurrenceId, runId);
        claim(
                "the row records the status the response actually carried",
                () -> assertThat(row.get("status")).isEqualTo("partial_success"));
        claim(
                "and the size counters reflect the real text the response carried, not zero",
                () -> assertThat(((Number) row.get("character_count")).intValue()).isGreaterThan(0));
        claim(
                "the reported error's category is recorded, free text alongside the row",
                () -> assertThat((String) row.get("error_summary")).contains("backend_failure"));
    }

    @Test
    @Story("Tier 1's zero-content floor, applied over the row just written")
    @DisplayName("A response whose text is empty earns a degenerate-output judgement")
    void anEmptyResponseIsJudgedDegenerate() {
        ExtractionMetrics metrics = new ExtractionMetrics(jdbcTemplate, new LanguageDetection());
        OccurrenceId occurrenceId = anOccurrence();
        RunId runId = aRun();
        DoclingResponse response = new DoclingResponse(
                ConversionStatus.SUCCESS, List.of(), 1.0, null, "{\"document\":{\"json_content\":{\"texts\":[]}}}");

        DegeneracyVerdict verdict = metrics.writeAndJudge(occurrenceId, runId, response, null);

        claim("empty extracted text trips tier 1", () -> assertThat(verdict.degenerate()).isTrue());
    }

    @Test
    @Story("A stored conversion the writer records, not just judges")
    @DisplayName("write() records a row for a document-scoped extraction-failed response, without judging degeneracy")
    void writeRecordsARowForAnExtractionFailedResponse() {
        ExtractionMetrics metrics = new ExtractionMetrics(jdbcTemplate, new LanguageDetection());
        OccurrenceId occurrenceId = anOccurrence();
        RunId runId = aRun();
        DoclingResponse response = new DoclingResponse(
                ConversionStatus.FAILURE,
                List.of(new DoclingError(
                        "document_backend", "docling", "corrupt content", FailureCategory.BACKEND_FAILURE, null)),
                0.8,
                null,
                "{}");

        metrics.write(occurrenceId, runId, response);

        Map<String, Object> row = rowFor(occurrenceId, runId);
        claim(
                "the extraction-failed response's own status is what the row records",
                () -> assertThat(row.get("status")).isEqualTo("failure"));
    }

    private Map<String, Object> rowFor(OccurrenceId occurrenceId, RunId runId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM extraction_metric WHERE occurrence_id = ? AND run_id = ?",
                occurrenceId.value(),
                runId.value());
    }

    private OccurrenceId anOccurrence() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        ledger.fileOccurrence(
                walkId, new OccurrencePath("document.txt"), 10, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse(
                        "2026-01-01T00:00:00Z"));
        return ledger.occurrenceId(walkId, new OccurrencePath("document.txt")).orElseThrow();
    }

    private RunId aRun() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/another-corpus"));
        return ledger.startRun("extraction", "abc123", "{}", walkId, List.of());
    }
}
