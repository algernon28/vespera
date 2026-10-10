package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code documents.csv} written a page of survivors at a time (ADR-223 section 7): the file is the same
 * bytes however its survivors are cut into pages, and they are the bytes it had when it was composed whole
 * from a list of every survivor.
 *
 * <p>The manifest is a read of its own because its rows are in occurrence order over the whole run, which no
 * seed partition's members are a run of. {@link ThreePartitions}'s eleven survivors alternate between three
 * partitions, so every page of two here holds survivors of two partitions, and a page of five holds all
 * three.
 *
 * <p><b>The expected file is captured</b>: it is {@code documents.csv} of {@link TheTreeWrittenFromLists},
 * which carries no corpus root. {@code ManifestCsvTest} goes on holding the quoting and the stop for a
 * survivor whose cluster the arrangement does not carry.
 *
 * <p>Pure: no database, and no file.
 */
@Epic("Synthesis")
@Feature("The listing of every document the tree arranges")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
class ManifestIsWrittenAPageAtATimeTest {

    private static final String THE_MANIFEST = TheTreeWrittenFromLists.files().get(Deliverable.MANIFEST_FILE_NAME);

    @Test
    @Story("The listing is the same listing, written a page of documents at a time")
    @DisplayName("Handed its documents all at once, two at a time, five at a time or one at a time, the listing is byte for byte the same file")
    void theSameFileHoweverItsSurvivorsArePaged() {
        for (int survivorsInAPage : List.of(Integer.MAX_VALUE, 5, 2, 1)) {
            String pages = survivorsInAPage == Integer.MAX_VALUE ? "one page" : "pages of " + survivorsInAPage;
            claim(
                    "with its eleven documents handed over in " + pages + ", the listing is the header and the"
                            + " eleven rows in the order the documents were first recorded, each with its group's"
                            + " two places, byte for byte as it was when it was composed whole",
                    () -> assertThat(ListedArrangement.manifestContents(
                                    ThreePartitions.arrangement(), ThreePartitions.survivors(), survivorsInAPage))
                            .isEqualTo(THE_MANIFEST));
        }
    }

    @Test
    @Story("The listing is the same listing, written a page of documents at a time")
    @DisplayName("With no document to list, the listing is its header and nothing else")
    void noSurvivorLeavesTheHeader() {
        claim(
                "a listing of nothing still names its columns, so a consumer loading it gets an empty table and"
                        + " not an empty file",
                () -> assertThat(ListedArrangement.manifestContents(ThreePartitions.arrangement(), List.of()))
                        .isEqualTo(THE_MANIFEST.substring(0, THE_MANIFEST.indexOf('\n') + 1)));
    }
}
