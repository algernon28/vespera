package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A survivor's pictures in the tree (ADR-149, #285): which of them are furniture and left out, how each
 * one kept is shown under its document's membership entry, by its own bytes as a base64 source on its
 * image (ADR-286 §1), what its alt text is, and how many a document may show.
 *
 * <p>Written against the writer with the pictures handed in by hand, as {@code ClusterFileTest} is:
 * the pictures a real run hands in come out of the extraction cache through {@code pipeline}, and a
 * stubbed extractor that gives every document the same response gives every document the same
 * pictures, which the furniture rule rightly removes. Nothing here renders a page; the bytes of the
 * page are the evidence, a picture being found on it as its {@code data:} source.
 *
 * <p>The archive these fixtures name is not on this machine, so no copy stands and each entry names its
 * document with the reason in italics (ADR-274 §2.6); the pictures are shown under it all the same. A cluster's page
 * is {@code index.html} in its cluster folder (ADR-280 §0), and its pictures are in it, written into no file
 * (ADR-286 §4), which {@code AClusterPageIsOneFileWithItsPicturesInlinedTest} holds.
 *
 * <p>Most pixels are short byte strings behind the signature of the kind they declare, rather than real
 * images, since a picture whose bytes are not of its kind is not shown (ADR-286 §1). They cannot be
 * decoded, so they have no difference hash, and the rules that need one leave them alone (ADR-150 §3): what
 * those tests claim
 * is decided by bytes, layer, budget and naming alone, and a string still being written is itself the
 * evidence that a picture which does not decode is not dropped. The tests of ADR-150's near-copy and
 * same-place rules use real PNGs, built so that their difference hashes differ in a stated number of
 * bits.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122).
 */
@Epic("The deliverable")
@Feature("The pictures a document carries")
@Issue("285")
@Link(name = "ADR-149", url = Adr.A_SURVIVORS_PICTURES_REACH_ITS_CLUSTER_FILE, type = "adr")
@Link(name = "ADR-103", url = Adr.THE_DELIVERABLE_IS_A_MARKDOWN_TREE_PER_RUN, type = "adr")
@Link(name = "ADR-135", url = Adr.A_MEMBERSHIP_ENTRY_LINKS_RELATIVELY_OR_NOT_AT_ALL, type = "adr")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
@Link(name = "ADR-280", url = Adr.A_CLUSTERS_FOLDER_HOLDS_ITS_PAGE_AND_ITS_SURVIVORS_COPIES, type = "adr")
@Link(name = "ADR-286", url = Adr.A_CLUSTERS_PAGE_IS_ONE_FILE_ITS_PICTURES_INLINED, type = "adr")
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

    /** That cluster's folder, named from its label, which holds its page and its pictures. */
    private static final String THE_CLUSTER_FOLDER = "1-fire-suppression-retrofits";

    /** That cluster's page, in its folder. */
    private static final String THE_CLUSTER_PAGE = THE_CLUSTER_FOLDER + "/index.html";

    /** A second cluster's label, for the fixture that spreads documents over two pages. */
    private static final String THE_OTHER_LABEL = "Sprinkler Maintenance";

    /** That second cluster's folder. */
    private static final String THE_OTHER_CLUSTER_FOLDER = "2-sprinkler-maintenance";

    /** And its page. */
    private static final String THE_OTHER_CLUSTER_PAGE = THE_OTHER_CLUSTER_FOLDER + "/index.html";

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

    private static final double A_LOW_SCORE = 0.2;

    /** The difference hash's grid: 9 columns compared pairwise, in 8 rows (ADR-150 §3). */
    private static final int HASH_COLUMNS = 9;

    private static final int HASH_ROWS = 8;

    /** How many bits, and how many pixels on each side, a near-copy may differ by (ADR-150 §3 (c)). */
    private static final int NEAR_COPY_BITS = 2;

    private static final int NEAR_COPY_PIXELS = 2;

    /** How many bits two pictures at one place may differ by, and how many points each edge (§3 (d)). */
    private static final int SAME_PLACE_BITS = 8;

    private static final double SAME_PLACE_POINTS = 3.0;

    /**
     * How far apart two crops of one header logo are in bits: more than a near-copy allows, within
     * what the same-place rule allows, as the measured crops were (research §6d).
     */
    private static final int SAME_PLACE_BITS_SEEN = 5;

    /**
     * Hashes for pictures that are nothing like one another: any two a test uses together still differ
     * in at least 18 bits after the bits it flips, far more than either rule allows.
     */
    private static final long A_LOGO = 0x0123456789abcdefL;

    private static final long A_DIAGRAM = ~A_LOGO;

    private static final long A_CHART = 0x0f0f0f0f0f0f0f0fL;

    private static final long AN_ICON = 0x3333333333333333L;

    private static final long A_STAMP = 0x5555555555555555L;

    /**
     * Where on the first page a picture sits, its edges in points. {@link #placed} moves one to the page
     * a picture is on.
     */
    private static final ListedPicturePlace THE_HEADER_FRAME = new ListedPicturePlace(1, 60.5, 795.5, 205.0, 761.0);

    private static final ListedPicturePlace A_BODY_FRAME = new ListedPicturePlace(1, 90.0, 470.0, 690.0, 160.0);

    private static final ListedPicturePlace A_FOOTER_FRAME = new ListedPicturePlace(1, 450.0, 60.0, 520.0, 30.0);

    /** The media type every picture in the measured cache carried. */
    private static final String PNG = "image/png";

    /** The other media type a page shows a picture of. */
    private static final String JPEG = "image/jpeg";

    /** A media type no page shows, because none was ever measured in the cache. */
    private static final String GIF = "image/gif";

    /** What every PNG begins with. */
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** What every JPEG begins with. */
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    /** The most pictures one document shows, as the writer states it. */
    private static final int THE_BUDGET = Deliverable.PICTURES_PER_DOCUMENT;

    /** The number the decision fixed, stated here so the claim can name it rather than read it back. */
    private static final int TEN = 10;

    /** How many pictures past the budget the overflowing fixture carries. */
    private static final int TWO_OVER = 2;

    /** What an entry says, in italics, of a document no longer in the archive (ADR-274 §2.3). */
    private static final String GONE = "<em>no longer in the archive, so it was not copied</em>";

    /**
     * The whole page for a written cluster of two documents with no pictures, as the writer wrote it
     * before pictures existed. The archive is not there, so each entry names its document and says so.
     */
    private static final String THE_PAGE_WITH_NO_PICTURES = Html.head(THE_TITLE)
            + "<h1>" + THE_TITLE + "</h1>\n"
            + "<div class=\"writing\" title=\"What the local model wrote over this group from the documents listed below it;"
            + " each number in brackets is a link to the document it cites.\">\n"
            + "<p>Both <a href=\"#document-1\">[1]</a> and <a href=\"#document-2\">[2]</a>.</p>\n"
            + "</div>\n"
            + "<h2>The documents in this group</h2>\n"
            + "<ol>\n"
            + "<li id=\"document-1\">\nreports/a.docx " + GONE + "\n</li>\n"
            + "<li id=\"document-2\">\nreports/b.pdf " + GONE + "\n</li>\n"
            + "</ol>\n"
            + Html.tail();

    /** How many times each listed document is asked for its pictures: once per pass. */
    private static final int TWO_PASSES = 2;

    /**
     * A document name carrying every character a rule elsewhere in the tree escapes, plus a quote the
     * JDK's Windows path parser refuses: legal on NTFS, and nothing a file name or a link may take up.
     */
    private static final String A_HOSTILE_DOCUMENT_NAME =
            "reports/a ](evil.md) &copy; `tick` \"quoted\" #1 100%.pdf";

    /** A cluster label that would climb out of the tree and form a link if anything carried it through. */
    private static final String A_HOSTILE_LABEL = "../../[x](y) & `z`";

    /** That label as {@code slug} leaves it, which is what the page's folder is named from. */
    private static final String THE_HOSTILE_LABELS_PAGE_STEM = "1-x-y-z";

    /**
     * A caption Docling might return: a link and an image written inside it, an HTML tag, an entity, a
     * code span, a backslash, and a blank line in the middle.
     */
    private static final String A_HOSTILE_CAPTION =
            "Fig. 2 a]b ![x](https://example.com/i.png) <b>bold</b> &copy; `c` \\ end\n\nsecond line";

    /**
     * That caption as alt text: each of the five characters the one rule writes as an entity so written, and
     * nothing else touched, its blank line included (ADR-274 §1.3).
     */
    private static final String THAT_CAPTION_AS_ALT_TEXT =
            "Fig. 2 a]b ![x](https://example.com/i.png) &lt;b&gt;bold&lt;/b&gt; &amp;copy; `c` \\ end\n\nsecond line";

    /** What a picture's source may be made of: its media type, then the base64 alphabet and its padding alone. */
    private static final Pattern A_SAFE_SOURCE = Pattern.compile("data:image/png;base64,[A-Za-z0-9+/]*={0,2}");

    /** Every image a page shows, as its destination and its alt text. */
    private static final Pattern AN_IMAGE = Pattern.compile("<img src=\"([^\"]*)\" alt=\"([^\"]*)\">");

    @Test
    @Story("A picture only one document carries is shown under that document")
    @DisplayName("A picture only one document carries is shown under its entry by its own bytes")
    void showsAPictureUnderItsEntryByItsOwnBytes(@TempDir Path workingDirectory) throws IOException {
        byte[] diagram = pixels("an architecture diagram");
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(new OccurrenceId(10), List.of(png(diagram)));

        Path tree = writeOneCluster(
                workingDirectory,
                new SynthesisDoc(THE_TITLE, "The nearest one [1] is the retrofit.", sent(10)),
                List.of(aMember(10, "reports/2019/retrofit.docx", A_HIGH_SCORE)),
                pictures);
        Path page = thePageOf(tree);

        claim(
                "the page shows the picture directly under the document's own entry, inside that entry's item so"
                        + " the list stays one list, its source exactly the bytes that were handed in, and no alt text"
                        + " because none was given",
                () -> assertThat(Files.readString(page))
                        .contains("<li id=\"document-1\">\nreports/2019/retrofit.docx " + GONE + "\n"
                                + "<p><img src=\"" + shown(diagram) + "\" alt=\"\"></p>\n</li>\n"));
    }

    @Test
    @Story("A picture that recurs is furniture and is left out")
    @DisplayName("A picture two documents share is left out of both, even when they sit on different pages")
    void leavesOutAPictureTwoDocumentsShare(@TempDir Path workingDirectory) throws IOException {
        byte[] logo = pixels("a letterhead logo");
        byte[] diagram = pixels("a diagram only the first document has");
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
                "the letterhead both documents carry is shown on neither page, although each page holds only"
                        + " one of the two documents: recurrence is counted over every document the tree"
                        + " lists, not page by page",
                () -> assertThat(shownAnywhere(tree)).doesNotContain(shown(logo)));
        claim(
                "while the diagram only the first document carries is still shown on the first page, so leaving"
                        + " out the shared picture costs its neighbour nothing",
                () -> assertThat(sourcesOn(partition.resolve(THE_CLUSTER_PAGE))).containsExactly(shown(diagram)));
        claim(
                "and the second group's page, whose document carried nothing but the shared letterhead, shows no"
                        + " picture",
                () -> assertThat(Files.readString(partition.resolve(THE_OTHER_CLUSTER_PAGE))).doesNotContain("<img"));
    }

    @Test
    @Story("A picture that recurs is furniture and is left out")
    @DisplayName("A picture repeated inside one document is left out, every copy of it")
    void leavesOutAPictureRepeatedInsideOneDocument(@TempDir Path workingDirectory) throws IOException {
        byte[] icon = pixels("a server icon repeated across a diagram");
        Map<OccurrenceId, List<ListedPicture>> pictures =
                Map.of(new OccurrenceId(10), List.of(png(icon), png(icon), png(icon)));

        Path tree = writeOneCluster(
                workingDirectory, null, List.of(aMember(10, "reports/deck.pptx", A_HIGH_SCORE)), pictures);

        claim(
                "an icon repeated three times inside one document is shown not even once, and the entry carries no"
                        + " image: the repetition is the evidence it is part of the document's furniture, and"
                        + " keeping the first copy would keep the furniture",
                () -> assertThat(Files.readString(thePageOf(tree))).doesNotContain("<img"));
    }

    @Test
    @Story("A picture that recurs is furniture and is left out")
    @DisplayName("A picture the converter itself placed in the page header or footer is left out")
    void leavesOutAPictureTheConverterPlacedInThePageFurniture(@TempDir Path workingDirectory) throws IOException {
        byte[] header = pixels("a page-header logo in a one-page document");
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(
                new OccurrenceId(10), List.of(new ListedPicture(PNG, header, true, "")));

        Path tree = writeOneCluster(
                workingDirectory, null, List.of(aMember(10, "reports/memo.docx", A_HIGH_SCORE)), pictures);

        claim(
                "a picture the converter placed in its page furniture is left out although nothing else in"
                        + " the tree carries the same bytes: that is the one case recurrence cannot see",
                () -> assertThat(shownAnywhere(tree)).doesNotContain(shown(header)));
    }

    @Test
    @Story("A picture that recurs is furniture and is left out")
    @DisplayName("A picture one document's header carries and another's body carries is left out of both")
    void countsAHeaderPictureTowardsRecurrenceInAnotherDocument(@TempDir Path workingDirectory) throws IOException {
        byte[] logo = pixels("a logo in one document's header and another's body");
        byte[] chart = pixels("a chart only the second document has");
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
                "the emblem is shown nowhere: in the second document it sits in the body, where its flag"
                        + " alone would have kept it, but its bytes recur because the first document carries"
                        + " them too, in its header -- recurrence counts every picture of every document,"
                        + " whichever layer it sits in",
                () -> assertThat(shownAnywhere(tree)).doesNotContain(shown(logo)));
        claim(
                "while the chart only the second document carries is still shown",
                () -> assertThat(sourcesOn(thePageOf(tree))).containsExactly(shown(chart)));
    }

    @Test
    @Story("A picture only one document carries is shown under that document")
    @DisplayName("Each document's picture sits directly under its own entry, and the next entry follows it")
    void placesEachPictureDirectlyUnderItsOwnEntry(@TempDir Path workingDirectory) throws IOException {
        byte[] first = pixels("the first document's diagram");
        byte[] second = pixels("the second document's diagram");
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
        String layout = "<li id=\"document-1\">\nreports/a.docx " + GONE + "\n"
                + "<p><img src=\"" + shown(first) + "\" alt=\"\"></p>\n"
                + "</li>\n"
                + "<li id=\"document-2\">\nreports/b.docx " + GONE + "\n"
                + "<p><img src=\"" + shown(second) + "\" alt=\"\"></p>\n"
                + "</li>\n";

        claim(
                "the page reads, byte for byte: the first entry's item, its document, its picture in a"
                        + " paragraph of its own and the item's end, then the second entry's item the same way --"
                        + " so each picture is inside the entry above it and the list runs on unbroken",
                () -> assertThat(text).contains(layout));
    }

    @Test
    @Story("A picture only one document carries is shown under that document")
    @DisplayName("Every document is asked for its pictures twice: once to find what recurs, once to write")
    void asksForEachDocumentsPicturesOncePerPass(@TempDir Path workingDirectory) {
        Map<OccurrenceId, Integer> asked = new HashMap<>();
        Map<OccurrenceId, List<ListedPicture>> pictures =
                Map.of(new OccurrenceId(10), List.of(png(pixels("a diagram"))));
        List<ListedSurvivor> members = List.of(
                aMemberOf(10, "reports/first.docx", A_HIGH_SCORE, FIRST_ORDINAL),
                aMemberOf(20, "reports/second.docx", A_HIGH_SCORE, SECOND_ORDINAL),
                aMemberOf(21, "reports/third.docx", A_MIDDLE_SCORE, SECOND_ORDINAL));

        ListedArrangement.writeTo(
                workingDirectory,
                provenance(workingDirectory),
                List.of(
                        cluster(FIRST_ORDINAL, 1, FIRST_PLACE, THE_LABEL),
                        cluster(SECOND_ORDINAL, 2, SECOND_PLACE, THE_OTHER_LABEL)),
                List.of(),
                members,
                survivor -> {
                    asked.merge(survivor.occurrence(), 1, Integer::sum);
                    return pictures.getOrDefault(survivor.occurrence(), List.of());
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
    @Story("A picture is shown by its own bytes")
    @DisplayName("No character of a document's name or a group's label reaches a picture's source or a file")
    @Issue("560")
    void noCharacterOfADocumentsNameOrALabelReachesAPicturesSource(@TempDir Path workingDirectory) throws IOException {
        byte[] screenshot = pixels("a table screenshot");
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(new OccurrenceId(10), List.of(png(screenshot)));

        Path tree = write(
                workingDirectory,
                List.of(cluster(FIRST_ORDINAL, 1, FIRST_PLACE, A_HOSTILE_LABEL)),
                List.of(),
                List.of(aMember(10, A_HOSTILE_DOCUMENT_NAME, A_HIGH_SCORE)),
                pictures);
        Path partition = tree.resolve(THE_PARTITION_DIRECTORY);
        Path page = partition.resolve(THE_HOSTILE_LABELS_PAGE_STEM).resolve("index.html");

        claim(
                "the picture's source is its media type and its own bytes in base64, made only of the base64"
                        + " alphabet and its padding, so no character of the document's name or the label reaches it"
                        + " and nothing in it is special to HTML",
                () -> assertThat(sourcesOn(page))
                        .singleElement()
                        .isEqualTo(shown(screenshot))
                        .matches(source -> A_SAFE_SOURCE.matcher(source).matches(), "a source of the safe shape"));
        claim(
                "and the page is the only file written anywhere beneath the partition, in the group's folder whose"
                        + " name the label only reaches after being reduced to letters, digits and hyphens: neither"
                        + " the document's name nor the label climbed out of the tree or named a second file",
                () -> assertThat(allFilesUnder(partition)).containsExactly(page));
    }

    @Test
    @Story("A picture's alt text is the converter's caption or nothing")
    @DisplayName("A caption is written as the picture's alt text, with nothing in it able to end the attribute or form a tag")
    void escapesACaptionWrittenAsAltText(@TempDir Path workingDirectory) throws IOException {
        byte[] chart = pixels("a chart with a caption");
        Map<OccurrenceId, List<ListedPicture>> pictures =
                Map.of(new OccurrenceId(10), List.of(new ListedPicture(PNG, chart, false, A_HOSTILE_CAPTION)));

        Path tree = writeOneCluster(
                workingDirectory, null, List.of(aMember(10, "reports/chart.docx", A_HIGH_SCORE)), pictures);
        String text = Files.readString(thePageOf(tree));

        claim(
                "the caption is the image's alt text, each angle bracket and ampersand written as its entity:"
                        + " written through, the tag inside it would end the attribute and the image's tag early,"
                        + " and what follows would be markup of the caption's own",
                () -> assertThat(text)
                        .contains("<p><img src=\"" + shown(chart)
                                + "\" alt=\"" + THAT_CAPTION_AS_ALT_TEXT + "\"></p>\n"));
        claim(
                "and the page shows exactly one image, the picture handed in, with no second image and no"
                        + " remote source anywhere",
                () -> assertThat(sourcesOn(thePageOf(tree))).containsExactly(shown(chart)));
    }

    @Test
    @Story("A document shows at most ten pictures, and says how many more it has")
    @DisplayName("A document with more pictures than a page should carry shows the first ten and says how many more there are")
    void showsTheFirstTenPicturesAndCountsTheRest(@TempDir Path workingDirectory) throws IOException {
        List<ListedPicture> twelve = new ArrayList<>();
        for (int at = 0; at < THE_BUDGET + TWO_OVER; at++) {
            twelve.add(png(pixels("slide " + at)));
        }
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(new OccurrenceId(10), List.copyOf(twelve));

        Path tree = writeOneCluster(
                workingDirectory, null, List.of(aMember(10, "reports/deck.pptx", A_HIGH_SCORE)), pictures);
        Path page = thePageOf(tree);
        List<String> expected = new ArrayList<>();
        for (int at = 0; at < THE_BUDGET; at++) {
            expected.add(shown(pixels("slide " + at)));
        }

        claim(
                "the budget is " + TEN + " pictures a document",
                () -> assertThat(THE_BUDGET).isEqualTo(TEN));
        claim(
                "the page shows the first " + TEN + " pictures, in the order the document gave them, and"
                        + " not the " + TWO_OVER + " after them",
                () -> assertThat(sourcesOn(page)).containsExactlyElementsOf(expected));
        claim(
                "and the entry says, after its pictures, how many more the document has, so the page never"
                        + " passes off part of a document's pictures as all of them",
                () -> assertThat(Files.readString(page))
                        .contains("<p><em>" + TWO_OVER + " more pictures from this document are not shown.</em></p>\n"));
    }

    @Test
    @Story("A document shows at most ten pictures, and says how many more it has")
    @DisplayName("One picture past the budget is counted in the singular, and a document within it says nothing")
    void countsOnePictureInTheSingularAndNothingWithinTheBudget(@TempDir Path workingDirectory) throws IOException {
        List<ListedPicture> eleven = new ArrayList<>();
        List<ListedPicture> three = new ArrayList<>();
        for (int at = 0; at <= THE_BUDGET; at++) {
            eleven.add(png(pixels("first document, slide " + at)));
        }
        for (int at = 0; at < 3; at++) {
            three.add(png(pixels("second document, figure " + at)));
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
                () -> assertThat(text).contains("<p><em>1 more picture from this document is not shown.</em></p>\n"));
        claim(
                "and that is the only such line on the page: the document within the budget carries none",
                () -> assertThat(text.split("from this document", -1)).hasSize(2));
    }

    @Test
    @Story("A picture is shown by its own bytes")
    @DisplayName("A JPEG picture is shown as a JPEG, and a picture of any other kind is counted rather than shown")
    @Issue("560")
    void showsAJpegAsAJpegAndCountsAnyOtherKind(@TempDir Path workingDirectory) throws IOException {
        byte[] photo = withSignature(JPEG_SIGNATURE, "a photograph");
        byte[] animation = "GIF89a an animation".getBytes(StandardCharsets.UTF_8);
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(
                new OccurrenceId(10),
                List.of(new ListedPicture(JPEG, photo, false, ""), new ListedPicture(GIF, animation, false, "")));

        Path tree = writeOneCluster(
                workingDirectory, null, List.of(aMember(10, "reports/site.docx", A_HIGH_SCORE)), pictures);

        claim(
                "the JPEG is shown as a JPEG, by its own bytes, and the picture of a kind never measured in the"
                        + " cache is not shown at all",
                () -> assertThat(sourcesOn(thePageOf(tree)))
                        .containsExactly("data:" + JPEG + ";base64," + Base64.getEncoder().encodeToString(photo)));
        claim(
                "but it is counted as not shown, so the page does not pass it off as absent",
                () -> assertThat(Files.readString(thePageOf(tree)))
                        .contains("<p><em>1 more picture from this document is not shown.</em></p>\n"));
    }

    @Test
    @Story("The listing and the index are unchanged")
    @DisplayName("The manifest and the index read the same with pictures as without them")
    void leavesTheManifestAndTheIndexAsTheyWere(@TempDir Path withPictures, @TempDir Path withoutPictures)
            throws IOException {
        byte[] diagram = pixels("a diagram");
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
                "and index.html is byte for byte the index written without them",
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
        List<ListedDoc> written = List.of(new ListedDoc(
                THE_SEED, FIRST_ORDINAL, new SynthesisDoc(THE_TITLE, "Both [1] and [2].", sent(10, 11))));

        Path withNone = ListedArrangement.writeTo(
                workingDirectory, provenance(workingDirectory), arrangement, written, members, SurvivorPictures.none());

        claim(
                "the page written with an empty source of pictures is exactly the page stated here -- the"
                        + " heading, the writing with its citations as links, the list heading and one line"
                        + " per document -- with no image, no not-shown line and no blank line added for"
                        + " pictures that are not there",
                () -> assertThat(Files.readString(withNone.resolve(THE_PARTITION_DIRECTORY).resolve(THE_CLUSTER_PAGE)))
                        .isEqualTo(THE_PAGE_WITH_NO_PICTURES));
        claim(
                "and the group's folder holds the page and no picture, there being nothing to put in it",
                () -> assertThat(allFilesUnder(theClusterFolderOf(withNone)))
                        .containsExactly(theClusterFolderOf(withNone).resolve("index.html")));
    }

    @Test
    @Story("A picture repeated as a near-copy is furniture and is left out")
    @DisplayName("Two crops of one emblem, two pixels apart in size and two bits apart, are left out of both documents")
    @Issue("286")
    @Link(name = "ADR-150", url = Adr.A_PDFS_PICTURES_ARE_ASKED_FOR_AS_EMBEDDED_PIXELS, type = "adr")
    void leavesOutANearCopyTwoDocumentsCarry(@TempDir Path workingDirectory) throws IOException {
        byte[] logo = aPictureHashing(A_LOGO, 100, 50);
        byte[] theSameLogoCroppedElsewhere = aPictureHashing(A_LOGO ^ bitsFlipped(NEAR_COPY_BITS), 100 + NEAR_COPY_PIXELS, 50 + NEAR_COPY_PIXELS);
        byte[] diagram = aPictureHashing(A_DIAGRAM, 400, 240);
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(
                new OccurrenceId(10), List.of(png(logo), png(diagram)),
                new OccurrenceId(20), List.of(png(theSameLogoCroppedElsewhere)));

        Path tree = writeOneCluster(
                workingDirectory,
                null,
                List.of(aMember(10, "reports/v1.pdf", A_HIGH_SCORE), aMember(20, "reports/v2.pdf", A_MIDDLE_SCORE)),
                pictures);
        List<String> written = shownAnywhere(tree);

        claim(
                "the emblem and its crop from the other document are different bytes, so recurrence of bytes"
                        + " cannot see them; being exactly " + NEAR_COPY_PIXELS + " pixels apart in width and in"
                        + " height and exactly " + NEAR_COPY_BITS + " bits apart in their hash, both are left"
                        + " out: each limit is included",
                () -> assertThat(written)
                        .doesNotContain(shown(logo), shown(theSameLogoCroppedElsewhere)));
        claim(
                "while the diagram, like neither of them, is still written",
                () -> assertThat(written).containsExactly(shown(diagram)));
    }

    @Test
    @Story("A picture repeated as a near-copy is furniture and is left out")
    @DisplayName("Pictures one bit too far apart, or a pixel too different in size, are both still shown")
    @Issue("286")
    @Link(name = "ADR-150", url = Adr.A_PDFS_PICTURES_ARE_ASKED_FOR_AS_EMBEDDED_PIXELS, type = "adr")
    void keepsPicturesJustOutsideTheNearCopyRule(@TempDir Path workingDirectory) throws IOException {
        byte[] banner = aPictureHashing(A_LOGO, 100, 50);
        byte[] aBannerWithOtherText = aPictureHashing(A_LOGO ^ bitsFlipped(NEAR_COPY_BITS + 1), 100, 50);
        byte[] theBannerAtAnotherSize = aPictureHashing(A_LOGO, 100 + NEAR_COPY_PIXELS + 1, 50);
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(
                new OccurrenceId(10), List.of(png(banner)),
                new OccurrenceId(20), List.of(png(aBannerWithOtherText)),
                new OccurrenceId(30), List.of(png(theBannerAtAnotherSize)));

        Path tree = writeOneCluster(
                workingDirectory,
                null,
                List.of(
                        aMember(10, "reports/a.docx", A_HIGH_SCORE),
                        aMember(20, "reports/b.docx", A_MIDDLE_SCORE),
                        aMember(30, "reports/c.docx", A_LOW_SCORE)),
                pictures);
        List<String> written = shownAnywhere(tree);

        claim(
                "two pictures of one size whose hashes differ in " + (NEAR_COPY_BITS + 1) + " bits are both"
                        + " shown: heading banners with different text were measured that close, and they"
                        + " are content",
                () -> assertThat(written).contains(shown(banner), shown(aBannerWithOtherText)));
        claim(
                "and a picture with the same hash but " + (NEAR_COPY_PIXELS + 1) + " pixels wider is written"
                        + " too: both conditions have to hold for a near-copy",
                () -> assertThat(written).contains(shown(theBannerAtAnotherSize)));
    }

    @Test
    @Story("A picture repeated at one place in its document is furniture and is left out")
    @DisplayName("A header picture cropped at the same place on two pages is left out, although its crops differ, and so is a footer stamp at the limit")
    @Issue("286")
    @Link(name = "ADR-150", url = Adr.A_PDFS_PICTURES_ARE_ASKED_FOR_AS_EMBEDDED_PIXELS, type = "adr")
    void leavesOutAPictureRepeatedAtOnePlace(@TempDir Path workingDirectory) throws IOException {
        byte[] headerOnPageOne = aPictureHashing(A_LOGO, 290, 70);
        byte[] headerOnPageTwo = aPictureHashing(A_LOGO ^ bitsFlipped(SAME_PLACE_BITS_SEEN), 290, 70);
        byte[] chart = aPictureHashing(A_DIAGRAM, 400, 240);
        byte[] footerOnPageOne = aPictureHashing(A_STAMP, 200, 60);
        byte[] footerOnPageThree = aPictureHashing(A_STAMP ^ bitsFlipped(SAME_PLACE_BITS), 200, 60);
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(new OccurrenceId(10), List.of(
                placed(headerOnPageOne, 1, THE_HEADER_FRAME, 0.0),
                placed(chart, 1, A_BODY_FRAME, 0.0),
                placed(footerOnPageOne, 1, A_FOOTER_FRAME, 0.0),
                placed(headerOnPageTwo, 2, THE_HEADER_FRAME, SAME_PLACE_POINTS),
                placed(footerOnPageThree, 3, A_FOOTER_FRAME, SAME_PLACE_POINTS)));

        Path tree = writeOneCluster(
                workingDirectory, null, List.of(aMember(10, "reports/spec.pdf", A_HIGH_SCORE)), pictures);
        List<String> written = shownAnywhere(tree);

        claim(
                "the two crops of the header are " + SAME_PLACE_BITS_SEEN + " bits apart, too far for a"
                        + " near-copy, and sit " + SAME_PLACE_POINTS + " points apart on different pages of one"
                        + " document: that is page furniture, and both are left out",
                () -> assertThat(written)
                        .doesNotContain(shown(headerOnPageOne), shown(headerOnPageTwo)));
        claim(
                "two footer stamps exactly " + SAME_PLACE_BITS + " bits apart, each edge exactly "
                        + SAME_PLACE_POINTS + " points apart, on pages one and three, are left out too: each limit"
                        + " is included",
                () -> assertThat(written)
                        .doesNotContain(shown(footerOnPageOne), shown(footerOnPageThree)));
        claim(
                "while the chart on the first page, somewhere else on it, is written",
                () -> assertThat(written).containsExactly(shown(chart)));
    }

    @Test
    @Story("A picture repeated at one place in its document is furniture and is left out")
    @DisplayName("Different charts in one frame, a picture a little elsewhere, and two on the same page are all still shown")
    @Issue("286")
    @Link(name = "ADR-150", url = Adr.A_PDFS_PICTURES_ARE_ASKED_FOR_AS_EMBEDDED_PIXELS, type = "adr")
    void keepsPicturesJustOutsideTheSamePlaceRule(@TempDir Path workingDirectory) throws IOException {
        byte[] chartOnPageOne = aPictureHashing(A_CHART, 300, 200);
        byte[] anotherChartOnPageTwo = aPictureHashing(A_CHART ^ bitsFlipped(SAME_PLACE_BITS + 1), 300, 200);
        byte[] iconOnPageOne = aPictureHashing(AN_ICON, 300, 200);
        byte[] iconSlightlyElsewhereOnPageThree = aPictureHashing(AN_ICON ^ bitsFlipped(SAME_PLACE_BITS_SEEN), 300, 200);
        byte[] stampOnPageFour = aPictureHashing(A_STAMP, 300, 200);
        byte[] stampAgainOnPageFour = aPictureHashing(A_STAMP ^ bitsFlipped(SAME_PLACE_BITS_SEEN), 300, 200);
        Map<OccurrenceId, List<ListedPicture>> pictures = Map.of(new OccurrenceId(10), List.of(
                placed(chartOnPageOne, 1, A_BODY_FRAME, 0.0),
                placed(anotherChartOnPageTwo, 2, A_BODY_FRAME, 0.0),
                placed(iconOnPageOne, 1, THE_HEADER_FRAME, 0.0),
                placed(iconSlightlyElsewhereOnPageThree, 3, THE_HEADER_FRAME, SAME_PLACE_POINTS + 1),
                placed(stampOnPageFour, 4, A_FOOTER_FRAME, 0.0),
                placed(stampAgainOnPageFour, 4, A_FOOTER_FRAME, 0.0)));

        Path tree = writeOneCluster(
                workingDirectory, null, List.of(aMember(10, "reports/report.pdf", A_HIGH_SCORE)), pictures);
        List<String> written = shownAnywhere(tree);

        claim(
                "two charts in the same frame on two pages, " + (SAME_PLACE_BITS + 1) + " bits apart, are both"
                        + " written: a report that draws one chart per page in one frame keeps its charts",
                () -> assertThat(written).contains(shown(chartOnPageOne), shown(anotherChartOnPageTwo)));
        claim(
                "two pictures alike enough, but " + (SAME_PLACE_POINTS + 1) + " points apart, are both written",
                () -> assertThat(written)
                        .contains(shown(iconOnPageOne), shown(iconSlightlyElsewhereOnPageThree)));
        claim(
                "and two alike at the same place on the same page are both written: the rule is about a"
                        + " page repeating what another page has",
                () -> assertThat(written).contains(shown(stampOnPageFour), shown(stampAgainOnPageFour)));
    }

    /** One cluster holding {@code members}, arranged at their count, written over by {@code doc} or not. */
    private static Path writeOneCluster(
            Path workingDirectory,
            SynthesisDoc doc,
            List<ListedSurvivor> members,
            Map<OccurrenceId, List<ListedPicture>> pictures) {
        List<ListedDoc> written =
                doc == null ? List.of() : List.of(new ListedDoc(THE_SEED, FIRST_ORDINAL, doc));
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
            List<ListedDoc> written,
            List<ListedSurvivor> members,
            Map<OccurrenceId, List<ListedPicture>> pictures) {
        Map<OccurrenceId, List<ListedPicture>> source = new HashMap<>(pictures);
        return ListedArrangement.writeTo(
                workingDirectory,
                provenance(workingDirectory),
                arrangement,
                written,
                members,
                survivor -> source.getOrDefault(survivor.occurrence(), List.of()));
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

    /** Every image source on {@code page}, in the order the page gives them. */
    private static List<String> sourcesOn(Path page) throws IOException {
        Matcher image = AN_IMAGE.matcher(Files.readString(page));
        List<String> found = new ArrayList<>();
        while (image.find()) {
            found.add(image.group(1));
        }
        return List.copyOf(found);
    }

    /** Every image source on every page of {@code tree}, page by page. */
    private static List<String> shownAnywhere(Path tree) throws IOException {
        List<String> found = new ArrayList<>();
        for (Path file : allFilesUnder(tree)) {
            if (file.getFileName().toString().endsWith(".html")) {
                found.addAll(sourcesOn(file));
            }
        }
        return List.copyOf(found);
    }

    private static List<Path> allFilesUnder(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).toList();
        }
    }

    /** How a page shows PNG {@code pixels}: their media type and their bytes in base64, as an image's source. */
    private static String shown(byte[] pixels) {
        return "data:" + PNG + ";base64," + Base64.getEncoder().encodeToString(pixels);
    }

    /** Bytes that begin as a PNG does and then are {@code text}: of the kind they declare, decoding to no image. */
    private static byte[] pixels(String text) {
        return withSignature(PNG_SIGNATURE, text);
    }

    private static byte[] withSignature(byte[] signature, String text) {
        byte[] tail = text.getBytes(StandardCharsets.UTF_8);
        byte[] whole = Arrays.copyOf(signature, signature.length + tail.length);
        System.arraycopy(tail, 0, whole, signature.length, tail.length);
        return whole;
    }

    private static ListedPicture png(byte[] pixels) {
        return new ListedPicture(PNG, pixels, false, "");
    }

    /**
     * A PNG picture on {@code page}, in {@code frame}'s edges with each moved by {@code shift} points, as
     * Docling moves a crop of one header picture from page to page.
     */
    private static ListedPicture placed(byte[] pixels, int page, ListedPicturePlace frame, double shift) {
        return new ListedPicture(
                PNG,
                pixels,
                false,
                "",
                Optional.of(new ListedPicturePlace(
                        page,
                        frame.left() + shift,
                        frame.top() + shift,
                        frame.right() + shift,
                        frame.bottom() + shift)));
    }

    /** The folder of {@code tree}'s one group, which holds its page. */
    private static Path theClusterFolderOf(Path tree) {
        return tree.resolve(THE_PARTITION_DIRECTORY).resolve(THE_CLUSTER_FOLDER);
    }

    /**
     * A real PNG, {@code width} by {@code height}, whose difference hash (ADR-150 §3) is exactly
     * {@code bits}. It is grey, drawn as the hash's own 9-by-8 grid with each pixel given its cell's
     * level, so every cell's mean is that level. Each row starts at 100 and steps up 10 for a set bit and
     * down 10 for a clear one, so it stays between 20 and 180.
     */
    private static byte[] aPictureHashing(long bits, int width, int height) {
        int[][] levels = new int[HASH_ROWS][HASH_COLUMNS];
        for (int row = 0; row < HASH_ROWS; row++) {
            int level = 100;
            levels[row][0] = level;
            for (int column = 0; column < HASH_COLUMNS - 1; column++) {
                boolean rising = ((bits >>> (63 - (row * (HASH_COLUMNS - 1) + column))) & 1L) == 1L;
                level += rising ? 10 : -10;
                levels[row][column + 1] = level;
            }
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int level = levels[y * HASH_ROWS / height][x * HASH_COLUMNS / width];
                image.setRGB(x, y, (level << 16) | (level << 8) | level);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** A mask of the lowest {@code count} bits, to flip that many bits of a hash. */
    private static long bitsFlipped(int count) {
        return (1L << count) - 1;
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
