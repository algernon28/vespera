package io.algernon.vespera.engine.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Who deletes the walk anomalies of a walk that is discarded: {@code corpus}, which owns their table, and
 * not the ledger, which deleted them until ADR-209 section 3.
 *
 * <p>The ledger still deletes the walk's row and its occurrences, and the anomalies refer to that row, so
 * the two deletes are one piece of work in a fixed order: the anomalies first. The second test is what
 * holds the ledger to having stopped: with the anomalies left standing, the database refuses the walk's
 * delete, which it could not do if the ledger still removed them itself.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Census")
@Feature("Walk anomalies")
@Issue("350")
@Link(name = "ADR-209", url = Adr.THE_LEDGER_IS_FOUR_RECORDS_AND_A_TABLES_SQL_IS_ITS_OWNERS, type = "adr")
@Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
class ADiscardedWalksAnomaliesAreCorpusToDeleteTest {

    /** The anomalies recorded against the walk that is discarded. */
    private static final int ANOMALIES_OF_THE_DISCARDED_WALK = 2;

    /** The one anomaly recorded against the walk that is kept, which no discard may touch. */
    private static final int ANOMALIES_OF_THE_WALK_KEPT = 1;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What corpus records about a walk anomaly")
    @DisplayName("Discarding a walk's anomalies removes that walk's and leaves every other walk's, and the walk can then be removed")
    void discardsThatWalksAnomaliesAndNoOthers() {
        Ledger ledger = new Ledger(jdbcTemplate);
        AnomalyLog anomalyLog = new AnomalyLog(jdbcTemplate);
        WalkId kept = ledger.walks().startWalk(Path.of("C:/corpus"));
        WalkId discarded = ledger.walks().startWalk(Path.of("C:/corpus"));
        anomalyLog.anomaly(kept, "kept.txt", WalkAnomalyKind.UNENCODABLE_PATH, "no UTF-8 encoding");
        anomalyLog.anomaly(discarded, "first.txt", WalkAnomalyKind.UNENCODABLE_PATH, "no UTF-8 encoding");
        anomalyLog.anomaly(discarded, "second.txt", WalkAnomalyKind.UNPROCESSABLE, "could not be read");

        assumeTrue(
                anomalyLog.anomalyCount(discarded) == ANOMALIES_OF_THE_DISCARDED_WALK,
                "the walk to be discarded starts with its anomalies recorded against it, so there is something to"
                        + " delete");

        anomalyLog.discardForWalk(discarded);

        claim(
                "none of the discarded walk's anomalies is left",
                () -> assertThat(anomalyLog.anomalyCount(discarded)).isZero());
        claim(
                "and the " + ANOMALIES_OF_THE_WALK_KEPT + " anomaly of the other walk over the same folder is"
                        + " untouched",
                () -> assertThat(anomalyLog.anomalyCount(kept)).isEqualTo(ANOMALIES_OF_THE_WALK_KEPT));

        ledger.walks().discardWalk(discarded);

        claim(
                "and with its anomalies gone the walk itself can be removed, which the database refuses while"
                        + " they stand",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM walk WHERE id = ?", Integer.class, discarded.value()))
                        .isZero());
    }
}
