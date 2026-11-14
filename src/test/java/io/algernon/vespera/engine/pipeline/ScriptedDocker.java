package io.algernon.vespera.engine.pipeline;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A {@link ProcessRunner} that runs nothing: it records every command it is given, in order, and answers each from a
 * script (ADR-323). The engine check answers exit 1 until it has been asked {@link #engineAnswersAfter} times and exit
 * 0 after; a start command answers what {@link #answering} set for it, and any other command {@link #otherCommandsExiting}, exit 1 unless set; {@code up} answers {@link
 * #upAnswers}, and on exit 0 runs {@link #whenUpSucceeds}, which is how a test has the sidecars answer once they are
 * up. Not installed, every command it is given throws as {@code ProcessBuilder.start} does for a program that is not
 * there. A command {@link #outputFileFails} names throws as the runner does where the temporary file its output goes to
 * cannot be created or read (ADR-326).
 *
 * <p>A command ending in {@link #ENGINE_CHECK} is the engine check, run as it is or inside a WSL distribution through
 * {@code wsl} (ADR-389 §1); not installed, {@code wsl} is as missing as {@code docker} is. Each command's limit is
 * recorded beside it, {@link #limits}.
 */
final class ScriptedDocker implements ProcessRunner {

    /** The command that asks whether the Docker engine answers. */
    static final List<String> ENGINE_CHECK = List.of("docker", "version", "--format", "{{.Server.Version}}");

    /** An engine check count no test reaches: the engine never answers. */
    static final int NEVER = Integer.MAX_VALUE;

    private final List<List<String>> commands = new ArrayList<>();
    private final List<Duration> limits = new ArrayList<>();
    private final Map<List<String>, ProcessOutcome> scripted = new HashMap<>();
    private final java.util.Set<List<String>> unlaunchable = new java.util.HashSet<>();
    private final Map<List<String>, CommandOutputFileException> outputFileFailures = new HashMap<>();
    private boolean installed = true;
    private int engineAnswersAfter;
    private int otherCommandsExit = 1;
    private int engineChecks;
    private ProcessOutcome upAnswers = new ProcessOutcome(0, " Container vespera-docling-serve-1 Started\n");
    private Runnable whenUpSucceeds = () -> {};

    /** No {@code docker} command exists on this machine. */
    static ScriptedDocker notInstalled() {
        ScriptedDocker docker = new ScriptedDocker();
        docker.installed = false;
        return docker;
    }

    /** The engine answers from the first check. */
    static ScriptedDocker engineRunning() {
        return new ScriptedDocker();
    }

    /** The engine fails its first {@code checks} checks and answers every one after. */
    static ScriptedDocker engineAnsweringAfter(int checks) {
        ScriptedDocker docker = new ScriptedDocker();
        docker.engineAnswersAfter = checks;
        return docker;
    }

    ScriptedDocker answering(List<String> command, int exitCode, String output) {
        scripted.put(command, new ProcessOutcome(exitCode, output));
        return this;
    }

    /** {@code command}'s program cannot be started, as {@code systemctl} cannot on a machine without systemd. */
    ScriptedDocker cannotLaunch(List<String> command, String why) {
        unlaunchable.add(command);
        scripted.put(command, new ProcessOutcome(0, why));
        return this;
    }

    /**
     * The temporary file {@code command}'s output goes to, in {@code folder}, cannot be created or read, for {@code
     * why}, as on a drive that has filled since the compose files were written out.
     */
    ScriptedDocker outputFileFails(List<String> command, java.nio.file.Path folder, String why) {
        outputFileFailures.put(command, new CommandOutputFileException(command, folder, new IOException(why)));
        return this;
    }

    /** What a command no script names exits with, and prints nothing: 1 unless set. */
    ScriptedDocker otherCommandsExiting(int exitCode) {
        otherCommandsExit = exitCode;
        return this;
    }

    ScriptedDocker upAnswering(int exitCode, String output) {
        upAnswers = new ProcessOutcome(exitCode, output);
        return this;
    }

    ScriptedDocker whenUpSucceeds(Runnable then) {
        whenUpSucceeds = then;
        return this;
    }

    /** Every command given, in the order given. */
    List<List<String>> commands() {
        return List.copyOf(commands);
    }

    /** The limit each command given to {@link #run} was given, in the order given; {@code up} is given none. */
    List<Duration> limits() {
        return List.copyOf(limits);
    }

    /** Whether {@code command} is the engine check, run as it is or inside {@code wsl}. */
    static boolean isTheEngineCheck(List<String> command) {
        return command.size() >= ENGINE_CHECK.size()
                && command.subList(command.size() - ENGINE_CHECK.size(), command.size()).equals(ENGINE_CHECK);
    }

    @Override
    public ProcessOutcome run(List<String> command, Duration timeout) throws IOException, CommandOutputFileException {
        commands.add(List.copyOf(command));
        limits.add(timeout);
        if (outputFileFailures.containsKey(command)) {
            throw outputFileFailures.get(command);
        }
        refuseWhereNotInstalled(command);
        if (unlaunchable.contains(command)) {
            throw new IOException(scripted.get(command).output());
        }
        if (isTheEngineCheck(command)) {
            engineChecks++;
            return engineChecks > engineAnswersAfter
                    ? new ProcessOutcome(0, "29.9.1\n")
                    : new ProcessOutcome(1, "Cannot connect to the Docker daemon at unix:///var/run/docker.sock."
                            + " Is the docker daemon running?\n");
        }
        return scripted.getOrDefault(command, new ProcessOutcome(otherCommandsExit, ""));
    }

    @Override
    public ProcessOutcome runToTheEnd(List<String> command, Consumer<String> eachLine) throws IOException {
        commands.add(List.copyOf(command));
        refuseWhereNotInstalled(command);
        upAnswers.output().lines().forEach(eachLine);
        if (upAnswers.exitCode() == 0) {
            whenUpSucceeds.run();
        }
        return upAnswers;
    }

    private void refuseWhereNotInstalled(List<String> command) throws IOException {
        if (!installed && (command.getFirst().equals("docker") || command.getFirst().equals("wsl"))) {
            throw new IOException("Cannot run program \"" + command.getFirst() + "\": error=2, No such file or directory");
        }
    }
}
