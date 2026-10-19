package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.ListedTree.cluster;
import static io.algernon.vespera.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.deliverable.ListedTree.survivor;
import static io.algernon.vespera.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A citation on a split cluster's first page links the page its entry is on (ADR-303 §6, amending ADR-109 and ADR-135
 * in the {@code href} alone): {@code #document-n} where entry n is on the first page, {@code ./index-<k>.html#document-n}
 * where it is on page k, and {@code #document-n} still for a number no entry has. {@code ProseHtml} is told the page of
 * each number and escapes first, as ADR-274 §1.3 has it.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122).
 */
@Epic("The deliverable")
@Feature("The page a group is read on")
@Issue("571")
@Link(name = "ADR-303", url = Adr.A_CLUSTERS_PAGE_PAST_THE_CEILING_IS_SPLIT_INTO_PAGES, type = "adr")
@Link(name = "ADR-135", url = Adr.A_MEMBERSHIP_ENTRY_LINKS_RELATIVELY_OR_NOT_AT_ALL, type = "adr")
class ACitationLinksToThePageItsEntryIsOnTest {

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/Fire Safety.docx";

    /** The writing over a cluster of three documents, citing each and a ninth no entry has. */
    private static final String PROSE = "Doors [1], frames [2], closers [3] & hinges [9].";

    /** That writing as it reads on a page holding entry 1, with entry 2 on the second page and 3 on the third. */
    private static final String ON_A_FIRST_PAGE_OF_THREE = "<p>Doors <a href=\"#document-1\">[1]</a>,"
            + " frames <a href=\"./index-2.html#document-2\">[2]</a>,"
            + " closers <a href=\"./index-3.html#document-3\">[3]</a>"
            + " &amp; hinges <a href=\"#document-9\">[9]</a>.</p>";

    /** That writing as it reads on a page holding every entry. */
    private static final String ON_A_PAGE_OF_EVERY_ENTRY = "<p>Doors <a href=\"#document-1\">[1]</a>,"
            + " frames <a href=\"#document-2\">[2]</a>,"
            + " closers <a href=\"#document-3\">[3]</a>"
            + " &amp; hinges <a href=\"#document-9\">[9]</a>.</p>";

    @Test
    @Story("A citation lands on its document, whichever page lists it")
    @DisplayName("Told the page each number is on, the writing links a citation on this page by its anchor and one on another page by that page and its anchor")
    void aCitationOfAnEntryOnAnotherPageNamesThatPage() {
        String html = ProseHtml.of(PROSE, number -> number == 2 ? "index-2.html" : number == 3 ? "index-3.html" : "");

        claim(
                "[1] links #document-1 on this page; [2] and [3] link the second and third pages and the anchor on each;"
                        + " [9], whose page is given as this one, links #document-9; and the ampersand is escaped",
                () -> assertThat(html).isEqualTo(ON_A_FIRST_PAGE_OF_THREE));
    }

    @Test
    @Story("A citation lands on its document, whichever page lists it")
    @DisplayName("The writing rendered with no page given links every citation on its own page, as it always did")
    void theFormWithNoPageLinksEveryCitationOnItsOwnPage() {
        claim(
                "the form of one argument links every citation by its anchor alone, the same as being told every number"
                        + " is on this page",
                () -> {
                    assertThat(ProseHtml.of(PROSE)).isEqualTo(ON_A_PAGE_OF_EVERY_ENTRY);
                    assertThat(ProseHtml.of(PROSE, number -> "")).isEqualTo(ON_A_PAGE_OF_EVERY_ENTRY);
                });
    }

    @Test
    @Story("A citation lands on its document, whichever page lists it")
    @DisplayName("On a group split into pages, each citation of the writing links the page its document is listed on, and a number no document has links its anchor")
    void onADrawnFirstPageEachCitationLinksItsEntrysPage(@TempDir Path base) throws IOException {
        RecordedCluster doors = cluster(THE_SEED, 0, "Fire doors", 1, 3);
        List<ListedSurvivor> members = List.of(
                survivor(10, "reports/a.docx", "0".repeat(64), THE_SEED, SEED_PATH, 0, 0.9),
                survivor(11, "reports/b.docx", "1".repeat(64), THE_SEED, SEED_PATH, 0, 0.8),
                survivor(12, "reports/c.docx", "2".repeat(64), THE_SEED, SEED_PATH, 0, 0.7));

        Path tree = ListedTree.writeWithCeiling(
                base.resolve("work"), base.resolve("archive"), List.of(doors),
                Map.of(slotOf(doors), writing("Fire doors and their inspection", PROSE, 10, 11, 12)), members, Map.of(),
                SurvivorPictures.none(), new ClusterPageCeiling.Bytes(1), DeliverableProgress.NONE);
        String firstPage = Files.readString(tree.resolve("1-fire-safety/1-fire-doors/index.html"), StandardCharsets.UTF_8);
        String secondPage = Files.readString(tree.resolve("1-fire-safety/1-fire-doors/index-2.html"), StandardCharsets.UTF_8);

        claim(
                "split at a ceiling of one byte, a page an entry, the first page's writing links [1] on itself, [2] and"
                        + " [3] on the second and third pages, and [9], which no entry has, by its anchor as it always did",
                () -> assertThat(firstPage).contains(ON_A_FIRST_PAGE_OF_THREE));
        claim(
                "and the second page carries the anchor its citation names, and no writing",
                () -> assertThat(secondPage).contains("<li id=\"document-2\">").doesNotContain("class=\"writing\""));
    }
}
