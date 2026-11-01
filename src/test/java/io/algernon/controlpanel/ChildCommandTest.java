package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The command line the control panel starts the jar with (ADR-278 §1, landed by ADR-285 §5): {@code run}, {@code
 * label} or {@code publish} (ADR-308 §6) only, after the two encoding flags, every path absolute, {@code CI=1}, and the working directory as the
 * child's current directory.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("562")
@Link(name = "ADR-285", url = Adr.THE_CONTROL_PANEL_IS_A_SECOND_JAR_LINKING_NO_CLASS_OF_THE_JAR, type = "adr")
@Link(name = "ADR-278", url = Adr.THE_CONTROL_PANEL_STARTS_THE_JAR_AS_ITS_CHILD_AND_STOPS_ONLY_ITS_OWN, type = "adr")
@Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
class ChildCommandTest {

    /** The JVM, as #567 will find it: absolute, and passed as given. */
    private static final Path JAVA = Path.of(ProcessHandle.current().info().command()
            .orElseGet(() -> Path.of(System.getProperty("java.home"), "bin", "java").toString()));

    /** Relative on purpose, so that each must be made absolute. */
    private static final Path JAR = Path.of("vespera.jar");

    private static final Path CORPUS_ROOT = Path.of("corpus");

    private static final Path WORKING_DIRECTORY = Path.of("work");

    /** The launch values naming the corpus root and the working directory, and nothing else. */
    private static final LaunchValues THE_TWO = new LaunchValues(CORPUS_ROOT.toString(),
            WORKING_DIRECTORY.toString(), "", "", "", "", "", "");

    /** Every launch value named, and a last seed folder that must reach the child nowhere. */
    private static final String LAST_SEED_FOLDER = "seeds-last-confirmed";

    private static final LaunchValues EVERY_VALUE = new LaunchValues(CORPUS_ROOT.toString(),
            WORKING_DIRECTORY.toString(), "out", "image:v1", "http://docling", "http://ollama", "qwen3:14b",
            LAST_SEED_FOLDER);

    private static final List<String> BEFORE_THE_COMMAND = List.of(
            JAVA.toString(), "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-jar", absolute(JAR));

    private static final String DB_DIR = "--db-dir=" + absolute(WORKING_DIRECTORY);

    private static final List<String> NEVER_SPELLED = List.of("--version", "--help");

    /** A text the command line needs, which the scan of the compiled classes must find. */
    private static final String A_TEXT_THE_COMMAND_LINE_NEEDS = "-Dstdout.encoding=UTF-8";

    private static final String CONTROL_PANEL_FOLDER = "io/algernon/controlpanel";

    @Test
    @Story("The control panel starts the jar as shipped")
    @DisplayName("Run is the jar's run command, after both encoding flags, with the corpus root and the working directory absolute")
    void runIsTheJarsRunCommandWithBothEncodingFlagsAndAbsolutePaths() {
        claim("the command line is the JVM, the two encoding flags, -jar and the jar, then run, the corpus root and"
                        + " --db-dir naming the working directory, every path absolute, in that order",
                () -> assertThat(ChildCommand.run(JAVA, JAR, THE_TWO).command())
                        .containsExactlyElementsOf(with("run", absolute(CORPUS_ROOT), DB_DIR)));
    }

    @Test
    @Story("The control panel starts the jar as shipped")
    @DisplayName("Label is the jar's label command, with --auto only when asked, and the working directory absolute")
    void labelIsTheJarsLabelCommandWithAutoOnlyWhenAsked() {
        claim("asked for no model's answers, the command line ends with label and --db-dir",
                () -> assertThat(ChildCommand.label(JAVA, JAR, false, THE_TWO).command())
                        .containsExactlyElementsOf(with("label", DB_DIR)));
        claim("asked for them, it ends with label, --auto and --db-dir",
                () -> assertThat(ChildCommand.label(JAVA, JAR, true, THE_TWO).command())
                        .containsExactlyElementsOf(with("label", "--auto", DB_DIR)));
    }

    @Test
    @Story("The control panel starts the jar as shipped")
    @DisplayName("The child gets CI=1 in its environment and the working directory as its current directory")
    void theChildHasCiSetToOneAndTheWorkingDirectoryAsItsCurrentDirectory() {
        ChildCommand command = ChildCommand.run(JAVA, JAR, THE_TWO);
        ProcessBuilder builder = command.processBuilder();

        claim("with no launch value but the corpus root and the working directory, what the control panel adds to"
                        + " the child's environment is CI=1, and nothing else",
                () -> assertThat(command.environment()).isEqualTo(Map.of("CI", "1")));
        claim("the process builder starts that command line, with CI=1 in its environment",
                () -> {
                    assertThat(builder.command()).isEqualTo(command.command());
                    assertThat(builder.environment()).containsEntry("CI", "1");
                });
        claim("and the working directory, absolute, is the child's current directory",
                () -> {
                    assertThat(command.directory()).isEqualTo(WORKING_DIRECTORY.toAbsolutePath());
                    assertThat(builder.directory()).isEqualTo(WORKING_DIRECTORY.toAbsolutePath().toFile());
                });
    }

    @Test
    @Story("The control panel passes its launch values to the jar")
    @DisplayName("The child of Run and of Label gets CI=1 and the launch values in its environment, and the same command line")
    @Issue("564")
    @Link(name = "ADR-292", url = Adr.THE_CONTROL_PANEL_REMEMBERS_ITS_LAUNCH_VALUES, type = "adr")
    void theChildGetsCiAndTheLaunchValuesInItsEnvironment() {
        Map<String, String> expected = new HashMap<>(EVERY_VALUE.toChildEnvironment());
        expected.put("CI", "1");
        for (ChildCommand command : List.of(ChildCommand.run(JAVA, JAR, EVERY_VALUE),
                ChildCommand.label(JAVA, JAR, true, EVERY_VALUE))) {
            claim("the child's environment adds CI=1 and the launch values' five variables, and nothing else",
                    () -> assertThat(command.environment()).isEqualTo(expected));
            claim("the process builder carries every one of them",
                    () -> assertThat(command.processBuilder().environment()).containsAllEntriesOf(expected));
        }
        claim("and the command lines are those built from the corpus root and the working directory alone",
                () -> {
                    assertThat(ChildCommand.run(JAVA, JAR, EVERY_VALUE).command())
                            .isEqualTo(ChildCommand.run(JAVA, JAR, THE_TWO).command());
                    assertThat(ChildCommand.label(JAVA, JAR, true, EVERY_VALUE).command())
                            .isEqualTo(ChildCommand.label(JAVA, JAR, true, THE_TWO).command());
                });
    }

    @Test
    @Story("The control panel refuses what it cannot start")
    @DisplayName("Run without a corpus root and Label without a working directory are not built, the refusal given")
    @Issue("564")
    @Link(name = "ADR-292", url = Adr.THE_CONTROL_PANEL_REMEMBERS_ITS_LAUNCH_VALUES, type = "adr")
    void aRefusedCommandIsNotBuilt() {
        LaunchValues noCorpusRoot = new LaunchValues("", WORKING_DIRECTORY.toString(), "", "", "", "", "", "");
        LaunchValues noWorkingDirectory = new LaunchValues(CORPUS_ROOT.toString(), "", "", "", "", "", "", "");

        claim("Run with no corpus root is refused, with Run's refusal as the message",
                () -> assertThatThrownBy(() -> ChildCommand.run(JAVA, JAR, noCorpusRoot))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage(noCorpusRoot.refusalToRun().orElseThrow()));
        claim("Label with no working directory is refused, with Label's refusal as the message",
                () -> assertThatThrownBy(() -> ChildCommand.label(JAVA, JAR, false, noWorkingDirectory))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage(noWorkingDirectory.refusalToLabel().orElseThrow()));
    }

    @Test
    @Story("The control panel passes its launch values to the jar")
    @DisplayName("The last seed folder is in neither the command line nor the environment of Run or Label")
    @Issue("564")
    @Link(name = "ADR-292", url = Adr.THE_CONTROL_PANEL_REMEMBERS_ITS_LAUNCH_VALUES, type = "adr")
    void theLastSeedFolderReachesTheChildNowhere() {
        for (ChildCommand command : List.of(ChildCommand.run(JAVA, JAR, EVERY_VALUE),
                ChildCommand.label(JAVA, JAR, false, EVERY_VALUE))) {
            claim("no argument of the command line holds the last seed folder",
                    () -> assertThat(command.command()).noneMatch(argument -> argument.contains(LAST_SEED_FOLDER)));
            claim("and no variable the control panel adds holds it",
                    () -> assertThat(command.environment().values())
                            .noneMatch(value -> value.contains(LAST_SEED_FOLDER)));
        }
    }

    @Test
    @Story("The control panel starts the jar as shipped")
    @Issue("633")
    @DisplayName("Publish is the jar's publish command with the working directory absolute, and adds no Confluence value to the child's environment")
    void publishIsTheJarsPublishCommandAndAddsNoConfluenceValue() {
        ChildCommand publish = ChildCommand.publish(JAVA, JAR, EVERY_VALUE);

        claim("the command line ends with publish and --db-dir",
                () -> assertThat(ChildCommand.publish(JAVA, JAR, THE_TWO).command())
                        .containsExactlyElementsOf(with("publish", DB_DIR)));
        claim("and no variable the control panel adds names Confluence: the child inherits the five values from the"
                        + " operator's environment, and the control panel holds none of them",
                () -> assertThat(publish.environment().keySet()).noneMatch(name -> name.contains("CONFLUENCE")));
        claim("a publish with no working directory is not built, the label's refusal given",
                () -> assertThatThrownBy(() -> ChildCommand.publish(JAVA, JAR,
                                new LaunchValues(CORPUS_ROOT.toString(), "", "", "", "", "", "", "")))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    @Story("The control panel starts the jar as shipped")
    @DisplayName("Only the run, label and publish command lines can be built")
    void onlyRunLabelAndPublishCanBeBuilt() {
        List<String> factories = Arrays.stream(ChildCommand.class.getMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .filter(method -> method.getReturnType() == ChildCommand.class)
                .map(Method::getName)
                .sorted()
                .toList();

        claim("no command line is built by a public constructor",
                () -> assertThat(ChildCommand.class.getConstructors()).isEmpty());
        claim("and the only ways to build one are label, publish and run",
                () -> assertThat(factories).containsExactly("label", "publish", "run"));
    }

    @Test
    @Story("The control panel starts the jar as shipped")
    @DisplayName("No class of the control panel holds the text --version or --help")
    void noClassOfTheControlPanelSpellsVersionOrHelp() throws IOException, URISyntaxException {
        List<String> texts = textsOfTheControlPanel();

        claim("the scan read the control panel's compiled texts, checked by one the command line needs",
                () -> assertThat(texts).anyMatch(text -> text.contains(A_TEXT_THE_COMMAND_LINE_NEEDS)));
        claim("and none of them spells --version or --help, each of which boots the jar's context and takes its lock",
                () -> assertThat(texts).noneMatch(text -> NEVER_SPELLED.stream().anyMatch(text::contains)));
    }

    private static List<String> with(String... after) {
        List<String> command = new ArrayList<>(BEFORE_THE_COMMAND);
        command.addAll(List.of(after));
        return command;
    }

    private static String absolute(Path path) {
        return path.toAbsolutePath().toString();
    }

    /** Every string text of every class compiled from {@code src/main} under {@code io.algernon.controlpanel}. */
    static List<String> textsOfTheControlPanel() throws IOException, URISyntaxException {
        return textsOfEachClassOfTheControlPanel().values().stream().flatMap(List::stream).toList();
    }

    /** The same texts by the simple name of the class they were compiled from, a nested class's with its outer's. */
    static Map<String, List<String>> textsOfEachClassOfTheControlPanel() throws IOException, URISyntaxException {
        Path compiled = Path.of(ChildCommand.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolve(CONTROL_PANEL_FOLDER);
        Map<String, List<String>> texts = new TreeMap<>();
        try (Stream<Path> files = Files.walk(compiled)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String className = file.getFileName().toString().replaceFirst("[$.].*", "");
                try (InputStream in = Files.newInputStream(file)) {
                    for (PoolEntry entry : ClassFile.of().parse(in.readAllBytes()).constantPool()) {
                        if (entry instanceof StringEntry text) {
                            texts.computeIfAbsent(className, name -> new ArrayList<>()).add(text.stringValue());
                        }
                    }
                }
            }
        }
        return texts;
    }
}
