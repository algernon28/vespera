package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.NumericValue;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * The sentence an operator reads when stage 4's gate is shut, and which of its two reasons it names
 * (ADR-080, ADR-120).
 *
 * <p>The gate has had two reasons only since ADR-120. Before it, a value no number could be read from
 * never reached this line at all — reading it threw, and the invocation ended there — so the sentence
 * could assert the one remaining reason and be right. Now that such a value arrives as an ordinary
 * shut gate, a line saying the key is unset would tell someone who wrote something that they wrote
 * nothing, which is the one reading {@code CONTEXT.md} rules out for this state.
 *
 * <p>This is the only place stage 4's gate is worded, by that class's own rule, so it is the only
 * place the wording can be checked.
 */
@Epic("Census")
@Feature("Profile")
@Issue("203")
@Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
@Link(name = "ADR-080", url = Adr.THE_BOILERPLATE_FLOOR_IS_A_GATE, type = "adr")
class RedundancyGateLineTest {

    /** A decimal comma: the likeliest real mistake, and the one that used to be fatal in this key. */
    private static final String A_DECIMAL_COMMA = "0,4";

    /** Why a test wrote the value, in the place an operator would explain themselves. */
    private static final String WHY = "set by this test, mistyped on purpose";

    private ListAppender<ILoggingEvent> logged;
    private Logger gateLogger;

    @BeforeEach
    void captureTheGateLine() {
        logged = new ListAppender<>();
        logged.start();
        gateLogger = (Logger) LoggerFactory.getLogger(RedundancyJobConfiguration.class);
        gateLogger.addAppender(logged);
    }

    @AfterEach
    void releaseTheGateLine() {
        gateLogger.detachAppender(logged);
        logged.stop();
    }

    /** The one line this gate says, whatever else the logger carried. */
    private String theLine() {
        return logged.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("is gated"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the gate said nothing at all"));
    }

    @Test
    @Story("A shut gate says which of its two reasons shut it")
    @DisplayName("With nobody having answered the key, the gate says it is unset")
    void anUnansweredKeyIsReportedAsUnset() {
        RedundancyJobConfiguration.logGateClosed(gateLogger, NumericValue.unset());

        claim(
                "the sentence says the key is unset, which is exactly what it is: census wrote the key"
                        + " and nobody has answered it, and telling the operator so is what sends them to"
                        + " the measurement they read it off",
                () -> assertThat(theLine()).contains("boilerplateDocumentFrequencyFloor is unset"));
        claim(
                "and it still points at the two tables the number is read off, because naming a key with"
                        + " no way to choose a value for it is not an action anyone can take",
                () -> assertThat(theLine()).contains("shingle_document_frequency"));
    }

    @Test
    @Story("A shut gate says which of its two reasons shut it")
    @DisplayName("With the key mistyped, the gate quotes it back instead of calling it unset")
    void aMistypedKeyIsQuotedBackRatherThanCalledUnset() {
        RedundancyJobConfiguration.logGateClosed(gateLogger, new NumericValue(A_DECIMAL_COMMA, WHY, null));

        claim(
                "what the operator actually wrote is quoted back at them: they are looking for a comma or"
                        + " a stray letter in their own text, and a sentence describing the mistake"
                        + " instead of showing it leaves them hunting",
                () -> assertThat(theLine()).contains(A_DECIMAL_COMMA));
        claim(
                "and the line never calls this key unset, which is the whole of the difference: somebody"
                        + " did write a value here, and telling them they wrote nothing sends them looking"
                        + " for a key they would find already filled in",
                () -> assertThat(theLine()).doesNotContain("boilerplateDocumentFrequencyFloor is unset"));
        claim(
                "it still says the stage was gated, because the outcome is the same one either reason"
                        + " produces -- nothing was removed and no run was minted",
                () -> assertThat(theLine()).contains("No stage-4 run was minted"));
    }
}
