package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
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
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
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
 * What the relevance threshold entitles a run to do (ADR-088, #112): three states, and only one of
 * them removes anything.
 *
 * <p><b>A threshold is a number on a scale, and the model is the scale.</b> The state that matters
 * most here is the middle one — a floor read off one model's scores, offered to a run scored by
 * another. Applying it would write removals against a distribution it was never calibrated on, which
 * is the one failure in this stage that loses archive rather than merely reporting badly.
 *
 * <p>Which scale a floor belongs to is read from the labels it is meant to have been read off, since
 * those carry the embedder identity that was on screen when the answer was given. A floor with no
 * labels behind it has no recorded scale to disagree with, and ADR-088 is explicit that nothing
 * checks a threshold was ever labelled.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ProfileStore.class)
@Epic("Relevance")
@Feature("Relevance threshold")
@Issue("112")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
class RelevanceFloorTest {

    private static final String THIS_RUNS_IDENTITY =
            "model=qwen3-embedding:0.6b;digest=d34db33f;dtype=F16;dimension=8;instruction=none";
    private static final String ANOTHER_MODELS_IDENTITY =
            "model=nomic-embed-text;digest=0ff0ff0f;dtype=F32;dimension=768;instruction=none";

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
    @Story("An unset floor removes nothing and does not stop the run")
    @DisplayName("With no threshold answered, the floor removes nothing")
    void anUnansweredFloorRemovesNothing() {
        profileWithFloor(null);

        RelevanceFloor.State state = floor.stateFor(THIS_RUNS_IDENTITY);

        claim(
                "the state is unset, which is what the key ships as: the run is what produces the data"
                        + " the threshold is calibrated from, so there is nothing to apply on a first pass",
                () -> assertThat(state).isInstanceOf(RelevanceFloor.Unset.class));
        claim(
                "and nothing is removed -- an unset floor is not a gate, and the run proceeds to score,"
                        + " cluster and report exactly as it would with one set",
                () -> assertThat(state.removesAnything()).isFalse());
    }

    @Test
    @Story("A floor calibrated against another model is ignored, and the reason is stated")
    @DisplayName("A threshold read off another model's labels removes nothing")
    void aFloorCalibratedAgainstAnotherModelRemovesNothing() {
        profileWithFloor("0.42");
        aLabelGivenUnder(ANOTHER_MODELS_IDENTITY);

        RelevanceFloor.State state = floor.stateFor(THIS_RUNS_IDENTITY);

        claim(
                "the number is set and still nothing is removed: a threshold is a number on a scale, the"
                        + " model is the scale, and applying it here would write deletions against a"
                        + " distribution it was never calibrated on",
                () -> assertThat(state.removesAnything()).isFalse());
        claim(
                "and the state carries both identities rather than merely refusing, so the report can say"
                        + " why a value an operator did set is being ignored -- a number silently skipped"
                        + " reads as an engine that lost it",
                () -> assertThat(state)
                        .asInstanceOf(InstanceOfAssertFactories.type(RelevanceFloor.CalibratedElsewhere.class))
                        .satisfies(elsewhere -> {
                            assertThat(elsewhere.calibratedUnder()).isEqualTo(ANOTHER_MODELS_IDENTITY);
                            assertThat(elsewhere.currentIdentity()).isEqualTo(THIS_RUNS_IDENTITY);
                            assertThat(elsewhere.value()).isEqualTo(0.42);
                        }));
    }

    @Test
    @Story("A floor on this run's own scale is the only state that removes")
    @DisplayName("A threshold read off this model's labels is applied")
    void aFloorCalibratedHereIsApplied() {
        profileWithFloor("0.42");
        aLabelGivenUnder(THIS_RUNS_IDENTITY);

        RelevanceFloor.State state = floor.stateFor(THIS_RUNS_IDENTITY);

        claim(
                "the floor applies, and its value is the number the operator wrote: this is the one state"
                        + " in which stage 5 removes anything at all",
                () -> assertThat(state).isEqualTo(new RelevanceFloor.Applicable(0.42)));
        claim("and it says so", () -> assertThat(state.removesAnything()).isTrue());
    }

    @Test
    @Story("A floor on this run's own scale is the only state that removes")
    @DisplayName("A threshold nobody labelled is applied, because nothing checks that one was labelled")
    void anUnlabelledFloorIsApplied() {
        profileWithFloor("0.42");

        RelevanceFloor.State state = floor.stateFor(THIS_RUNS_IDENTITY);

        claim(
                "a floor with no labels behind it applies: ADR-088 is explicit that nothing checks a"
                        + " threshold was ever labelled, because provenance is free text and that is what"
                        + " provenance has been for since ADR-031. What is refused is not an uncalibrated"
                        + " floor but one whose recorded calibration was against a different model",
                () -> assertThat(state).isEqualTo(new RelevanceFloor.Applicable(0.42)));
    }

    @Test
    @Story("An unset floor removes nothing and does not stop the run")
    @DisplayName("A threshold that is not a number reads as unset rather than failing the run")
    void aThresholdThatIsNotANumberReadsAsUnset() {
        profileWithFloor("about a half");

        RelevanceFloor.State state = floor.stateFor(THIS_RUNS_IDENTITY);

        claim(
                "the run is not lost to a typo in one key of a file a person edits by hand: ADR-047 says"
                        + " an invocation ends having recorded what it learned, and removing nothing is the"
                        + " safe reading of a value nobody can parse",
                () -> assertThat(state).isInstanceOf(RelevanceFloor.Unset.class));
    }

    @Test
    @Story("A floor calibrated against another model is ignored, and the reason is stated")
    @DisplayName("Labels spanning two models record no single scale, so the floor removes nothing")
    void labelsSpanningTwoModelsRemoveNothing() {
        profileWithFloor("0.42");
        aLabelGivenUnder(THIS_RUNS_IDENTITY);
        aLabelGivenUnder(ANOTHER_MODELS_IDENTITY);

        RelevanceFloor.State state = floor.stateFor(THIS_RUNS_IDENTITY);

        claim(
                "answers spanning two models are a half-re-ingested pass, and the honest reading is that"
                        + " they record no single scale rather than whichever one was encountered first --"
                        + " so nothing is removed, which is the direction that loses no archive",
                () -> assertThat(state.removesAnything()).isFalse());
    }

    private void profileWithFloor(String value) {
        profileStore.save(new Profile(
                new ProfileValue(seedFolder.toString(), "set by this test", null),
                new ProfileValue(null, null, null),
                new ProfileValue(null, null, null),
                new ProfileValue("qwen3-embedding:0.6b", "set by this test", null),
                value == null ? new ProfileValue(null, null, null) : new ProfileValue(value, "read off the labels", null)));
    }

    /**
     * One answer given while {@code embedderIdentity} was in use, which is what makes a threshold
     * calibrated or not. A walk is minted only because a run is recorded against one; the answer
     * itself is keyed by the path and wants neither (ADR-097).
     */
    private void aLabelGivenUnder(String embedderIdentity) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus-" + System.nanoTime()));
        OccurrencePath path = new OccurrencePath("labelled-" + System.nanoTime() + ".txt");
        RunId runId = ledger.startRun("embedding-scoring", "abc" + System.nanoTime(), "{}", walkId, List.of());
        new RelevanceLabels(jdbcTemplate)
                .record(path, Walk.canonicalRoot(seedFolder).toString(), true, runId, 0.9, embedderIdentity);
    }
}
