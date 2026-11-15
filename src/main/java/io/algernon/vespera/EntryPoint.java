package io.algernon.vespera;

import io.algernon.vespera.engine.pipeline.BesideTheJar;
import io.algernon.vespera.engine.pipeline.BesideTheJarUnwritableException;
import io.algernon.vespera.engine.pipeline.DisplayPresence;
import io.algernon.vespera.engine.pipeline.MachineLock;
import io.algernon.vespera.engine.pipeline.ServeCommand;
import io.algernon.vespera.ui.ControlPanel;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
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

    /** The exit code of a start the machine lock refused (ADR-395 §5, ADR-141). */
    private static final int REFUSED = 1;

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
        // Built inside the lambda, so that --help, --version and a start with no display never touch the folder.
        start(arguments, DisplayPresence::ofThisProcess,
                kind -> MachineLock.ofThisProcess().refusal(kind, passedOn(arguments)),
                EntryPoint::openTheWindow, VesperaApplication::main, System.err, System::exit);
    }

    /**
     * Prepares the folder beside the jar, once the machine lock is held and before the window opens (ADR-397 §3), and
     * hands the window its path and the lines of §5; a folder that cannot be written refuses the start in §3's line.
     */
    private static ControlPanel.Opening openTheWindow() {
        BesideTheJar besideTheJar = BesideTheJar.ofThisApplication();
        List<String> lines;
        try {
            lines = besideTheJar.prepare();
        } catch (BesideTheJarUnwritableException refused) {
            return new ControlPanel.Opening(ControlPanel.Opening.Result.REFUSED, refused.getMessage());
        }
        return ControlPanel.open(besideTheJar.folder(), lines);
    }

    /**
     * What {@link #main} does, with its seams given: the display (ADR-362 §1), asked only once {@link #chosen}
     * says the control panel and before the toolkit; the machine lock, answering a refusal's line or empty where taken
     * (ADR-395 §4), asked for {@code run}, {@code label} and {@code publish} before the command line and for the control
     * panel once a display is found, before the window; the window; the command line; standard error; and the exit.
     * {@code --headless} first is handed to the command line as the JVM's property {@code vespera.headless}.
     */
    static void start(String[] arguments, Supplier<DisplayPresence.Answer> display,
                      Function<MachineLock.Kind, Optional<String>> machine, Supplier<ControlPanel.Opening> window,
                      Consumer<String[]> commandLine, PrintStream err, IntConsumer exit) {
        if (chosen(arguments) == Started.COMMAND_LINE) {
            Optional<MachineLock.Kind> kind = MachineLock.Kind.ofTheCommand(passedOn(arguments));
            if (kind.isPresent()) {
                Optional<String> refusal = machine.apply(kind.get());
                if (refusal.isPresent()) {
                    err.println(refusal.get());
                    exit.accept(REFUSED);
                    return;
                }
            }
            if (arguments.length > 0 && arguments[0].equals(HEADLESS)) {
                System.setProperty(DisplayPresence.HEADLESS_PROPERTY, "true");
            }
            runCommandLine(commandLine, passedOn(arguments), err, exit);
            return;
        }
        DisplayPresence.Answer answer = display.get();
        if (!answer.present()) {
            err.println(NO_WINDOW + "no display (" + answer.inWords() + ")");
            runCommandLine(commandLine, new String[0], err, exit);
            return;
        }
        Optional<String> refusal = machine.apply(MachineLock.Kind.CONTROL_PANEL);
        if (refusal.isPresent()) {
            err.println(refusal.get());
            exit.accept(REFUSED);
            return;
        }
        ControlPanel.Opening opening = window.get();
        switch (opening.result()) {
            case NO_JAR, REFUSED -> {
                err.println(opening.line());
                exit.accept(1);
            }
            case NO_WINDOW -> {
                err.println(NO_WINDOW + opening.line());
                runCommandLine(commandLine, new String[0], err, exit);
            }
            case CLOSED -> exit.accept(0);
        }
    }

    /**
     * Hands the arguments to the command line. A folder beside the jar it cannot write is thrown as Spring starts,
     * before its logging is configured, so a log line would be dropped: its one line goes on standard error as it
     * stands, and the exit is 1 (ADR-397 §3).
     */
    private static void runCommandLine(Consumer<String[]> commandLine, String[] arguments, PrintStream err,
                                       IntConsumer exit) {
        try {
            commandLine.accept(arguments);
        } catch (BesideTheJarUnwritableException refused) {
            err.println(refused.getMessage());
            exit.accept(REFUSED);
        }
    }
}
