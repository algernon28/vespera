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
 *
 * <p><b>ADR-211 changes three of them.</b> Stage 3's read of the extraction metrics and 5b's reads of the
 * corpus survivors' extraction metrics, the first and every later one, are made a page of survivors at a time,
 * one statement a page, and are told the rows they have read rather than SQLite's steps: none has a ratio to
 * declare, and none is a timed statement either, each having its total. They are named here by their names,
 * since 5b's later reads have a constant only once ADR-211 is built. Stage 3's shingle rows are grouped in the
 * database, in statements that sort, and so are timed.
 */
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("411")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
class StatementStepsPerRowAreTheDeclaredOnesTest {

    /** The reads made a page of survivors at a time, which count the rows they read themselves (ADR-211). */
    private static final List<String> READ_A_PAGE_OF_SURVIVORS_AT_A_TIME =
            List.of("EXTRACTION_METRICS", "CORPUS_METRICS", "CORPUS_METRICS_AGAIN");

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("Each counted statement declares the steps a row SQLite was measured to take")
    void eachCountedStatementDeclaresItsMeasuredSteps() {
        Map<Enum<?>, Integer> measured = Map.of(
                SimilarityStatement.SHINGLE_HASH_INDEX_BUILD,
                        StatementStepsPerRowTest.BUILD_STEPS_BEYOND_COLUMNS + 3,
                SimilarityStatement.SIGNED_OCCURRENCES, StatementStepsPerRowTest.SIGNED_OCCURRENCES_STEPS,
                // ADR-199's two counted reads of stage 2's resume, measured by their own test.
                ExtractionStatement.FAULTED_OCCURRENCES, UncoveredStatementsStepsPerRowTest.FAULTED_OCCURRENCES_STEPS,
                ExtractionStatement.RECORDED_OCCURRENCES,
                        UncoveredStatementsStepsPerRowTest.RECORDED_OCCURRENCES_STEPS,
                EmbeddingStatement.UNUSABLE_SEEDS, StatementStepsPerRowTest.UNUSABLE_SEEDS_STEPS,
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

        List<String> embedding = Arrays.stream(EmbeddingStatement.values()).map(Enum::name).toList();
        claim(
                "the comparison's later reads of the surviving documents' metrics have a statement of their own,"
                        + " named straight after the first read's, the order they are issued in",
                () -> assertThat(embedding).containsSubsequence("CORPUS_METRICS", "CORPUS_METRICS_AGAIN")
                        .contains("CORPUS_METRICS_AGAIN")
                        .satisfies(names -> assertThat(names.indexOf("CORPUS_METRICS_AGAIN"))
                                .isEqualTo(names.indexOf("CORPUS_METRICS") + 1)));
        List<Enum<?>> readAPage = everyStatement()
                .filter(statement -> READ_A_PAGE_OF_SURVIVORS_AT_A_TIME.contains(statement.name()))
                .toList();
        for (Enum<?> read : readAPage) {
            claim(
                    read + " is read a page of surviving documents at a time and counts the rows it reads itself,"
                            + " so it declares no steps a row",
                    () -> assertThat(stepsPerRowOf(read)).isEmpty());
        }

        List<Enum<?>> timed = everyStatement()
                .filter(statement -> !measured.containsKey(statement))
                .filter(statement -> !READ_A_PAGE_OF_SURVIVORS_AT_A_TIME.contains(statement.name()))
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
