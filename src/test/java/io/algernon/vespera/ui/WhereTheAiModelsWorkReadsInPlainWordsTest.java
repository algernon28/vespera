package io.algernon.vespera.ui;

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
 * Where the AI models work, the row of {@code modelsRunOn}, is worded under a profile row's rules (ADR-388 §1, ADR-377
 * §4): its title, short description and tooltip are the record's; a change of it, made in {@code profile.yaml} alone,
 * redoes no work and takes effect the next time Vespera is started, which is not the line of the values a publish alone
 * reads (§2); and the writing model's tooltip no longer says a model is refused for where it works, which nothing checks
 * since ADR-384 (§3).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("813")
@Link(name = "ADR-388", url = Adr.WHERE_THE_MODELS_RUN_IS_WORDED_UNDER_A_PROFILE_ROWS_RULES, type = "adr")
@Link(name = "ADR-384", url = Adr.WHETHER_THE_MODELS_RUN_LOCALLY_OR_IN_THE_CLOUD_IS_THE_PROFILES_TO_SAY, type = "adr")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class WhereTheAiModelsWorkReadsInPlainWordsTest {

    private static final String THE_TITLE = "Where the AI models work";

    private static final String THE_SHORT_DESCRIPTION = "The AI models that read your documents work on this computer;"
            + " using them in the cloud is not supported yet.";

    private static final String THE_TOOLTIP = "Says whether the AI models that read your documents work on this"
            + " computer or in the cloud. Using them in the cloud is not supported yet, so this value cannot be changed"
            + " here; profile.yaml holds it as a comment. Vespera takes every AI model it uses to work on this computer"
            + " and does not check it, so name only models that do: one whose name ends in 'cloud' would be sent your"
            + " documents' text. If profile.yaml is edited by hand to say cloud, or anything other than local, Vespera"
            + " stops at the start of anything that would send your documents' text to an AI model, before reading a"
            + " file or asking a model anything, and says why.";

    private static final String THE_COST_LINE =
            "Changing it in profile.yaml redoes no work; it takes effect the next time you start Vespera.";

    /** The sentence ADR-377 §4 gave the writing model's tooltip, which ADR-384 made untrue. */
    private static final String THE_UNTRUE_SENTENCE = "Vespera does not use a model that sends text off this computer,"
            + " such as one whose name ends in 'cloud'.";

    /** The sentence in its place. */
    private static final String THE_TRUE_SENTENCE = "Vespera does not check where the model works, which the \"Where"
            + " the AI models work\" row says, so name one that works on this computer: one whose name ends in 'cloud'"
            + " would be sent your documents' text.";

    @Test
    @Story("Where the models run is the operator's to say")
    @DisplayName("Where the AI models work is titled, described and explained in the record's words")
    void itsTitleShortDescriptionAndTooltipAreTheRecords() {
        claim("its title is \"" + THE_TITLE + "\"",
                () -> assertThat(ProfileKey.MODELS_RUN_ON.title()).isEqualTo(THE_TITLE));
        claim("its short description is the record's",
                () -> assertThat(ProfileKey.MODELS_RUN_ON.shortDescription()).isEqualTo(THE_SHORT_DESCRIPTION));
        claim("and its tooltip is the record's",
                () -> assertThat(ProfileKey.MODELS_RUN_ON.tooltip()).isEqualTo(THE_TOOLTIP));
    }

    @Test
    @Story("Where the models run is the operator's to say")
    @DisplayName("A change of where the AI models work redoes no work and takes effect the next time Vespera is started, not at the next Publish")
    void aChangeRedoesNoWorkAndTakesEffectTheNextTimeVesperaIsStarted() {
        claim("its cost line says a change in profile.yaml redoes no work and takes effect the next time Vespera is"
                        + " started",
                () -> assertThat(ProfileRow.costOf(ProfileKey.MODELS_RUN_ON)).isEqualTo(THE_COST_LINE));
        claim("which is not the line of a value a publish alone reads",
                () -> assertThat(ProfileRow.costOf(ProfileKey.MODELS_RUN_ON))
                        .isNotEqualTo(ProfileRow.costOf(ProfileKey.CONFLUENCE_BASE_URL)));
        claim("and no warning stands under it",
                () -> assertThat(ProfileRow.warningsOf(ProfileKey.MODELS_RUN_ON)).isEmpty());
    }

    @Test
    @Story("Where the models run is the operator's to say")
    @DisplayName("The writing model's tooltip says nothing checks where a model works, and no longer that a cloud model is refused")
    void theWritingModelsTooltipSaysNothingChecksWhereAModelWorks() {
        claim("the writing model's tooltip no longer says a model that sends text off this computer is not used",
                () -> assertThat(ProfileKey.GENERATION_MODEL.tooltip()).doesNotContain(THE_UNTRUE_SENTENCE));
        claim("and says instead that nothing checks where the model works, naming the row that says it",
                () -> assertThat(ProfileKey.GENERATION_MODEL.tooltip()).contains(THE_TRUE_SENTENCE));
    }
}
