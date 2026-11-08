package io.algernon.vespera.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javafx.application.Platform;

/**
 * JavaFX's toolkit started in a process of its own, as the control panel's start starts it, with the filter of ADR-360
 * installed first or not, for {@link TheToolkitsStartWritesNoClassPathLineTest}: a toolkit starts once in a JVM, so
 * what its start writes is seen only in a JVM no other test has started one in.
 *
 * <p>Its one argument is {@link #WITH_THE_FILTER} or {@link #WITHOUT_IT}. Before the toolkit starts it asks for
 * {@link #COLLECTIONS} collections, so that a logger nothing holds is lost as the log manager would lose it, and puts a
 * handler on the root logger that writes each record reaching it to standard output, as {@link #RECORD}, its logger,
 * its level and its message, a tab between each. It prints {@link #STARTED} once the toolkit is up, then writes
 * {@link #ANOTHER_WARNING} and {@link #AN_ERROR} through {@code System.getLogger("javafx")}, as JavaFX writes its own,
 * and ends.
 */
public final class AToolkitStartedForTests {

    /** The argument that installs the control panel's filter before the toolkit starts. */
    public static final String WITH_THE_FILTER = "with the filter";

    /** The argument that installs nothing. */
    public static final String WITHOUT_IT = "without it";

    /** What each line a record is written to begins with. */
    public static final String RECORD = "record\t";

    /** The line printed once the toolkit is up. */
    public static final String STARTED = "the toolkit is started";

    /** A warning written on JavaFX's logger after the start. */
    public static final String ANOTHER_WARNING = "another warning of JavaFX's general logger";

    /** An error written on JavaFX's logger after the start, which {@code java.util.logging} gives as {@code SEVERE}. */
    public static final String AN_ERROR = "an error of JavaFX's general logger";

    /** How many collections are asked for before the toolkit starts: five lost an unheld logger in ADR-360's probe. */
    private static final int COLLECTIONS = 5;

    /** How long the toolkit is waited for. */
    private static final long BOUND_SECONDS = 30;

    private AToolkitStartedForTests() {
    }

    public static void main(String[] args) throws Exception {
        if (args[0].equals(WITH_THE_FILTER)) {
            ClassPathLine.install();
        } else if (!args[0].equals(WITHOUT_IT)) {
            throw new IllegalArgumentException("neither '" + WITH_THE_FILTER + "' nor '" + WITHOUT_IT + "': " + args[0]);
        }
        for (int collection = 0; collection < COLLECTIONS; collection++) {
            System.gc();
        }
        Logger.getLogger("").addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                System.out.println(RECORD + record.getLoggerName() + "\t" + record.getLevel().getName() + "\t"
                        + String.valueOf(record.getMessage()).replace('\n', ' ').replace('\r', ' '));
                System.out.flush();
            }

            @Override
            public void flush() {
                System.out.flush();
            }

            @Override
            public void close() {
            }
        });
        CountDownLatch up = new CountDownLatch(1);
        Platform.startup(up::countDown);
        if (!up.await(BOUND_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("the toolkit did not start within " + BOUND_SECONDS + " seconds");
        }
        System.out.println(STARTED);
        System.out.flush();
        System.Logger javafx = System.getLogger("javafx");
        javafx.log(System.Logger.Level.WARNING, ANOTHER_WARNING);
        javafx.log(System.Logger.Level.ERROR, AN_ERROR);
        System.out.flush();
        System.err.flush();
        Platform.exit();
        System.exit(0);
    }
}
