package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.WalkCounts;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
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
 * Every condition that closes the seed gate, one at a time (ADR-064, ADR-080, ADR-089).
 *
 * <p>Tested here rather than through an invocation, because two of these cannot be staged end to end
 * at all: census walks the seed folder on every run and finishes it, so an <em>unfinished</em> seed
 * walk cannot survive a full invocation to be observed. The gate is the object that decides, so the
 * gate is where each condition is pinned; {@code SeedExtractionInvocationTest} then covers what a
 * closed gate means for the job as a whole.
 *
 * <p>The redundancy-floor condition is the one worth reading the reasoning for, since it is not one of
 * the three conditions the hand-off spec lists. Stage 5's measurement run names stage 4's run as the
 * one before it (ADR-089), and {@code run_upstream.upstream_run_id} is a foreign key — so while the
 * boilerplate floor is unset there is no row for it to point at, and a stage-5 run could only exist by
 * misstating its own ancestry. It also matches the delivered sequence: the invocation that supplies the
 * boilerplate floor is the one that extracts the seed set.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({SeedGate.class, RedundancyGate.class, ProfileStore.class, Ledger.class})
@Epic("Relevance")
@Feature("Seed set")
@Issue("104")
@Link(name = "ADR-064", url = Adr.THE_WALK_INSTRUMENT_GENERALIZES, type = "adr")
@Link(name = "ADR-080", url = Adr.THE_BOILERPLATE_FLOOR_IS_A_GATE, type = "adr")
@Link(name = "ADR-089", url = Adr.A_RUN_NAMES_ITS_IMMEDIATE_PREDECESSOR_UPSTREAM, type = "adr")
class SeedGateTest {

    @TempDir
    static Path workingDirectory;

    /** The least aggressive floor that still counts as answered. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private SeedGate seedGate;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private Ledger ledger;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("The gate opens only when everything it needs is there")
    @DisplayName("A named seed folder with a finished walk, and the earlier floor answered, opens the gate")
    void opensWhenTheFolderIsNamedItsWalkFinishedAndTheEarlierFloorAnswered(@TempDir Path seeds) {
        profile(seeds.toString(), BOILERPLATE_FLOOR);
        WalkId walkId = finishedWalkOf(seeds);

        claim(
                "the gate opens and hands back the finished walk of the folder the operator named,"
                        + " together with the root to resolve its documents against -- a walk id alone"
                        + " cannot reach a file, because a document's path is stored relative to the root"
                        + " it was walked from",
                () -> assertThat(seedGate.seedWalk())
                        .contains(new SeedGate.SeedWalk(walkId, Walk.canonicalRoot(seeds))));
    }

    @Test
    @Story("A gate closes rather than failing")
    @DisplayName("A seed folder whose walk has not finished closes the gate")
    void closesWhenTheSeedWalkHasNotFinished(@TempDir Path seeds) {
        profile(seeds.toString(), BOILERPLATE_FLOOR);
        ledger.startWalk(Walk.canonicalRoot(seeds));

        claim(
                "an unfinished walk closes the gate exactly as an unnamed folder does. A walk that has"
                        + " not finished may hold fewer documents than the folder holds files, and a seed"
                        + " set quietly missing entries would score every document against the wrong set,"
                        + " silently, for the rest of the run",
                () -> assertThat(seedGate.seedWalk()).isEmpty());
    }

    @Test
    @Story("A gate closes rather than failing")
    @DisplayName("No seed folder named closes the gate, however much else is answered")
    void closesWhenNoSeedFolderIsNamed() {
        profile(null, BOILERPLATE_FLOOR);

        claim(
                "with no folder named there is nothing to open the gate onto -- an archive can be"
                        + " surveyed and deduplicated before anybody has decided what the seed set is",
                () -> assertThat(seedGate.seedWalk()).isEmpty());
    }

    @Test
    @Story("A gate closes rather than failing")
    @DisplayName("A seed folder that names nothing on disk closes the gate rather than raising")
    void closesWhenTheNamedFolderCannotBeResolved() {
        profile(workingDirectory.resolve("no-such-folder").toString(), BOILERPLATE_FLOOR);

        claim(
                "a mistyped folder closes the gate instead of failing the invocation: the survey that ran"
                        + " earlier already recorded why it could not walk that path, and raising here"
                        + " would report one typo twice",
                () -> assertThat(seedGate.seedWalk()).isEmpty());
    }

    @Test
    @Story("A gate closes rather than failing")
    @DisplayName("The earlier pass's own unanswered floor closes this gate too")
    void closesWhileTheEarlierFloorIsUnanswered(@TempDir Path seeds) {
        profile(seeds.toString(), null);
        finishedWalkOf(seeds);

        claim(
                "with the earlier pass's floor unanswered this gate stays shut, even though the seed"
                        + " folder is named and its walk has finished. This run records the run before it as"
                        + " its predecessor, and that reference has to point at a row that exists -- while"
                        + " the earlier pass is itself waiting for a value, there is no such row, and a run"
                        + " here could only exist by misstating what it was derived from",
                () -> assertThat(seedGate.seedWalk()).isEmpty());
    }

    /** The two keys this gate reads, either of them {@code null} for unanswered. */
    private void profile(String seedFolder, String boilerplateFloor) {
        profileStore.save(new Profile(
                new ProfileValue(seedFolder, seedFolder == null ? null : "set by this test", null),
                profileStore.load().degenerateOutputConfidenceFloor(),
                new ProfileValue(boilerplateFloor, boilerplateFloor == null ? null : "set by this test", null)));
    }

    /** A walk of {@code root} that census would have finished. */
    private WalkId finishedWalkOf(Path root) {
        WalkId walkId = ledger.startWalk(Walk.canonicalRoot(root));
        ledger.finishWalk(walkId, new WalkCounts(0, 1));
        return walkId;
    }
}
