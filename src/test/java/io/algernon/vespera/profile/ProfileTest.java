package io.algernon.vespera.profile;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The profile record itself, apart from {@link ProfileStoreTest}'s file round-trip: what a fresh
 * skeleton carries for the key #58 adds, and that a two-key call site the key predates still compiles
 * and still merges the third key in unset (ADR-062's "a key the file predates is added unset,"
 * exercised here at the constructor rather than through YAML).
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
    @Story("A key the file predates is added unset")
    @DisplayName("The two-key constructor every call site before #58 used still defaults the third key unset")
    void theTwoKeyConstructorDefaultsTheThirdKeyUnset() {
        Profile profile = new Profile(ProfileValue.unset(), ProfileValue.unset());

        claim(
                "a call site that has not been touched since #58 still gets a profile whose third key"
                        + " reads exactly like any other key nobody has answered",
                () -> assertThat(profile.boilerplateDocumentFrequencyFloor()).isEqualTo(ProfileValue.unset()));
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
    @Story("A key the file predates is added unset")
    @DisplayName("The three-key constructor every call site before #107 used still defaults the fourth key unset")
    @Issue("107")
    void theThreeKeyConstructorDefaultsTheFourthKeyUnset() {
        Profile profile = new Profile(ProfileValue.unset(), ProfileValue.unset(), ProfileValue.unset());

        claim(
                "a call site that has not been touched since #107 still gets a profile whose fourth key"
                        + " reads exactly like any other key nobody has answered",
                () -> assertThat(profile.embeddingModel()).isEqualTo(ProfileValue.unset()));
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
    @Story("A key the file predates is added unset")
    @DisplayName("The four-key constructor every call site before #110 used still defaults the fifth key unset")
    @Issue("110")
    void theFourKeyConstructorDefaultsTheFifthKeyUnset() {
        Profile profile =
                new Profile(ProfileValue.unset(), ProfileValue.unset(), ProfileValue.unset(), ProfileValue.unset());

        claim(
                "a call site that has not been touched since #110 still gets a profile whose fifth key"
                        + " reads exactly like any other key nobody has answered",
                () -> assertThat(profile.relevanceScoreFloor()).isEqualTo(ProfileValue.unset()));
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
}
