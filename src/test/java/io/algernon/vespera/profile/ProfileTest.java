package io.algernon.vespera.profile;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The profile record itself, apart from {@link ProfileStoreTest}'s file round-trip: what a fresh
 * skeleton carries for each key as it was added, and the shape of the record's one way in.
 *
 * <p>The four tests that used to sit here — one per arity, each asserting that a call site untouched
 * since an earlier ticket still got the later key unset — went with the constructors they were about
 * (ADR-119). What they claimed is claimed still: through the file, by {@link ProfileStoreTest}, which
 * is where ADR-062's merge actually lives, and through {@link ProfileFixture} below, which is where
 * the source compatibility they were really defending now sits.
 */
@Epic("Census")
@Feature("Profile")
@Issue("58")
@Link(name = "ADR-074", url = Adr.STAGE_3_MEASURES_SHINGLE_DOCUMENT_FREQUENCY, type = "adr")
@Link(name = "ADR-062", url = Adr.CENSUS_MERGES_AND_NEVER_OVERWRITES, type = "adr")
class ProfileTest {

    @Test
    @Story("Stage 3's boilerplate floor ships unset")
    @DisplayName("A fresh skeleton carries the boilerplate-document-frequency-floor key, unset")
    void skeletonCarriesTheBoilerplateFloorUnset() {
        Profile skeleton = Profile.skeleton();

        claim(
                "the key #58 adds is present rather than missing, and unanswered rather than guessed at"
                        + " -- stage 3 only measures document frequency, nothing here applies a floor yet",
                () -> assertThat(skeleton.boilerplateDocumentFrequencyFloor().isSet()).isFalse());
    }

    @Test
    @Story("Gate 3's model key ships unset")
    @DisplayName("A fresh skeleton carries the embedding-model key, unset")
    @Issue("107")
    void skeletonCarriesTheEmbeddingModelKeyUnset() {
        Profile skeleton = Profile.skeleton();

        claim(
                "the model key #107 adds is present rather than missing, and unanswered rather than"
                        + " guessed at -- naming a model is purely the operator's call",
                () -> assertThat(skeleton.embeddingModel().isSet()).isFalse());
    }

    @Test
    @Story("Stage 5's relevance floor ships unset")
    @DisplayName("A fresh skeleton carries the relevance-score-floor key, unset")
    @Issue("110")
    @Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
    void skeletonCarriesTheRelevanceScoreFloorUnset() {
        Profile skeleton = Profile.skeleton();

        claim(
                "the floor #110 adds is present rather than missing, and unanswered rather than guessed"
                        + " at -- the value is read off sixty answers a person has not given yet, and"
                        + " while it is unset the run scores everything and removes nothing",
                () -> assertThat(skeleton.relevanceScoreFloor().isSet()).isFalse());
    }

    @Test
    @Story("Stage 5's relevance floor ships unset")
    @DisplayName("The floor points at the labelling report, so the person setting it knows what to read")
    @Issue("110")
    @Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
    void theFloorPointsAtTheLabellingReport() {
        Measurement labellingReport = new Measurement("relevance-labelling.html", java.time.Instant.parse("2026-09-09T10:00:00Z"));

        Profile pointed = Profile.skeleton().withRelevanceScoreFloorMeasurement(labellingReport);

        claim(
                "the key names the page the number is read off, which is the whole of what tells an"
                        + " operator where a threshold is supposed to come from",
                () -> assertThat(pointed.relevanceScoreFloor().measurement()).isEqualTo(labellingReport));
        claim(
                "pointing at the measurement does not answer the key: the value stays unset until a"
                        + " person writes one, because nothing may guess a threshold",
                () -> assertThat(pointed.relevanceScoreFloor().isSet()).isFalse());
    }

    @Test
    @Story("The arrangement approval ships unset")
    @DisplayName("A fresh skeleton carries the arrangement-approved key, unset")
    @Issue("175")
    @Link(name = "ADR-107", url = Adr.THE_ARRANGEMENT_GATE_APPROVES_A_NAMED_RUN, type = "adr")
    void skeletonCarriesTheArrangementApprovedKeyUnset() {
        Profile skeleton = Profile.skeleton();

        claim(
                "the approval is present as a question rather than missing, and unanswered rather than"
                        + " assumed -- nothing may be written over an arrangement nobody has looked at, and"
                        + " an approval that defaulted to yes would be exactly that",
                () -> assertThat(skeleton.arrangementApproved().isSet()).isFalse());
    }

    /**
     * How many constructors {@link Profile} is allowed to have: one, the canonical seven-component
     * one. Before ADR-119 there were six — the canonical one plus five of arity two through six, each
     * added by the ticket that added a profile key so that the previous ticket's test call sites would
     * still compile. None of the five had a production caller.
     */
    private static final int ONE_CONSTRUCTOR = 1;

    /** How many keys the profile carries today, and therefore how many the one constructor takes. */
    private static final int SEVEN_KEYS = 7;

    @Test
    @Story("The profile record has one way in")
    @DisplayName("The profile record declares a single constructor, taking every key")
    @Issue("197")
    @Link(name = "ADR-119", url = Adr.PROFILE_HAS_ONE_CONSTRUCTOR, type = "adr")
    void theRecordDeclaresASingleConstructor() {
        Constructor<?>[] constructors = Profile.class.getDeclaredConstructors();

        claim(
                "there is exactly 1 constructor, down from the 6 that had accumulated -- one per key added"
                        + " since the record shipped, every parameter the same type, and none of them"
                        + " reachable from anything but a test",
                () -> assertThat(constructors).hasSize(ONE_CONSTRUCTOR));
        claim(
                "and it takes all 7 keys, so a caller wanting a subset says which subset by naming the keys"
                        + " rather than by choosing a length",
                () -> assertThat(constructors[0].getParameterCount()).isEqualTo(SEVEN_KEYS));
    }

    @Test
    @Story("The profile record has one way in")
    @DisplayName("A key nobody names is unanswered, so a new key disturbs no existing test")
    @Issue("197")
    @Link(name = "ADR-119", url = Adr.PROFILE_HAS_ONE_CONSTRUCTOR, type = "adr")
    void aKeyNobodyNamesIsUnanswered() {
        Profile built = ProfileFixture.profile()
                .seedFolder("/corpus/exemplars", "chosen by the archivist")
                .build();

        claim(
                "the key that was named carries the answer it was given",
                () -> assertThat(built.seedFolder().value()).isEqualTo("/corpus/exemplars"));
        claim(
                "and every key that was not named is unanswered rather than absent or guessed -- which is"
                        + " what lets a new key be added without touching a call site, the one thing the"
                        + " deleted constructors were buying",
                () -> assertThat(built.generationModel()).isEqualTo(ProfileValue.unset()));
        claim(
                "and so is the key that sits between them, because this is a property of every key the"
                        + " caller left alone rather than of the last one",
                () -> assertThat(built.relevanceScoreFloor()).isEqualTo(ProfileValue.unset()));
    }
}
