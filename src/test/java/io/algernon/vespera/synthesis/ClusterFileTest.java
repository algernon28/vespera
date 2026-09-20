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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The page a cluster is read on (ADR-103, ADR-104, ADR-109, ADR-122, #187): the heading it opens
 * with, the writing with its citations resolved into links, the disclosure where the writing rests on
 * part of the cluster, and the complete membership below it.
 *
 * <p>Written against the writer rather than through an invocation for the states a fixture corpus
 * cannot reach: a cluster written from a strict subset of its documents, a cluster whose recorded size
 * is larger than the list handed to the page, and a document whose own name carries a character the
 * JDK's path parser refuses. A local Markdown page is read at the moment it is written, so its own
 * bytes are the evidence — nothing here renders it.
 *
 * <p><b>That a real invocation reaches this page at all is claimed where the invocation is</b>:
 * {@code DeliverableInvocationTest} drives the whole run and reads the page it produced, because every
 * claim here is made over values handed in by hand, and a writer that is never called writes a page
 * nobody ever sees.
 *
 * <p><b>The archive is referenced and never touched.</b> ADR-104 says a membership entry links to the
 * original as an absolute {@code file:} target and that nothing is stat-ed to build it, and every
 * fixture below names an archive this machine does not have.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122). The page is
 * one of that record's outside-facing surfaces, so every word a reader meets in it uses the plain
 * word, while every name here that is ours does not.
 */
@Epic("Synthesis")
@Feature("The page a group is read on")
@Issue("187")
@Link(name = "ADR-103", url = Adr.THE_DELIVERABLE_IS_A_MARKDOWN_TREE_PER_RUN, type = "adr")
@Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
@Link(name = "ADR-109", url = Adr.A_CITATION_IS_AN_ORDINAL_MINTED_FOR_ONE_CALL, type = "adr")
@Link(name = "ADR-122", url = Adr.THE_VOCABULARY_BINDS_OUR_NAMES_NOT_RENDERED_PROSE, type = "adr")
class ClusterFileTest {

    /** A run id of the shape the ledger mints: sixty-four hexadecimal characters (ADR-048). */
    private static final String RUN_ID = "a".repeat(48) + "0123456789abcdef";

    /** The walk the run read, which is what says when the links in the tree were true (ADR-104). */
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

    /** What the title would slug to, which must never name the file: a faulted cluster has no title. */
    private static final String THE_TITLE_SLUGGED = "retrofitting-suppression-2018-to-2021";

    /** A corpus root that is not on this machine, so anything reaching the archive fails or shows. */
    private static final String THE_ARCHIVE_ROOT = Path.of("D:", "archive").toString();

    /** The one partition directory these fixtures write, named from the seed's stem, slugged. */
    private static final String THE_PARTITION_DIRECTORY = "1-industrial-safety-standards";

    /** The one cluster's page beneath it, named from the derived label and never the generated heading. */
    private static final String THE_CLUSTER_PAGE = "1-" + THE_LABEL_SLUGGED + ".md";

    /** The first cluster ordinal these fixtures hand out, which is the identity 6a minted. */
    private static final int FIRST_ORDINAL = 0;

    /** The first place in an order, which both levels count from (ADR-112). */
    private static final int FIRST_PLACE = 1;

    /** How many documents the cluster holds in the fixture that writes over all of them. */
    private static final int TWO_DOCUMENTS = 2;

    /** And how many it holds in the fixture that sends only part. */
    private static final int THREE_DOCUMENTS = 3;

    /** How many documents the call sent in the fixture that overflows the budget. */
    private static final int ONE_DOCUMENT_SENT = 1;

    /** The first entry of the membership list, which is the document a call mints as its first ordinal. */
    private static final int THE_FIRST_ENTRY = 1;

    /** The relevance score of the document placed furthest from the seed, listed last. */
    private static final double A_LOW_SCORE = 0.1;

    /** The score of the one placed between them. */
    private static final double A_MIDDLE_SCORE = 0.5;

    /** And the score of the closest one, which the page has to list first. */
    private static final double A_HIGH_SCORE = 0.9;

    /**
     * A bracketed ordinal that is not immediately followed by {@code (}, which is a citation the page
     * failed to rewrite (ADR-109): a link's own {@code [n]} is followed by its destination and so is
     * not a survivor.
     */
    private static final Pattern A_RAW_CITATION = Pattern.compile("\\[\\d+\\](?!\\()");

    @Test
    @Story("The heading names the group the way the writing did, and the label where it did not")
    @DisplayName("A group's page opens with the heading the model wrote, and its file is named from the other name")
    void leadsWithTheGeneratedTitleAndNamesTheFileFromTheLabel(@TempDir Path workingDirectory) throws IOException {
        Path tree = write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The nearest one [1] is the retrofit.", ONE_DOCUMENT_SENT),
                List.of(aMember(10, "reports/2019/retrofit.pdf", A_HIGH_SCORE, FIRST_ORDINAL)));

        String page = pageOf(tree);

        claim(
                "the page opens with the heading the model wrote rather than the name stage 6a derived:"
                        + " the heading is the one piece of writing a reader is meant to read first, and"
                        + " burying it under a mechanical name would present the derivation as the title",
                () -> assertThat(page).startsWith("# " + THE_TITLE));
        claim(
                "though the file itself is named from the derived label and not from that heading -- \""
                        + THE_CLUSTER_PAGE + "\" -- because a name depending on generation succeeding would"
                        + " not exist for a group nothing was written over, and every group keeps its slot",
                () -> assertThat(tree.resolve(THE_PARTITION_DIRECTORY).resolve(THE_CLUSTER_PAGE))
                        .isRegularFile());
        claim(
                "and it is the only file the partition holds, so no path anywhere in it followed the"
                        + " model's title -- \"" + THE_TITLE_SLUGGED + "\" under any ordinal included --"
                        + " because a path that did would move the moment the model was asked again",
                () -> assertThat(tree.resolve(THE_PARTITION_DIRECTORY))
                        .isDirectoryContaining(file -> file.getFileName().toString().equals(THE_CLUSTER_PAGE))
                        .isDirectoryNotContaining(
                                file -> file.getFileName().toString().contains(THE_TITLE_SLUGGED)));
    }

    @Test
    @Story("The heading names the group the way the writing did, and the label where it did not")
    @DisplayName("A group nothing was written over is headed by its derived label and says so")
    @Link(name = "ADR-111", url = Adr.A_CLUSTER_FAULT_IS_A_ROW_IN_SYNTHESIS, type = "adr")
    void fallsBackToTheDerivedLabelWhereGenerationFailed(@TempDir Path workingDirectory) throws IOException {
        Path tree = write(
                workingDirectory,
                null,
                List.of(aMember(10, "reports/2019/retrofit.pdf", A_HIGH_SCORE, FIRST_ORDINAL)));

        String page = pageOf(tree);

        claim(
                "a group nothing was written over still has a page, headed by the label stage 6a derived"
                        + " for it: the label is the name that always exists, so the hole in the archive"
                        + " can be matched against the index without anything having been generated",
                () -> assertThat(page).startsWith("# " + THE_LABEL));
        claim(
                "and the page says plainly that nothing was written over it, rather than leaving a reader"
                        + " to infer it from a heading and a list",
                () -> assertThat(page).contains(Deliverable.NOTHING_WAS_WRITTEN_OVER_IT));
    }

    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("Every citation becomes a link to an entry in the list below, and no raw marker survives")
    @Link(name = "ADR-109", url = Adr.A_CITATION_IS_AN_ORDINAL_MINTED_FOR_ONE_CALL, type = "adr")
    void rewritesEveryCitationIntoALink(@TempDir Path workingDirectory) throws IOException {
        String page = pageOf(write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "Both of them [1] describe it, and the third [2] too.", TWO_DOCUMENTS),
                List.of(
                        aMember(10, "reports/2019/retrofit.pdf", A_HIGH_SCORE, FIRST_ORDINAL),
                        aMember(11, "reports/2021/follow-up.pdf", A_MIDDLE_SCORE, FIRST_ORDINAL))));

        claim(
                "each [n] in the writing is rewritten into a link to membership entry n, so following a"
                        + " claim is one click to the entry rather than a number a reader has to hunt for",
                () -> assertThat(page)
                        .contains("[1](#document-1)")
                        .contains("[2](#document-2)"));
        claim(
                "and no raw marker survives anywhere in the page: a bare [n] left behind would be the"
                        + " model's own notation sitting in a file a reader follows links from, and one at"
                        + " the start of a line would parse as a Markdown reference-link definition",
                () -> assertThat(A_RAW_CITATION.matcher(page).find()).isFalse());
    }

    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("Every entry in the list below is a link to the original in the archive")
    @Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
    void linksEveryMembershipEntryToTheOriginal(@TempDir Path workingDirectory) throws IOException {
        String page = pageOf(write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The nearest one [1] is the retrofit.", ONE_DOCUMENT_SENT),
                List.of(aMember(10, "reports/2019/retrofit.pdf", A_HIGH_SCORE, FIRST_ORDINAL))));

        claim(
                "the entry is a link to the document where it already sits, composed from the recorded"
                        + " root and the document's own root-relative path rather than copied: the second"
                        + " click of the chain ends at the file the operator owns",
                () -> assertThat(page)
                        .contains("[reports/2019/retrofit.pdf](file:///D:/archive/reports/2019/retrofit.pdf)"));
    }

    @Test
    @Story("The list below is the whole group, not the part the writing happened to mention")
    @DisplayName("Every document of the group is listed in closeness order, the ones the call never sent included")
    @Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
    void listsEveryMemberInScoreOrderIncludingThoseNeverSent(@TempDir Path workingDirectory) throws IOException {
        String page = pageOf(write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The closest one [1] is the retrofit.", ONE_DOCUMENT_SENT),
                List.of(
                        aMember(10, "reports/middle.pdf", A_MIDDLE_SCORE, FIRST_ORDINAL),
                        aMember(11, "reports/high.pdf", A_HIGH_SCORE, FIRST_ORDINAL),
                        aMember(12, "reports/low.pdf", A_LOW_SCORE, FIRST_ORDINAL))));

        claim(
                "all " + THREE_DOCUMENTS + " documents of the group are listed, though the call sent only "
                        + ONE_DOCUMENT_SENT + ": the page states the arrangement rather than what the model"
                        + " happened to mention, so a document the writing never cited is still one a reader"
                        + " can find and open",
                () -> assertThat(page)
                        .contains("reports/high.pdf")
                        .contains("reports/middle.pdf")
                        .contains("reports/low.pdf"));
        claim(
                "listed closest to the seed first, which is the order the call drew its documents in: entry"
                        + " " + THE_FIRST_ENTRY + " is the document the model was given as [1], so the"
                        + " numbering in the writing and the numbering on the page are one numbering and a"
                        + " citation resolves by construction rather than by looking a document up",
                () -> assertThat(page)
                        .contains("1. <a id=\"document-1\"></a>[reports/high.pdf]")
                        .contains("2. <a id=\"document-2\"></a>[reports/middle.pdf]")
                        .contains("3. <a id=\"document-3\"></a>[reports/low.pdf]"));
        claim(
                "and no entry's anchor sits on a line of its own: an anchor tag alone on a line is a"
                        + " paragraph, and a numbered entry can interrupt a paragraph only at 1 -- so entry "
                        + THE_FIRST_ENTRY + " would open a list and every entry after it would be swallowed"
                        + " into the text above it, leaving a page whose bytes hold the whole group and"
                        + " whose rendering shows one document and a wall of prose",
                () -> assertThat(page).doesNotContain("</a>\n"));
        claim(
                "and the page says how many of them the writing rests on, naming both numbers, so a reader"
                        + " knows the prose was written from the " + ONE_DOCUMENT_SENT + " closest of "
                        + THREE_DOCUMENTS + " without going to look",
                () -> assertThat(page)
                        .contains("Written from the " + ONE_DOCUMENT_SENT + " highest-scoring of "
                                + THREE_DOCUMENTS + " documents."));
    }

    @Test
    @Story("The list below is the whole group, not the part the writing happened to mention")
    @DisplayName("A group written from every one of its documents makes no claim about a subset")
    void disclosesNothingWhereTheWritingRestsOnTheWholeCluster(@TempDir Path workingDirectory) throws IOException {
        String page = pageOf(write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "Both of them [1] describe it.", TWO_DOCUMENTS),
                List.of(
                        aMember(10, "reports/2019/retrofit.pdf", A_HIGH_SCORE, FIRST_ORDINAL),
                        aMember(11, "reports/2021/follow-up.pdf", A_MIDDLE_SCORE, FIRST_ORDINAL))));

        claim(
                "a page over the whole group carries no disclosure sentence: a sentence saying every"
                        + " document was read is noise at best, and the claim it exists to make -- that"
                        + " some were left out -- is false here",
                () -> assertThat(page).doesNotContain("Written from the"));
    }

    @Test
    @Story("The list below is the whole group, not the part the writing happened to mention")
    @DisplayName("The sentence about how much was read counts the group as it was arranged")
    @Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
    void countsTheDisclosureFromTheArrangementRatherThanTheListItWasHanded(@TempDir Path workingDirectory)
            throws IOException {
        String page = pageOf(write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The closest one [1] is the retrofit.", ONE_DOCUMENT_SENT),
                List.of(
                        aMember(10, "reports/high.pdf", A_HIGH_SCORE, FIRST_ORDINAL),
                        aMember(11, "reports/middle.pdf", A_MIDDLE_SCORE, FIRST_ORDINAL)),
                THREE_DOCUMENTS));

        claim(
                "the sentence names " + THREE_DOCUMENTS + ", the size the group was arranged at, and not"
                        + " the " + TWO_DOCUMENTS + " entries this page was handed: the size is stated once,"
                        + " where the group was arranged, and a page that counted its own list instead would"
                        + " be a second statement of it -- free to disagree with the index in the one"
                        + " sentence a reader is invited to trust the number in",
                () -> assertThat(page)
                        .contains("Written from the " + ONE_DOCUMENT_SENT + " highest-scoring of "
                                + THREE_DOCUMENTS + " documents."));
    }

    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("A name carrying a space or a quote becomes a link a reader can still follow")
    void escapesCharactersThatWouldBreakTheLink(@TempDir Path workingDirectory) throws IOException {
        String page = pageOf(write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The nearest one [1] is the retrofit.", ONE_DOCUMENT_SENT),
                List.of(
                        aMember(10, "reports/follow-up report.pdf", A_HIGH_SCORE, FIRST_ORDINAL),
                        aMember(11, "reports/2019/retrofit, phase \"two\".pdf", A_MIDDLE_SCORE, FIRST_ORDINAL))));

        claim(
                "a space in a document's own name is escaped in the link rather than left to open it at"
                        + " the first whitespace, so a name that is perfectly legal on this filesystem still"
                        + " resolves when a reader clicks it",
                () -> assertThat(page).contains("reports/follow-up%20report.pdf"));
        claim(
                "and a quote is escaped too, because NTFS allows it and the JDK's own path parser refuses"
                        + " it -- a link composed by handing such a name to a Path would throw before any"
                        + " URI was built, leaving the document unreachable",
                () -> assertThat(page).contains("phase%20%22two%22.pdf"));
    }

    /** Writes one tree holding one cluster arranged at the size of the list it holds, and returns its root. */
    private static Path write(Path workingDirectory, SynthesisDoc doc, List<ListedSurvivor> members) {
        return write(workingDirectory, doc, members, members.size());
    }

    /**
     * The same, with the size the cluster was arranged at stated separately from the list handed in, so
     * a claim can tell apart the number 6a recorded and the number this page could count for itself.
     */
    private static Path write(
            Path workingDirectory, SynthesisDoc doc, List<ListedSurvivor> members, int documentCount) {
        List<RecordedCluster> arrangement =
                List.of(new RecordedCluster(
                        new ArrangedCluster(THE_SEED, FIRST_ORDINAL, documentCount, FIRST_PLACE, FIRST_PLACE),
                        new ClusterLabel(THE_LABEL)));
        List<RecordedSynthesisDoc> written = doc == null
                ? List.of()
                : List.of(new RecordedSynthesisDoc(THE_SEED, FIRST_ORDINAL, doc));
        return Deliverable.writeTo(workingDirectory, provenance(), arrangement, written, members);
    }

    /** The bytes of the one cluster's page, read at the moment a claim asks for them. */
    private static String pageOf(Path tree) throws IOException {
        return Files.readString(tree.resolve(THE_PARTITION_DIRECTORY).resolve(THE_CLUSTER_PAGE));
    }

    /** What produced the tree, with the archive root stated so the links compose against it. */
    private static DeliverableProvenance provenance() {
        return new DeliverableProvenance(RUN_ID, WALK, THE_ARCHIVE_ROOT, List.of());
    }

    /** One survivor of the one cluster, at {@code path} with {@code score}. */
    private static ListedSurvivor aMember(long occurrence, String path, double score, int ordinal) {
        return new ListedSurvivor(
                new OccurrenceId(occurrence),
                new OccurrencePath(path),
                "hash-" + occurrence,
                THE_SEED,
                SEED_PATH,
                ordinal,
                score);
    }
}