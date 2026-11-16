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
 * {@code CONTEXT.md} names what a topic is called (ADR-403, Consequences): an entry of its own, whose {@code _Avoid_}
 * line refuses the two words a cluster's two names already hold, so that a topic's name is not read as derived, as a
 * cluster's label is, nor as written over a cluster at 6b, as its title is.
 */
@Epic("Architecture")
@Feature("The project's vocabulary")
@Link(name = "ADR-403", url = Adr.THE_GENERATION_MODEL_NAMES_EACH_TOPIC, type = "adr")
@Issue("829")
class TheVocabularyNamesTheTopicNameTest {

    private static final Path VOCABULARY = Path.of("CONTEXT.md");

    private static final String HEADING = "**Topic name**:";

    private static final String AVOID = "_Avoid_:";

    @Test
    @Story("What a topic is called is a term of the project's own")
    @DisplayName("The glossary defines a topic's name, and refuses calling it a label or a title")
    void topicNameIsATerm() throws IOException {
        List<String> lines = Files.readAllLines(VOCABULARY);
        int heading = lines.indexOf(HEADING);

        claim("The glossary has an entry for a topic's name", () -> assertThat(heading).isNotNegative());
        List<String> entry = lines.subList(heading + 1, lines.size()).stream()
                .takeWhile(line -> !line.isBlank())
                .toList();
        claim(
                "Its list of rejected words opens with topic label, then topic title",
                () -> assertThat(entry).filteredOn(line -> line.startsWith(AVOID))
                        .singleElement()
                        .satisfies(line -> assertThat(line).startsWith(AVOID + " topic label (")
                                .contains("), topic title (")));
    }
}
