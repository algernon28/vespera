package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
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
 * {@code index.md}: what produced the tree, then one heading per seed partition and one table row per
 * cluster in it, a written cluster's label linking to its page (ADR-103, ADR-112, ADR-122).
 *
 * <p><b>Every expected index here is what {@code Deliverable.writeTo} wrote at {@code 4b99a03}</b> for
 * the same provenance, arrangement, writing and survivors.
 */
@Epic("Synthesis")
@Feature("The tree the operator is handed")
@Issue("351")
@Link(name = "ADR-213", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
@Link(name = "ADR-103", url = Adr.THE_DELIVERABLE_IS_A_MARKDOWN_TREE_PER_RUN, type = "adr")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
class IndexPageTest {

    /** The seed whose partition every cluster below sits in. */
    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    /** The paragraph every index carries under its provenance, saying what the paths resolve against. */
    private static final String WHAT_THE_PATHS_RESOLVE_AGAINST = "\nThe path column of documents.csv names each"
            + " document beneath the archive root recorded above, and the links inside a group's page resolve"
            + " against it too. The seed_partition column is not one of those: it names a seed beneath the seed"
            + " folder, which is a root of its own. Nothing here is a copy of the archive, so if the archive"
            + " moves, everything resolved against that archive root dies until this tree is re-pointed at"
            + " where it went.\n";

    /** The table every partition opens with. */
    private static final String THE_TABLE_HEAD = "| Group | Documents | Written up as |\n|---|---|---|\n";

    /**
     * The index for a partition whose seed path carries an ampersand and brackets, holding one written
     * cluster whose label carries a pipe and whose title a backtick and brackets, and one unwritten
     * cluster whose label carries a line break.
     */
    private static final String THE_INDEX = "# Deliverable run-1\n"
            + "\n"
            + "- Run: run-1\n"
            + "- Walk: 7\n"
            + "- Archive root: /srv/archive\n"
            + "- relevanceFloor: 0.42\n"
            + WHAT_THE_PATHS_RESOLVE_AGAINST
            + "\n"
            + "## seeds/Fire \\& Safety \\[2019\\].docx\n"
            + "\n"
            + THE_TABLE_HEAD
            + "| [Fire Suppression \\| Retrofits](1-fire-safety-2019/1-fire-suppression-retrofits.md) | 4 |"
            + " Retrofitting \\`Suppression\\`, 2018 \\[draft\\] |\n"
            + "| Sprinkler Maintenance | 2 | *nothing was written over this group* |\n";

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
        List<RecordedSynthesisDoc> written = List.of(new RecordedSynthesisDoc(
                THE_SEED,
                0,
                new SynthesisDoc("Retrofitting `Suppression`, 2018 [draft]", "Both [1] and [2].", List.of())));
        List<ListedSurvivor> survivors = List.of(
                survivor(10, 0, A_SEED_PATH_WITH_MARKUP), survivor(12, 1, A_SEED_PATH_WITH_MARKUP));

        claim(
                "the index states the run, the walk, the archive root and every named value, says what the"
                        + " paths resolve against, heads the partition with its seed path escaped as a heading,"
                        + " links the written cluster's label escaped as a cell to its page, and shows the"
                        + " unwritten one's label folded with no link -- byte for byte as the deliverable wrote it",
                () -> assertThat(IndexPage.contents(
                                new DeliverableProvenance(
                                        "run-1", 7L, "/srv/archive", List.of(new NamedValue("relevanceFloor", "0.42"))),
                                arrangement,
                                written,
                                survivors))
                        .isEqualTo(THE_INDEX));
    }

    @Test
    @Story("The index lists every cluster under its partition")
    @DisplayName("With ten clusters in a partition, a page's name is padded to two digits, so the names sort as the clusters do")
    void padsThePageNameToTheWidthOfTheLargestOrder() {
        List<RecordedCluster> arrangement = new ArrayList<>();
        List<ListedSurvivor> survivors = new ArrayList<>();
        for (int ordinal = 0; ordinal < 10; ordinal++) {
            arrangement.add(new RecordedCluster(
                    new ArrangedCluster(THE_SEED, ordinal, 1, 1, ordinal + 1),
                    new ClusterLabel("Group " + (ordinal + 1))));
            survivors.add(survivor(100 + ordinal, ordinal, "seeds/Seed One.pdf"));
        }
        List<RecordedSynthesisDoc> written =
                List.of(new RecordedSynthesisDoc(THE_SEED, 9, new SynthesisDoc("Tenth", "x", List.of())));

        String index = IndexPage.contents(
                new DeliverableProvenance("run-2", 3L, "/srv/archive", List.of()), arrangement, written, survivors);

        claim(
                "the tenth cluster's page is named 10-group-10.md under the partition's directory 1-seed-one,"
                        + " and the first nine would be 01- to 09-, so a file listing sorts as 6a ordered them",
                () -> assertThat(index).endsWith("| Group 9 | 1 | *nothing was written over this group* |\n"
                        + "| [Group 10](1-seed-one/10-group-10.md) | 1 | Tenth |\n"));
    }

    @Test
    @Story("The index lists every cluster under its partition")
    @DisplayName("A partition whose seed no survivor names stops the writer, since its directory cannot be named")
    void refusesAPartitionNoSurvivorNames() {
        List<RecordedCluster> arrangement = List.of(new RecordedCluster(
                new ArrangedCluster(new OccurrenceId(2), 0, 1, 1, 1), new ClusterLabel("Orphaned")));

        claim(
                "the partition's directory is named from its seed's path, which only a survivor carries, so a"
                        + " partition no survivor names is refused with the seed it is about",
                () -> assertThatThrownBy(() -> IndexPage.contents(
                                new DeliverableProvenance("run-3", 1L, "/srv/archive", List.of()),
                                arrangement,
                                List.of(),
                                List.of(survivor(10, 0, "seeds/Seed One.pdf"))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("no survivor names the seed of partition 2; a partition directory cannot be"
                                + " named without it"));
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
