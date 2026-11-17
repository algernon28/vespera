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
 * {@code CONTEXT.md} names the level between a topic and its clusters (ADR-406 §6, as ADR-396 §10 left it to #831): a
 * <b>Subtopic</b> entry, whose {@code _Avoid_} line refuses "sub-group" first, the word the map called it by, and
 * "group", a cluster's rendering for a reader outside the project (ADR-122); and no other entry refuses the word.
 */
@Epic("Architecture")
@Feature("The project's vocabulary")
@Link(name = "ADR-406", url = Adr.A_CROWDED_TOPIC_IS_DIVIDED_INTO_SUBTOPICS, type = "adr")
@Issue("831")
class TheVocabularyNamesTheSubtopicTest {

    private static final Path VOCABULARY = Path.of("CONTEXT.md");

    private static final String HEADING = "**Subtopic**:";

    private static final String AVOID = "_Avoid_:";

    @Test
    @Story("The level between a topic and its clusters is a term of the project's own")
    @DisplayName("The glossary defines a subtopic, and refuses calling it a sub-group or a group")
    void subtopicIsATerm() throws IOException {
        List<String> lines = Files.readAllLines(VOCABULARY);
        int heading = lines.indexOf(HEADING);

        claim("The glossary has an entry for a subtopic", () -> assertThat(heading).isNotNegative());
        List<String> entry = lines.subList(heading + 1, lines.size()).stream()
                .takeWhile(line -> !line.isBlank())
                .toList();
        claim("Its list of rejected words opens with sub-group, and names group",
                () -> assertThat(entry).filteredOn(line -> line.startsWith(AVOID))
                        .singleElement()
                        .satisfies(line -> assertThat(line)
                                .startsWith(AVOID + " sub-group,")
                                .contains(" group (")));
    }

    @Test
    @Story("The level between a topic and its clusters is a term of the project's own")
    @DisplayName("No entry of the glossary refuses the word subtopic")
    void noAvoidLineRejectsSubtopic() throws IOException {
        List<String> avoidLines = Files.readAllLines(VOCABULARY).stream()
                .filter(line -> line.startsWith(AVOID))
                .toList();

        claim("No _Avoid_ line lists subtopic as a rejected word",
                () -> assertThat(avoidLines)
                        .noneMatch(line -> line.toLowerCase(Locale.ROOT).matches(".*\\bsub-?topics?\\b.*")));
    }
}
