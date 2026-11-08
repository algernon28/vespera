package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.ListedTree.sha256Of;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.synthesis.ArrangedCluster;
import io.algernon.vespera.synthesis.ClusterLabel;
import io.algernon.vespera.synthesis.ListedDoc;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The page a cluster is read on (ADR-103, ADR-104, ADR-109, ADR-122, ADR-274 §1.4, #187): the heading it opens
 * with, the writing with its citations resolved into links, the disclosure where the writing rests on part of the
 * cluster, and the complete membership below it, each entry leading to its copy inside the tree.
 *
 * <p>Written against the writer rather than through an invocation for the states a fixture corpus
 * cannot reach: a cluster written from a strict subset of its documents, a cluster whose recorded size
 * is larger than the list handed to the page, and an archive root the JDK's path parser refuses. A local
 * HTML page is read at the moment it is written, so its own bytes are the evidence — nothing here renders it.
 *
 * <p><b>That a real invocation reaches this page at all is claimed where the invocation is</b>:
 * {@code DeliverableInvocationTest} drives the whole run and reads the page it produced, because every
 * claim here is made over values handed in by hand, and a writer that is never called writes a page
 * nobody ever sees.
 *
 * <p><b>The archive is read and never linked to</b> (ADR-274 §2.6). Each fixture places an archive beside the
 * working directory whose every file holds its own root-relative path's bytes, which is what its recorded key is
 * taken from, so each copy is made and stands. The page is {@code index.html} in its cluster's folder, and an entry
 * links to its copy beside it, by the copy's name percent-encoded (ADR-280 §5), and never into the archive.
 *
 * <p><b>Some claims here follow a link rather than reading it.</b> ADR-103's test is that a link
 * resolves: the destination is followed from the directory the page sits in, and what it lands on is
 * compared with the copy's own place in the tree and its bytes with the original's.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122). The page is
 * one of that record's outside-facing surfaces, so every word a reader meets in it uses the plain
 * word, while every name here that is ours does not.
 */
@Epic("The deliverable")
@Feature("The page a group is read on")
@Issue("187")
@Link(name = "ADR-103", url = Adr.THE_DELIVERABLE_IS_A_MARKDOWN_TREE_PER_RUN, type = "adr")
@Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
@Link(name = "ADR-109", url = Adr.A_CITATION_IS_AN_ORDINAL_MINTED_FOR_ONE_CALL, type = "adr")
@Link(name = "ADR-122", url = Adr.THE_VOCABULARY_BINDS_OUR_NAMES_NOT_RENDERED_PROSE, type = "adr")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
@Link(name = "ADR-280", url = Adr.A_CLUSTERS_FOLDER_HOLDS_ITS_PAGE_AND_ITS_SURVIVORS_COPIES, type = "adr")
class ClusterPageLinksAndWordingTest {

    /** A run id of the shape the ledger mints: sixty-four hexadecimal characters (ADR-048). */
    private static final String RUN_ID = "a".repeat(48) + "0123456789abcdef";

    /** The walk the run read, which is what says which archive the copies were taken from (ADR-104). */
    private static final long WALK = 7L;

    /** The seed whose partition every cluster below sits in. */
    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    /** The seed document's own filename, which is what the partition directory is named from. */
    private static final String SEED_PATH = "seeds/Industrial Safety Standards.docx";

    /** What stage 6a called the one cluster most of these tests arrange. */
    private static final String THE_LABEL = "Fire Suppression Retrofits";

    /** That label slugged, which is what the cluster's own filename has to come out as. */
    private static final String THE_LABEL_SLUGGED = "fire-suppression-retrofits";

    /** What stage 6b called it, which is a different name for the same cluster (ADR-106). */
    private static final String THE_TITLE = "Retrofitting Suppression, 2018 to 2021";

    /** The document most fixtures below place in the archive, by the path the ledger holds for it. */
    private static final String THE_DOCUMENT = "reports/2019/retrofit.pdf";

    /** Where the copy of that document stands in the tree: under its own name, in its cluster's folder. */
    private static final String THE_DOCUMENTS_COPY = "1-industrial-safety-standards/1-fire-suppression-retrofits/retrofit.pdf";

    /** The one partition directory these fixtures write, named from the seed's stem, slugged. */
    private static final String THE_PARTITION_DIRECTORY = "1-industrial-safety-standards";

    /** The one cluster's folder beneath it, named from the derived label and never the generated heading. */
    private static final String THE_CLUSTER_FOLDER = "1-" + THE_LABEL_SLUGGED;

    /** The cluster's page, in that folder. */
    private static final String THE_CLUSTER_PAGE = THE_CLUSTER_FOLDER + "/index.html";

    /** The first cluster ordinal these fixtures hand out, which is the identity 6a minted. */
    private static final int FIRST_ORDINAL = 0;

    /** The first place in an order, which both levels count from (ADR-112). */
    private static final int FIRST_PLACE = 1;

    /** How many documents the cluster holds in the fixture that writes over all of them. */
    private static final int TWO_DOCUMENTS = 2;

    /** And how many it holds in the fixture that sends only part. */
    private static final int THREE_DOCUMENTS = 3;

    /** The first entry of the membership list, which is the document a call mints as its first ordinal. */
    private static final int THE_FIRST_ENTRY = 1;

    /** The second, which is the document the call was given as [2] and not the second-highest scorer. */
    private static final int THE_SECOND_ENTRY = 2;

    /** The third, where a document the call never carried lands once the ones it did are numbered. */
    private static final int THE_THIRD_ENTRY = 3;

    /** The relevance score of the document placed furthest from the seed, listed last. */
    private static final double A_LOW_SCORE = 0.1;

    /** The score of the one placed between them. */
    private static final double A_MIDDLE_SCORE = 0.5;

    /** And the score of the closest one, which the page has to list first. */
    private static final double A_HIGH_SCORE = 0.9;

    /** The destination of the first membership entry: the link that opens its line, if it has one. */
    private static final Pattern THE_FIRST_ENTRYS_DESTINATION =
            Pattern.compile("<li id=\"document-1\">\n<a href=\"([^\"]*)\">");

    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("Every entry in the list below leads to the document's copy in the tree, by a route the page itself supplies")
    @Issue("253")
    @Link(name = "ADR-135", url = Adr.A_MEMBERSHIP_ENTRY_LINKS_RELATIVELY_OR_NOT_AT_ALL, type = "adr")
    void linksEveryMembershipEntryToItsCopyByAPathRelativeToThePage(@TempDir Path workingDirectory)
            throws IOException {
        Path tree = write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The nearest one [1] is the retrofit.", sent(10)),
                List.of(aMember(10, THE_DOCUMENT, A_HIGH_SCORE, FIRST_ORDINAL)));
        Path page = thePageOf(tree);

        claim(
                "the entry's destination names no scheme and no host, and is not absolute: a link that leaves the"
                        + " tree breaks the moment the folder is carried elsewhere",
                () -> {
                    URI destination = theDestinationOn(page);
                    assertThat(destination.isAbsolute()).isFalse();
                    assertThat(destination.getHost()).isNull();
                    assertThat(destination.getPath()).doesNotStartWith("/");
                });
        claim(
                "following that destination from the directory the page sits in arrives exactly at the"
                        + " document's copy inside the tree, with no database, no archive and no network between"
                        + " the reader and it",
                () -> assertThat(whereTheFirstEntryLeadsFrom(page)).isEqualTo(tree.resolve(THE_DOCUMENTS_COPY)));
        claim(
                "and what it lands on holds exactly the bytes of the document in the archive",
                () -> assertThat(whereTheFirstEntryLeadsFrom(page)).hasBinaryContent(bytesOf(THE_DOCUMENT)));
        claim(
                "and the link's text is the document's place beneath the archive root the index states, so a"
                        + " reader still learns where it came from",
                () -> assertThat(Files.readString(page))
                        .contains("<a href=\"./retrofit.pdf\">" + THE_DOCUMENT + "</a>"));
    }

    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("Where a document was passed over, a citation still reaches the document it was written from")
    @Issue("236")
    @Link(name = "ADR-133", url = Adr.THE_EXEMPLARS_ONE_CALL_SENT_ARE_RECORDED, type = "adr")
    void numbersTheListFromWhatTheCallCarriedWhereAMemberWasPassedOver(@TempDir Path workingDirectory)
            throws IOException {
        ListedSurvivor high = aMember(10, "reports/high.pdf", A_HIGH_SCORE, FIRST_ORDINAL);
        ListedSurvivor middle = aMember(11, "reports/middle.pdf", A_MIDDLE_SCORE, FIRST_ORDINAL);
        ListedSurvivor low = aMember(12, "reports/low.pdf", A_LOW_SCORE, FIRST_ORDINAL);
        String page = pageOf(write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The nearest [1] and the furthest [2] agree.", sent(10, 12)),
                List.of(high, middle, low)));

        claim(
                "entry " + THE_SECOND_ENTRY + " is the document the call was given as [2] -- the"
                        + " furthest one -- and not the middle document, which scores higher than it and"
                        + " was never sent: the call dropped that one, so numbering the group's own"
                        + " documents by score would have pointed [2] at a document the writing was not"
                        + " made from, and the link would have resolved, opened and been wrong",
                () -> assertThat(page).contains(entry(THE_FIRST_ENTRY, high) + entry(THE_SECOND_ENTRY, low)));
        claim(
                "and the document that was passed over is still listed, last of the " + THREE_DOCUMENTS
                        + ": the page states the group as it was arranged, and a document nothing could"
                        + " be written from is still one a reader can find and open",
                () -> assertThat(page).contains(entry(THE_THIRD_ENTRY, middle) + "</ol>"));
        claim(
                "and the sentence about how much was read names the first " + TWO_DOCUMENTS + " of "
                        + THREE_DOCUMENTS + ", which is true of this page and checkable on it: the two"
                        + " that were sent are not the " + TWO_DOCUMENTS + " highest-scoring, and saying"
                        + " so would have been the same wrong claim as the link, written in words",
                () -> assertThat(page)
                        .contains("Written from the first " + TWO_DOCUMENTS + " of the " + THREE_DOCUMENTS
                                + " documents in this group."));
    }

    /**
     * Writes one tree holding one cluster arranged at the size of the list it holds, over an archive beside the
     * working directory that holds every member's file, and returns its root.
     */
    private static Path write(Path workingDirectory, SynthesisDoc doc, List<ListedSurvivor> members)
            throws IOException {
        return write(workingDirectory, doc, members, members);
    }

    /** The same, the archive holding only the files of {@code inTheArchive}. */
    private static Path write(
            Path workingDirectory, SynthesisDoc doc, List<ListedSurvivor> members, List<ListedSurvivor> inTheArchive)
            throws IOException {
        Path archive = anArchiveBeside(workingDirectory);
        for (ListedSurvivor member : inTheArchive) {
            Path file = archive.resolve(member.path().value());
            Files.createDirectories(file.getParent());
            Files.write(file, bytesOf(member.path().value()));
        }
        return write(workingDirectory, archive.toString(), doc, members, members.size());
    }

    /**
     * And over an archive root stated as the string the ledger recorded, which is what {@code
     * DeliverableProvenance} actually carries, with the size the cluster was arranged at stated separately from
     * the list handed in, so a claim can tell apart the number 6a recorded and the number this page could count.
     */
    private static Path write(
            Path workingDirectory,
            String corpusRoot,
            SynthesisDoc doc,
            List<ListedSurvivor> members,
            int documentCount) {
        List<RecordedCluster> arrangement =
                List.of(new RecordedCluster(
                        new ArrangedCluster(THE_SEED, FIRST_ORDINAL, documentCount, FIRST_PLACE, FIRST_PLACE),
                        new ClusterLabel(THE_LABEL)));
        List<ListedDoc> written = doc == null
                ? List.of()
                : List.of(new ListedDoc(THE_SEED, FIRST_ORDINAL, doc));
        return ListedArrangement.writeTo(workingDirectory, provenance(corpusRoot), arrangement, written, members);
    }

    /**
     * One membership entry whose copy stands, as the page writes it: its id, a link to its copy beside the page, its
     * path. For a member whose file name needs no percent-encoding, so its copy is linked by that name as it is.
     */
    private static String entry(int number, ListedSurvivor member) {
        String path = member.path().value();
        return entry(number, member, "./" + path.substring(path.lastIndexOf('/') + 1));
    }

    /** The same, linking to {@code destination}. */
    private static String entry(int number, ListedSurvivor member, String destination) {
        return "<li id=\"document-" + number + "\">\n<a href=\"" + destination + "\">"
                + Html.escape(member.path().value()) + "</a>\n</li>\n";
    }

    /** The bytes of the one cluster's page, read at the moment a claim asks for them. */
    private static String pageOf(Path tree) throws IOException {
        return Files.readString(thePageOf(tree));
    }

    /** Where that page sits, which a claim about a relative destination has to follow it from. */
    private static Path thePageOf(Path tree) {
        return tree.resolve(THE_PARTITION_DIRECTORY).resolve(THE_CLUSTER_PAGE);
    }

    /** An archive beside the working directory, on its volume. */
    private static Path anArchiveBeside(Path workingDirectory) {
        return workingDirectory.resolveSibling(workingDirectory.getFileName() + "-archive");
    }

    /** The destination of the page's first membership entry, read as a {@link URI}. */
    private static URI theDestinationOn(Path page) throws IOException {
        return URI.create(theDestinationTextOn(page));
    }

    /** The same destination as the characters the page carries, read without a parser between them and the claim. */
    private static String theDestinationTextOn(Path page) throws IOException {
        Matcher entry = THE_FIRST_ENTRYS_DESTINATION.matcher(Files.readString(page));
        if (!entry.find()) {
            throw new IllegalStateException("no membership entry on " + page + " carries a link at all");
        }
        return entry.group(1);
    }

    /** Where that destination leads, followed from the directory the page sits in. */
    private static Path whereTheFirstEntryLeadsFrom(Path page) throws IOException {
        return page.getParent().resolve(theDestinationOn(page).getPath()).normalize();
    }

    /** What produced the tree, with the archive root stated so the copies are read from it. */
    private static DeliverableProvenance provenance(String corpusRoot) {
        return new DeliverableProvenance(RUN_ID, WALK, corpusRoot, List.of());
    }

    /**
     * The documents one call carried, named by their occurrences in the order its ordinals were
     * minted (ADR-133) — so a fixture says which document {@code [n]} was given to the model as,
     * rather than only how many documents went.
     */
    private static List<OccurrenceId> sent(long... occurrences) {
        List<OccurrenceId> carried = new ArrayList<>();
        for (long occurrence : occurrences) {
            carried.add(new OccurrenceId(occurrence));
        }
        return List.copyOf(carried);
    }

    /** What a member's file in the archive holds: its own path, so its recorded key follows from the path alone. */
    private static byte[] bytesOf(String path) {
        return path.getBytes(StandardCharsets.UTF_8);
    }

    /** One survivor of the one cluster, at {@code path} with {@code score}, its key that of its file's bytes. */
    private static ListedSurvivor aMember(long occurrence, String path, double score, int ordinal) {
        return new ListedSurvivor(
                new OccurrenceId(occurrence),
                new OccurrencePath(path),
                sha256Of(bytesOf(path)),
                THE_SEED,
                SEED_PATH,
                ordinal,
                score);
    }
}
