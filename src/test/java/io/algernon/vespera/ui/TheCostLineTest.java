package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a row says a change costs, and the value in use (ADR-377 §4): one sentence chosen by the earliest work a change
 * redoes, a warning for each consequence the cost applies, in a fixed order, and the line of the value Vespera uses
 * where the profile names none, its source in plain words.
 *
 * <p>Each expected line is written out as the record words it. {@code modelsRunOn}, which enters no work and is not
 * changed in the window, takes a line of its own (ADR-377 §4, ADR-384 §7).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("802")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class TheCostLineTest {

    /** The line of a key whose change redoes no work. */
    private static final String NO_WORK =
            "Changing it redoes no work; it takes effect the next time you press Publish.";

    /** The line of where the models run, which the window does not change. */
    private static final String WHERE_THE_MODELS_RUN =
            "It cannot be changed here; Vespera checks it each time it starts, and it redoes no work.";

    /** The line of the largest group page, which redraws the group pages alone. */
    private static final String THE_GROUP_PAGES = "Changing it rewrites only the group pages (a split group's pictures"
            + " are read again); everything else is kept.";

    /** The line for each earliest work a change redoes. */
    private static final Map<ProfileKey.Run, String> BY_EARLIEST = Map.of(
            ProfileKey.Run.BYTE_LEVEL_REDUCTION, "Changing it redoes everything from sorting your files to writing the"
                    + " pages; text already read out of your files is kept.",
            ProfileKey.Run.EXTRACTION, "Changing it redoes everything after reading the text out of your files; the"
                    + " text already read is kept.",
            ProfileKey.Run.CONTENT_REDUNDANCY, "Changing it redoes the search for documents that say the same thing,"
                    + " the groups and the pages; your files are not read again.",
            ProfileKey.Run.SEED_MEASUREMENT, "Changing it redoes everything from reading your seed documents to writing"
                    + " the pages; your other files are not read again.",
            ProfileKey.Run.EMBEDDING_SCORING, "Changing it redoes the measuring of closeness, the grouping and the"
                    + " writing of the pages; your files are not read again.",
            ProfileKey.Run.GENERATION, "Changing it redoes only the writing of the pages; everything before it is kept.");

    /** The four warnings, in the order a row shows them. */
    private static final String APPROVAL =
            "Approval: a grouping you approved must be approved again in the Approved grouping row.";
    private static final String ANSWERS_REFUSED =
            "Answers: press Record my answers first; after the change the old answers file is refused.";
    private static final String ANSWERS_LOST =
            "Answers: press Record my answers first, or unsaved answers in relevance-labels.yaml are lost.";
    private static final String NEW_SAMPLE =
            "New sample: Vespera picks new documents for you to judge in relevance-labels.yaml.";

    /** The five settings of Confluence a publish reads. */
    private static final Set<ProfileKey> CONFLUENCE = Set.of(ProfileKey.CONFLUENCE_BASE_URL,
            ProfileKey.CONFLUENCE_SPACE_KEY, ProfileKey.CONFLUENCE_FOLDER_ID, ProfileKey.CONFLUENCE_FOLDER_TITLE,
            ProfileKey.CONFLUENCE_EMAIL);

    /** The value in use the lines are drawn for. */
    private static final String MODEL = "qwen3:8b";

    @Test
    @Story("A setting's row says what a change redoes")
    @DisplayName("Each setting's cost line is the line of the earliest work a change of it redoes")
    void eachKeysCostLineIsTheLineOfItsEarliestRun() {
        for (ProfileKey key : ProfileKey.values()) {
            Optional<ProfileKey.Run> earliest = key.earliestRun();
            String expected = key == ProfileKey.CLUSTER_PAGE_BYTE_CEILING ? THE_GROUP_PAGES
                    : key == ProfileKey.MODELS_RUN_ON ? WHERE_THE_MODELS_RUN
                    : earliest.map(BY_EARLIEST::get).orElse(NO_WORK);
            claim(key.key() + "'s cost line is the line of "
                            + earliest.map(run -> "the earliest work it redoes, " + run.stageName())
                                    .orElse("a change that redoes no work"),
                    () -> assertThat(ProfileRow.costOf(key)).isEqualTo(expected));
        }
        claim("the five Confluence settings each take the line of a change that redoes no work",
                () -> assertThat(CONFLUENCE).allSatisfy(key -> assertThat(ProfileRow.costOf(key)).isEqualTo(NO_WORK)));
        claim("where the models run takes its own line, since the window does not change it, and says it redoes no work",
                () -> assertThat(ProfileRow.costOf(ProfileKey.MODELS_RUN_ON)).isEqualTo(WHERE_THE_MODELS_RUN)
                        .isNotEqualTo(NO_WORK));
        claim("the largest group page takes its own line, though a change of it enters no work either",
                () -> assertThat(ProfileKey.CLUSTER_PAGE_BYTE_CEILING.earliestRun()).isEmpty());
        claim("no setting's earliest work is the content census or the arrangement, which the table gives no line",
                () -> assertThat(ProfileKey.values()).noneMatch(key -> key.earliestRun()
                        .filter(run -> run == ProfileKey.Run.CONTENT_CENSUS || run == ProfileKey.Run.ARRANGEMENT)
                        .isPresent()));
    }

    @Test
    @Story("A setting's row says what a change redoes")
    @DisplayName("A warning stands only where a change of the setting has its consequence, in a fixed order")
    void aWarningStandsOnlyWhereTheCostAppliesIt() {
        for (ProfileKey key : ProfileKey.values()) {
            ProfileKey.Cost cost = key.cost();
            List<String> expected = new ArrayList<>();
            if (cost.approvalCloses()) {
                expected.add(APPROVAL);
            }
            if (cost.labelsRefused()) {
                expected.add(ANSWERS_REFUSED);
            } else if (cost.sampleDrawnAgain()) {
                expected.add(ANSWERS_LOST);
            }
            if (cost.sampleDrawnAgain()) {
                expected.add(NEW_SAMPLE);
            }
            claim(key.key() + "'s warnings are those its cost applies, in order",
                    () -> assertThat(ProfileRow.warningsOf(key)).containsExactlyElementsOf(expected));
        }
        claim("the seed folder warns of the approval, of the answers refused and of the new sample",
                () -> assertThat(ProfileRow.warningsOf(ProfileKey.SEED_FOLDER))
                        .containsExactly(APPROVAL, ANSWERS_REFUSED, NEW_SAMPLE));
        claim("the writing model warns of nothing",
                () -> assertThat(ProfileRow.warningsOf(ProfileKey.GENERATION_MODEL)).isEmpty());
    }

    @Test
    @Story("A setting's row says the value in use")
    @DisplayName("The line of the value in use says where the value comes from in plain words")
    void theLineOfTheValueInUseSaysWhereItComesFromInPlainWords() {
        Map<String, String> bySource = Map.of(
                "shipped", "Left empty, Vespera uses qwen3:8b (Vespera's settings).",
                "local-file", "Left empty, Vespera uses qwen3:8b (your local settings file).",
                "command-line", "Left empty, Vespera uses qwen3:8b (the command that started Vespera).",
                "environment", "Left empty, Vespera uses qwen3:8b (this computer's environment).",
                "code", "Left empty, Vespera uses qwen3:8b (Vespera's built-in value).",
                "unknown", "Left empty, Vespera uses qwen3:8b.",
                "nowhere", "Left empty, Vespera uses qwen3:8b.");
        bySource.forEach((source, line) -> claim("a value from the source " + source + " reads: " + line,
                () -> assertThat(ProfileRow.inUseOf(new EffectiveValues.Value(MODEL, source))).isEqualTo(line)));
    }
}
