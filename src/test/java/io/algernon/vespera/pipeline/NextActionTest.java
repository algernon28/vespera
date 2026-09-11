package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileValue;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The line an invocation ends on (ADR-098, #136): what is set, what is not, and the single next
 * action.
 *
 * <p>It exists because of a property of the aggregate rather than of any one message. With nothing
 * set, an invocation emits nine locally-correct "gated" lines naming three different missing values,
 * and the one thing the operator has to do next is buried among them. Each of those nine stays
 * exactly as it is; this is the line that tells the reader which of them to act on.
 *
 * <p>Asserted here as a pure function of the profile and the answers recorded against the seed set,
 * which is every input the next action depends on. Whether the line is reached, and reached last, is
 * a different claim and belongs to an invocation-level test.
 */
@Epic("Pipeline")
@Feature("The operator is told the next value")
@Link(name = "ADR-098", url = Adr.FOUR_INVOCATIONS_AND_THE_NEXT_VALUE, type = "adr")
@Issue("136")
class NextActionTest {

    /** No answers have been written into the label file yet. */
    private static final int NOTHING_ANSWERED = 0;

    /** The scoring run wrote its sample of questions, which is the ordinary case. */
    private static final boolean QUESTIONS_WRITTEN = true;

    /** No label file exists: the step that writes one was gated, or the run never reached it. */
    private static final boolean NO_QUESTIONS_YET = false;

    /** ADR-088's stratified sample, answered in full. */
    private static final int SIXTY_ANSWERED = 60;

    @Test
    @Story("Step zero is the seed folder, because it is the only key available on day one")
    @DisplayName("With nothing set, the next action is to name the seed folder")
    void withNothingSetTheNextActionIsTheSeedFolder() {
        String line = NextAction.line(nothingSet(), NOTHING_ANSWERED, NO_QUESTIONS_YET);

        claim(
                "the line names seedFolder as the value to write, which is the one key no measurement"
                        + " could inform: the exemplars are the operator's own knowledge, and they are"
                        + " available on day one",
                () -> assertThat(line).contains("seedFolder"));
        claim(
                "and it says where to write it, because a key named with no file to put it in is not an"
                        + " action anyone can take",
                () -> assertThat(line).contains("profile.yaml"));
        claim(
                "it is one line, so it cannot itself become the nine lines it exists to summarise",
                () -> assertThat(line.lines()).hasSize(1));
    }

    @Test
    @Story("The next action names every value the next invocation needs, so no invocation is spent discovering one")
    @DisplayName("With nothing set, the line also names the two values invocation 2 needs")
    void withNothingSetTheLineNamesEveryValueTheNextInvocationNeeds() {
        String line = NextAction.line(nothingSet(), NOTHING_ANSWERED, NO_QUESTIONS_YET);

        claim(
                "the boilerplate floor is named, and it is actionable now rather than later: the"
                        + " invocation that just finished is the one that measured the document frequency"
                        + " the number is read off",
                () -> assertThat(line).contains("boilerplateDocumentFrequencyFloor"));
        claim(
                "and the embedding model, which needs no measurement at all -- naming a model is a choice"
                        + " from outside, available on day one like the seed folder",
                () -> assertThat(line).contains("embeddingModel"));
        claim(
                "and the relevance threshold is not named, because nothing an operator could read it off"
                        + " exists yet: the labelling report is written by the invocation these values"
                        + " unlock. Naming it here is how a fourth invocation becomes a fifth",
                () -> assertThat(line).doesNotContain("relevanceScoreFloor"));
    }

    @Test
    @Story("With the run's own values answered, the next act is the operator's own: labelling")
    @DisplayName("With the three run values set and nothing answered, the next action is to label and ingest")
    void withTheRunValuesSetTheNextActionIsToLabel() {
        String line = NextAction.line(theRunValuesSet(), NOTHING_ANSWERED, QUESTIONS_WRITTEN);

        claim(
                "the line says what is set, so an operator who has forgotten what they wrote is not sent"
                        + " back to the file to find out",
                () -> assertThat(line).contains("seedFolder").contains("embeddingModel"));
        claim(
                "and the next action is the label file the invocation just wrote, named so it can be"
                        + " found without reading this page's own report first",
                () -> assertThat(line).contains(RelevanceLabelFile.FILE_NAME));
        claim(
                "ingested by vespera label, which is an invocation of its own -- a person finishes"
                        + " answering and then says so, and nothing folds that into the unattended run",
                () -> assertThat(line).contains("vespera label"));
    }

    @Test
    @Story("Answers recorded and no threshold: the number is the operator's to write")
    @DisplayName("With answers recorded, the next action is to write the threshold off the labelling report")
    void withAnswersRecordedTheNextActionIsTheThreshold() {
        String line = NextAction.line(theRunValuesSet(), SIXTY_ANSWERED, QUESTIONS_WRITTEN);

        claim(
                "the threshold is named now, because the page it is read off exists now -- which is why"
                        + " it was not named at the end of the invocation before this one",
                () -> assertThat(line).contains("relevanceScoreFloor"));
        claim(
                "and the report the number comes off is named, because nothing writes that number and"
                        + " nothing checks the operator read this page first",
                () -> assertThat(line).contains(RelevanceLabellingReport.FILE_NAME));
        claim(
                "the answers are counted in words a person uses -- this line is read by an operator, and"
                        + " answer(s) is a plural written for a compiler",
                () -> assertThat(line).contains("60 answers are recorded").doesNotContain("answer(s)"));
        claim(
                "the operator is not sent back to the label file they have just finished with",
                () -> assertThat(line).doesNotContain("vespera label"));
    }

    @Test
    @Story("The last point on the path still ends with a line, and it says there is nothing to do")
    @DisplayName("With the threshold set and applied, the line says nothing is left to set")
    void withTheThresholdSetThereIsNothingLeftToSet() {
        String line = NextAction.line(everythingSet(), SIXTY_ANSWERED, QUESTIONS_WRITTEN);

        claim(
                "the line says every value is answered, rather than the invocation ending on silence --"
                        + " an operator cannot tell a finished path from a forgotten line",
                () -> assertThat(line).contains("Every value"));
        claim(
                "and it asks for nothing, because nothing is left: the fourth invocation is where the"
                        + " documents are removed and the path ends",
                () -> assertThat(line).doesNotContain("Next:"));
        claim(
                "it says the value is answered and claims nothing about what was done with it: a set key"
                        + " is not an applied threshold -- the floor is withheld from a run whose labels"
                        + " were given under another model, and this line cannot see that",
                () -> assertThat(line).doesNotContain("applied"));
        claim(
                "it is still one line, on the same channel, at the same place as the other three",
                () -> assertThat(line.lines()).hasSize(1));
    }

    @Test
    @Story("An operator who took step zero is not told their answer is missing")
    @DisplayName("With only the seed folder set, the line says so and names the two values still wanted")
    void withOnlyTheSeedFolderSetTheLineCreditsIt() {
        String line = NextAction.line(onlyTheSeedFolderSet(), NOTHING_ANSWERED, NO_QUESTIONS_YET);

        claim(
                "the seed folder is reported as set -- this is where the table in ADR-098 puts an"
                        + " operator at the end of invocation 1, having taken step zero before running"
                        + " anything, and telling them nothing is answered is false",
                () -> assertThat(line).contains("seedFolder is set"));
        claim(
                "the two values invocation 2 still wants are named",
                () -> assertThat(line)
                        .contains("boilerplateDocumentFrequencyFloor")
                        .contains("embeddingModel"));
        claim(
                "the threshold is absent from both halves, not merely from the action: listing it as"
                        + " unset invites an operator to go and set it, which is the fifth invocation"
                        + " this line exists to prevent",
                () -> assertThat(line).doesNotContain("relevanceScoreFloor"));
        claim(
                "and what informs a value is said once rather than in both halves of the line: the state"
                        + " half names keys, the action half says how to choose them, and a line that"
                        + " repeats itself is the noise this one exists to replace",
                () -> assertThat(line.split("shingle_document_frequency", -1)).hasSize(2));
    }

    @Test
    @Story("A threshold nobody can parse is not a threshold, and the line says so")
    @DisplayName("A non-numeric threshold is reported as wanting a number, not as answered")
    void aThresholdThatIsNotANumberIsNotAnAnswer() {
        String line = NextAction.line(theFloorMistyped(), SIXTY_ANSWERED, QUESTIONS_WRITTEN);

        claim(
                "the line does not call the path finished: the run read this value, failed to parse it"
                        + " and removed nothing, so telling the operator there is nothing left to do"
                        + " contradicts the step line above it",
                () -> assertThat(line).doesNotContain("Nothing is left to set"));
        claim(
                "it names the key and says a number is wanted, which is the whole of the fix",
                () -> assertThat(line).contains("relevanceScoreFloor").contains("number"));
        claim(
                "and quotes back what is written there, so the operator can see the comma or the stray"
                        + " letter rather than hunting for it",
                () -> assertThat(line).contains(A_MISTYPED_FLOOR));
    }

    @Test
    @Story("The operator is never sent to a file the invocation did not write")
    @DisplayName("With no questions written, the line does not send the operator to the label file")
    void withNoQuestionsWrittenTheOperatorIsNotSentToTheLabelFile() {
        String line = NextAction.line(theRunValuesSet(), NOTHING_ANSWERED, NO_QUESTIONS_YET);

        claim(
                "no label file is named, because none was written -- a seed folder that produced no"
                        + " usable text leaves stage 5 gated, and the questions with it",
                () -> assertThat(line).doesNotContain(RelevanceLabelFile.FILE_NAME));
        claim(
                "and vespera label is not proposed, since it would refuse for want of that same file",
                () -> assertThat(line).doesNotContain("vespera label"));
        claim(
                "the action is the one the gated line above already names, said once here rather than"
                        + " restated: this line points at it instead of inventing a value to ask for",
                () -> assertThat(line).contains("Next:"));
    }

    /** A profile census has created and nobody has answered — every key present and unset. */
    private static Profile nothingSet() {
        return new Profile(null, null, null, null, null);
    }

    /** Step zero taken and nothing else: the seed folder named before the first invocation. */
    private static Profile onlyTheSeedFolderSet() {
        return new Profile(set("/corpus/exemplars"), null, null, null, null);
    }

    /** Everything invocation 2 needs, answered; the threshold still the operator's to read off. */
    private static Profile theRunValuesSet() {
        return new Profile(set("/corpus/exemplars"), null, set("0.4"), set("embeddinggemma"), null);
    }

    /** What an operator writes when their keyboard or their locale disagrees with Double.parseDouble. */
    private static final String A_MISTYPED_FLOOR = "0,62";

    /** Every run value answered, and a threshold written in a form no run can read. */
    private static Profile theFloorMistyped() {
        return new Profile(set("/corpus/exemplars"), null, set("0.4"), set("embeddinggemma"), set(A_MISTYPED_FLOOR));
    }

    /** The end of the path: every value the four invocations ask for, answered. */
    private static Profile everythingSet() {
        return new Profile(set("/corpus/exemplars"), null, set("0.4"), set("embeddinggemma"), set("0.62"));
    }

    /** An answered key, with the provenance an operator is asked to record beside it. */
    private static ProfileValue set(String value) {
        return new ProfileValue(value, "recorded by the operator", null);
    }
}
