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

    /** That writing as the page must carry it: escaped whole, two paragraphs, each citation a link. */
    private static final String PROSE_ON_THE_PAGE = "<p>First line <a href=\"#document-1\">[1]</a> &amp; &lt;more&gt;.</p>\n"
            + "<p>Indented <a href=\"#document-2\">[2]</a>, as written.</p>";

    /** The sentence ADR-174 gives a cluster none of whose documents could be sent, in its own words. */
    private static final String NO_SENDABLE_DOCUMENT = "Nothing was written over this group: none of its documents"
            + " could be read and sent to the writing model.";

    /** What the index's cell says of a cluster with no writing. */
    private static final String NOTHING_WAS_WRITTEN = "nothing was written over this group";

    /** Five characters, each of which the one escaping rule writes as an entity. */
    private static final String FIVE_HOSTILE = "<b>&\"'";

    /** Those five as the rule writes them. */
    private static final String FIVE_ESCAPED = "&lt;b&gt;&amp;&quot;&#39;";

    /** The pages the fixture's tree holds: the index, and one for each of its two groups. */
    private static final int PAGES_OF_THE_FIXTURE = 3;

    /** A slug's longest length, in characters, after the order prefix. */
    private static final int SLUG_LENGTH = 40;

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

    /** A citation as the model wrote it, standing anywhere but as the text of the link it became. */
    private static final Pattern A_RAW_CITATION = Pattern.compile("(?<!>)\\[\\d+\\](?!</a>)");

    @Test
    @Story("The tree is a set of web pages")
    @DisplayName("The tree is an HTML index and one HTML page for each group, with the listing beside them, and no Markdown")
    void writesAnHtmlIndexAndAnHtmlPageForEachClusterAndNoMarkdown(@TempDir Path base) throws IOException {
        Path tree = theFixtureWritten(base);

        claim(
                "the pages of the tree are exactly index.html at its root and one page for each of its groups,"
                        + " index.html in the group's folder in the partition's folder, the group's folder named by its"
                        + " place and its label",
                () -> assertThat(filesUnder(tree).stream().filter(name -> name.endsWith(".html")))
                        .containsExactly(
                                PARTITION_FOLDER + "/1-fire-doors/index.html",
                                PARTITION_FOLDER + "/2-smoke-dampers/index.html",
                                "index.html"));
        claim(
                "documents.csv is still at the root, for a program to load",
                () -> assertThat(tree.resolve("documents.csv")).isRegularFile());
        claim(
                "and no file anywhere in the tree is Markdown: nothing downstream reads it",
                () -> assertThat(filesUnder(tree)).noneMatch(name -> name.endsWith(".md")));
    }

    @Test
    @Story("The tree is a set of web pages")
    @DisplayName("Every page carries its own styling and no script, and loads no stylesheet from elsewhere")
    void everyPageStandsAlone(@TempDir Path base) throws IOException {
        Path tree = theFixtureWritten(base);
        List<String> pages = filesUnder(tree).stream().filter(name -> name.endsWith(".html")).toList();

        claim(
                "the " + PAGES_OF_THE_FIXTURE + " pages, the index and one for each group, are there to be read, so the"
                        + " claims below are about pages that exist",
                () -> assertThat(pages).hasSize(PAGES_OF_THE_FIXTURE));
        for (String name : pages) {
            String page = Files.readString(tree.resolve(name), StandardCharsets.UTF_8);
            claim(
                    name + " opens as an HTML document in English with its character set, and carries exactly one"
                            + " stylesheet of its own",
                    () -> {
                        assertThat(page).startsWith("<!DOCTYPE html>");
                        assertThat(page).contains("<html lang=\"en\">", "<meta charset=\"UTF-8\">");
                        assertThat(Pattern.compile("<style>").matcher(page).results().count()).isEqualTo(1);
                    });
            claim(
                    name + " runs no script and loads no stylesheet from elsewhere",
                    () -> assertThat(page).doesNotContain("<script", "<link"));
        }
    }

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
        Path tree = ListedTree.write(
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
                        : List.of());
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

    /**
     * ADR-303 §6 read through ADR-286 §5's test: a tree whose written group is split at a ceiling of one byte, a page an
     * entry, over an archive that holds every document. Its first page's citation of the second entry names the second
     * page and the anchor on it, and every page links the others of its group.
     */
    @Test
    @Story("The tree is a set of web pages")
    @DisplayName("On a tree whose group is split into pages, every link lands on its page, on a file inside the tree, or on another page of its group and an id on it")
    @Issue("571")
    @Link(name = "ADR-303", url = Adr.A_CLUSTERS_PAGE_PAST_THE_CEILING_IS_SPLIT_INTO_PAGES, type = "adr")
    void everyLinkOfASplitClusterLandsOnASiblingPageAndAnIdOnIt(@TempDir Path base) throws IOException {
        byte[] firstDocument = "the first document".getBytes(StandardCharsets.UTF_8);
        byte[] secondDocument = "the second document".getBytes(StandardCharsets.UTF_8);
        byte[] thirdDocument = "the third document".getBytes(StandardCharsets.UTF_8);
        Path archive = base.resolve("archive");
        Map<String, byte[]> originals = Map.of(
                "reports/doors a.docx", firstDocument,
                "reports/doors b.docx", secondDocument,
                "reports/dampers.pdf", thirdDocument);
        for (Map.Entry<String, byte[]> original : originals.entrySet()) {
            Path file = archive.resolve(original.getKey());
            Files.createDirectories(file.getParent());
            Files.write(file, original.getValue());
        }
        List<ListedSurvivor> survivors = List.of(
                survivor(10, "reports/doors a.docx", ListedTree.sha256Of(firstDocument), THE_SEED, SEED_PATH, 0, 0.9),
                survivor(11, "reports/doors b.docx", ListedTree.sha256Of(secondDocument), THE_SEED, SEED_PATH, 0, 0.8),
                survivor(12, "reports/dampers.pdf", ListedTree.sha256Of(thirdDocument), THE_SEED, SEED_PATH, 1, 0.7));
        Path tree = ListedTree.writeWithCeiling(
                base.resolve("work"),
                archive,
                List.of(WRITTEN, UNWRITTEN),
                Map.of(slotOf(WRITTEN), writing("Fire doors and their inspection", PROSE, 10, 11)),
                survivors,
                Map.of(slotOf(UNWRITTEN), Unwritten.NO_SENDABLE_DOCUMENT),
                SurvivorPictures.none(),
                new ClusterPageCeiling.Bytes(1),
                DeliverableProgress.NONE);
        List<String> imagesNamingAFile = new ArrayList<>();

        Set<String> kindsSeen = everySourceAndLinkOf(tree, imagesNamingAFile);

        claim(
                "the written group has two pages, a page an entry, and the other group, of one document, one",
                () -> assertThat(filesUnder(tree).stream().filter(name -> name.endsWith(".html")))
                        .containsExactly(
                                PARTITION_FOLDER + "/1-fire-doors/index-2.html",
                                PARTITION_FOLDER + "/1-fire-doors/index.html",
                                PARTITION_FOLDER + "/2-smoke-dampers/index.html",
                                "index.html"));
        claim(
                "a fragment on its page, a file in the tree and a page of the same group with an id on it were each met,"
                        + " so the claims above read each kind",
                () -> assertThat(kindsSeen).containsExactly(
                        "a file in the tree", "a fragment", "a fragment on another page of its group"));
        claim(
                "the first page's citation of the second entry names the second page and that entry's anchor",
                () -> assertThat(Files.readString(tree.resolve(PARTITION_FOLDER + "/1-fire-doors/index.html"), StandardCharsets.UTF_8))
                        .contains("<a href=\"./index-2.html#document-2\">[2]</a>"));
        Map<String, List<String>> copiesByFolder = new TreeMap<>();
        for (String copy : ListedTree.columnByPath(tree, "copy").values()) {
            copiesByFolder.computeIfAbsent(copy.substring(0, copy.lastIndexOf('/')), folder -> new ArrayList<>())
                    .add(copy.substring(copy.lastIndexOf('/') + 1));
        }
        Map<String, List<String>> pagesByFolder = Map.of(
                PARTITION_FOLDER + "/1-fire-doors", List.of("index.html", "index-2.html"),
                PARTITION_FOLDER + "/2-smoke-dampers", List.of("index.html"));
        for (Map.Entry<String, List<String>> folder : pagesByFolder.entrySet()) {
            List<String> expected = new ArrayList<>(copiesByFolder.getOrDefault(folder.getKey(), List.of()));
            expected.addAll(folder.getValue());
            claim(
                    "the group's folder " + folder.getKey() + " holds its pages and the copies the listing names in it,"
                            + " and nothing else",
                    () -> assertThat(filesUnder(tree.resolve(folder.getKey()))).containsExactlyInAnyOrderElementsOf(expected));
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

        Path tree = ListedTree.write(
                base.resolve("work"),
                base.resolve("archive"),
                List.of(hostile),
                Map.of(slotOf(hostile), writing("Title " + FIVE_HOSTILE, "Prose [1].", 10)),
                List.of(member),
                Map.of(),
                survivor -> List.of(captioned));
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

    @Test
    @Story("Nothing a document or the model wrote is read as markup")
    @DisplayName("The writing is on its group's page as HTML, each citation a link to its document")
    void rendersTheWritingOnItsPageWithEachCitationALink(@TempDir Path base) throws IOException {
        Path tree = theFixtureWritten(base);
        String page = Files.readString(tree.resolve(PARTITION_FOLDER + "/1-fire-doors/index.html"), StandardCharsets.UTF_8);

        claim(
                "the writing is on the page escaped whole and rendered as its two paragraphs, with both citations as"
                        + " links to the entries they name, inside the block that holds the writing",
                () -> {
                    assertThat(page).contains(PROSE_ON_THE_PAGE);
                    assertThat(page.indexOf("<div class=\"writing\" title=\""))
                            .isNotNegative()
                            .isLessThan(page.indexOf(PROSE_ON_THE_PAGE));
                });
        claim(
                "no citation is left as the model's own notation",
                () -> assertThat(A_RAW_CITATION.matcher(page).find()).isFalse());
        claim(
                "entry 1 is the first document the call sent and entry 2 the second, each carrying the id its"
                        + " citation links to, in that order down the page",
                () -> assertThat(List.of(
                                page.indexOf("id=\"document-1\""),
                                page.indexOf("reports/doors a.docx"),
                                page.indexOf("id=\"document-2\""),
                                page.indexOf("reports/doors b.docx")))
                        .doesNotContain(-1)
                        .isSorted());
    }

    @Test
    @Story("A group nothing was written over is a hole a reader can see")
    @DisplayName("A group nothing was written over says why in italics, and the index lists it with nothing to open")
    void saysWhyNothingWasWrittenInItalics(@TempDir Path base) throws IOException {
        Path tree = theFixtureWritten(base);
        String page = Files.readString(tree.resolve(PARTITION_FOLDER + "/2-smoke-dampers/index.html"), StandardCharsets.UTF_8);
        String row = theIndexRowNaming(tree, "Smoke dampers");

        claim(
                "the group's page carries the sentence for its reason in its own words, in italics",
                () -> assertThat(page).contains("<em>" + NO_SENDABLE_DOCUMENT + "</em>"));
        claim(
                "with none of the asterisks that were Markdown's italics",
                () -> assertThat(page).doesNotContain("*"));
        claim(
                "and its row of the index says nothing was written, in italics, and links to no page",
                () -> assertThat(row).contains("<em>" + NOTHING_WAS_WRITTEN + "</em>").doesNotContain("<a "));
    }

    @Test
    @Story("Every file of the tree has a name of bounded length")
    @DisplayName("A long label and a long seed name give folders named by forty characters of slug")
    void cutsEverySlugToFortyCharacters(@TempDir Path base) throws IOException {
        String longSeed = "seeds/Building Regulations Approved Document B Fire Safety Volume 2.pdf";
        RecordedCluster longLabel =
                cluster(THE_SEED, 0, "Annual inspection of every fire door in the north and south stairwells", 1, 1);
        ListedSurvivor member = survivor(10, "reports/a.docx", "0".repeat(64), THE_SEED, longSeed, 0, 0.9);

        Path tree = ListedTree.write(
                base.resolve("work"), base.resolve("archive"), List.of(longLabel), Map.of(), List.of(member), Map.of());

        claim(
                "the partition's folder is its place and the first " + SLUG_LENGTH + " characters of its seed's"
                        + " slug, and the group's folder, holding its page, its place and the first " + SLUG_LENGTH
                        + " of its label's, less the hyphen the cut left at its end",
                () -> assertThat(filesUnder(tree).stream().filter(name -> name.endsWith(".html")))
                        .containsExactly(
                                "1-building-regulations-approved-document-b/1-annual-inspection-of-every-fire-door-in"
                                        + "/index.html",
                                "index.html"));
    }

    /**
     * Reads every {@code src} and {@code href} of every page of {@code tree} and claims each lands where ADR-286 §5 lets
     * it, a link with a path and a fragment landing on a page of the same group whose ids hold it (ADR-303 §6); answers
     * the kinds met, and adds each image naming a file to {@code imagesNamingAFile}.
     */
    private static Set<String> everySourceAndLinkOf(Path tree, List<String> imagesNamingAFile) throws IOException {
        List<String> pages = filesUnder(tree).stream().filter(name -> name.endsWith(".html")).toList();
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

    /** The two clusters of one partition, the first written over, the second with no document it could send. */
    private static Path theFixtureWritten(Path base) {
        List<ListedSurvivor> survivors = List.of(
                survivor(10, "reports/doors a.docx", "0".repeat(64), THE_SEED, SEED_PATH, 0, 0.9),
                survivor(11, "reports/doors b.docx", "1".repeat(64), THE_SEED, SEED_PATH, 0, 0.8),
                survivor(12, "reports/dampers.pdf", "2".repeat(64), THE_SEED, SEED_PATH, 1, 0.7));
        return ListedTree.write(
                base.resolve("work"),
                base.resolve("archive"),
                List.of(WRITTEN, UNWRITTEN),
                Map.of(slotOf(WRITTEN), writing("Fire doors and their inspection", PROSE, 10, 11)),
                survivors,
                Map.of(slotOf(UNWRITTEN), Unwritten.NO_SENDABLE_DOCUMENT));
    }

    /** The one page beneath {@code tree} that is not the tree's own index. */
    private static Path theOnePageUnder(Path tree) throws IOException {
        List<String> pages = filesUnder(tree).stream()
                .filter(name -> name.endsWith(".html") && !name.equals("index.html"))
                .toList();
        if (pages.size() != 1) {
            throw new IllegalStateException("one group was arranged, and the tree holds pages " + pages);
        }
        return tree.resolve(pages.getFirst());
    }

    /** The one row of the index's tables naming {@code label}, from its {@code <tr>} to its {@code </tr>}. */
    private static String theIndexRowNaming(Path tree, String label) throws IOException {
        String index = Files.readString(tree.resolve("index.html"), StandardCharsets.UTF_8);
        int at = index.indexOf(label);
        if (at < 0) {
            throw new IllegalStateException("the index names no group " + label);
        }
        return index.substring(index.lastIndexOf("<tr", at), index.indexOf("</tr>", at));
    }
}
