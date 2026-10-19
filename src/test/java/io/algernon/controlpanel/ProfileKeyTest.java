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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The control panel's table of profile keys (ADR-295 §5): each key, in the profile's order, with its type and the
 * earliest run it enters, and the cost of a change said before it is saved. That the keys and types are the jar's
 * {@code Profile}'s, and the runs its {@code StageModules}', is the contract test's,
 * {@code pipeline.TheJarReadsWhatTheControlPanelWritesTest}.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("566")
@Link(name = "ADR-295", url = Adr.THE_CONTROL_PANEL_WRITES_HOLDING_THE_WORKING_DIRECTORY_LOCK, type = "adr")
class ProfileKeyTest {

    /** One row of the table: the key as profile.yaml spells it, its type and the earliest run it enters. */
    private record Row(String key, Kind kind, Run earliestRun) {}

    /** The sixteen keys in the profile's order, with the type and run the record's table gives each. */
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
            new Row("logTimestampShareFloor", Kind.NUMBER, Run.BYTE_LEVEL_REDUCTION),
            new Row("extractionAttempt", Kind.NUMBER, Run.EXTRACTION),
            new Row("codeNameEndings", Kind.NAME_ENDINGS, Run.BYTE_LEVEL_REDUCTION),
            new Row("outOfScopeNameEndings", Kind.NAME_ENDINGS, Run.BYTE_LEVEL_REDUCTION),
            new Row("translationSimilarityFloor", Kind.NUMBER, Run.EMBEDDING_SCORING),
            new Row("preferredLanguages", Kind.LANGUAGES, Run.EMBEDDING_SCORING),
            new Row("configurationSettingShareFloor", Kind.NUMBER, Run.BYTE_LEVEL_REDUCTION));

    @Test
    @Story("The control panel says what a change costs before it is saved")
    @DisplayName("Each of the sixteen profile keys, in the profile's order, has its type and the earliest run it enters")
    void eachKeyHasItsTypeAndEarliestRun() {
        claim("the table holds the sixteen keys in the profile's order, each with its type and earliest run",
                () -> assertThat(Arrays.stream(ProfileKey.values())
                                .map(key -> new Row(key.key(), key.kind(), key.earliestRun()))
                                .toList())
                        .containsExactlyElementsOf(TABLE));
    }

    @Test
    @Story("The control panel says what a change costs before it is saved")
    @DisplayName("A change costs its key's earliest run and every later one; the approval closes where the arrangement is among them, and the labels are refused after a seed folder change")
    void aChangeCostsItsRunAndEveryLaterOne() {
        List<Run> cascade = List.of(Run.values());

        claim("the runs, in the cascade's order, are byte-level reduction, extraction, content census, content"
                        + " redundancy, seed measurement, embedding scoring, arrangement and generation",
                () -> assertThat(cascade).containsExactly(Run.BYTE_LEVEL_REDUCTION, Run.EXTRACTION,
                        Run.CONTENT_CENSUS, Run.CONTENT_REDUNDANCY, Run.SEED_MEASUREMENT, Run.EMBEDDING_SCORING,
                        Run.ARRANGEMENT, Run.GENERATION));
        for (ProfileKey key : ProfileKey.values()) {
            List<Run> fromItsRunOn = cascade.subList(cascade.indexOf(key.earliestRun()), cascade.size());
            Cost expected = new Cost(fromItsRunOn, fromItsRunOn.contains(Run.ARRANGEMENT), key == ProfileKey.SEED_FOLDER);
            claim("a change of " + key.key() + " costs " + fromItsRunOn + ", closes the approval only if the"
                            + " arrangement is among them, and refuses the labels only for the seed folder",
                    () -> assertThat(key.cost()).isEqualTo(expected));
        }
        claim("so a change of the generation model leaves the approval open, and one of the embedding model closes it",
                () -> {
                    assertThat(ProfileKey.GENERATION_MODEL.cost().approvalCloses()).isFalse();
                    assertThat(ProfileKey.EMBEDDING_MODEL.cost().approvalCloses()).isTrue();
                });
    }
}
