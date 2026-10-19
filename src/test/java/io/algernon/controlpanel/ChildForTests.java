package io.algernon.controlpanel;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A small program the control panel's tests start as a child in place of the jar (ADR-285 §5), with the JVM that runs
 * the tests, so that they run alike on Linux and on Windows and need no packaged jar.
 *
 * <p>What it does is named by its first argument:
 *
 * <ul>
 *   <li>{@code lines <n>}: prints {@code line 1} to {@code line <n>} on standard output.
 *   <li>{@code stderr <bytes>}: writes at least that many bytes to standard error, in lines, and nothing to standard
 *       output.
 *   <li>{@code interleave <n>}: prints {@code out 1}, {@code err 2}, {@code out 3} ... to {@code <n>}, odd numbers on
 *       standard output and even ones on standard error, flushing each.
 *   <li>{@code utf8}: prints {@link #WRITTEN_IN_UTF8}.
 *   <li>{@code exit <code> <line>}: prints the line, then a line of spaces and an empty line, and exits with the code.
 *   <li>{@code sleep}: sleeps until it is killed.
 *   <li>{@code grandchild}: starts a second copy of itself that sleeps, prints {@link #GRANDCHILD} and its pid, and
 *       sleeps until it is killed.
 * </ul>
 *
 * <p>The tests start it with {@code -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8}, as the control panel starts the
 * jar.
 */
public final class ChildForTests {

    /** A line holding a character outside every single-byte code page, one outside Latin-2 and one inside windows-1252. */
    public static final String WRITTEN_IN_UTF8 = "ł 漢 è";

    /** What the {@code grandchild} mode prints before its grandchild's pid. */
    public static final String GRANDCHILD = "grandchild ";

    /** Longer than any test waits; the test kills this process long before. */
    private static final long SLEEP_MILLIS = 600_000L;

    /** The width of each line the {@code stderr} mode writes, newline included. */
    private static final int LINE_WIDTH = 100;

    private ChildForTests() {
    }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "lines" -> {
                int count = Integer.parseInt(args[1]);
                for (int i = 1; i <= count; i++) {
                    System.out.println("line " + i);
                }
                System.out.flush();
            }
            case "stderr" -> {
                long bytes = Long.parseLong(args[1]);
                String line = "x".repeat(LINE_WIDTH - 1);
                for (long written = 0; written < bytes; written += LINE_WIDTH) {
                    System.err.println(line);
                }
                System.err.flush();
            }
            case "interleave" -> {
                int count = Integer.parseInt(args[1]);
                for (int i = 1; i <= count; i++) {
                    PrintStream stream = i % 2 == 1 ? System.out : System.err;
                    stream.println((i % 2 == 1 ? "out " : "err ") + i);
                    stream.flush();
                }
            }
            case "utf8" -> {
                System.out.println(WRITTEN_IN_UTF8);
                System.out.flush();
            }
            case "exit" -> {
                System.out.println(args[2]);
                System.out.println("   ");
                System.out.println();
                System.out.flush();
                System.exit(Integer.parseInt(args[1]));
            }
            case "sleep" -> Thread.sleep(SLEEP_MILLIS);
            case "grandchild" -> {
                Process grandchild = builder("sleep").start();
                System.out.println(GRANDCHILD + grandchild.pid());
                System.out.flush();
                Thread.sleep(SLEEP_MILLIS);
            }
            default -> throw new IllegalArgumentException("no such mode: " + args[0]);
        }
    }

    /** A process builder starting this class in a JVM of its own, on the running class path, in the given mode. */
    public static ProcessBuilder builder(String... args) {
        List<String> command = new ArrayList<>(List.of(
                ProcessHandle.current().info().command().orElseThrow(),
                "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8",
                "-cp",
                System.getProperty("java.class.path"),
                ChildForTests.class.getName()));
        command.addAll(List.of(args));
        return new ProcessBuilder(command);
    }
}
