package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import picocli.CommandLine;

/**
 * {@code vespera publish --to} refuses each destination it names where the working directory's last {@code vespera run}
 * arrived at no generation run (ADR-393 §7): each in one line on standard error naming it, in the order written, its
 * plugin not asked, and the invocation exits 1, though an earlier run's rendering of each is drawn and finished. A bare
 * publish's own line is held by {@code PublishTakesTheGenerationRunTheLastRunInvocationArrivedAtTest}.
 *
 * <p>One invocation is one {@code publish} command line parsed by picocli over {@code VesperaCommand.Publish}, built
 * with two scripted destinations named as the jar's two plugins are, which deliver nothing and write down what they
 * were asked for. No site and no folder is touched.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Operation")
@Feature("The publish")
@Issue("778")
@Link(name = "ADR-393", url = Adr.A_WORKING_DIRECTORY_IS_NAMED_FOR_ITS_CORPUS_ROOT_AND_DAY_AND_PUBLISH_TAKES_WHAT_THE_LAST_RUN_REACHED,
        type = "adr")
class PublishToADestinationTheLastRunDidNotReachSendsNothingInvocationTest {

    private static final String CONFLUENCE = "confluence";

    private static final String WEBSITE = "website";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path workingDirectory;

    @Test
    @Story("A publish delivers what the last run reached")
    @DisplayName("Each destination named is refused in a line of its own, in the order written, where the last run stopped before the deliverable")
    void eachDestinationNamedIsRefusedWhereTheLastRunReachedNoDeliverable() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        RunId earlier = drawnGenerationRun(ledger);
        copy("20261112T080000Z", earlier);
        copy("20261113T080000Z", null);
        List<String> asked = new ArrayList<>();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream before = System.err;
        int exit;
        try (PrintStream captured = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            System.setErr(captured);
            VesperaCommand.Publish publish = new VesperaCommand.Publish(ledger,
                    List.of(destination(CONFLUENCE, asked), destination(WEBSITE, asked)), workingDirectory, "");
            exit = new CommandLine(publish).execute("--to", WEBSITE + "," + CONFLUENCE);
        } finally {
            System.setErr(before);
        }
        List<String> lines = errors.toString(StandardCharsets.UTF_8).strip().lines().map(String::strip).toList();

        claim("standard error holds one line for each destination, website's then confluence's as --to names them,"
                        + " each naming its destination and saying the last run did not reach the deliverable",
                () -> assertThat(lines).containsExactly(refusalOf(WEBSITE), refusalOf(CONFLUENCE)));
        claim("neither destination was asked to deliver anything, though the earlier run's rendering of each is drawn",
                () -> assertThat(asked).isEmpty());
        claim("and the publish exits 1", () -> assertThat(exit).isEqualTo(CommandLine.ExitCode.SOFTWARE));
    }

    /** The line refusing {@code name}, as ADR-393 §7 words it for a destination {@code --to} names. */
    private static String refusalOf(String name) {
        return "vespera publish sent nothing to " + name + ": the last vespera run did not reach the deliverable."
                + " Run vespera run to the deliverable, and publish again.";
    }

    /** A destination named {@code name} that delivers, writing down as {@code <name> <generation run>} what it was asked. */
    private static Plugin destination(String name, List<String> asked) {
        return new Plugin() {
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
                asked.add(name + " " + generationRun);
                return new Delivery(true, name + " delivered");
            }
        };
    }

    private static String listOf(String name) {
        return CONFLUENCE.equals(name) ? "pages.json" : "files.json";
    }

    /**
     * A generation run, its step recorded finished, its tree holding, for each destination, its folder with its list
     * and a {@code drawing.txt} that says {@code drawn}: what the last generation made would have published.
     */
    private RunId drawnGenerationRun(Ledger ledger) throws IOException {
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId run = ledger.runs().startRun(StageModules.GENERATION.stage(), "test", "the earlier floor", walk, List.of());
        ledger.runs().finishStep(run, StepNames.GENERATION);
        Path tree = workingDirectory.resolve("deliverable").resolve(run.value());
        for (String plugin : List.of(CONFLUENCE, WEBSITE)) {
            Path folder = Files.createDirectories(tree.resolve(plugin));
            Files.writeString(folder.resolve(listOf(plugin)), "{}");
            Files.writeString(folder.resolve(Plugin.DRAWING_STATE_FILE_NAME), Plugin.DRAWN + "\n");
        }
        return run;
    }

    /**
     * The copy {@code logs/report-<id>.json} of an ended {@code vespera run}, its {@code arrivedAt} naming {@code
     * generation} under the generation stage beside an arrangement run, or the arrangement run alone where it is null.
     */
    private void copy(String id, RunId generation) throws IOException {
        String arrivedAt = "[{\"stage\":\"" + StageModules.ARRANGEMENT.stage() + "\",\"run\":\"arrangement-of-" + id
                + "\"}" + (generation == null ? ""
                        : ",{\"stage\":\"" + StageModules.GENERATION.stage() + "\",\"run\":\"" + generation.value()
                                + "\"}")
                + "]";
        String report = "{\"format\":" + InvocationReport.FORMAT + ",\"invocation\":{\"id\":\"" + id
                + "\",\"pid\":4242,\"command\":\"run\",\"outcome\":\"completed\"},\"arrivedAt\":" + arrivedAt + "}";
        Path logs = Files.createDirectories(workingDirectory.resolve("logs"));
        Files.writeString(logs.resolve("report-" + id + ".json"), report, StandardCharsets.UTF_8);
    }
}
