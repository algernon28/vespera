package io.algernon.vespera.pipeline;

/** There is no {@code docker} command on this machine (ADR-323 §8). */
final class DockerIsNotInstalledException extends SidecarStartRefusedException {

    DockerIsNotInstalledException() {
        super("the sidecars are not answering and Vespera cannot start them: no docker command was found. Install"
                + " Docker -- Docker Desktop on Windows and macOS, Docker Engine on Linux -- and run the same command"
                + " again, or start the sidecars yourself and set VESPERA_SIDECARS_START=false.");
    }
}
