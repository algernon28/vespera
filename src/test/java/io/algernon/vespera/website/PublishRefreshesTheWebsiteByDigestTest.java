package io.algernon.vespera.website;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.ListedTree.cluster;
import static io.algernon.vespera.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.deliverable.ListedTree.survivor;
import static io.algernon.vespera.deliverable.ListedTree.writing;
import static io.algernon.vespera.website.WebsiteFixture.DAMPERS_FOLDER;
import static io.algernon.vespera.website.WebsiteFixture.DOORS_FOLDER;
import static io.algernon.vespera.website.WebsiteFixture.DOORS_LABEL;
import static io.algernon.vespera.website.WebsiteFixture.DOORS_TITLE;
import static io.algernon.vespera.website.WebsiteFixture.THE_SEED;
import static io.algernon.vespera.website.WebsiteFixture.VESPERA_WEBSITE_JSON;
import static io.algernon.vespera.website.WebsiteFixture.WEBSITE;
import static io.algernon.vespera.website.WebsiteFixture.filesUnder;
import static io.algernon.vespera.website.WebsiteFixture.listedIn;
import static io.algernon.vespera.website.WebsiteFixture.renderingOf;
import static io.algernon.vespera.website.WebsiteFixture.sha256;
import static io.algernon.vespera.website.WebsiteFixture.survivors;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.deliverable.DeliverableRoot;
import io.algernon.vespera.deliverable.ListedCodeSets;
import io.algernon.vespera.deliverable.ListedSurvivor;
import io.algernon.vespera.deliverable.Plugin;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code vespera publish --to website} refreshes one folder, {@code <working directory>/website/}, by digest (ADR-333
 * §7): a file standing with the digest {@code files.json} names is not written; one that does not is, through its
 * {@code .part}; a file its own previous publish listed, which the list no longer names and which still hashes to what
 * was listed, is removed, a folder so emptied with it; one the operator changed is kept and counted; a file the
 * operator put there is never touched. A source not at the size and digest its list names stops the delivery, copies
 * having gone first and {@code index.html} last, and {@code vespera-website.json}, written first with both lists,
 * lets the next publish go on from there and remove what the stopped one wrote and no list keeps.
 *
 * <p>Each rendering is drawn in a folder of its own and published to one working directory, as the last drawing of
 * each generation run is. The counts a closing line must give are worked out here from the two lists and the files
 * that stand, so a claim says what each file was and not merely a number.
 *
 * <p>The closing line is the record's, its counts read whether one is {@code file} or {@code files}, and its folder
 * the website folder as the publish was handed its working directory.
 */
@Epic("Operation")
@Feature("The website")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
class PublishRefreshesTheWebsiteByDigestTest {

    private static final Pattern REFRESHED = Pattern.compile(
            "vespera publish refreshed the website at (.+): (\\d+) files? written, (\\d+) standing, (\\d+) removed,"
                    + " (\\d+) changed by hand and kept\\. Open (.+), or run vespera serve\\.");

    /** A time no publish writes a file at, set on every file before a publish that should write none. */
    private static final FileTime LONG_AGO = FileTime.from(Instant.parse("2001-02-03T04:05:06Z"));

    @TempDir
    Path base;

    @Test
    @Story("The website is refreshed by digest")
    @DisplayName("A first publish writes every file the list names, and a second, the rendering unchanged, writes none")
    void aFirstPublishWritesEverythingAndASecondNothing() throws IOException {
        Path tree = WebsiteFixture.drawn(base.resolve("a"));
        Path workingDirectory = Files.createDirectories(base.resolve("work"));
        Path website = workingDirectory.resolve(WEBSITE);
        Map<String, WebsiteFixture.Listed> listed = listedIn(renderingOf(tree));

        Plugin.Delivery first = WebsiteFixture.published(tree, workingDirectory);

        claim(
                "the publish delivered, its closing line naming the website folder and counting every listed file"
                        + " written",
                () -> {
                    assertThat(first.delivered()).as(first.closingLine()).isTrue();
                    assertThat(countsOf(first, website)).containsExactly((long) listed.size(), 0L, 0L, 0L);
                });
        claim(
                "the website holds each listed file at its path, hashing to its sha256, its marker and its own list,"
                        + " and nothing else",
                () -> {
                    assertThat(filesUnder(website).keySet()).containsExactlyInAnyOrderElementsOf(withItsOwnFiles(listed));
                    for (WebsiteFixture.Listed entry : listed.values()) {
                        assertThat(sha256(website.resolve(entry.path()))).as(entry.path()).isEqualTo(entry.sha256());
                    }
                });

        for (String name : listed.keySet()) {
            Files.setLastModifiedTime(website.resolve(name), LONG_AGO);
        }
        Plugin.Delivery second = WebsiteFixture.published(tree, workingDirectory);

        claim(
                "a second publish of the same rendering writes nothing: every file stands",
                () -> {
                    assertThat(second.delivered()).as(second.closingLine()).isTrue();
                    assertThat(countsOf(second, website)).containsExactly(0L, (long) listed.size(), 0L, 0L);
                });
        claim(
                "and no listed file was touched, each keeping the time set on it before",
                () -> assertThat(listed.keySet()).allSatisfy(name -> assertThat(
                                Files.getLastModifiedTime(website.resolve(name)))
                        .as(name)
                        .isEqualTo(LONG_AGO)));
    }

    @Test
    @Story("The website is refreshed by digest")
    @DisplayName("A changed source is written, a file the list drops is removed with the folder it empties, a listed file changed by hand is kept and counted, and the operator's own files stay")
    void aChangedSourceIsWrittenADroppedFileRemovedAndTheOperatorsKept() throws IOException {
        Path earlier = WebsiteFixture.drawn(base.resolve("a"));
        Path workingDirectory = Files.createDirectories(base.resolve("work"));
        Path website = workingDirectory.resolve(WEBSITE);
        Plugin.Delivery firstPublish = WebsiteFixture.published(earlier, workingDirectory);
        Map<String, WebsiteFixture.Listed> before = listedIn(renderingOf(earlier));

        Path operatorsNote = website.resolve("notes of my own.txt");
        Path operatorsFileBesideAPage = website.resolve(DOORS_FOLDER).resolve("my own.txt");
        Files.writeString(operatorsNote, "the operator's");
        Files.writeString(operatorsFileBesideAPage, "the operator's too");
        String changedByHand = DOORS_FOLDER + "/inspection-log.docx";
        Files.writeString(website.resolve(changedByHand), "changed by the operator's hand");

        RecordedCluster doorsOfTwo = cluster(THE_SEED, 0, DOORS_LABEL, 1, 2);
        List<ListedSurvivor> twoDoors = survivors().stream()
                .filter(survivor -> survivor.occurrence().value() == 10 || survivor.occurrence().value() == 12)
                .toList();
        Path later = WebsiteFixture.drawn(base.resolve("c"), List.of(doorsOfTwo),
                Map.of(slotOf(doorsOfTwo), writing(DOORS_TITLE, "Doors are rehung [1] once a decade.", 10)),
                twoDoors, ListedCodeSets.NONE);
        Map<String, WebsiteFixture.Listed> after = listedIn(renderingOf(later));
        Set<String> changed = new TreeSet<>();
        Set<String> standing = new TreeSet<>();
        after.forEach((path, entry) -> (before.containsKey(path) && before.get(path).sha256().equals(entry.sha256())
                ? standing : changed).add(path));
        Set<String> dropped = new TreeSet<>(before.keySet());
        dropped.removeAll(after.keySet());
        Set<String> removed = new TreeSet<>(dropped);
        removed.remove(changedByHand);

        Plugin.Delivery publish = WebsiteFixture.published(later, workingDirectory);

        claim(
                "the first publish delivered, and the second rendering drops the dampers' page and two copies and"
                        + " changes the doors' page, so each count below counts something",
                () -> {
                    assertThat(firstPublish.delivered()).isTrue();
                    assertThat(dropped).contains(DAMPERS_FOLDER + "/index.html", changedByHand,
                            DAMPERS_FOLDER + "/damper #2.png");
                    assertThat(changed).contains(DOORS_FOLDER + "/index.html");
                    assertThat(standing).contains(DOORS_FOLDER + "/door survey.docx");
                });
        claim(
                "the publish delivered, writing what changed, leaving what stands, removing what was dropped and"
                        + " keeping the one file changed by hand",
                () -> {
                    assertThat(publish.delivered()).as(publish.closingLine()).isTrue();
                    assertThat(countsOf(publish, website)).containsExactly(
                            (long) changed.size(), (long) standing.size(), (long) removed.size(), 1L);
                });
        claim(
                "every file the new list names stands at its digest",
                () -> assertThat(after.values()).allSatisfy(entry -> assertThat(sha256(website.resolve(entry.path())))
                        .as(entry.path())
                        .isEqualTo(entry.sha256())));
        claim(
                "each dropped file is gone, and the dampers' folder, emptied, is gone with it",
                () -> {
                    assertThat(removed).allSatisfy(path -> assertThat(website.resolve(path)).as(path).doesNotExist());
                    assertThat(website.resolve(DAMPERS_FOLDER)).doesNotExist();
                });
        claim(
                "the listed file the operator changed is kept as they left it",
                () -> assertThat(website.resolve(changedByHand)).hasContent("changed by the operator's hand"));
        claim(
                "and the operator's own files, at the website's top and beside a page, are as they put them",
                () -> {
                    assertThat(operatorsNote).hasContent("the operator's");
                    assertThat(operatorsFileBesideAPage).hasContent("the operator's too");
                });
    }

    /** The paths {@code listed} names, with the website's marker and its own list. */
    private static List<String> withItsOwnFiles(Map<String, WebsiteFixture.Listed> listed) {
        List<String> files = new ArrayList<>(listed.keySet());
        files.add(DeliverableRoot.MARKER_FILE_NAME);
        files.add(VESPERA_WEBSITE_JSON);
        return files;
    }

    /**
     * The four counts of {@code delivery}'s closing line, written, standing, removed and changed by hand, once the
     * line is held to name {@code website} as the folder refreshed and its index as the page to open.
     */
    private static List<Long> countsOf(Plugin.Delivery delivery, Path website) {
        Matcher line = REFRESHED.matcher(delivery.closingLine());
        assertThat(line.matches()).as("the closing line %s", delivery.closingLine()).isTrue();
        assertThat(line.group(1)).isEqualTo(website.toString());
        assertThat(line.group(6)).startsWith(website.toString()).endsWith("index.html");
        return List.of(Long.parseLong(line.group(2)), Long.parseLong(line.group(3)), Long.parseLong(line.group(4)),
                Long.parseLong(line.group(5)));
    }
}
