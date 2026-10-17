package io.algernon.vespera.deliverable;

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
 * bytes however its survivors are cut into pages.
 *
 * <p>The manifest is a read of its own because its rows are in occurrence order over the whole run, which no
 * seed partition's members are a run of. {@link ThreePartitions}'s eleven survivors alternate between three
 * partitions, so every page of two here holds survivors of two partitions, and a page of five holds all
 * three.
 *
 * <p><b>The expected file is composed here</b>, row by row from {@link ThreePartitions}, where until ADR-274 it was a
 * capture of the Markdown tree; the archive is not there, so every copy is {@code gone} and its cell empty. {@code
 * ManifestCsvTest} goes on holding the quoting and the stop for a survivor whose cluster the arrangement does not
 * carry.
 *
 * <p>Pure: no database, and no file written.
 */
@Epic("The deliverable")
@Feature("The listing of every document the tree arranges")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
class ManifestIsWrittenAPageAtATimeTest {

    private static final String THE_HEADER = "occurrence_id,path,content_hash,winning_seed,relevance_score,"
            + "seed_partition,cluster,partition_order,cluster_order,copy,copy_outcome\n";

    private static final String THE_MANIFEST = THE_HEADER
            + "10,reports/doors a.docx,key10,2,0.5,seeds/Beta & Co [2019].pdf,0,1,1,,gone\n"
            + "11,\"reports/stairs, north.pdf\",key11,1,0.7,seeds/alpha.docx,0,2,1,,gone\n"
            + "12,reports/sprinklers-1.pdf,key12,3,0.4,seeds/gamma.docx,1,3,1,,gone\n"
            + "13,reports/doors [final].docx,key13,2,0.9,seeds/Beta & Co [2019].pdf,0,1,1,,gone\n"
            + "14,reports/dampers.pdf,key14,2,0.6,seeds/Beta & Co [2019].pdf,1,1,2,,gone\n"
            + "15,notes/risers.txt,key15,1,0.8,seeds/alpha.docx,2,2,2,,gone\n"
            + "16,notes/hose reels.txt,key16,3,0.3,seeds/gamma.docx,0,3,2,,gone\n"
            + "17,reports/doors b.docx,key17,2,0.5,seeds/Beta & Co [2019].pdf,0,1,1,,gone\n"
            + "18,\"reports/stairs, south.pdf\",key18,1,0.7,seeds/alpha.docx,0,2,1,,gone\n"
            + "19,reports/sprinklers-2.pdf,key19,3,0.4,seeds/gamma.docx,1,3,1,,gone\n"
            + "20,\"reports/dampers \"\"old\"\".pdf\",key20,2,0.65,seeds/Beta & Co [2019].pdf,1,1,2,,gone\n";

    @Test
    @Story("The listing is the same listing, written a page of documents at a time")
    @DisplayName("Handed its documents all at once, two at a time, five at a time or one at a time, the listing is byte for byte the same file")
    void theSameFileHoweverItsSurvivorsArePaged() {
        for (int survivorsInAPage : List.of(Integer.MAX_VALUE, 5, 2, 1)) {
            String pages = survivorsInAPage == Integer.MAX_VALUE ? "one page" : "pages of " + survivorsInAPage;
            claim(
                    "with its eleven documents handed over in " + pages + ", the listing is the header and the"
                            + " eleven rows in the order the documents were first recorded, each with its group's"
                            + " two places and what became of its copy, byte for byte",
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
                        .isEqualTo(THE_HEADER));
    }
}
