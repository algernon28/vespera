package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ui.ProfileKey.Run;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a row says a change costs, and the value it uses when left empty (ADR-377 §4): one sentence chosen by the earliest
 * work a change redoes, a one-line warning only where the cost applies it, and the value in use with where it comes from
 * in plain words. Each line is written out as §4's tables give it.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("802")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class TheCostLineTest {

    /** The cost line of each earliest run, as §4's table gives it. */
    private static final Map<Run, String> THE_LINE_OF_EACH_RUN = Map.of(
            Run.BYTE_LEVEL_REDUCTION, "Changing it redoes everything from sorting your files to writing the pages; text"
                    + " already read out of your files is kept.",
            Run.EXTRACTION, "Changing it redoes everything after reading the text out of your files; the text already"
                    + " read is kept.",
            Run.CONTENT_REDUNDANCY, "Changing it redoes the search for documents that say the same thing, the groups and"
                    + " the pages; your files are not read again.",
            Run.SEED_MEASUREMENT, "Changing it redoes everything from reading your seed documents to writing the pages;"
                    + " your other files are not read again.",
            Run.EMBEDDING_SCORING, "Changing it redoes the measuring of closeness, the grouping and the writing of the"
                    + " pages; your files are not read again.",
            Run.GENERATION, "Changing it redoes only the writing of the pages; everything before it is kept.");

    /** The cost line of the five Confluence keys, which a publish alone reads. */
    private static final String A_PUBLISHS_LINE =
            "Changing it redoes no work; it takes effect the next time you press Publish.";

    /** The cost line of the largest group page, which enters no run but draws the group pages again. */
    private static final String THE_GROUP_PAGES_LINE = "Changing it rewrites only the group pages (a split group's"
            + " pictures are read again); everything else is kept.";

    /** The runs §4's table gives no line, which no key enters first. */
    private static final List<Run> RUNS_GIVEN_NO_LINE = List.of(Run.CONTENT_CENSUS, Run.ARRANGEMENT);

    private static final String APPROVAL =
            "Approval: a grouping you approved must be approved again in the Approved grouping row.";

    private static final String ANSWERS_REFUSED =
            "Answers: press Record my answers first; after the change the old answers file is refused.";

    private static final String ANSWERS_LOST =
            "Answers: press Record my answers first, or unsaved answers in relevance-labels.yaml are lost.";

    private static final String NEW_SAMPLE =
            "New sample: Vespera picks new documents for you to judge in relevance-labels.yaml.";

    /** The value the lines of the value in use are written for. */
    private static final String A_MODEL = "qwen3:8b";

    /** The line of the value in use for each source §4 names. */
    private static final Map<String, String> THE_LINE_OF_EACH_SOURCE = Map.of(
            "shipped", "Left empty, Vespera uses qwen3:8b (Vespera's settings).",
            "local-file", "Left empty, Vespera uses qwen3:8b (your local settings file).",
            "command-line", "Left empty, Vespera uses qwen3:8b (the command that started Vespera).",
            "environment", "Left empty, Vespera uses qwen3:8b (this computer's environment).",
            "code", "Left empty, Vespera uses qwen3:8b (Vespera's built-in value).");

    /** The line for a source §4 does not name, with no brackets. */
    private static final String NO_SOURCE_SAID = "Left empty, Vespera uses qwen3:8b.";

    @Test
    @Story("Each value's row says what a change costs")
    @DisplayName("Each value's cost line is the line of the earliest work a change of it redoes")
    void eachKeysCostLineIsTheLineOfItsEarliestRun() {
        claim("no key enters first the content census or the grouping's proposal, which the table gives no line",
                () -> assertThat(Arrays.stream(ProfileKey.values()).map(ProfileKey::earliestRun).flatMap(Optional::stream))
                        .doesNotContainAnyElementsOf(RUNS_GIVEN_NO_LINE));
        claim("each key's cost line is the table's line for its earliest run; the largest group page's its own; and the"
                        + " five Confluence values', which enter no run, the line of a publish",
                () -> assertThat(ProfileKey.values()).allSatisfy(key -> assertThat(ProfileRow.costOf(key))
                        .as("%s's cost line", key.key())
                        .isEqualTo(key == ProfileKey.CLUSTER_PAGE_BYTE_CEILING ? THE_GROUP_PAGES_LINE
                                : key.earliestRun().map(THE_LINE_OF_EACH_RUN::get).orElse(A_PUBLISHS_LINE))));
    }

    @Test
    @Story("Each value's row says what a change costs")
    @DisplayName("A warning stands under a value only where a change of it brings that cost")
    void aWarningStandsOnlyWhereTheCostAppliesIt() {
        claim("each key's warnings are, in order, the approval's where the grouping is redone, the refused answers' for"
                        + " the seed folder, the lost answers' where a new sample is drawn otherwise, and the new"
                        + " sample's where one is drawn, and nothing else",
                () -> assertThat(ProfileKey.values()).allSatisfy(key -> assertThat(ProfileRow.warningsOf(key))
                        .as("%s's warnings", key.key())
                        .containsExactlyElementsOf(warningsOf(key.cost()))));
        claim("and the writing model, which redoes only the writing, has none",
                () -> assertThat(ProfileRow.warningsOf(ProfileKey.GENERATION_MODEL)).isEmpty());
    }

    @Test
    @Story("Each value's row says what a change costs")
    @DisplayName("The line of the value in use says in plain words where the value comes from")
    void theLineOfTheValueInUseSaysWhereItComesFromInPlainWords() {
        claim("for each source, the line reads as the table of sources says",
                () -> assertThat(THE_LINE_OF_EACH_SOURCE).allSatisfy((source, line) -> assertThat(
                                ProfileRow.inUseOf(new EffectiveValues.Value(A_MODEL, source)))
                        .isEqualTo(line)));
        claim("and a source unknown, or any other, gives the line with no brackets",
                () -> assertThat(List.of("unknown", "nowhere")).allSatisfy(source -> assertThat(
                                ProfileRow.inUseOf(new EffectiveValues.Value(A_MODEL, source)))
                        .isEqualTo(NO_SOURCE_SAID)));
    }

    /** The warnings §4's second table gives a cost, in its order. */
    private static List<String> warningsOf(ProfileKey.Cost cost) {
        List<String> warnings = new ArrayList<>();
        if (cost.approvalCloses()) {
            warnings.add(APPROVAL);
        }
        if (cost.labelsRefused()) {
            warnings.add(ANSWERS_REFUSED);
        } else if (cost.sampleDrawnAgain()) {
            warnings.add(ANSWERS_LOST);
        }
        if (cost.sampleDrawnAgain()) {
            warnings.add(NEW_SAMPLE);
        }
        return warnings;
    }
}
