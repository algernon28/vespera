package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.deliverable.ListedTree.cluster;
import static io.algernon.vespera.engine.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.engine.deliverable.ListedTree.survivor;
import static io.algernon.vespera.engine.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.ReplacingMove;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code cluster-page-sizes.html}, which every drawing writes in the working directory, whatever the ceiling (ADR-303
 * §8): the key and how it reads, how many groups, their one-page sizes' largest, 95th percentile and median by nearest
 * rank, how many pass each of four marks, the ten largest with their entries and pages, and, where the ceiling is set,
 * what it split; in numbers and fixed words, and nothing that names a cluster, a partition, a document or a path.
 *
 * <p>The expected numbers are read off the tree the same drawing wrote, a page's size being its file's: the report's
 * claim is that it states the pages the operator is handed. Each tree is drawn over an empty archive, so every copy is
 * {@code gone}, and a group's size is made by the length of its documents' paths.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122).
 */
@Epic("The deliverable")
@Feature("The page a group is read on")
@Issue("571")
@Link(name = "ADR-303", url = Adr.A_CLUSTERS_PAGE_PAST_THE_CEILING_IS_SPLIT_INTO_PAGES, type = "adr")
class TheClusterPageSizesReportTest {

    /** The report's name, in the working directory beside the other reports. */
    private static final String THE_REPORT = "cluster-page-sizes.html";

    /** The tree's index, which is put in place before the report. */
    private static final String THE_INDEX = "index.html";

    /** What a file is called while it is written beside its place. */
    private static final String WHILE_IT_IS_WRITTEN = ".part";

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/Fire Safety.docx";

    /**
     * A report that cannot be put in place fails nothing, and its {@code .part} is removed (ADR-303 §8): the tree is
     * already in place, and the report is the operator's evidence, not part of it.
     */
    @Test
    @Story("Every drawing reports how large the groups' pages are")
    @DisplayName("A report whose move is refused fails nothing: the tree is drawn, no report stands, and nothing is left beside its place")
    void aReportThatCannotBeMovedFailsNothingAndLeavesNoPart(@TempDir Path base) {
        Path work = base.resolve("work");
        ReplacingMove refusingTheReport = new ReplacingMove(
                (from, to) -> {
                    if (to.equals(work.resolve(THE_REPORT))) {
                        throw new IOException("the report's place refuses it");
                    }
                    Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                },
                millis -> {});

        Throwable thrown = catchThrowable(() -> drawASmallTree(base, refusingTheReport));
        Path tree = treeUnder(base);

        claim(
                "the drawing throws nothing",
                () -> assertThat(thrown).isNull());
        claim(
                "the tree's index is in place",
                () -> assertThat(tree.resolve(THE_INDEX)).isRegularFile());
        claim(
                "no report stands in the working directory, and no file is left under the name it was written under",
                () -> {
                    assertThat(work.resolve(THE_REPORT)).doesNotExist();
                    assertThat(work.resolve(THE_REPORT + WHILE_IT_IS_WRITTEN)).doesNotExist();
                });
    }

    /** Where {@link #drawASmallTree} draws its tree under {@code base}. */
    private static Path treeUnder(Path base) {
        return DeliverableRoot.UNNAMED.treeOf(base.resolve("work"), ListedTree.provenance(base.resolve("archive")).runId());
    }

    /** One group of two short documents, written over, drawn under {@code base} with no ceiling, put in place by {@code moves}. */
    private static Path drawASmallTree(Path base, ReplacingMove moves) {
        RecordedCluster small = cluster(THE_SEED, 0, "Alarm panel", 1, 2);
        long[] next = {1_000};
        return ListedTree.writeWithCeiling(
                base.resolve("work"), base.resolve("archive"), List.of(small),
                Map.of(slotOf(small), writing("Alarm panel", "Panels [1] and bells [2].", 1_000, 1_001)),
                members(0, 2, false, next), Map.of(), new ClusterPageCeiling.Unset(), DeliverableProgress.NONE, moves);
    }

    /** {@code count} survivors of the cluster of {@code ordinal}, their paths near 800 characters where {@code long}. */
    private static List<ListedSurvivor> members(int ordinal, int count, boolean longPaths, long[] next) {
        List<ListedSurvivor> members = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            long occurrence = next[0]++;
            String path = (longPaths ? "r/".repeat(395) : "reports/") + occurrence + ".docx";
            members.add(survivor(occurrence, path, "0".repeat(64), THE_SEED, SEED_PATH, ordinal, 0.9 - at * 1e-4));
        }
        return members;
    }

}
