package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.deliverable.ListedTree.sha256Of;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One cluster's page: its heading, the writing or the sentence written in its place (ADR-174), the
 * sentence saying the writing rests on part of the cluster, the citations as links (ADR-109), and the
 * membership list numbered from what the call sent (ADR-133), each entry leading to its copy beside the page or
 * saying why none stands (ADR-274 §1.4, §2.6, ADR-280 §5).
 *
 * <p>Each page is written by the stream form of {@code ClusterPage.write} into its file (ADR-286 §2).
 *
 * <p>Every expected page here is the page's body as ADR-274 §1.4 lays it out, inside the skeleton {@link Html}
 * writes for every page of the tree, which {@code TheTreeIsHtmlTest} holds. The page is {@code index.html} in its
 * cluster's folder of a tree under {@code <base>/wd}, the copies beside it (ADR-280 §0), the archive is {@code
 * <base>/archive}, and each member's file there holds its own path's bytes, which its recorded key is taken from,
 * save one member whose file is not there.
 */
@Epic("The deliverable")
@Feature("The page a group is read on")
@Issue("351")
@Link(name = "ADR-213", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
@Link(name = "ADR-133", url = Adr.THE_EXEMPLARS_ONE_CALL_SENT_ARE_RECORDED, type = "adr")
@Link(name = "ADR-109", url = Adr.A_CITATION_IS_AN_ORDINAL_MINTED_FOR_ONE_CALL, type = "adr")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
@Link(name = "ADR-280", url = Adr.A_CLUSTERS_FOLDER_HOLDS_ITS_PAGE_AND_ITS_SURVIVORS_COPIES, type = "adr")
class ClusterPageTest {

    /** The seed whose partition the cluster sits in. */
    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    /** The seed's path, as each member carries it. */
    private static final String SEED_PATH = "seeds/Fire & Safety [2019].docx";

    /** A document the call sent that the cluster no longer holds: its entry keeps its number and says so. */
    private static final OccurrenceId GONE_SINCE_THE_CALL = new OccurrenceId(99);

    /** The cluster's three members, in the order they are handed over, and their scores. */
    private static final ListedSurvivor HIGHEST = member(10, "reports/a.docx", 0.9);

    private static final ListedSurvivor LOWEST = member(11, "reports/b, c.pdf", 0.5);

    /** The one member whose file is no longer in the archive, so no copy of it stands. */
    private static final ListedSurvivor MIDDLE = member(14, "reports/f.pdf", 0.6);

    /**
     * What the model wrote: a title carrying a backtick, an ampersand and brackets, prose citing the second, the
     * first and the third document it was sent, and a sent list whose third document the cluster no longer holds.
     */
    private static final SynthesisDoc THE_WRITING = new SynthesisDoc(
            "Retrofitting `Suppression` & Sprinklers, 2018 [draft]",
            "Both [2] and [1] agree; [3] is gone.",
            List.of(LOWEST.occurrence(), HIGHEST.occurrence(), GONE_SINCE_THE_CALL));

    @Test
    @Story("The membership list is numbered from what the call sent")
    @DisplayName("The documents the call sent are numbered first, in the order sent, a gone one keeps its number, and the rest follow by score")
    void numbersTheSentDocumentsFirstThenTheRestByScore() {
        List<Optional<ListedSurvivor>> numbered =
                ClusterPage.numbered(THE_WRITING, List.of(HIGHEST, LOWEST, MIDDLE));

        claim(
                "entries 1 to 3 are the call's exemplars in the order it sent them, so the model's [n] lands on"
                        + " the document it was written about; the third was sent and is no longer in"
                        + " the cluster, so its number is kept, empty, rather than given to another document;"
                        + " and the one member the call did not send follows, as entry 4",
                () -> assertThat(numbered)
                        .containsExactly(
                                Optional.of(LOWEST), Optional.of(HIGHEST), Optional.empty(), Optional.of(MIDDLE)));
    }

    /** Where an entry links to the copy whose name is {@code encoded} once percent-encoded: beside the page. */
    private static String beside(String encoded) {
        return "./" + encoded;
    }

    /** What a member's file holds: its own path, so its recorded key follows from the path alone. */
    private static byte[] bytesOf(String path) {
        return path.getBytes(StandardCharsets.UTF_8);
    }

    private static ListedSurvivor member(long occurrence, String path, double score) {
        return new ListedSurvivor(
                new OccurrenceId(occurrence),
                new OccurrencePath(path),
                sha256Of(bytesOf(path)),
                THE_SEED,
                SEED_PATH,
                0,
                score);
    }
}
