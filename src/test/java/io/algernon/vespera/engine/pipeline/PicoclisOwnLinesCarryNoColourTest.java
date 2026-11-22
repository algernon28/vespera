package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.objenesis.ObjenesisStd;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;

/**
 * The command line {@code VesperaCommand.commandLine()} builds draws no colour, whatever picocli would read of the
 * environment, so that picocli's own refusals and usage, which ADR-364 §4 logs on the operator logger, are their
 * words alone on the console, in {@code vespera.log}, in the output file and in the output panel (ADR-443 §1).
 *
 * <p>An environment variable cannot be set from a test, so picocli's colour is forced on here by the one switch of its
 * own a test can set, the system property {@code picocli.ansi} at {@code true}, which turns its {@code AUTO} on as
 * {@code TERM} or {@code OSTYPE} does on Windows; the property is put back as it was after the test.
 *
 * <p>The three subcommands are made without their constructors, as {@code HelpAndVersionTest} makes them: the refusal
 * is picocli's parse alone and reaches none of their collaborators.
 */
@Epic("Architecture")
@Feature("Logging")
@Link(name = "ADR-443", url = Adr.PICOCLIS_OWN_LINES_CARRY_NO_COLOUR, type = "adr")
class PicoclisOwnLinesCarryNoColourTest {

    /** Picocli's system property that turns its colour on whatever the streams are. */
    private static final String PICOCLI_ANSI = "picocli.ansi";

    /** The character every colour code of picocli's starts with. */
    private static final char ESCAPE = '\u001B';

    private static final String AN_UNKNOWN_OPTION = "--no-such-option";

    private final Logger operator = (Logger) LoggerFactory.getLogger(OperatorLines.LOGGER_NAME);
    private ListAppender<ILoggingEvent> logged;
    private String picocliAnsiBefore;

    @BeforeEach
    void forceColourAndCapture() {
        picocliAnsiBefore = System.getProperty(PICOCLI_ANSI);
        System.setProperty(PICOCLI_ANSI, "true");
        logged = new ListAppender<>();
        logged.start();
        operator.addAppender(logged);
    }

    @AfterEach
    void putBack() {
        operator.detachAppender(logged);
        if (picocliAnsiBefore == null) {
            System.clearProperty(PICOCLI_ANSI);
        } else {
            System.setProperty(PICOCLI_ANSI, picocliAnsiBefore);
        }
    }

    @Test
    @Story("Picocli's own lines carry no colour")
    @DisplayName("A refused option and the usage after it carry no colour codes, even where colour is forced on")
    void aRefusedOptionAndItsUsageCarryNoColourWhereColourIsForcedOn() {
        ObjenesisStd withoutTheirCollaborators = new ObjenesisStd();
        CommandLine commandLine = new VesperaCommand(
                withoutTheirCollaborators.newInstance(VesperaCommand.Run.class),
                withoutTheirCollaborators.newInstance(VesperaCommand.Label.class),
                withoutTheirCollaborators.newInstance(VesperaCommand.Publish.class))
                .commandLine();

        commandLine.execute("run", AN_UNKNOWN_OPTION);

        List<String> lines = logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        List<String> linesCarryingAnEscape = lines.stream()
                .filter(line -> line.indexOf(ESCAPE) >= 0)
                .map(line -> line.replace(String.valueOf(ESCAPE), "<ESC>"))
                .toList();
        Map<String, Ansi> colourOfEachCommand = new LinkedHashMap<>();
        colourOfEachCommand.put(commandLine.getCommandName(), commandLine.getColorScheme().ansi());
        commandLine.getSubcommands().forEach((name, subcommand) ->
                colourOfEachCommand.put(name, subcommand.getColorScheme().ansi()));

        claim("no line the refusal and its usage logged carries the escape character a colour code starts with, so"
                        + " the console, the log, the output file and the output panel hold the words alone",
                () -> assertThat(linesCarryingAnEscape)
                        .as("the lines carrying a colour code, the escape character written as <ESC>")
                        .isEmpty());
        claim("the refusal names the unknown option in its words alone, and the usage of the command follows it",
                () -> {
                    assertThat(lines).contains("Unknown option: '" + AN_UNKNOWN_OPTION + "'");
                    assertThat(lines).anyMatch(line -> line.startsWith("Usage: vespera run"));
                });
        claim("the command line and each of its four subcommands, run, label, publish and serve, draw with colour off",
                () -> assertThat(colourOfEachCommand)
                        .containsOnlyKeys("vespera", "run", "label", "publish", "serve")
                        .allSatisfy((command, colour) -> assertThat(colour).as(command).isEqualTo(Ansi.OFF)));
    }
}
