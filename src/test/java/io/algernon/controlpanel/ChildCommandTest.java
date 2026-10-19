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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The command line the control panel starts the jar with (ADR-278 §1, landed by ADR-285 §5): {@code run} or {@code
 * label} only, after the two encoding flags, every path absolute, {@code CI=1}, and the working directory as the
 * child's current directory.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("562")
@Link(name = "ADR-285", url = Adr.THE_CONTROL_PANEL_IS_A_SECOND_JAR_LINKING_NO_CLASS_OF_THE_JAR, type = "adr")
@Link(name = "ADR-278", url = Adr.THE_CONTROL_PANEL_STARTS_THE_JAR_AS_ITS_CHILD_AND_STOPS_ONLY_ITS_OWN, type = "adr")
class ChildCommandTest {

    /** The JVM, as #567 will find it: absolute, and passed as given. */
    private static final Path JAVA = Path.of(ProcessHandle.current().info().command()
            .orElseGet(() -> Path.of(System.getProperty("java.home"), "bin", "java").toString()));

    /** Relative on purpose, so that each must be made absolute. */
    private static final Path JAR = Path.of("vespera.jar");

    private static final Path CORPUS_ROOT = Path.of("corpus");

    private static final Path WORKING_DIRECTORY = Path.of("work");

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
                () -> assertThat(ChildCommand.run(JAVA, JAR, CORPUS_ROOT, WORKING_DIRECTORY).command())
                        .containsExactlyElementsOf(with("run", absolute(CORPUS_ROOT), DB_DIR)));
    }

    @Test
    @Story("The control panel starts the jar as shipped")
    @DisplayName("Label is the jar's label command, with --auto only when asked, and the working directory absolute")
    void labelIsTheJarsLabelCommandWithAutoOnlyWhenAsked() {
        claim("asked for no model's answers, the command line ends with label and --db-dir",
                () -> assertThat(ChildCommand.label(JAVA, JAR, false, WORKING_DIRECTORY).command())
                        .containsExactlyElementsOf(with("label", DB_DIR)));
        claim("asked for them, it ends with label, --auto and --db-dir",
                () -> assertThat(ChildCommand.label(JAVA, JAR, true, WORKING_DIRECTORY).command())
                        .containsExactlyElementsOf(with("label", "--auto", DB_DIR)));
    }

    @Test
    @Story("The control panel starts the jar as shipped")
    @DisplayName("The child gets CI=1 in its environment and the working directory as its current directory")
    void theChildHasCiSetToOneAndTheWorkingDirectoryAsItsCurrentDirectory() {
        ChildCommand command = ChildCommand.run(JAVA, JAR, CORPUS_ROOT, WORKING_DIRECTORY);
        ProcessBuilder builder = command.processBuilder();

        claim("what the control panel adds to the child's environment is CI=1, and nothing else",
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
    @Story("The control panel starts the jar as shipped")
    @DisplayName("Only the run and label command lines can be built")
    void onlyRunAndLabelCanBeBuilt() {
        List<String> factories = Arrays.stream(ChildCommand.class.getMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .filter(method -> method.getReturnType() == ChildCommand.class)
                .map(Method::getName)
                .sorted()
                .toList();

        claim("no command line is built by a public constructor",
                () -> assertThat(ChildCommand.class.getConstructors()).isEmpty());
        claim("and the only ways to build one are label and run",
                () -> assertThat(factories).containsExactly("label", "run"));
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
        Path compiled = Path.of(ChildCommand.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolve(CONTROL_PANEL_FOLDER);
        List<String> texts = new ArrayList<>();
        try (Stream<Path> files = Files.walk(compiled)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                try (InputStream in = Files.newInputStream(file)) {
                    for (PoolEntry entry : ClassFile.of().parse(in.readAllBytes()).constantPool()) {
                        if (entry instanceof StringEntry text) {
                            texts.add(text.stringValue());
                        }
                    }
                }
            }
        }
        return texts;
    }
}
