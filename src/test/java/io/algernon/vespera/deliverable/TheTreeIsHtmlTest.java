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
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The tree stage 6b leaves is HTML and nothing else, every page standing alone, every value escaped and the writing
 * rendered on its group's page (ADR-272 §1). {@code TheWritingIsRenderedFromASubsetOfMarkdownTest} holds the
 * renderer's subset.
 *
 * <p>Written over an archive that is not there, so every copy is {@code gone}: what this class claims is about the
 * pages, which are written whatever became of the copies. {@code EverySurvivorIsCopiedTest} holds the copies.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-272", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
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

    /** A scheme opening a {@code src} or {@code href}, or a host after {@code //}: a page reaching off itself. */
    private static final Pattern A_LINK_OFF_THE_TREE =
            Pattern.compile("(?i)\\b(?:src|href)\\s*=\\s*\"(?:[a-z][a-z0-9+.-]*:|//)");

    /** A citation as the model wrote it, standing anywhere but as the text of the link it became. */
    private static final Pattern A_RAW_CITATION = Pattern.compile("(?<!>)\\[\\d+\\](?!</a>)");

    @Test
    @Story("The tree is a set of web pages")
    @DisplayName("The tree is an HTML index and one HTML page for each group, with the listing beside them, and no Markdown")
    void writesAnHtmlIndexAndAnHtmlPageForEachClusterAndNoMarkdown(@TempDir Path base) throws IOException {
        Path tree = theFixtureWritten(base);

        claim(
                "the pages of the tree are exactly index.html at its root and one page for each of its groups, in"
                        + " the partition's folder, named by the group's place and its label",
                () -> assertThat(filesUnder(tree).stream().filter(name -> name.endsWith(".html")))
                        .containsExactly(
                                PARTITION_FOLDER + "/1-fire-doors.html",
                                PARTITION_FOLDER + "/2-smoke-dampers.html",
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
    @DisplayName("Every page carries its own styling and no script, and links to nothing outside the tree")
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
                    name + " runs no script, loads no stylesheet from elsewhere, and no source or link on it names a"
                            + " scheme or a host: it opens offline, carried anywhere",
                    () -> {
                        assertThat(page).doesNotContain("<script", "<link");
                        assertThat(A_LINK_OFF_THE_TREE.matcher(page).find()).isFalse();
                    });
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
        ListedPicture captioned = new ListedPicture("image/png", new byte[] {1, 2, 3}, false, "Caption " + FIVE_HOSTILE);

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
        String page = Files.readString(tree.resolve(PARTITION_FOLDER + "/1-fire-doors.html"), StandardCharsets.UTF_8);

        claim(
                "the writing is on the page escaped whole and rendered as its two paragraphs, with both citations as"
                        + " links to the entries they name, inside the block that holds the writing",
                () -> {
                    assertThat(page).contains(PROSE_ON_THE_PAGE);
                    assertThat(page.indexOf("<div class=\"writing\">"))
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
        String page = Files.readString(tree.resolve(PARTITION_FOLDER + "/2-smoke-dampers.html"), StandardCharsets.UTF_8);
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
    @DisplayName("A long label and a long seed name give a folder and a page named by forty characters of slug")
    void cutsEverySlugToFortyCharacters(@TempDir Path base) throws IOException {
        String longSeed = "seeds/Building Regulations Approved Document B Fire Safety Volume 2.pdf";
        RecordedCluster longLabel =
                cluster(THE_SEED, 0, "Annual inspection of every fire door in the north and south stairwells", 1, 1);
        ListedSurvivor member = survivor(10, "reports/a.docx", "0".repeat(64), THE_SEED, longSeed, 0, 0.9);

        Path tree = ListedTree.write(
                base.resolve("work"), base.resolve("archive"), List.of(longLabel), Map.of(), List.of(member), Map.of());

        claim(
                "the folder is the partition's place and the first " + SLUG_LENGTH + " characters of its seed's slug,"
                        + " and the page the group's place and the first " + SLUG_LENGTH + " of its label's, less the"
                        + " hyphen the cut left at its end",
                () -> assertThat(filesUnder(tree).stream().filter(name -> name.endsWith(".html")))
                        .containsExactly(
                                "1-building-regulations-approved-document-b/1-annual-inspection-of-every-fire-door-in.html",
                                "index.html"));
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

    /** The one page beneath {@code tree} that is not the index. */
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
