package io.algernon.vespera.engine.pipeline;

/** The engine check's program, {@code docker} or {@code wsl} on Windows, is not on this machine (ADR-389 §6). */
final class DockerIsNotInstalledException extends SidecarStartRefusedException {

    DockerIsNotInstalledException(String program) {
        super("Vespera cannot start Docker: no " + program + " command was found. Install Docker Engine -- in a WSL 2"
                + " distribution on Windows, with Colima on macOS, as the docker service on Linux -- and run the same"
                + " command again.");
    }
}
