package io.algernon.vespera.publish.website;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.deliverable.ListedTree.cluster;
import static io.algernon.vespera.engine.deliverable.ListedTree.sha256Of;
import static io.algernon.vespera.engine.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.engine.deliverable.ListedTree.survivor;
import static io.algernon.vespera.engine.deliverable.ListedTree.writing;
import static io.algernon.vespera.publish.website.WebsiteFixture.DAMPERS;
import static io.algernon.vespera.publish.website.WebsiteFixture.DAMPERS_FOLDER;
import static io.algernon.vespera.publish.website.WebsiteFixture.DOORS_FOLDER;
import static io.algernon.vespera.publish.website.WebsiteFixture.DOORS_LABEL;
import static io.algernon.vespera.publish.website.WebsiteFixture.DOORS_TITLE;
import static io.algernon.vespera.publish.website.WebsiteFixture.SEED_PATH;
import static io.algernon.vespera.publish.website.WebsiteFixture.THE_SEED;
import static io.algernon.vespera.publish.website.WebsiteFixture.VESPERA_WEBSITE_JSON;
import static io.algernon.vespera.publish.website.WebsiteFixture.WEBSITE;
import static io.algernon.vespera.publish.website.WebsiteFixture.WRITING;
import static io.algernon.vespera.publish.website.WebsiteFixture.bytes;
import static io.algernon.vespera.publish.website.WebsiteFixture.filesUnder;
import static io.algernon.vespera.publish.website.WebsiteFixture.listedIn;
import static io.algernon.vespera.publish.website.WebsiteFixture.renderingOf;
import static io.algernon.vespera.publish.website.WebsiteFixture.sha256;
import static io.algernon.vespera.publish.website.WebsiteFixture.survivors;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.ClusterPage;
import io.algernon.vespera.engine.deliverable.DeliverableRoot;
import io.algernon.vespera.engine.deliverable.ListedCodeSets;
import io.algernon.vespera.engine.deliverable.ListedSurvivor;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    private static final Pattern STOPPED = Pattern.compile(
            "vespera publish stopped the website: .+\\. (\\d+) of (\\d+) files? written\\. Publish again to go on from"
                    + " there\\.");

    /** A time no publish writes a file at, set on every file before a publish that should write none. */
    private static final FileTime LONG_AGO = FileTime.from(Instant.parse("2001-02-03T04:05:06Z"));

    /** A document only a later drawing lists, its copy written first by the publish of that drawing. */
    private static final String FRAME_CHECK = "reports/frame-check.docx";

    private static final byte[] FRAME_CHECK_BYTES = bytes("the frame check");

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

    @Test
    @Story("A publish stopped partway goes on from there")
    @DisplayName("A source unlike its list stops the publish after the copies, and the next publish goes on from there")
    void aStopMidwayResumes() throws IOException {
        Path workingDirectory = Files.createDirectories(base.resolve("work"));
        Path website = workingDirectory.resolve(WEBSITE);
        WebsiteFixture.published(WebsiteFixture.drawn(base.resolve("a")), workingDirectory);

        Path stopped = drawnWithTheFrameCheck(base.resolve("b"));
        byte[] unlikeItsList = theDoorsPageMadeUnlikeItsList(stopped);
        Plugin.Delivery stop = WebsiteFixture.published(stopped, workingDirectory);
        Matcher stopLine = STOPPED.matcher(stop.closingLine());

        claim(
                "the publish stopped, in the record's line, having written at least one file",
                () -> {
                    assertThat(stop.delivered()).isFalse();
                    assertThat(stopLine.matches()).as(stop.closingLine()).isTrue();
                    assertThat(Long.parseLong(stopLine.group(1))).isPositive()
                            .isLessThanOrEqualTo(Long.parseLong(stopLine.group(2)));
                });
        claim(
                "copies went first: the new document's copy stands, byte for byte",
                () -> assertThat(website.resolve(DOORS_FOLDER).resolve("frame-check.docx"))
                        .hasBinaryContent(FRAME_CHECK_BYTES));
        claim(
                "and the page unlike its list was not written",
                () -> assertThat(Files.readAllBytes(website.resolve(DOORS_FOLDER).resolve("index.html")))
                        .isNotEqualTo(unlikeItsList));

        Path redrawn = drawnWithTheFrameCheck(base.resolve("b-again"));
        Map<String, WebsiteFixture.Listed> listed = listedIn(renderingOf(redrawn));
        long toWrite = listed.values().stream().filter(entry -> !standsAsListed(website, entry)).count();
        Plugin.Delivery resumed = WebsiteFixture.published(redrawn, workingDirectory);

        claim(
                "the next publish, the rendering drawn again, delivers, writing only what does not stand yet: the copy"
                        + " written before the stop stands",
                () -> {
                    assertThat(resumed.delivered()).as(resumed.closingLine()).isTrue();
                    assertThat(countsOf(resumed, website))
                            .containsExactly(toWrite, listed.size() - toWrite, 0L, 0L);
                    assertThat(toWrite).isLessThan(listed.size());
                });
        claim(
                "and the website is the rendering's, its marker and its own list, and nothing else",
                () -> assertThat(filesUnder(website).keySet())
                        .containsExactlyInAnyOrderElementsOf(withItsOwnFiles(listed)));
    }

    @Test
    @Story("A publish stopped partway goes on from there")
    @DisplayName("What a stopped publish wrote and the next list does not name is removed by the next publish, leaving no orphan")
    void aStopMidwayLeavesNoOrphan() throws IOException {
        Path workingDirectory = Files.createDirectories(base.resolve("work"));
        Path website = workingDirectory.resolve(WEBSITE);
        WebsiteFixture.published(WebsiteFixture.drawn(base.resolve("a")), workingDirectory);
        Path stopped = drawnWithTheFrameCheck(base.resolve("b"));
        theDoorsPageMadeUnlikeItsList(stopped);
        Plugin.Delivery stop = WebsiteFixture.published(stopped, workingDirectory);

        Path asBefore = WebsiteFixture.drawn(base.resolve("a-again"));
        Map<String, WebsiteFixture.Listed> listed = listedIn(renderingOf(asBefore));
        Plugin.Delivery next = WebsiteFixture.published(asBefore, workingDirectory);

        claim(
                "the publish stopped after writing the new document's copy, which no later list names",
                () -> {
                    assertThat(stop.delivered()).isFalse();
                    assertThat(listed).doesNotContainKey(DOORS_FOLDER + "/frame-check.docx");
                });
        claim(
                "the next publish, of a rendering without it, delivers and counts it removed",
                () -> {
                    assertThat(next.delivered()).as(next.closingLine()).isTrue();
                    assertThat(countsOf(next, website).get(2)).isPositive();
                });
        claim(
                "so the copy the stopped publish wrote is gone: the website is the rendering's, its marker and its own"
                        + " list, and nothing else",
                () -> {
                    assertThat(website.resolve(DOORS_FOLDER).resolve("frame-check.docx")).doesNotExist();
                    assertThat(filesUnder(website).keySet())
                            .containsExactlyInAnyOrderElementsOf(withItsOwnFiles(listed));
                });
    }

    @Test
    @Story("The website is refreshed by digest")
    @DisplayName("A copy renamed by case alone is not removed where the file system takes the two names for one file: it stands, counted standing, and the page links it")
    void aFileRenamedByCaseAloneIsNotRemoved() throws IOException {
        aCopyRenamedToOneFileStands("Report.docx", "report.docx");
    }

    /**
     * {@code ΟΔΟΣ 1.docx} renamed to {@code ΟΔΟσ 1.docx}, its last Σ, U+03A3, made σ, U+03C3: lower-casing the whole
     * name makes that Σ the final ς, U+03C2, since it ends a word, and leaves the σ as it is, so a key by lower case
     * holds the two apart. ADR-280 §1.4's per-unit key, each UTF-16 unit upper-cased on its own as NTFS compares names,
     * gives both {@code ΟΔΟΣ 1.DOCX}, and NTFS takes them for one file. A comparison by lower case would remove the file
     * the page links.
     */
    @Test
    @Story("The website is refreshed by digest")
    @DisplayName("A copy renamed from Σ to σ at a word's end is not removed where the file system takes the two names for one file, as NTFS's per-unit upper case does: it stands, counted standing, and the page links it")
    void aFileRenamedByAFoldLowerCaseMissesIsNotRemoved() throws IOException {
        aCopyRenamedToOneFileStands("ΟΔΟΣ 1.docx", "ΟΔΟσ 1.docx");
    }

    /**
     * The doors' one copy, first listed as {@code firstName} and then, its bytes the same, as {@code renamedName}, is
     * held to stand after the second publish where the file system takes the two names for one file; skipped where it
     * tells them apart, as a probe folder holding {@code firstName} shows.
     */
    private void aCopyRenamedToOneFileStands(String firstName, String renamedName) throws IOException {
        Path probe = Files.createDirectories(base.resolve("name-probe"));
        Files.writeString(probe.resolve(firstName), "a");
        assumeTrue(Files.exists(probe.resolve(renamedName))
                        && Files.isSameFile(probe.resolve(firstName), probe.resolve(renamedName)),
                "the file system tells " + firstName + " and " + renamedName + " apart, so the rename is two files there");

        byte[] reportBytes = bytes("the report on the doors");
        String reportSha = sha256Of(reportBytes);
        String asFirstNamed = DOORS_FOLDER + "/" + firstName;
        String asRenamed = DOORS_FOLDER + "/" + renamedName;
        Path workingDirectory = Files.createDirectories(base.resolve("work"));
        Path website = workingDirectory.resolve(WEBSITE);

        Path earlier = drawnWithOneReport(base.resolve("a"), "reports/" + firstName, reportBytes);
        Map<String, WebsiteFixture.Listed> before = listedIn(renderingOf(earlier));
        Plugin.Delivery first = WebsiteFixture.published(earlier, workingDirectory);

        Path later = drawnWithOneReport(base.resolve("b"), "reports/" + renamedName, reportBytes);
        Map<String, WebsiteFixture.Listed> after = listedIn(renderingOf(later));
        Set<String> dropped = new TreeSet<>(before.keySet());
        dropped.removeAll(after.keySet());
        Set<String> standing = new TreeSet<>();
        after.forEach((path, entry) -> {
            if (path.equals(asRenamed)
                    || (before.containsKey(path) && before.get(path).sha256().equals(entry.sha256()))) {
                standing.add(path);
            }
        });

        Plugin.Delivery second = WebsiteFixture.published(later, workingDirectory);

        claim(
                "the first publish delivered; its list names the copy as " + firstName + ", the next as " + renamedName
                        + ", each with the same sha256, and that copy is the one path the next list drops",
                () -> {
                    assertThat(first.delivered()).as(first.closingLine()).isTrue();
                    assertThat(before).containsKey(asFirstNamed).doesNotContainKey(asRenamed);
                    assertThat(after).containsKey(asRenamed).doesNotContainKey(asFirstNamed);
                    assertThat(before.get(asFirstNamed).sha256()).isEqualTo(reportSha);
                    assertThat(after.get(asRenamed).sha256()).isEqualTo(reportSha);
                    assertThat(dropped).containsExactly(asFirstNamed);
                });
        claim(
                "the doors' page links the copy as " + renamedName,
                () -> assertThat(website.resolve(DOORS_FOLDER).resolve("index.html"))
                        .content(StandardCharsets.UTF_8)
                        .contains("href=\"./" + ClusterPage.percentEncoded(renamedName) + "\""));
        claim(
                "the file the page links still stands after the second publish, hashing to the copy's sha256",
                () -> {
                    assertThat(website.resolve(asRenamed)).as(asRenamed).isRegularFile();
                    assertThat(sha256(website.resolve(asRenamed))).as(asRenamed).isEqualTo(reportSha);
                });
        claim(
                "the second publish delivered, counting the renamed copy standing and nothing removed",
                () -> {
                    assertThat(second.delivered()).as(second.closingLine()).isTrue();
                    assertThat(countsOf(second, website)).containsExactly(
                            (long) (after.size() - standing.size()), (long) standing.size(), 0L, 0L);
                });
    }

    /**
     * The doors alone, holding one document at {@code archivePath} with {@code content}, cited by their writing, drawn
     * in {@code drawnIn}; its copy keeps the name it has in the archive (ADR-280).
     */
    private static Path drawnWithOneReport(Path drawnIn, String archivePath, byte[] content) throws IOException {
        Path archive = drawnIn.resolve("archive");
        Files.createDirectories(archive.resolve(archivePath).getParent());
        Files.write(archive.resolve(archivePath), content);
        RecordedCluster doorsOfOne = cluster(THE_SEED, 0, DOORS_LABEL, 1, 1);
        return WebsiteFixture.drawn(drawnIn, List.of(doorsOfOne),
                Map.of(slotOf(doorsOfOne), writing(DOORS_TITLE, "Doors are rehung [1] once a decade.", 14)),
                List.of(survivor(14, archivePath, sha256Of(content), THE_SEED, SEED_PATH, 0, 0.9)),
                ListedCodeSets.NONE);
    }

    /** The fixture with a fourth document among the doors, its writing changed, drawn in {@code drawnIn}. */
    private static Path drawnWithTheFrameCheck(Path drawnIn) throws IOException {
        Path archive = drawnIn.resolve("archive");
        Files.createDirectories(archive.resolve("reports"));
        Files.write(archive.resolve(FRAME_CHECK), FRAME_CHECK_BYTES);
        RecordedCluster doorsOfFour = cluster(THE_SEED, 0, DOORS_LABEL, 1, 4);
        List<ListedSurvivor> survivors = new ArrayList<>(survivors());
        survivors.add(3, survivor(13, FRAME_CHECK, sha256Of(FRAME_CHECK_BYTES), THE_SEED, SEED_PATH, 0, 0.65));
        return WebsiteFixture.drawn(drawnIn, List.of(doorsOfFour, DAMPERS),
                Map.of(slotOf(doorsOfFour), writing(DOORS_TITLE, WRITING + " Frames are checked [3].", 10, 11, 13)),
                survivors, ListedCodeSets.NONE);
    }

    /** Makes the doors' page of {@code tree}'s rendering unlike its list by a byte added, and gives its bytes. */
    private static byte[] theDoorsPageMadeUnlikeItsList(Path tree) throws IOException {
        Path page = renderingOf(tree).resolve(DOORS_FOLDER).resolve("index.html");
        Files.writeString(page, "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        return Files.readAllBytes(page);
    }

    /** Whether {@code entry}'s file stands in {@code website} hashing to its sha256. */
    private static boolean standsAsListed(Path website, WebsiteFixture.Listed entry) {
        try {
            Path file = website.resolve(entry.path());
            return Files.isRegularFile(file) && sha256(file).equals(entry.sha256());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
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
