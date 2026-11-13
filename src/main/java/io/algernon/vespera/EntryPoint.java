package io.algernon.vespera;

import io.algernon.vespera.engine.pipeline.DisplayPresence;
import io.algernon.vespera.engine.pipeline.ServeCommand;
import io.algernon.vespera.ui.ControlPanel;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The jar's {@code Start-Class} (ADR-342 §2), in the root package beside {@link VesperaApplication}, outside the
 * capability modules and outside the control panel's package, the one class that names both. It extends no JavaFX {@code Application}, so the Java launcher's check for JavaFX's modules is
 * never reached and Spring Boot's launcher stays the jar's {@code Main-Class}.
 *
 * <p>Started with no argument the jar opens the control panel's window; started with any argument it is the command
 * line as before; and {@code --headless} as the first argument never opens the window, the command line being given
 * every argument after it. Nothing of the control panel is loaded before {@link VesperaApplication#main} but where the
 * window could not open.
 */
public final class EntryPoint {

    /** What a start of the jar comes to. */
    public enum Started { CONTROL_PANEL, COMMAND_LINE }

    /** The flag that, as the first argument, keeps the jar from opening the window. */
    private static final String HEADLESS = "--headless";

    /** What both lines about a window that cannot open begin with (ADR-342 §2). */
    private static final String NO_WINDOW =
            "the control panel cannot open a window here, so the jar answers as its command line: ";

    private EntryPoint() {
    }

    /** {@link Started#CONTROL_PANEL} for no argument at all, {@link Started#COMMAND_LINE} for any other start. */
    public static Started chosen(String[] arguments) {
        return arguments.length == 0 ? Started.CONTROL_PANEL : Started.COMMAND_LINE;
    }

    /** The arguments the command line is given: every one, the first dropped where it is {@code --headless}. */
    public static String[] passedOn(String[] arguments) {
        return arguments.length > 0 && arguments[0].equals(HEADLESS)
                ? Arrays.copyOfRange(arguments, 1, arguments.length) : arguments;
    }

    public static void main(String[] arguments) {
        // `vespera serve` is handed on before SpringApplication is built (ADR-333 section 8, ADR-364 section 4): no
        // context, none of the listeners, no datasource, no lock and no log file, and so outside logback.
        Optional<List<String>> served = ServeCommand.handedOn(passedOn(arguments));
        if (served.isPresent()) {
            System.exit(new ServeCommand(System.out, System.err).call(served.get().toArray(String[]::new)));
            return;
        }
        start(arguments, DisplayPresence::ofThisProcess, ControlPanel::open, VesperaApplication::main, System.err);
    }

    /**
     * What {@link #main} does, with its four seams given: the display (ADR-362 §1), asked only once {@link #chosen}
     * says the control panel and before the toolkit; the window; the command line; and standard error. {@code
     * --headless} first is handed to the command line as the JVM's property {@code vespera.headless}.
     */
    static void start(String[] arguments, Supplier<DisplayPresence.Answer> display, Supplier<ControlPanel.Opening> window,
                      Consumer<String[]> commandLine, PrintStream err) {
        if (chosen(arguments) == Started.COMMAND_LINE) {
            if (arguments.length > 0 && arguments[0].equals(HEADLESS)) {
                System.setProperty(DisplayPresence.HEADLESS_PROPERTY, "true");
            }
            commandLine.accept(passedOn(arguments));
            return;
        }
        DisplayPresence.Answer answer = display.get();
        if (!answer.present()) {
            err.println(NO_WINDOW + "no display (" + answer.inWords() + ")");
            commandLine.accept(new String[0]);
            return;
        }
        ControlPanel.Opening opening = window.get();
        switch (opening.result()) {
            case NO_JAR -> {
                err.println(opening.line());
                System.exit(1);
            }
            case NO_WINDOW -> {
                err.println(NO_WINDOW + opening.line());
                commandLine.accept(new String[0]);
            }
            case CLOSED -> System.exit(0);
        }
    }
}
