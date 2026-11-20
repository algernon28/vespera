package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.algernon.vespera.engine.pipeline.BesideTheJarUnwritableException;
import io.algernon.vespera.engine.pipeline.DisplayPresence;
import io.algernon.vespera.engine.pipeline.NoWorkingDirectoryNamedException;
import io.algernon.vespera.ui.ControlPanel;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The jar's entry point (ADR-342 §2): started with no argument, the Vespera jar opens the control panel's window;
 * started with any argument, it is the command line as before, and nothing of the control panel is reached.
 *
 * <p>{@code EntryPoint} is the jar's {@code Start-Class}, in {@code io.algernon.vespera} beside {@code VesperaApplication},
 * outside both the capability modules and the control panel's package, and the one class that names both
 * (ADR-367). It extends no JavaFX {@code Application}, so that the
 * Java launcher's check for JavaFX's modules is never reached and Spring Boot's launcher stays the jar's
 * {@code Main-Class}. That the packaged jar names it is {@code PackagedJarIT}'s.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-342", url = Adr.THE_CONTROL_PANEL_IS_A_JAVAFX_WINDOW_INSIDE_THE_JAR, type = "adr")
@Link(name = "ADR-367", url = Adr.THE_JARS_PACKAGES_LIE_UNDER_ENGINE_PUBLISH_AND_UI, type = "adr")
class EntryPointTest {

    /** The class whose {@code main} opens the window. */
    private static final String THE_CONTROL_PANEL = "io.algernon.vespera.ui.ControlPanel";

    /** The class whose {@code main} runs the command line. */
    private static final String THE_COMMAND_LINE = "io.algernon.vespera.VesperaApplication";

    @Test
    @Story("The operator opens the control panel by starting the jar")
    @DisplayName("The jar started with no argument opens the control panel")
    void noArgumentOpensTheControlPanel() {
        claim("a start with no argument at all opens the control panel's window and runs no command",
                () -> assertThat(EntryPoint.chosen(new String[0])).isEqualTo(EntryPoint.Started.CONTROL_PANEL));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("startsWithArguments")
    @Story("The operator opens the control panel by starting the jar")
    @DisplayName("The jar started with any argument runs the command line as before")
    void anyArgumentIsTheCommandLine(String[] arguments) {
        claim("a start with any argument is the command line, whatever the argument is",
                () -> assertThat(EntryPoint.chosen(arguments)).isEqualTo(EntryPoint.Started.COMMAND_LINE));
        claim("and the command line is given every argument as it stands, a --headless that is not first among them",
                () -> assertThat(EntryPoint.passedOn(arguments)).containsExactly(arguments));
    }

    /** A command with a --headless that is not first, the start an argument could be mistaken for the window's flag. */
    static Stream<Arguments> startsWithArguments() {
        return Stream.of(
                Arguments.of(Named.of("the headless flag after a command",
                        new String[] {"run", "D:/corpus", "--headless"})));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("headlessFirst")
    @Story("The jar can always be started without the window")
    @DisplayName("The headless flag first never opens the window, and the command line is given the arguments after it")
    void headlessFirstNeverOpensTheWindowAndIsDroppedBeforeTheCommandLine(String[] arguments, String[] passedOn) {
        claim("a start whose first argument is --headless is the command line, never the window",
                () -> assertThat(EntryPoint.chosen(arguments)).isEqualTo(EntryPoint.Started.COMMAND_LINE));
        claim("and the command line is given every argument after the flag, the flag itself dropped, so the flag"
                        + " alone is a start of the command line with no argument, which prints its usage",
                () -> assertThat(EntryPoint.passedOn(arguments)).containsExactly(passedOn));
    }

    /** The flag before a command and its arguments. */
    static Stream<Arguments> headlessFirst() {
        return Stream.of(
                Arguments.of(Named.of("the flag before a run of a corpus root",
                                new String[] {"--headless", "run", "D:/corpus"}),
                        new String[] {"run", "D:/corpus"}));
    }

    @Test
    @Story("The operator opens the control panel by starting the jar")
    @DisplayName("The jar's entry point is no JavaFX application, lies outside both packages, and names the two it starts")
    void theEntryPointIsNoJavaFxApplicationAndNamesBothItStarts() {
        JavaClass entryPoint = new ClassFileImporter().importClass(EntryPoint.class);
        Set<String> named = entryPoint.getDirectDependenciesFromSelf().stream()
                .map(Dependency::getTargetClass)
                .map(JavaClass::getName)
                .collect(Collectors.toSet());

        claim("it is no JavaFX application, so the Java launcher never asks for JavaFX's modules",
                () -> assertThat(javafx.application.Application.class.isAssignableFrom(EntryPoint.class)).isFalse());
        claim("it lies in the root package, io.algernon.vespera itself, neither in the jar's modules nor in the control"
                        + " panel's package",
                () -> assertThat(EntryPoint.class.getPackageName()).isEqualTo("io.algernon.vespera"));
        claim("and it names the control panel's main class and the command line's, the two it starts",
                () -> assertThat(named).contains(THE_CONTROL_PANEL, THE_COMMAND_LINE));
    }

    /** What a start did: the arguments the command line got, whether the window was tried, and standard error. */
    private record Did(List<List<String>> commandLines, int windowsTried, String err) {
    }

    private static Did started(String[] arguments, DisplayPresence.Answer display) {
        List<List<String>> commandLines = new ArrayList<>();
        int[] windows = {0};
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        EntryPoint.start(arguments, () -> display, kind -> java.util.Optional.empty(),
                () -> {
                    windows[0]++;
                    return new ControlPanel.Opening(ControlPanel.Opening.Result.NO_WINDOW, "the toolkit said no");
                },
                passed -> commandLines.add(List.of(passed)), new PrintStream(err, true, StandardCharsets.UTF_8),
                code -> {
                });
        return new Did(commandLines, windows[0], err.toString(StandardCharsets.UTF_8));
    }

    @Test
    @Story("A start with no display answers as the command line")
    @DisplayName("With no display the window is not tried and the jar answers as its command line")
    void withNoDisplayTheWindowIsNotTriedAndTheJarAnswersAsItsCommandLine() {
        Did did = started(new String[0], new DisplayPresence.Answer(false, "no-display-variable", null));
        claim("no window, and so no control panel class, is tried",
                () -> assertThat(did.windowsTried()).isZero());
        claim("one line on standard error says the window cannot open, no display, and why in words",
                () -> assertThat(did.err().strip()).isEqualTo(
                        "the control panel cannot open a window here, so the jar answers as its command line: "
                                + "no display (neither DISPLAY nor WAYLAND_DISPLAY is set)"));
        claim("and the command line is started with no argument",
                () -> assertThat(did.commandLines()).containsExactly(List.of()));
    }

    @Test
    @Story("A start with no display answers as the command line")
    @DisplayName("A display that cannot be asked is said on standard error with the exception's class name")
    void aDisplayThatCannotBeAskedIsSaidOnStandardErrorWithTheExceptionsClassName() {
        DisplayPresence.Answer asked = DisplayPresence.of("Windows 11", java.util.Map.of(), false, () -> {
            throw new IllegalStateException("no user32");
        }, () -> true);
        Did did = started(new String[0], asked);
        claim("the line names not-answered's words and the exception's class",
                () -> assertThat(did.err()).contains("no display (whether a display is present could not be asked: "
                        + "java.lang.IllegalStateException)"));
        claim("no window is tried, and the command line answers with no argument",
                () -> assertThat(List.of(did.windowsTried(), did.commandLines().size())).containsExactly(0, 1));
    }

    @Test
    @Story("The configuration lies beside the jar")
    @DisplayName("A command line refused for a folder beside the jar it cannot write says the refusal's one line on standard error and exits 1")
    @Issue("836")
    @Link(name = "ADR-397", url = Adr.THE_YAML_CONFIGURATION_LIES_BESIDE_THE_JAR, type = "adr")
    void aFolderBesideTheJarThatCannotBeWrittenIsSaidOnStandardErrorAndExitsOne(@TempDir Path besideTheJar) {
        BesideTheJarUnwritableException refusal = new BesideTheJarUnwritableException("application.yaml", besideTheJar,
                new AccessDeniedException(besideTheJar.resolve("application.yaml").toString()));
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        List<Integer> exits = new ArrayList<>();

        Throwable escaped = org.assertj.core.api.Assertions.catchThrowable(() -> EntryPoint.start(
                new String[] {"run", "a corpus root of this test's"}, () -> {
                    throw new AssertionError("a start of the command line asks for no display");
                }, kind -> java.util.Optional.empty(), () -> {
                    throw new AssertionError("a start of the command line opens no window");
                }, passed -> {
                    throw refusal;
                }, new PrintStream(err, true, StandardCharsets.UTF_8), exits::add));
        String said = err.toString(StandardCharsets.UTF_8);

        claim("the refusal does not escape the start as a stack trace",
                () -> assertThat(escaped).as("what escaped the start").isNull());
        claim("standard error holds exactly the refusal's one line, naming the file and the folder beside the jar",
                () -> assertThat(said.lines().toList()).as("the lines said on standard error")
                        .containsExactly(refusal.getMessage()));
        claim("and the process exits 1, once", () -> assertThat(exits).as("the exit codes asked for")
                .containsExactly(1));
    }

    @Test
    @Story("A command names its working directory")
    @DisplayName("A command line refused for naming no working directory says the refusal's one line on standard error and exits 1")
    @Issue("905")
    @Link(name = "ADR-436", url = Adr.NOTHING_USES_A_WORKING_DIRECTORY_NOTHING_NAMES, type = "adr")
    void aStartNamingNoWorkingDirectorySaysItsLineOnStandardErrorAndExitsOne() {
        NoWorkingDirectoryNamedException refusal = new NoWorkingDirectoryNamedException();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        List<Integer> exits = new ArrayList<>();

        Throwable escaped = org.assertj.core.api.Assertions.catchThrowable(() -> EntryPoint.start(
                new String[] {"run", "a corpus root of this test's"}, () -> {
                    throw new AssertionError("a start of the command line asks for no display");
                }, kind -> java.util.Optional.empty(), () -> {
                    throw new AssertionError("a start of the command line opens no window");
                }, passed -> {
                    throw refusal;
                }, new PrintStream(err, true, StandardCharsets.UTF_8), exits::add));
        String said = err.toString(StandardCharsets.UTF_8);

        claim("the refusal does not escape the start as a stack trace",
                () -> assertThat(escaped).as("what escaped the start").isNull());
        claim("standard error holds exactly the refusal's one line, saying no working directory is named",
                () -> assertThat(said.lines().toList()).as("the lines said on standard error")
                        .containsExactly(refusal.getMessage()));
        claim("and the process exits 1, once", () -> assertThat(exits).as("the exit codes asked for")
                .containsExactly(1));
    }

    @Test
    @Story("A command names its working directory")
    @DisplayName("Help and version are answered before Spring and before the machine lock, and a command still reaches the command line")
    @Issue("905")
    @Link(name = "ADR-436", url = Adr.NOTHING_USES_A_WORKING_DIRECTORY_NOTHING_NAMES, type = "adr")
    void helpAndVersionAreAnsweredBeforeSpringAndTheMachineLock() {
        List<String> locksAsked = new ArrayList<>();
        List<List<String>> commandLines = new ArrayList<>();
        List<Integer> exits = new ArrayList<>();
        for (String[] arguments : List.of(new String[] {"--help"}, new String[] {"--headless", "--version"},
                new String[] {"-h"})) {
            EntryPoint.start(arguments, () -> {
                throw new AssertionError("a start of the command line asks for no display");
            }, kind -> {
                locksAsked.add(String.join(" ", arguments));
                return java.util.Optional.empty();
            }, () -> {
                throw new AssertionError("a start of the command line opens no window");
            }, passed -> commandLines.add(List.of(passed)), new PrintStream(new ByteArrayOutputStream(), true,
                    StandardCharsets.UTF_8), exits::add);
        }
        List<List<String>> beforeTheCommand = List.copyOf(commandLines);
        EntryPoint.start(new String[] {"run", "a corpus root of this test's"}, () -> {
            throw new AssertionError("a start of the command line asks for no display");
        }, kind -> java.util.Optional.empty(), () -> {
            throw new AssertionError("a start of the command line opens no window");
        }, passed -> commandLines.add(List.of(passed)), new PrintStream(new ByteArrayOutputStream(), true,
                StandardCharsets.UTF_8), code -> {
        });

        claim("--help, --headless --version and -h each exit 0, once",
                () -> assertThat(exits).containsExactly(0, 0, 0));
        claim("none of them asked for the machine lock or ran the command line, where Spring is built",
                () -> {
                    assertThat(locksAsked).isEmpty();
                    assertThat(beforeTheCommand).isEmpty();
                });
        claim("and run with a corpus root still reaches the command line, its arguments whole",
                () -> assertThat(commandLines).containsExactly(List.of("run", "a corpus root of this test's")));
    }
}
