package io.algernon.vespera.engine.pipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Five classes the detector of {@link NoCommandOpensAnythingTest} is run over, to show it reports a way to open a file
 * and nothing else (ADR-362 section 2). They are never run; only their compiled form is read.
 */
final class OpenersUnderTestFixtures {

    private OpenersUnderTestFixtures() {}

    /** Starts a process on the constant {@code open}: reported. */
    static final class StartsAProcessOnOpen {
        Process start(Path file) throws IOException {
            return new ProcessBuilder(List.of("open", file.toString())).start();
        }
    }

    /** Loads {@code open} and runs its list through the runner: reported. */
    static final class RunsOpenThroughTheRunner {
        private final ProcessRunner runner = null;

        void open(Path file) throws Exception {
            runner.run(List.of("open", file.toString()), Duration.ofSeconds(1));
        }
    }

    /** Loads {@code rundll32} and starts no process: reported. */
    static final class LoadsRundll32 {
        List<String> command() {
            return List.of("rundll32");
        }
    }

    /** Holds {@code open} as a key alone: not reported. */
    static final class HoldsOpenAsAKey {
        Map<String, Integer> key() {
            return Map.of("open", 1);
        }
    }

    /** References {@code ProcessBuilder} and loads none of the four constants: not reported. */
    static final class StartsAProcessOnNoneOfThem {
        Process start() throws IOException {
            return new ProcessBuilder("ls").start();
        }
    }
}
