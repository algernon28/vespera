package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.PrintWriter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * The jar's own lines are logged on a logger of their own, a line at a time, at INFO for what was printed on
 * standard output and WARN for what was printed on standard error (ADR-364 §4).
 */
@Epic("Architecture")
@Feature("Logging")
@Issue("773")
@Link(name = "ADR-364", url = Adr.THE_OUTPUT_PANEL_IS_COPYABLE_TEXT_AND_EVERY_LINE_GOES_THROUGH_LOGBACK, type = "adr")
class OperatorLinesTest {

    private final Logger operator = (Logger) LoggerFactory.getLogger(OperatorLines.LOGGER_NAME);
    private ListAppender<ILoggingEvent> logged;

    @BeforeEach
    void capture() {
        logged = new ListAppender<>();
        logged.start();
        operator.addAppender(logged);
    }

    @AfterEach
    void release() {
        operator.detachAppender(logged);
    }

    @Test
    @Story("The jar's own lines go through logback")
    @DisplayName("What is written to either writer is logged a line at a time, INFO for one and WARN for the other")
    void whatIsWrittenToEitherWriterIsLoggedALineAtATime() throws Exception {
        PrintWriter out = OperatorLines.out();
        out.println("first line");
        out.println("second line");
        out.print("third line without its end");
        out.flush();
        PrintWriter err = OperatorLines.err();
        err.println("a refusal");
        err.flush();

        claim(
                "four events on the operator logger: three at INFO from the first writer, the unended line among them"
                        + " on flush, and one at WARN from the second, each line's words without its end",
                () -> {
                    assertThat(logged.list).extracting(ILoggingEvent::getFormattedMessage)
                            .containsExactly("first line", "second line", "third line without its end", "a refusal");
                    assertThat(logged.list).extracting(ILoggingEvent::getLevel)
                            .containsExactly(Level.INFO, Level.INFO, Level.INFO, Level.WARN);
                });
        // The logger's level is the second claim of this class, over a context of its own; one test method holds both,
        // since the suite holds at most 700 (ADR-368).
        theOperatorLoggerLogsAtInfoWhateverItsParentsLevel();
    }

    private void theOperatorLoggerLogsAtInfoWhateverItsParentsLevel() throws Exception {
        LoggerContext context = new LoggerContext();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        try (var configuration = OperatorLinesTest.class.getResourceAsStream("/logback-test.xml")) {
            configurator.doConfigure(configuration);
        }
        Logger parent = context.getLogger("io.algernon.vespera");
        parent.setLevel(Level.WARN);
        Logger declared = context.getLogger(OperatorLines.LOGGER_NAME);
        ListAppender<ILoggingEvent> inThisContext = new ListAppender<>();
        inThisContext.setContext(context);
        inThisContext.start();
        declared.addAppender(inThisContext);

        declared.info("an operator line");

        claim(
                "the declared logger has its own level INFO and no additivity, so an INFO line still reaches its appender"
                        + " although io.algernon.vespera is at WARN",
                () -> {
                    assertThat(declared.getLevel()).isEqualTo(Level.INFO);
                    assertThat(declared.isAdditive()).isFalse();
                    assertThat(inThisContext.list).extracting(ILoggingEvent::getFormattedMessage)
                            .isEqualTo(List.of("an operator line"));
                });
        context.stop();
    }
}
