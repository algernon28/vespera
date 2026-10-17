package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.ReplacingMove;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A write of the tree whose move of {@code index.md} into place is refused twice as access denied, as Windows
 * refuses a move while a virus scanner, an indexer or a folder kept in step with a cloud holds the page it has
 * just seen written, is still finished: the index and the manifest stand in their places, the same bytes the
 * write gives with no refusal, and no {@code .part} file is left (ADR-242, ADR-223 section 6). At 6b a thrown
 * move rolls back every synthesis doc the invocation wrote.
 *
 * <p>Pure: no database, and no Windows. The move is stood in for by one refused as Windows refuses it, and the
 * waits between attempts are counted and not slept.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-242", url = Adr.A_MOVE_REFUSED_AS_ACCESS_DENIED_IS_ATTEMPTED_AGAIN, type = "adr")
class TreeWhoseIndexIsRefusedForAMomentIsStillPutInPlaceTest {

    /** How many times the move of the index is refused before it goes through. */
    private static final int REFUSED_TWICE = 2;

    @Test
    @Story("A page is put in its place whole")
    @DisplayName("A tree whose index cannot be moved into place twice in a row, because another program holds it, is finished on the third attempt")
    void theTreeIsFinishedOnTheThirdAttempt(@TempDir Path base) throws IOException {
        String corpusRoot = base.resolve("archive").toString();
        Path unrefused = ListedArrangement.writeTo(
                base.resolve("unrefused"),
                ThreePartitions.provenance(corpusRoot),
                ThreePartitions.arrangement(),
                ThreePartitions.written(),
                ThreePartitions.survivors(),
                SurvivorPictures.none(),
                ThreePartitions.unwritten());
        List<String> moved = new ArrayList<>();
        int[] indexRefusals = {0};
        ReplacingMove refusingTheIndexTwice = new ReplacingMove(
                (part, target) -> {
                    moved.add(target.getFileName().toString());
                    if (target.getFileName().toString().equals(Deliverable.INDEX_FILE_NAME)
                            && indexRefusals[0] < REFUSED_TWICE) {
                        indexRefusals[0]++;
                        throw new AccessDeniedException(part.toString(), target.toString(), null);
                    }
                    Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                },
                millis -> {});

        Path[] tree = new Path[1];
        Throwable thrown = catchThrowable(() -> tree[0] = Deliverable.writeTo(
                base.resolve("work"),
                ThreePartitions.provenance(corpusRoot),
                ListedArrangement.of(
                        ThreePartitions.arrangement(),
                        ThreePartitions.written(),
                        ThreePartitions.survivors(),
                        ThreePartitions.unwritten()),
                SurvivorPictures.none(),
                DeliverableProgress.NONE,
                refusingTheIndexTwice));

        claim("the write finished, and nothing was thrown", () -> assertThat(thrown).isNull());
        claim(
                "the index was attempted three times, the first two refused, and the listing once, after it",
                () -> assertThat(moved).containsExactly(
                        Deliverable.INDEX_FILE_NAME,
                        Deliverable.INDEX_FILE_NAME,
                        Deliverable.INDEX_FILE_NAME,
                        Deliverable.MANIFEST_FILE_NAME));
        claim(
                "the index and the listing in their places are those a write with no refusal gives, character for"
                        + " character",
                () -> {
                    assertThat(Files.readString(tree[0].resolve(Deliverable.INDEX_FILE_NAME), StandardCharsets.UTF_8))
                            .isEqualTo(Files.readString(unrefused.resolve(Deliverable.INDEX_FILE_NAME), StandardCharsets.UTF_8));
                    assertThat(Files.readString(tree[0].resolve(Deliverable.MANIFEST_FILE_NAME), StandardCharsets.UTF_8))
                            .isEqualTo(Files.readString(unrefused.resolve(Deliverable.MANIFEST_FILE_NAME), StandardCharsets.UTF_8));
                });
        claim(
                "no page is left beside its place, so the tree reads as finished",
                () -> {
                    try (Stream<Path> files = Files.walk(tree[0])) {
                        assertThat(files.map(Path::toString)).noneMatch(name -> name.endsWith(Deliverable.PART_SUFFIX));
                    }
                });
    }
}
