package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.ListedTree.sha256Of;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.ReplacingMove;
import io.algernon.vespera.synthesis.ArrangedCluster;
import io.algernon.vespera.synthesis.ClusterLabel;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.algernon.vespera.synthesis.Unwritten;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.OutputStream;
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

    /** Two members scoring the same, for the order a tie keeps. */
    private static final ListedSurvivor TIED_FIRST = member(12, "reports/d.pdf", 0.7);

    private static final ListedSurvivor TIED_SECOND = member(13, "reports/e.pdf", 0.7);

    /** Every member whose file the archive holds. */
    private static final List<ListedSurvivor> IN_THE_ARCHIVE = List.of(HIGHEST, LOWEST, TIED_FIRST, TIED_SECOND);

    /**
     * What the model wrote: a title carrying a backtick, an ampersand and brackets, prose citing the second, the
     * first and the third document it was sent, and a sent list whose third document the cluster no longer holds.
     */
    private static final SynthesisDoc THE_WRITING = new SynthesisDoc(
            "Retrofitting `Suppression` & Sprinklers, 2018 [draft]",
            "Both [2] and [1] agree; [3] is gone.",
            List.of(LOWEST.occurrence(), HIGHEST.occurrence(), GONE_SINCE_THE_CALL));

    /** The cluster that writing is over: four documents by 6a's count, three of them still listed. */
    private static final RecordedCluster THE_WRITTEN_CLUSTER = new RecordedCluster(
            new ArrangedCluster(THE_SEED, 0, 4, 1, 1), new ClusterLabel("Fire Suppression | Retrofits"));

    /** A cluster nothing was written over, whose label carries a line break. */
    private static final RecordedCluster THE_UNWRITTEN_CLUSTER = new RecordedCluster(
            new ArrangedCluster(THE_SEED, 1, 2, 1, 2), new ClusterLabel("Sprinkler\nMaintenance"));

    /** The written cluster's page, whole. */
    private static final String THE_WRITTEN_PAGE = Html.head(THE_WRITING.title(), DeliverableLanguage.ENGLISH)
            + "<h1>Retrofitting `Suppression` &amp; Sprinklers, 2018 [draft]</h1>\n"
            + "<div class=\"writing\" title=\"What the local model wrote over this group from the documents listed below it;"
            + " each number in brackets is a link to the document it cites.\">\n"
            + "<p>Both <a href=\"#document-2\">[2]</a> and <a href=\"#document-1\">[1]</a> agree;"
            + " <a href=\"#document-3\">[3]</a> is gone.</p>\n"
            + "</div>\n"
            + "<p>Written from the first 3 of the 4 documents in this group.</p>\n"
            + "<h2>The documents in this group</h2>\n"
            + "<ol>\n"
            + "<li id=\"document-1\">\n"
            + "<a href=\"" + beside("b%2C%20c.pdf") + "\">reports/b, c.pdf</a>\n"
            + "</li>\n"
            + "<li id=\"document-2\">\n"
            + "<a href=\"" + beside("a.docx") + "\">reports/a.docx</a>\n"
            + "</li>\n"
            + "<li id=\"document-3\">\n"
            + "<em>a document the writing was made from, which this group no longer holds</em>\n"
            + "</li>\n"
            + "<li id=\"document-4\">\n"
            + "reports/f.pdf <em>no longer in the archive, so it was not copied</em>\n"
            + "</li>\n"
            + "</ol>\n"
            + Html.tail();

    /** The unwritten cluster's page, whole, where no reason was handed over. */
    private static final String THE_UNWRITTEN_PAGE = Html.head(THE_UNWRITTEN_CLUSTER.label().value(), DeliverableLanguage.ENGLISH)
            + "<h1>Sprinkler\nMaintenance</h1>\n"
            + "<p><em>nothing was written over this group</em></p>\n"
            + "<h2>The documents in this group</h2>\n"
            + "<ol>\n"
            + "<li id=\"document-1\">\n"
            + "<a href=\"" + beside("d.pdf") + "\">reports/d.pdf</a>\n"
            + "</li>\n"
            + "<li id=\"document-2\">\n"
            + "<a href=\"" + beside("e.pdf") + "\">reports/e.pdf</a>\n"
            + "</li>\n"
            + "</ol>\n"
            + Html.tail();

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
                "a citation [n] becomes a link to #document-n reading [n], whatever n is, so no raw marker survives"
                        + " into the page",
                () -> assertThat(ProseHtml.of("Both [2] and [1] agree; [12] too."))
                        .isEqualTo("<p>Both <a href=\"#document-2\">[2]</a> and <a href=\"#document-1\">[1]</a> agree;"
                                + " <a href=\"#document-12\">[12]</a> too.</p>"));
        claim(
                "brackets around anything but digits, a spaced number, or a number in braces are not citations,"
                        + " and are left exactly as the model wrote them",
                () -> assertThat(ProseHtml.of("See [x], [ 1], {1} and [1a]."))
                        .isEqualTo("<p>See [x], [ 1], {1} and [1a].</p>"));
    }

    @Test
    @Story("A written page")
    @DisplayName("A written page is its title, its writing with links, the subset sentence, and the numbered list leading to the copies")
    void writesAWrittenPageWhole(@TempDir Path base) throws IOException {
        Path page = aPageIn(base, "1-fire-suppression-retrofits");

        writeInto(
                page,
                THE_WRITTEN_CLUSTER,
                THE_WRITING,
                null,
                List.of(HIGHEST, LOWEST, MIDDLE),
                copiesOf(base, page, List.of(HIGHEST, LOWEST, MIDDLE)),
                noPictures(),
                DeliverableProgress.NONE);

        claim(
                "the page is the title escaped as its heading, the writing with each citation a link, the sentence"
                        + " saying it was written from 3 of the 4 documents, and the list numbered from what the"
                        + " call sent, each entry a link to its copy beside the page, its name percent-encoded, the"
                        + " gone document's entry its path and why no copy stands, linking nowhere",
                () -> assertThat(Files.readString(page, StandardCharsets.UTF_8)).isEqualTo(THE_WRITTEN_PAGE));
    }

    @Test
    @Story("A page nothing was written over")
    @DisplayName("A page nothing was written over is headed by its label, says so, and lists its documents by score")
    void writesAnUnwrittenPageWhole(@TempDir Path base) throws IOException {
        Path page = aPageIn(base, "2-sprinkler-maintenance");

        writeInto(
                page,
                THE_UNWRITTEN_CLUSTER,
                null,
                null,
                List.of(TIED_FIRST, TIED_SECOND),
                copiesOf(base, page, List.of(TIED_FIRST, TIED_SECOND)),
                noPictures(),
                DeliverableProgress.NONE);

        claim(
                "with no writing and no reason handed over, the page is the label as its heading, its line break"
                        + " kept since no element ends at one, the sentence saying nothing was written in italics,"
                        + " and the members by score",
                () -> assertThat(Files.readString(page, StandardCharsets.UTF_8)).isEqualTo(THE_UNWRITTEN_PAGE));
    }

    @Test
    @Story("A page nothing was written over")
    @DisplayName("A page nothing was written over carries the reason it was handed, in the place the writing would be")
    void writesTheReasonInPlaceOfTheWriting(@TempDir Path base) throws IOException {
        Path page = aPageIn(base, "2-sprinkler-maintenance");

        writeInto(
                page,
                THE_UNWRITTEN_CLUSTER,
                null,
                Unwritten.NOT_REACHED,
                List.of(TIED_FIRST, TIED_SECOND),
                copiesOf(base, page, List.of(TIED_FIRST, TIED_SECOND)),
                noPictures(),
                DeliverableProgress.NONE);

        claim(
                "the reason's sentence replaces the plain one and nothing else on the page changes (ADR-174)",
                () -> assertThat(Files.readString(page, StandardCharsets.UTF_8))
                        .isEqualTo(THE_UNWRITTEN_PAGE.replace(
                                Deliverable.NOTHING_WAS_WRITTEN_OVER_IT, UnwrittenSentences.of(Unwritten.NOT_REACHED, DeliverableLanguage.ENGLISH))));
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

        Path page = aPageIn(base, "1-fire-suppression-retrofits");

        writeInto(
                page,
                THE_WRITTEN_CLUSTER,
                THE_WRITING,
                null,
                List.of(HIGHEST, LOWEST, MIDDLE),
                copiesOf(base, page, List.of(HIGHEST, LOWEST, MIDDLE)),
                noPictures(),
                counting);

        claim(
                "three of the page's four entries carry a document, and those three are reported, so the count"
                        + " the step announced before it began (ADR-192 §5) is the count it reaches",
                () -> assertThat(entriesReported.get()).isEqualTo(3));
    }

    /**
     * {@code page} written by the stream form of {@link ClusterPage#write}, the copies standing in its folder, and the
     * bytes it tells at each entry's end not heard (ADR-286 §2): the bytes of the page are what this class claims.
     */
    private static void writeInto(
            Path page,
            RecordedCluster recorded,
            SynthesisDoc doc,
            Unwritten why,
            List<ListedSurvivor> members,
            Copies copies,
            EntryPictures pictures,
            DeliverableProgress progress)
            throws IOException {
        try (OutputStream out = Files.newOutputStream(page)) {
            ClusterPage.write(
                    out, recorded, doc, why, members, page.getParent(), copies, pictures, progress, bytes -> {},
                    ConfluenceEntries.NONE, false, DeliverableLanguage.ENGLISH);
        }
    }

    /** Where an entry links to the copy whose name is {@code encoded} once percent-encoded: beside the page. */
    private static String beside(String encoded) {
        return "./" + encoded;
    }

    /** The tree's root beneath {@code base}, as the writer names it. */
    private static Path theTree(Path base) {
        return base.resolve("wd").resolve("deliverable").resolve("run-1").toAbsolutePath();
    }

    /** A page's path, {@code index.html} in a cluster's folder of the tree, that folder made, as the writer makes it. */
    private static Path aPageIn(Path base, String clusterFolder) throws IOException {
        Path folder = theTree(base).resolve("1-fire-safety-2019").resolve(clusterFolder);
        Files.createDirectories(folder);
        return folder.resolve("index.html");
    }

    /**
     * The copies of {@code members}, made as the writer makes them before a page is written, into the folder of {@code
     * page}, over an archive beside the working directory that holds every member's file but {@link #MIDDLE}'s.
     */
    private static Copies copiesOf(Path base, Path page, List<ListedSurvivor> members) throws IOException {
        Path archive = base.resolve("archive").toAbsolutePath();
        for (ListedSurvivor member : IN_THE_ARCHIVE) {
            Path file = archive.resolve(member.path().value());
            Files.createDirectories(file.getParent());
            Files.write(file, bytesOf(member.path().value()));
        }
        Copies copies = new Copies(
                theTree(base), archive.toString(), ReplacingMove.ATOMIC, () -> Long.MAX_VALUE, DeliverableProgress.NONE);
        copies.copyAll(page.getParent(), members);
        return copies;
    }

    /** Pictures for a tree whose documents carry none. */
    private static EntryPictures noPictures() {
        return ListedArrangement.picturesAmong(List.of(), SurvivorPictures.none(), DeliverableProgress.NONE);
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
