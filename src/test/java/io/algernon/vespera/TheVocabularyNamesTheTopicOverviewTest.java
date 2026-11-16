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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code CONTEXT.md} names a topic's overview (ADR-402, Consequences): an entry of its own, whose {@code _Avoid_} line
 * refuses the word a synthesis doc's refuses first, so that the overview is not called a summary of its clusters.
 */
@Epic("Architecture")
@Feature("The project's vocabulary")
@Link(name = "ADR-402", url = Adr.A_TOPICS_OVERVIEW_CITES_ITS_WRITTEN_CLUSTERS, type = "adr")
@Issue("830")
class TheVocabularyNamesTheTopicOverviewTest {

    private static final Path VOCABULARY = Path.of("CONTEXT.md");

    private static final String HEADING = "**Topic overview**:";

    private static final String AVOID = "_Avoid_:";

    @Test
    @Story("A topic's overview is a term of the project's own")
    @DisplayName("The glossary defines a topic overview, and refuses calling it a summary")
    void topicOverviewIsATerm() throws IOException {
        List<String> lines = Files.readAllLines(VOCABULARY);
        int heading = lines.indexOf(HEADING);

        claim("The glossary has an entry for a topic overview", () -> assertThat(heading).isNotNegative());
        List<String> entry = lines.subList(heading + 1, lines.size()).stream()
                .takeWhile(line -> !line.isBlank())
                .toList();
        claim(
                "Its list of rejected words opens with summary",
                () -> assertThat(entry).filteredOn(line -> line.startsWith(AVOID))
                        .singleElement()
                        .satisfies(line -> assertThat(line).startsWith(AVOID + " summary,")));
    }
}
