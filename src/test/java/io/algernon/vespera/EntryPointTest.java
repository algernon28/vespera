package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.Set;
import java.util.stream.Collectors;

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
}
