package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.deliverable.Drawing;
import io.algernon.vespera.deliverable.Plugin;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
 * {@code vespera publish --to} delivers to each destination it names, in turn (ADR-333 §6): a bare publish is
 * Confluence; the names are split at {@code ,} and delivered in the order written, a name written twice delivered
 * once; a name no plugin has is refused before anything is delivered, exit 2, in one line naming the plugins the
 * beans have; one destination stopping, or throwing, does not stop the next; each has its one closing line, a stop's
 * on standard error; the invocation's last line is the last destination's closing line, and it exits 1 if any
 * stopped. Each destination finds its own rendering: the last generation run whose tree holds that plugin's folder
 * with its list or a {@code drawing.txt}.
 *
 * <p>One invocation is one {@code publish} command line parsed by picocli over {@code VesperaCommand.Publish}, built
 * with the plugins it is handed, as Spring hands it the beans: two scripted destinations named as the jar's two
 * plugins are, which deliver nothing and write down what they were asked for. The rendering each reads is laid out by
 * hand in each generation run's tree: its folder, its list and a {@code drawing.txt} that says {@code drawn}. No site
 * and no folder is touched, so the class is untagged and CI runs it.
 *
 * <p>The line naming the destinations a refused name could have been lists the plugins' names sorted, as the drawing
 * orders the plugins by {@link Plugin#name()} (ADR-333 §2): the record's line, {@code it knows confluence, website.},
 * is that order, and the order Spring hands the beans in is no order of the record's.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Operation")
@Feature("The publish")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
@Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
class PublishToEachDestinationInTurnInvocationTest {

    private static final String CONFLUENCE = "confluence";

    private static final String WEBSITE = "website";

    /** The line refusing a name no plugin has, the plugins' names after it (ADR-333 §6). */
    private static final String NO_DESTINATION_NOWHERE =
            "vespera publish has no destination nowhere: it knows confluence, website.";

    /** ADR-308 §5's refusal, as a bare publish still says it where no Confluence rendering is drawn (ADR-333 §6). */
    private static final String A_BARE_PUBLISH_FINDS_NOTHING = "vespera publish sent nothing: no Confluence rendering"
            + " is drawn in this working directory. Run vespera run to the deliverable, and publish again.";

    /** The same refusal for a destination named on --to, naming it (ADR-333 §6). */
    private static final String NO_WEBSITE_RENDERING = "vespera publish sent nothing to website: no website rendering"
            + " is drawn in this working directory. Run vespera run to the deliverable, and publish again.";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path workingDirectory;

    @Test
    @Story("A publish names its destinations")
    @DisplayName("A bare publish delivers to Confluence alone, and its closing line is the last line")
    void aBarePublishIsConfluence() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        RunId run = generationRun(ledger, CONFLUENCE, WEBSITE);
        Destinations destinations = new Destinations();

        Invocation invocation = publish(ledger, destinations);

        claim("the publish ended 0",
                () -> assertThat(invocation.exit()).isEqualTo(CommandLine.ExitCode.OK));
        claim("Confluence alone was asked, for the run drawn, though the website rendering is drawn too",
                () -> assertThat(destinations.asked).containsExactly(CONFLUENCE + " " + run.value()));
        claim("and its closing line is the invocation's last line, on standard output",
                () -> {
                    assertThat(invocation.lines().getLast()).isEqualTo("out: " + CONFLUENCE + " delivered");
                    assertThat(invocation.errors()).isEmpty();
                });
    }

    @Test
    @Story("A publish names its destinations")
    @DisplayName("--to delivers to each destination in the order written, a name written twice once, each with its closing line")
    void eachDestinationInTheOrderWrittenAndOnce() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        RunId run = generationRun(ledger, CONFLUENCE, WEBSITE);
        Destinations destinations = new Destinations();

        Invocation invocation = publish(ledger, destinations, "--to", "website,confluence,website");

        claim("the publish ended 0",
                () -> assertThat(invocation.exit()).isEqualTo(CommandLine.ExitCode.OK));
        claim("the website was asked first and Confluence second, each once, each for the run drawn",
                () -> assertThat(destinations.asked)
                        .containsExactly(WEBSITE + " " + run.value(), CONFLUENCE + " " + run.value()));
        claim("each destination's own lines and its closing line are on standard output, in turn, and the last line is"
                        + " the last destination's closing line",
                () -> assertThat(invocation.lines()).containsExactly(
                        "out: " + WEBSITE + " says a line of its own",
                        "out: " + WEBSITE + " delivered",
                        "out: " + CONFLUENCE + " says a line of its own",
                        "out: " + CONFLUENCE + " delivered"));
    }

    @Test
    @Story("A publish names its destinations")
    @DisplayName("A name no plugin has is refused before anything is delivered, exit 2, naming the destinations there are")
    void anUnknownNameIsRefusedBeforeAnything() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        generationRun(ledger, CONFLUENCE, WEBSITE);
        Destinations destinations = new Destinations();

        Invocation invocation = publish(ledger, destinations, "--to", "website,nowhere");

        claim("the publish was refused as a usage error, exit 2",
                () -> assertThat(invocation.exit()).isEqualTo(CommandLine.ExitCode.USAGE));
        claim("nothing was delivered, not even to the website named before the unknown name",
                () -> assertThat(destinations.asked).isEmpty());
        claim("and the refusal names the unknown name and the plugins' names, sorted, read from the beans",
                () -> assertThat(invocation.errors()).contains(NO_DESTINATION_NOWHERE));
    }

    @Test
    @Story("One destination stopping does not stop the next")
    @DisplayName("A destination that stops does not stop the next; its closing line is on standard error, and the invocation exits 1")
    void aStopDoesNotStopTheNext() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        RunId run = generationRun(ledger, CONFLUENCE, WEBSITE);
        Destinations destinations = new Destinations().stopping(CONFLUENCE);

        Invocation invocation = publish(ledger, destinations, "--to", "confluence,website");

        claim("the publish ended 1, a destination having stopped",
                () -> assertThat(invocation.exit()).isEqualTo(CommandLine.ExitCode.SOFTWARE));
        claim("the website was still asked, after Confluence",
                () -> assertThat(destinations.asked)
                        .containsExactly(CONFLUENCE + " " + run.value(), WEBSITE + " " + run.value()));
        claim("Confluence's closing line is on standard error, the website's on standard output, and the last line is"
                        + " the website's",
                () -> {
                    assertThat(invocation.lines()).contains("err: " + CONFLUENCE + " stopped",
                            "out: " + WEBSITE + " delivered");
                    assertThat(invocation.lines().getLast()).isEqualTo("out: " + WEBSITE + " delivered");
                });
    }

    @Test
    @Story("One destination stopping does not stop the next")
    @DisplayName("A destination that throws is a stop, its closing line naming it and the exception's type and never its message")
    void aThrowIsAStopNamedByItsType() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        generationRun(ledger, CONFLUENCE, WEBSITE);
        Destinations destinations = new Destinations().throwing(CONFLUENCE);

        Invocation invocation = publish(ledger, destinations, "--to", "confluence,website");

        claim("the publish ended 1, and the website was still asked",
                () -> {
                    assertThat(invocation.exit()).isEqualTo(CommandLine.ExitCode.SOFTWARE);
                    assertThat(destinations.asked).hasSize(2).last().asString().startsWith(WEBSITE);
                });
        claim("one line on standard error names Confluence and the type it threw",
                () -> assertThat(invocation.errors())
                        .anyMatch(line -> line.contains(CONFLUENCE) && line.contains("IllegalStateException")));
        claim("no line carries the exception's message, which could name a document (ADR-274 §8)",
                () -> assertThat(invocation.lines()).noneMatch(line -> line.contains(Destinations.THE_MESSAGE)));
        claim("and the last line is the website's closing line",
                () -> assertThat(invocation.lines().getLast()).isEqualTo("out: " + WEBSITE + " delivered"));
    }

    @Test
    @Story("Each destination finds its own rendering")
    @DisplayName("Each destination is delivered the last generation run whose tree holds its own rendering")
    void eachDestinationFindsItsOwnRendering() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        RunId both = generationRun(ledger, CONFLUENCE, WEBSITE);
        RunId confluenceAlone = generationRun(ledger, CONFLUENCE);
        Destinations destinations = new Destinations();

        Invocation invocation = publish(ledger, destinations, "--to", "website,confluence");

        claim("the publish ended 0",
                () -> assertThat(invocation.exit()).isEqualTo(CommandLine.ExitCode.OK));
        claim("the website was delivered the earlier run, the last whose tree holds website/, and Confluence the later",
                () -> assertThat(destinations.asked)
                        .containsExactly(WEBSITE + " " + both.value(), CONFLUENCE + " " + confluenceAlone.value()));
    }

    @Test
    @Story("Each destination finds its own rendering")
    @DisplayName("A destination with no rendering drawn sends nothing, in one line naming it, and the next is still delivered")
    void aDestinationWithNoRenderingIsRefusedAloneAndNamed() throws IOException {
        Ledger ledger = new Ledger(jdbcTemplate);
        RunId run = generationRun(ledger, CONFLUENCE);
        Destinations destinations = new Destinations();

        Invocation invocation = publish(ledger, destinations, "--to", "website,confluence");

        claim("the publish ended 1, the website having sent nothing",
                () -> assertThat(invocation.exit()).isEqualTo(CommandLine.ExitCode.SOFTWARE));
        claim("the website was not asked, and Confluence was",
                () -> assertThat(destinations.asked).containsExactly(CONFLUENCE + " " + run.value()));
        claim("the refusal names the website in ADR-308 §5's line, on standard error, and Confluence's closing line is"
                        + " the last line",
                () -> {
                    assertThat(invocation.errors()).contains(NO_WEBSITE_RENDERING);
                    assertThat(invocation.lines().getLast()).isEqualTo("out: " + CONFLUENCE + " delivered");
                });
    }

    @Test
    @Story("Each destination finds its own rendering")
    @DisplayName("A bare publish with nothing drawn says what it said before --to")
    void aBarePublishWithNothingDrawnSaysWhatItSaid() {
        Destinations destinations = new Destinations();

        Invocation invocation = publish(new Ledger(jdbcTemplate), destinations);

        claim("the publish refused, exit 1, asking nobody",
                () -> {
                    assertThat(invocation.exit()).isEqualTo(CommandLine.ExitCode.SOFTWARE);
                    assertThat(destinations.asked).isEmpty();
                });
        claim("in ADR-308 §5's line as it stood",
                () -> assertThat(invocation.errors()).containsExactly(A_BARE_PUBLISH_FINDS_NOTHING));
    }

    /** How one invocation ended: its exit, and every line it wrote, each after {@code out: } or {@code err: }. */
    private record Invocation(int exit, List<String> lines) {

        List<String> errors() {
            return lines.stream().filter(line -> line.startsWith("err: ")).map(line -> line.substring(5)).toList();
        }
    }

    /** One {@code vespera publish} with {@code arguments}, parsed by picocli, both streams captured in one order. */
    private Invocation publish(Ledger ledger, Destinations destinations, String... arguments) {
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        PrintStream out = System.out;
        PrintStream err = System.err;
        int exit;
        try (PrintStream capturedOut = linesInto(lines, "out: ");
                PrintStream capturedErr = linesInto(lines, "err: ")) {
            System.setOut(capturedOut);
            System.setErr(capturedErr);
            VesperaCommand.Publish publish =
                    new VesperaCommand.Publish(ledger, destinations.plugins(), workingDirectory, "");
            exit = new CommandLine(publish).execute(arguments);
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
        return new Invocation(exit, List.copyOf(lines));
    }

    /** A stream adding each line written to it, after {@code tag}, to {@code lines}. */
    private static PrintStream linesInto(List<String> lines, String tag) {
        return new PrintStream(new OutputStream() {
            private final ByteArrayOutputStream line = new ByteArrayOutputStream();

            @Override
            public synchronized void write(int b) {
                if (b == '\n') {
                    lines.add(tag + line.toString(StandardCharsets.UTF_8).replace("\r", ""));
                    line.reset();
                } else {
                    line.write(b);
                }
            }
        }, true, StandardCharsets.UTF_8);
    }

    /**
     * A generation run, its step recorded finished, its tree holding, for each of {@code drawn}, the plugin's folder
     * with its list and a {@code drawing.txt} that says {@code drawn}.
     */
    private RunId generationRun(Ledger ledger, String... drawn) throws IOException {
        WalkId walk = ledger.walks().startWalk(workingDirectory.resolve("corpus"));
        RunId run = ledger.runs().startRun(
                StageModules.GENERATION.stage(), "test", "drawn " + String.join(",", drawn) + " " + System.nanoTime(),
                walk, List.of());
        ledger.runs().finishStep(run, StepNames.GENERATION);
        Path tree = workingDirectory.resolve("deliverable").resolve(run.value());
        for (String plugin : drawn) {
            Path folder = Files.createDirectories(tree.resolve(plugin));
            Files.writeString(folder.resolve(Destinations.listOf(plugin)), "{}");
            Files.writeString(folder.resolve(Plugin.DRAWING_STATE_FILE_NAME), Plugin.DRAWN + "\n");
        }
        return run;
    }

    /**
     * The two destinations, named as the jar's plugins are, handed to the command website first, so that the order the
     * beans come in is not the order a line names them in; each delivers, stops or throws as it is told, and writes
     * down, as {@code <name> <generation run>}, what it was asked for.
     */
    private static final class Destinations {

        static final String THE_MESSAGE = "the scripted message no line may carry";

        final List<String> asked = new ArrayList<>();

        private final List<String> stopping = new ArrayList<>();

        private final List<String> throwing = new ArrayList<>();

        Destinations stopping(String name) {
            stopping.add(name);
            return this;
        }

        Destinations throwing(String name) {
            throwing.add(name);
            return this;
        }

        static String listOf(String name) {
            return CONFLUENCE.equals(name) ? "pages.json" : "files.json";
        }

        List<Plugin> plugins() {
            return List.of(destination(WEBSITE), destination(CONFLUENCE));
        }

        private Plugin destination(String name) {
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
                    out.accept(name + " says a line of its own");
                    if (throwing.contains(name)) {
                        throw new IllegalStateException(THE_MESSAGE);
                    }
                    return stopping.contains(name)
                            ? new Delivery(false, name + " stopped")
                            : new Delivery(true, name + " delivered");
                }
            };
        }
    }
}
