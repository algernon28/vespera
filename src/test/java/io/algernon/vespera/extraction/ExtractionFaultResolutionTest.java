package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The resolution of extraction faults (ADR-139 sections 2 and 3), in {@code extraction} since ADR-189: a
 * set-aside occurrence's fault row is written when the set-aside is heard, in the transaction of the chunk it
 * happened in, and only where the step completed is each fault row of the run resolved, at the step's end, into
 * {@code extraction-failed}, its reason the category and the converter's message as {@code category: message}.
 *
 * <p><b>Since ADR-220, §14</b>, nothing is held until the step ends. Until then every set-aside occurrence was
 * held in a list, as many as the step's faults, and written at its end, ADR-139 section 2 having held that a row
 * written where the skip is heard needs a second connection; spring-batch-core 6.0.5 hears a skip in processing
 * inside the chunk's own transaction, on the step's thread, so the row goes on the chunk's connection. The test's
 * own transaction stands in for the chunk's here.
 *
 * <p>When it runs, in which transaction, and how the step's exit status is read stay {@code pipeline}'s, in
 * {@code ExtractionFaultRecorder}. It names the methods ADR-220 gives this class, so the test tree does not
 * compile until they exist.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("407")
@Issue("458")
@Link(name = "ADR-189", url = Adr.STAGE_2_RULES_LIVE_IN_EXTRACTION, type = "adr")
@Link(name = "ADR-139", url = Adr.A_REFUSED_CONVERSION_LEAVES_A_FAULT_ROW, type = "adr")
@Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
class ExtractionFaultResolutionTest {

    /** Two set-aside occurrences, so a resolution that wrote only the first or the last would show. */
    private static final int TWO_RECORDED = 2;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A file set aside is recorded when it is set aside, and resolved at the end of the stage")
    @DisplayName("Recording a set-aside file writes its fault at once, in the transaction it was set aside in, and judges nothing")
    void recordingWritesTheFaultAtOnce() {
        OccurrencesUnderARun occurrences = OccurrencesUnderARun.of(jdbcTemplate, 1);
        ExtractionFaultResolution resolution = resolution();

        boolean emptyBefore = resolution.nothingRecorded();
        resolution.record(occurrences.get(0), occurrences.run(), "capacity", "no room");

        claim(
                "before anything is recorded there is nothing to resolve",
                () -> assertThat(emptyBefore).isTrue());
        claim(
                "and recording one writes its fault row at once, with the category and the message, inside the"
                        + " transaction it was recorded in, where the chunk's other rows are; and no verdict, which"
                        + " waits for the end of the stage",
                () -> {
                    assertThat(resolution.nothingRecorded()).isFalse();
                    assertThat(jdbcTemplate.queryForList(
                                    "SELECT category, detail FROM extraction_fault WHERE run_id = ?",
                                    occurrences.run().value()))
                            .containsExactly(Map.of("category", "capacity", "detail", "no room"));
                    assertThat(count("verdict")).isZero();
                });
    }

    @Test
    @Story("A file set aside is recorded when it is set aside, and resolved at the end of the stage")
    @DisplayName("Nothing a stage sets aside is kept in memory to the end of the stage")
    void nothingIsHeldToTheEndOfTheStage() throws ClassNotFoundException {
        Class<?> recorder = Class.forName("io.algernon.vespera.pipeline.ExtractionFaultRecorder");
        claim(
                "the resolution keeps no collection of any kind, so it cannot hold the stage's faults: each is"
                        + " written when it is recorded, and the end of the stage reads them back a page at a time",
                () -> assertThat(collectionFieldsOf(ExtractionFaultResolution.class)).isEmpty());
        claim(
                "nor does the listener that hears each set-aside and ends the stage, so what it hears it hands on at"
                        + " once",
                () -> assertThat(collectionFieldsOf(recorder)).isEmpty());
    }

    @Test
    @Story("A file set aside is recorded when it is set aside, and resolved at the end of the stage")
    @DisplayName("A stage that completed resolves each recorded file into a verdict naming the category and the converter's message")
    void aCompletedStageResolvesEveryRecordedFault() {
        OccurrencesUnderARun occurrences = OccurrencesUnderARun.of(jdbcTemplate, TWO_RECORDED);
        ExtractionFaultResolution resolution = resolution();
        resolution.record(occurrences.get(0), occurrences.run(), "capacity", "no room for this call");
        resolution.record(occurrences.get(1), occurrences.run(), "timeout", "the converter ran out of time");

        resolution.resolve(occurrences.run(), true);

        claim(
                "each of the " + TWO_RECORDED + " is a fault row under the run, carrying the category and the"
                        + " message as the converter gave them",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT category, detail FROM extraction_fault WHERE run_id = ? ORDER BY occurrence_id",
                                occurrences.run().value()))
                        .containsExactly(
                                Map.of("category", "capacity", "detail", "no room for this call"),
                                Map.of("category", "timeout", "detail", "the converter ran out of time")));
        claim(
                "and each is resolved into extraction-failed, the reason the category, a colon and the message,"
                        + " the shape every other such reason has",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT kind, reason FROM verdict WHERE run_id = ? ORDER BY occurrence_id",
                                occurrences.run().value()))
                        .containsExactly(
                                Map.of("kind", "EXTRACTION_FAILED", "reason", "capacity: no room for this call"),
                                Map.of("kind", "EXTRACTION_FAILED", "reason", "timeout: the converter ran out of time")));
    }

    @Test
    @Story("A file set aside is recorded when it is set aside, and resolved at the end of the stage")
    @DisplayName("A stage that stopped keeps every recorded file's fault and judges none of them")
    void aStoppedStageKeepsTheFaultsAndJudgesNothing() {
        OccurrencesUnderARun occurrences = OccurrencesUnderARun.of(jdbcTemplate, TWO_RECORDED);
        ExtractionFaultResolution resolution = resolution();
        resolution.record(occurrences.get(0), occurrences.run(), "internal", "the converter failed");
        resolution.record(occurrences.get(1), occurrences.run(), "internal", "the converter failed again");

        resolution.resolve(occurrences.run(), false);

        claim(
                "both fault rows stand, written when they were recorded, so the next invocation knows to ask about"
                        + " them again",
                () -> assertThat(count("extraction_fault")).isEqualTo(TWO_RECORDED));
        claim(
                "and neither is judged: in a stage that stopped, the converter did not answer for the files"
                        + " around them, so nothing says the refusal was about the file",
                () -> assertThat(count("verdict")).isZero());
    }

    private ExtractionFaultResolution resolution() {
        return new ExtractionFaultResolution(new ExtractionFaults(jdbcTemplate), new Ledger(jdbcTemplate));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    /** The instance fields of {@code type} that can hold many values: a collection, a map or an array. */
    static java.util.List<String> collectionFieldsOf(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> Collection.class.isAssignableFrom(field.getType())
                        || Map.class.isAssignableFrom(field.getType())
                        || field.getType().isArray())
                .map(Field::getName)
                .toList();
    }
}
