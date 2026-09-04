package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Walk anomalies as {@code corpus} records them: not a verdict, so not in the ledger (ADR-041) —
 * read back through the same seam a real caller would use, not through the columns underneath.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Census")
@Feature("Walk anomalies")
class AnomalyLogTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What corpus records about a walk anomaly")
    @DisplayName("A walk anomaly recorded against a walk is read back by that walk's id")
    @Issue("6")
    @Link(name = "ADR-053", url = Adr.WALK_ANOMALY_VOCABULARY_IS_THREE_KINDS, type = "adr")
    @Link(name = "ADR-041", url = Adr.LEDGER_OWNS_IDENTITY_AND_VERDICTS, type = "adr")
    void recordsAWalkAnomalyAndReadsItBackByWalkId() {
        WalkId walkId = new Ledger(jdbcTemplate).startWalk(Path.of("C:/corpus"));
        AnomalyLog anomalyLog = new AnomalyLog(jdbcTemplate);

        anomalyLog.anomaly(walkId, "orphan.txt", WalkAnomalyKind.UNENCODABLE_PATH, "no UTF-8 encoding");

        claim(
                "the anomaly recorded against this walk is read back exactly as it was written",
                () -> assertThat(anomalyLog.anomaliesForWalk(walkId))
                        .containsExactly(new RecordedAnomaly(
                                "orphan.txt", WalkAnomalyKind.UNENCODABLE_PATH, "no UTF-8 encoding")));
    }
}
