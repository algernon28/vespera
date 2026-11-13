package io.algernon.vespera.engine.profile;

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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.provider.Arguments;

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
    @Issue("558")
    @Issue("571")
    @Issue("575")
    @Issue("576")
    @Link(name = "ADR-256", url = Adr.NAME_ENDINGS_THE_PROFILE_LISTS_ARE_OUT_OF_SCOPE, type = "adr")
    @Link(name = "ADR-277", url = Adr.CODE_IS_RECOGNISED_BY_A_NAME_ENDING_OR_A_GENERATORS_MARK, type = "adr")
    @Link(name = "ADR-282", url = Adr.FEWER_WORDS_THAN_THE_WORD_FLOOR_IS_DEGENERATE_OUTPUT, type = "adr")
    @Link(name = "ADR-287", url = Adr.LOGS_AND_CONFIGURATION_FILES_ARE_TOLD_BY_THEIR_BYTES, type = "adr")
    @Link(name = "ADR-303", url = Adr.A_CLUSTERS_PAGE_PAST_THE_CEILING_IS_SPLIT_INTO_PAGES, type = "adr")
    @Link(name = "ADR-384", url = Adr.WHETHER_THE_MODELS_RUN_LOCALLY_OR_IN_THE_CLOUD_IS_THE_PROFILES_TO_SAY, type = "adr")
    void writesACompleteSkeletonForAFreshCorpus(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);

        Profile loaded = store.load();
        store.save(loaded);

        claim(
                "every key the profile carries is present and unanswered -- no value, no provenance, no pointer --"
                        + " so no threshold is guessed at, no page is split and nothing is removed until the operator"
                        + " writes a value. The keys are read off the record, so a key added later is held here too",
                () -> assertThat(ProfileKeys.valuesOf(loaded))
                        .allSatisfy((key, value) -> assertThat(
                                        Arrays.asList(value.value(), value.provenance(), value.measurement()))
                                .as("%s is unanswered", key)
                                .containsOnlyNulls()));
        claim(
                "the deliverable's index states every one of them, empty",
                () -> assertThat(loaded.keysAsWritten().values()).containsOnly(""));
        List<String> lines = Files.readAllLines(store.file());
        claim(
                "and the file census writes names every key, in the record's order, so an operator has each one to"
                        + " fill in, but where the models run, which it writes as a comment",
                () -> assertThat(topLevelKeys(lines)).containsExactlyElementsOf(ProfileKeys.everyKey().stream()
                        .filter(key -> !key.equals(WHERE_THE_MODELS_RUN))
                        .toList()));
        claim(
                "where the models run stands in its place, right after the writing model, as three lines of comment: what"
                        + " it is, and the key with its one value supported, which uncommented reads as written",
                () -> assertThat(lines.subList(lines.indexOf("generationContextWindow:") - 3,
                                lines.indexOf("generationContextWindow:")))
                        .containsExactly(THE_COMMENTED_KEY));
    }

    /** The key ADR-384 adds, which the file holds as a comment while it is unset. */
    private static final String WHERE_THE_MODELS_RUN = "modelsRunOn";

    /** The three lines the file holds in its place while it is unset (ADR-384 §6). */
    private static final String[] THE_COMMENTED_KEY = {
        "# Where the models run: local, on this machine. Cloud is not supported yet.",
        "# modelsRunOn:",
        "#   value: local"
    };

    /** The keys a {@code profile.yaml} names at its top level, in the order it names them. */
    private static List<String> topLevelKeys(List<String> lines) {
        return lines.stream()
                .filter(line -> line.matches("[A-Za-z]+:.*"))
                .map(line -> line.substring(0, line.indexOf(':')))
                .toList();
    }

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A value and its provenance survive a second census run untouched")
    void neverTouchesAnAnswerAlreadyInTheFile(@TempDir Path workingDirectory) {
        ProfileStore store = new ProfileStore(workingDirectory);
        store.save(ProfileFixture.profile()
                .seedFolder(new TextValue(
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
                .seedFolder(new TextValue("C:/seeds", "the archivist's pick", new Measurement("walk 1", FIRST_RUN)))
                .build();

        store.save(written);

        claim(
                "the profile round-trips through YAML unchanged, timestamps included",
                () -> assertThat(store.load()).isEqualTo(written));
    }

    /** The words the operator gives for the word floor they wrote. */
    private static final String WHY_TWO_WORDS = "read off word-count-distribution.html: a floor of 2 removes 40 images";

    /**
     * One key answered as an operator may write it, each value as a person types it: a trailing nought a
     * value normalised on its way would lose, endings with their case, spacing and comma, and a floor with
     * a decimal comma no number is read from, which loads all the same.
     */
    static Stream<Arguments> answersAsWritten() {
        return Stream.of(
                Arguments.of(
                        Named.of("the confidence floor", "degenerateOutputConfidenceFloor"),
                        "0.5",
                        "matched to Docling's own poor/fair cut-off"),
                Arguments.of(Named.of("the word floor", "degenerateOutputWordFloor"), "2", WHY_TWO_WORDS),
                Arguments.of(
                        Named.of("the page ceiling, with a trailing nought", "clusterPageByteCeiling"),
                        "300000.0",
                        "read off cluster-page-sizes.html"),
                Arguments.of(
                        Named.of("the name endings kept as code, with their case, spacing and comma", "codeNameEndings"),
                        ".java  .C, .h",
                        "the archive holds the code of the terminals"),
                Arguments.of(
                        Named.of("the name endings left out, with their case, spacing and comma", "outOfScopeNameEndings"),
                        ".P3A  .p3l, .DGN",
                        "the terminal files are not documents"),
                Arguments.of(
                        Named.of(
                                "the floor on the share of settings, with a trailing nought",
                                "configurationSettingShareFloor"),
                        "0.80",
                        "read off the page"),
                Arguments.of(
                        Named.of(
                                "a floor on the share of settings with a decimal comma, from which no number is read",
                                "configurationSettingShareFloor"),
                        "0,8",
                        "typed in a hurry"));
    }

    /** The value {@link #EVERY_KEY_ANSWERED} writes under each key, copied from it. */
    private static final Map<String, String> EVERY_KEY_AS_ANSWERED = Map.ofEntries(
            Map.entry("seedFolder", "C:/seeds"),
            Map.entry("degenerateOutputConfidenceFloor", "0.51"),
            Map.entry("degenerateOutputWordFloor", "6"),
            Map.entry("boilerplateDocumentFrequencyFloor", "0.52"),
            Map.entry("embeddingModel", "an-embedding-model:0.6b"),
            Map.entry("relevanceScoreFloor", "0.53"),
            Map.entry("arrangementApproved", "9f2c41ab77de"),
            Map.entry("generationModel", "a-generation-model:8b"),
            Map.entry("generationContextWindow", "4097"),
            Map.entry("clusterPageByteCeiling", "4098"),
            Map.entry("logTimestampShareFloor", "0.54"),
            Map.entry("extractionAttempt", "3"),
            Map.entry("codeNameEndings", ".C .Java"),
            Map.entry("outOfScopeNameEndings", ".P3A .DGN"),
            Map.entry("translationSimilarityFloor", "0.55"),
            Map.entry("preferredLanguages", "it, en"),
            Map.entry("configurationSettingShareFloor", "0.56"),
            Map.entry("deliverableLanguage", "it"),
            Map.entry("clusterResolution", "2.5"),
            Map.entry("foldersNotWalked", "archive/old-flows"),
            Map.entry("confluenceBaseUrl", "https://wiki.example.com"),
            Map.entry("confluenceSpaceKey", "DOCS"),
            Map.entry("confluenceFolderId", "123456789"),
            Map.entry("confluenceFolderTitle", "Hand-overs"),
            Map.entry("confluenceEmail", "operator@example.org"));
}
