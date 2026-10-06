package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.embedding.EmbeddingStatement;
import io.algernon.vespera.extraction.ExtractionStatement;
import io.algernon.vespera.similarity.SimilarityStatement;
import io.algernon.vespera.synthesis.SynthesisStatement;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The steps a row each module declares for its counted statements are the ones SQLite was measured to
 * take, and every other statement declares none (ADR-193 sections 3 and 7, #411).
 *
 * <p>It was parked under {@code docs/adr/0193/tests/b/} until part (b) added the last of the four enums, and
 * came into {@code src/test} with that part (ADR-204).
 *
 * <p>Four, where ADR-193 section 7 named five: {@code corpus} has no statement of that record's since
 * ADR-200 (#405), under which stage 1 drains no survivors, so no {@code CorpusStatement} is written.
 *
 * <p>The measured figures are {@link StatementStepsPerRowTest}'s, which runs each statement against the
 * bundled SQLite, and for ADR-199's two reads {@link UncoveredStatementsStepsPerRowTest}'s. This test only
 * holds the declarations to them, so a ratio changed in one place and not the other fails here. ADR-199's
 * three timed survivor counts are in no enum: {@code pipeline} times each where it makes the call.
 */
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("411")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
class StatementStepsPerRowAreTheDeclaredOnesTest {

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("Each counted statement declares the steps a row SQLite was measured to take")
    void eachCountedStatementDeclaresItsMeasuredSteps() {
        Map<Enum<?>, Integer> measured = Map.of(
                SimilarityStatement.SHINGLE_HASH_INDEX_BUILD,
                        StatementStepsPerRowTest.BUILD_STEPS_BEYOND_COLUMNS + 3,
                SimilarityStatement.SHINGLE_ROWS, StatementStepsPerRowTest.SHINGLE_ROWS_STEPS,
                SimilarityStatement.SIGNED_OCCURRENCES, StatementStepsPerRowTest.SIGNED_OCCURRENCES_STEPS,
                ExtractionStatement.EXTRACTION_METRICS, StatementStepsPerRowTest.CONFIDENCE_METRICS_STEPS,
                // ADR-199's two counted reads of stage 2's resume, measured by their own test. Nine pairs of
                // Map.of's ten: one more counted statement moves this to Map.ofEntries.
                ExtractionStatement.FAULTED_OCCURRENCES, UncoveredStatementsStepsPerRowTest.FAULTED_OCCURRENCES_STEPS,
                ExtractionStatement.RECORDED_OCCURRENCES,
                        UncoveredStatementsStepsPerRowTest.RECORDED_OCCURRENCES_STEPS,
                EmbeddingStatement.UNUSABLE_SEEDS, StatementStepsPerRowTest.UNUSABLE_SEEDS_STEPS,
                EmbeddingStatement.CORPUS_METRICS, StatementStepsPerRowTest.COMPARISON_METRICS_STEPS,
                EmbeddingStatement.SEED_METRICS, StatementStepsPerRowTest.COMPARISON_METRICS_STEPS);

        for (Map.Entry<Enum<?>, Integer> statement : measured.entrySet()) {
            claim(
                    statement.getKey() + " declares " + statement.getValue() + " steps a row, as measured",
                    () -> assertThat(stepsPerRowOf(statement.getKey())).hasValue(statement.getValue()));
        }
        claim(
                "start-up's builds take eight steps a row beyond their columns, as measured",
                () -> assertThat(StartUpIndexAnnouncement.INDEX_BUILD_STEPS_BEYOND_COLUMNS)
                        .isEqualTo(StatementStepsPerRowTest.BUILD_STEPS_BEYOND_COLUMNS));

        List<Enum<?>> timed = everyStatement()
                .filter(statement -> !measured.containsKey(statement))
                .toList();
        for (Enum<?> statement : timed) {
            claim(
                    statement + " is timed, and declares no steps a row",
                    () -> assertThat(stepsPerRowOf(statement)).isEmpty());
        }
    }

    private static Stream<Enum<?>> everyStatement() {
        return Stream.of(
                        ExtractionStatement.values(),
                        SimilarityStatement.values(),
                        EmbeddingStatement.values(),
                        SynthesisStatement.values())
                .flatMap(Arrays::stream);
    }

    private static OptionalInt stepsPerRowOf(Enum<?> statement) {
        return switch (statement) {
            case ExtractionStatement extraction -> extraction.stepsPerRow();
            case SimilarityStatement similarity -> similarity.stepsPerRow();
            case EmbeddingStatement embedding -> embedding.stepsPerRow();
            case SynthesisStatement synthesis -> synthesis.stepsPerRow();
            default -> throw new IllegalArgumentException("not a statement of ADR-193: " + statement);
        };
    }
}
