package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A working directory already in use is said in one line on standard error, in place of Spring's
 * account of a failed start, and the invocation exits 1 (ADR-177 §1, #364).
 *
 * <p>The same shape as a profile that does not load (#321), for the same reason: the refusal happens
 * while the environment is prepared, so there is no banner, no bean and no context to report, and
 * the reporter claims only its own fault. {@code WorkingDirectoryInUseIT} pins what reaches the
 * terminal and the shell from the packaged jar; this pins the reporter on its own.
 */
@Epic("Architecture")
@Feature("One invocation per working directory")
@Issue("364")
@Link(name = "ADR-177", url = Adr.ONE_INVOCATION_PER_WORKING_DIRECTORY, type = "adr")
@Link(name = "ADR-141", url = Adr.THE_CLI_EXITS_WITH_THE_COMMANDS_EXIT_CODE, type = "adr")
class WorkingDirectoryInUseRefusalTest {

    /** What the holding invocation wrote into the lock file. */
    private static final String HOLDER = "pid=4242 started=2026-09-28T22:38:44+02:00 command=run archive-root";

    /** The whole of what the operator is shown: one line. */
    private static final int ONE_LINE = 1;

    @Test
    @Story("A second invocation on the same working directory is refused")
    @DisplayName("A working directory in use is reported in one line on standard error, wrapped or not")
    void aWorkingDirectoryInUseIsReportedInOneLine() {
        WorkingDirectoryInUseException inUse = new WorkingDirectoryInUseException(Path.of("working-dir"), HOLDER);
        WorkingDirectoryInUseRefusal refusal = new WorkingDirectoryInUseRefusal();

        Reported direct = reported(() -> refusal.reportException(inUse));
        Reported wrapped = reported(() -> refusal.reportException(
                new IllegalStateException("the application failed to start", inUse)));

        claim("the reporter claims the refusal, so Spring prints nothing of its own after it",
                () -> assertThat(direct.claimed()).isTrue());
        claim("having printed the refusal's message as one line on standard error",
                () -> assertThat(direct.printed().lines().toList()).containsExactly(inUse.getMessage()));
        claim("and it claims the same refusal arriving wrapped in another failure, printing the same one line",
                () -> {
                    assertThat(wrapped.claimed()).isTrue();
                    assertThat(wrapped.printed().lines().toList()).hasSize(ONE_LINE).containsExactly(inUse.getMessage());
                });
        claim("the entry point is told the failure was this refusal, wrapped or not",
                () -> {
                    assertThat(WorkingDirectoryInUseRefusal.refuses(inUse)).isTrue();
                    assertThat(WorkingDirectoryInUseRefusal.refuses(new IllegalStateException("wrapped", inUse))).isTrue();
                });
    }

    /** Whether the reporter claimed the failure, and what it printed on standard error meanwhile. */
    private record Reported(boolean claimed, String printed) {
    }

    private interface Report {
        boolean report();
    }

    private static Reported reported(Report report) {
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            boolean claimed = report.report();
            return new Reported(claimed, captured.toString(StandardCharsets.UTF_8));
        } finally {
            System.setErr(original);
        }
    }
}
