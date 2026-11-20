package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code CONTEXT.md} names the top level of the arrangement a topic and retires the seed partition (ADR-396 §10).
 *
 * <p>It reads the vocabulary as entries: a line {@code **Name**:} opens one, and the lines beneath it up to the next
 * blank line are its own. A line opening with an underscore, as {@code _Avoid_:} and {@code _Renders as_:} do, is one
 * of the entry's labelled lines; every other line is its definition.
 *
 * <p>The first test holds that "topic" is a term: it has an entry, and so has the winning topic, and no {@code
 * _Avoid_} line lists it as a rejected word, as the seed partition's did. The second holds that the seed partition is
 * retired: no entry's definition names one but the retired entry of its page's, which opens by saying it is retired.
 * The third holds that the seed partition has no entry of its own since ADR-426: its words stand on the topic's
 * {@code _Avoid_} line, citing that record.
 */
@Epic("Architecture")
@Feature("The project's vocabulary")
@Link(name = "ADR-396", url = Adr.TOPICS_REPLACE_SEED_PARTITIONS, type = "adr")
@Issue("825")
class TheVocabularyNamesTopicsAndRetiresTheSeedPartitionTest {

    private static final Path VOCABULARY = Path.of("CONTEXT.md");

    private static final Pattern ENTRY_HEADING = Pattern.compile("^\\*\\*(.+)\\*\\*:$");

    private static final String AVOID = "_Avoid_:";

    /** What an {@code _Avoid_} line says of a word it lists, which is no word it rejects. */
    private static final Pattern A_REASON_IN_PARENTHESES = Pattern.compile("\\([^)]*\\)");

    private static final String TOPIC = "Topic";

    private static final String WINNING_TOPIC = "Winning topic";

    /** The one retired entry left: the seed partition's own went to the topic's {@code _Avoid_} line (ADR-426). */
    private static final List<String> THE_RETIRED_ENTRIES = List.of("Seed partition's page");

    /** The heading the seed partition's own entry had, which no entry has now. */
    private static final String THE_RETIRED_TERMS_ENTRY = "Seed partition";

    /** The record that took the seed partition's entry down to an {@code _Avoid_} line. */
    private static final String THE_RECORD_RETIRING_ITS_NAMES = "ADR-426";

    private static final String RETIRED_BY_THE_RECORD = "Retired by ADR-396";

    /** The retired term, which its plural "seed partitions" also holds. */
    private static final String THE_RETIRED_TERM = "seed partition";

    /** How the topic's definition says a topic may be the operator's (ADR-435 section 13). */
    private static final String LISTED_IN_THE_PROFILE = "listed by the operator in the profile's `topics`, by name";

    /** The record that lets the operator list the topics. */
    private static final String THE_RECORD_LETTING_IT_BE_LISTED = "ADR-435";

    /** What the definition said before ADR-435, which no longer holds. */
    private static final String NEVER_SET_BY_THE_OPERATOR = "never set by the operator";

    /** Each entry's name, in the order the vocabulary gives them, to the lines beneath its heading. */
    private static final Map<String, List<String>> ENTRIES = entries();

    private static Map<String, List<String>> entries() {
        Map<String, List<String>> read = new LinkedHashMap<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(VOCABULARY);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> current = null;
        for (String line : lines) {
            Matcher heading = ENTRY_HEADING.matcher(line);
            if (heading.matches()) {
                current = new ArrayList<>();
                read.put(heading.group(1), current);
            } else if (line.isBlank()) {
                current = null;
            } else if (current != null) {
                current.add(line);
            }
        }
        return read;
    }

    private static String definitionOf(final List<String> lines) {
        return String.join("\n", lines.stream().filter(line -> !line.startsWith("_")).toList());
    }

    @Test
    @Story("A topic is a term of the project's own")
    @DisplayName("The glossary defines a topic and a winning topic, and no list of rejected words holds topic or topics")
    void topicIsATermAndNoAvoidLineRejectsIt() {
        List<String> rejected = new ArrayList<>();
        ENTRIES.values().forEach(lines -> lines.stream()
                .filter(line -> line.startsWith(AVOID))
                .map(line -> A_REASON_IN_PARENTHESES.matcher(line.substring(AVOID.length())).replaceAll(""))
                .flatMap(words -> List.of(words.split(",")).stream())
                .map(word -> word.strip().toLowerCase(Locale.ROOT))
                .forEach(rejected::add));

        claim("The glossary has an entry for a topic and one for a winning topic",
                () -> assertThat(ENTRIES).containsKeys(TOPIC, WINNING_TOPIC));
        claim("No glossary entry lists topic, or topics, among the words it rejects",
                () -> assertThat(rejected).isNotEmpty().doesNotContain("topic", "topics"));
    }

    @Test
    @Story("The seed partition is retired")
    @DisplayName("Only the retired glossary entry for the seed partition's page uses the words 'seed partition' in its definition, and it says it is retired")
    void noDefinitionButTheRetiredOnesNamesASeedPartition() {
        Map<String, String> naming = new LinkedHashMap<>();
        ENTRIES.forEach((name, lines) -> {
            String definition = definitionOf(lines);
            if (definition.toLowerCase(Locale.ROOT).contains(THE_RETIRED_TERM)) {
                naming.put(name, definition);
            }
        });

        claim("No entry's definition uses the words 'seed partition' but the retired entry for the seed partition's page",
                () -> assertThat(naming.keySet()).isSubsetOf(THE_RETIRED_ENTRIES));
        claim("That retired entry's definition opens by saying the record retired it",
                () -> assertThat(THE_RETIRED_ENTRIES).allSatisfy(name ->
                        assertThat(definitionOf(ENTRIES.get(name))).startsWith(RETIRED_BY_THE_RECORD)));
    }

    @Test
    @Link(name = "ADR-426", url = Adr.THE_SEED_PARTITIONS_NAMES_ARE_RETIRED_FROM_THE_CODE_AND_THE_TESTS, type = "adr")
    @Issue("895")
    @Story("The seed partition is retired")
    @DisplayName("The seed partition has no glossary entry of its own: the topic's list of rejected words carries it, and names the record that retired it from every name")
    void theSeedPartitionHasNoEntryAndTheTopicsAvoidLineRetiresIt() {
        List<String> topicsAvoidLines = ENTRIES.get(TOPIC).stream().filter(line -> line.startsWith(AVOID)).toList();

        claim("No glossary entry is headed by the seed partition any more",
                () -> assertThat(ENTRIES).doesNotContainKey(THE_RETIRED_TERMS_ENTRY));
        claim("The topic's one list of rejected words lists the seed partition and names the record that retired its"
                        + " names",
                () -> assertThat(topicsAvoidLines).singleElement().satisfies(line -> assertThat(line)
                        .contains(THE_RETIRED_TERM, THE_RECORD_RETIRING_ITS_NAMES)));
    }

    @Test
    @Link(name = "ADR-435", url = Adr.THE_PROFILE_MAY_LIST_THE_TOPICS_BY_NAME, type = "adr")
    @Issue("898")
    @Story("A topic may be listed by the operator")
    @DisplayName("The glossary's topic may be listed by the operator in the profile, by name, and no longer says it is never set by the operator")
    void aTopicMayBeListedInTheProfile() {
        String definition = definitionOf(ENTRIES.get(TOPIC));

        claim("The topic's definition says a topic is listed by the operator in the profile's topics, by name, citing the"
                        + " record that lets it be",
                () -> assertThat(definition).contains(LISTED_IN_THE_PROFILE, THE_RECORD_LETTING_IT_BE_LISTED));
        claim("and it no longer says a topic is never set by the operator",
                () -> assertThat(definition).doesNotContain(NEVER_SET_BY_THE_OPERATOR));
    }
}
