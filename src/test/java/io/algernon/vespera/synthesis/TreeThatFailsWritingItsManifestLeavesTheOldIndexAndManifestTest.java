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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a write of the tree that fails while it writes {@code documents.csv} leaves of {@code index.md} and
 * {@code documents.csv} (ADR-223 section 6, and {@link Deliverable}'s own contract): the pair the write before
 * left, or none. The index is whole before the manifest is begun, so a write that moved it into place then
 * would leave a new index beside the old manifest, or beside no manifest at all.
 *
 * <p>{@code TreeThatFailsPartwayLeavesTheOldIndexAndManifestTest} fails the write at its second seed
 * partition, before the index is whole. This fails it after: the manifest's read hands its first page of
 * survivors and then throws, which is where a statement that fails would throw. The source is handed no
 * synthesis doc at all, so an index it had moved into place would differ from the first write's, and an index
 * left as it was is told apart from one written again.
 *
 * <p>Pure: no database.
 */
@Epic("Synthesis")
@Feature("The tree the operator is handed")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-103", url = Adr.THE_DELIVERABLE_IS_A_MARKDOWN_TREE_PER_RUN, type = "adr")
class TreeThatFailsWritingItsManifestLeavesTheOldIndexAndManifestTest {

    /** Survivors the manifest's read hands over at a time: four, so the eleven of the fixture are three pages. */
    private static final int SURVIVORS_IN_A_PAGE = 4;

    /** The page of the manifest's read that cannot be read: the second. */
    private static final int THE_SECOND_PAGE = 2;

    private static final String WHAT_THE_READ_SAID = "the listing's second page of documents could not be read";

    @Test
    @Story("A write that fails partway leaves the index and the listing whole")
    @DisplayName("A second write that fails while it writes the listing leaves the index and the listing exactly as the first write left them")
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
                aSourceThatFailsAtTheManifestsSecondPage(),
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
    @DisplayName("A first write that fails while it writes the listing leaves no index and no listing at all")
    void aFirstWriteThatFailsLeavesNeither(@TempDir Path base) {
        String corpusRoot = base.resolve("archive").toString();
        DeliverableProvenance provenance = ThreePartitions.provenance(corpusRoot);

        Throwable thrown = catchThrowable(() -> Deliverable.writeTo(
                base.resolve("work"),
                provenance,
                aSourceThatFailsAtTheManifestsSecondPage(),
                SurvivorPictures.none(),
                DeliverableProgress.NONE));

        Path tree = base.resolve("work").resolve(Deliverable.DIRECTORY_NAME).resolve(provenance.runId());
        claim(
                "the write stopped where its source failed",
                () -> assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage(WHAT_THE_READ_SAID));
        claim(
                "no index stands in the tree, where one standing alone would read as a finished tree with its"
                        + " listing missing; and no listing either",
                () -> {
                    assertThat(tree.resolve(Deliverable.INDEX_FILE_NAME)).doesNotExist();
                    assertThat(tree.resolve(Deliverable.MANIFEST_FILE_NAME)).doesNotExist();
                });
    }

    /**
     * {@link ThreePartitions}'s arrangement and survivors with no synthesis doc and no reason, behind a source
     * whose manifest read hands its first page of {@link #SURVIVORS_IN_A_PAGE} survivors and throws at its
     * second. Every other read, the pictures' read of every survivor among them, answers in full.
     */
    private static ArrangedSurvivors aSourceThatFailsAtTheManifestsSecondPage() {
        ArrangedSurvivors whole = ListedArrangement.of(
                ThreePartitions.arrangement(), List.of(), ThreePartitions.survivors(), Map.of(), SURVIVORS_IN_A_PAGE);
        return ArrangedSurvivors.reading(
                whole::partitions,
                whole::survivorCount,
                whole::clustersOf,
                whole::survivorsOf,
                whole::writtenOver,
                whole::whyUnwritten,
                whole::placesOf,
                whole::eachPageOfSurvivorsForTheirPictures,
                page -> {
                    AtomicInteger pages = new AtomicInteger();
                    whole.eachPageOfSurvivorsForTheManifest(survivors -> {
                        if (pages.incrementAndGet() == THE_SECOND_PAGE) {
                            throw new IllegalStateException(WHAT_THE_READ_SAID);
                        }
                        page.accept(survivors);
                    });
                });
    }
}
