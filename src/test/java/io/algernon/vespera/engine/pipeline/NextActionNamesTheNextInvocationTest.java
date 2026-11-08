package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.pipeline.NextInvocationFile.Contents;
import io.algernon.vespera.engine.pipeline.NextInvocationFile.Invocation;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What {@link NextAction} gives beside each closing line for the file the control panel reads (ADR-294 §2): the
 * command the next act ends on, the profile keys the line asks for in the order it names them, whether the questions
 * are to be answered, and the arrangement to approve. One branch of {@code NextAction} words the line and gives the
 * contents, so each claim here compares the contents whole and holds them to the line they came with.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("565")
@Link(name = "ADR-294", url = Adr.THE_CONTROL_PANEL_LEARNS_WHAT_THE_NEXT_INVOCATION_WANTS_FROM_A_FILE, type = "adr")
@Link(name = "ADR-317", url = Adr.AN_APPROVAL_OF_AUTO_OPENS_THE_GATE_ON_EVERY_ARRANGEMENT, type = "adr")
class NextActionNamesTheNextInvocationTest {

    private static final String NOTHING_ARRANGED = null;

    private static final int NOTHING_ANSWERED = 0;

    private static final boolean NO_QUESTIONS_YET = false;

    private static final String THE_GENERATION_MODEL = "a-generation-model:8b";

    private static final boolean NO_APPROVAL_MATCHES_THIS_INVOCATION = false;

    @Test
    @Story("The control panel learns which values the next run wants")
    @DisplayName("With nothing set, the next command is a run wanting the seed folder and the boilerplate floor, in that order")
    void withNothingSetTheNextRunWantsTheTwoRunValuesInOrder() {
        NextAction.Closing closing = NextAction.closing(ProfileFixture.profile().build(), NOTHING_ANSWERED,
                NO_QUESTIONS_YET, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim("the next command is a run wanting seedFolder and boilerplateDocumentFrequencyFloor, in that order,"
                        + " with no questions to answer and no arrangement to approve",
                () -> assertThat(closing.next()).isEqualTo(run("seedFolder", "boilerplateDocumentFrequencyFloor")));
        namedByTheLineInItsOrder(closing);
    }

    /** Every key the file wants is a word of the line, and the line names them in the same order. */
    private static void namedByTheLineInItsOrder(NextAction.Closing closing) {
        List<Integer> where = closing.next().wants().stream().map(closing.line()::indexOf).toList();
        claim("every key the file wants is named by the closing line, in the order the file lists them",
                () -> assertThat(where).doesNotContain(-1).isSorted());
    }

    private static Contents run(String... wanted) {
        return new Contents(Invocation.RUN, List.of(wanted), false, Optional.empty());
    }
}
