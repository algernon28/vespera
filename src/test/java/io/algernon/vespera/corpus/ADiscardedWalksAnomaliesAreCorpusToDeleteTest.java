package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.WalkId;
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
import org.springframework.dao.DataAccessException;
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

    /** The one anomaly the second test records and never deletes. */
    private static final int THE_ANOMALY_LEFT_STANDING = 1;

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

        claim(
                "the walk to be discarded starts with the " + ANOMALIES_OF_THE_DISCARDED_WALK
                        + " anomalies recorded against it, so there is something to delete",
                () -> assertThat(anomalyLog.anomalyCount(discarded)).isEqualTo(ANOMALIES_OF_THE_DISCARDED_WALK));

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
                "with its anomalies gone the walk itself is removed, which is the order the two deletes are"
                        + " made in",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM walk WHERE id = ?", Integer.class, discarded.value()))
                        .isZero());
    }

    @Test
    @Story("What corpus records about a walk anomaly")
    @DisplayName("The ledger does not delete a walk's anomalies itself, so a walk whose anomalies still stand cannot be removed")
    void theLedgerLeavesTheAnomaliesToTheirOwner() {
        Ledger ledger = new Ledger(jdbcTemplate);
        AnomalyLog anomalyLog = new AnomalyLog(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus"));
        anomalyLog.anomaly(walk, "orphan.txt", WalkAnomalyKind.UNENCODABLE_PATH, "no UTF-8 encoding");

        claim(
                "removing the walk while an anomaly still refers to it is refused by the database: the ledger"
                        + " deletes only its own rows, and had it deleted the anomaly as well, as it used to,"
                        + " nothing would be left to refuse",
                () -> assertThatThrownBy(() -> ledger.walks().discardWalk(walk)).isInstanceOf(DataAccessException.class));
        claim(
                "and the anomaly is still recorded, so the refused delete took nothing with it",
                () -> assertThat(anomalyLog.anomalyCount(walk)).isEqualTo(THE_ANOMALY_LEFT_STANDING));
    }
}
