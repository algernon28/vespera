package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.ProfileKey.Cost;
import io.algernon.controlpanel.ProfileKey.Kind;
import io.algernon.controlpanel.ProfileKey.Run;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The control panel's table of profile keys (ADR-295 §5): each key, in the profile's order, with its type and the
 * earliest run it enters, or none for {@code clusterPageByteCeiling}, which enters no run (ADR-303 §9), and the cost of
 * a change said before it is saved. That the keys and types are the jar's
 * {@code Profile}'s, and the runs its {@code StageModules}', is the contract test's,
 * {@code pipeline.TheJarReadsWhatTheControlPanelWritesTest}.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("566")
@Issue("571")
@Link(name = "ADR-295", url = Adr.THE_CONTROL_PANEL_WRITES_HOLDING_THE_WORKING_DIRECTORY_LOCK, type = "adr")
@Link(name = "ADR-303", url = Adr.A_CLUSTERS_PAGE_PAST_THE_CEILING_IS_SPLIT_INTO_PAGES, type = "adr")
class ProfileKeyTest {

    /**
     * One row of the table: the key as profile.yaml spells it, the name of its type and the earliest run it enters, if
     * any. The type is held by its name, so that a kind a record adds before the table has it fails this test alone.
     */
    private record Row(String key, String kind, Optional<Run> earliestRun) {

        Row(String key, Kind kind, Run earliestRun) {
            this(key, kind.name(), Optional.of(earliestRun));
        }

        Row(String key, Kind kind, Optional<Run> earliestRun) {
            this(key, kind.name(), earliestRun);
        }

        Row(String key, String kind, Run earliestRun) {
            this(key, kind, Optional.of(earliestRun));
        }
    }

    /** The twenty-six keys in the profile's order, with the type and run the records' tables give each. */
    private static final List<Row> TABLE = List.of(
            new Row("seedFolder", Kind.TEXT, Run.SEED_MEASUREMENT),
            new Row("degenerateOutputConfidenceFloor", Kind.NUMBER, Run.EXTRACTION),
            new Row("degenerateOutputWordFloor", Kind.NUMBER, Run.EXTRACTION),
            new Row("boilerplateDocumentFrequencyFloor", Kind.NUMBER, Run.CONTENT_REDUNDANCY),
            new Row("embeddingModel", Kind.TEXT, Run.EMBEDDING_SCORING),
            new Row("relevanceScoreFloor", Kind.NUMBER, Run.EMBEDDING_SCORING),
            new Row("arrangementApproved", Kind.TEXT, Run.GENERATION),
            new Row("generationModel", Kind.TEXT, Run.GENERATION),
            new Row("generationContextWindow", Kind.NUMBER, Run.GENERATION),
            new Row("clusterPageByteCeiling", Kind.NUMBER, Optional.empty()),
            new Row("logTimestampShareFloor", Kind.NUMBER, Run.BYTE_LEVEL_REDUCTION),
            new Row("extractionAttempt", Kind.NUMBER, Run.EXTRACTION),
            new Row("codeNameEndings", Kind.NAME_ENDINGS, Run.BYTE_LEVEL_REDUCTION),
            new Row("outOfScopeNameEndings", Kind.NAME_ENDINGS, Run.BYTE_LEVEL_REDUCTION),
            new Row("translationSimilarityFloor", Kind.NUMBER, Run.EMBEDDING_SCORING),
            new Row("preferredLanguages", Kind.LANGUAGES, Run.EMBEDDING_SCORING),
            new Row("configurationSettingShareFloor", Kind.NUMBER, Run.BYTE_LEVEL_REDUCTION),
            // ADR-321: the deliverable's language enters the generation run alone, so the approval stays open.
            new Row("deliverableLanguage", Kind.TEXT, Run.GENERATION),
            // ADR-330 §9: last of the profile, entering the scoring run, where a cluster's identity lies.
            new Row("clusterResolution", Kind.NUMBER, Run.EMBEDDING_SCORING),
            // ADR-334 §5.1: a list of folders outside the corpus root, entering byte-level reduction, the walk being another.
            new Row("codeSetFolders", Kind.FOLDERS, Run.BYTE_LEVEL_REDUCTION),
            // ADR-340 §8.1: folders below the corpus root the walk never enters, entering byte-level reduction, the walk
            // that leaves them out being another.
            new Row("foldersNotWalked", "CORPUS_FOLDERS", Run.BYTE_LEVEL_REDUCTION),
            // ADR-359 §1: where a publish sends, which no stage reads, so each enters no run.
            new Row("confluenceBaseUrl", Kind.TEXT, Optional.empty()),
            new Row("confluenceSpaceKey", Kind.TEXT, Optional.empty()),
            new Row("confluenceFolderId", Kind.TEXT, Optional.empty()),
            new Row("confluenceFolderTitle", Kind.TEXT, Optional.empty()),
            new Row("confluenceEmail", Kind.TEXT, Optional.empty()));

    /** The runs from each run on, in the cascade's order, as ADR-295 §5's table spells them. */
    private static final List<Run> FROM_BYTE_LEVEL_REDUCTION = List.of(Run.BYTE_LEVEL_REDUCTION, Run.EXTRACTION,
            Run.CONTENT_CENSUS, Run.CONTENT_REDUNDANCY, Run.SEED_MEASUREMENT, Run.EMBEDDING_SCORING, Run.ARRANGEMENT,
            Run.GENERATION);

    private static final List<Run> FROM_EXTRACTION = List.of(Run.EXTRACTION, Run.CONTENT_CENSUS,
            Run.CONTENT_REDUNDANCY, Run.SEED_MEASUREMENT, Run.EMBEDDING_SCORING, Run.ARRANGEMENT, Run.GENERATION);

    private static final List<Run> FROM_CONTENT_REDUNDANCY = List.of(Run.CONTENT_REDUNDANCY, Run.SEED_MEASUREMENT,
            Run.EMBEDDING_SCORING, Run.ARRANGEMENT, Run.GENERATION);

    private static final List<Run> FROM_SEED_MEASUREMENT = List.of(Run.SEED_MEASUREMENT, Run.EMBEDDING_SCORING,
            Run.ARRANGEMENT, Run.GENERATION);

    private static final List<Run> FROM_EMBEDDING_SCORING = List.of(Run.EMBEDDING_SCORING, Run.ARRANGEMENT,
            Run.GENERATION);

    private static final List<Run> FROM_GENERATION = List.of(Run.GENERATION);

    /**
     * A change of a key whose runs include embedding scoring: the arrangement is among them, so the approval closes,
     * and the sample being answered is drawn again (ADR-330 §9, §10); the labels stand.
     */
    private static Cost scoringAgainFrom(List<Run> runs) {
        return new Cost(runs, true, false, true);
    }

    /**
     * Each key's cost, written out by hand from the records' tables: sixteen keys draw the sample again, the seed
     * folder alone refuses the labels, the four generation keys close no approval and the page ceiling costs nothing.
     */
    private static final Map<String, Cost> COST_OF_EACH_KEY = costOfEachKey();

    @Test
    @Story("The control panel says what a change costs before it is saved")
    @DisplayName("Each of the twenty-six profile keys, in the profile's order, has its type and the earliest run it enters, the page ceiling and the five values of a publish entering none")
    @Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
    @Link(name = "ADR-359", url = Adr.THE_PUBLISH_READS_ITS_CONFLUENCE_VALUES_FROM_THE_PROFILE_AND_THE_CONTROL_PANEL_PASSES_THE_TOKEN, type = "adr")
    void eachKeyHasItsTypeAndEarliestRun() {
        claim("the table holds the twenty-six keys in the profile's order, each with its type and earliest run, and the"
                        + " page ceiling and the five values of a publish, last, with none",
                () -> assertThat(Arrays.stream(ProfileKey.values())
                                .map(key -> new Row(key.key(), key.kind(), key.earliestRun()))
                                .toList())
                        .containsExactlyElementsOf(TABLE));
    }

    @Test
    @Story("The control panel says what a change costs before it is saved")
    @DisplayName("A change costs its key's earliest run and every later one; the approval closes where the arrangement is among them, the labels are refused after a seed folder change, and the sample being answered is drawn again for the sixteen values whose runs include embedding scoring")
    @Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
    void aChangeCostsItsRunAndEveryLaterOne() {
        Map<String, Cost> costs = new LinkedHashMap<>();
        for (ProfileKey key : ProfileKey.values()) {
            costs.put(key.key(), key.cost());
        }

        claim("the runs, in the cascade's order, are byte-level reduction, extraction, content census, content"
                        + " redundancy, seed measurement, embedding scoring, arrangement and generation",
                () -> assertThat(Run.values()).containsExactly(Run.BYTE_LEVEL_REDUCTION, Run.EXTRACTION,
                        Run.CONTENT_CENSUS, Run.CONTENT_REDUNDANCY, Run.SEED_MEASUREMENT, Run.EMBEDDING_SCORING,
                        Run.ARRANGEMENT, Run.GENERATION));
        claim("a change of each key costs its earliest run and every later one; it closes the approval only where the"
                        + " arrangement is among them, refuses the labels only for the seed folder, and draws the"
                        + " sample again for the sixteen whose runs include embedding scoring; the page ceiling and the"
                        + " five values of a publish cost no run, close no approval, refuse no labels and draw no sample",
                () -> assertThat(costs).containsExactlyEntriesOf(COST_OF_EACH_KEY));
    }

    /**
     * The keys {@code effective-values.properties} says a value beneath (ADR-330 §1, §14), by name, the resolution's
     * preset among them, so that a page told nothing says it does not know them.
     */
    @Test
    @Story("The control panel says what a change costs before it is saved")
    @DisplayName("Five values have something beneath the profile: the two models, the reading window, the language and how finely documents are grouped")
    @Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
    void fiveKeysHaveAValueBeneathTheProfile() {
        claim("these five and no other, in the profile's order",
                () -> assertThat(Arrays.stream(ProfileKey.values())
                                .filter(ProfileKey::hasAValueBeneath)
                                .map(ProfileKey::key)
                                .toList())
                        .containsExactly("embeddingModel", "generationModel", "generationContextWindow",
                                "deliverableLanguage", "clusterResolution"));
    }

    private static Map<String, Cost> costOfEachKey() {
        Map<String, Cost> costs = new LinkedHashMap<>();
        costs.put("seedFolder", new Cost(FROM_SEED_MEASUREMENT, true, true, true));
        costs.put("degenerateOutputConfidenceFloor", scoringAgainFrom(FROM_EXTRACTION));
        costs.put("degenerateOutputWordFloor", scoringAgainFrom(FROM_EXTRACTION));
        costs.put("boilerplateDocumentFrequencyFloor", scoringAgainFrom(FROM_CONTENT_REDUNDANCY));
        costs.put("embeddingModel", scoringAgainFrom(FROM_EMBEDDING_SCORING));
        costs.put("relevanceScoreFloor", scoringAgainFrom(FROM_EMBEDDING_SCORING));
        costs.put("arrangementApproved", new Cost(FROM_GENERATION, false, false, false));
        costs.put("generationModel", new Cost(FROM_GENERATION, false, false, false));
        costs.put("generationContextWindow", new Cost(FROM_GENERATION, false, false, false));
        costs.put("clusterPageByteCeiling", new Cost(List.of(), false, false, false));
        costs.put("logTimestampShareFloor", scoringAgainFrom(FROM_BYTE_LEVEL_REDUCTION));
        costs.put("extractionAttempt", scoringAgainFrom(FROM_EXTRACTION));
        costs.put("codeNameEndings", scoringAgainFrom(FROM_BYTE_LEVEL_REDUCTION));
        costs.put("outOfScopeNameEndings", scoringAgainFrom(FROM_BYTE_LEVEL_REDUCTION));
        costs.put("translationSimilarityFloor", scoringAgainFrom(FROM_EMBEDDING_SCORING));
        costs.put("preferredLanguages", scoringAgainFrom(FROM_EMBEDDING_SCORING));
        costs.put("configurationSettingShareFloor", scoringAgainFrom(FROM_BYTE_LEVEL_REDUCTION));
        costs.put("deliverableLanguage", new Cost(FROM_GENERATION, false, false, false));
        costs.put("clusterResolution", scoringAgainFrom(FROM_EMBEDDING_SCORING));
        costs.put("codeSetFolders", scoringAgainFrom(FROM_BYTE_LEVEL_REDUCTION));
        costs.put("foldersNotWalked", scoringAgainFrom(FROM_BYTE_LEVEL_REDUCTION));
        for (String publishes : List.of("confluenceBaseUrl", "confluenceSpaceKey", "confluenceFolderId",
                "confluenceFolderTitle", "confluenceEmail")) {
            costs.put(publishes, new Cost(List.of(), false, false, false));
        }
        return costs;
    }
}
