package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code CONTEXT.md} names what Vespera runs beside itself (ADR-433 §1, §2, §7): a <b>Container</b> entry, whose
 * {@code _Avoid_} line refuses the word ADR-011 gave first and names the record that retired it, and whose {@code
 * _Renders as_} line says what the operator reads where Vespera runs one and where Docling or Ollama runs on another
 * machine; and no other line of the vocabulary says the earlier word.
 */
@Epic("Architecture")
@Feature("The project's vocabulary")
@Link(name = "ADR-433", url = Adr.DOCLING_AND_OLLAMA_ARE_CONTAINERS_WHERE_VESPERA_RUNS_THEM, type = "adr")
@Issue("891")
class TheVocabularyNamesTheContainerTest {

    private static final Path VOCABULARY = Path.of("CONTEXT.md");

    private static final String HEADING = "**Container**:";

    private static final String AVOID = "_Avoid_:";

    private static final String RENDERS_AS = "_Renders as_:";

    /** The word ADR-011 gave, in any case and inside any longer word. */
    private static final String THE_EARLIER_WORD = "sidecar";

    /** The record that retired it, which the entry's {@code _Avoid_} line names. */
    private static final String THE_RECORD = "ADR-433";

    /** What the operator reads, whole (ADR-433 §2, §7). */
    private static final String WHAT_THE_OPERATOR_READS = RENDERS_AS + " **container** where Vespera runs it; where"
            + " Docling or Ollama runs on another machine, **Docling**, **Ollama** or **the services**, never container.";

    @Test
    @Story("What Vespera runs beside itself is a container")
    @DisplayName("The glossary defines a container, refuses the earlier word for it, and says what the operator reads of one on another machine")
    void theContainerEntryRefusesTheEarlierWordAndSaysWhatTheOperatorReadsOnAnotherMachine() throws IOException {
        List<String> lines = Files.readAllLines(VOCABULARY);
        int heading = lines.indexOf(HEADING);

        claim("the glossary has an entry for a container", () -> assertThat(heading).isNotNegative());
        List<String> entry = lines.subList(heading + 1, lines.size()).stream()
                .takeWhile(line -> !line.isBlank())
                .toList();
        claim("its one list of rejected words opens with sidecar and names the record that retired it",
                () -> assertThat(entry).filteredOn(line -> line.startsWith(AVOID))
                        .singleElement()
                        .satisfies(line -> assertThat(line)
                                .startsWith(AVOID + " " + THE_EARLIER_WORD + ",")
                                .contains(THE_RECORD)));
        claim("its one line of what the operator reads says container where Vespera runs it, and Docling, Ollama or"
                        + " the services where they run on another machine, never container",
                () -> assertThat(entry).filteredOn(line -> line.startsWith(RENDERS_AS))
                        .containsExactly(WHAT_THE_OPERATOR_READS));
    }

    @Test
    @Story("What Vespera runs beside itself is a container")
    @DisplayName("No line of the glossary uses the earlier word, but the container entry's list of rejected words")
    void noLineOfTheVocabularyButTheContainersAvoidLineSaysTheEarlierWord() throws IOException {
        List<String> lines = Files.readAllLines(VOCABULARY);
        int heading = lines.indexOf(HEADING);
        String containersAvoidLine = lines.subList(heading + 1, lines.size()).stream()
                .takeWhile(line -> !line.isBlank())
                .filter(line -> line.startsWith(AVOID))
                .findFirst()
                .orElse("");

        claim("the one line of the glossary that says sidecar is the container entry's list of rejected words",
                () -> assertThat(lines)
                        .filteredOn(line -> line.toLowerCase(Locale.ROOT).contains(THE_EARLIER_WORD))
                        .containsExactly(containersAvoidLine));
    }
}
