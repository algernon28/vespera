package io.algernon.vespera.pipeline;

import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Runs the command the operator typed, and makes its exit code the process's.
 *
 * <p>The exit code is the point. An invocation is something a person or a scheduler waits on, so
 * "did it work" has to be answerable without reading the log — and a pipeline that never blocks
 * (ADR-047) is one whose only report is that code and what it left in the ledger.
 */
@Component
public class VesperaCli implements CommandLineRunner, ExitCodeGenerator {

    private final VesperaCommand command;

    private int exitCode;

    public VesperaCli(VesperaCommand command) {
        this.command = command;
    }

    @Override
    public void run(String... args) {
        exitCode = command.commandLine().execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
