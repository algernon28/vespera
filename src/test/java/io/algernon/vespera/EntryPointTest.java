package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.algernon.vespera.engine.pipeline.DisplayPresence;
import io.algernon.vespera.ui.ControlPanel;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
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

    /**
     * The commands, the two standard options, an option alone, an empty argument, and a --headless that is not first:
     * every start the command line answers today.
     */
    static Stream<Arguments> startsWithArguments() {
        return Stream.of(
                Arguments.of(Named.of("a run of a corpus root", new String[] {"run", "D:/corpus"})),
                Arguments.of(Named.of("the local model answering", new String[] {"label", "--auto"})),
                Arguments.of(Named.of("a publish", new String[] {"publish"})),
                Arguments.of(Named.of("the help", new String[] {"--help"})),
                Arguments.of(Named.of("the version", new String[] {"--version"})),
                Arguments.of(Named.of("an option and no command", new String[] {"--db-dir=D:/working"})),
                Arguments.of(Named.of("one empty argument", new String[] {""})),
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

    /** The flag alone, and the flag before a command and its arguments. */
    static Stream<Arguments> headlessFirst() {
        return Stream.of(
                Arguments.of(Named.of("the flag alone", new String[] {"--headless"}), new String[0]),
                Arguments.of(Named.of("the flag before a run of a corpus root",
                                new String[] {"--headless", "run", "D:/corpus"}),
                        new String[] {"run", "D:/corpus"}),
                Arguments.of(Named.of("the flag before the local model answering",
                                new String[] {"--headless", "label", "--auto"}),
                        new String[] {"label", "--auto"}));
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
        EntryPoint.start(arguments, () -> display,
                () -> {
                    windows[0]++;
                    return new ControlPanel.Opening(ControlPanel.Opening.Result.NO_WINDOW, "the toolkit said no");
                },
                passed -> commandLines.add(List.of(passed)), new PrintStream(err, true, StandardCharsets.UTF_8));
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
    @Story("A start with no display answers as the command line")
    @DisplayName("With a display the window is tried, and the toolkit's failure is ADR-342's line")
    void withADisplayTheWindowIsTriedAndItsFailureIsAdr342sLine() {
        Did did = started(new String[0], new DisplayPresence.Answer(true, "display-variable", null));
        claim("the window is tried once",
                () -> assertThat(did.windowsTried()).isEqualTo(1));
        claim("its failure is the line with the toolkit's message, then the command line with no argument",
                () -> assertThat(did.err().strip()).isEqualTo(
                        "the control panel cannot open a window here, so the jar answers as its command line: "
                                + "the toolkit said no"));
        claim("and the command line answers", () -> assertThat(did.commandLines()).containsExactly(List.of()));
    }

    @Test
    @Story("The jar can always be started without the window")
    @DisplayName("The display is not asked for a start with an argument")
    void aStartWithAnArgumentAsksNoDisplay() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        boolean[] asked = {false};
        EntryPoint.start(new String[] {"run", "D:/corpus"}, () -> {
            asked[0] = true;
            return new DisplayPresence.Answer(true, "display-variable", null);
        }, () -> null, passed -> { }, new PrintStream(err));
        claim("a function of the arguments alone is chosen, and the display is asked after it says the control panel",
                () -> assertThat(asked[0]).isFalse());
    }

    @Test
    @Story("The jar can always be started without the window")
    @DisplayName("--headless first sets the headless property and passes the rest on")
    void headlessFirstSetsTheHeadlessProperty() {
        String before = System.getProperty(DisplayPresence.HEADLESS_PROPERTY);
        try {
            System.clearProperty(DisplayPresence.HEADLESS_PROPERTY);
            Did did = started(new String[] {"--headless", "run", "D:/corpus"},
                    new DisplayPresence.Answer(true, "display-variable", null));
            claim("run <root> is passed on",
                    () -> assertThat(did.commandLines()).containsExactly(List.of("run", "D:/corpus")));
            claim("and vespera.headless is set, which DisplayPresence reads as no display",
                    () -> assertThat(DisplayPresence.ofThisProcess().reason()).isEqualTo("headless"));
            claim("the window is not tried", () -> assertThat(did.windowsTried()).isZero());
        } finally {
            if (before == null) {
                System.clearProperty(DisplayPresence.HEADLESS_PROPERTY);
            } else {
                System.setProperty(DisplayPresence.HEADLESS_PROPERTY, before);
            }
        }
    }
}
