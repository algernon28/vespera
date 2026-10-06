package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.similarity.SimilarityStatement;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The three ratios ADR-193's part (a) declared in {@code src/main} are the ones {@link
 * StatementStepsPerRowTest} measures against the bundled SQLite (ADR-193 section 3, ADR-199 section 6,
 * #411).
 *
 * <p>{@code StatementStepsPerRowTest} measures SQLite and names no declaration; until this class, nothing
 * held a declaration to a measurement, so a ratio changed in {@code src/main} alone passed every test, and so
 * did a measured constant changed alone. Three declarations exist on main: the build of {@code
 * shingle_by_hash}, stage 3's read of the shingle rows, and the eight steps a row every index build takes
 * beyond its columns.
 *
 * <p><b>It gives way to {@code StatementStepsPerRowAreTheDeclaredOnesTest}</b>, parked under {@code
 * docs/adr/0193/tests/b/} until part (b) adds the other three enums. That class makes these three claims and
 * every other declaration's, so the change that moves it into {@code src/test} deletes this one.
 */
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("411")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
@Link(name = "ADR-199", url = Adr.THE_STATEMENTS_ADR_193_LEFT_UNNAMED_TAKE_ITS_RULE, type = "adr")
class DeclaredStepsPerRowOfPartATest {

    /** The columns of {@code shingle_by_hash}: the run, the granularity and the hash. */
    private static final int COLUMNS_OF_SHINGLE_BY_HASH = 3;

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("The steps a row declared for the two statements and the index builds that report progress today are the measured ones")
    void theDeclarationsOfPartAAreTheMeasuredOnes() {
        int build = StatementStepsPerRowTest.BUILD_STEPS_BEYOND_COLUMNS + COLUMNS_OF_SHINGLE_BY_HASH;
        claim(
                "the build of shingle_by_hash declares " + build + " steps a row: the "
                        + StatementStepsPerRowTest.BUILD_STEPS_BEYOND_COLUMNS + " measured for every index build,"
                        + " and one for each of its " + COLUMNS_OF_SHINGLE_BY_HASH + " columns",
                () -> assertThat(SimilarityStatement.SHINGLE_HASH_INDEX_BUILD.stepsPerRow()).hasValue(build));
        claim(
                "the read of one run's shingle rows declares the " + StatementStepsPerRowTest.SHINGLE_ROWS_STEPS
                        + " steps a row measured for it",
                () -> assertThat(SimilarityStatement.SHINGLE_ROWS.stepsPerRow())
                        .hasValue(StatementStepsPerRowTest.SHINGLE_ROWS_STEPS));
        claim(
                "start-up's index builds declare the " + StatementStepsPerRowTest.BUILD_STEPS_BEYOND_COLUMNS
                        + " steps a row measured beyond one for each column",
                () -> assertThat(StartUpIndexAnnouncement.INDEX_BUILD_STEPS_BEYOND_COLUMNS)
                        .isEqualTo(StatementStepsPerRowTest.BUILD_STEPS_BEYOND_COLUMNS));
    }
}
