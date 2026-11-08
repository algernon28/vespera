package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javafx.application.Application;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The filter the control panel's start puts on JavaFX's logger before the toolkit starts (ADR-360 §1, §2): it drops the
 * one record whose message begins {@code Unsupported JavaFX configuration}, which JavaFX writes on the logger {@code
 * javafx} as its toolkit starts from the class path, at whatever level, and passes every other record; {@code
 * install} sets it on that logger, which its class holds so the log manager does not lose it; and {@code
 * ControlPanel.open} installs it before it launches the window.
 *
 * <p>Held on records the test builds and in the test JVM, with no toolkit started: JavaFX starts once in a JVM, and
 * which test class started it here is not known. What the toolkit's own start writes is held in a JVM of its own, by
 * {@link TheToolkitsStartWritesNoClassPathLineTest}.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("723")
@Link(name = "ADR-360", url = Adr.THE_CONTROL_PANEL_DROPS_JAVAFXS_CLASS_PATH_LINE, type = "adr")
class ClassPathLineTest {

    /** The words JavaFX's line begins with, which the filter drops a record for. */
    private static final String OPENING = "Unsupported JavaFX configuration";

    /** The line as JavaFX 26.0.2 writes it at a start of the window from the jar, its module's name included. */
    private static final String THE_LINE = OPENING + ": classes were loaded from 'unnamed module @4962191c'";

    /** The logger JavaFX writes the line on: its general logger, and not the class the line names as its source. */
    private static final String JAVAFX_LOGGER = "javafx";

    /** The seven levels {@code java.util.logging} names, from the most severe to the least. */
    private static final List<Level> EVERY_LEVEL = List.of(
            Level.SEVERE, Level.WARNING, Level.INFO, Level.CONFIG, Level.FINE, Level.FINER, Level.FINEST);

    /**
     * Six messages that are not the line: another warning, the words not at the start, part of the words, the words in
     * another case, the words after a space, and no message at all.
     */
    private static final List<String> OTHER_MESSAGES = Arrays.asList(
            "Unable to load the image, its format is not supported",
            "JavaFX says: " + THE_LINE,
            "Unsupported JavaFX",
            "unsupported javafx configuration: classes were loaded from 'unnamed module @4962191c'",
            " " + THE_LINE,
            null);

    @Test
    @Story("JavaFX's start writes no line about its class path")
    @DisplayName("A record whose message begins with JavaFX's words about its class path is dropped, at every level")
    void aRecordWhoseMessageBeginsWithTheLinesOpeningWordsIsDroppedAtEveryLevel() {
        ClassPathLine filter = new ClassPathLine();

        List<Level> lineLetThrough = EVERY_LEVEL.stream()
                .filter(level -> filter.isLoggable(new LogRecord(level, THE_LINE)))
                .toList();
        List<Level> wordsLetThrough = EVERY_LEVEL.stream()
                .filter(level -> filter.isLoggable(new LogRecord(level, OPENING)))
                .toList();

        claim("the line JavaFX writes as it starts from the class path is dropped at each of the seven levels"
                        + " java.util.logging names, not at its own level alone",
                () -> assertThat(lineLetThrough).isEmpty());
        claim("and so is a message of the words alone, at each of the seven levels, since the filter matches how the"
                + " message begins and not the whole of it", () -> assertThat(wordsLetThrough).isEmpty());
    }

    @Test
    @Story("JavaFX's start writes no line about its class path")
    @DisplayName("Every other record passes at every level: another message, the words elsewhere, part of them, in another case, or no message")
    void everyOtherRecordPassesAtEveryLevel() {
        ClassPathLine filter = new ClassPathLine();

        List<String> dropped = new ArrayList<>();
        for (Level level : EVERY_LEVEL) {
            for (String message : OTHER_MESSAGES) {
                if (!filter.isLoggable(new LogRecord(level, message))) {
                    dropped.add(level + " " + message);
                }
            }
        }

        claim("none of the six other messages, a warning that is not the line, the words not at its start, part of"
                        + " them, the words in another case, the words after a space, and no message, is dropped at any"
                        + " of the seven levels",
                () -> assertThat(dropped).isEmpty());
    }

    @Test
    @Story("JavaFX's start writes no line about its class path")
    @DisplayName("Installing the filter puts it on the logger named javafx, and its class holds that logger for as long as it is loaded")
    void installingPutsTheFilterOnTheLoggerNamedJavafxAndItsClassHoldsThatLogger() throws Exception {
        Logger javafx = Logger.getLogger(JAVAFX_LOGGER);
        Filter before = javafx.getFilter();
        try {
            ClassPathLine.install();
            Filter installed = Logger.getLogger(JAVAFX_LOGGER).getFilter();
            List<Object> held = loggersHeldBy(ClassPathLine.class);

            claim("the logger named javafx, which JavaFX writes the line on, carries the control panel's filter",
                    () -> assertThat(installed).isInstanceOf(ClassPathLine.class));
            claim("and a static field of the filter's class holds that very logger, so the log manager, which holds a"
                            + " logger weakly, keeps the one carrying the filter and gives JavaFX no new one without it",
                    () -> assertThat(held).anySatisfy(logger -> assertThat(logger).isSameAs(javafx)));
        } finally {
            javafx.setFilter(before);
        }
    }

    @Test
    @Story("JavaFX's start writes no line about its class path")
    @DisplayName("The control panel's start installs the filter before it launches the window, which starts JavaFX's toolkit")
    void theControlPanelsStartInstallsTheFilterBeforeItLaunchesTheWindow() {
        Set<JavaMethodCall> calls = new ClassFileImporter().importClass(ControlPanel.class)
                .getMethod("open").getMethodCallsFromSelf();

        List<Integer> installs = linesOfCalls(calls, ClassPathLine.class.getName(), "install");
        List<Integer> launches = linesOfCalls(calls, Application.class.getName(), "launch");

        claim("the control panel's opening installs the filter once", () -> assertThat(installs).hasSize(1));
        claim("and launches the window once", () -> assertThat(launches).hasSize(1));
        claim("and installs the filter on a line before the one that launches the window, so the filter stands when"
                        + " JavaFX writes its line as the toolkit starts",
                () -> assertThat(installs.getFirst()).isLessThan(launches.getFirst()));
    }

    /** The values of the static fields of {@code type} that are of {@link Logger}'s type, read as the class holds them. */
    private static List<Object> loggersHeldBy(Class<?> type) throws IllegalAccessException {
        List<Object> held = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Logger.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                held.add(field.get(null));
            }
        }
        return held;
    }

    /** The source lines of the calls among {@code calls} to the method {@code name} of the class {@code owner}. */
    private static List<Integer> linesOfCalls(Set<JavaMethodCall> calls, String owner, String name) {
        return calls.stream()
                .filter(call -> call.getTargetOwner().getName().equals(owner) && call.getName().equals(name))
                .map(JavaMethodCall::getLineNumber)
                .sorted()
                .toList();
    }
}
