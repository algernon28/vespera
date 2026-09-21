package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
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
 * <p><b>The archive is referenced and never touched.</b> ADR-104 says nothing is stat-ed to build a
 * membership entry, and every fixture below names an archive this machine does not have. What the
 * entry links by is ADR-135's: a path relative to the page it is written on, or no link at all where
 * there is no relative path to compose — so the fixtures place that archive beside the working
 * directory, on its volume, and the one fixture that wants the second case puts it on another volume
 * and is guarded to Windows, which is the only platform that has two.
 *
 * <p><b>One claim here follows a link rather than reading it.</b> ADR-103's test is that a link
 * resolves, and ADR-135 reads that through the renderer: the destination is decoded as a renderer
 * decodes it and followed from the directory the page sits in, and what it lands on is compared to
 * the original's own place beneath the archive root. Nothing is stat-ed to make that claim either --
 * the archive is not there, and the claim is about where the link points rather than what is at the
 * other end, which {@code DeliverableInvocationTest} claims over an archive that really exists.
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

    /** Whether this is the platform that has more than one volume, and so the only one that can have two. */
    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    /** The document most fixtures below place in the archive, by the path the ledger holds for it. */
    private static final String THE_DOCUMENT = "reports/2019/retrofit.pdf";

    /**
     * An archive root recorded as a relative path, which every platform has and which no destination
     * can be composed against: it names a different directory for every reader, depending on where they
     * happen to be standing.
     */
    private static final String A_RELATIVE_ARCHIVE_ROOT = "archive";

    /**
     * An archive root no path parser accepts, on either platform: a NUL is refused by the Windows
     * parser as an illegal character and by the POSIX one as a name it cannot hold.
     *
     * <p>Recorded roots are canonicalised on the machine that walked, and this one could not have been
     * -- which is the point. A tree is read on machines other than the one that wrote it, and a root
     * written for another platform, or a row edited by hand, arrives at this writer as a string it can
     * make no path of.
     */
    private static final String AN_ARCHIVE_ROOT_NO_PARSER_ACCEPTS = "D:/cor\u0000pus";

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

    /**
     * How many renderer settings the entry was measured in: four renderers, two of which have a second
     * documented mode that changes the answer.
     */
    private static final int SEVEN_CONFIGURATIONS = 7;

    /** And how many of them make a link of a destination carrying a scheme, which is where this began. */
    private static final int WHERE_A_SCHEME_SURVIVES = 2;

    /**
     * A bracketed ordinal that is not immediately followed by {@code (}, which is a citation the page
     * failed to rewrite (ADR-109): a link's own {@code [n]} is followed by its destination and so is
     * not a survivor.
     */
    private static final Pattern A_RAW_CITATION = Pattern.compile("\\[\\d+\\](?!\\()");

    /**
     * The destination of the first membership entry, captured the way a renderer reads one: everything
     * between the parenthesis that opens the destination and the one that closes it.
     *
     * <p>The link text is skipped over rather than matched loosely, because it may carry an escaped
     * bracket of its own -- {@code \\[} and {@code \\]} are what {@code escapeLinkText} inserts, and a
     * pattern stopping at the first {@code ]} would cut a name in half and read the rest as a
     * destination.
     */
    private static final Pattern THE_FIRST_ENTRYS_DESTINATION =
            Pattern.compile("1\\. <a id=\"document-1\"></a>\\[(?:\\\\.|[^\\]])*\\]\\(([^)]*)\\)");

    @Test
    @Story("The heading names the group the way the writing did, and the label where it did not")
    @DisplayName("A group's page opens with the heading the model wrote, and its file is named from the other name")
    void leadsWithTheGeneratedTitleAndNamesTheFileFromTheLabel(@TempDir Path workingDirectory) throws IOException {
        Path tree = write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The nearest one [1] is the retrofit.", sent(10)),
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
                new SynthesisDoc(THE_TITLE, "Both of them [1] describe it, and the third [2] too.", sent(10, 11)),
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
    @DisplayName("Every entry in the list below leads to the original, by a route the page itself supplies")
    @Issue("253")
    @Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
    @Link(name = "ADR-135", url = Adr.A_MEMBERSHIP_ENTRY_LINKS_RELATIVELY_OR_NOT_AT_ALL, type = "adr")
    void linksEveryMembershipEntryToTheOriginalByAPathRelativeToThePage(@TempDir Path workingDirectory)
            throws IOException {
        Path archive = anArchiveBeside(workingDirectory);
        Path page = thePageOf(write(
                workingDirectory,
                archive,
                new SynthesisDoc(THE_TITLE, "The nearest one [1] is the retrofit.", sent(10)),
                List.of(aMember(10, THE_DOCUMENT, A_HIGH_SCORE, FIRST_ORDINAL))));

        claim(
                "the entry's destination names no scheme at all: of the " + SEVEN_CONFIGURATIONS
                        + " renderer settings this was measured in, a destination beginning \"file:\" is a"
                        + " link in " + WHERE_A_SCHEME_SURVIVES + " -- the rest either strip it or print the"
                        + " whole entry at the reader as the source it was written in -- while a destination"
                        + " that is a plain relative path is a link in all " + SEVEN_CONFIGURATIONS,
                () -> assertThat(theDestinationOn(page).isAbsolute()).isFalse());
        claim(
                "and following that destination from the directory the page sits in arrives exactly at the"
                        + " document beneath the archive's own root: the chain's second click ends at the"
                        + " file the operator owns, with no database, no listing and no network between the"
                        + " reader and it -- which is the test this tree is written against, read as a"
                        + " reader exercises it rather than as the filesystem alone would",
                () -> assertThat(whereTheFirstEntryLeadsFrom(page)).isEqualTo(archive.resolve(THE_DOCUMENT)));
    }

    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("Where no route to the archive exists, the entry states the document and offers no link")
    @Issue("253")
    @Link(name = "ADR-135", url = Adr.A_MEMBERSHIP_ENTRY_LINKS_RELATIVELY_OR_NOT_AT_ALL, type = "adr")
    void statesTheDocumentWithoutALinkWhereTheArchiveIsOnAnotherVolume(@TempDir Path workingDirectory)
            throws IOException {
        assumeTrue(WINDOWS, "only Windows has two volumes, and so two places with no path between them");
        Path archive = Path.of(theVolumeThatIsNot(workingDirectory), "archive");
        Path page = thePageOf(write(
                workingDirectory,
                archive,
                new SynthesisDoc(THE_TITLE, "The nearest one [1] is the retrofit.", sent(10)),
                List.of(aMember(10, THE_DOCUMENT, A_HIGH_SCORE, FIRST_ORDINAL))));
        String text = Files.readString(page);

        claim(
                "the entry names the document and stops there, keeping its number and its anchor: there is"
                        + " no relative route from a tree on one volume to an archive on another, and the"
                        + " reader is given the document's place beneath the root the index states rather"
                        + " than a link",
                () -> assertThat(text).contains(THE_FIRST_ENTRY + ". <a id=\"document-1\"></a>" + THE_DOCUMENT));
        claim(
                "and no link is written in its place: the absolute form this stood in for is a link in "
                        + WHERE_A_SCHEME_SURVIVES + " of the " + SEVEN_CONFIGURATIONS + " measured, and in"
                        + " two of the others the reader is shown the source of an entry instead of an"
                        + " entry -- a page saying something other than what it means to say, which is the"
                        + " one failure this writer can decline to author",
                () -> assertThat(text).doesNotContain("file:").doesNotContain(THE_DOCUMENT + "]("));
    }

    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("An archive named by a relative path is stated in the entry and never linked to")
    @Issue("253")
    @Link(name = "ADR-135", url = Adr.A_MEMBERSHIP_ENTRY_LINKS_RELATIVELY_OR_NOT_AT_ALL, type = "adr")
    void statesTheDocumentWithoutALinkWhereTheArchiveRootIsItselfRelative(@TempDir Path workingDirectory)
            throws IOException {
        Path page = thePageOf(write(
                workingDirectory,
                A_RELATIVE_ARCHIVE_ROOT,
                new SynthesisDoc(THE_TITLE, "The nearest one [1] is the retrofit.", sent(10)),
                List.of(aMember(10, THE_DOCUMENT, A_HIGH_SCORE, FIRST_ORDINAL))));
        String text = Files.readString(page);

        claim(
                "precondition: the archive is named by a path that is relative on this machine, which is"
                        + " the state being claimed about and not an incidental of the fixture",
                () -> assertThat(Path.of(A_RELATIVE_ARCHIVE_ROOT).isAbsolute()).isFalse());
        claim(
                "the entry names the document and offers no link: a route composed from an archive that is"
                        + " itself named relatively would be a route from wherever the reader happens to be"
                        + " standing, so it would lead somewhere different for every reader and nowhere for"
                        + " most -- and an entry that states the document leads nowhere wrong",
                () -> assertThat(text)
                        .contains(THE_FIRST_ENTRY + ". <a id=\"document-1\"></a>" + THE_DOCUMENT)
                        .doesNotContain(THE_DOCUMENT + "]("));
        claim(
                "and the tree this fixture could not link through was written whole all the same: a writer"
                        + " that refused a page over a root it could not compose against would lose the"
                        + " faults the same invocation had already recorded, which is a heavy price for an"
                        + " entry that can simply say less",
                () -> assertThat(text).startsWith("# " + THE_TITLE));
    }

    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("An archive root this machine cannot even parse costs the link and nothing else")
    @Issue("253")
    @Link(name = "ADR-135", url = Adr.A_MEMBERSHIP_ENTRY_LINKS_RELATIVELY_OR_NOT_AT_ALL, type = "adr")
    @Link(name = "ADR-111", url = Adr.A_CLUSTER_FAULT_IS_A_ROW_IN_SYNTHESIS, type = "adr")
    void statesTheDocumentWithoutALinkWhereNoPathCanBeMadeOfTheArchiveRoot(@TempDir Path workingDirectory)
            throws IOException {
        Path page = thePageOf(write(
                workingDirectory,
                AN_ARCHIVE_ROOT_NO_PARSER_ACCEPTS,
                new SynthesisDoc(THE_TITLE, "The nearest one [1] is the retrofit.", sent(10)),
                List.of(aMember(10, THE_DOCUMENT, A_HIGH_SCORE, FIRST_ORDINAL))));
        String text = Files.readString(page);

        claim(
                "precondition: this machine's own path parser refuses the recorded root outright, which is"
                        + " the state being claimed about -- and it refuses it on both platforms this is"
                        + " built on, so the claim below is not one platform's",
                () -> assertThatThrownBy(() -> Path.of(AN_ARCHIVE_ROOT_NO_PARSER_ACCEPTS))
                        .isInstanceOf(InvalidPathException.class));
        claim(
                "the page was still written, and written whole: a root this machine cannot parse is not a"
                        + " reason to abandon a page, and a throw from this writer would escape the step and"
                        + " roll back every fault the invocation had already recorded against other groups",
                () -> assertThat(text).startsWith("# " + THE_TITLE));
        claim(
                "and the entry states the document with no link and no scheme: there is no route to compose"
                        + " without a path to compose it from, and the reader is given the document's place"
                        + " beneath the root the index states, which is a string and needs no parser",
                () -> assertThat(text)
                        .contains(THE_FIRST_ENTRY + ". <a id=\"document-1\"></a>" + THE_DOCUMENT)
                        .doesNotContain("file:")
                        .doesNotContain(THE_DOCUMENT + "]("));
    }

    @Test
    @Story("The list below is the whole group, not the part the writing happened to mention")
    @DisplayName("Every document of the group is listed in closeness order, the ones the call never sent included")
    @Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
    void listsEveryMemberInScoreOrderIncludingThoseNeverSent(@TempDir Path workingDirectory) throws IOException {
        String page = pageOf(write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The closest one [1] is the retrofit.", sent(11)),
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
                "the one document the call carried is entry " + THE_FIRST_ENTRY + ", and the ones it never"
                        + " carried follow it closest to the seed first: nothing was passed over here, so"
                        + " numbering from what was sent comes out as closeness order -- which is the"
                        + " ordinary case, and the case the two numberings always agreed in",
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
                "and the page says how much of the group the writing rests on, naming both numbers, so a"
                        + " reader knows the prose was written from " + ONE_DOCUMENT_SENT + " of "
                        + THREE_DOCUMENTS + " without going to look -- and says it as the first of them"
                        + " rather than the highest-scoring, because a document the call could not carry"
                        + " is dropped wherever it scored and the ones it did carry are then the top of"
                        + " nothing",
                () -> assertThat(page)
                        .contains("Written from the first " + ONE_DOCUMENT_SENT + " of the "
                                + THREE_DOCUMENTS + " documents in this group."));
    }

    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("Where a document was passed over, a citation still reaches the document it was written from")
    @Issue("236")
    @Link(name = "ADR-133", url = Adr.THE_EXEMPLARS_ONE_CALL_SENT_ARE_RECORDED, type = "adr")
    void numbersTheListFromWhatTheCallCarriedWhereAMemberWasPassedOver(@TempDir Path workingDirectory)
            throws IOException {
        String page = pageOf(write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The nearest [1] and the furthest [2] agree.", sent(10, 12)),
                List.of(
                        aMember(10, "reports/high.pdf", A_HIGH_SCORE, FIRST_ORDINAL),
                        aMember(11, "reports/middle.pdf", A_MIDDLE_SCORE, FIRST_ORDINAL),
                        aMember(12, "reports/low.pdf", A_LOW_SCORE, FIRST_ORDINAL))));

        claim(
                "entry " + THE_SECOND_ENTRY + " is the document the call was given as [2] -- the"
                        + " furthest one -- and not the middle document, which scores higher than it and"
                        + " was never sent: the call dropped that one, so numbering the group's own"
                        + " documents by score would have pointed [2] at a document the writing was not"
                        + " made from, and the link would have resolved, opened and been wrong",
                () -> assertThat(page)
                        .contains(THE_FIRST_ENTRY + ". <a id=\"document-1\"></a>[reports/high.pdf]")
                        .contains(THE_SECOND_ENTRY + ". <a id=\"document-2\"></a>[reports/low.pdf]"));
        claim(
                "and the document that was passed over is still listed, last of the " + THREE_DOCUMENTS
                        + ": the page states the group as it was arranged, and a document nothing could"
                        + " be written from is still one a reader can find and open",
                () -> assertThat(page)
                        .contains(THE_THIRD_ENTRY + ". <a id=\"document-3\"></a>[reports/middle.pdf]"));
        claim(
                "and the sentence about how much was read names the first " + TWO_DOCUMENTS + " of "
                        + THREE_DOCUMENTS + ", which is true of this page and checkable on it: the two"
                        + " that were sent are not the " + TWO_DOCUMENTS + " highest-scoring, and saying"
                        + " so would have been the same wrong claim as the link, written in words",
                () -> assertThat(page)
                        .contains("Written from the first " + TWO_DOCUMENTS + " of the " + THREE_DOCUMENTS
                                + " documents in this group."));
    }

    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("A document the writing was made from and the group no longer holds keeps its number")
    @Issue("236")
    @Link(name = "ADR-133", url = Adr.THE_EXEMPLARS_ONE_CALL_SENT_ARE_RECORDED, type = "adr")
    void keepsTheNumberOfADocumentTheClusterNoLongerHolds(@TempDir Path workingDirectory) throws IOException {
        String page = pageOf(write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The first [1] and the second [2] agree.", sent(10, 11)),
                List.of(aMember(10, "reports/high.pdf", A_HIGH_SCORE, FIRST_ORDINAL))));

        claim(
                "the document the group no longer holds keeps entry " + THE_SECOND_ENTRY + " and the"
                        + " page says what it is, rather than the entry being dropped: [2] above points"
                        + " at that number, and dropping the entry would move every document beneath it"
                        + " up one and point live citations at other documents",
                () -> assertThat(page)
                        .contains(THE_SECOND_ENTRY + ". <a id=\"document-2\"></a>"
                                + Deliverable.THE_CLUSTER_NO_LONGER_HOLDS_IT));
        claim(
                "and the page was still written rather than the run stopping over it: a throw from the"
                        + " writer would roll back every fault this invocation had already recorded, and"
                        + " an entry that says what happened costs nothing",
                () -> assertThat(page).contains("[reports/high.pdf]"));
    }

    @Test
    @Story("The list below is the whole group, not the part the writing happened to mention")
    @DisplayName("A group written from every one of its documents makes no claim about a subset")
    void disclosesNothingWhereTheWritingRestsOnTheWholeCluster(@TempDir Path workingDirectory) throws IOException {
        String page = pageOf(write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "Both of them [1] describe it.", sent(10, 11)),
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
                new SynthesisDoc(THE_TITLE, "The closest one [1] is the retrofit.", sent(10)),
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
                        .contains("Written from the first " + ONE_DOCUMENT_SENT + " of the "
                                + THREE_DOCUMENTS + " documents in this group."));
    }

    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("A name carrying a space or a quote becomes a link a reader can still follow")
    void escapesCharactersThatWouldBreakTheLink(@TempDir Path workingDirectory) throws IOException {
        String page = pageOf(write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The nearest one [1] is the retrofit.", sent(10)),
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
        return write(workingDirectory, anArchiveBeside(workingDirectory), doc, members, documentCount);
    }

    /** The same again, over a stated archive, for the claims that are about where the archive sits. */
    private static Path write(
            Path workingDirectory, Path archive, SynthesisDoc doc, List<ListedSurvivor> members) {
        return write(workingDirectory, archive.toString(), doc, members, members.size());
    }

    /**
     * And over an archive root stated as the string the ledger recorded, which is what {@code
     * DeliverableProvenance} actually carries.
     *
     * <p>Needed for the one fixture whose root this machine's path parser refuses outright: such a root
     * has no {@link Path} to hand in, and that is the whole of what it claims.
     */
    private static Path write(
            Path workingDirectory, String corpusRoot, SynthesisDoc doc, List<ListedSurvivor> members) {
        return write(workingDirectory, corpusRoot, doc, members, members.size());
    }

    private static Path write(
            Path workingDirectory,
            Path archive,
            SynthesisDoc doc,
            List<ListedSurvivor> members,
            int documentCount) {
        return write(workingDirectory, archive.toString(), doc, members, documentCount);
    }

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
        List<RecordedSynthesisDoc> written = doc == null
                ? List.of()
                : List.of(new RecordedSynthesisDoc(THE_SEED, FIRST_ORDINAL, doc));
        return Deliverable.writeTo(workingDirectory, provenance(corpusRoot), arrangement, written, members);
    }

    /** The bytes of the one cluster's page, read at the moment a claim asks for them. */
    private static String pageOf(Path tree) throws IOException {
        return Files.readString(thePageOf(tree));
    }

    /** Where that page sits, which a claim about a relative destination has to follow it from. */
    private static Path thePageOf(Path tree) {
        return tree.resolve(THE_PARTITION_DIRECTORY).resolve(THE_CLUSTER_PAGE);
    }

    /**
     * An archive beside the working directory, on its volume, and not on this machine at all.
     *
     * <p>Beside rather than at a name of its own, because ADR-135 composes the entry from the two
     * directories rather than from a name: a fixture naming a fixed drive letter would be in the
     * relative case on a machine whose temporary directory is on that drive and in the unlinked case
     * on one whose is not, so which decision it pinned would be a property of the machine.
     */
    private static Path anArchiveBeside(Path workingDirectory) {
        return workingDirectory.resolveSibling("archive-that-is-not-there");
    }

    /** A volume this working directory is not on, which is what makes a relative route impossible. */
    private static String theVolumeThatIsNot(Path workingDirectory) {
        return String.valueOf(workingDirectory.getRoot()).startsWith("C") ? "D:\\" : "C:\\";
    }

    /**
     * The destination of the page's first membership entry, read as a {@link URI} because that is what
     * decides the claim: a destination carrying a scheme is one a renderer may refuse, and a relative
     * one has none.
     */
    private static URI theDestinationOn(Path page) throws IOException {
        Matcher entry = THE_FIRST_ENTRYS_DESTINATION.matcher(Files.readString(page));
        if (!entry.find()) {
            throw new IllegalStateException("no membership entry on " + page + " carries a link at all");
        }
        return URI.create(entry.group(1));
    }

    /**
     * Where that destination leads, followed from the directory the page sits in and decoded first --
     * the percent-escapes are the renderer's to undo, and a claim that read them as characters of a
     * filename would be checking the encoding against itself rather than following the link.
     */
    private static Path whereTheFirstEntryLeadsFrom(Path page) throws IOException {
        return page.getParent()
                .resolve(theDestinationOn(page).getPath())
                .normalize();
    }

    /** What produced the tree, with the archive root stated so the links compose against it. */
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