package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Command;

/**
 * The {@code run} command's own description says what it does: it takes the archive as far as the next
 * value the operator has to supply, which is the README's sentence for it (ADR-216 section 6).
 *
 * <p>Until ADR-216 the description read "Walks a corpus and records what it holds.", which was the whole of
 * the command when only the census existed. Since ADR-101 the command runs the cascade to its end, each
 * step behind its own gate, and the README's command table already said so. The description is what
 * {@code vespera run --help} and bare {@code vespera} print, so the operator read the old sentence too.
 *
 * <p>Read off the annotation by reflection, with no application context. Red against {@code 3fc6f5d}, by
 * design: the first claim names the old sentence.
 */
@Epic("Pipeline")
@Feature("Invocation")
@Issue("352")
@Link(name = "ADR-216", url = Adr.NOTHING_SHIPS_THAT_NO_DECISION_REQUIRES_AND_NOTHING_CALLS, type = "adr")
class TheRunCommandSaysWhatItDoesTest {

    /** What the command prints about itself: the README's sentence, as a description is written. */
    private static final String WHAT_RUN_DOES = "Walks a corpus and takes it as far as the next missing value.";

    /** The README's line for the command in its table of commands, as the operator reads it. */
    private static final String THE_READMES_LINE =
            "vespera run <root> [--db-dir=<path>]     walk a corpus and take it as far as the next missing value";

    private static final Path README = Path.of("README.md");

    @Test
    @Story("The command says what it does")
    @DisplayName("The run command describes itself as taking the archive as far as the next missing value")
    void theRunCommandDescribesWhatItDoes() {
        Command command = VesperaCommand.Run.class.getAnnotation(Command.class);

        claim(
                "the command's description is the one sentence \"" + WHAT_RUN_DOES + "\": the command runs every"
                        + " step it can, not only the walk, and stops at the first value the operator still has to"
                        + " give",
                () -> assertThat(command.description()).containsExactly(WHAT_RUN_DOES));
    }

    @Test
    @Story("The command says what it does")
    @DisplayName("The README's table of commands describes the run command in the same words")
    void theReadmeSaysTheSame() throws IOException {
        String readme = Files.readString(README);

        claim(
                "the README's table of commands carries the same sentence for it, so the help an operator prints"
                        + " and the page they read agree",
                () -> assertThat(readme).contains(THE_READMES_LINE));
    }
}
