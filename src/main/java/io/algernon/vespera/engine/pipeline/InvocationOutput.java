package io.algernon.vespera.engine.pipeline;

import ch.qos.logback.core.Context;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/**
 * The one sink of an invocation's output file, {@code logs/output-<invocation>.log} (ADR-364 sections 2, 3 and 6): one to
 * a logger context, held in it as an object under {@link #CONTEXT_OBJECT}, which every {@link InvocationOutputAppender}
 * of the context writes to, so that a line is written whole before the next whatever thread logs it.
 *
 * <p>Before the invocation {@link #begin begins} it holds what it is handed, at most {@link #HELD_AT_MOST} bytes, the
 * oldest lines dropped whole past it; at the beginning it deletes the output files more than fourteen days older than
 * the invocation, opens its own, writes what it held, and from then on writes each line as it comes, flushed. A file
 * that cannot be opened, or a write that fails, fails nothing: one warning, and every line after it dropped. No warning
 * is logged while the sink's lock is held: it is kept, and logged once the lock is let go.
 */
public final class InvocationOutput {

    /** The name the sink is held under in its logger context. */
    public static final String CONTEXT_OBJECT = "io.algernon.vespera.invocationOutput";

    /** What the sink holds before the invocation begins, at most: 4 MiB. */
    static final int HELD_AT_MOST = 4 * 1024 * 1024;

    /** How much older than the invocation an output file is before it is deleted (ADR-364 section 6). */
    static final Duration KEPT_FOR = Duration.ofDays(14);

    /** The id's form, ADR-362 section 3's: the UTC start to the second, with {@code -2} and on for one in the same second. */
    private static final Pattern ID = Pattern.compile("(\\d{8}T\\d{6}Z)(?:-[1-9]\\d*)?");

    private static final Pattern FILE_NAME = Pattern.compile("output-(" + ID.pattern() + ")\\.log");

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    /** How the sink deletes an old output file: {@code Files::delete} as shipped, one that refuses in a test. */
    @FunctionalInterface
    public interface Deletion {
        void delete(Path file) throws IOException;
    }

    /**
     * What begins the output with the invocation's id (ADR-364 section 3): {@link #beginInThisContext} as shipped, a
     * recording one in a test.
     */
    @FunctionalInterface
    public interface Beginning {
        void begin(String invocation);
    }

    private final Object lock = new Object();
    private final Path folder;
    private final Deletion deletion;
    private final ArrayDeque<byte[]> held = new ArrayDeque<>();
    private long heldBytes;
    private long dropped;
    private boolean begun;
    private boolean failed;
    private OutputStream file;

    InvocationOutput(Path folder, Deletion deletion) {
        this.folder = folder;
        this.deletion = deletion;
    }

    /** The sink of {@code context}, made and held there where it has none yet, writing in {@code folder}. */
    static InvocationOutput in(Context context, Path folder) {
        synchronized (context) {
            Object found = context.getObject(CONTEXT_OBJECT);
            if (found instanceof InvocationOutput sink) {
                return sink;
            }
            InvocationOutput sink = new InvocationOutput(folder, Files::delete);
            context.putObject(CONTEXT_OBJECT, sink);
            return sink;
        }
    }

    /** The sink {@code context} holds; an {@link IllegalStateException} where it holds none. */
    static InvocationOutput in(Context context) {
        Object found = context.getObject(CONTEXT_OBJECT);
        if (found instanceof InvocationOutput sink) {
            return sink;
        }
        throw new IllegalStateException("the logger context holds no output file's sink");
    }

    /**
     * Begins the sink of the logger context SLF4J is bound to, and does nothing where that context holds none: a test
     * JVM, whose configuration declares no sink, and {@code vespera serve}, which Spring never configures.
     */
    public static void beginInThisContext(String invocation) {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (factory instanceof Context context && context.getObject(CONTEXT_OBJECT) instanceof InvocationOutput sink) {
            sink.begin(invocation);
        }
    }

    /** Whether {@code name} is an output file's: {@code output-<id>.log}, its id of ADR-362 section 3's form. */
    public static boolean isOutputFileName(String name) {
        return name != null && FILE_NAME.matcher(name).matches();
    }

    /** Whether {@code id} is of ADR-362 section 3's form, and so names an output file and nothing else. */
    public static boolean isInvocationId(String id) {
        return id != null && isOutputFileName("output-" + id + ".log");
    }

    /** The instant an output file's name or an id reads as, empty where it reads as none. */
    private static Optional<Instant> instantOfFile(String name) {
        Matcher matcher = FILE_NAME.matcher(name);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return instantOfStamp(matcher.group(2));
    }

    private static Optional<Instant> instantOfStamp(String stamp) {
        try {
            return Optional.of(LocalDateTime.parse(stamp, STAMP).toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException notADate) {
            return Optional.empty();
        }
    }

    /** One line, encoded whole, from an appender of the context. */
    void write(byte[] line) {
        List<String> warnings = new ArrayList<>(1);
        synchronized (lock) {
            if (failed) {
                return;
            }
            if (!begun) {
                hold(line);
                return;
            }
            try {
                file.write(line);
                file.flush();
            } catch (IOException | RuntimeException unwritten) {
                failed = true;
                warnings.add("the output file could not be written: " + unwritten.getClass().getName());
            }
        }
        warn(warnings);
    }

    /**
     * The invocation begins under {@code invocation}: old output files are deleted, its own is opened, and what was
     * held is written into it first. A second beginning of the same sink is refused.
     */
    public void begin(String invocation) {
        List<String> warnings = new ArrayList<>();
        synchronized (lock) {
            if (begun) {
                throw new IllegalStateException("the output file has already begun");
            }
            begun = true;
            Matcher id = ID.matcher(invocation == null ? "" : invocation);
            Optional<Instant> beganAt = id.matches() ? instantOfStamp(id.group(1)) : Optional.empty();
            beganAt.ifPresent(at -> deleteOlderThan(at, warnings));
            try {
                Files.createDirectories(folder);
                file = Files.newOutputStream(
                        folder.resolve("output-" + invocation + ".log"),
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                if (dropped > 0) {
                    file.write((dropped + " lines logged before this invocation began are not in this file;"
                            + " logs/vespera.log holds them" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
                }
                for (byte[] line : held) {
                    file.write(line);
                }
                file.flush();
            } catch (IOException | RuntimeException unopened) {
                failed = true;
                warnings.add("the output file could not be opened: " + unopened.getClass().getName());
            }
            held.clear();
            heldBytes = 0;
        }
        warn(warnings);
    }

    private void hold(byte[] line) {
        held.addLast(line);
        heldBytes += line.length;
        while (heldBytes > HELD_AT_MOST && !held.isEmpty()) {
            heldBytes -= held.removeFirst().length;
            dropped++;
        }
    }

    /** Deletes each output file in the folder more than {@link #KEPT_FOR} older than {@code beganAt}. */
    private void deleteOlderThan(Instant beganAt, List<String> warnings) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        List<Path> old = new ArrayList<>();
        try (Stream<Path> each = Files.list(folder)) {
            each.filter(Files::isRegularFile)
                    .filter(path -> instantOfFile(path.getFileName().toString())
                            .map(at -> Duration.between(at, beganAt).compareTo(KEPT_FOR) > 0)
                            .orElse(false))
                    .forEach(old::add);
        } catch (IOException | RuntimeException unlisted) {
            warnings.add("the old output files could not be listed: " + unlisted.getClass().getName());
            return;
        }
        for (Path file : old) {
            try {
                deletion.delete(file);
            } catch (IOException | RuntimeException undeleted) {
                warnings.add("the old output file " + file + " could not be deleted: " + undeleted.getClass().getName());
            }
        }
    }

    /** Logs what was kept while the lock was held, once it is let go, so that a logging thread and the sink never wait on each other. */
    private static void warn(List<String> warnings) {
        for (String warning : warnings) {
            LoggerFactory.getLogger(InvocationOutput.class).warn(warning);
        }
    }
}
