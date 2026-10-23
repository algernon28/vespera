package io.algernon.vespera.pipeline;

/**
 * How a command Vespera ran ended (ADR-322 §3 to §5): its exit code and everything it printed, both streams
 * together. A command that had to be killed for running past its limit has an exit code that is not zero.
 */
record ProcessOutcome(int exitCode, String output) {

    /** The last line of the output that is not blank, or {@code no output} where there is none (ADR-322 §8). */
    String lastLine() {
        return output.lines()
                .filter(line -> !line.isBlank())
                .reduce((first, second) -> second)
                .map(String::strip)
                .orElse("no output");
    }
}
