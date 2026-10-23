package io.algernon.vespera.pipeline;

import java.util.List;

/**
 * A program that starts {@link SleepsUntilKilled} with its own output, writes the child's process id to the file its
 * one argument names, and exits at once, leaving the child holding the output open, as {@code cmd /c start} leaves Docker Desktop, for {@link
 * SystemProcessRunnerTest}.
 */
final class LeavesAChildHoldingItsOutput {

    private LeavesAChildHoldingItsOutput() {
    }

    public static void main(String[] args) throws Exception {
        Process child = new ProcessBuilder(List.of(
                        ProcessHandle.current().info().command().orElseThrow(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        SleepsUntilKilled.class.getName()))
                .inheritIO()
                .start();
        java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]), Long.toString(child.pid()));
        System.exit(0);
    }
}
