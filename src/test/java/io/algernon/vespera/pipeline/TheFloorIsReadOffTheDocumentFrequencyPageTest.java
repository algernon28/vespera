package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.NumericValue;
import io.algernon.vespera.profile.ProfileFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * The two lines that send an operator to choose the boilerplate floor name the page it is read off,
 * {@code document-frequency.html}, and no table of {@code similarity} (ADR-262 §5): the last line of an
 * invocation, and stage 4's gate line.
 *
 * <p>The appender is attached in {@link BeforeEach} and detached in {@link AfterEach}, so no line logged by
 * another test is read here.
 */
@Epic("Pipeline")
@Feature("The operator is told the next value")
@Link(name = "ADR-262", url = Adr.STAGE_3_WRITES_THE_PAGE_THE_BOILERPLATE_FLOOR_IS_READ_OFF, type = "adr")
@Link(name = "ADR-098", url = Adr.FOUR_INVOCATIONS_AND_THE_NEXT_VALUE, type = "adr")
@Link(name = "ADR-080", url = Adr.THE_BOILERPLATE_FLOOR_IS_A_GATE, type = "adr")
class TheFloorIsReadOffTheDocumentFrequencyPageTest {

    private static final String THE_PAGE = "document-frequency.html";

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

    @Test
    @Story("The boilerplate floor is read off a page")
    @DisplayName("With nothing set, the last line names document-frequency.html for the boilerplate floor")
    void theLastLineNamesThePage() {
        String line = NextAction.line(ProfileFixture.profile().build(), 0, false, null, "a-generation-model:8b", false);

        claim(
                "the line sends the operator to the page stage 3 just wrote for the boilerplate floor",
                () -> assertThat(line)
                        .withFailMessage("the line was: %s", line)
                        .contains("boilerplateDocumentFrequencyFloor")
                        .contains(THE_PAGE));
        claim(
                "and not to similarity's tables, which take Python and a database no command holds to read",
                () -> assertThat(line)
                        .withFailMessage("the line was: %s", line)
                        .doesNotContain("shingle_document_frequency")
                        .doesNotContain("shingle_corpus_size"));
    }

    @Test
    @Story("The boilerplate floor is read off a page")
    @DisplayName("Stage 4's gate line names document-frequency.html, whether the key is unset or unreadable")
    void theShutGateNamesThePage() {
        RedundancyJobConfiguration.logGateClosed(gateLogger, NumericValue.unset());
        RedundancyJobConfiguration.logGateClosed(
                gateLogger, new NumericValue("0,4", "set by this test, mistyped on purpose", null));

        claim(
                "both reasons the gate is shut were logged",
                () -> assertThat(gateLines()).hasSize(2));
        for (String line : gateLines()) {
            claim(
                    "the gate line names the page the floor is read off, and no table of similarity",
                    () -> assertThat(line)
                            .withFailMessage("the gate line was: %s", line)
                            .contains(THE_PAGE)
                            .doesNotContain("shingle_document_frequency")
                            .doesNotContain("shingle_corpus_size"));
        }
    }

    private java.util.List<String> gateLines() {
        return logged.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("is gated"))
                .toList();
    }
}
