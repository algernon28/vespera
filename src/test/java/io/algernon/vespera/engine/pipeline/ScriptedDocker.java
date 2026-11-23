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
 * #upAnswers}, and on exit 0 runs {@link #whenUpSucceeds}, which is how a test has the containers answer once they are
 * up. Not installed, every command it is given throws as {@code ProcessBuilder.start} does for a program that is not
 * there. A command {@link #outputFileFails} names throws as the runner does where the temporary file its output goes to
 * cannot be created or read (ADR-326).
 *
 * <p>A command ending in {@link #ENGINE_CHECK} is the engine check, run as it is or inside a WSL distribution through
 * {@code wsl} (ADR-390 §1); not installed, {@code wsl} is as missing as {@code docker} is. Each command's limit is
 * recorded beside it, {@link #limits}.
 *
 * <p>A command ending in {@link #COMPOSE_CHECK} or {@link #BUILDX_CHECK}, as it is or inside {@code wsl}, answers exit 0
 * with the version Ubuntu 24.04's packages print, unless {@link #withoutCompose} or {@link #withoutBuildx} took that
 * one away, when it answers as the {@code docker} client answers a command it has no plugin for (ADR-410, Measured);
 * a script {@link #answering} sets for the whole command comes first.
 *
 * <p>A command ending in {@link #RUNTIME_CHECK}, as it is or inside {@code wsl}, answers exit 0 with the runtimes an
 * engine the NVIDIA Container Toolkit configured lists, {@code nvidia} among them, unless {@link
 * #withoutTheNvidiaRuntime} took it away (ADR-444 §1). A command given to {@link #runFeeding} is recorded as any other,
 * with what it was fed, {@link #fed}, copied as it was given, runs what {@link #whenFed} set, and is answered as
 * {@link #run} answers it.
 */
final class ScriptedDocker implements ProcessRunner {

    /** The command that asks the engine for the runtimes it was configured with (ADR-444 §1). */
    static final List<String> RUNTIME_CHECK = List.of("docker", "info", "--format", "{{json .Runtimes}}");

    /** What a command given to {@link #runFeeding} was fed on its standard input; null where it was fed nothing. */
    record Fed(List<String> command, String input) {
    }

    /** The command that asks whether the Docker engine answers. */
    static final List<String> ENGINE_CHECK = List.of("docker", "version", "--format", "{{.Server.Version}}");

    /** The command that asks whether the {@code docker} client has Compose (ADR-410 §1). */
    static final List<String> COMPOSE_CHECK = List.of("docker", "compose", "version");

    /** The command that asks whether the {@code docker} client has buildx (ADR-410 §1). */
    static final List<String> BUILDX_CHECK = List.of("docker", "buildx", "version");

    /** An engine check count no test reaches: the engine never answers. */
    static final int NEVER = Integer.MAX_VALUE;

    private final List<List<String>> commands = new ArrayList<>();
    private final List<Duration> limits = new ArrayList<>();
    private final Map<List<String>, ProcessOutcome> scripted = new HashMap<>();
    private final java.util.Set<List<String>> unlaunchable = new java.util.HashSet<>();
    private final Map<List<String>, CommandOutputFileException> outputFileFailures = new HashMap<>();
    private boolean installed = true;
    private boolean composeInstalled = true;
    private boolean buildxInstalled = true;
    private boolean nvidiaRuntimeListed = true;
    private final List<Fed> fed = new ArrayList<>();
    private int engineAnswersAfter;
    private int otherCommandsExit = 1;
    private int engineChecks;
    private ProcessOutcome upAnswers = new ProcessOutcome(0, " Container vespera-docling-serve-1 Started\n");
    private Runnable whenUpSucceeds = () -> {};
    private Runnable whenFed = () -> {};

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

    /** The {@code docker} client has no Compose, as Ubuntu's {@code docker.io} has none without {@code docker-compose-v2}. */
    ScriptedDocker withoutCompose() {
        composeInstalled = false;
        return this;
    }

    /** The {@code docker} client has no buildx, as Ubuntu's {@code docker.io} has none without {@code docker-buildx}. */
    ScriptedDocker withoutBuildx() {
        buildxInstalled = false;
        return this;
    }

    /** The engine lists no {@code nvidia} runtime, as one the NVIDIA Container Toolkit never configured (ADR-444 §1). */
    ScriptedDocker withoutTheNvidiaRuntime() {
        nvidiaRuntimeListed = false;
        return this;
    }

    /** Runs {@code then} as each command given to {@link #runFeeding} runs, before it is answered (ADR-444 §6). */
    ScriptedDocker whenFed(Runnable then) {
        whenFed = then;
        return this;
    }

    /** Every command given to {@link #runFeeding}, with what it was fed, in the order given. */
    List<Fed> fed() {
        return List.copyOf(fed);
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
        return endsWith(command, ENGINE_CHECK);
    }

    private static boolean endsWith(List<String> command, List<String> tail) {
        return command.size() >= tail.size()
                && command.subList(command.size() - tail.size(), command.size()).equals(tail);
    }

    /** How the {@code docker} client answers {@code docker <subcommand> version} with no plugin for it (ADR-410). */
    private static ProcessOutcome unknownToTheClient(String subcommand) {
        return new ProcessOutcome(1, "docker: unknown command: docker " + subcommand + "\n\n"
                + "Run 'docker --help' for more information\n");
    }

    @Override
    public ProcessOutcome run(List<String> command, Duration timeout) throws IOException, CommandOutputFileException {
        commands.add(List.copyOf(command));
        limits.add(timeout);
        return answer(command);
    }

    @Override
    public ProcessOutcome runFeeding(List<String> command, Duration timeout, char[] input)
            throws IOException, CommandOutputFileException {
        commands.add(List.copyOf(command));
        limits.add(timeout);
        fed.add(new Fed(List.copyOf(command), input == null ? null : new String(input)));
        whenFed.run();
        return answer(command);
    }

    private ProcessOutcome answer(List<String> command) throws IOException, CommandOutputFileException {
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
        if (!scripted.containsKey(command) && endsWith(command, COMPOSE_CHECK)) {
            return composeInstalled
                    ? new ProcessOutcome(0, "Docker Compose version 2.40.3+ds1-0ubuntu1~24.04.1\n")
                    : unknownToTheClient("compose");
        }
        if (!scripted.containsKey(command) && endsWith(command, BUILDX_CHECK)) {
            return buildxInstalled
                    ? new ProcessOutcome(0, "github.com/docker/buildx 0.30.1 0.30.1-0ubuntu1~24.04.1\n")
                    : unknownToTheClient("buildx");
        }
        if (!scripted.containsKey(command) && endsWith(command, RUNTIME_CHECK)) {
            return new ProcessOutcome(0, nvidiaRuntimeListed
                    ? "{\"io.containerd.runc.v2\":{\"path\":\"runc\",\"status\":{}},\"nvidia\":{\"path\":"
                            + "\"nvidia-container-runtime\",\"status\":{}},\"runc\":{\"path\":\"runc\",\"status\":{}}}\n"
                    : "{\"io.containerd.runc.v2\":{\"path\":\"runc\",\"status\":{}},\"runc\":{\"path\":\"runc\","
                            + "\"status\":{}}}\n");
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
