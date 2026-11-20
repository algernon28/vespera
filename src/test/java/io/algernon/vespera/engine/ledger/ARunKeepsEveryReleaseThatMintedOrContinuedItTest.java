package io.algernon.vespera.engine.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The ledger keeps, beside each run, every release version that minted or continued it, in {@code run_release}, and
 * the run's id does not move with it (ADR-430 §3, §5): two jars of one commit built as two releases carry on one run,
 * which then names both.
 *
 * <p>{@code Runs.recordRelease} is reached by its name, so that the suite compiles before it is written; each claim
 * fails until it is and {@code schema.sql} holds the table. The rows are read by a statement of this test's own, as
 * no shipped class reads them.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Operation")
@Feature("Vespera's version")
@Issue("904")
@Link(name = "ADR-430", url = Adr.THE_RELEASE_VERSION_IS_THE_TAGS_AND_ENTERS_NO_RUN_ID, type = "adr")
class ARunKeepsEveryReleaseThatMintedOrContinuedItTest {

    /** A stage name for the run rows below. */
    private static final String A_STAGE = "extraction";

    /** The implementation version both jars share: one commit, built twice. */
    private static final String ONE_COMMIT = "765e0dd026347ddf972cc306b8057764ddfd5649";

    /** The build no tag made, and the release built from the same commit. */
    private static final String NO_TAG = "0-SNAPSHOT";

    private static final String A_RELEASE = "2.0.0";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Vespera's version never decides which work is done again")
    @DisplayName("Two builds of one commit carry on one piece of work, which keeps each version that worked on it, once")
    void aRunKeepsEachReleaseOnceAndItsIdDoesNotMove() throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus"));
        Method recordRelease = Runs.class.getMethod("recordRelease", RunId.class, String.class);

        RunId underNoTag = ledger.runs().startRun(A_STAGE, ONE_COMMIT, "{}", walk, List.of());
        recordRelease.invoke(ledger.runs(), underNoTag, NO_TAG);
        RunId underTheRelease = ledger.runs().startRun(A_STAGE, ONE_COMMIT, "{}", walk, List.of());
        recordRelease.invoke(ledger.runs(), underTheRelease, A_RELEASE);
        recordRelease.invoke(ledger.runs(), underTheRelease, A_RELEASE);

        claim("the release carries on the work the build no tag made began, under the same name",
                () -> assertThat(underTheRelease).isEqualTo(underNoTag));
        claim("that work keeps both versions that worked on it, each once, however often it was carried on",
                () -> assertThat(releasesOf(underNoTag)).containsExactly(NO_TAG, A_RELEASE));
    }

    private List<String> releasesOf(RunId run) {
        return jdbcTemplate.queryForList(
                "SELECT release_version FROM run_release WHERE run_id = ? ORDER BY release_version",
                String.class,
                run.value());
    }
}
