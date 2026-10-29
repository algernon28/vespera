package io.algernon.vespera.pipeline;

/**
 * The sidecars were not answering and Vespera could not make them (ADR-323 §8). The one type the two commands
 * that use a sidecar catch: its message is the one line they print on standard error before they exit 1.
 */
abstract class SidecarStartRefusedException extends RuntimeException {

    SidecarStartRefusedException(String line) {
        super(line);
    }

    /** A command line as run, its parts joined by single spaces. */
    static String asRun(java.util.List<String> command) {
        return String.join(" ", command);
    }
}
