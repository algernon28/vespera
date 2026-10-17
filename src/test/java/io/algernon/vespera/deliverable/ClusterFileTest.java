package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.ListedTree.sha256Of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
 * taken from, so each copy is made and stands. An entry links to that copy, by a path relative to the page, and
 * never into the archive.
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
class ClusterFileTest {

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

    /** What the title would slug to, which must never name the file: a faulted cluster has no title. */
    private static final String THE_TITLE_SLUGGED = "retrofitting-suppression-2018-to-2021";

    /** The document most fixtures below place in the archive, by the path the ledger holds for it. */
    private static final String THE_DOCUMENT = "reports/2019/retrofit.pdf";

    /** Where the copy of that document stands in the tree, as occurrence 10. */
    private static final String THE_DOCUMENTS_COPY = "documents/10-retrofit.pdf";

    /**
     * An archive root no path parser accepts, on either platform: a NUL is refused by the Windows
     * parser as an illegal character and by the POSIX one as a name it cannot hold.
     *
     * <p>Recorded roots are canonicalised on the machine that walked, and this one could not have been
     * -- which is the point. A root written for another platform, or a row edited by hand, arrives at this
     * writer as a string it can make no path of.
     */
    private static final String AN_ARCHIVE_ROOT_NO_PARSER_ACCEPTS = "D:/cor\u0000pus";

    /** What an entry says, in italics, where its copy could not be made for any reason but the four named. */
    private static final String COULD_NOT_BE_COPIED = "could not be copied";

    /** The one partition directory these fixtures write, named from the seed's stem, slugged. */
    private static final String THE_PARTITION_DIRECTORY = "1-industrial-safety-standards";

    /** The one cluster's page beneath it, named from the derived label and never the generated heading. */
    private static final String THE_CLUSTER_PAGE = "1-" + THE_LABEL_SLUGGED + ".html";

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
     * A cluster title that is itself an image in Markdown: the two brackets a link is made of, with a bang in
     * front of them, and a host. What the model answers is under no filesystem constraint at all, so nothing
     * bounds what it can spell, and this is the value the page's own heading is given.
     */
    private static final String A_TITLE_THAT_IS_ITSELF_AN_IMAGE = "Survey ![shot](https://example.com/x.png) 2019";

    /**
     * A document whose own name holds an ampersand and so spells an entity by accident, which this
     * filesystem allows and a walk can really record.
     */
    private static final String A_NAME_SPELLING_AN_ENTITY = "reports/&copy; notes.pdf";

    /** That name as the entry has to carry it: the ampersand as its entity, so a reader is shown the ampersand. */
    private static final String THAT_NAME_WITH_ITS_AMPERSAND_ESCAPED = "reports/&amp;copy; notes.pdf";

    /**
     * A document whose own name carries one backtick, standing in for an apostrophe as it often does in
     * a name typed on a keyboard that has one. NTFS permits it, so a walk can really record it.
     */
    private static final String A_NAME_WITH_ONE_BACKTICK = "reports/O`Brien survey.pdf";

    /** A document whose name carries two backticks either side of a bracketed word, a code span in Markdown. */
    private static final String A_NAME_WITH_BACKTICKS_AROUND_A_BRACKET = "reports/the `[final]` draft.pdf";

    /** A cluster title carrying one backtick and no partner for it. */
    private static final String A_TITLE_WITH_ONE_BACKTICK = "Retrofits after the O`Brien survey";

    /** A document whose own name carries a space, which a link to the archive had to encode. */
    private static final String A_NAME_WITH_A_SPACE = "reports/follow-up report.pdf";

    /** A document whose own name carries a quote, which NTFS allows and the JDK's path parser on Windows refuses. */
    private static final String A_NAME_WITH_A_QUOTE = "reports/2019/retrofit, phase \"two\".pdf";

    /** That name as the entry has to carry it: each quote as its entity, so no attribute can end at it. */
    private static final String THAT_NAME_WITH_ITS_QUOTES_ESCAPED = "reports/2019/retrofit, phase &quot;two&quot;.pdf";

    /** A bracketed ordinal that is not the text of a citation's link: a citation the page failed to rewrite (ADR-109). */
    private static final Pattern A_RAW_CITATION = Pattern.compile("(?<!\">)\\[\\d+\\]");

    /** The destination of the first membership entry: the link that opens its line, if it has one. */
    private static final Pattern THE_FIRST_ENTRYS_DESTINATION =
            Pattern.compile("<li id=\"document-1\">\n<a href=\"([^\"]*)\">");

    @Test
    @Story("The heading names the group the way the writing did, and the label where it did not")
    @DisplayName("A group's page opens with the heading the model wrote, and its file is named from the other name")
    void leadsWithTheGeneratedTitleAndNamesTheFileFromTheLabel(@TempDir Path workingDirectory) throws IOException {
        Path tree = write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The nearest one [1] is the retrofit.", sent(10)),
                List.of(aMember(10, THE_DOCUMENT, A_HIGH_SCORE, FIRST_ORDINAL)));

        String page = pageOf(tree);

        claim(
                "the page's heading is the one the model wrote rather than the name stage 6a derived:"
                        + " the heading is the one piece of writing a reader is meant to read first, and"
                        + " burying it under a mechanical name would present the derivation as the title",
                () -> assertThat(firstHeadingOf(page)).isEqualTo(THE_TITLE));
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
                List.of(aMember(10, THE_DOCUMENT, A_HIGH_SCORE, FIRST_ORDINAL)));

        String page = pageOf(tree);

        claim(
                "a group nothing was written over still has a page, headed by the label stage 6a derived"
                        + " for it: the label is the name that always exists, so the hole in the archive"
                        + " can be matched against the index without anything having been generated",
                () -> assertThat(firstHeadingOf(page)).isEqualTo(THE_LABEL));
        claim(
                "and the page says plainly, in italics, that nothing was written over it, rather than leaving a"
                        + " reader to infer it from a heading and a list",
                () -> assertThat(page).contains("<p><em>" + Deliverable.NOTHING_WAS_WRITTEN_OVER_IT + "</em></p>"));
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
                        aMember(10, THE_DOCUMENT, A_HIGH_SCORE, FIRST_ORDINAL),
                        aMember(11, "reports/2021/follow-up.pdf", A_MIDDLE_SCORE, FIRST_ORDINAL))));

        claim(
                "each [n] in the writing is rewritten into a link to membership entry n, so following a"
                        + " claim is one click to the entry rather than a number a reader has to hunt for",
                () -> assertThat(page)
                        .contains("<a href=\"#document-1\">[1]</a>")
                        .contains("<a href=\"#document-2\">[2]</a>"));
        claim(
                "and each entry carries the id its citation links to, so the click lands on it",
                () -> assertThat(page).contains("<li id=\"document-1\">", "<li id=\"document-2\">"));
        claim(
                "and no raw marker survives anywhere in the page: a bare [n] left behind would be the"
                        + " model's own notation sitting in a page a reader follows links from",
                () -> assertThat(A_RAW_CITATION.matcher(page).find()).isFalse());
    }

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
                        .contains("<a href=\"../" + THE_DOCUMENTS_COPY + "\">" + THE_DOCUMENT + "</a>"));
    }

    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("An archive root this machine cannot even parse costs the copy and nothing else")
    @Issue("253")
    @Link(name = "ADR-111", url = Adr.A_CLUSTER_FAULT_IS_A_ROW_IN_SYNTHESIS, type = "adr")
    void statesTheDocumentWithoutALinkWhereNoPathCanBeMadeOfTheArchiveRoot(@TempDir Path workingDirectory)
            throws IOException {
        Path page = thePageOf(write(
                workingDirectory,
                AN_ARCHIVE_ROOT_NO_PARSER_ACCEPTS,
                new SynthesisDoc(THE_TITLE, "The nearest one [1] is the retrofit.", sent(10)),
                List.of(aMember(10, THE_DOCUMENT, A_HIGH_SCORE, FIRST_ORDINAL)),
                1));
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
                () -> assertThat(firstHeadingOf(text)).isEqualTo(THE_TITLE));
        claim(
                "and the entry states the document and why no copy stands, in italics, and links nowhere: there"
                        + " is no copy to lead to, and no entry leads into the archive",
                () -> assertThat(text)
                        .contains("<li id=\"document-1\">\n" + THE_DOCUMENT + " <em>" + COULD_NOT_BE_COPIED
                                + "</em>\n</li>")
                        .doesNotContain("href=\"../"));
    }

    @Test
    @Story("The list below is the whole group, not the part the writing happened to mention")
    @DisplayName("Every document of the group is listed in closeness order, the ones the call never sent included")
    @Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
    void listsEveryMemberInScoreOrderIncludingThoseNeverSent(@TempDir Path workingDirectory) throws IOException {
        ListedSurvivor middle = aMember(10, "reports/middle.pdf", A_MIDDLE_SCORE, FIRST_ORDINAL);
        ListedSurvivor high = aMember(11, "reports/high.pdf", A_HIGH_SCORE, FIRST_ORDINAL);
        ListedSurvivor low = aMember(12, "reports/low.pdf", A_LOW_SCORE, FIRST_ORDINAL);
        String page = pageOf(write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The closest one [1] is the retrofit.", sent(11)),
                List.of(middle, high, low)));

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
                        + " carried follow it closest to the seed first, one entry after another in one list:"
                        + " nothing was passed over here, so numbering from what was sent comes out as"
                        + " closeness order -- which is the ordinary case, and the case the two numberings"
                        + " always agreed in",
                () -> assertThat(page)
                        .contains("<ol>\n" + entry(THE_FIRST_ENTRY, high) + entry(THE_SECOND_ENTRY, middle)
                                + entry(THE_THIRD_ENTRY, low) + "</ol>\n"));
        claim(
                "and the page says how much of the group the writing rests on, naming both numbers, so a"
                        + " reader knows the prose was written from " + ONE_DOCUMENT_SENT + " of "
                        + THREE_DOCUMENTS + " without going to look -- and says it as the first of them"
                        + " rather than the highest-scoring, because a document the call could not carry"
                        + " is dropped wherever it scored and the ones it did carry are then the top of"
                        + " nothing",
                () -> assertThat(page)
                        .contains("<p>Written from the first " + ONE_DOCUMENT_SENT + " of the "
                                + THREE_DOCUMENTS + " documents in this group.</p>"));
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

    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("A document the writing was made from and the group no longer holds keeps its number")
    @Issue("236")
    @Link(name = "ADR-133", url = Adr.THE_EXEMPLARS_ONE_CALL_SENT_ARE_RECORDED, type = "adr")
    void keepsTheNumberOfADocumentTheClusterNoLongerHolds(@TempDir Path workingDirectory) throws IOException {
        ListedSurvivor high = aMember(10, "reports/high.pdf", A_HIGH_SCORE, FIRST_ORDINAL);
        String page = pageOf(write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The first [1] and the second [2] agree.", sent(10, 11)),
                List.of(high)));

        claim(
                "the document the group no longer holds keeps entry " + THE_SECOND_ENTRY + " and the"
                        + " page says what it is, in italics, rather than the entry being dropped: [2] above"
                        + " points at that number, and dropping the entry would move every document beneath it"
                        + " up one and point live citations at other documents",
                () -> assertThat(page)
                        .contains("<li id=\"document-" + THE_SECOND_ENTRY + "\">\n<em>"
                                + Deliverable.THE_CLUSTER_NO_LONGER_HOLDS_IT + "</em>\n</li>\n"));
        claim(
                "and the page was still written rather than the run stopping over it: a throw from the"
                        + " writer would roll back every fault this invocation had already recorded, and"
                        + " an entry that says what happened costs nothing",
                () -> assertThat(page).contains(entry(THE_FIRST_ENTRY, high)));
    }

    @Test
    @Story("The list below is the whole group, not the part the writing happened to mention")
    @DisplayName("A group written from every one of its documents makes no claim about a subset")
    void disclosesNothingWhereTheWritingRestsOnTheWholeCluster(@TempDir Path workingDirectory) throws IOException {
        String page = pageOf(write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "Both of them [1] describe it.", sent(10, 11)),
                List.of(
                        aMember(10, THE_DOCUMENT, A_HIGH_SCORE, FIRST_ORDINAL),
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
                anArchiveBeside(workingDirectory).toString(),
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
    @DisplayName("A name carrying a space or a quote is shown as it is and its entry still leads to its copy")
    void keepsANameWithASpaceOrAQuoteReadableAndFollowable(@TempDir Path workingDirectory) throws IOException {
        ListedSurvivor spaced = aMember(10, A_NAME_WITH_A_SPACE, A_HIGH_SCORE, FIRST_ORDINAL);
        ListedSurvivor quoted = aMember(11, A_NAME_WITH_A_QUOTE, A_MIDDLE_SCORE, FIRST_ORDINAL);
        boolean theQuotedOriginalStands = true;
        try {
            Path theQuotedOriginal = anArchiveBeside(workingDirectory).resolve(A_NAME_WITH_A_QUOTE);
            Files.createDirectories(theQuotedOriginal.getParent());
            Files.write(theQuotedOriginal, bytesOf(A_NAME_WITH_A_QUOTE));
        } catch (InvalidPathException | IOException e) {
            theQuotedOriginalStands = false;
        }
        Path tree = write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The nearest one [1] is the retrofit.", sent(10)),
                List.of(spaced, quoted),
                List.of(spaced));
        String page = pageOf(tree);

        claim(
                "the entry for a name with a space links to its copy, whose name carries no space, and shows the"
                        + " name as the archive holds it",
                () -> assertThat(page).contains(entry(THE_FIRST_ENTRY, spaced)));
        claim(
                "and a name with a quote is shown with each quote as its entity, so nothing in it can end an"
                        + " attribute or open a tag, whatever became of its copy",
                () -> assertThat(page)
                        .contains(THAT_NAME_WITH_ITS_QUOTES_ESCAPED)
                        .doesNotContain("phase \"two\""));
        assumeTrue(
                theQuotedOriginalStands,
                "this file system will not create a name with a double quote, as NTFS will not, so there is no copy"
                        + " of one to link to");
        claim(
                "and where the quoted document is in the archive, its entry links to its copy, whose name carries no"
                        + " quote, with the name as the link's text",
                () -> assertThat(page).contains(entry(THE_SECOND_ENTRY, quoted)));
        claim(
                "and the copy it links to holds the quoted document's bytes",
                () -> assertThat(tree.resolve(Copies.pathOf(quoted))).hasBinaryContent(bytesOf(A_NAME_WITH_A_QUOTE)));
    }

    /**
     * The one heading in the tree a generated name reaches on its own (ADR-138, ADR-274 §1.3).
     *
     * <p>A title that spells an image in Markdown opened the page as an image, fetched from the host the name
     * gave, in a tree whose whole claim is that it resolves without a network. The title is not Markdown on an
     * HTML page and is not read as such: it is escaped by the one rule and shown as its characters.
     */
    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("A group whose written-up name spells an image opens its page with the name, not the image")
    @Issue("258")
    @Link(name = "ADR-138", url = Adr.A_BRACKET_IS_ESCAPED_IN_EVERY_SURROUNDING_A_VALUE_IS_READ_IN, type = "adr")
    void keepsATitleThatIsItselfAnImageReadableAsAName(@TempDir Path workingDirectory) throws IOException {
        String page = pageOf(write(
                workingDirectory,
                new SynthesisDoc(A_TITLE_THAT_IS_ITSELF_AN_IMAGE, "The nearest one [1] is the retrofit.", sent(10)),
                List.of(aMember(10, THE_DOCUMENT, A_HIGH_SCORE, FIRST_ORDINAL))));

        claim(
                "the page's heading is the name the writing gave the group, every character of it as written,"
                        + " the word between the brackets included",
                () -> assertThat(firstHeadingOf(page)).isEqualTo(A_TITLE_THAT_IS_ITSELF_AN_IMAGE));
        claim(
                "and nothing on the page fetches a picture from that host: no image names a source with a host",
                () -> assertThat(page).doesNotContain("<img").doesNotContain("src=\"https:"));
    }

    /**
     * The entry's text for a document whose own name spells an entity (ADR-136, ADR-274 §1.3).
     *
     * <p>An entity-shaped run left bare is the one ampersand case that loses text: the spelling of a copyright
     * sign is resolved into the sign. Under the one rule the ampersand is written as its entity, so a reader is
     * shown the characters the filename really holds.
     */
    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("A document whose own name spells an entity is listed under the name the archive holds")
    @Issue("251")
    @Link(name = "ADR-136", url = Adr.THE_ANGLE_BRACKET_AND_THE_AMPERSAND_ARE_ESCAPED, type = "adr")
    void keepsAnAmpersandInAnEntryReadableAsItself(@TempDir Path workingDirectory) throws IOException {
        String text = pageOf(write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The nearest one [1] is the retrofit.", sent(10)),
                List.of(aMember(10, A_NAME_SPELLING_AN_ENTITY, A_HIGH_SCORE, FIRST_ORDINAL))));

        claim(
                "the entry names the document with its ampersand written as the entity for an ampersand, so the"
                        + " reader is shown the characters the filename really holds rather than the sign they"
                        + " spell",
                () -> assertThat(text).contains(">" + THAT_NAME_WITH_ITS_AMPERSAND_ESCAPED + "</a>"));
        claim(
                "and the name's own run is never written bare, where it would be read as the sign",
                () -> assertThat(text).doesNotContain(A_NAME_SPELLING_AN_ENTITY));
    }

    /**
     * Where that same entry leads, which is a different question from what it says (ADR-137, ADR-274 §2.1).
     *
     * <p>The claim is over the characters the page carries: a copy's name is the occurrence and a slug, so the
     * route carries no ampersand at all, cannot begin an entity-shaped run, and holds for every name of that
     * shape rather than for the one this fixture spells.
     */
    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("A document whose own name spells an entity is reached at its copy")
    @Issue("259")
    @Link(name = "ADR-137", url = Adr.A_DESTINATIONS_AMPERSAND_IS_PERCENT_ENCODED, type = "adr")
    void routesADocumentWhoseNameSpellsAnEntityToItsCopy(@TempDir Path workingDirectory) throws IOException {
        Path tree = write(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The nearest one [1] is the retrofit.", sent(10)),
                List.of(aMember(10, A_NAME_SPELLING_AN_ENTITY, A_HIGH_SCORE, FIRST_ORDINAL)));
        Path page = thePageOf(tree);
        String destination = theDestinationTextOn(page);

        claim(
                "no ampersand survives anywhere in the route, so there is nothing in it for a reader's browser"
                        + " to resolve into some other character, whatever the document is called",
                () -> assertThat(destination).doesNotContain("&"));
        claim(
                "and following it arrives at the document's copy, which holds the bytes of the document the"
                        + " archive really holds",
                () -> assertThat(whereTheFirstEntryLeadsFrom(page))
                        .hasBinaryContent(bytesOf(A_NAME_SPELLING_AN_ENTITY)));
    }

    /**
     * The two positions a backtick reaches on a group's page, the membership entry and the heading (ADR-148,
     * ADR-274 §1.3).
     *
     * <p>Two backticks were a code span in Markdown, and inside one a backslash written before a bracket was shown
     * at the reader. HTML has no such reading: a backtick is none of the five characters the one rule writes as an
     * entity, so it is shown as written, with no backslash beside it, in the heading and the entries alike.
     */
    @Test
    @Story("A claim leads to the document behind it in two clicks")
    @DisplayName("A document or group named with backticks is listed under that name rather than as code")
    @Issue("261")
    @Link(name = "ADR-148", url = Adr.A_BACKTICK_IS_ESCAPED_IN_EVERY_SURROUNDING_A_VALUE_IS_READ_IN, type = "adr")
    void keepsABacktickInAnEntryAndAHeadingReadableAsItself(@TempDir Path workingDirectory) throws IOException {
        Path tree = write(
                workingDirectory,
                new SynthesisDoc(A_TITLE_WITH_ONE_BACKTICK, "The nearest one [1] is the survey.", sent(10)),
                List.of(
                        aMember(10, A_NAME_WITH_ONE_BACKTICK, A_HIGH_SCORE, FIRST_ORDINAL),
                        aMember(11, A_NAME_WITH_BACKTICKS_AROUND_A_BRACKET, A_MIDDLE_SCORE, FIRST_ORDINAL)));
        Path page = thePageOf(tree);
        String text = Files.readString(page);
        String destination = theDestinationTextOn(page);

        claim(
                "the page's heading is the name the writing gave the group, its one backtick as written and"
                        + " nothing put beside it",
                () -> assertThat(firstHeadingOf(text)).isEqualTo(A_TITLE_WITH_ONE_BACKTICK));
        claim(
                "and each entry shows its document's name as the archive holds it, the backticks and the bracketed"
                        + " word included, and no backslash anywhere in the page",
                () -> assertThat(text)
                        .contains(">" + A_NAME_WITH_ONE_BACKTICK + "</a>")
                        .contains(">" + A_NAME_WITH_BACKTICKS_AROUND_A_BRACKET + "</a>")
                        .doesNotContain("\\"));
        claim(
                "while the route to the first document carries no backtick and no backslash, and still arrives at"
                        + " its copy, which holds the original's bytes",
                () -> {
                    assertThat(destination).doesNotContain("`").doesNotContain("\\");
                    assertThat(whereTheFirstEntryLeadsFrom(page)).hasBinaryContent(bytesOf(A_NAME_WITH_ONE_BACKTICK));
                });
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

    /** One membership entry whose copy stands, as the page writes it: its id, a link to its copy, its path. */
    private static String entry(int number, ListedSurvivor member) {
        return "<li id=\"document-" + number + "\">\n<a href=\"../" + Copies.pathOf(member) + "\">"
                + Html.escape(member.path().value()) + "</a>\n</li>\n";
    }

    /** The text of the page's first {@code <h1>}. */
    private static String firstHeadingOf(String page) {
        Matcher heading = Pattern.compile("<h1>(.*?)</h1>", Pattern.DOTALL).matcher(page);
        if (!heading.find()) {
            throw new IllegalStateException("the page has no heading at all");
        }
        return heading.group(1);
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
