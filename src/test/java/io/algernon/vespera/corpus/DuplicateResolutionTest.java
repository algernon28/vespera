package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.DuplicateResolution.Candidate;
import io.algernon.vespera.corpus.DuplicateResolution.Resolution;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DuplicateResolution} against hand-built groups (ADR-069): pure, so the earliest-creation-
 * time rule and its tie-break are pinned without needing real filesystem timestamps, which are not
 * precisely controllable from a test.
 */
@Epic("Byte-level reduction")
@Feature("Duplicate resolution")
@Issue("37")
@Link(name = "ADR-069", url = Adr.DUPLICATE_SET_RESOLVES_BY_EARLIEST_CREATION_TIME, type = "adr")
class DuplicateResolutionTest {

    private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    @Story("Which occurrence becomes the representative")
    @DisplayName("The earliest-created member becomes the representative; every other member is superseded")
    void earliestCreatedMemberIsTheRepresentative() {
        Candidate older = new Candidate(new OccurrenceId(1), new OccurrencePath("b.txt"), EARLIER);
        Candidate newer = new Candidate(new OccurrenceId(2), new OccurrencePath("a.txt"), LATER);

        Resolution resolution = DuplicateResolution.resolve(List.of(newer, older));

        claim(
                "the older-created occurrence wins, even though it sorts second by path -- creation time"
                        + " leads, path only breaks a tie",
                () -> assertThat(resolution.representative()).isEqualTo(older.occurrenceId()));
        claim(
                "the newer occurrence is the one superseded",
                () -> assertThat(resolution.superseded()).containsExactly(newer.occurrenceId()));
    }

    @Test
    @Story("Which occurrence becomes the representative")
    @DisplayName("An identical creation time breaks the tie on lexicographically-lowest path")
    void identicalCreationTimeBreaksOnPath() {
        Candidate zPath = new Candidate(new OccurrenceId(1), new OccurrencePath("z.txt"), EARLIER);
        Candidate aPath = new Candidate(new OccurrenceId(2), new OccurrencePath("a.txt"), EARLIER);

        Resolution resolution = DuplicateResolution.resolve(List.of(zPath, aPath));

        claim(
                "both share a creation time, so the lexicographically-lowest path, a.txt, wins",
                () -> assertThat(resolution.representative()).isEqualTo(aPath.occurrenceId()));
        claim(
                "z.txt is the one superseded",
                () -> assertThat(resolution.superseded()).containsExactly(zPath.occurrenceId()));
    }

    @Test
    @Story("Which occurrence becomes the representative")
    @DisplayName("A group of three resolves to one representative and two superseded members")
    void aGroupOfThreeResolvesToOneRepresentative() {
        Candidate earliest = new Candidate(new OccurrenceId(1), new OccurrencePath("earliest.txt"), EARLIER);
        Candidate middle = new Candidate(
                new OccurrenceId(2), new OccurrencePath("middle.txt"), EARLIER.plusSeconds(60));
        Candidate latest = new Candidate(new OccurrenceId(3), new OccurrencePath("latest.txt"), LATER);

        Resolution resolution = DuplicateResolution.resolve(List.of(latest, earliest, middle));

        claim(
                "the earliest of the three wins regardless of the order the group is given in",
                () -> assertThat(resolution.representative()).isEqualTo(earliest.occurrenceId()));
        claim(
                "the other two are both superseded, and only those two",
                () -> assertThat(resolution.superseded())
                        .containsExactlyInAnyOrder(middle.occurrenceId(), latest.occurrenceId()));
    }

    @Test
    @Story("What resolve refuses")
    @DisplayName("A group of fewer than two members has nothing to resolve")
    void refusesAGroupOfFewerThanTwo() {
        Candidate lone = new Candidate(new OccurrenceId(1), new OccurrencePath("only.txt"), EARLIER);

        claim(
                "resolving a group of one is a caller error -- a lone occurrence was never a duplicate,"
                        + " so nothing should have called resolve on it",
                () -> assertThatThrownBy(() -> DuplicateResolution.resolve(List.of(lone)))
                        .isInstanceOf(IllegalArgumentException.class));
    }
}
