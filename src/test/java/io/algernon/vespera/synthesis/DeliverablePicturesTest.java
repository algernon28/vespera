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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A survivor's pictures in the tree (ADR-149, #285): which of them are furniture and left out, where
 * the ones kept are written and what they are named, how each is shown under its document's
 * membership entry, what its alt text is, and how many a document may show.
 *
 * <p>Written against the writer with the pictures handed in by hand, as {@code ClusterFileTest} is:
 * the pictures a real run hands in come out of the extraction cache through {@code pipeline}, and a
 * stubbed extractor that gives every document the same response gives every document the same
 * pictures, which the furniture rule rightly removes. Nothing here renders a page; the bytes of the
 * page and of the files beside it are the evidence, and the destinations are followed the way a
 * renderer follows them.
 *
 * <p>The pixels are short byte strings rather than real images. The writer never decodes a picture:
 * it compares bytes, names a file after their digest, and writes them through, so a valid PNG would
 * prove nothing a distinct byte string does not.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122).
 */
@Epic("Synthesis")
@Feature("The pictures a document carries")
@Issue("285")
@Link(name = "ADR-149", url = Adr.A_SURVIVORS_PICTURES_REACH_ITS_CLUSTER_FILE, type = "adr")
@Link(name = "ADR-103", url = Adr.THE_DELIVERABLE_IS_A_MARKDOWN_TREE_PER_RUN, type = "adr")
@Link(name = "ADR-135", url = Adr.A_MEMBERSHIP_ENTRY_LINKS_RELATIVELY_OR_NOT_AT_ALL, type = "adr")
class DeliverablePicturesTest {

    /** A run id of the shape the ledger mints: sixty-four hexadecimal characters (ADR-048). */
    private static final String RUN_ID = "b".repeat(48) + "0123456789abcdef";

    /** The walk the run read. */
    private static final long WALK = 7L;

    /** The seed whose partition every cluster below sits in. */
    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    /** The seed document's own filename, which is what the partition directory is named from. */
    private static final String SEED_PATH = "seeds/Industrial Safety Standards.docx";

    /** The one partition directory these fixtures write. */
    private static final String THE_PARTITION_DIRECTORY = "1-industrial-safety-standards";

    /** What stage 6a called the cluster most fixtures arrange. */
    private static final String THE_LABEL = "Fire Suppression Retrofits";

    /** That cluster's page, named from its label. */
    private static final String THE_CLUSTER_PAGE = "1-fire-suppression-retrofits.md";

    /** The directory its pictures go in: the page's own name without its extension. */
    private static final String THE_PICTURE_DIRECTORY = "1-fire-suppression-retrofits";

    /** A second cluster's label, for the fixture that spreads documents over two pages. */
    private static final String THE_OTHER_LABEL = "Sprinkler Maintenance";

    /** That second cluster's page. */
    private static final String THE_OTHER_CLUSTER_PAGE = "2-sprinkler-maintenance.md";

    /** And the directory its pictures would go in. */
    private static final String THE_OTHER_PICTURE_DIRECTORY = "2-sprinkler-maintenance";

    /** What the model called the cluster, where a fixture wants a written page. */
    private static final String THE_TITLE = "Retrofitting Suppression, 2018 to 2021";

    /** The cluster identity 6a minted for the first cluster, and for the second. */
    private static final int FIRST_ORDINAL = 0;

    private static final int SECOND_ORDINAL = 1;

    /** The first and second places in an order, which both levels count from (ADR-112). */
    private static final int FIRST_PLACE = 1;

    private static final int SECOND_PLACE = 2;

    /** Scores that put the members of a cluster in a known order. */
    private static final double A_HIGH_SCORE = 0.9;

    private static final double A_MIDDLE_SCORE = 0.5;

    /** The media type every picture in the measured cache carried. */
    private static final String PNG = "image/png";

    /** The other media type the tree writes a file for. */
    private static final String JPEG = "image/jpeg";

    /** A media type the tree writes no file for, because none was ever measured in the cache. */
    private static final String GIF = "image/gif";

    /** How many hexadecimal characters of a picture's digest name its file. */
    private static final int NAME_LENGTH = 16;

    /** The most pictures one document shows, as the writer states it. */
    private static final int THE_BUDGET = Deliverable.PICTURES_PER_DOCUMENT;

    /** The number the decision fixed, stated here so the claim can name it rather than read it back. */
    private static final int TEN = 10;

    /** How many pictures past the budget the overflowing fixture carries. */
    private static final int TWO_OVER = 2;

    /**
     * The whole page for a written cluster of two documents with no pictures, as the writer wrote it
     * before pictures existed. The routes climb four directories -- the partition, the run's tree,
     * {@code deliverable} and the working directory -- to the archive beside the working directory.
     */
    private static final String THE_PAGE_WITH_NO_PICTURES = "# " + THE_TITLE + "\n"
            + "\n"
            + "Both [1](#document-1) and [2](#document-2).\n"
            + "\n"
            + "## The documents in this group\n"
            + "\n"
            + "1. <a id=\"document-1\"></a>[reports/a.docx](../../../../archive-that-is-not-there/reports/a.docx)\n"
            + "\n"
            + "2. <a id=\"document-2\"></a>[reports/b.pdf](../../../../archive-that-is-not-there/reports/b.pdf)\n"
            + "\n";

    /** How many times each listed document is asked for its pictures: once per pass. */
    private static final int TWO_PASSES = 2;

    /** An entry number wide enough that its list marker is four characters, {@code "10. "}. */
    private static final int A_TWO_DIGIT_ENTRY = 10;

    /**
     * A document name carrying every character a rule elsewhere in the tree escapes, plus a quote the
     * JDK's Windows path parser refuses: legal on NTFS, and nothing a file name or a link may take up.
     */
    private static final String A_HOSTILE_DOCUMENT_NAME =
            "reports/a ](evil.md) &copy; `tick` \"quoted\" #1 100%.pdf";

    /** A cluster label that would climb out of the tree and form a link if anything carried it through. */
    private static final String A_HOSTILE_LABEL = "../../[x](y) & `z`";

    /** That label as {@code slug} leaves it, which is what the page and its directory are named from. */
    private static final String THE_HOSTILE_LABELS_PAGE_STEM = "1-x-y-z";

    /**
     * A caption Docling might return: a link and an image written inside it, an HTML tag, an entity, a
     * code span, a backslash, and a blank line in the middle.
     */
    private static final String A_HOSTILE_CAPTION =
            "Fig. 2 a]b ![x](https://example.com/i.png) <b>bold</b> &copy; `c` \\ end\n\nsecond line";

    /**
     * That caption as alt text: folded onto one line, then every character the membership entry's rule
     * escapes written behind a backslash, the backslash itself first.
     */
    private static final String THAT_CAPTION_AS_ALT_TEXT =
            "Fig. 2 a\\]b !\\[x\\](https://example.com/i.png) \\<b>bold\\</b> \\&copy; \\`c\\` \\\\ end second line";

    /** Characters a picture's destination may be made of: the page stem, a slash, hex, a dot, an extension. */
    private static final Pattern A_SAFE_DESTINATION = Pattern.compile("[0-9a-z-]+/[0-9a-f]{16}\\.(png|jpg)");

    /** Every image a page shows, as its alt text and its destination. */
    private static final Pattern AN_IMAGE = Pattern.compile("!\\[((?:\\\\.|[^\\]\\\\])*)\\]\\(([^)]*)\\)");

    @Test
    @Story("A picture only one document carries is shown under that document")
    @DisplayName("A picture only one document carries is written beside the group's page and shown under its entry")
    void writesAPictureBesideThePageAndShowsItUnderItsEntry(@TempDir Path workingDirectory) throws IOException {
        byte[] diagram = bytes("an architecture diagram");
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(new OccurrenceId(10), List.of(png(diagram)));

        Path tree = writeOneCluster(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The nearest one [1] is the retrofit.", sent(10)),
                List.of(aMember(10, "reports/2019/retrofit.docx", A_HIGH_SCORE)),
                pictures);
        Path page = thePageOf(tree);
        Path file = tree.resolve(THE_PARTITION_DIRECTORY).resolve(THE_PICTURE_DIRECTORY).resolve(nameOf(diagram, ".png"));

        claim(
                "the picture is written as a file in a directory beside the group's page, named after that"
                        + " page, and the file holds exactly the bytes that were handed in",
                () -> assertThat(file).isRegularFile().hasBinaryContent(diagram));
        claim(
                "and the page shows it as an image directly under the document's own entry, indented into"
                        + " that entry so the list stays one list, with no alt text because none was given",
                () -> assertThat(Files.readString(page))
                        .contains("1. <a id=\"document-1\"></a>[reports/2019/retrofit.docx](")
                        .contains(")\n\n   ![](" + THE_PICTURE_DIRECTORY + "/" + nameOf(diagram, ".png") + ")\n"));
        claim(
                "and the image's destination is relative, carries no scheme, and followed from the page's"
                        + " own directory lands on the file that was written: nothing outside the tree and"
                        + " nothing on a network is needed to see it",
                () -> assertThat(whereTheOnlyImageLeads(page)).isEqualTo(file));
    }

    @Test
    @Story("A picture that recurs is furniture and is left out")
    @DisplayName("A picture two documents share is left out of both, even when they sit on different pages")
    void leavesOutAPictureTwoDocumentsShare(@TempDir Path workingDirectory) throws IOException {
        byte[] logo = bytes("a letterhead logo");
        byte[] diagram = bytes("a diagram only the first document has");
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(
                new OccurrenceId(10), List.of(png(logo), png(diagram)),
                new OccurrenceId(20), List.of(png(logo)));

        Path tree = writeTwoClusters(
                workingDirectory,
                List.of(
                        aMemberOf(10, "reports/first.docx", A_HIGH_SCORE, FIRST_ORDINAL),
                        aMemberOf(20, "reports/second.docx", A_HIGH_SCORE, SECOND_ORDINAL)),
                pictures);
        Path partition = tree.resolve(THE_PARTITION_DIRECTORY);

        claim(
                "the logo both documents carry is written nowhere in the tree, although each page holds only"
                        + " one of the two documents: recurrence is counted over every document the tree"
                        + " lists, not page by page",
                () -> assertThat(allFilesUnder(tree)).noneMatch(file -> file.getFileName().toString()
                        .equals(nameOf(logo, ".png"))));
        claim(
                "and neither page links it",
                () -> assertThat(Files.readString(partition.resolve(THE_CLUSTER_PAGE))
                                + Files.readString(partition.resolve(THE_OTHER_CLUSTER_PAGE)))
                        .doesNotContain(nameOf(logo, ".png")));
        claim(
                "while the diagram only the first document carries is still written and shown, so leaving"
                        + " out the shared picture costs its neighbour nothing",
                () -> assertThat(partition.resolve(THE_PICTURE_DIRECTORY).resolve(nameOf(diagram, ".png")))
                        .hasBinaryContent(diagram));
        claim(
                "and the second page, whose document carried nothing but the shared logo, gets no picture"
                        + " directory at all",
                () -> assertThat(partition.resolve(THE_OTHER_PICTURE_DIRECTORY)).doesNotExist());
    }

    @Test
    @Story("A picture that recurs is furniture and is left out")
    @DisplayName("A picture repeated inside one document is left out, every copy of it")
    void leavesOutAPictureRepeatedInsideOneDocument(@TempDir Path workingDirectory) throws IOException {
        byte[] icon = bytes("a server icon repeated across a diagram");
        Map<OccurrenceId, List<ListedPicture>> pictures =
                Map.of(new OccurrenceId(10), List.of(png(icon), png(icon), png(icon)));

        Path tree = writeOneCluster(
                workingDirectory, null, List.of(aMember(10, "reports/deck.pptx", A_HIGH_SCORE)), pictures);

        claim(
                "an icon repeated three times inside one document is written not even once: the repetition"
                        + " is the evidence it is part of the document's furniture, and keeping the first"
                        + " copy would keep the furniture",
                () -> assertThat(tree.resolve(THE_PARTITION_DIRECTORY).resolve(THE_PICTURE_DIRECTORY))
                        .doesNotExist());
        claim(
                "and the entry carries no image",
                () -> assertThat(Files.readString(thePageOf(tree))).doesNotContain("!["));
    }

    @Test
    @Story("A picture that recurs is furniture and is left out")
    @DisplayName("A picture the converter itself placed in the page header or footer is left out")
    void leavesOutAPictureTheConverterPlacedInThePageFurniture(@TempDir Path workingDirectory) throws IOException {
        byte[] header = bytes("a page-header logo in a one-page document");
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(
                new OccurrenceId(10), List.of(new ListedPicture(PNG, header, true, "")));

        Path tree = writeOneCluster(
                workingDirectory, null, List.of(aMember(10, "reports/memo.docx", A_HIGH_SCORE)), pictures);

        claim(
                "a picture the converter placed in its page furniture is left out although nothing else in"
                        + " the tree carries the same bytes: that is the one case recurrence cannot see",
                () -> assertThat(allFilesUnder(tree)).noneMatch(file -> file.getFileName().toString()
                        .equals(nameOf(header, ".png"))));
    }

    @Test
    @Story("A picture that recurs is furniture and is left out")
    @DisplayName("A picture one document's header carries and another's body carries is left out of both")
    void countsAHeaderPictureTowardsRecurrenceInAnotherDocument(@TempDir Path workingDirectory) throws IOException {
        byte[] logo = bytes("a logo in one document's header and another's body");
        byte[] chart = bytes("a chart only the second document has");
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(
                new OccurrenceId(10), List.of(new ListedPicture(PNG, logo, true, "")),
                new OccurrenceId(11), List.of(png(logo), png(chart)));

        Path tree = writeOneCluster(
                workingDirectory,
                null,
                List.of(
                        aMember(10, "reports/memo.docx", A_HIGH_SCORE),
                        aMember(11, "reports/report.docx", A_MIDDLE_SCORE)),
                pictures);

        claim(
                "the logo is written nowhere: in the second document it sits in the body, where its flag"
                        + " alone would have kept it, but its bytes recur because the first document carries"
                        + " them too, in its header -- recurrence counts every picture of every document,"
                        + " whichever layer it sits in",
                () -> assertThat(allFilesUnder(tree)).noneMatch(file -> file.getFileName().toString()
                        .equals(nameOf(logo, ".png"))));
        claim(
                "while the chart only the second document carries is still shown",
                () -> assertThat(destinationsOn(thePageOf(tree)))
                        .containsExactly(THE_PICTURE_DIRECTORY + "/" + nameOf(chart, ".png")));
    }

    @Test
    @Story("A picture only one document carries is shown under that document")
    @DisplayName("Each document's picture sits directly under its own entry, and the next entry follows it")
    void placesEachPictureDirectlyUnderItsOwnEntry(@TempDir Path workingDirectory) throws IOException {
        byte[] first = bytes("the first document's diagram");
        byte[] second = bytes("the second document's diagram");
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(
                new OccurrenceId(10), List.of(png(first)),
                new OccurrenceId(11), List.of(png(second)));

        Path tree = writeOneCluster(
                workingDirectory,
                null,
                List.of(
                        aMember(10, "reports/a.docx", A_HIGH_SCORE),
                        aMember(11, "reports/b.docx", A_MIDDLE_SCORE)),
                pictures);
        String text = Files.readString(thePageOf(tree));
        Pattern layout = Pattern.compile(Pattern.quote("1. <a id=\"document-1\"></a>[reports/a.docx](")
                + "[^)\\n]*"
                + Pattern.quote(")\n\n   ![](" + THE_PICTURE_DIRECTORY + "/" + nameOf(first, ".png") + ")\n\n"
                        + "2. <a id=\"document-2\"></a>[reports/b.docx](")
                + "[^)\\n]*"
                + Pattern.quote(")\n\n   ![](" + THE_PICTURE_DIRECTORY + "/" + nameOf(second, ".png") + ")\n\n"));

        claim(
                "the page reads, byte for byte apart from the two routes into the archive: the first entry's"
                        + " line, a blank line, its picture indented three spaces, a blank line, then the"
                        + " second entry's line, a blank line and its own picture -- so each picture is a"
                        + " paragraph inside the entry above it and the list runs on unbroken",
                () -> assertThat(layout.matcher(text).find()).isTrue());
    }

    @Test
    @Story("A picture only one document carries is shown under that document")
    @DisplayName("Every document is asked for its pictures twice: once to find what recurs, once to write")
    void asksForEachDocumentsPicturesOncePerPass(@TempDir Path workingDirectory) {
        Map<OccurrenceId, Integer> asked = new HashMap<>();
        Map<OccurrenceId, List<ListedPicture>> pictures =
                Map.of(new OccurrenceId(10), List.of(png(bytes("a diagram"))));
        List<ListedSurvivor> members = List.of(
                aMemberOf(10, "reports/first.docx", A_HIGH_SCORE, FIRST_ORDINAL),
                aMemberOf(20, "reports/second.docx", A_HIGH_SCORE, SECOND_ORDINAL),
                aMemberOf(21, "reports/third.docx", A_MIDDLE_SCORE, SECOND_ORDINAL));

        Deliverable.writeTo(
                workingDirectory,
                provenance(workingDirectory),
                List.of(
                        cluster(FIRST_ORDINAL, 1, FIRST_PLACE, THE_LABEL),
                        cluster(SECOND_ORDINAL, 2, SECOND_PLACE, THE_OTHER_LABEL)),
                List.of(),
                members,
                occurrence -> {
                    asked.merge(occurrence, 1, Integer::sum);
                    return pictures.getOrDefault(occurrence, List.of());
                });

        claim(
                "each of the " + members.size() + " documents, on either page and with or without a picture,"
                        + " is asked exactly " + TWO_PASSES + " times: once while every document is counted for"
                        + " recurrence, once while its own page is written -- so nothing has to hold every"
                        + " document's pixels at once, and no document is asked a third time",
                () -> assertThat(asked).containsOnlyKeys(
                                new OccurrenceId(10), new OccurrenceId(20), new OccurrenceId(21))
                        .allSatisfy((occurrence, count) -> assertThat(count).isEqualTo(TWO_PASSES)));
    }

    @Test
    @Story("A picture's file is named from its own bytes")
    @DisplayName("No character of a document's name or a group's label reaches a picture's file name or link")
    void namesThePictureFromItsBytesWhateverTheDocumentIsCalled(@TempDir Path workingDirectory) throws IOException {
        byte[] screenshot = bytes("a table screenshot");
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(new OccurrenceId(10), List.of(png(screenshot)));

        Path tree = write(
                workingDirectory,
                List.of(cluster(FIRST_ORDINAL, 1, FIRST_PLACE, A_HOSTILE_LABEL)),
                List.of(),
                List.of(aMember(10, A_HOSTILE_DOCUMENT_NAME, A_HIGH_SCORE)),
                pictures);
        Path partition = tree.resolve(THE_PARTITION_DIRECTORY);
        Path page = partition.resolve(THE_HOSTILE_LABELS_PAGE_STEM + ".md");
        Path expected = partition.resolve(THE_HOSTILE_LABELS_PAGE_STEM).resolve(nameOf(screenshot, ".png"));

        claim(
                "the picture's file is named from the first " + NAME_LENGTH + " hexadecimal characters of"
                        + " its own digest and sits in the directory named after the page, whose name the"
                        + " label only reaches after being reduced to letters, digits and hyphens",
                () -> assertThat(expected).isRegularFile().hasBinaryContent(screenshot));
        claim(
                "and it is the only file written anywhere beneath the partition apart from the page, so"
                        + " neither the document's name nor the label climbed out of the tree or named a"
                        + " second file",
                () -> assertThat(allFilesUnder(partition)).containsExactlyInAnyOrder(page, expected));
        claim(
                "and the destination the page gives it is made only of the page's stem, a slash, hexadecimal"
                        + " and an extension -- no character a Markdown, URI or filesystem rule treats as"
                        + " special, so there is nothing to escape and nothing to decode",
                () -> assertThat(destinationsOn(page)).singleElement().matches(d -> A_SAFE_DESTINATION
                        .matcher(d).matches(), "a destination of the safe shape"));
        claim(
                "and following it from the page's own directory lands on that file",
                () -> assertThat(whereTheOnlyImageLeads(page)).isEqualTo(expected));
    }

    @Test
    @Story("A picture's alt text is the converter's caption or nothing")
    @DisplayName("A caption is written as the picture's alt text on one line, with nothing in it able to form a link or an image")
    void escapesACaptionWrittenAsAltText(@TempDir Path workingDirectory) throws IOException {
        byte[] chart = bytes("a chart with a caption");
        Map<OccurrenceId, List<ListedPicture>> pictures =
                Map.of(new OccurrenceId(10), List.of(new ListedPicture(PNG, chart, false, A_HOSTILE_CAPTION)));

        Path tree = writeOneCluster(
                workingDirectory, null, List.of(aMember(10, "reports/chart.docx", A_HIGH_SCORE)), pictures);
        String text = Files.readString(thePageOf(tree));

        claim(
                "the caption is the image's alt text, folded onto one line and with every bracket, angle"
                        + " bracket, ampersand, backtick and backslash behind a backslash: written through,"
                        + " the link inside it would end the alt text early and the image inside it would"
                        + " replace the tree's own picture with one fetched from the network",
                () -> assertThat(text)
                        .contains("   ![" + THAT_CAPTION_AS_ALT_TEXT + "](" + THE_PICTURE_DIRECTORY + "/"
                                + nameOf(chart, ".png") + ")\n"));
        claim(
                "and the page shows exactly one image, the one from the tree, with no second image and no"
                        + " remote destination anywhere",
                () -> assertThat(destinationsOn(thePageOf(tree)))
                        .containsExactly(THE_PICTURE_DIRECTORY + "/" + nameOf(chart, ".png")));
    }

    @Test
    @Story("A document shows at most ten pictures, and says how many more it has")
    @DisplayName("A document with more pictures than a page should carry shows the first ten and says how many more there are")
    void showsTheFirstTenPicturesAndCountsTheRest(@TempDir Path workingDirectory) throws IOException {
        List<ListedPicture> twelve = new ArrayList<>();
        for (int at = 0; at < THE_BUDGET + TWO_OVER; at++) {
            twelve.add(png(bytes("slide " + at)));
        }
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(new OccurrenceId(10), List.copyOf(twelve));

        Path tree = writeOneCluster(
                workingDirectory, null, List.of(aMember(10, "reports/deck.pptx", A_HIGH_SCORE)), pictures);
        Path page = thePageOf(tree);
        List<String> expected = new ArrayList<>();
        for (int at = 0; at < THE_BUDGET; at++) {
            expected.add(THE_PICTURE_DIRECTORY + "/" + nameOf(bytes("slide " + at), ".png"));
        }

        claim(
                "the budget is " + TEN + " pictures a document",
                () -> assertThat(THE_BUDGET).isEqualTo(TEN));
        claim(
                "the page shows the first " + TEN + " pictures, in the order the document gave them, and"
                        + " not the " + TWO_OVER + " after them",
                () -> assertThat(destinationsOn(page)).containsExactlyElementsOf(expected));
        claim(
                "and only those " + TEN + " files are written",
                () -> assertThat(allFilesUnder(tree.resolve(THE_PARTITION_DIRECTORY).resolve(THE_PICTURE_DIRECTORY)))
                        .hasSize(TEN));
        claim(
                "and the entry says, after its pictures, how many more the document has, so the page never"
                        + " passes off part of a document's pictures as all of them",
                () -> assertThat(Files.readString(page))
                        .contains("\n\n   *" + TWO_OVER + " more pictures from this document are not shown.*\n"));
    }

    @Test
    @Story("A document shows at most ten pictures, and says how many more it has")
    @DisplayName("One picture past the budget is counted in the singular, and a document within it says nothing")
    void countsOnePictureInTheSingularAndNothingWithinTheBudget(@TempDir Path workingDirectory) throws IOException {
        List<ListedPicture> eleven = new ArrayList<>();
        List<ListedPicture> three = new ArrayList<>();
        for (int at = 0; at <= THE_BUDGET; at++) {
            eleven.add(png(bytes("first document, slide " + at)));
        }
        for (int at = 0; at < 3; at++) {
            three.add(png(bytes("second document, figure " + at)));
        }
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(
                new OccurrenceId(10), List.copyOf(eleven),
                new OccurrenceId(11), List.copyOf(three));

        Path tree = writeOneCluster(
                workingDirectory,
                null,
                List.of(
                        aMember(10, "reports/deck.pptx", A_HIGH_SCORE),
                        aMember(11, "reports/figures.docx", A_MIDDLE_SCORE)),
                pictures);
        String text = Files.readString(thePageOf(tree));

        claim(
                "one picture past the budget is counted in the singular",
                () -> assertThat(text).contains("   *1 more picture from this document is not shown.*\n"));
        claim(
                "and that is the only such line on the page: the document within the budget carries none",
                () -> assertThat(text.split("from this document", -1)).hasSize(2));
    }

    @Test
    @Story("A picture's file is named from its own bytes")
    @DisplayName("A JPEG picture is written as .jpg, and a picture of any other kind is counted rather than written")
    void writesAJpegAsJpgAndCountsAnyOtherKind(@TempDir Path workingDirectory) throws IOException {
        byte[] photo = bytes("a photograph");
        byte[] animation = bytes("an animation");
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(
                new OccurrenceId(10),
                List.of(new ListedPicture(JPEG, photo, false, ""), new ListedPicture(GIF, animation, false, "")));

        Path tree = writeOneCluster(
                workingDirectory, null, List.of(aMember(10, "reports/site.docx", A_HIGH_SCORE)), pictures);
        Path directory = tree.resolve(THE_PARTITION_DIRECTORY).resolve(THE_PICTURE_DIRECTORY);

        claim(
                "the JPEG is written with a .jpg extension and linked by it",
                () -> assertThat(destinationsOn(thePageOf(tree)))
                        .containsExactly(THE_PICTURE_DIRECTORY + "/" + nameOf(photo, ".jpg")));
        claim(
                "and the picture of a kind never measured in the cache is not written under any name",
                () -> assertThat(allFilesUnder(directory)).containsExactly(directory.resolve(nameOf(photo, ".jpg"))));
        claim(
                "but it is counted as not shown, so the page does not pass it off as absent",
                () -> assertThat(Files.readString(thePageOf(tree)))
                        .contains("   *1 more picture from this document is not shown.*\n"));
    }

    @Test
    @Story("A picture only one document carries is shown under that document")
    @DisplayName("Under a two-digit entry a picture is indented four spaces, so it stays inside that entry")
    void indentsAPictureByTheWidthOfItsEntrysNumber(@TempDir Path workingDirectory) throws IOException {
        List<ListedSurvivor> members = new ArrayList<>();
        for (int at = 1; at <= A_TWO_DIGIT_ENTRY; at++) {
            // Descending scores put occurrence 100 + n at entry n on a page nothing was written over.
            members.add(aMember(100 + at, "reports/doc-" + at + ".docx", 1.0 - at / 100.0));
        }
        byte[] figure = bytes("the tenth document's figure");
        Map<OccurrenceId, List<ListedPicture>> pictures =
                Map.of(new OccurrenceId(100 + A_TWO_DIGIT_ENTRY), List.of(png(figure)));

        Path tree = writeOneCluster(workingDirectory, null, List.copyOf(members), pictures);

        claim(
                "the picture under entry " + A_TWO_DIGIT_ENTRY + " is indented by the four characters its"
                        + " marker takes, \"10. \": fewer and the picture falls out of the entry, and four"
                        + " more would turn it into a code block",
                () -> assertThat(Files.readString(thePageOf(tree)))
                        .contains(")\n\n    ![](" + THE_PICTURE_DIRECTORY + "/" + nameOf(figure, ".png") + ")\n"));
    }

    @Test
    @Story("The listing and the index are unchanged")
    @DisplayName("The manifest and the index read the same with pictures as without them")
    void leavesTheManifestAndTheIndexAsTheyWere(@TempDir Path withPictures, @TempDir Path withoutPictures)
            throws IOException {
        byte[] diagram = bytes("a diagram");
        List<ListedSurvivor> members = List.of(aMember(10, "reports/a.docx", A_HIGH_SCORE));

        Path shown = writeOneCluster(
                withPictures, null, members, Map.of(new OccurrenceId(10), List.of(png(diagram))));
        Path plain = writeOneCluster(withoutPictures, null, members, Map.of());

        claim(
                "documents.csv is byte for byte the file written without pictures: a row per document, and"
                        + " no column a picture could be squeezed into",
                () -> assertThat(Files.readString(shown.resolve(Deliverable.MANIFEST_FILE_NAME)))
                        .isEqualTo(Files.readString(plain.resolve(Deliverable.MANIFEST_FILE_NAME))));
        claim(
                "and index.md is byte for byte the index written without them",
                () -> assertThat(Files.readString(shown.resolve(Deliverable.INDEX_FILE_NAME)))
                        .isEqualTo(Files.readString(plain.resolve(Deliverable.INDEX_FILE_NAME))));
    }

    @Test
    @Story("The listing and the index are unchanged")
    @DisplayName("A page with no pictures to show carries exactly the heading, the writing and the list, and nothing else")
    void writesAPageWithNoPicturesExactlyAsItWasWritten(@TempDir Path workingDirectory) throws IOException {
        List<ListedSurvivor> members = List.of(
                aMember(10, "reports/a.docx", A_HIGH_SCORE), aMember(11, "reports/b.pdf", A_MIDDLE_SCORE));
        List<RecordedCluster> arrangement = List.of(cluster(FIRST_ORDINAL, members.size(), FIRST_PLACE, THE_LABEL));
        List<RecordedSynthesisDoc> written = List.of(new RecordedSynthesisDoc(
                THE_SEED, FIRST_ORDINAL, new SynthesisDoc(THE_TITLE, "Both [1] and [2].", sent(10, 11))));

        Path withNone = Deliverable.writeTo(
                workingDirectory, provenance(workingDirectory), arrangement, written, members, SurvivorPictures.none());

        claim(
                "the page written with an empty source of pictures is exactly the page stated here -- the"
                        + " heading, the writing with its citations as links, the list heading and one line"
                        + " per document -- with no image, no not-shown line and no blank line added for"
                        + " pictures that are not there",
                () -> assertThat(Files.readString(withNone.resolve(THE_PARTITION_DIRECTORY).resolve(THE_CLUSTER_PAGE)))
                        .isEqualTo(THE_PAGE_WITH_NO_PICTURES));
        claim(
                "and no picture directory is created where there is nothing to put in it",
                () -> assertThat(withNone.resolve(THE_PARTITION_DIRECTORY).resolve(THE_PICTURE_DIRECTORY))
                        .doesNotExist());
    }

    /** One cluster holding {@code members}, arranged at their count, written over by {@code doc} or not. */
    private static Path writeOneCluster(
            Path workingDirectory,
            SynthesisDoc doc,
            List<ListedSurvivor> members,
            Map<OccurrenceId, List<ListedPicture>> pictures) {
        List<RecordedSynthesisDoc> written =
                doc == null ? List.of() : List.of(new RecordedSynthesisDoc(THE_SEED, FIRST_ORDINAL, doc));
        return write(
                workingDirectory,
                List.of(cluster(FIRST_ORDINAL, members.size(), FIRST_PLACE, THE_LABEL)),
                written,
                members,
                pictures);
    }

    /** Two clusters of one document each, neither written over, so each has its own page. */
    private static Path writeTwoClusters(
            Path workingDirectory, List<ListedSurvivor> members, Map<OccurrenceId, List<ListedPicture>> pictures) {
        return write(
                workingDirectory,
                List.of(
                        cluster(FIRST_ORDINAL, 1, FIRST_PLACE, THE_LABEL),
                        cluster(SECOND_ORDINAL, 1, SECOND_PLACE, THE_OTHER_LABEL)),
                List.of(),
                members,
                pictures);
    }

    private static Path write(
            Path workingDirectory,
            List<RecordedCluster> arrangement,
            List<RecordedSynthesisDoc> written,
            List<ListedSurvivor> members,
            Map<OccurrenceId, List<ListedPicture>> pictures) {
        Map<OccurrenceId, List<ListedPicture>> source = new HashMap<>(pictures);
        return Deliverable.writeTo(
                workingDirectory,
                provenance(workingDirectory),
                arrangement,
                written,
                members,
                occurrence -> source.getOrDefault(occurrence, List.of()));
    }

    private static RecordedCluster cluster(int ordinal, int documentCount, int clusterOrder, String label) {
        return new RecordedCluster(
                new ArrangedCluster(THE_SEED, ordinal, documentCount, FIRST_PLACE, clusterOrder),
                new ClusterLabel(label));
    }

    /** What produced the tree, over an archive beside the working directory and not on this machine. */
    private static DeliverableProvenance provenance(Path workingDirectory) {
        return new DeliverableProvenance(
                RUN_ID, WALK, workingDirectory.resolveSibling("archive-that-is-not-there").toString(), List.of());
    }

    private static Path thePageOf(Path tree) {
        return tree.resolve(THE_PARTITION_DIRECTORY).resolve(THE_CLUSTER_PAGE);
    }

    /** Every image destination on {@code page}, in the order the page gives them. */
    private static List<String> destinationsOn(Path page) throws IOException {
        Matcher image = AN_IMAGE.matcher(Files.readString(page));
        List<String> found = new ArrayList<>();
        while (image.find()) {
            found.add(image.group(2));
        }
        return List.copyOf(found);
    }

    /**
     * Where the page's one image leads, followed from the page's own directory and decoded first, as a
     * renderer follows it. A destination carrying a scheme fails here rather than resolving somewhere.
     */
    private static Path whereTheOnlyImageLeads(Path page) throws IOException {
        List<String> destinations = destinationsOn(page);
        if (destinations.size() != 1) {
            throw new IllegalStateException("expected one image on " + page + ", found " + destinations);
        }
        URI destination = URI.create(destinations.getFirst());
        if (destination.isAbsolute() || destination.getPath().startsWith("/")) {
            throw new IllegalStateException("the image on " + page + " is not relative: " + destination);
        }
        return page.getParent().resolve(destination.getPath()).normalize();
    }

    private static List<Path> allFilesUnder(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).toList();
        }
    }

    /** The file name ADR-149 gives a picture: sixteen hex characters of its digest, then the extension. */
    private static String nameOf(byte[] pixels, String extension) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(pixels);
            return HexFormat.of().formatHex(digest).substring(0, NAME_LENGTH) + extension;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static ListedPicture png(byte[] pixels) {
        return new ListedPicture(PNG, pixels, false, "");
    }

    private static List<OccurrenceId> sent(long... occurrences) {
        List<OccurrenceId> carried = new ArrayList<>();
        for (long occurrence : occurrences) {
            carried.add(new OccurrenceId(occurrence));
        }
        return List.copyOf(carried);
    }

    /** One survivor of the first cluster. */
    private static ListedSurvivor aMember(long occurrence, String path, double score) {
        return aMemberOf(occurrence, path, score, FIRST_ORDINAL);
    }

    private static ListedSurvivor aMemberOf(long occurrence, String path, double score, int ordinal) {
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
