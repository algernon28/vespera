package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.ListedTree.cluster;
import static io.algernon.vespera.deliverable.ListedTree.filesUnder;
import static io.algernon.vespera.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.deliverable.ListedTree.survivor;
import static io.algernon.vespera.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.algernon.vespera.synthesis.Unwritten;
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
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The tree stage 6b leaves is HTML and nothing else, every page standing alone, every value escaped and the writing
 * rendered on its group's page (ADR-274 §1). {@code TheWritingIsRenderedFromASubsetOfMarkdownTest} holds the
 * renderer's subset.
 *
 * <p>Written over an archive that is not there, so every copy is {@code gone}: what this class claims is about the
 * pages, which are written whatever became of the copies. {@code EverySurvivorIsCopiedTest} holds the copies. The one
 * test of every source and link (ADR-286 §5) draws over an archive that holds every document, so each link has a
 * copy to land on.
 *
 * <p><b>The HTML tree's pages alone</b> (ADR-333 §3): ADR-286 §5 holds of the HTML tree, and a plugin's rendering
 * beside it, under {@code website/} or {@code confluence/} at the tree's root, is held by its own plugin's tests. So
 * every tree here is drawn with two stand-in plugins named {@code website} and {@code confluence}, each drawing pages
 * of its own that load a script and a stylesheet and link out of the tree, and every claim that walks the tree's pages
 * reads the HTML tree's and leaves those two folders out: a walk that read them would fail here.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
@Link(name = "ADR-280", url = Adr.A_CLUSTERS_FOLDER_HOLDS_ITS_PAGE_AND_ITS_SURVIVORS_COPIES, type = "adr")
class TheTreeIsHtmlTest {

    /** The seed every cluster here sits under. */
    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    /** Its path, whose stem names the partition's folder. */
    private static final String SEED_PATH = "seeds/Fire Safety.docx";

    /** The partition's folder: its place, 1 of 1, and its seed's stem as a slug. */
    private static final String PARTITION_FOLDER = "1-fire-safety";

    /** The cluster written over, placed first. */
    private static final RecordedCluster WRITTEN = cluster(THE_SEED, 0, "Fire doors", 1, 2);

    /** The cluster nothing was written over, placed second. */
    private static final RecordedCluster UNWRITTEN = cluster(THE_SEED, 1, "Smoke dampers", 2, 1);

    /** The writing over the first cluster, its call having sent occurrences 10 and then 11. */
    private static final String PROSE = "First line [1] & <more>.\n\n  Indented [2], as written.";

    /** The sentence ADR-174 gives a cluster none of whose documents could be sent, in its own words. */
    private static final String NO_SENDABLE_DOCUMENT = "Nothing was written over this group: none of its documents"
            + " could be read and sent to the writing model.";

    /** Five characters, each of which the one escaping rule writes as an entity. */
    private static final String FIVE_HOSTILE = "<b>&\"'";

    /** Those five as the rule writes them. */
    private static final String FIVE_ESCAPED = "&lt;b&gt;&amp;&quot;&#39;";

    /** An element's tag, its name and its attributes, attribute values holding no {@code >} once escaped. */
    private static final Pattern A_TAG = Pattern.compile("<([a-zA-Z][a-zA-Z0-9]*)([^>]*)>");

    /** A {@code src} or an {@code href} among a tag's attributes, as its name and its value. */
    private static final Pattern A_SOURCE_OR_LINK = Pattern.compile("\\b(src|href)=\"([^\"]*)\"");

    /** The one scheme a page may name, on an image's source alone, and its payload in RFC 4648's padded alphabet. */
    private static final Pattern AN_INLINED_PICTURE = Pattern.compile(
            "data:(image/png|image/jpeg);base64,((?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?)");

    /** What every PNG begins with. */
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** What every JPEG begins with. */
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    /** The folders of the two renderings beside the HTML tree, which no claim here reads (ADR-333 §3). */
    private static final List<String> THE_PLUGINS_FOLDERS = List.of("confluence/", "website/");

    /**
     * Two stand-ins for the plugins, named as the two compiled into the jar are, each drawing pages no page of the
     * HTML tree may be: a script, a stylesheet loaded from elsewhere and a link out of the tree.
     */
    private static final List<Plugin> BESIDE_THE_TREE =
            List.of(new PagesOfItsOwn("confluence"), new PagesOfItsOwn("website"));

    /**
     * ADR-286 §5, the test of self-contained that replaces ADR-103's as ADR-135 bound it: every {@code src} and {@code
     * href} of every page lands on that page, on a file inside the tree, or is a picture inlined on an image's source,
     * and a group's folder holds its page and its copies alone. The tree is drawn over an archive that holds every
     * document, so each copy stands and each entry links one; one document carries a PNG and a JPEG picture, and one
     * is itself a PNG image whose name carries a space and a {@code #}.
     */
    @Test
    @Story("The tree is a set of web pages")
    @DisplayName("Every source and link on every page lands on that page or on a file inside the tree, or is a picture inlined on an image")
    @Issue("560")
    @Link(name = "ADR-286", url = Adr.A_CLUSTERS_PAGE_IS_ONE_FILE_ITS_PICTURES_INLINED, type = "adr")
    void everySourceAndLinkStaysOnItsPageOrInTheTree(@TempDir Path base) throws IOException {
        byte[] firstDocument = "the first document".getBytes(StandardCharsets.UTF_8);
        byte[] secondDocument = "the second document".getBytes(StandardCharsets.UTF_8);
        byte[] aDamperScan = ListedTree.anImage("png", 6, 4);
        Path archive = base.resolve("archive");
        Map<String, byte[]> originals = Map.of(
                "reports/doors a.docx", firstDocument,
                "reports/doors b.docx", secondDocument,
                "scans/damper #2.png", aDamperScan);
        for (Map.Entry<String, byte[]> original : originals.entrySet()) {
            Path file = archive.resolve(original.getKey());
            Files.createDirectories(file.getParent());
            Files.write(file, original.getValue());
        }
        List<ListedSurvivor> survivors = List.of(
                survivor(10, "reports/doors a.docx", ListedTree.sha256Of(firstDocument), THE_SEED, SEED_PATH, 0, 0.9),
                survivor(11, "reports/doors b.docx", ListedTree.sha256Of(secondDocument), THE_SEED, SEED_PATH, 0, 0.8),
                survivor(12, "scans/damper #2.png", ListedTree.sha256Of(aDamperScan), THE_SEED, SEED_PATH, 1, 0.7));
        Path tree = ListedTree.writeWithCeiling(
                base.resolve("work"),
                archive,
                List.of(WRITTEN, UNWRITTEN),
                Map.of(slotOf(WRITTEN), writing("Fire doors and their inspection", PROSE, 10, 11)),
                survivors,
                Map.of(slotOf(UNWRITTEN), Unwritten.NO_SENDABLE_DOCUMENT),
                survivor -> survivor.occurrence().value() == 10
                        ? List.of(
                                new ListedPicture("image/png", ListedTree.anImage("png", 5, 5), false, ""),
                                new ListedPicture("image/jpeg", ListedTree.anImage("jpg", 7, 3), false, ""))
                        : List.of(),
                new ClusterPageCeiling.Unset(),
                BESIDE_THE_TREE);
        List<String> imagesNamingAFile = new ArrayList<>();
        Set<String> kindsSeen = everySourceAndLinkOf(tree, imagesNamingAFile);

        claim(
                "every kind of source and link the tree has was met on its pages, so the claims above read each one",
                () -> assertThat(kindsSeen).containsExactly("a file in the tree", "a fragment", "an inlined picture"));
        claim(
                "and no image's source names a file",
                () -> assertThat(imagesNamingAFile).isEmpty());
        Map<String, List<String>> copiesByFolder = new TreeMap<>();
        for (String copy : ListedTree.columnByPath(tree, "copy").values()) {
            copiesByFolder.computeIfAbsent(copy.substring(0, copy.lastIndexOf('/')), folder -> new ArrayList<>())
                    .add(copy.substring(copy.lastIndexOf('/') + 1));
        }
        for (String folder : List.of(PARTITION_FOLDER + "/1-fire-doors", PARTITION_FOLDER + "/2-smoke-dampers")) {
            List<String> expected = new ArrayList<>(copiesByFolder.getOrDefault(folder, List.of()));
            expected.add("index.html");
            claim(
                    "the group's folder " + folder + " holds its page and the copies the listing names in it, and"
                            + " nothing else",
                    () -> assertThat(filesUnder(tree.resolve(folder))).containsExactlyInAnyOrderElementsOf(expected));
        }
    }

    @Test
    @Story("Nothing a document or the model wrote is read as markup")
    @DisplayName("A name, a heading, a path, a seed and a picture's caption carrying markup are shown as text, in text and in attributes")
    void escapesEveryValueInTextAndInAttributes(@TempDir Path base) throws IOException {
        String seedPath = "seeds/Seed " + FIVE_HOSTILE + ".docx";
        RecordedCluster hostile = cluster(THE_SEED, 0, "Label " + FIVE_HOSTILE, 1, 1);
        String path = "reports/Path " + FIVE_HOSTILE + ".docx";
        ListedSurvivor member = survivor(10, path, "0".repeat(64), THE_SEED, seedPath, 0, 0.9);
        ListedPicture captioned =
                new ListedPicture("image/png", ListedTree.anImage("png", 2, 2), false, "Caption " + FIVE_HOSTILE);

        Path tree = ListedTree.writeWithCeiling(
                base.resolve("work"),
                base.resolve("archive"),
                List.of(hostile),
                Map.of(slotOf(hostile), writing("Title " + FIVE_HOSTILE, "Prose [1].", 10)),
                List.of(member),
                Map.of(),
                survivor -> List.of(captioned),
                new ClusterPageCeiling.Unset(),
                BESIDE_THE_TREE);
        String index = Files.readString(tree.resolve("index.html"), StandardCharsets.UTF_8);
        String page = Files.readString(theOnePageUnder(tree), StandardCharsets.UTF_8);

        claim(
                "the index shows the label, the heading the model wrote and the seed's path with each of the five"
                        + " characters written as its entity",
                () -> assertThat(index).contains("Label " + FIVE_ESCAPED, "Title " + FIVE_ESCAPED, "Seed " + FIVE_ESCAPED));
        claim(
                "the group's page shows the heading and the document's path the same way",
                () -> assertThat(page).contains("Title " + FIVE_ESCAPED, "Path " + FIVE_ESCAPED));
        claim(
                "the picture's caption lands in its alt attribute escaped, so its quote cannot close the attribute",
                () -> assertThat(page).contains("alt=\"Caption " + FIVE_ESCAPED + "\""));
        claim(
                "and the tag those values carry is nowhere on either page as a tag",
                () -> {
                    assertThat(index).doesNotContain("<b>");
                    assertThat(page).doesNotContain("<b>");
                });
    }

    /**
     * Reads every {@code src} and {@code href} of every page of {@code tree} and claims each lands where ADR-286 §5 lets
     * it, a link with a path and a fragment landing on a page of the same group whose ids hold it (ADR-303 §6); answers
     * the kinds met, and adds each image naming a file to {@code imagesNamingAFile}.
     */
    private static Set<String> everySourceAndLinkOf(Path tree, List<String> imagesNamingAFile) throws IOException {
        List<String> pages = htmlTreePagesOf(tree);
        Set<String> kindsSeen = new TreeSet<>();

        for (String name : pages) {
            Path page = tree.resolve(name);
            String text = Files.readString(page, StandardCharsets.UTF_8);
            Matcher tag = A_TAG.matcher(text);
            while (tag.find()) {
                String element = tag.group(1);
                Matcher attribute = A_SOURCE_OR_LINK.matcher(tag.group(2));
                while (attribute.find()) {
                    String which = element + " " + attribute.group(1);
                    String value = attribute.group(2).replace("&amp;", "&");
                    if (value.startsWith("#")) {
                        kindsSeen.add("a fragment");
                        claim(
                                which + "=\"" + value + "\" on " + name + " names an id on the same page",
                                () -> assertThat(text).contains("id=\"" + value.substring(1) + "\""));
                    } else if (value.startsWith("data:")) {
                        kindsSeen.add("an inlined picture");
                        Matcher inlined = AN_INLINED_PICTURE.matcher(value);
                        claim(
                                "the data source on " + name + " is an image's source, of a PNG or a JPEG, its payload in"
                                        + " the standard alphabet, padded, with no line break, decoding to bytes"
                                        + " that begin with the signature of the kind it declares",
                                () -> {
                                    assertThat(which).isEqualTo("img src");
                                    assertThat(inlined.matches()).isTrue();
                                    byte[] decoded = Base64.getDecoder().decode(inlined.group(2));
                                    byte[] signature =
                                            inlined.group(1).equals("image/png") ? PNG_SIGNATURE : JPEG_SIGNATURE;
                                    assertThat(Arrays.copyOf(decoded, signature.length)).isEqualTo(signature);
                                });
                    } else {
                        kindsSeen.add("a file in the tree");
                        if (element.equals("img")) {
                            imagesNamingAFile.add(name + ": " + value);
                        }
                        URI destination = URI.create(value);
                        claim(
                                which + "=\"" + value + "\" on " + name + " names no scheme, is not rooted, and,"
                                        + " decoded and followed from the page's own folder, is a file inside the tree",
                                () -> {
                                    assertThat(destination.isAbsolute()).isFalse();
                                    assertThat(destination.getRawPath()).doesNotStartWith("/");
                                    Path landed = page.getParent().resolve(destination.getPath()).normalize();
                                    assertThat(landed.startsWith(tree.normalize())).isTrue();
                                    assertThat(landed).isRegularFile();
                                });
                        if (destination.getRawFragment() != null) {
                            kindsSeen.add("a fragment on another page of its group");
                            Path landed = page.getParent().resolve(destination.getPath()).normalize();
                            claim(
                                    which + "=\"" + value + "\" on " + name + " lands on a page in the same group's"
                                            + " folder, and its fragment names an id on that page",
                                    () -> {
                                        assertThat(landed.getParent()).isEqualTo(page.getParent());
                                        assertThat(Files.readString(landed, StandardCharsets.UTF_8))
                                                .contains("id=\"" + destination.getFragment() + "\"");
                                    });
                        }
                    }
                }
            }
        }

        return kindsSeen;
    }

    /**
     * The HTML tree's pages beneath {@code tree}, sorted: every {@code .html} file but those of the renderings beside
     * it, under {@code website/} and {@code confluence/} at its root (ADR-333 §3).
     */
    private static List<String> htmlTreePagesOf(Path tree) throws IOException {
        return filesUnder(tree).stream()
                .filter(name -> name.endsWith(".html"))
                .filter(name -> THE_PLUGINS_FOLDERS.stream().noneMatch(name::startsWith))
                .toList();
    }

    /** The one page of the HTML tree beneath {@code tree} that is not the tree's own index. */
    private static Path theOnePageUnder(Path tree) throws IOException {
        List<String> pages = htmlTreePagesOf(tree).stream()
                .filter(name -> !name.equals("index.html"))
                .toList();
        if (pages.size() != 1) {
            throw new IllegalStateException("one group was arranged, and the tree holds pages " + pages);
        }
        return tree.resolve(pages.getFirst());
    }

    /**
     * A stand-in for a plugin named {@code name}: at the end of the drawing it writes, beneath its own folder, an index
     * and a group's page that load a script and a stylesheet from elsewhere and link out of the tree, which no page of
     * the HTML tree may do, so that a claim reading them would fail.
     */
    private static final class PagesOfItsOwn implements Plugin {

        private final String name;

        PagesOfItsOwn(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String listName() {
            return "pages-of-its-own.json";
        }

        @Override
        public Drawing drawing(DrawingContext context) {
            context.written().add(name);
            Path folder = context.tree().resolve(name);
            return new Drawing() {
                @Override
                public void partitionStarted(ListedPartition partition, int partitionCount) {}

                @Override
                public Entries entriesOf(
                        ListedPartition partition, RecordedCluster recorded, List<ListedSurvivor> members,
                        Path clusterFolder, Copies copies) {
                    return Entries.NONE;
                }

                @Override
                public void clusterDrawn(
                        ListedPartition partition, RecordedCluster recorded, Optional<SynthesisDoc> doc, Unwritten why,
                        int entryCount, Optional<ClusterPage.PageBreaks> breaks, Entries entries,
                        boolean writingFinished) {}

                @Override
                public void partitionEnded() {}

                @Override
                public void finish(Path manifest) throws IOException {
                    String page = "<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"UTF-8\">"
                            + "<link rel=\"stylesheet\" href=\"https://example.invalid/its.css\">"
                            + "<script src=\"https://example.invalid/its.js\"></script><style></style><style></style>"
                            + "</head><body><a href=\"../../../outside-the-tree.html\">out</a>"
                            + "<img src=\"its-own-picture.png\"></body></html>\n";
                    Path cluster = Files.createDirectories(folder.resolve(PARTITION_FOLDER).resolve("1-fire-doors"));
                    Files.writeString(folder.resolve("index.html"), page, StandardCharsets.UTF_8);
                    Files.writeString(cluster.resolve("index.html"), page, StandardCharsets.UTF_8);
                }

                @Override
                public void close() {}
            };
        }

        @Override
        public Delivery deliver(
                Path rendering, String generationRun, Path workingDirectory, java.util.function.Consumer<String> out) {
            return new Delivery(false, name + " delivers nothing here");
        }
    }
}
