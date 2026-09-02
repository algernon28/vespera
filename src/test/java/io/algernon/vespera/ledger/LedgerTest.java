package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The ledger's record of file occurrences, read back through the same seam a real caller would use
 * rather than through the columns underneath it, so the shape of the tables is free to change
 * without these tests reacting.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Census")
@Feature("Ledger")
class LedgerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What the ledger records")
    @DisplayName("A file occurrence recorded against a walk is read back by that walk's id")
    @Link(name = "ADR-069", url = Adr.DUPLICATE_SET_RESOLVES_BY_EARLIEST_CREATION_TIME, type = "adr")
    void recordsAFileOccurrenceAndReadsItBackByWalkId() {
        Ledger ledger = new Ledger(jdbcTemplate);
        Instant lastModified = Instant.parse("2026-08-29T10:15:30Z");
        Instant creationTime = Instant.parse("2026-08-20T08:00:00Z");

        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        ledger.fileOccurrence(walkId, new OccurrencePath("a/b.txt"), 10, lastModified, creationTime);

        claim(
                "the occurrence recorded against this walk is read back exactly as it was written, creation"
                        + " time included",
                () -> assertThat(ledger.occurrencesForWalk(walkId))
                        .containsExactly(new RecordedOccurrence(
                                new OccurrencePath("a/b.txt"), 10, lastModified, creationTime)));
    }
}
