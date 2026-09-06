package io.algernon.vespera.similarity;

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
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * What a document's signature is computed over, and which documents get one at all (ADR-080,
 * ADR-081).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Redundancy")
@Feature("MinHash signatures")
@Issue("75")
@Link(name = "ADR-080", url = Adr.THE_BOILERPLATE_FLOOR_IS_A_GATE, type = "adr")
@Link(name = "ADR-081", url = Adr.MINHASH_RETRIEVES_SHINGLE_SETS_JUDGE, type = "adr")
class RedundancySignaturesTest {

    /** One row per band: 16 bands of 8 rows over 128 permutations (ADR-081). */
    private static final int EXPECTED_BAND_ROWS = 16;

    private static final long DISTINCTIVE_HASH = 4_242L;

    private static final long BOILERPLATE_HASH = 7L;

    private static final double FLOOR = 0.5;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A signature stands for what is distinctive about a document")
    @DisplayName("A document with distinctive shingles left gets one signature and one row per band")
    void writesASignatureAndItsBands() {
        Fixture fixture = fixture();
        OccurrenceId document = fixture.occurrence("paper.pdf", Set.of(DISTINCTIVE_HASH, BOILERPLATE_HASH));

        boolean signed = fixture.sign(document, Set.of(BOILERPLATE_HASH));

        claim("the document was signed, over what was left after boilerplate came out", () -> assertThat(signed)
                .isTrue());
        claim("one signature row exists for it", () -> assertThat(signatureCount(document))
                .isEqualTo(1));
        claim(
                "and " + EXPECTED_BAND_ROWS + " band rows -- one per band of 8 minima, which is what"
                        + " candidate generation groups on",
                () -> assertThat(bandCount(document)).isEqualTo(EXPECTED_BAND_ROWS));
    }

    @Test
    @Story("A document that is entirely boilerplate is empty, not redundant")
    @DisplayName("A document whose every shingle is boilerplate gets no signature and no bands")
    void writesNothingForAnAllBoilerplateDocument() {
        Fixture fixture = fixture();
        OccurrenceId coverSheet = fixture.occurrence("cover-sheet.pdf", Set.of(BOILERPLATE_HASH));

        boolean signed = fixture.sign(coverSheet, Set.of(BOILERPLATE_HASH));

        claim(
                "nothing distinctive remained, so nothing was signed -- a standard cover sheet or"
                        + " disclaimer page is empty rather than redundant (ADR-080)",
                () -> assertThat(signed).isFalse());
        claim("no signature row was written", () -> assertThat(signatureCount(coverSheet))
                .isZero());
        claim(
                "and no band rows either, so it never appears in a candidate bucket -- an empty set"
                        + " matches everything or nothing depending on how the estimator is written, and"
                        + " neither answer is true of it",
                () -> assertThat(bandCount(coverSheet)).isZero());
    }

    private int signatureCount(OccurrenceId occurrenceId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM minhash_signature WHERE occurrence_id = ?", Integer.class, occurrenceId.value());
    }

    private int bandCount(OccurrenceId occurrenceId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM signature_band WHERE occurrence_id = ?", Integer.class, occurrenceId.value());
    }

    private Fixture fixture() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        RunId stage2RunId = ledger.startRun("extraction", "abc123", "{}", walkId, List.of());
        RunId stage4RunId = ledger.startRun("content-redundancy", "ghi789", "{}", walkId, List.of(stage2RunId));
        return new Fixture(ledger, walkId, stage2RunId, stage4RunId);
    }

    private class Fixture {
        private final Ledger ledger;
        private final WalkId walkId;
        private final RunId stage2RunId;
        private final RunId stage4RunId;

        Fixture(Ledger ledger, WalkId walkId, RunId stage2RunId, RunId stage4RunId) {
            this.ledger = ledger;
            this.walkId = walkId;
            this.stage2RunId = stage2RunId;
            this.stage4RunId = stage4RunId;
        }

        OccurrenceId occurrence(String path, Set<Long> shingleHashes) {
            ledger.fileOccurrence(
                    walkId,
                    new OccurrencePath(path),
                    1,
                    Instant.parse("2026-08-29T10:15:30Z"),
                    Instant.parse("2026-08-20T08:00:00Z"));
            OccurrenceId occurrenceId =
                    ledger.occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
            for (long hash : shingleHashes) {
                jdbcTemplate.update(
                        "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                                + " VALUES (?, ?, ?, ?)",
                        occurrenceId.value(),
                        stage2RunId.value(),
                        ShingleParameters.DEFAULT.identity(),
                        hash);
            }
            return occurrenceId;
        }

        boolean sign(OccurrenceId occurrenceId, Set<Long> boilerplateHashes) {
            return new RedundancySignatures(jdbcTemplate)
                    .write(occurrenceId, stage4RunId, stage2RunId, boilerplateHashes, FLOOR);
        }
    }
}
