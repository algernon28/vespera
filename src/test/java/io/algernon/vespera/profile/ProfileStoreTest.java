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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
        claim(
                "and the file census writes names every key, in the record's order, so an operator has each one to"
                        + " fill in",
                () -> assertThat(topLevelKeys(Files.readAllLines(store.file())))
                        .containsExactlyElementsOf(ProfileKeys.everyKey()));
    }

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("answersAsWritten")
    @Story("What census writes to the profile")
    @DisplayName("An answer the operator writes is read and written back as written, on its own key")
    @Issue("558")
    @Issue("571")
    @Issue("575")
    @Issue("576")
    @Link(name = "ADR-256", url = Adr.NAME_ENDINGS_THE_PROFILE_LISTS_ARE_OUT_OF_SCOPE, type = "adr")
    @Link(name = "ADR-277", url = Adr.CODE_IS_RECOGNISED_BY_A_NAME_ENDING_OR_A_GENERATORS_MARK, type = "adr")
    @Link(name = "ADR-282", url = Adr.FEWER_WORDS_THAN_THE_WORD_FLOOR_IS_DEGENERATE_OUTPUT, type = "adr")
    @Link(name = "ADR-287", url = Adr.LOGS_AND_CONFIGURATION_FILES_ARE_TOLD_BY_THEIR_BYTES, type = "adr")
    @Link(name = "ADR-303", url = Adr.A_CLUSTERS_PAGE_PAST_THE_CEILING_IS_SPLIT_INTO_PAGES, type = "adr")
    void anAnswerIsReadAndWrittenBackAsWritten(String key, String asWritten, String why, @TempDir Path workingDirectory)
            throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(store.file(), key + ":\n  value: \"" + asWritten + "\"\n  provenance: \"" + why + "\"\n");

        Profile loaded = store.load();
        store.save(loaded);
        Profile reloaded = new ProfileStore(workingDirectory).load();
        ProfileValue answer = ProfileKeys.valuesOf(reloaded).get(key);

        claim(
                "the value and its provenance are the operator's, as written, after a load and a save",
                () -> assertThat(List.of(answer.value(), answer.provenance())).containsExactly(asWritten, why));
        claim(
                "and the key reads as answered, the same check every answered key passes",
                () -> assertThat(answer.isSet()).isTrue());
        claim(
                "the deliverable's index states it as written, before the save and after it",
                () -> assertThat(List.of(loaded.keysAsWritten().get(key), reloaded.keysAsWritten().get(key)))
                        .containsExactly(asWritten, asWritten));
        claim(
                "and every other key is left unanswered: the answer landed on its own key",
                () -> assertThat(ProfileKeys.valuesOf(reloaded))
                        .allSatisfy((other, value) -> assertThat(value.isSet())
                                .as("%s is unanswered", other)
                                .isEqualTo(other.equals(key))));
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
            degenerateOutputWordFloor:
              value: "6"
              provenance: "read off the word counts"
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
            generationContextWindow:
              value: "4097"
              provenance: "what this machine serves"
            clusterPageByteCeiling:
              value: "4098"
              provenance: "read off the page sizes"
            logTimestampShareFloor:
              value: "0.54"
              provenance: "read off the format mix"
            extractionAttempt:
              value: "3"
              provenance: "the converter was busy twice"
            codeNameEndings:
              value: ".C .Java"
              provenance: "read off the format mix"
            outOfScopeNameEndings:
              value: ".P3A .DGN"
              provenance: "read off the format mix"
            translationSimilarityFloor:
              value: "0.55"
              provenance: "read off the translations page"
            preferredLanguages:
              value: "it, en"
              provenance: "the languages the readers read"
            configurationSettingShareFloor:
              value: "0.56"
              provenance: "read off the format mix"
            deliverableLanguage:
              value: "it"
              provenance: "the readers read Italian"
            clusterResolution:
              value: "2.5"
              provenance: "read off the group sizes"
            """;

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
            Map.entry("clusterResolution", "2.5"));

    @Test
    @Story("What census writes to the profile")
    @DisplayName("A file answering every key loads with every answer on the key that carried it")
    @Issue("197")
    @Link(name = "ADR-119", url = Adr.PROFILE_HAS_ONE_CONSTRUCTOR, type = "adr")
    @Link(name = "ADR-277", url = Adr.CODE_IS_RECOGNISED_BY_A_NAME_ENDING_OR_A_GENERATORS_MARK, type = "adr")
    @Link(name = "ADR-287", url = Adr.LOGS_AND_CONFIGURATION_FILES_ARE_TOLD_BY_THEIR_BYTES, type = "adr")
    void everyAnswerLandsOnTheKeyThatCarriedIt(@TempDir Path workingDirectory) throws IOException {
        Files.writeString(workingDirectory.resolve("profile.yaml"), EVERY_KEY_ANSWERED);

        Profile loaded = new ProfileStore(workingDirectory).load();

        claim(
                "every one of the " + ProfileKeys.everyKey().size() + " keys the profile carries is"
                        + " answered, which no reader that built the record any other way than through its"
                        + " one whole-record constructor could produce -- a shorter one has no parameter"
                        + " for the last key, and a strict reader would refuse the file over it. The keys"
                        + " are read off the record, so a key added later and missing from the file above"
                        + " fails here by name",
                () -> assertThat(ProfileKeys.valuesOf(loaded))
                        .allSatisfy((key, value) -> assertThat(value.isSet())
                                .as("%s is answered", key)
                                .isTrue()));
        claim(
                "and each one sits on the key the file wrote it under, every value in the file being one no other"
                        + " key carries, so no reader shifted the values along by a position or swapped two of them",
                () -> assertThat(loaded.keysAsWritten()).containsAllEntriesOf(EVERY_KEY_AS_ANSWERED));
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
                        + " null, missing or refused -- which is the promise a reader has to keep whatever it builds"
                        + " the record through, and the one thing a shorter way in was ever standing in for",
                () -> assertThat(ProfileKeys.valuesOf(loaded))
                        .allSatisfy((key, value) -> assertThat(value.isSet())
                                .as("%s is answered only if it is the key the file answers", key)
                                .isEqualTo(key.equals("seedFolder"))));
    }

    @Test
    @Story("A wrong answer in the file is never mistaken for a missing one")
    @DisplayName("The word null in quotes is read as a wrong answer, not as an unanswered key")
    @Issue("203")
    @Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
    void aQuotedNullIsReadAsAWrongAnswer(@TempDir Path workingDirectory) throws IOException {
        Files.writeString(
                workingDirectory.resolve("profile.yaml"),
                """
                relevanceScoreFloor:
                  value: "null"
                  provenance: meant to clear the threshold
                """);
        ProfileStore store = new ProfileStore(workingDirectory);

        Profile loaded = store.load();

        claim(
                "the file loads rather than the tool refusing to run, and the quoted word is read as an answer"
                        + " nothing can act on, carrying back exactly what was typed -- this is the mistake the file format was chosen to catch, and for"
                        + " every numeric key it used to arrive at the stages as ordinary text",
                () -> assertThat(loaded.relevanceScoreFloor().reading())
                        .isEqualTo(new NumericValue.Unreadable("null")));
    }

    @Test
    @Story("A wrong answer in the file is never mistaken for a missing one")
    @DisplayName("A key left blank in the file stays distinguishable from one answered wrongly")
    @Issue("203")
    @Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
    void anUnansweredNumericKeyStaysDistinguishableFromAWrongOne(@TempDir Path workingDirectory)
            throws IOException {
        Files.writeString(
                workingDirectory.resolve("profile.yaml"),
                """
                relevanceScoreFloor:
                  value: null
                boilerplateDocumentFrequencyFloor:
                  value: not a number at all
                """);
        ProfileStore store = new ProfileStore(workingDirectory);

        Profile loaded = store.load();

        claim(
                "an empty value is nobody having answered, which is what every threshold ships as and"
                        + " what the whole pipeline is built to carry on through",
                () -> assertThat(loaded.relevanceScoreFloor().reading())
                        .isInstanceOf(NumericValue.Unset.class));
        claim(
                "and a filled-in value nothing can read is the other thing entirely -- the two have to"
                        + " stay apart, because only one of them is worth going back to the person about",
                () -> assertThat(loaded.boilerplateDocumentFrequencyFloor().reading())
                        .isInstanceOf(NumericValue.Unreadable.class));
    }
}
