package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The gate between arranging the documents and writing anything over them (ADR-107).
 *
 * <p><b>It holds a name, not a yes.</b> The operator writes the first twelve characters of the id of
 * the arrangement they read. That is the whole point of it: an approval is of a particular
 * arrangement, and an approval that were merely true would never expire — an operator who looked at
 * one shape of their archive would go on shipping every later shape having looked at nothing.
 *
 * <p>The two failure directions are deliberately different. A name matching nothing leaves the gate
 * shut and says so, because a typo must not be indistinguishable from an approval. A name matching
 * two arrangements stops the run, which is what this system already does anywhere it would otherwise
 * have to guess which of two runs was meant.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({ArrangementGate.class, ProfileStore.class, Ledger.class})
@Epic("Arrangement")
@Feature("Approving the arrangement")
@Issue("175")
@Link(name = "ADR-107", url = Adr.THE_ARRANGEMENT_GATE_APPROVES_A_NAMED_RUN, type = "adr")
class ArrangementGateTest {

    /** How much of an arrangement's id the operator is asked to copy (ADR-107). */
    private static final int APPROVAL_LENGTH = 12;

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private ArrangementGate arrangementGate;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A gate closes rather than failing")
    @DisplayName("Nothing approved closes the gate")
    void closesWhenNothingIsApproved() {
        WalkId walk = aWalk();
        anArrangement(walk);
        approve(null);

        claim(
                "with nothing approved there is nothing to write over: the documents have been arranged"
                        + " and the person who has to look at that arrangement has not said so yet",
                () -> assertThat(arrangementGate.approvedArrangement(walk)).isEmpty());
    }

    @Test
    @Story("A gate closes rather than failing")
    @DisplayName("A name matching no arrangement closes the gate rather than proceeding")
    void closesWhenTheNameMatchesNothing() {
        WalkId walk = aWalk();
        anArrangement(walk);
        approve("0123456789ab");

        claim(
                "a name matching nothing leaves the gate shut instead of carrying on: a mistyped name"
                        + " that quietly behaved like an approval would be the one failure this gate exists"
                        + " to prevent",
                () -> assertThat(arrangementGate.approvedArrangement(walk)).isEmpty());
    }

    @Test
    @Story("The gate opens only when everything it needs is there")
    @DisplayName("A name matching one arrangement opens the gate on that arrangement")
    void opensOnTheArrangementTheNameMatches() {
        WalkId walk = aWalk();
        RunId arrangement = anArrangement(walk);
        approve(arrangement.value().substring(0, APPROVAL_LENGTH));

        claim(
                "the gate opens on the very arrangement the name picked out, not merely on the fact that"
                        + " some approval was written -- what is generated has to be generated over what"
                        + " was read",
                () -> assertThat(arrangementGate.approvedArrangement(walk)).contains(arrangement));
    }

    @Test
    @Story("The gate opens only when everything it needs is there")
    @DisplayName("A name matching two arrangements stops the run instead of choosing one")
    void stopsWhenTheNameMatchesTwo() {
        WalkId walk = aWalk();
        RunId first = anArrangement(walk);
        RunId second = anotherArrangementSharingThePrefixOf(first, walk);
        approve(first.value().substring(0, APPROVAL_LENGTH));

        claim(
                "with two arrangements answering to one name the run stops rather than picking either:"
                        + " guessing which one a person meant would mean generating over an arrangement"
                        + " nobody approved, and doing it invisibly",
                () -> assertThatThrownBy(() -> arrangementGate.approvedArrangement(walk))
                        .isInstanceOf(AmbiguousArrangementException.class)
                        .hasMessageContaining(first.value())
                        .hasMessageContaining(second.value()));
    }

    private void approve(String approval) {
        Profile loaded = profileStore.load();
        profileStore.save(ProfileFixture.profileFrom(loaded)
                .arrangementApproved(approval, approval == null ? null : "read by this test")
                .build());
    }

    private WalkId aWalk() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus-" + System.nanoTime()));
        ledger.fileOccurrence(
                walkId,
                new OccurrencePath("a.txt"),
                1,
                Instant.parse("2026-09-13T10:15:30Z"),
                Instant.parse("2026-09-01T08:00:00Z"));
        return walkId;
    }

    private RunId anArrangement(WalkId walkId) {
        return new Ledger(jdbcTemplate)
                .startRun(ArrangementRun.STAGE, "v" + System.nanoTime(), "{}", walkId, List.of());
    }

    /**
     * A second arrangement under the same walk whose id opens with the first's twelve characters.
     * Written straight into the table because a content-derived id cannot be steered into a collision,
     * and what is under test is what the gate does when it meets one — not how likely that is.
     */
    private RunId anotherArrangementSharingThePrefixOf(RunId first, WalkId walkId) {
        String colliding = first.value().substring(0, APPROVAL_LENGTH) + "f".repeat(8);
        jdbcTemplate.update(
                "INSERT INTO run (id, stage, implementation_version, config_consumed, walk_id)"
                        + " VALUES (?, ?, ?, ?, ?)",
                colliding,
                ArrangementRun.STAGE,
                "v-collision",
                "{}",
                walkId.value());
        return new RunId(colliding);
    }
}
