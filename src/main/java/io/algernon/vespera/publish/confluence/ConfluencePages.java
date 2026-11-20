package io.algernon.vespera.publish.confluence;

import io.algernon.vespera.engine.deliverable.ClusterPage;
import io.algernon.vespera.engine.deliverable.Copies;
import io.algernon.vespera.engine.deliverable.Deliverable;
import io.algernon.vespera.engine.deliverable.Drawing;
import io.algernon.vespera.engine.deliverable.Entries;
import io.algernon.vespera.engine.deliverable.FixedWords;
import io.algernon.vespera.engine.deliverable.Html;
import io.algernon.vespera.engine.deliverable.IndexPage;
import io.algernon.vespera.engine.deliverable.ListedOverview;
import io.algernon.vespera.engine.deliverable.ListedSubtopic;
import io.algernon.vespera.engine.deliverable.ListedTopic;
import io.algernon.vespera.engine.deliverable.ListedSurvivor;
import io.algernon.vespera.engine.deliverable.SeeAlsoLine;
import io.algernon.vespera.engine.deliverable.TopicOverviewWording;
import io.algernon.vespera.engine.ledger.ReplacingMove;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.OverviewUnwritten;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import io.algernon.vespera.engine.synthesis.Unwritten;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Confluence rendering, drawn under {@value #DIRECTORY_NAME}{@code /} at the tree's root by every drawing beside
 * the HTML tree (ADR-301, ADR-312), and its page list, {@value #PAGE_LIST_FILE_NAME} (ADR-316).
 *
 * <p><b>It follows the HTML tree's shape</b> (ADR-312 §1): the root page's body and the page list directly in {@code
 * confluence/}, a folder for each topic, within it one for each subtopic of a crowded topic, and within that, or within
 * the topic's, one for each cluster, named as the HTML tree names them (ADR-415 §7),
 * each holding its page's body, {@value #BODY_NAME} or {@code page-<k>.xml} for page k of a split cluster, and its
 * pictures' files (ADR-314). A copy is not written again: the page list names it at its place in the HTML tree
 * (ADR-316 §2).
 *
 * <p><b>It is appended to as the HTML tree is</b> (ADR-223 §6): a cluster's pages are written as its HTML pages are, a
 * topic's page when its last cluster is drawn, and the root page and the page list last, once {@code
 * documents.csv} stands (ADR-315 §3). What is held is one topic's rows; the root page's body and the page
 * list's entries after the root's are written to their {@code .part} files as they come.
 *
 * <p><b>Nothing names the corpus root, the working directory or a profile value</b> (ADR-315 §1, ADR-316 §2), and
 * every page's title is ADR-310's. Every body, file and the page list is written through {@code .part} and moved into
 * place.
 */
final class ConfluencePages implements Drawing {

    /** Where the rendering lies, at the tree's root (ADR-312 §1). */
    static final String DIRECTORY_NAME = Publisher.RENDERING_FOLDER;

    /** The page list, directly in {@code confluence/} (ADR-316 §1). */
    static final String PAGE_LIST_FILE_NAME = Publisher.PAGE_LIST_FILE_NAME;

    /** A page's storage body, or a split cluster's first page's. */
    static final String BODY_NAME = "page.xml";

    /** The page list's format (ADR-316 §2, ADR-328 §3). */
    private static final int FORMAT = 3;

    /** How many hexadecimal characters of the run's id its marker is (ADR-310 §1); the page list names it, no page does. */
    private static final int MARKER_LENGTH = 12;

    /** The root page's one sentence (ADR-315 §2). */
    static final String THE_ROOT_PAGES_SENTENCE = FixedWords.rootSentence(DeliverableLanguage.ENGLISH);

    /** What the page list's entries after the root page's are held in while the drawing runs. */
    private static final String REST_OF_THE_LIST = PAGE_LIST_FILE_NAME + ".rest" + Deliverable.PART_SUFFIX;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Path directory;
    private final String runId;
    private final String marker;
    private final ReplacingMove moves;
    private final DeliverableLanguage language;
    private final Set<String> written;
    private final Writer root;
    private final Writer rest;

    /** The topic being drawn, and its rows, held until its last cluster is drawn. */
    private ListedTopic topicDrawn;
    private int topicCount;
    private String topicKey;
    private String topicPlace;
    private String topicTitle;
    private String topicFolder;
    private ListedOverview overview;
    private List<ListedSubtopic> subtopics = List.of();

    /** What a crowded topic's subtopic has drawn: its rows, and its clusters' pages listed beneath its own. */
    private final class SubtopicDrawn {
        final ListedSubtopic subtopic;
        final String key;
        final String title;
        final String folder;
        final StringBuilder pageRows = new StringBuilder();
        final StringBuilder indexRows = new StringBuilder();
        final List<ListedPage> pages = new ArrayList<>();
        int clusters;
        long documents;

        SubtopicDrawn(ListedSubtopic subtopic) {
            this.subtopic = subtopic;
            this.key = topicKey + "." + subtopic.subtopicOrder();
            this.title = ConfluenceTitles.subtopic(
                    subtopic.name(),
                    topicPlace + "." + IndexPage.pad(subtopic.subtopicOrder(), IndexPage.widthOf(subtopics.size())),
                    language);
            this.folder = IndexPage.subtopicDirectoryName(subtopic, subtopics.size());
        }
    }

    private final Map<Integer, SubtopicDrawn> subtopicsDrawn = new LinkedHashMap<>();

    /** The rows of an undivided topic's table, and its clusters' pages. */
    private final StringBuilder topicPageRows = new StringBuilder();
    private final StringBuilder topicIndexRows = new StringBuilder();
    private final List<ListedPage> clusterPages = new ArrayList<>();

    /** The title of the first page of the cluster at each place of the topic's one sequence, which its overview cites. */
    private final Map<Integer, String> firstPageTitles = new HashMap<>();

    /** The topic's see-also lines, as list items, held until its page is written. */
    private final StringBuilder seeAlsoItems = new StringBuilder();

    /**
     * Opens the rendering in {@code tree}, writing the root page's opening, and adds every folder it writes to {@code
     * written}, by its path from the tree's root, so ADR-280 §4's count reads them as drawn (ADR-312 §4).
     */
    ConfluencePages(Path tree, String runId, ReplacingMove moves, Set<String> written, DeliverableLanguage language)
            throws IOException {
        this.directory = tree.resolve(DIRECTORY_NAME);
        this.runId = runId;
        this.marker = runId.substring(0, Math.min(MARKER_LENGTH, runId.length()));
        this.moves = moves;
        this.language = language;
        this.written = written;
        Files.createDirectories(directory);
        written.add(DIRECTORY_NAME);
        this.root = Files.newBufferedWriter(directory.resolve(BODY_NAME + Deliverable.PART_SUFFIX), StandardCharsets.UTF_8);
        this.rest = Files.newBufferedWriter(directory.resolve(REST_OF_THE_LIST), StandardCharsets.UTF_8);
        root.append("<h1>").append(FixedWords.theDeliverable(language).strip()).append("</h1>\n<p>")
                .append(Html.escape(FixedWords.rootSentence(language))).append("</p>\n");
    }

    /** An {@code ac:link} to the page titled {@code title}, reading {@code text}, which it escapes (ADR-309). */
    static String link(String title, String text) {
        return "<ac:link><ri:page ri:content-title=\"" + Html.escape(title) + "\" /><ac:link-body>" + Html.escape(text)
                + "</ac:link-body></ac:link>";
    }

    /** Starts a topic's page with no subtopic and no overview, which a drawing that is handed neither is left to answer. */
    @Override
    public void topicStarted(ListedTopic topic, int topicCount) throws IOException {
        topicStarted(topic, topicCount, List.of(), new ListedOverview.Unwritten(OverviewUnwritten.NOT_REACHED));
    }

    /**
     * Starts a topic's page: its folder, and a folder for each of its subtopics, named as the HTML tree's (ADR-312 §1,
     * ADR-415 §7).
     */
    @Override
    public void topicStarted(
            ListedTopic topic, int topicCount, List<ListedSubtopic> subtopics, ListedOverview overview)
            throws IOException {
        String place = IndexPage.pad(topic.topicOrder(), IndexPage.widthOf(topicCount));
        this.topicDrawn = topic;
        this.topicCount = topicCount;
        this.topicFolder = IndexPage.topicDirectoryName(topic, topicCount);
        this.topicKey = language.code() + ":" + topic.topicOrder();
        this.topicPlace = place;
        this.topicTitle = ConfluenceTitles.topic(topic.name(), place, language);
        this.overview = overview;
        this.subtopics = List.copyOf(subtopics);
        Files.createDirectories(directory.resolve(topicFolder));
        written.add(DIRECTORY_NAME + "/" + topicFolder);
        subtopicsDrawn.clear();
        for (ListedSubtopic subtopic : this.subtopics) {
            SubtopicDrawn drawn = new SubtopicDrawn(subtopic);
            subtopicsDrawn.put(subtopic.subtopic(), drawn);
            Files.createDirectories(directory.resolve(topicFolder).resolve(drawn.folder));
            written.add(DIRECTORY_NAME + "/" + topicFolder + "/" + drawn.folder);
        }
        topicPageRows.setLength(0);
        topicIndexRows.setLength(0);
        seeAlsoItems.setLength(0);
        firstPageTitles.clear();
        clusterPages.clear();
    }

    /** The subtopic {@code recorded} lies in, null where its topic has none. */
    private SubtopicDrawn subtopicOf(RecordedCluster recorded) {
        if (recorded.cluster().subtopic().isEmpty()) {
            return null;
        }
        SubtopicDrawn drawn = subtopicsDrawn.get(recorded.cluster().subtopic().getAsInt());
        if (drawn == null) {
            throw new IllegalStateException("a cluster lies in subtopic " + recorded.cluster().subtopic().getAsInt()
                    + ", which the topic's subtopics do not include");
        }
        return drawn;
    }

    /** The folder of {@code recorded} beneath {@code confluence/}: in its subtopic's where it has one. */
    private String clusterFolderPath(ListedTopic topic, RecordedCluster recorded) {
        SubtopicDrawn within = subtopicOf(recorded);
        return topicFolder + (within == null ? "" : "/" + within.folder) + "/"
                + IndexPage.clusterFolderName(recorded, IndexPage.widthOf(topic.clusterCount()));
    }

    /**
     * Opens one cluster's folder under {@code confluence/} and the entries its HTML page written whole writes for it too
     * ({@link ConfluenceEntries}).
     *
     * @param clusterFolder the cluster's folder in the HTML tree, where its copies stand
     */
    @Override
    public Entries entriesOf(
            ListedTopic topic, RecordedCluster recorded, List<ListedSurvivor> members, Path clusterFolder,
            Copies copies)
            throws IOException {
        String folderPath = clusterFolderPath(topic, recorded);
        Path folder = directory.resolve(folderPath);
        Files.createDirectories(folder);
        written.add(DIRECTORY_NAME + "/" + folderPath);
        return new ConfluenceEntries(folder, folderPath, clusterFolder, copies, members, moves, language);
    }

    /**
     * Writes one cluster's page, or pages where {@code breaks} splits its HTML page (ADR-303), from {@code entries}, which
     * it closes and deletes, and its row of its topic's table and the root page's.
     */
    @Override
    public void clusterDrawn(
            ListedTopic topic,
            RecordedCluster recorded,
            Optional<SynthesisDoc> doc,
            Unwritten why,
            int entryCount,
            Optional<ClusterPage.PageBreaks> breaks,
            Entries handedBack,
            boolean writingFinished)
            throws IOException {
        // The entries this drawing's own entriesOf returned (ADR-333 §2).
        ConfluenceEntries entries = (ConfluenceEntries) handedBack;
        entries.close();
        int clusterWidth = IndexPage.widthOf(topic.clusterCount());
        SubtopicDrawn within = subtopicOf(recorded);
        String folderPath = clusterFolderPath(topic, recorded);
        Path folder = directory.resolve(folderPath);
        String parentKey = within == null ? topicKey : within.key;
        String place = (within == null
                        ? topicPlace
                        : topicPlace + "."
                                + IndexPage.pad(within.subtopic.subtopicOrder(), IndexPage.widthOf(subtopics.size())))
                + "." + IndexPage.pad(recorded.cluster().clusterOrder(), clusterWidth);
        String text = doc.map(SynthesisDoc::title).orElse(recorded.label().value());
        int pages = breaks.map(ClusterPage.PageBreaks::pages).orElse(1);
        String firstKey = parentKey + "." + recorded.cluster().clusterOrder();
        List<ListedPage> listedPages = within == null ? clusterPages : within.pages;
        for (int page = 1; page <= pages; page++) {
            String body = page == 1 ? BODY_NAME : "page-" + page + ".xml";
            Path part = folder.resolve(body + Deliverable.PART_SUFFIX);
            List<PageFile> files;
            try (OutputStream out = Files.newOutputStream(part)) {
                files = ConfluencePage.write(
                        out, recorded, doc.orElse(null), why, entryCount, entries, breaks.orElse(null), page,
                        k -> ConfluenceTitles.cluster(text, place, k, language), writingFinished, language);
            }
            String digest = sha256Of(part);
            moves.intoPlace(part, folder.resolve(body));
            listedPages.add(new ListedPage(
                    page == 1 ? firstKey : firstKey + "." + page,
                    page == 1 ? parentKey : firstKey,
                    ConfluenceTitles.cluster(text, place, page, language),
                    folderPath + "/" + body,
                    digest,
                    files));
        }
        Files.delete(entries.file());
        String firstPageTitle = ConfluenceTitles.cluster(text, place, 1, language);
        String clusterLink = link(firstPageTitle, recorded.label().value());
        String titleCell = doc.isEmpty()
                ? "<em>" + Html.escape(FixedWords.nothingWasWrittenOverIt(language)) + "</em>"
                : Html.escape(doc.get().title());
        String pageRow = "<tr><td>" + recorded.cluster().clusterOrder() + "</td><td>" + clusterLink + "</td><td>"
                + recorded.cluster().documentCount() + "</td><td>" + titleCell + "</td></tr>\n";
        String indexRow = "<tr><td>" + clusterLink + "</td><td>" + recorded.cluster().documentCount() + "</td><td>"
                + titleCell + "</td></tr>\n";
        firstPageTitles.put(recorded.cluster().clusterOrder(), firstPageTitle);
        if (within == null) {
            topicPageRows.append(pageRow);
            topicIndexRows.append(indexRow);
        } else {
            within.pageRows.append(pageRow);
            within.indexRows.append(indexRow);
            within.clusters++;
            within.documents += recorded.cluster().documentCount();
        }
    }

    /**
     * Holds one page of the topic's see-also lines until its page is written (ADR-415 §5, §7): each path an {@code
     * ac:link} to the title of its cluster's first page, followed by the name of the topic it is placed under.
     */
    @Override
    public void seeAlso(ListedTopic topic, List<SeeAlsoLine> lines) {
        for (SeeAlsoLine line : lines) {
            String place = IndexPage.pad(line.topic().topicOrder(), IndexPage.widthOf(topicCount))
                    + line.subtopic()
                            .map(subtopic -> "." + IndexPage.pad(subtopic.subtopicOrder(), IndexPage.widthOf(line.subtopicCount())))
                            .orElse("")
                    + "." + IndexPage.pad(
                            line.cluster().cluster().clusterOrder(), IndexPage.widthOf(line.topic().clusterCount()));
            String text = line.writingTitle().orElse(line.cluster().label().value());
            seeAlsoItems.append("<li>")
                    .append(link(ConfluenceTitles.cluster(text, place, 1, language), line.path().value()))
                    .append(" (")
                    .append(Html.escape(line.topic().name()))
                    .append(")</li>\n");
        }
    }

    /**
     * Writes the topic's page, with its overview above its table, its subtopics' pages and the root page's section for it,
     * then lists the topic's page, and beneath each parent its children's pages, parents first (ADR-316 §2, ADR-415 §7).
     */
    @Override
    public void topicEnded() throws IOException {
        boolean divided = !subtopics.isEmpty();
        StringBuilder body = new StringBuilder(overviewStorage());
        if (divided) {
            body.append("<table><tbody>\n").append(FixedWords.subtopicTableHeadRow(language));
            for (SubtopicDrawn drawn : subtopicsDrawn.values()) {
                body.append("<tr><td>").append(link(drawn.title, drawn.subtopic.name())).append("</td><td>")
                        .append(drawn.clusters).append("</td><td>").append(drawn.documents).append("</td></tr>\n");
            }
            body.append("</tbody></table>\n");
        } else if (topicDrawn.clusterCount() > 0) {
            // A listed topic no document is placed under has no table (ADR-429 section 8).
            body.append(clusterTable(FixedWords.topicTableHeadRow(language), topicPageRows));
        }
        if (seeAlsoItems.length() > 0) {
            body.append("<h2>").append(Html.escape(FixedWords.seeAlso(language))).append("</h2>\n<p>")
                    .append(Html.escape(FixedWords.seeAlsoSentence(language))).append("</p>\n<ul>\n")
                    .append(seeAlsoItems).append("</ul>\n");
        }
        String topicDigest = written(directory.resolve(topicFolder), body.toString());
        root.append("<h2>").append(link(topicTitle, topicDrawn.name())).append("</h2>\n");
        if (divided) {
            for (SubtopicDrawn drawn : subtopicsDrawn.values()) {
                root.append("<h3>").append(link(drawn.title, drawn.subtopic.name())).append("</h3>\n")
                        .append(clusterTable(FixedWords.clusterTableHeadRow(language), drawn.indexRows));
            }
        } else if (topicDrawn.clusterCount() > 0) {
            root.append(clusterTable(FixedWords.clusterTableHeadRow(language), topicIndexRows));
        }
        listed(new ListedPage(topicKey, rootKey(), topicTitle, topicFolder + "/" + BODY_NAME, topicDigest, List.of()));
        for (SubtopicDrawn drawn : subtopicsDrawn.values()) {
            String page = "<p>" + Html.escape(FixedWords.inTheTopic(language)) + " "
                    + link(topicTitle, topicDrawn.name()) + ".</p>\n"
                    + clusterTable(FixedWords.topicTableHeadRow(language), drawn.pageRows);
            String digest = written(directory.resolve(topicFolder).resolve(drawn.folder), page);
            listed(new ListedPage(
                    drawn.key, topicKey, drawn.title, topicFolder + "/" + drawn.folder + "/" + BODY_NAME, digest,
                    List.of()));
            for (ListedPage clusterPage : drawn.pages) {
                listed(clusterPage);
            }
        }
        for (ListedPage page : clusterPages) {
            listed(page);
        }
        clusterPages.clear();
        subtopicsDrawn.clear();
    }

    /** The topic's overview in storage format, or the sentence for its absence, and the disclosure beneath one (ADR-415 §2, §7). */
    private String overviewStorage() {
        return switch (overview) {
            case ListedOverview.Written written -> {
                StringBuilder text = new StringBuilder(
                        ProseStorage.ofAnOverview(written.writing(), firstPageTitles::get)).append('\n');
                int clusters = topicDrawn.clusterCount();
                if (written.clustersSent() < clusters) {
                    text.append("<p><em>")
                            .append(Html.escape(TopicOverviewWording.disclosure(language)
                                    .formatted(written.clustersSent(), clusters)))
                            .append("</em></p>\n");
                }
                yield text.toString();
            }
            case ListedOverview.Unwritten unwritten -> "<p><em>"
                    + Html.escape(TopicOverviewWording.of(unwritten.why(), language)) + "</em></p>\n";
        };
    }

    private static String clusterTable(String head, CharSequence rows) {
        return "<table><tbody>\n" + head + rows + "</tbody></table>\n";
    }

    /** Writes {@code body} as the page body of {@code folder} through its {@code .part}, and answers its digest. */
    private String written(Path folder, String body) throws IOException {
        Path part = folder.resolve(BODY_NAME + Deliverable.PART_SUFFIX);
        Files.writeString(part, body, StandardCharsets.UTF_8);
        String digest = sha256Of(part);
        moves.intoPlace(part, folder.resolve(BODY_NAME));
        return digest;
    }

    /**
     * Writes the root page, carrying {@code manifest} as a file (ADR-315 §3), and then the page list, once every page and
     * file it names is written (ADR-316 §1). The page list's held entries are let go.
     */
    @Override
    public void finish(Path manifest) throws IOException {
        root.append("<p>").append(Html.escape(FixedWords.madeWith(language))).append("</p>\n");
        root.close();
        rest.close();
        Path rootPart = directory.resolve(BODY_NAME + Deliverable.PART_SUFFIX);
        String rootDigest = sha256Of(rootPart);
        moves.intoPlace(rootPart, directory.resolve(BODY_NAME));
        ListedPage rootPage = new ListedPage(
                rootKey(),
                null,
                ConfluenceTitles.root(language),
                BODY_NAME,
                rootDigest,
                List.of(new PageFile(
                        Deliverable.MANIFEST_FILE_NAME, "../" + Deliverable.MANIFEST_FILE_NAME, PageFile.LISTING,
                        Files.size(manifest), sha256Of(manifest))));
        Path restPart = directory.resolve(REST_OF_THE_LIST);
        Path listPart = directory.resolve(PAGE_LIST_FILE_NAME + Deliverable.PART_SUFFIX);
        try (OutputStream list = Files.newOutputStream(listPart)) {
            list.write(("{\"format\":" + FORMAT + ",\"generationRun\":" + JSON.writeValueAsString(runId) + ",\"marker\":"
                    + JSON.writeValueAsString(marker) + ",\"language\":" + JSON.writeValueAsString(language.code())
                    + ",\"pages\":[\n" + JSON.writeValueAsString(rootPage))
                    .getBytes(StandardCharsets.UTF_8));
            Files.copy(restPart, list);
            list.write("\n]}\n".getBytes(StandardCharsets.UTF_8));
        }
        Files.delete(restPart);
        moves.intoPlace(listPart, directory.resolve(PAGE_LIST_FILE_NAME));
    }

    /** Closes what is still open, where the drawing failed before {@link #finish}; nothing is deleted. */
    @Override
    public void close() throws IOException {
        root.close();
        rest.close();
    }

    private String rootKey() {
        return language.code() + ":root";
    }

    private void listed(ListedPage page) throws IOException {
        rest.append(",\n").append(JSON.writeValueAsString(page));
    }

    /** The SHA-256 of {@code file}'s bytes, lower-case hexadecimal, read a buffer at a time. */
    static String sha256Of(Path file) throws IOException {
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), MessageDigest.getInstance("SHA-256"))) {
            in.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(((DigestInputStream) in).getMessageDigest().digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }
}
