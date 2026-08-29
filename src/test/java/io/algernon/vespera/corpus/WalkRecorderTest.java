package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RecordedOccurrence;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * A live walk against a real filesystem, persisted through {@link Ledger} and {@link AnomalyLog}
 * rather than only reported to an in-memory recorder, as {@link io.algernon.vespera.corpus.WalkTest}
 * does. Read back through the same reader seams those tests already establish.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Census")
@Feature("Walk recording")
class WalkRecorderTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A live walk is persisted")
    @DisplayName("A file occurrence found by a live walk is recorded under that walk's id")
    void aLiveWalkRecordsItsOccurrencesUnderOneWalkId(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "hi");
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkRecorder recorder = new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate));

        WalkId walkId = recorder.walk(root);

        claim(
                "the one file written was recorded as an occurrence under the walk's own id",
                () -> assertThat(ledger.occurrencesForWalk(walkId))
                        .extracting(RecordedOccurrence::path)
                        .containsExactly(new OccurrencePath("a.txt")));
    }

    @Test
    @Story("A live walk is persisted")
    @DisplayName("A walk anomaly found by a live walk is recorded under that same walk's id")
    void aLiveWalkRecordsItsAnomaliesUnderTheSameWalkId(@TempDir Path root) throws IOException {
        Path unstorable = root.resolve("orphan-" + (char) 0xD800 + ".txt");
        try {
            Files.writeString(unstorable, "content");
        } catch (IOException e) {
            abort("this filesystem will not create a name with an unpaired surrogate: " + e.getMessage());
        }
        AnomalyLog anomalyLog = new AnomalyLog(jdbcTemplate);
        WalkRecorder recorder = new WalkRecorder(new Ledger(jdbcTemplate), anomalyLog);

        WalkId walkId = recorder.walk(root);

        claim(
                "the unstorable name was recorded as one anomaly under the walk's own id",
                () -> assertThat(anomalyLog.anomaliesForWalk(walkId))
                        .extracting(RecordedAnomaly::kind)
                        .containsExactly(WalkAnomalyKind.UNENCODABLE_PATH));
    }
}
