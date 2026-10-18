package io.algernon.vespera.deliverable;

import static io.algernon.vespera.HoverTexts.attributeOf;
import static io.algernon.vespera.HoverTexts.divsOf;
import static io.algernon.vespera.HoverTexts.escapedForItsAttributes;
import static io.algernon.vespera.HoverTexts.titleOf;
import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.ListedTree.cluster;
import static io.algernon.vespera.deliverable.ListedTree.filesUnder;
import static io.algernon.vespera.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.deliverable.ListedTree.survivor;
import static io.algernon.vespera.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.Unwritten;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every {@code <div>} of the deliverable carries a hover text saying what it shows, fixed words holding nothing of
 * the cluster it stands on, so a tree drawn again says the same of every cluster and keys no run (ADR-275 §1 to §3,
 * ADR-274 §5). The tree is written once for the class, over an archive that is not there, with two clusters written
 * over, of different titles and writing, and one nothing was written over.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-275", url = Adr.EVERY_DIV_SAYS_ON_HOVER_WHAT_IT_SHOWS_AND_EVERY_STEP_WHAT_IT_DOES, type = "adr")
class EveryDivOfTheTreeSaysWhatItShowsTest {

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/Fire Safety.docx";

    private static final RecordedCluster FIRST_WRITTEN = cluster(THE_SEED, 0, "Fire doors", 1, 1);

    private static final RecordedCluster SECOND_WRITTEN = cluster(THE_SEED, 1, "Sprinklers", 2, 1);

    private static final RecordedCluster UNWRITTEN = cluster(THE_SEED, 2, "Smoke dampers", 3, 1);

    /** The first cluster's title and writing, which its hover must not hold. */
    private static final String FIRST_TITLE = "Fire doors and their inspection";

    private static final String FIRST_PROSE = "Doors are inspected yearly [1].";

    /** The second cluster's, different in every word from the first's. */
    private static final String SECOND_TITLE = "Sprinkler heads & pipes";

    private static final String SECOND_PROSE = "Heads are replaced when corroded [1].";

    /** The pages of the tree, by their path beneath it. */
    private static final Map<String, String> PAGES = new LinkedHashMap<>();

    @TempDir
    static Path base;

    @BeforeAll
    static void writeTheTree() throws IOException {
        Path tree = ListedTree.write(
                base.resolve("work"),
                base.resolve("archive"),
                List.of(FIRST_WRITTEN, SECOND_WRITTEN, UNWRITTEN),
                Map.of(
                        slotOf(FIRST_WRITTEN), writing(FIRST_TITLE, FIRST_PROSE, 10),
                        slotOf(SECOND_WRITTEN), writing(SECOND_TITLE, SECOND_PROSE, 11)),
                List.of(
                        survivor(10, "reports/doors.docx", "0".repeat(64), THE_SEED, SEED_PATH, 0, 0.9),
                        survivor(11, "reports/sprinklers.docx", "1".repeat(64), THE_SEED, SEED_PATH, 1, 0.8),
                        survivor(12, "reports/dampers.pdf", "2".repeat(64), THE_SEED, SEED_PATH, 2, 0.7)),
                Map.of(slotOf(UNWRITTEN), Unwritten.NO_SENDABLE_DOCUMENT));
        for (String name : filesUnder(tree)) {
            if (name.endsWith(".html")) {
                PAGES.put(name, Files.readString(tree.resolve(name), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    @Story("Every part of a page says what it shows when the pointer rests on it")
    @DisplayName("Every box of every page of the tree carries a description shown on hover, escaped in its tag")
    void everyDivOfEveryPageOfTheTreeCarriesAHoverText() {
        List<String> boxes = new ArrayList<>();
        PAGES.values().forEach(page -> boxes.addAll(divsOf(page)));

        claim(
                "the tree's pages hold boxes to describe, the writing of each of the two groups written over among"
                        + " them",
                () -> assertThat(boxes.stream().filter(tag -> "writing".equals(attributeOf(tag, "class")))).hasSize(2));
        PAGES.forEach((name, page) -> claim(
                "every box of " + name + " carries a description that is not blank, its tag a list of attributes in"
                        + " double quotes with no angle bracket, no quote and no bare ampersand in any value",
                () -> assertThat(divsOf(page)).allSatisfy(tag -> {
                    assertThat(titleOf(tag)).as(tag).isNotBlank();
                    assertThat(escapedForItsAttributes(tag)).as(tag).isTrue();
                })));
    }

    @Test
    @Story("Every part of a page says what it shows when the pointer rests on it")
    @DisplayName("The writing's description is one text on the pages of two groups of different titles and writing, and holds neither")
    void theWritingsHoverHoldsNothingOfTheCluster() {
        List<String> descriptions = new ArrayList<>();
        PAGES.values().forEach(page -> divsOf(page).stream()
                .filter(tag -> "writing".equals(attributeOf(tag, "class")))
                .forEach(tag -> descriptions.add(titleOf(tag))));

        claim(
                "the writing of each of the two groups written over carries a description",
                () -> assertThat(descriptions).hasSize(2).doesNotContainNull());
        claim(
                "the two are one text, though their groups' titles and writing share no word",
                () -> assertThat(descriptions.stream().distinct()).hasSize(1));
        claim(
                "and it holds neither group's label, title or writing",
                () -> assertThat(descriptions).allSatisfy(description -> assertThat(description).doesNotContain(
                        FIRST_WRITTEN.label().value(), SECOND_WRITTEN.label().value(),
                        FIRST_TITLE, SECOND_TITLE, FIRST_PROSE, SECOND_PROSE)));
    }
}
