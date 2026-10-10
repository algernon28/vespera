package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.FloorReach;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
 * What the relevance threshold entitles a run to do (ADR-088, #112), as the step is answered it over the
 * profile and the answers on record: only a number on this run's own scale removes anything.
 *
 * <p><b>A threshold is a number on a scale, and the model is the scale.</b> The case that matters most
 * here is a floor read off one model's scores, offered to a run scored by another. Applying it would write
 * removals against a distribution it was never read off, which is the one failure in this stage that loses
 * part of the corpus rather than merely reporting badly.
 *
 * <p>Which scale a floor belongs to is read from the answers it is meant to have been read off, since
 * those carry the embedder identity that was on screen when the answer was given. A floor with no answers
 * behind it has no recorded scale to disagree with, and ADR-088 is explicit that nothing checks a
 * threshold was ever labelled.
 *
 * <p>Since ADR-226 the rule is {@code embedding}'s {@link FloorReach}, which {@code FloorReachTest} holds
 * apart from any database. {@code RelevanceFloor} hands it the number the run is identified by, read off
 * the profile, and the answers recorded for the seed folder; these claims hold the two together.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ProfileStore.class)
@Epic("Relevance")
@Feature("Relevance threshold")
@Issue("112")
@Issue("479")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
@Link(name = "ADR-226", url = Adr.NO_STAGE_NAMES_PIPELINE_AND_ITS_RULES_LIVE_IN_THE_CAPABILITY_MODULES, type = "adr")
class RelevanceFloorTest {

    private static final String THIS_RUNS_IDENTITY =
            "model=qwen3-embedding:0.6b;digest=d34db33f;dtype=F16;dimension=8;instruction=none";
    private static final String ANOTHER_MODELS_IDENTITY =
            "model=nomic-embed-text;digest=0ff0ff0f;dtype=F32;dimension=768;instruction=none";

    /**
     * The step the floor is asked by, whose name opens the lines around the one read the floor makes
     * (ADR-204 section 3). No claim here is about those lines; the floor's answer does not depend on it.
     */
    private static final String THE_STEP_THAT_ASKS = "Stage 5e (relevance floor)";

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path seedFolder;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProfileStore profileStore;

    private RelevanceFloor floor;

    @BeforeEach
    void floor() {
        floor = new RelevanceFloor(profileStore, new RelevanceLabels(jdbcTemplate));
    }

    @Test
    @Story("A floor with no number removes nothing and does not stop the run")
    @DisplayName("With no threshold answered, the floor removes nothing")
    void anUnansweredFloorRemovesNothing() {
        profileWithFloor(null);

        FloorReach reach = floor.reachFor(Optional.of(THIS_RUNS_IDENTITY), THE_STEP_THAT_ASKS);

        claim(
                "nothing is removed, which is what the key ships as: the run is what produces the data the"
                        + " threshold is read off, so there is nothing to apply on a first pass",
                () -> assertThat(reach.removesBelow()).isEmpty());
        claim(
                "and no number is there to name -- the floor is not a gate, and the run proceeds to score,"
                        + " cluster and report exactly as it would with one set",
                () -> assertThat(reach.floor()).isEmpty());
    }

    @Test
    @Story("A floor answered for under another model removes nothing, and the reason is stated")
    @DisplayName("A threshold read off another model's labels removes nothing")
    void aFloorAnsweredUnderAnotherModelRemovesNothing() {
        profileWithFloor("0.42");
        aLabelGivenUnder(ANOTHER_MODELS_IDENTITY);

        FloorReach reach = floor.reachFor(Optional.of(THIS_RUNS_IDENTITY), THE_STEP_THAT_ASKS);

        claim(
                "the number is set and still nothing is removed: a threshold is a number on a scale, the"
                        + " model is the scale, and applying it here would write removals against a"
                        + " distribution it was never read off",
                () -> assertThat(reach.removesBelow()).isEmpty());
        claim(
                "and the answer carries the identity the answers were given under and the number set, rather"
                        + " than merely refusing, so the report can say why a value an operator did set is"
                        + " being passed over -- a number passed over in silence reads as one the engine lost",
                () -> {
                    assertThat(reach.answeredUnder()).containsExactly(ANOTHER_MODELS_IDENTITY);
                    assertThat(reach.floor()).hasValue(0.42);
                });
    }

    @Test
    @Story("A floor on this run's own scale is the only one that removes")
    @DisplayName("A threshold read off this model's labels is applied")
    void aFloorAnsweredUnderThisModelIsApplied() {
        profileWithFloor("0.42");
        aLabelGivenUnder(THIS_RUNS_IDENTITY);

        FloorReach reach = floor.reachFor(Optional.of(THIS_RUNS_IDENTITY), THE_STEP_THAT_ASKS);

        claim(
                "the floor applies, and the number it removes below is the number the operator wrote: this is"
                        + " the one case in which stage 5 removes anything at all",
                () -> assertThat(reach.removesBelow()).hasValue(0.42));
    }

    @Test
    @Story("A floor on this run's own scale is the only one that removes")
    @DisplayName("A threshold nobody labelled is applied, because nothing checks that one was labelled")
    void anUnlabelledFloorIsApplied() {
        profileWithFloor("0.42");

        FloorReach reach = floor.reachFor(Optional.of(THIS_RUNS_IDENTITY), THE_STEP_THAT_ASKS);

        claim(
                "a floor with no answers behind it applies: ADR-088 is explicit that nothing checks a"
                        + " threshold was ever labelled, because provenance is free text and that is what"
                        + " provenance has been for since ADR-031. What is refused is not a floor nobody"
                        + " labelled but one whose answers were given under a different model",
                () -> assertThat(reach.removesBelow()).hasValue(0.42));
    }

    @Test
    @Story("A floor with no number removes nothing and does not stop the run")
    @DisplayName("A threshold that is not a number removes nothing rather than failing the run")
    void aThresholdThatIsNotANumberRemovesNothing() {
        profileWithFloor("about a half");

        FloorReach reach = floor.reachFor(Optional.of(THIS_RUNS_IDENTITY), THE_STEP_THAT_ASKS);

        claim(
                "the run is not lost to a typo in one key of a file a person edits by hand: ADR-047 says"
                        + " an invocation ends having recorded what it learned, and removing nothing is the"
                        + " safe reading of a value nobody can parse",
                () -> assertThat(reach.removesBelow()).isEmpty());
        claim(
                "and the floor handed over carries no number, the same reading the run's identity makes of it",
                () -> assertThat(reach.floor()).isEmpty());
    }

    @Test
    @Story("A floor answered for under another model removes nothing, and the reason is stated")
    @DisplayName("Labels spanning two models record no single scale, so the floor removes nothing")
    void labelsSpanningTwoModelsRemoveNothing() {
        profileWithFloor("0.42");
        aLabelGivenUnder(THIS_RUNS_IDENTITY);
        aLabelGivenUnder(ANOTHER_MODELS_IDENTITY);

        FloorReach reach = floor.reachFor(Optional.of(THIS_RUNS_IDENTITY), THE_STEP_THAT_ASKS);

        claim(
                "answers spanning two models are a half-repeated labelling, and the honest reading is that"
                        + " they record no single scale rather than whichever one was met first -- so"
                        + " nothing is removed, which is the direction that loses no part of the corpus",
                () -> assertThat(reach.removesBelow()).isEmpty());
    }

    @Test
    @Story("Where the vectors carry no single embedder identity, nothing is removed and nothing is withdrawn")
    @DisplayName("With no single embedder identity under the vectors, the floor neither removes nor withdraws")
    void noSingleIdentityNeitherRemovesNorWithdraws() {
        profileWithFloor("0.42");
        aLabelGivenUnder(THIS_RUNS_IDENTITY);

        FloorReach reach = floor.reachFor(Optional.empty(), THE_STEP_THAT_ASKS);

        claim(
                "nothing is removed: there is no one scale for the number to be on",
                () -> assertThat(reach.removesBelow()).isEmpty());
        claim(
                "and the removals the run already has stand, as they did before the rule moved: that case"
                        + " withdraws nothing either",
                () -> assertThat(reach.withdrawsStandingRemovals()).isFalse());
    }

    private void profileWithFloor(String value) {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seedFolder.toString(), "set by this test")
                .embeddingModel("qwen3-embedding:0.6b", "set by this test")
                .relevanceScoreFloor(value, value == null ? null : "read off the labels")
                .build());
    }

    /**
     * One answer given while {@code embedderIdentity} was in use, which is what puts a threshold on a
     * scale or not. A walk is minted only because a run is recorded against one; the answer itself is keyed
     * by the path and wants neither (ADR-097).
     */
    private void aLabelGivenUnder(String embedderIdentity) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus-" + System.nanoTime()));
        OccurrencePath path = new OccurrencePath("labelled-" + System.nanoTime() + ".txt");
        RunId runId = ledger.runs().startRun("embedding-scoring", "abc" + System.nanoTime(), "{}", walkId, List.of());
        new RelevanceLabels(jdbcTemplate)
                .record(path, Walk.canonicalRoot(seedFolder).toString(), true, runId, 0.9, embedderIdentity);
    }
}
