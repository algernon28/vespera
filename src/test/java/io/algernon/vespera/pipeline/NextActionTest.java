package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    /** The short name of the arrangement this invocation wrote, as the gate page prints it. */
    private static final String AN_ARRANGEMENT = "9f2c41ab77de";

    /** No arrangement was written this invocation, so there is nothing to approve. */
    private static final String NOTHING_ARRANGED = null;

    /** No answers have been written into the label file yet. */
    private static final int NOTHING_ANSWERED = 0;

    /** The scoring run wrote its sample of questions, which is the ordinary case. */
    private static final boolean QUESTIONS_WRITTEN = true;

    /** No label file exists: the step that writes one was gated, or the run never reached it. */
    private static final boolean NO_QUESTIONS_YET = false;

    /** The model resolved for this invocation, as {@code GenerationModel} would hand it over. */
    private static final String THE_GENERATION_MODEL = "a-generation-model:8b";

    /** ADR-088's stratified sample, answered in full. */
    private static final int SIXTY_ANSWERED = 60;

    /**
     * Whether the approval in the profile names the arrangement this invocation arrived at (ADR-154
     * §2). Every state here but one is before any approval, or has nothing arranged, so none matches.
     */
    private static final boolean NO_APPROVAL_MATCHES_THIS_INVOCATION = false;

    /** The one state here whose approval names the arrangement the invocation arrived at. */
    private static final boolean APPROVAL_MATCHES_THIS_INVOCATION = true;

    @Test
    @Story("Step zero is the seed folder, because it is the only key available on day one")
    @DisplayName("With nothing set, the next action is to name the seed folder")
    void withNothingSetTheNextActionIsTheSeedFolder() {
        String line = NextAction.line(nothingSet(), NOTHING_ANSWERED, NO_QUESTIONS_YET, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

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
    @DisplayName("With nothing set, the line also names the value invocation 2 needs, and not the embedding model")
    @Link(name = "ADR-298", url = Adr.THE_EMBEDDING_MODEL_SHIPS_WITH_A_DEFAULT, type = "adr")
    void withNothingSetTheLineNamesEveryValueTheNextInvocationNeeds() {
        String line = NextAction.line(nothingSet(), NOTHING_ANSWERED, NO_QUESTIONS_YET, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the boilerplate floor is named, and it is actionable now rather than later: the"
                        + " invocation that just finished is the one that measured the document frequency"
                        + " the number is read off",
                () -> assertThat(line).contains("boilerplateDocumentFrequencyFloor"));
        claim(
                "and the embedding model is not: the application ships with one, so an operator who names"
                        + " none is not asked for one, and naming another is an override rather than a stop",
                () -> assertThat(line).doesNotContain("embeddingModel"));
        claim(
                "and the relevance threshold is not named, because nothing an operator could read it off"
                        + " exists yet: the labelling report is written by the invocation these values"
                        + " unlock. Naming it here is how a fourth invocation becomes a fifth",
                () -> assertThat(line).doesNotContain("relevanceScoreFloor"));
    }

    @Test
    @Story("With the run's own values answered, the next act is the operator's own: labelling")
    @DisplayName("With the two run values set and nothing answered, the next action is to label and ingest")
    @Link(name = "ADR-298", url = Adr.THE_EMBEDDING_MODEL_SHIPS_WITH_A_DEFAULT, type = "adr")
    void withTheRunValuesSetTheNextActionIsToLabel() {
        String line = NextAction.line(theRunValuesSet(), NOTHING_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the line says what is set, so an operator who has forgotten what they wrote is not sent"
                        + " back to the file to find out",
                () -> assertThat(line).contains("seedFolder and boilerplateDocumentFrequencyFloor are set"));
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
        String line = NextAction.line(theRunValuesSet(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

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
    @DisplayName("With everything answered, including the arrangement approved, nothing is left to set")
    void withTheThresholdSetThereIsNothingLeftToSet() {
        String line = NextAction.line(everythingApproved(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, AN_ARRANGEMENT, THE_GENERATION_MODEL, APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the line says every value is answered, rather than the invocation ending on silence --"
                        + " an operator cannot tell a finished path from a forgotten line",
                () -> assertThat(line).contains("Every value"));
        claim(
                "and it asks for nothing, because nothing is left: the path ends once the arrangement"
                        + " the documents were written over is one the operator approved",
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
    @DisplayName("With only the seed folder set, the line says so and names the one value still wanted")
    @Link(name = "ADR-298", url = Adr.THE_EMBEDDING_MODEL_SHIPS_WITH_A_DEFAULT, type = "adr")
    void withOnlyTheSeedFolderSetTheLineCreditsIt() {
        String line = NextAction.line(onlyTheSeedFolderSet(), NOTHING_ANSWERED, NO_QUESTIONS_YET, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the seed folder is reported as set -- this is where the table in ADR-098 puts an"
                        + " operator at the end of invocation 1, having taken step zero before running"
                        + " anything, and telling them nothing is answered is false",
                () -> assertThat(line).contains("seedFolder is set"));
        claim(
                "the one value invocation 2 still wants is named, and the embedding model is not among them:"
                        + " the application ships with one",
                () -> assertThat(line)
                        .contains("boilerplateDocumentFrequencyFloor")
                        .doesNotContain("embeddingModel"));
        claim(
                "the threshold is absent from both halves, not merely from the action: listing it as"
                        + " unset invites an operator to go and set it, which is the fifth invocation"
                        + " this line exists to prevent",
                () -> assertThat(line).doesNotContain("relevanceScoreFloor"));
        claim(
                "and what informs a value is said once rather than in both halves of the line: the state"
                        + " half names keys, the action half says how to choose them, and a line that"
                        + " repeats itself is the noise this one exists to replace",
                () -> assertThat(line.split("document-frequency\\.html", -1)).hasSize(2));
    }

    @Test
    @Story("A threshold nobody can parse is not a threshold, and the line says so")
    @DisplayName("A non-numeric threshold is reported as wanting a number, not as answered")
    void aThresholdThatIsNotANumberIsNotAnAnswer() {
        String line = NextAction.line(theFloorMistyped(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

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
    @Story("A threshold nobody can parse is not a threshold, and the line says so")
    @DisplayName("A non-numeric repetition floor is reported the same way the relevance one is")
    @Issue("203")
    @Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
    void aMistypedBoilerplateFloorIsReportedToo() {
        String line = NextAction.line(
                theBoilerplateFloorMistyped(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the key is named and a number is asked for, exactly as for the other threshold: until"
                        + " now a mistyped value here ended the invocation outright, which at least made"
                        + " it impossible to miss -- now that it is ignored instead, saying so is the only"
                        + " thing standing between ignored and silently dropped",
                () -> assertThat(line).contains("boilerplateDocumentFrequencyFloor").contains("number"));
        claim(
                "and what was typed is quoted back, so the operator is looking at their own mistake"
                        + " rather than at a description of one",
                () -> assertThat(line).contains(A_MISTYPED_FLOOR));
        claim(
                "and the same line does not also call the key answered: the run ignored it, so a sentence"
                        + " crediting the operator with having set it would contradict the very clause"
                        + " beside it telling them to go and write one",
                () -> assertThat(line).doesNotContain("boilerplateDocumentFrequencyFloor is set")
                        .doesNotContain("boilerplateDocumentFrequencyFloor are set"));
    }

    @Test
    @Story("A threshold nobody can parse is not a threshold, and the line says so")
    @DisplayName("A mistyped floor beside an answered seed folder is reported, and nothing else is asked for")
    @Issue("203")
    @Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
    @Link(name = "ADR-298", url = Adr.THE_EMBEDDING_MODEL_SHIPS_WITH_A_DEFAULT, type = "adr")
    void aMistypedFloorIsReportedAndNothingElseIsAskedFor() {
        Profile mistypedBesideASeedFolder = ProfileFixture.profile()
                .seedFolder(THE_SEED_FOLDER, RECORDED_BY_THE_OPERATOR)
                .boilerplateDocumentFrequencyFloor(A_MISTYPED_FLOOR, RECORDED_BY_THE_OPERATOR)
                .build();

        String line = NextAction.line(
                mistypedBesideASeedFolder, NOTHING_ANSWERED, NO_QUESTIONS_YET, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the mistyped value is quoted back beside a key that is answered: the line has the seed"
                        + " folder to credit, and the value it cannot use has already been ignored once",
                () -> assertThat(line).contains(A_MISTYPED_FLOOR));
        claim(
                "and the key is not credited as answered anywhere in the sentence -- a line that said it"
                        + " was set while the run ignored it is the one wording that would leave someone"
                        + " believing a floor is in force when none is",
                () -> assertThat(line).doesNotContain("boilerplateDocumentFrequencyFloor is set")
                        .doesNotContain("and boilerplateDocumentFrequencyFloor are set"));
        claim(
                "and it is not called unset either, which is the other wrong answer: the operator wrote"
                        + " something, and sending them to look at a key they would find already filled in"
                        + " is how a person concludes the tool is broken rather than their value",
                () -> assertThat(line).doesNotContain("boilerplateDocumentFrequencyFloor is not"));
        claim(
                "and no other value is asked for: the embedding model is the only other key the next"
                        + " invocation reads before scoring, and the application ships with one",
                () -> assertThat(line).doesNotContain("embeddingModel"));
    }

    @Test
    @Story("A threshold nobody can parse is not a threshold, and the line says so")
    @DisplayName("With nothing else answered, a mistyped floor is still the thing the line reports")
    @Issue("203")
    @Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
    void aMistypedFloorIsReportedWhenNothingElseIsAnsweredEither() {
        Profile onlyTheMistypedFloor = ProfileFixture.profile()
                .boilerplateDocumentFrequencyFloor(A_MISTYPED_FLOOR, RECORDED_BY_THE_OPERATOR)
                .build();

        String line = NextAction.line(
                onlyTheMistypedFloor, NOTHING_ANSWERED, NO_QUESTIONS_YET, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the line still says nothing is answered, which is true: the one key holding anything"
                        + " holds something the run could not use, so there is no answered value to credit",
                () -> assertThat(line).contains("No value in the profile is answered yet"));
        claim(
                "and it says so without swallowing the mistake -- the clause naming what is written there"
                        + " is what keeps that sentence from reading as though the file were empty, which"
                        + " is the one way a person could believe they had never written the key at all",
                () -> assertThat(line).contains(A_MISTYPED_FLOOR)
                        .contains("boilerplateDocumentFrequencyFloor"));
        claim(
                "it is still one line, because the state of every key has to fit in the sentence a person"
                        + " actually reads",
                () -> assertThat(line.lines()).hasSize(1));
    }

    @Test
    @Story("A threshold nobody can parse is not a threshold, and the line says so")
    @DisplayName("A non-numeric conversion-quality floor is reported the same way")
    @Issue("203")
    @Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
    void aMistypedConfidenceFloorIsReportedToo() {
        String line = NextAction.line(
                theConfidenceFloorMistyped(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "this key is named too, and it is the one where saying nothing would be worst: a"
                        + " mistyped value here used to stop the tool from starting at all, so an operator"
                        + " who fixed nothing would now get a run that quietly judged no document on the"
                        + " quality of its conversion",
                () -> assertThat(line).contains("degenerateOutputConfidenceFloor").contains("number"));
        claim(
                "and their own text is quoted back to them",
                () -> assertThat(line).contains(A_MISTYPED_FLOOR));
    }

    /**
     * ADR-171 §3: an unreadable log floor applies no log rule, and the closing line says so in the words
     * it uses for the conversion-quality floor.
     */
    @Test
    @Story("A threshold nobody can parse is not a threshold, and the line says so")
    @DisplayName("A non-numeric log floor is reported the same way")
    @Issue("370")
    @Link(name = "ADR-171", url = Adr.LOGS_AND_TEXT_TOO_LARGE_FOR_DOCLING_ARE_OUT_OF_SCOPE, type = "adr")
    void aMistypedLogFloorIsReportedToo() {
        String line = NextAction.line(
                theLogFloorMistyped(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the log floor is named, with the word that says it is not a number, so an operator who"
                        + " mistyped it learns that no file was left out as a log rather than assuming the"
                        + " rule ran",
                () -> assertThat(line).contains("logTimestampShareFloor").contains("number"));
        claim(
                "their own text is quoted back to them",
                () -> assertThat(line).contains(A_MISTYPED_FLOOR));
        claim(
                "and it is still one line",
                () -> assertThat(line.lines()).hasSize(1));
    }

    /**
     * ADR-185 section 1: an {@code extractionAttempt} that is not a whole number of 1 or more is ignored,
     * as ADR-120 ignores every unreadable number, so stage 2 stays on its first attempt -- and the closing
     * line says so in the words it uses for the two floors above. Text is unreadable to every numeric key;
     * the other five are numbers {@code Double.parseDouble} accepts that number no attempt.
     *
     * <p>The profile is written as YAML and loaded through {@link ProfileStore}, not built with {@code
     * ProfileFixture}, so this compiles before the key exists. Until it does, {@code ProfileStore} refuses
     * a file carrying it, and this fails there.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"two", "1.5", "0", "-1", "NaN", "Infinity", "2147483648"})
    @Story("An extraction attempt that is not a whole number changes nothing, and the line says so")
    @DisplayName("An extraction attempt that is not a whole number of 1 or more is reported")
    @Issue("386")
    @Link(name = "ADR-185", url = Adr.RAISING_THE_EXTRACTION_ATTEMPT_ASKS_THE_CONVERTER_AGAIN, type = "adr")
    void anExtractionAttemptThatIsNotAWholeNumberIsReported(String written, @TempDir Path workingDirectory) {
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        profileStore.save(theRunValuesSet());
        ExtractionAttemptInProfile.write(profileStore, written, RECORDED_BY_THE_OPERATOR);

        String line = NextAction.line(
                profileStore.load(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the key is named, with the words that say what it should hold, so an operator who meant the"
                        + " converter to be asked again learns that it was not",
                () -> assertThat(line).contains(ExtractionAttemptInProfile.KEY).contains("whole number"));
        claim(
                "their own text is quoted back to them",
                () -> assertThat(line).contains('"' + written + '"'));
        claim(
                "and it is still one line",
                () -> assertThat(line.lines()).hasSize(1));
    }

    /**
     * ADR-282 section 3: a {@code degenerateOutputWordFloor} that is not a whole number of 0 or more is ignored, as
     * ADR-120 ignores every unreadable number, so nothing is removed for too few words -- and the closing line says
     * so, naming the page the number is read off. Text is unreadable to every numeric key; the others are numbers
     * {@code Double.parseDouble} accepts that number no count of words.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"two", "2.5", "-1", "NaN", "Infinity", "2147483648"})
    @Story("A word floor that is not a whole number changes nothing, and the line says so")
    @DisplayName("A word floor that is not a whole number of 0 or more is reported, with what was written and the page to read")
    @Issue("576")
    @Link(name = "ADR-282", url = Adr.FEWER_WORDS_THAN_THE_WORD_FLOOR_IS_DEGENERATE_OUTPUT, type = "adr")
    void aWordFloorThatIsNotAWholeNumberIsReported(String written) {
        Profile profile = ProfileFixture.profileFrom(theRunValuesSet())
                .degenerateOutputWordFloor(written, RECORDED_BY_THE_OPERATOR)
                .build();

        String line = NextAction.line(
                profile, SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the key is named, with the words that say what it should hold, so an operator who meant files of"
                        + " too few words to be removed learns that none was",
                () -> assertThat(line).contains("degenerateOutputWordFloor").contains("whole number"));
        claim(
                "their own text is quoted back to them",
                () -> assertThat(line).contains('"' + written + '"'));
        claim(
                "the page the number is read off is named",
                () -> assertThat(line).contains("word-count-distribution.html"));
        claim(
                "and it is still one line",
                () -> assertThat(line.lines()).hasSize(1));
    }

    @Test
    @Story("A word floor left unset is no stop")
    @DisplayName("An unset word floor is not named in the line, which goes on to the next value")
    @Issue("576")
    @Link(name = "ADR-282", url = Adr.FEWER_WORDS_THAN_THE_WORD_FLOOR_IS_DEGENERATE_OUTPUT, type = "adr")
    void anUnsetWordFloorIsNotNamed() {
        String line = NextAction.line(
                theRunValuesSet(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "observe before enforce: an unset floor removes nothing and is not a value the run stops for, so the"
                        + " line says nothing of it",
                () -> assertThat(line).doesNotContain("degenerateOutputWordFloor"));
    }

    /**
     * ADR-256 §5: a list of name endings holding a piece that is not an ending -- one with no dot before it,
     * nothing but dots, a wildcard, a path separator, or no ending at all -- is unreadable as a whole, ignored,
     * and named in the closing line with its text quoted back. Written as YAML through {@link
     * OutOfScopeNameEndingsInProfile}, so this compiles before the key exists and fails at the load until it does.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {".", "...", "P3A", ".P3A P3L", ".P3*", ".P3?", "terminali/.P3A", "terminali\\.P3A", ", ,"})
    @Story("A list of name endings nothing can be read from changes nothing, and the line says so")
    @DisplayName("A list of name endings that is not one is reported, with what was written")
    @Link(name = "ADR-256", url = Adr.NAME_ENDINGS_THE_PROFILE_LISTS_ARE_OUT_OF_SCOPE, type = "adr")
    void anUnreadableListOfNameEndingsIsReported(String written, @TempDir Path workingDirectory) {
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        profileStore.save(theRunValuesSet());
        OutOfScopeNameEndingsInProfile.write(profileStore, written, RECORDED_BY_THE_OPERATOR);

        String line = NextAction.line(
                profileStore.load(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the key is named, with the words that say what an ending is, so an operator who meant files to be"
                        + " left out learns that none was",
                () -> assertThat(line).contains(OutOfScopeNameEndingsInProfile.KEY).contains("beginning with a dot"));
        claim(
                "their own text is quoted back to them",
                () -> assertThat(line).contains('"' + written + '"'));
        claim(
                "and it is still one line",
                () -> assertThat(line.lines()).hasSize(1));
    }

    /** ADR-256 §2: endings in any case, separated by spaces, commas or both, and with more than one dot, are read. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {".P3A", ".p3a .DGN", ".P3A,.DGN", ".P3A, .DGN", ", .P3A", ".tar.gz", "  .P3A  "})
    @Story("A list of name endings nothing can be read from changes nothing, and the line says so")
    @DisplayName("A list of name endings that can be read is not reported")
    @Link(name = "ADR-256", url = Adr.NAME_ENDINGS_THE_PROFILE_LISTS_ARE_OUT_OF_SCOPE, type = "adr")
    void aReadableListOfNameEndingsIsNotReported(String written, @TempDir Path workingDirectory) {
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        profileStore.save(theRunValuesSet());
        OutOfScopeNameEndingsInProfile.write(profileStore, written, RECORDED_BY_THE_OPERATOR);

        String line = NextAction.line(
                profileStore.load(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the line does not name the key, because the run applied what it holds",
                () -> assertThat(line).doesNotContain(OutOfScopeNameEndingsInProfile.KEY));
    }

    /**
     * ADR-277 §2: a list of name endings kept as code is read as {@code outOfScopeNameEndings} is, so a piece that is
     * not an ending makes it unreadable as a whole, ignored, and named in the closing line with its text quoted back.
     * Written as YAML through {@link CodeNameEndingsInProfile}, so this compiles before the key exists.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {".", "...", "java", ".java c", ".j*", ".c?", "src/.java", "src\\.java", ", ,"})
    @Story("A list of name endings nothing can be read from changes nothing, and the line says so")
    @DisplayName("A list of name endings kept as code that is not one is reported, with what was written")
    @Link(name = "ADR-277", url = Adr.CODE_IS_RECOGNISED_BY_A_NAME_ENDING_OR_A_GENERATORS_MARK, type = "adr")
    void anUnreadableListOfCodeNameEndingsIsReported(String written, @TempDir Path workingDirectory) {
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        profileStore.save(theRunValuesSet());
        CodeNameEndingsInProfile.write(profileStore, written, RECORDED_BY_THE_OPERATOR);

        String line = NextAction.line(
                profileStore.load(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the key is named, with the words that say what an ending is and that nothing was kept as code for its"
                        + " name, so an operator who meant files to be kept as code learns that none was",
                () -> assertThat(line)
                        .contains(CodeNameEndingsInProfile.KEY)
                        .contains("beginning with a dot")
                        .contains("kept nothing as code for the ending of its name"));
        claim(
                "their own text is quoted back to them",
                () -> assertThat(line).contains('"' + written + '"'));
        claim(
                "and it is still one line",
                () -> assertThat(line.lines()).hasSize(1));
    }

    /** ADR-277 §2: the same endings {@code outOfScopeNameEndings} reads are read here, and none is reported. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {".java", ".java .C", ".java,.h", ".java, .h", ", .java", ".spec.ts", "  .py  "})
    @Story("A list of name endings nothing can be read from changes nothing, and the line says so")
    @DisplayName("A list of name endings kept as code that can be read is not reported")
    @Link(name = "ADR-277", url = Adr.CODE_IS_RECOGNISED_BY_A_NAME_ENDING_OR_A_GENERATORS_MARK, type = "adr")
    void aReadableListOfCodeNameEndingsIsNotReported(String written, @TempDir Path workingDirectory) {
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        profileStore.save(theRunValuesSet());
        CodeNameEndingsInProfile.write(profileStore, written, RECORDED_BY_THE_OPERATOR);

        String line = NextAction.line(
                profileStore.load(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the line does not name the key, because the run applied what it holds",
                () -> assertThat(line).doesNotContain(CodeNameEndingsInProfile.KEY));
    }

    /**
     * ADR-287 §7: a floor on the share of settings that no number can be read from is ignored as unset, so no file is
     * found to be a configuration file, and the closing line names the key and quotes back what is written. Written
     * as YAML through {@link ConfigurationSettingShareFloorInProfile}, so this compiles before the key exists.
     */
    @Test
    @Story("A threshold nobody can parse is not a threshold, and the line says so")
    @DisplayName("A floor on the share of settings that is not a number is reported, with what was written")
    @Issue("575")
    @Link(name = "ADR-287", url = Adr.LOGS_AND_CONFIGURATION_FILES_ARE_TOLD_BY_THEIR_BYTES, type = "adr")
    void anUnreadableConfigurationFloorIsReported(@TempDir Path workingDirectory) {
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        profileStore.save(theRunValuesSet());
        ConfigurationSettingShareFloorInProfile.write(profileStore, A_MISTYPED_FLOOR, RECORDED_BY_THE_OPERATOR);

        String line = NextAction.line(
                profileStore.load(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the key is named, with the words that say it is not a number and that no configuration file was found,"
                        + " so an operator who set it learns that the rule did not run",
                () -> assertThat(line)
                        .contains(ConfigurationSettingShareFloorInProfile.KEY)
                        .contains("which is not a number")
                        .contains("found no configuration file"));
        claim(
                "their own text is quoted back to them",
                () -> assertThat(line).contains('"' + A_MISTYPED_FLOOR + '"'));
        claim(
                "and it is still one line",
                () -> assertThat(line.lines()).hasSize(1));
    }

    /**
     * ADR-277 §6: an ending both lists hold leaves its files out of scope, and the closing line names the ending and
     * the two keys, so the operator learns that the files they listed as code were left out instead.
     */
    @Test
    @Story("A list of name endings nothing can be read from changes nothing, and the line says so")
    @DisplayName("An ending listed both as out of scope and as code is named in the closing line, which says its files were left out")
    @Link(name = "ADR-277", url = Adr.CODE_IS_RECOGNISED_BY_A_NAME_ENDING_OR_A_GENERATORS_MARK, type = "adr")
    void anEndingBothListsHoldIsReported(@TempDir Path workingDirectory) {
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        profileStore.save(theRunValuesSet());
        OutOfScopeNameEndingsInProfile.write(profileStore, ".P3A .INC", RECORDED_BY_THE_OPERATOR);
        CodeNameEndingsInProfile.write(profileStore, ".java .inc", RECORDED_BY_THE_OPERATOR);

        String line = NextAction.line(
                profileStore.load(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the line names both keys and the ending they share, in the form the run used, and says its files"
                        + " were left out of scope",
                () -> assertThat(line)
                        .contains(OutOfScopeNameEndingsInProfile.KEY)
                        .contains(CodeNameEndingsInProfile.KEY)
                        .contains("both list .inc")
                        .contains("left its files out of scope"));
        claim(
                "an ending only one list holds is not named",
                () -> assertThat(line).doesNotContain(".java").doesNotContain(".p3a"));
        claim(
                "and it is still one line",
                () -> assertThat(line.lines()).hasSize(1));
    }

    /**
     * ADR-277 §6 says the closing line names a shared ending "where nothing earlier is wanted". The architect gate
     * accepted the reading that places it straight after {@code codeNameEndings}' own line (§2): after every value
     * that line follows, and ahead of the lines for {@code translationSimilarityFloor}, {@code preferredLanguages}
     * and {@code extractionAttempt}. Each of those three is written here in a form no run reads, and the line is
     * still the shared ending's.
     */
    @Test
    @Story("A list of name endings nothing can be read from changes nothing, and the line says so")
    @DisplayName("An ending both lists hold is named ahead of an unreadable similarity floor, list of languages or attempt")
    @Link(name = "ADR-277", url = Adr.CODE_IS_RECOGNISED_BY_A_NAME_ENDING_OR_A_GENERATORS_MARK, type = "adr")
    void anEndingBothListsHoldIsNamedAheadOfTheValuesAfterIt() {
        Profile profile = ProfileFixture.profileFrom(theRunValuesSet())
                .outOfScopeNameEndings(".P3A .INC", RECORDED_BY_THE_OPERATOR)
                .codeNameEndings(".java .inc", RECORDED_BY_THE_OPERATOR)
                .translationSimilarityFloor("nought point eight", RECORDED_BY_THE_OPERATOR)
                .preferredLanguages("italian, english", RECORDED_BY_THE_OPERATOR)
                .extractionAttempt("two", RECORDED_BY_THE_OPERATOR)
                .build();

        String line = NextAction.line(
                profile, SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the line is the shared ending's, naming .inc and saying its files were left out of scope",
                () -> assertThat(line).contains("both list .inc").contains("left its files out of scope"));
        claim(
                "and names none of the three values after it, each of which would have had the line had no ending"
                        + " been shared",
                () -> assertThat(line)
                        .doesNotContain("translationSimilarityFloor")
                        .doesNotContain("preferredLanguages")
                        .doesNotContain("extractionAttempt"));
    }

    /**
     * ADR-277 §6, the other side of the same order: a value whose line comes before {@code codeNameEndings}' is
     * wanted earlier, so an unreadable log floor has the line and the shared ending waits for the next run.
     */
    @Test
    @Story("A list of name endings nothing can be read from changes nothing, and the line says so")
    @DisplayName("An unreadable log floor is named ahead of an ending both lists hold")
    @Link(name = "ADR-277", url = Adr.CODE_IS_RECOGNISED_BY_A_NAME_ENDING_OR_A_GENERATORS_MARK, type = "adr")
    void anUnreadableLogFloorIsNamedAheadOfAnEndingBothListsHold() {
        Profile profile = ProfileFixture.profileFrom(theLogFloorMistyped())
                .outOfScopeNameEndings(".P3A .INC", RECORDED_BY_THE_OPERATOR)
                .codeNameEndings(".java .inc", RECORDED_BY_THE_OPERATOR)
                .build();

        String line = NextAction.line(
                profile, SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the line is the log floor's",
                () -> assertThat(line).contains("logTimestampShareFloor").contains(A_MISTYPED_FLOOR));
        claim(
                "and does not name the shared ending",
                () -> assertThat(line).doesNotContain("both list"));
    }

    @Test
    @Story("The operator is never sent to a file the invocation did not write")
    @DisplayName("With no questions written, the line does not send the operator to the label file")
    void withNoQuestionsWrittenTheOperatorIsNotSentToTheLabelFile() {
        String line = NextAction.line(theRunValuesSet(), NOTHING_ANSWERED, NO_QUESTIONS_YET, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

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
        return ProfileFixture.profile().build();
    }

    @Test
    @Story("With the threshold answered, the next act is approving what was arranged")
    @DisplayName("With the threshold set and an arrangement written, the next action is to approve it")
    @Issue("175")
    @Link(name = "ADR-107", url = Adr.THE_ARRANGEMENT_GATE_APPROVES_A_NAMED_RUN, type = "adr")
    void withTheThresholdSetTheNextActionIsToApproveTheArrangement() {
        String line = NextAction.line(everythingSet(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, AN_ARRANGEMENT, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the approval is named now, because the arrangement it is about exists now -- which is"
                        + " exactly why it was not named at the end of any invocation before this one",
                () -> assertThat(line).contains("arrangementApproved"));
        claim(
                "and the line hands over the short name itself, ready to paste: the operator must not"
                        + " have to go and look up which arrangement they just read about",
                () -> assertThat(line).contains(AN_ARRANGEMENT));
        claim(
                "and it names the page that arrangement is described on, so the value is not copied"
                        + " out of a message into a file without anybody having looked at what it names",
                () -> assertThat(line).contains(ArrangementTasklet.ARRANGEMENT_FILE_NAME));
    }

    @Test
    @Story("With the threshold answered, the next act is approving what was arranged")
    @DisplayName("With the threshold set and nothing arranged, the approval is not asked for yet")
    @Issue("175")
    void withNothingArrangedTheApprovalIsNotAskedForYet() {
        String line = NextAction.line(everythingSet(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "nothing asks the operator to approve an arrangement that was never written: a value"
                        + " they cannot supply is not a next action, it is a dead end with instructions",
                () -> assertThat(line).doesNotContain("arrangementApproved"));
    }

    @Test
    @Story("With the threshold answered, the next act is approving what was arranged")
    @DisplayName("The approval line names the model the next invocation will generate under")
    @Issue("179")
    @Link(name = "ADR-114", url = Adr.THE_GENERATION_MODEL_IS_CONFIGURATION_WITH_A_DEFAULT, type = "adr")
    void theApprovalLineNamesTheModelGenerationWillRunUnder() {
        String line = NextAction.line(
                everythingSet(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, AN_ARRANGEMENT, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the operator is told which model is about to write the connecting text, before the call"
                        + " is spent rather than after: the model is not a value they have to supply, so a"
                        + " default they never chose would otherwise reach them only as provenance",
                () -> assertThat(line).contains(THE_GENERATION_MODEL));
        claim(
                "and it is still one line, because a disclosure that doubles the length of the closing"
                        + " sentence is a disclosure nobody finishes reading",
                () -> assertThat(line.lines()).hasSize(1));
    }

    @Test
    @Story("With the threshold answered, the next act is approving what was arranged")
    @DisplayName("With nothing arranged, no model is named either")
    @Issue("179")
    void withNothingArrangedNoModelIsNamed() {
        String line = NextAction.line(
                everythingSet(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "naming the model that would generate is only useful beside an arrangement to approve:"
                        + " on a line that asks the operator to go and fix something, it is noise",
                () -> assertThat(line).doesNotContain(THE_GENERATION_MODEL));
    }

    /** Step zero taken and nothing else: the seed folder named before the first invocation. */
    private static Profile onlyTheSeedFolderSet() {
        return ProfileFixture.profile().seedFolder(THE_SEED_FOLDER, RECORDED_BY_THE_OPERATOR).build();
    }

    /** Everything invocation 2 needs, answered; the threshold still the operator's to read off. */
    private static Profile theRunValuesSet() {
        return ProfileFixture.profile()
                .seedFolder(THE_SEED_FOLDER, RECORDED_BY_THE_OPERATOR)
                .boilerplateDocumentFrequencyFloor(THE_BOILERPLATE_FLOOR, RECORDED_BY_THE_OPERATOR)
                .build();
    }

    /** What an operator writes when their keyboard or their locale disagrees with Double.parseDouble. */
    private static final String A_MISTYPED_FLOOR = "0,62";

    /** Every run value answered, and a threshold written in a form no run can read. */
    private static Profile theFloorMistyped() {
        return ProfileFixture.profile()
                .seedFolder(THE_SEED_FOLDER, RECORDED_BY_THE_OPERATOR)
                .boilerplateDocumentFrequencyFloor(THE_BOILERPLATE_FLOOR, RECORDED_BY_THE_OPERATOR)
                .embeddingModel(THE_EMBEDDING_MODEL, RECORDED_BY_THE_OPERATOR)
                .relevanceScoreFloor(A_MISTYPED_FLOOR, RECORDED_BY_THE_OPERATOR)
                .build();
    }

    /** The end of the path: every value the four invocations ask for, answered. */
    private static Profile everythingSet() {
        return ProfileFixture.profile()
                .seedFolder(THE_SEED_FOLDER, RECORDED_BY_THE_OPERATOR)
                .boilerplateDocumentFrequencyFloor(THE_BOILERPLATE_FLOOR, RECORDED_BY_THE_OPERATOR)
                .embeddingModel(THE_EMBEDDING_MODEL, RECORDED_BY_THE_OPERATOR)
                .relevanceScoreFloor(THE_RELEVANCE_FLOOR, RECORDED_BY_THE_OPERATOR)
                .build();
    }

    /** The true end of the path: every value answered, the arrangement among them. */
    private static Profile everythingApproved() {
        return ProfileFixture.profile()
                .seedFolder(THE_SEED_FOLDER, RECORDED_BY_THE_OPERATOR)
                .boilerplateDocumentFrequencyFloor(THE_BOILERPLATE_FLOOR, RECORDED_BY_THE_OPERATOR)
                .embeddingModel(THE_EMBEDDING_MODEL, RECORDED_BY_THE_OPERATOR)
                .relevanceScoreFloor(THE_RELEVANCE_FLOOR, RECORDED_BY_THE_OPERATOR)
                .arrangementApproved(AN_ARRANGEMENT, RECORDED_BY_THE_OPERATOR)
                .build();
    }

    /** The provenance an operator is asked to record beside every answer they give. */
    private static final String RECORDED_BY_THE_OPERATOR = "recorded by the operator";

    /** The folder of known-relevant documents, as the operator would write it into the profile. */
    private static final String THE_SEED_FOLDER = "/corpus/exemplars";

    /** Stage 3's floor, answered: any value at all opens stage 4's gate, and the number is not read here. */
    private static final String THE_BOILERPLATE_FLOOR = "0.4";

    /** Gate 3's model, answered: the name opens the gate, and nothing here embeds anything. */
    private static final String THE_EMBEDDING_MODEL = "embeddinggemma";

    /** A threshold on the run's own scale, answered, and parseable -- the contrast to the mistyped one. */
    private static final String THE_RELEVANCE_FLOOR = "0.62";

    /** Every run value answered, with the repetition floor written in a form no run can read. */
    private static Profile theBoilerplateFloorMistyped() {
        return ProfileFixture.profile()
                .seedFolder(THE_SEED_FOLDER, RECORDED_BY_THE_OPERATOR)
                .boilerplateDocumentFrequencyFloor(A_MISTYPED_FLOOR, RECORDED_BY_THE_OPERATOR)
                .embeddingModel(THE_EMBEDDING_MODEL, RECORDED_BY_THE_OPERATOR)
                .build();
    }

    /** Every run value answered, with the conversion-quality floor written in a form no run can read. */
    private static Profile theConfidenceFloorMistyped() {
        return ProfileFixture.profile()
                .seedFolder(THE_SEED_FOLDER, RECORDED_BY_THE_OPERATOR)
                .boilerplateDocumentFrequencyFloor(THE_BOILERPLATE_FLOOR, RECORDED_BY_THE_OPERATOR)
                .degenerateOutputConfidenceFloor(A_MISTYPED_FLOOR, RECORDED_BY_THE_OPERATOR)
                .embeddingModel(THE_EMBEDDING_MODEL, RECORDED_BY_THE_OPERATOR)
                .build();
    }

    /** Every run value answered, with the log floor written in a form no run can read. */
    private static Profile theLogFloorMistyped() {
        return ProfileFixture.profile()
                .seedFolder(THE_SEED_FOLDER, RECORDED_BY_THE_OPERATOR)
                .boilerplateDocumentFrequencyFloor(THE_BOILERPLATE_FLOOR, RECORDED_BY_THE_OPERATOR)
                .logTimestampShareFloor(A_MISTYPED_FLOOR, RECORDED_BY_THE_OPERATOR)
                .embeddingModel(THE_EMBEDDING_MODEL, RECORDED_BY_THE_OPERATOR)
                .build();
    }
}
