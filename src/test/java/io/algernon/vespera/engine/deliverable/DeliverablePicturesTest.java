package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.synthesis.ArrangedCluster;
import io.algernon.vespera.engine.synthesis.ClusterLabel;
import io.algernon.vespera.engine.synthesis.ListedDoc;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A survivor's pictures in the tree (ADR-149, #285): which of them are furniture and left out, how each
 * one kept is shown under its document's membership entry, by its own bytes as a base64 source on its
 * image (ADR-286 §1), what its alt text is, and how many a document may show.
 *
 * <p>Written against the writer with the pictures handed in by hand, as {@code ClusterPageLinksAndWordingTest} is:
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

    /** The cluster identity 6a minted for the first cluster, and for the second. */
    private static final int FIRST_ORDINAL = 0;

    private static final int SECOND_ORDINAL = 1;

    /** The first and second places in an order, which both levels count from (ADR-112). */
    private static final int FIRST_PLACE = 1;

    private static final int SECOND_PLACE = 2;

    /** Scores that put the members of a cluster in a known order. */
    private static final double A_HIGH_SCORE = 0.9;

    /** The media type every picture in the measured cache carried. */
    private static final String PNG = "image/png";

    /** What every PNG begins with. */
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

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
