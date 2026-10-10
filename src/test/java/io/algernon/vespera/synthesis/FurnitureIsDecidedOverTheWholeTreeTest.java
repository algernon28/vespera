package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The furniture rule's population after ADR-223 (section 7): still every picture of every survivor the tree
 * lists (ADR-149 section 1), although the tree is written one seed partition at a time and the first pass is
 * fed a page of survivors at a time.
 *
 * <p>The danger this holds off is the cheap bound: deciding furniture over one partition, or one page, would
 * hold less, and would show a logo that two partitions share under both. The operator kept the whole tree.
 * So the picture that recurs here recurs across two partitions and across two pages, with a survivor of a
 * third partition between them, and each page holds one survivor.
 *
 * <p>The pictures are bytes that decode to no image, so they have no difference hash and only recurrence
 * can make furniture of them, as in {@code EntryPicturesTest}. The near-copy and same-place conditions are
 * held end to end by {@code DeliverablePicturesTest}, which reaches the writer through the same source.
 *
 * <p>Pure: no database, and no file.
 */
@Epic("Synthesis")
@Feature("The pictures a document carries")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-149", url = Adr.A_SURVIVORS_PICTURES_REACH_ITS_CLUSTER_FILE, type = "adr")
class FurnitureIsDecidedOverTheWholeTreeTest {

    private static final String PNG = "image/png";

    /** Carried by survivor 10, of the first partition, and by survivor 15, of the second. */
    private static final byte[] A_LOGO_TWO_PARTITIONS_SHARE = "a logo two partitions share".getBytes(StandardCharsets.UTF_8);

    /** Carried by survivor 12 alone, of the third partition. */
    private static final byte[] A_DIAGRAM = "a diagram".getBytes(StandardCharsets.UTF_8);

    private static final OccurrenceId IN_THE_FIRST_PARTITION = new OccurrenceId(10);

    private static final OccurrenceId IN_THE_THIRD_PARTITION = new OccurrenceId(12);

    private static final OccurrenceId IN_THE_SECOND_PARTITION = new OccurrenceId(15);

    /** One survivor a page, so the two carriers of the logo are never in hand together. */
    private static final int ONE_SURVIVOR_IN_A_PAGE = 1;

    @Test
    @Story("A picture that recurs anywhere in the tree is furniture")
    @DisplayName("A picture two documents under two different exemplars share is furniture, though the documents are never handed over together")
    void aPictureSharedAcrossPartitionsAndPagesIsFurniture() {
        List<OccurrenceId> asked = new ArrayList<>();

        EntryPictures pictures = EntryPictures.among(
                ListedArrangement.of(
                        ThreePartitions.arrangement(),
                        List.of(),
                        ThreePartitions.survivors(),
                        Map.of(),
                        ONE_SURVIVOR_IN_A_PAGE),
                occurrence -> {
                    asked.add(occurrence);
                    return picturesOf(occurrence);
                },
                DeliverableProgress.NONE);

        claim(
                "each of the eleven documents was asked for its pictures once, in the order they were first"
                        + " recorded, so the two that share the logo were five documents and several pages apart",
                () -> assertThat(asked)
                        .extracting(OccurrenceId::value)
                        .containsExactly(10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L));
        claim(
                "the logo is furniture: its bytes recur among the pictures of everything the tree lists, and"
                        + " which exemplar each document sits under has nothing to do with it",
                () -> assertThat(pictures.furniture()).contains(digestOf(A_LOGO_TWO_PARTITIONS_SHARE)));
        claim(
                "the diagram, which one document carries, is not",
                () -> assertThat(pictures.furniture()).doesNotContain(digestOf(A_DIAGRAM)));
    }

    private static List<ListedPicture> picturesOf(OccurrenceId occurrence) {
        if (occurrence.equals(IN_THE_FIRST_PARTITION) || occurrence.equals(IN_THE_SECOND_PARTITION)) {
            return List.of(new ListedPicture(PNG, A_LOGO_TWO_PARTITIONS_SHARE, false, ""));
        }
        if (occurrence.equals(IN_THE_THIRD_PARTITION)) {
            return List.of(new ListedPicture(PNG, A_DIAGRAM, false, ""));
        }
        return List.of();
    }

    /** {@code bytes}' SHA-256 in lower-case hexadecimal, which is how the furniture is named. */
    private static String digestOf(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }
}
