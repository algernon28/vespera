package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The presets the window passes as options of the command line, and the command lines it shows (ADR-344 §4): five
 * presets the jar ships, each passed only to the commands it is for, after the command's own arguments, in the table's
 * order; the label file passed to {@code label} and never to {@code label --auto}; and each command line shown as
 * exactly the arguments the child is started with.
 *
 * <p>Which values are passed at all, those that differ from what the child would read without them, is
 * {@code StartingValuesTest}'s; here every option given is one to pass.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
class TheCommandOptionsTest {

    /** The jar's own configuration, which every option must be a value of. */
    private static final Path SHIPPED_APPLICATION_YAML = Path.of("src", "main", "resources", "application.yaml");

    /** The five presets, in the table's order, as ADR-344 §4 lists them. */
    private static final List<String> THE_FIVE = List.of(
            "vespera.progress-interval",
            "vespera.sidecars.start",
            "vespera.sidecars.wait-at-most",
            "vespera.models.pull",
            "vespera.models.pull-stalled-at-most");

    /** A value for each of the five, other than the shipped one, given in an order other than the table's. */
    private static final Map<String, String> EVERY_OPTION_SET = everyOptionSet();

    @TempDir
    Path folder;

    @Test
    @Story("The operator sets the options of the commands in the window")
    @DisplayName("The options offered are five presets the jar ships, each for the commands that read it")
    void theOptionsAreFivePresetsTheJarShips() throws IOException {
        Map<String, String> shipped = StartingValues.readApplicationYaml(
                Files.readString(SHIPPED_APPLICATION_YAML, StandardCharsets.UTF_8));
        Map<String, List<String>> commandsOf = new LinkedHashMap<>();
        ChildCommand.OPTIONS.forEach(option -> commandsOf.put(option.property(), option.commands()));

        claim("the options are the progress page's interval, whether and how long the sidecars are started, and whether"
                        + " and how long a missing model is pulled, in that order, and no other",
                () -> assertThat(List.copyOf(commandsOf.keySet())).containsExactlyElementsOf(THE_FIVE));
        claim("each is passed to the commands that read it: the interval to both kinds of label and to run, the"
                        + " sidecars' to run and to the local model's answers, the models' to run alone",
                () -> assertThat(commandsOf).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "vespera.progress-interval", List.of("run", "label", "label --auto"),
                        "vespera.sidecars.start", List.of("run", "label --auto"),
                        "vespera.sidecars.wait-at-most", List.of("run", "label --auto"),
                        "vespera.models.pull", List.of("run"),
                        "vespera.models.pull-stalled-at-most", List.of("run"))));
        claim("and each is a value the jar's own application.yaml ships",
                () -> assertThat(shipped).containsKeys(THE_FIVE.toArray(String[]::new)));
    }

    @Test
    @Story("The operator sets the options of the commands in the window")
    @DisplayName("Each option is passed only to the commands it is for, after their own arguments, in the table's order")
    void eachOptionIsPassedOnlyToTheCommandsItIsForAfterTheirArguments() throws IOException {
        LaunchValues values = values();
        String workingDirectory = "--db-dir=" + Path.of(values.workingDirectory()).toAbsolutePath();

        List<String> run = argumentsOf(ChildCommand.run(java(), jar(), values).withOptions(EVERY_OPTION_SET));
        List<String> label = argumentsOf(ChildCommand.label(java(), jar(), false, values).withOptions(EVERY_OPTION_SET));
        List<String> auto = argumentsOf(ChildCommand.label(java(), jar(), true, values).withOptions(EVERY_OPTION_SET));
        List<String> publish = argumentsOf(ChildCommand.publish(java(), jar(), values, List.of("confluence"))
                .withOptions(EVERY_OPTION_SET));

        claim("run is given all five, after its corpus root and working directory, in the table's order",
                () -> assertThat(run).containsExactly("run", Path.of(values.corpusRoot()).toAbsolutePath().toString(),
                        workingDirectory, "--vespera.progress-interval=2s", "--vespera.sidecars.start=false",
                        "--vespera.sidecars.wait-at-most=60s", "--vespera.models.pull=false",
                        "--vespera.models.pull-stalled-at-most=120s"));
        claim("label is given the interval alone",
                () -> assertThat(label).containsExactly("label", workingDirectory, "--vespera.progress-interval=2s"));
        claim("label --auto is given the interval and the sidecars'",
                () -> assertThat(auto).containsExactly("label", "--auto", workingDirectory,
                        "--vespera.progress-interval=2s", "--vespera.sidecars.start=false",
                        "--vespera.sidecars.wait-at-most=60s"));
        claim("and publish none, after its destinations and working directory",
                () -> assertThat(publish).containsExactly("publish", "--to", "confluence", workingDirectory));
    }

    @Test
    @Story("The operator sets the options of the commands in the window")
    @DisplayName("The label file is passed to the recording of answers and never to the local model's")
    void theLabelFileIsPassedToLabelAndNeverToLabelAuto() throws IOException {
        LaunchValues values = values();
        Path labels = Files.createFile(folder.resolve("labels-kept-elsewhere.yaml"));
        String workingDirectory = "--db-dir=" + Path.of(values.workingDirectory()).toAbsolutePath();

        claim("label is given the file, absolute, as its argument before the working directory",
                () -> assertThat(argumentsOf(ChildCommand.label(java(), jar(), false, values).withLabelFile(labels)))
                        .containsExactly("label", labels.toAbsolutePath().toString(), workingDirectory));
        claim("and label --auto, which refuses a file, is given none",
                () -> assertThat(argumentsOf(ChildCommand.label(java(), jar(), true, values).withLabelFile(labels)))
                        .containsExactly("label", "--auto", workingDirectory));
    }

    @Test
    @Story("The operator sets the options of the commands in the window")
    @DisplayName("The command line shown is vespera and the arguments the child is started with, one holding a space quoted")
    void theLineShownIsTheCommandStarted() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("the corpus"));
        Path work = Files.createDirectories(folder.resolve("work"));
        LaunchValues values = new LaunchValues(corpusRoot.toString(), work.toString(), "", "", "", "", "", "");

        ChildCommand run = ChildCommand.run(java(), jar(), values).withOptions(Map.of("vespera.models.pull", "false"));
        List<String> started = argumentsOf(run);
        String expected = "vespera " + String.join(" ", started.stream()
                .map(argument -> argument.contains(" ") ? "\"" + argument + "\"" : argument)
                .toList());

        claim("the child is started with a corpus root holding a space, so the line below has one to quote",
                () -> assertThat(started).anyMatch(argument -> argument.contains(" ")));
        claim("the line shown is vespera and the arguments the child is started with, in their order, the one holding"
                        + " a space in double quotes",
                () -> assertThat(run.shown()).isEqualTo(expected));
    }

    private LaunchValues values() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path work = Files.createDirectories(folder.resolve("work"));
        return new LaunchValues(corpusRoot.toString(), work.toString(), "", "", "", "", "", "");
    }

    private Path java() {
        return folder.resolve("java");
    }

    private Path jar() {
        return folder.resolve("vespera.jar");
    }

    /** The arguments after {@code -jar <jar>}: the command, its own arguments and the options. */
    private List<String> argumentsOf(ChildCommand command) {
        List<String> line = command.command();
        return line.subList(line.indexOf("-jar") + 2, line.size());
    }

    private static Map<String, String> everyOptionSet() {
        Map<String, String> set = new LinkedHashMap<>();
        set.put("vespera.models.pull-stalled-at-most", "120s");
        set.put("vespera.models.pull", "false");
        set.put("vespera.sidecars.wait-at-most", "60s");
        set.put("vespera.sidecars.start", "false");
        set.put("vespera.progress-interval", "2s");
        return set;
    }
}
