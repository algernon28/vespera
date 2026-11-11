package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.Drawing;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import picocli.CommandLine;
import tools.jackson.databind.JsonNode;

/**
 * {@code vespera publish} writes the invocation report (ADR-362 section 3) with its {@code publish} field, one entry for
 * each destination in the order delivered, and prints the report's two lines (section 7): the first before the first
 * destination, the report line last, after the last destination's closing line. One invocation is one {@code publish}
 * command line parsed by picocli over {@code VesperaCommand.Publish}, built with two scripted destinations named as the
 * jar's two plugins are and given a real report, as Spring hands it the bean; the report lines are read off the logger
 * the application logs through.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Operation")
@Feature("The publish")
@Issue("763")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class PublishWritesTheReportInvocationTest {

    private static final String CONFLUENCE = "confluence";

    private static final String WEBSITE = "website";

    private static final String REPORT_PREFIX = "Report: ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path workingDirectory;

    private ListAppender<ILoggingEvent> logged;

    private ch.qos.logback.classic.Logger applicationLogger;

    @BeforeEach
    void captureTheLines() {
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("io.algernon.vespera");
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseTheLines() {
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("The report holds each destination")
    @DisplayName("A publish to both destinations, one stopping, writes a report holding two destinations with their outcomes and lines, the invocation stopped, and the report line last")
    void eachDestinationsOutcomeAndLineIsInTheReport() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        generationRun(ledger);
        Seen whileTheWebsiteIsDelivered = new Seen();
        Path report = InvocationReport.pathIn(workingDirectory);

        int exit = publish(ledger, whileTheWebsiteIsDelivered, CONFLUENCE, "--to", "confluence,website");

        JsonNode written = TheReportFile.in(workingDirectory);
        JsonNode destinations = written.get("publish").get("destinations");
        claim("the publish ended 1, a destination having stopped", () -> assertThat(exit).isEqualTo(CommandLine.ExitCode.SOFTWARE));
        claim("the report names the publish command, says it ended stopped with exit code 1, and has no steps",
                () -> {
                    assertThat(written.get("invocation").get("command").asString()).isEqualTo("publish");
                    assertThat(written.get("invocation").get("state").asString()).isEqualTo("ended");
                    assertThat(written.get("invocation").get("outcome").asString()).isEqualTo("stopped");
                    assertThat(written.get("invocation").get("exitCode").asLong()).isEqualTo(1);
                    assertThat(written.get("steps")).isEmpty();
                });
        claim("its publish field holds two entries, in the order delivered, each with its destination, its outcome and its closing line whole",
                () -> {
                    assertThat(destinations).hasSize(2);
                    assertThat(destinations.get(0).get("destination").asString()).isEqualTo(CONFLUENCE);
                    assertThat(destinations.get(0).get("outcome").asString()).isEqualTo("stopped");
                    assertThat(destinations.get(0).get("line").asString()).isEqualTo(CONFLUENCE + " stopped");
                    assertThat(destinations.get(1).get("destination").asString()).isEqualTo(WEBSITE);
                    assertThat(destinations.get(1).get("outcome").asString()).isEqualTo("delivered");
                    assertThat(destinations.get(1).get("line").asString()).isEqualTo(WEBSITE + " delivered");
                });
        claim("the report's closing is the last destination's line, and the stop is among its refusals",
                () -> {
                    assertThat(written.get("closing").asString()).isEqualTo(WEBSITE + " delivered");
                    assertThat(written.get("refusals")).hasSize(1);
                    assertThat(written.get("refusals").get(0).asString()).isEqualTo(CONFLUENCE + " stopped");
                    assertThat(written.get("label").isNull()).isTrue();
                });
        claim("the report was written again after the first destination ended: read while the second was delivered, it was still running and held one destination",
                () -> {
                    assertThat(whileTheWebsiteIsDelivered.state).isEqualTo("running");
                    assertThat(whileTheWebsiteIsDelivered.destinations).isEqualTo(1);
                });
        claim("its copy in logs holds the same text",
                () -> assertThat(Files.readString(TheReportFile.copyOf(workingDirectory, written)))
                        .isEqualTo(TheReportFile.textIn(workingDirectory)));
        List<String> lines = loggedLines();
        claim("the first line logged names report.html, and the last is the report line, after the closing line, which is not logged",
                () -> {
                    assertThat(lines.getFirst()).isEqualTo(REPORT_PREFIX + ReportHtml.pathIn(workingDirectory));
                    assertThat(lines.getLast()).startsWith(REPORT_PREFIX + report + ", drawn as "
                            + ReportHtml.pathIn(workingDirectory) + ". Nothing was opened: ");
                    assertThat(lines.stream().filter(line -> line.startsWith(REPORT_PREFIX))).hasSize(2);
                });
    }

    private List<String> loggedLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** One {@code vespera publish} with {@code arguments}, over a real report; standard output and error are swallowed. */
    private int publish(Ledger ledger, Seen seen, String stopping, String... arguments) {
        PrintStream out = System.out;
        PrintStream err = System.err;
        try (PrintStream swallowed = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)) {
            System.setOut(swallowed);
            System.setErr(swallowed);
            VesperaCommand.Publish publish =
                    new VesperaCommand.Publish(ledger, destinations(seen, stopping), workingDirectory, "");
            publish.writeTheReportWith(SixJobsForTests.aReport(workingDirectory));
            return new CommandLine(publish).execute(arguments);
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
    }

    /** A generation run, its step recorded finished, its tree holding each destination's folder, list and a drawing that says drawn. */
    private RunId generationRun(Ledger ledger) throws IOException {
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId run = ledger.runs().startRun(
                StageModules.GENERATION.stage(), "test", "drawn " + System.nanoTime(), walk, List.of());
        ledger.runs().finishStep(run, StepNames.GENERATION);
        Path tree = workingDirectory.resolve("deliverable").resolve(run.value());
        for (String plugin : List.of(CONFLUENCE, WEBSITE)) {
            Path folder = Files.createDirectories(tree.resolve(plugin));
            Files.writeString(folder.resolve(listOf(plugin)), "{}");
            Files.writeString(folder.resolve(Plugin.DRAWING_STATE_FILE_NAME), Plugin.DRAWN + "\n");
        }
        return run;
    }

    private static String listOf(String name) {
        return CONFLUENCE.equals(name) ? "pages.json" : "files.json";
    }

    /** What the report said while the website was being delivered. */
    private static final class Seen {
        String state;
        int destinations = -1;
    }

    /** Confluence stops where {@code stopping} names it; the website reads the report as it is handed its rendering. */
    private List<Plugin> destinations(Seen seen, String stopping) {
        List<Plugin> plugins = new ArrayList<>();
        for (String name : List.of(WEBSITE, CONFLUENCE)) {
            plugins.add(new Plugin() {
                @Override
                public String name() {
                    return name;
                }

                @Override
                public String listName() {
                    return listOf(name);
                }

                @Override
                public Drawing drawing(DrawingContext context) {
                    throw new UnsupportedOperationException("a publish draws nothing");
                }

                @Override
                public Delivery deliver(Path rendering, String generationRun, Path workingDirectory, Consumer<String> out) {
                    if (WEBSITE.equals(name)) {
                        JsonNode now = TheReportFile.in(workingDirectory);
                        seen.state = now.get("invocation").get("state").asString();
                        seen.destinations = now.get("publish").get("destinations").size();
                    }
                    return name.equals(stopping)
                            ? new Delivery(false, name + " stopped")
                            : new Delivery(true, name + " delivered");
                }
            });
        }
        return plugins;
    }
}
