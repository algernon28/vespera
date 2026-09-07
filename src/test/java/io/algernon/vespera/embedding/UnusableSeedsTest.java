package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * A seed document that produced no text, recorded rather than judged (ADR-083).
 *
 * <p>The claim worth stating plainly, because it is the one an implementer is most likely to
 * "correct": there is no verdict here, and there never will be. Every kind in the closed vocabulary
 * exists to remove a document from publication, and a seed is never published — an unreadable seed
 * is an operator's problem to fix, not a document to filter.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Seed set")
@Issue("104")
@Link(name = "ADR-083", url = Adr.THE_SEED_SET_IS_EXTRACTED_BY_STAGE_5, type = "adr")
class UnusableSeedsTest {

    /** Stage 2's tier-1 wording, which this bar is deliberately identical to (ADR-070). */
    private static final String REASON = "zero alphanumeric content after whitespace normalisation";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("An unusable seed is data, not a verdict")
    @DisplayName("A seed that produced no text is recorded with which occurrence it was and why")
    void recordsWhichSeedWasUnusableAndWhy() {
        Ledger ledger = new Ledger(jdbcTemplate);
        Fixture fixture = fixture(ledger);
        UnusableSeeds unusableSeeds = new UnusableSeeds(jdbcTemplate);

        unusableSeeds.record(fixture.seed(), fixture.runId(), REASON);

        claim(
                "the seed is recorded against the run that found it, carrying the reason it produced no"
                        + " text -- an operator fixing a seed folder needs to know which file and what was"
                        + " wrong with it, and a bare count answers neither",
                () -> assertThat(unusableSeeds.forRun(fixture.runId()))
                        .containsExactly(new UnusableSeed(fixture.seed(), REASON)));
    }

    @Test
    @Story("An unusable seed is data, not a verdict")
    @DisplayName("Recording an unusable seed writes no verdict of any kind against it")
    void writesNoVerdictOfAnyKindAgainstASeedOccurrence() {
        Ledger ledger = new Ledger(jdbcTemplate);
        Fixture fixture = fixture(ledger);

        new UnusableSeeds(jdbcTemplate).record(fixture.seed(), fixture.runId(), REASON);

        claim(
                "no verdict row of any kind stands against the seed -- asserted against the whole closed"
                        + " vocabulary rather than the plausible kinds, because the mistake to catch is a"
                        + " later slice reaching for extraction-failed as the nearest-looking word for a"
                        + " condition that is not a removal at all",
                () -> assertThat(verdictKindsAgainst(fixture.seed())).isEmpty());
    }

    @Test
    @Story("A corrected seed folder is a different run")
    @DisplayName("Two runs' unusable seeds are separate row sets rather than one overwritten row")
    void keepsEachRunsUnusableSeedsApart() {
        Ledger ledger = new Ledger(jdbcTemplate);
        Fixture fixture = fixture(ledger);
        RunId laterRun = ledger.startRun("seed-measurement", "later", "{}", fixture.walkId(), List.of());
        UnusableSeeds unusableSeeds = new UnusableSeeds(jdbcTemplate);

        unusableSeeds.record(fixture.seed(), fixture.runId(), REASON);
        unusableSeeds.record(fixture.seed(), laterRun, REASON);

        claim(
                "the first run's row is still its own after the second run recorded the same occurrence:"
                        + " a corrected seed folder is a different run (ADR-089), and that is what keeps"
                        + " scores taken against a partial seed set from being read as scores against a"
                        + " complete one",
                () -> assertThat(unusableSeeds.forRun(fixture.runId()))
                        .containsExactly(new UnusableSeed(fixture.seed(), REASON)));
    }

    /** One seed walk, one occurrence in it, and a measurement run over it. */
    private Fixture fixture(Ledger ledger) {
        WalkId walkId = ledger.startWalk(Path.of("C:/seeds"));
        ledger.fileOccurrence(
                walkId, new OccurrencePath("empty.pdf"), 1L, Instant.EPOCH, Instant.EPOCH);
        OccurrenceId seed = ledger.occurrenceId(walkId, new OccurrencePath("empty.pdf"))
                .orElseThrow(() -> new IllegalStateException("the fixture's own occurrence was not recorded"));
        RunId runId = ledger.startRun("seed-measurement", "abc123", "{}", walkId, List.of());
        return new Fixture(walkId, seed, runId);
    }

    private record Fixture(WalkId walkId, OccurrenceId seed, RunId runId) {}

    /** Every verdict kind standing against an occurrence, whatever it says. */
    private List<String> verdictKindsAgainst(OccurrenceId occurrenceId) {
        List<String> kinds = jdbcTemplate.queryForList(
                "SELECT kind FROM verdict WHERE occurrence_id = ?", String.class, occurrenceId.value());
        List<String> vocabulary = Arrays.stream(VerdictKind.values()).map(Enum::name).toList();
        if (!vocabulary.containsAll(kinds)) {
            throw new IllegalStateException("a verdict row carries a kind outside the closed vocabulary: " + kinds);
        }
        return kinds;
    }
}
