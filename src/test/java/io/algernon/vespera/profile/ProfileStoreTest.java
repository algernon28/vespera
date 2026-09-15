package io.algernon.vespera.profile;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The profile as a file a person edits and census merges into (ADR-061, ADR-062).
 *
 * <p>The asymmetry is the whole decision, so it is what these pin: an answer already in the file is
 * census's to leave alone, and census's own pointer to where the data lives is census's to refresh.
 * Getting that backwards would silently overwrite a judgement nobody can recompute.
 */
@Epic("Census")
@Feature("Profile")
@Issue("13")
@Link(name = "ADR-061", url = Adr.PROFILE_IS_YAML_TYPED_RECORDS, type = "adr")
@Link(name = "ADR-062", url = Adr.CENSUS_MERGES_AND_NEVER_OVERWRITES, type = "adr")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
class ProfileStoreTest {

    private static final Instant FIRST_RUN = Instant.parse("2026-08-29T10:15:30Z");
    private static final Instant SECOND_RUN = Instant.parse("2026-08-30T11:00:00Z");

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A corpus with no profile yet gets every key the code knows about, all unset")
    void writesACompleteSkeletonForAFreshCorpus(@TempDir Path workingDirectory) {
        ProfileStore store = new ProfileStore(workingDirectory);

        Profile loaded = store.load();
        store.save(loaded);

        claim(
                "the seed-folder key is present rather than missing, and unanswered rather than guessed",
                () -> assertThat(loaded.seedFolder().isSet()).isFalse());
        claim(
                "so is stage 2's tier-2 confidence floor -- unset per observe-before-enforce, not guessed at"
                        + " (ADR-070)",
                () -> assertThat(loaded.degenerateOutputConfidenceFloor().isSet()).isFalse());
        claim(
                "the file now exists, so an operator has something to edit",
                () -> assertThat(Files.exists(workingDirectory.resolve("profile.yaml"))).isTrue());
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A value and its provenance survive a second census run untouched")
    void neverTouchesAnAnswerAlreadyInTheFile(@TempDir Path workingDirectory) {
        ProfileStore store = new ProfileStore(workingDirectory);
        store.save(ProfileFixture.profile()
                .seedFolder(new ProfileValue(
                        "C:/seeds", "chosen by the archivist from the 2019 handover", new Measurement("walk 1", FIRST_RUN)))
                .build());

        Profile reloaded = store.load();
        store.save(reloaded.withSeedFolderMeasurement(new Measurement("walk 7", SECOND_RUN)));
        Profile afterTheSecondRun = store.load();

        claim(
                "the operator's value is exactly what it was before the second run",
                () -> assertThat(afterTheSecondRun.seedFolder().value()).isEqualTo("C:/seeds"));
        claim(
                "so is the provenance they wrote beside it",
                () -> assertThat(afterTheSecondRun.seedFolder().provenance())
                        .isEqualTo("chosen by the archivist from the 2019 handover"));
        claim(
                "and census's own pointer moved on to the walk the second run made",
                () -> assertThat(afterTheSecondRun.seedFolder().measurement())
                        .isEqualTo(new Measurement("walk 7", SECOND_RUN)));
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A key the file predates is added unset, without disturbing the file's answers")
    void addsAKeyTheFileDoesNotYetMention(@TempDir Path workingDirectory) throws IOException {
        // A profile written before the seed-folder key existed: no keys at all.
        Files.writeString(workingDirectory.resolve("profile.yaml"), "{}");
        ProfileStore store = new ProfileStore(workingDirectory);

        Profile loaded = store.load();

        claim(
                "the key the code has since learned about is present and unset, which is the same gated"
                        + " state as a key nobody has answered",
                () -> assertThat(loaded.seedFolder()).isEqualTo(ProfileValue.unset()));
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A key nobody recognises fails the load rather than being ignored")
    void refusesAKeyTheCodeDoesNotKnow(@TempDir Path workingDirectory) throws IOException {
        Files.writeString(workingDirectory.resolve("profile.yaml"), "seedFolders:\n  value: C:/seeds\n");
        ProfileStore store = new ProfileStore(workingDirectory);

        claim(
                "a misspelled key stops the load, rather than leaving the real key silently unset and the"
                        + " pipeline gating on it",
                () -> assertThatThrownBy(store::load).hasMessageContaining("seedFolders"));
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A profile written out is a profile that reads back the same")
    void writesWhatItCanReadBack(@TempDir Path workingDirectory) {
        ProfileStore store = new ProfileStore(workingDirectory);
        Profile written = ProfileFixture.profile()
                .seedFolder(new ProfileValue("C:/seeds", "the archivist's pick", new Measurement("walk 1", FIRST_RUN)))
                .build();

        store.save(written);

        claim(
                "the profile round-trips through YAML unchanged, timestamps included",
                () -> assertThat(store.load()).isEqualTo(written));
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("An operator-set tier-2 confidence floor round-trips like any other answered key")
    void anOperatorSetConfidenceFloorRoundTrips(@TempDir Path workingDirectory) {
        ProfileStore store = new ProfileStore(workingDirectory);
        Profile written = ProfileFixture.profile()
                .degenerateOutputConfidenceFloor(new ProfileValue(
                        "0.5", "matched to Docling's own poor/fair cut-off", new Measurement("run 3", FIRST_RUN)))
                .build();

        store.save(written);
        Profile reloaded = store.load();

        claim(
                "the operator's threshold value survives the round trip",
                () -> assertThat(reloaded.degenerateOutputConfidenceFloor().value()).isEqualTo("0.5"));
        claim(
                "and it now reads as set, the same isSet() check every other answered key uses",
                () -> assertThat(reloaded.degenerateOutputConfidenceFloor().isSet()).isTrue());
    }

    /**
     * Every key the profile carries, each answered with a value nothing else in this file uses, so that
     * a value arriving on the wrong key is visible rather than coincidentally right.
     */
    private static final String EVERY_KEY_ANSWERED =
            """
            seedFolder:
              value: "C:/seeds"
              provenance: "the archivist's pick"
            degenerateOutputConfidenceFloor:
              value: "0.51"
              provenance: "read off the confidence distribution"
            boilerplateDocumentFrequencyFloor:
              value: "0.52"
              provenance: "read off the document-frequency table"
            embeddingModel:
              value: "an-embedding-model:0.6b"
              provenance: "the one the bake-off picked"
            relevanceScoreFloor:
              value: "0.53"
              provenance: "read off sixty answers"
            arrangementApproved:
              value: "9f2c41ab77de"
              provenance: "read and approved on screen"
            generationModel:
              value: "a-generation-model:8b"
              provenance: "overriding what is configured"
            """;

    /** How many keys the profile carries, and therefore how many the file above answers. */
    private static final int SEVEN_KEYS = 7;

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A file answering every key loads with every answer on the key that carried it")
    @Issue("197")
    @Link(name = "ADR-119", url = Adr.PROFILE_HAS_ONE_CONSTRUCTOR, type = "adr")
    void everyAnswerLandsOnTheKeyThatCarriedIt(@TempDir Path workingDirectory) throws IOException {
        Files.writeString(workingDirectory.resolve("profile.yaml"), EVERY_KEY_ANSWERED);

        Profile loaded = new ProfileStore(workingDirectory).load();

        claim(
                "all 7 answers are present, which no reader that built the record any other way than"
                        + " through its one whole-record constructor could produce -- a shorter one has no"
                        + " parameter for the last key, and a strict reader would refuse the file over it",
                () -> assertThat(List.of(
                                loaded.seedFolder().value(),
                                loaded.degenerateOutputConfidenceFloor().value(),
                                loaded.boilerplateDocumentFrequencyFloor().value(),
                                loaded.embeddingModel().value(),
                                loaded.relevanceScoreFloor().value(),
                                loaded.arrangementApproved().value(),
                                loaded.generationModel().value()))
                        .hasSize(SEVEN_KEYS)
                        .doesNotContainNull());
        claim(
                "and each one sits on the key the file wrote it under, so no reader silently shifted the"
                        + " values along by a position",
                () -> assertThat(loaded.relevanceScoreFloor().value()).isEqualTo("0.53"));
        claim(
                "including the last key, which is the one a shorter way in could not have filled",
                () -> assertThat(loaded.generationModel().value()).isEqualTo("a-generation-model:8b"));
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A file answering one key loads with every other key unanswered")
    @Issue("197")
    @Link(name = "ADR-119", url = Adr.PROFILE_HAS_ONE_CONSTRUCTOR, type = "adr")
    void aFileAnsweringOneKeyLeavesTheRestUnanswered(@TempDir Path workingDirectory) throws IOException {
        Files.writeString(
                workingDirectory.resolve("profile.yaml"),
                """
                seedFolder:
                  value: "C:/seeds"
                  provenance: "the archivist's pick"
                """);

        Profile loaded = new ProfileStore(workingDirectory).load();

        claim(
                "the key the file answers is answered",
                () -> assertThat(loaded.seedFolder().value()).isEqualTo("C:/seeds"));
        claim(
                "and every key written since that file was saved is present and unanswered, rather than"
                        + " null or missing -- which is the promise a reader has to keep whatever it builds"
                        + " the record through, and the one thing a shorter way in was ever standing in for",
                () -> assertThat(loaded.generationModel()).isEqualTo(ProfileValue.unset()));
        claim(
                "so the file still loads at all, on a reader that refuses a key it does not recognise",
                () -> assertThat(loaded.arrangementApproved()).isEqualTo(ProfileValue.unset()));
    }
}
