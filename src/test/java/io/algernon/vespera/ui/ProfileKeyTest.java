package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.ui.ProfileKey.Cost;
import io.algernon.vespera.ui.ProfileKey.Kind;
import io.algernon.vespera.ui.ProfileKey.Run;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
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
