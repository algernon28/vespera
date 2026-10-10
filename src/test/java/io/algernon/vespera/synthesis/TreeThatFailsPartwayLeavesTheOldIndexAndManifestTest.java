package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
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
 * What a write of the tree that fails partway leaves of {@code index.md} and {@code documents.csv} (ADR-223
 * section 6): each is written beside its target and moved into place once it is whole, so a write that stops
 * at its second seed partition leaves both as the write before left them, or leaves neither.
 *
 * <p>Until ADR-223 each was composed whole in memory and written in one call, so neither could be found half
 * written. Written as their rows come, either could be, and a reader opening an index that lists one
 * partition of three has no way to know the other two exist.
 *
 * <p><b>The failure is the source's.</b> The second write is handed a source whose read of the second
 * partition's clusters throws, which is where a statement that fails would throw. It is also handed no
 * synthesis doc at all, so an index it had finished would differ from the first write's in two rows, and an
 * index left as it was is told apart from one written again.
 *
 * <p>Pure: no database.
 */
@Epic("Synthesis")
@Feature("The tree the operator is handed")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-103", url = Adr.THE_DELIVERABLE_IS_A_MARKDOWN_TREE_PER_RUN, type = "adr")
class TreeThatFailsPartwayLeavesTheOldIndexAndManifestTest {

    /** The partition whose clusters cannot be read: the second of three. */
    private static final int THE_SECOND_PARTITION = 2;

    private static final String WHAT_THE_READ_SAID = "the second partition's clusters could not be read";

    @Test
    @Story("A write that fails partway leaves the index and the listing whole")
    @DisplayName("A second write that fails at its second exemplar leaves the index and the listing exactly as the first write left them")
    void theOldIndexAndManifestStand(@TempDir Path base) throws IOException {
        String corpusRoot = base.resolve("archive").toString();
        Path tree = ListedArrangement.writeTo(
                base.resolve("work"),
                ThreePartitions.provenance(corpusRoot),
                ThreePartitions.arrangement(),
                ThreePartitions.written(),
                ThreePartitions.survivors(),
                SurvivorPictures.none(),
                ThreePartitions.unwritten());
        String theIndexBefore = Files.readString(tree.resolve(Deliverable.INDEX_FILE_NAME), StandardCharsets.UTF_8);
        String theManifestBefore = Files.readString(tree.resolve(Deliverable.MANIFEST_FILE_NAME), StandardCharsets.UTF_8);

        Throwable thrown = catchThrowable(() -> Deliverable.writeTo(
                base.resolve("work"),
                ThreePartitions.provenance(corpusRoot),
                aSourceThatFailsAtItsSecondPartition(),
                SurvivorPictures.none(),
                DeliverableProgress.NONE));

        claim(
                "the second write stopped where its source failed, with the source's own failure",
                () -> assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage(WHAT_THE_READ_SAID));
        claim(
                "the index is the first write's, character for character: it still names both written texts,"
                        + " which the second write was not handed and would have left out",
                () -> assertThat(Files.readString(tree.resolve(Deliverable.INDEX_FILE_NAME), StandardCharsets.UTF_8))
                        .isEqualTo(theIndexBefore)
                        .contains("Risers"));
        claim(
                "and the listing is the first write's too",
                () -> assertThat(Files.readString(tree.resolve(Deliverable.MANIFEST_FILE_NAME), StandardCharsets.UTF_8))
                        .isEqualTo(theManifestBefore));
    }

    @Test
    @Story("A write that fails partway leaves the index and the listing whole")
    @DisplayName("A first write that fails at its second exemplar leaves no index and no listing at all")
    void aFirstWriteThatFailsLeavesNeither(@TempDir Path base) {
        String corpusRoot = base.resolve("archive").toString();
        DeliverableProvenance provenance = ThreePartitions.provenance(corpusRoot);

        Throwable thrown = catchThrowable(() -> Deliverable.writeTo(
                base.resolve("work"),
                provenance,
                aSourceThatFailsAtItsSecondPartition(),
                SurvivorPictures.none(),
                DeliverableProgress.NONE));

        Path tree = base.resolve("work").resolve(Deliverable.DIRECTORY_NAME).resolve(provenance.runId());
        claim(
                "the write stopped where its source failed",
                () -> assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage(WHAT_THE_READ_SAID));
        claim(
                "no index stands in the tree, where one listing the first exemplar alone would have read as a"
                        + " finished tree of one exemplar; and no listing either",
                () -> {
                    assertThat(tree.resolve(Deliverable.INDEX_FILE_NAME)).doesNotExist();
                    assertThat(tree.resolve(Deliverable.MANIFEST_FILE_NAME)).doesNotExist();
                });
    }

    /**
     * {@link ThreePartitions}'s arrangement and survivors with no synthesis doc and no reason, behind a source
     * whose read of the second partition's clusters throws.
     */
    private static ArrangedSurvivors aSourceThatFailsAtItsSecondPartition() {
        ArrangedSurvivors whole =
                ListedArrangement.of(ThreePartitions.arrangement(), List.of(), ThreePartitions.survivors(), Map.of());
        return ArrangedSurvivors.reading(
                whole::partitions,
                whole::survivorCount,
                partition -> {
                    if (partition.partitionOrder() == THE_SECOND_PARTITION) {
                        throw new IllegalStateException(WHAT_THE_READ_SAID);
                    }
                    return whole.clustersOf(partition);
                },
                whole::survivorsOf,
                whole::writtenOver,
                whole::whyUnwritten,
                whole::placeOf,
                whole::eachPageOfSurvivors);
    }
}
