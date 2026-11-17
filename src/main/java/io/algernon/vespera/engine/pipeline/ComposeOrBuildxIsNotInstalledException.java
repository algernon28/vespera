package io.algernon.vespera.engine.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * The Docker engine answers, but the {@code docker} client it is reached through has no Compose, no buildx, or neither
 * (ADR-410 §3). The line says what is missing, where, and the command that installs it on that system.
 */
final class ComposeOrBuildxIsNotInstalledException extends SidecarStartRefusedException {

    ComposeOrBuildxIsNotInstalledException(
            HostSystem host, String wslDistribution, boolean composeMissing, boolean buildxMissing) {
        super(line(host, wslDistribution, composeMissing, buildxMissing));
    }

    private static String line(HostSystem host, String wslDistribution, boolean composeMissing, boolean buildxMissing) {
        boolean both = composeMissing && buildxMissing;
        String missing = both ? "docker compose and docker buildx" : composeMissing ? "docker compose" : "docker buildx";
        String named = wslDistribution == null ? "" : wslDistribution.strip();
        String where = host != HostSystem.WINDOWS
                ? "on this machine"
                : named.isEmpty() ? "in WSL's default distribution" : "in the WSL distribution " + named;
        return "Vespera cannot start the sidecars: the Docker engine answers, but " + missing + (both ? " are" : " is")
                + " not installed " + where + ". Install " + (both ? "them" : "it") + " with "
                + install(host, named, composeMissing, buildxMissing) + ", and run the same command again.";
    }

    private static String install(HostSystem host, String named, boolean composeMissing, boolean buildxMissing) {
        List<String> ubuntu = new ArrayList<>();
        List<String> docker = new ArrayList<>();
        List<String> formulae = new ArrayList<>();
        if (composeMissing) {
            ubuntu.add("docker-compose-v2");
            docker.add("docker-compose-plugin");
            formulae.add("docker-compose");
        }
        if (buildxMissing) {
            ubuntu.add("docker-buildx");
            docker.add("docker-buildx-plugin");
            formulae.add("docker-buildx");
        }
        String alternative = ", or " + String.join(" ", docker) + " where Docker Engine came from Docker's own repository";
        return switch (host) {
            case WINDOWS -> "wsl " + (named.isEmpty() ? "" : "-d " + named + " ") + "-u root --exec apt-get install -y "
                    + String.join(" ", ubuntu) + alternative;
            case LINUX -> "sudo apt-get install -y " + String.join(" ", ubuntu) + alternative;
            case MAC -> "brew install " + String.join(" ", formulae)
                    + ", add $HOMEBREW_PREFIX/lib/docker/cli-plugins to cliPluginsExtraDirs in ~/.docker/config.json";
        };
    }
}
