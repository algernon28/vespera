package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.synthesis.ArrangedCluster;
import io.algernon.vespera.synthesis.ClusterLabel;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.algernon.vespera.synthesis.ListedDoc;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code index.html}: what produced the tree, then one heading per seed partition and one table row per
 * cluster in it, a written cluster's label linking to its page, {@code index.html} in its cluster folder (ADR-103,
 * ADR-112, ADR-122, ADR-274 §1.4, ADR-280 §0).
 *
 * <p>Until ADR-274 the expected index was what {@code Deliverable.writeTo} wrote as Markdown at {@code 4b99a03}. It
 * is HTML now, so the expected text is composed here: the page's skeleton is {@code Html.head}'s and {@code
 * Html.tail}'s, held by {@code TheTreeIsHtmlTest}, and everything between them is spelled out.
 *
 * <p>Since ADR-223 the index is appended a partition at a time and nothing composes it whole, so the lists
 * here go through {@link ListedArrangement}, which drives the page as the tree's writer does.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Issue("351")
@Link(name = "ADR-213", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
@Link(name = "ADR-280", url = Adr.A_CLUSTERS_FOLDER_HOLDS_ITS_PAGE_AND_ITS_SURVIVORS_COPIES, type = "adr")
class IndexPageTest {

    /** The seed whose partition every cluster below sits in. */
    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    /**
     * The paragraph every index carries under its provenance, saying what the paths and the copies are, and where the
     * copies lie: in each group's folder, with no folder of documents named.
     */
    private static final String WHAT_THE_PATHS_AND_THE_COPIES_ARE = "<p>The path column of documents.csv names"
            + " each document beneath the archive root recorded above. Each group's folder holds its page and a copy"
            + " of every document listed on it, checked against the digest taken when it was converted, and each"
            + " link on a group's page leads to its copy; the archive root names where they were copied from. The"
            + " seed_partition column is not a path beneath that root: it names a seed beneath the seed folder,"
            + " which is a root of its own. The copy and copy_outcome columns say which documents were copied"
            + " and, of any that were not, why.</p>\n";

    /** The table every partition opens with. */
    private static final String THE_TABLE_HEAD =
            "<table>\n<tr><th>Group</th><th>Documents</th><th>Written up as</th></tr>\n";

    /** What a cluster nothing was written over shows in its third cell. */
    private static final String NOTHING_WRITTEN = "<td><em>nothing was written over this group</em></td>";

    /**
     * The index for a partition whose seed path carries an ampersand and brackets, holding one written
     * cluster whose label carries a pipe and whose title a backtick and brackets, and one unwritten
     * cluster whose label carries a line break: only the ampersand is markup to HTML, and is escaped.
     */
    private static final String THE_INDEX = Html.head("Deliverable run-1", DeliverableLanguage.ENGLISH)
            + "<h1>Deliverable run-1</h1>\n"
            + "<ul>\n"
            + "<li>Run: run-1</li>\n"
            + "<li>Walk: 7</li>\n"
            + "<li>Archive root: /srv/archive</li>\n"
            + "<li>relevanceFloor: 0.42</li>\n"
            + "</ul>\n"
            + WHAT_THE_PATHS_AND_THE_COPIES_ARE
            + "<h2>seeds/Fire &amp; Safety [2019].docx</h2>\n"
            + THE_TABLE_HEAD
            + "<tr><td><a href=\"1-fire-safety-2019/1-fire-suppression-retrofits/index.html\">Fire Suppression | Retrofits</a>"
            + "</td><td>4</td><td>Retrofitting `Suppression`, 2018 [draft]</td></tr>\n"
            + "<tr><td>Sprinkler\nMaintenance</td><td>2</td>" + NOTHING_WRITTEN + "</tr>\n"
            + "</table>\n"
            + Html.tail();

    /** The seed's path in {@link #THE_INDEX}. */
    private static final String A_SEED_PATH_WITH_MARKUP = "seeds/Fire & Safety [2019].docx";

    @Test
    @Story("The index lists every cluster under its partition")
    @DisplayName("The index is the provenance, then each partition's heading and one row per cluster, exactly as before")
    void writesTheWholeIndex() {
        List<RecordedCluster> arrangement = List.of(
                new RecordedCluster(
                        new ArrangedCluster(THE_SEED, 0, 4, 1, 1), new ClusterLabel("Fire Suppression | Retrofits")),
                new RecordedCluster(
                        new ArrangedCluster(THE_SEED, 1, 2, 1, 2), new ClusterLabel("Sprinkler\nMaintenance")));
        List<ListedDoc> written = List.of(new ListedDoc(
                THE_SEED,
                0,
                new SynthesisDoc("Retrofitting `Suppression`, 2018 [draft]", "Both [1] and [2].", List.of())));
        List<ListedSurvivor> survivors = List.of(
                survivor(10, 0, A_SEED_PATH_WITH_MARKUP), survivor(12, 1, A_SEED_PATH_WITH_MARKUP));

        claim(
                "the index states the run, the walk, the archive root and every named value, says what the"
                        + " paths and the copies are and that each group's folder holds its copies, heads the"
                        + " partition with its seed path escaped, links the written group's label to the page in its"
                        + " folder, and shows the unwritten one's label as written with no link -- byte for byte",
                () -> assertThat(ListedArrangement.indexContents(
                                new DeliverableProvenance(
                                        "run-1", 7L, "/srv/archive", List.of(new NamedValue("relevanceFloor", "0.42"))),
                                arrangement,
                                written,
                                survivors))
                        .isEqualTo(THE_INDEX));
    }

    @Test
    @Story("The index lists every cluster under its partition")
    @DisplayName("With ten groups in a partition, a group's folder is named with two digits, so the names sort as the groups do")
    void padsThePageNameToTheWidthOfTheLargestOrder() {
        List<RecordedCluster> arrangement = new ArrayList<>();
        List<ListedSurvivor> survivors = new ArrayList<>();
        for (int ordinal = 0; ordinal < 10; ordinal++) {
            arrangement.add(new RecordedCluster(
                    new ArrangedCluster(THE_SEED, ordinal, 1, 1, ordinal + 1),
                    new ClusterLabel("Group " + (ordinal + 1))));
            survivors.add(survivor(100 + ordinal, ordinal, "seeds/Seed One.pdf"));
        }
        List<ListedDoc> written =
                List.of(new ListedDoc(THE_SEED, 9, new SynthesisDoc("Tenth", "x", List.of())));

        String index = ListedArrangement.indexContents(
                new DeliverableProvenance("run-2", 3L, "/srv/archive", List.of()), arrangement, written, survivors);

        claim(
                "the tenth group's page is index.html in the folder 10-group-10 under the partition's folder"
                        + " 1-seed-one, and the first nine would be 01- to 09-, so a file listing sorts as 6a ordered"
                        + " them",
                () -> assertThat(index).endsWith("<tr><td>Group 9</td><td>1</td>" + NOTHING_WRITTEN + "</tr>\n"
                        + "<tr><td><a href=\"1-seed-one/10-group-10/index.html\">Group 10</a></td><td>1</td><td>Tenth</td></tr>\n"
                        + "</table>\n" + Html.tail()));
    }

    @Test
    @Story("The index lists every cluster under its partition")
    @DisplayName("A partition with no seed path is refused before it can reach the index, since its directory cannot be named")
    void refusesAPartitionWithNoSeedPath() {
        claim(
                "the partition's directory is named from its seed's path, so a partition handed over with none"
                        + " is refused where it is made, naming the seed it is about, and the index is never"
                        + " asked to head a table with nothing",
                () -> assertThatThrownBy(() -> new ListedPartition(new OccurrenceId(2), null, 1, 1))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("seed partition 2 has no seed path; a partition directory cannot be named"
                                + " without it"));
        claim(
                "a seed path of nothing but spaces names no directory either, and is refused in the same words",
                () -> assertThatThrownBy(() -> new ListedPartition(new OccurrenceId(2), "  ", 1, 1))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("seed partition 2 has no seed path; a partition directory cannot be named"
                                + " without it"));
    }

    private static ListedSurvivor survivor(long occurrence, int clusterOrdinal, String seedPath) {
        return new ListedSurvivor(
                new OccurrenceId(occurrence),
                new OccurrencePath("reports/" + occurrence + ".pdf"),
                "h" + occurrence,
                THE_SEED,
                seedPath,
                clusterOrdinal,
                0.5);
    }
}
