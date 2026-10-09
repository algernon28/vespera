package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * One cluster's page: its heading, the writing or the sentence written in its place (ADR-174), the
 * sentence saying the writing rests on part of the cluster, the citations as links (ADR-109), and the
 * membership list numbered from what the call sent (ADR-133).
 *
 * <p><b>Every expected page here is what {@code Deliverable.writeClusterFile} wrote at {@code 4b99a03}</b>
 * for the same cluster, writing and members, with the page four directories below the parent of the
 * archive, as every page of a tree is.
 */
@Epic("Synthesis")
@Feature("The page a group is read on")
@Issue("351")
@Link(name = "ADR-212", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
@Link(name = "ADR-133", url = Adr.THE_EXEMPLARS_ONE_CALL_SENT_ARE_RECORDED, type = "adr")
@Link(name = "ADR-109", url = Adr.A_CITATION_IS_AN_ORDINAL_MINTED_FOR_ONE_CALL, type = "adr")
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

    private static final ListedSurvivor MIDDLE = member(14, "reports/f.pdf", 0.6);

    /** Two members scoring the same, for the order a tie keeps. */
    private static final ListedSurvivor TIED_FIRST = member(12, "reports/d.pdf", 0.7);

    private static final ListedSurvivor TIED_SECOND = member(13, "reports/e.pdf", 0.7);

    /**
     * What the model wrote: a title carrying a backtick and brackets, prose citing the second, the first
     * and the third document it was sent, and a sent list whose third document the cluster no longer
     * holds.
     */
    private static final SynthesisDoc THE_WRITING = new SynthesisDoc(
            "Retrofitting `Suppression`, 2018 [draft]",
            "Both [2] and [1] agree; [3] is gone.",
            List.of(LOWEST.occurrence(), HIGHEST.occurrence(), GONE_SINCE_THE_CALL));

    /** The cluster that writing is over: four documents by 6a's count, three of them still listed. */
    private static final RecordedCluster THE_WRITTEN_CLUSTER = new RecordedCluster(
            new ArrangedCluster(THE_SEED, 0, 4, 1, 1), new ClusterLabel("Fire Suppression | Retrofits"));

    /** A cluster nothing was written over, whose label carries a line break. */
    private static final RecordedCluster THE_UNWRITTEN_CLUSTER = new RecordedCluster(
            new ArrangedCluster(THE_SEED, 1, 2, 1, 2), new ClusterLabel("Sprinkler\nMaintenance"));

    /** The written cluster's page, whole. */
    private static final String THE_WRITTEN_PAGE = "# Retrofitting \\`Suppression\\`, 2018 \\[draft\\]\n"
            + "\n"
            + "Both [2](#document-2) and [1](#document-1) agree; [3](#document-3) is gone.\n"
            + "\n"
            + "Written from the first 3 of the 4 documents in this group.\n"
            + "\n"
            + "## The documents in this group\n"
            + "\n"
            + "1. <a id=\"document-1\"></a>[reports/b, c.pdf](../../../../archive/reports/b,%20c.pdf)\n"
            + "\n"
            + "2. <a id=\"document-2\"></a>[reports/a.docx](../../../../archive/reports/a.docx)\n"
            + "\n"
            + "3. <a id=\"document-3\"></a>*a document the writing was made from, which this group no longer holds*\n"
            + "\n"
            + "4. <a id=\"document-4\"></a>[reports/f.pdf](../../../../archive/reports/f.pdf)\n"
            + "\n";

    /** The unwritten cluster's page, whole, where no reason was handed over. */
    private static final String THE_UNWRITTEN_PAGE = "# Sprinkler Maintenance\n"
            + "\n"
            + "*nothing was written over this group*\n"
            + "\n"
            + "## The documents in this group\n"
            + "\n"
            + "1. <a id=\"document-1\"></a>[reports/d.pdf](../../../../archive/reports/d.pdf)\n"
            + "\n"
            + "2. <a id=\"document-2\"></a>[reports/e.pdf](../../../../archive/reports/e.pdf)\n"
            + "\n";

    @Test
    @Story("The membership list is numbered from what the call sent")
    @DisplayName("The documents the call sent are numbered first, in the order sent, a gone one keeps its number, and the rest follow by score")
    void numbersTheSentDocumentsFirstThenTheRestByScore() {
        List<Optional<ListedSurvivor>> numbered =
                ClusterPage.numbered(THE_WRITING, List.of(HIGHEST, LOWEST, MIDDLE));

        claim(
                "entries 1 to 3 are the call's exemplars in the order it sent them, so the model's [n] lands on"
                        + " the document it was written about (ADR-133); the third was sent and is no longer in"
                        + " the cluster, so its number is kept, empty, rather than given to another document;"
                        + " and the one member the call did not send follows, as entry 4",
                () -> assertThat(numbered)
                        .containsExactly(
                                Optional.of(LOWEST), Optional.of(HIGHEST), Optional.empty(), Optional.of(MIDDLE)));
    }

    @Test
    @Story("The membership list is numbered from what the call sent")
    @DisplayName("Where nothing was written, the members are numbered by score, and a tie keeps the order they arrived in")
    void numbersByScoreWhereNothingWasWritten() {
        List<Optional<ListedSurvivor>> numbered =
                ClusterPage.numbered(null, List.of(TIED_FIRST, TIED_SECOND, HIGHEST));

        claim(
                "with no call there is no record of what was sent, so the list is in relevance-score order,"
                        + " highest first, and two documents scoring the same keep the order they were handed"
                        + " over in, which is the order the exemplars are drawn in",
                () -> assertThat(numbered)
                        .containsExactly(Optional.of(HIGHEST), Optional.of(TIED_FIRST), Optional.of(TIED_SECOND)));
    }

    @Test
    @Story("A citation is a link to the entry it numbers")
    @DisplayName("Each bracketed number in the writing becomes a link to the entry with that number, and nothing else is touched")
    void rewritesEachCitationAsALinkToItsEntry() {
        claim(
                "a citation [n] becomes [n](#document-n), whatever n is, so no raw marker survives into the page",
                () -> assertThat(ClusterPage.withCitationLinks("Both [2] and [1] agree; [12] too."))
                        .isEqualTo("Both [2](#document-2) and [1](#document-1) agree; [12](#document-12) too."));
        claim(
                "brackets around anything but digits, a spaced number, or a number in braces are not citations,"
                        + " and are left exactly as the model wrote them",
                () -> assertThat(ClusterPage.withCitationLinks("See [x], [ 1], {1} and [1a]."))
                        .isEqualTo("See [x], [ 1], {1} and [1a]."));
    }

    @Test
    @Story("A written page")
    @DisplayName("A written page is its title, its writing with links, the subset sentence, and the numbered list, exactly as before")
    void writesAWrittenPageWhole(@TempDir Path base) throws IOException {
        Path page = aPageIn(base, "1-fire-suppression-retrofits.md");

        ClusterPage.write(
                page,
                THE_WRITTEN_CLUSTER,
                THE_WRITING,
                null,
                List.of(HIGHEST, LOWEST, MIDDLE),
                theArchiveUnder(base),
                noPictures(),
                DeliverableProgress.NONE);

        claim(
                "the page is the title escaped as a heading, the writing with each citation a link, the sentence"
                        + " saying it was written from 3 of the 4 documents, and the list numbered from what the"
                        + " call sent, each entry a relative link -- byte for byte as the deliverable wrote it",
                () -> assertThat(Files.readString(page, StandardCharsets.UTF_8)).isEqualTo(THE_WRITTEN_PAGE));
    }

    @Test
    @Story("A page nothing was written over")
    @DisplayName("A page nothing was written over is headed by its label on one line, says so, and lists its documents by score")
    void writesAnUnwrittenPageWhole(@TempDir Path base) throws IOException {
        Path page = aPageIn(base, "2-sprinkler-maintenance.md");

        ClusterPage.write(
                page,
                THE_UNWRITTEN_CLUSTER,
                null,
                null,
                List.of(TIED_FIRST, TIED_SECOND),
                theArchiveUnder(base),
                noPictures(),
                DeliverableProgress.NONE);

        claim(
                "with no writing and no reason handed over, the page is the label folded onto one line as a"
                        + " heading, the sentence saying nothing was written, and the members by score -- byte"
                        + " for byte as the deliverable wrote it",
                () -> assertThat(Files.readString(page, StandardCharsets.UTF_8)).isEqualTo(THE_UNWRITTEN_PAGE));
    }

    @Test
    @Story("A page nothing was written over")
    @DisplayName("A page nothing was written over carries the reason it was handed, in the place the writing would be")
    void writesTheReasonInPlaceOfTheWriting(@TempDir Path base) throws IOException {
        Path page = aPageIn(base, "2-sprinkler-maintenance.md");

        ClusterPage.write(
                page,
                THE_UNWRITTEN_CLUSTER,
                null,
                Unwritten.NOT_REACHED,
                List.of(TIED_FIRST, TIED_SECOND),
                theArchiveUnder(base),
                noPictures(),
                DeliverableProgress.NONE);

        claim(
                "the reason's sentence replaces the plain one and nothing else on the page changes (ADR-174)",
                () -> assertThat(Files.readString(page, StandardCharsets.UTF_8))
                        .isEqualTo(THE_UNWRITTEN_PAGE.replace(
                                Deliverable.NOTHING_WAS_WRITTEN_OVER_IT, Unwritten.NOT_REACHED.sentence())));
    }

    @Test
    @Story("A written page")
    @DisplayName("Each entry that carries a document is reported once as written, and an entry for a gone document is not")
    void reportsEachEntryThatCarriesADocument(@TempDir Path base) throws IOException {
        AtomicInteger entriesReported = new AtomicInteger();
        DeliverableProgress counting = new DeliverableProgress() {
            @Override
            public void membershipEntryWritten() {
                entriesReported.incrementAndGet();
            }
        };

        ClusterPage.write(
                aPageIn(base, "1-fire-suppression-retrofits.md"),
                THE_WRITTEN_CLUSTER,
                THE_WRITING,
                null,
                List.of(HIGHEST, LOWEST, MIDDLE),
                theArchiveUnder(base),
                noPictures(),
                counting);

        claim(
                "three of the page's four entries carry a document, and those three are reported, so the count"
                        + " the step announced before it began (ADR-192 §5) is the count it reaches",
                () -> assertThat(entriesReported.get()).isEqualTo(3));
    }

    /** A page's path four directories below {@code base}, its partition directory made, as the writer makes it. */
    private static Path aPageIn(Path base, String fileName) throws IOException {
        Path partition = base.resolve("wd").resolve("deliverable").resolve("run-1").resolve("1-fire-safety-2019");
        Files.createDirectories(partition);
        return partition.resolve(fileName).toAbsolutePath();
    }

    /** The archive, beside the working directory. */
    private static String theArchiveUnder(Path base) {
        return base.resolve("archive").toAbsolutePath().toString();
    }

    /** Pictures for a tree whose documents carry none. */
    private static EntryPictures noPictures() {
        return EntryPictures.among(List.of(), SurvivorPictures.none(), DeliverableProgress.NONE);
    }

    private static ListedSurvivor member(long occurrence, String path, double score) {
        return new ListedSurvivor(
                new OccurrenceId(occurrence), new OccurrencePath(path), "h" + occurrence, THE_SEED, SEED_PATH, 0, score);
    }
}
