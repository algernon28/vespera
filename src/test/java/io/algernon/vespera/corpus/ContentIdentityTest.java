package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
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
 * {@code corpus}'s own record of content identity and duplicate resolution (ADR-067, ADR-069): not
 * a verdict, so not the ledger's concern — read back through the same seam a real caller would use
 * rather than through the columns underneath it, matching {@code AnomalyLogTest}'s pattern for
 * {@code corpus}'s other side table.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Byte-level reduction")
@Feature("Content identity")
@Issue("37")
class ContentIdentityTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String A_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b85";

    @Test
    @Story("Content identity")
    @DisplayName("A recorded hash is read back exactly, scoped to its run")
    @Link(name = "ADR-067", url = Adr.CONTENT_IDENTITY_IS_A_SHA_256_HASH, type = "adr")
    void recordsAHashAndReadsItBackByRun() {
        ContentIdentity contentIdentity = new ContentIdentity(jdbcTemplate);
        OccurrenceId occurrenceId = anOccurrence();
        RunId runId = aRun(occurrenceId);

        contentIdentity.recordHash(occurrenceId, runId, A_SHA256);

        claim(
                "the hash recorded against this occurrence and run is read back exactly as written",
                () -> assertThat(contentIdentity.hashFor(occurrenceId, runId)).contains(A_SHA256));
        claim(
                "an occurrence with no recorded hash under a run reads back as absent, not as an error",
                () -> assertThat(contentIdentity.hashFor(new OccurrenceId(occurrenceId.value() + 1), runId))
                        .isEmpty());
    }

    @Test
    @Story("Duplicate resolution")
    @DisplayName("A recorded superseded-by pointer is read back exactly, scoped to its run")
    @Link(name = "ADR-069", url = Adr.DUPLICATE_SET_RESOLVES_BY_EARLIEST_CREATION_TIME, type = "adr")
    void recordsASupersededByPointerAndReadsItBackByRun() {
        ContentIdentity contentIdentity = new ContentIdentity(jdbcTemplate);
        OccurrenceId representative = anOccurrence();
        OccurrenceId superseded = anOccurrence();
        RunId runId = aRun(representative);

        contentIdentity.recordSupersededBy(superseded, runId, representative);

        claim(
                "the superseded occurrence points back at its representative",
                () -> assertThat(contentIdentity.representativeFor(superseded, runId)).contains(representative));
        claim(
                "the representative itself carries no pointer of its own -- it was never superseded",
                () -> assertThat(contentIdentity.representativeFor(representative, runId)).isEmpty());
    }

    private OccurrenceId anOccurrence() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        String path = "occurrence-" + jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_occurrence", Long.class);
        ledger.fileOccurrence(
                walkId,
                new OccurrencePath(path),
                1,
                java.time.Instant.parse("2026-08-29T10:15:30Z"),
                java.time.Instant.parse("2026-08-20T08:00:00Z"));
        return ledger.occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }

    private RunId aRun(OccurrenceId anyOccurrenceInTheWalk) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = new WalkId(jdbcTemplate.queryForObject(
                "SELECT walk_id FROM file_occurrence WHERE id = ?", Long.class, anyOccurrenceInTheWalk.value()));
        return ledger.startRun("byte-level-reduction", "abc123", "{}", walkId, List.of());
    }
}
