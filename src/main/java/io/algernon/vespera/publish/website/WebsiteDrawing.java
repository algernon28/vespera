package io.algernon.vespera.publish.website;

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
import io.algernon.vespera.engine.deliverable.TopicLayout;
import io.algernon.vespera.engine.deliverable.TopicPages;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.json.JsonMapper;

/**
 * The website rendering, drawn under {@value #DIRECTORY_NAME}{@code /} at the tree's root by every drawing beside the
 * HTML tree (ADR-333 §3, §4), and its list, {@value ListedFile#LIST_FILE_NAME}.
 *
 * <p><b>It follows the HTML tree's shape</b>: {@code index.html} at the root, and for each topic a folder and
 * within it for each cluster a folder, named as the HTML tree names them, holding its page, {@code index.html}, or its
 * pages, {@code index-<k>.html}, split where the HTML pages are (ADR-303). Beside the index stand {@value
 * #SEARCH_INDEX_FILE_NAME}, {@value #SCRIPT_FILE_NAME}, {@value #THEME_FILE_NAME}, the list and the state of the
 * drawing, which {@code Deliverable} writes. A copy is not written again: the list names it at its place in the HTML
 * tree (ADR-316 §2, ADR-333 §3). The code sets and {@code code.csv} are not on the website (ADR-333 §10).
 *
 * <p><b>It is appended to as the HTML tree is</b> (ADR-223 §6): a cluster's pages are written as its HTML pages are,
 * its row of the index and of the search's index as they come, and what is held is one cluster's entries. The index,
 * the search's index and the list are written to their {@code .part} files as they come and moved into place once
 * {@code documents.csv} stands; the list is moved last.
 *
 * <p><b>Nothing names the corpus root, the working directory, a profile value or the generation run</b>, so a page is
 * the same from one run to the next where its content is, and a publish writes only what changed (ADR-333 §7).
 */
final class WebsiteDrawing implements Drawing {

    /** Where the rendering lies, at the tree's root (ADR-333 §3). */
    static final String DIRECTORY_NAME = WebsitePlugin.NAME;

    /** The search's index: a script setting one global, never fetched (ADR-333 §3, §9). */
    static final String SEARCH_INDEX_FILE_NAME = "search-index.js";

    static final String SCRIPT_FILE_NAME = "website.js";

    static final String THEME_FILE_NAME = "website.css";

    /** The list's format. */
    private static final int FORMAT = 1;

    /** Where the script and the theme are shipped, on the class path. */
    private static final String SHIPPED = "/io/algernon/vespera/publish/website/";

    /** What the list's entries are held in while the drawing runs. */
    private static final String REST_OF_THE_LIST = ListedFile.LIST_FILE_NAME + ".rest" + Deliverable.PART_SUFFIX;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** One entry of the search's index (ADR-333 §9): the name of a document as its page shows it, and where it is. */
    record SearchEntry(String name, String link) {}

    /**
     * One cluster of the search's index (ADR-333 §9, ADR-415 §8): its topic's name and its subtopic's, which is empty
     * where its topic has none; the title and the writing are empty where nothing was written.
     */
    record SearchCluster(String topic, String subtopic, String title, String label, String writing, String link,
            List<SearchEntry> entries) {}

    /** What a page's content is written by, between the website's opening and its closing. */
    @FunctionalInterface
    private interface Content {
        void writeTo(Appendable page) throws IOException;
    }

    private final Path directory;
    private final ReplacingMove moves;
    private final DeliverableLanguage language;
    private final Set<String> written;
    private final Writer index;
    private final Writer search;
    private final Writer rest;
    private boolean firstSearchRow = true;
    private boolean firstListed = true;

    /** The topic being drawn: its folder, its name and what its page and its subtopics' pages are written from. */
    private ListedTopic topicDrawn;
    private int topicCount;
    private String topicFolder;
    private String topicName;
    private ListedOverview overview;
    private List<ListedSubtopic> subtopics = List.of();
    private final List<RecordedCluster> clustersDrawn = new ArrayList<>();
    private final List<TopicPages.Row> rows = new ArrayList<>();
    private final List<List<SeeAlsoLine>> seeAlsoPages = new ArrayList<>();
    private ListedSubtopic openSubtopic;

    /**
     * Opens the rendering in {@code tree}, writing the index's opening, and adds every folder it writes to {@code
     * written}, by its path from the tree's root, so ADR-280 §4's count reads them as drawn (ADR-312 §4).
     */
    WebsiteDrawing(Path tree, ReplacingMove moves, Set<String> written, DeliverableLanguage language)
            throws IOException {
        this.directory = tree.resolve(DIRECTORY_NAME);
        this.moves = moves;
        this.language = language;
        this.written = written;
        Files.createDirectories(directory);
        written.add(DIRECTORY_NAME);
        this.index = Files.newBufferedWriter(
                directory.resolve(Deliverable.INDEX_FILE_NAME + Deliverable.PART_SUFFIX), StandardCharsets.UTF_8);
        this.search = Files.newBufferedWriter(
                directory.resolve(SEARCH_INDEX_FILE_NAME + Deliverable.PART_SUFFIX), StandardCharsets.UTF_8);
        this.rest = Files.newBufferedWriter(directory.resolve(REST_OF_THE_LIST), StandardCharsets.UTF_8);
        String heading = FixedWords.theDeliverable(language).strip();
        index.append(WebsitePage.opening(heading, "", language))
                .append("<h1>").append(Html.escape(heading)).append("</h1>\n<p>")
                .append(Html.escape(FixedWords.rootSentence(language)))
                .append("</p>\n<p><a href=\"./").append(Deliverable.MANIFEST_FILE_NAME).append("\">")
                .append(Deliverable.MANIFEST_FILE_NAME).append("</a></p>\n");
        search.append("globalThis.VESPERA_SEARCH_INDEX = [\n");
    }

    /** Starts a topic with no subtopic and no overview, which a drawing that is handed neither is left to answer. */
    @Override
    public void topicStarted(ListedTopic topic, int topicCount) throws IOException {
        topicStarted(topic, topicCount, List.of(), new ListedOverview.Unwritten(OverviewUnwritten.NOT_REACHED));
    }

    /**
     * Starts a topic's heading in the index, linking its page, and its folder, and a folder for each subtopic, named as
     * the HTML tree's (ADR-415 §4, §8); the table of a topic with no subtopics is opened, a divided topic's subtopics'
     * as their first cluster comes.
     */
    @Override
    public void topicStarted(
            ListedTopic topic, int topicCount, List<ListedSubtopic> subtopics, ListedOverview overview)
            throws IOException {
        this.topicDrawn = topic;
        this.topicCount = topicCount;
        this.topicFolder = IndexPage.topicDirectoryName(topic, topicCount);
        this.topicName = topic.name();
        this.overview = overview;
        this.subtopics = List.copyOf(subtopics);
        this.openSubtopic = null;
        clustersDrawn.clear();
        rows.clear();
        seeAlsoPages.clear();
        Files.createDirectories(directory.resolve(topicFolder));
        written.add(DIRECTORY_NAME + "/" + topicFolder);
        for (ListedSubtopic subtopic : this.subtopics) {
            String folder = topicFolder + "/" + IndexPage.subtopicDirectoryName(subtopic, this.subtopics.size());
            Files.createDirectories(directory.resolve(folder));
            written.add(DIRECTORY_NAME + "/" + folder);
        }
        index.append("<h2><a href=\"").append(Html.escape(topicFolder + "/" + Deliverable.INDEX_FILE_NAME))
                .append("\">").append(Html.escape(topic.name())).append("</a></h2>\n");
        if (this.subtopics.isEmpty()) {
            index.append("<table>\n").append(FixedWords.clusterTableHeadRow(language));
        }
    }

    /** The subtopic {@code recorded} lies in, null where its topic has none. */
    private ListedSubtopic subtopicOf(RecordedCluster recorded) {
        if (recorded.cluster().subtopic().isEmpty()) {
            return null;
        }
        int number = recorded.cluster().subtopic().getAsInt();
        return subtopics.stream()
                .filter(subtopic -> subtopic.subtopic() == number)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "a cluster lies in subtopic " + number + ", which the topic's subtopics do not include"));
    }

    /** The folder of {@code subtopic} from the website's root. */
    private String subtopicFolderPath(ListedSubtopic subtopic) {
        return topicFolder + "/" + IndexPage.subtopicDirectoryName(subtopic, subtopics.size());
    }

    /**
     * Opens one cluster's folder under {@code website/} and the entries its HTML page written whole writes for it too
     * ({@link WebsiteEntries}).
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
        return new WebsiteEntries(folder, folderPath, clusterFolder, copies, language);
    }

    /**
     * Writes one cluster's page, or pages where {@code breaks} splits its HTML page (ADR-303), from {@code entries},
     * which it closes and deletes, then its row of the index and of the search's index.
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
        WebsiteEntries entries = (WebsiteEntries) handedBack;
        entries.close();
        ListedSubtopic within = subtopicOf(recorded);
        if (within != null && !within.equals(openSubtopic)) {
            if (openSubtopic != null) {
                index.append("</table>\n");
            }
            openSubtopic = within;
            index.append("<h3><a href=\"")
                    .append(Html.escape(subtopicFolderPath(within) + "/" + Deliverable.INDEX_FILE_NAME))
                    .append("\">").append(Html.escape(within.name())).append("</a></h3>\n<table>\n")
                    .append(FixedWords.clusterTableHeadRow(language));
        }
        String folderPath = clusterFolderPath(topic, recorded);
        Path folder = directory.resolve(folderPath);
        // The way up from a group's page to the website's root: its folder, its subtopic's where it has one, its topic's.
        String up = "../".repeat(within == null ? 2 : 3);
        int pages = breaks.map(ClusterPage.PageBreaks::pages).orElse(1);
        for (int page = 1; page <= pages; page++) {
            String name = ClusterPage.nameOfPage(page);
            Path part = folder.resolve(name + Deliverable.PART_SUFFIX);
            List<ListedFile> files;
            try (OutputStream out = Files.newOutputStream(part)) {
                files = WebsitePage.write(
                        out, recorded, doc.orElse(null), why, entryCount, entries, breaks.orElse(null), page,
                        writingFinished, up, language);
            }
            String digest = ListedFile.sha256Of(part);
            long size = Files.size(part);
            moves.intoPlace(part, folder.resolve(name));
            String path = folderPath + "/" + name;
            listed(new ListedFile(path, path, size, digest, ListedFile.PAGE));
            for (ListedFile file : files) {
                listed(file);
            }
        }
        List<SearchEntry> searched = new ArrayList<>();
        for (WebsiteEntries.Named named : entries.names()) {
            String page = breaks.map(found -> found.nameOfPageHolding(named.number())).filter(name -> !name.isEmpty())
                    .orElse(ClusterPage.nameOfPage(1));
            searched.add(new SearchEntry(
                    named.name(), folderPath + "/" + page + "#" + ClusterPage.ANCHOR_PREFIX + named.number()));
        }
        String link = folderPath + "/" + ClusterPage.nameOfPage(1);
        search.append(firstSearchRow ? "" : ",\n").append(JSON.writeValueAsString(new SearchCluster(
                topicName,
                within == null ? "" : within.name(),
                doc.map(SynthesisDoc::title).orElse(""),
                recorded.label().value(),
                doc.map(SynthesisDoc::prose).orElse(""),
                link,
                searched)));
        firstSearchRow = false;
        Files.delete(entries.file());
        index.append("<tr><td><a href=\"").append(Html.escape(link)).append("\">")
                .append(Html.escape(recorded.label().value())).append("</a></td><td>")
                .append(String.valueOf(recorded.cluster().documentCount())).append("</td><td>")
                .append(doc.isEmpty()
                        ? "<em>" + Html.escape(FixedWords.nothingWasWrittenOverIt(language)) + "</em>"
                        : Html.escape(doc.get().title()))
                .append("</td></tr>\n");
        clustersDrawn.add(recorded);
        rows.add(new TopicPages.Row(recorded, doc.map(SynthesisDoc::title)));
    }

    /** Holds one page of the topic's see-also lines until its page is written (ADR-415 §5, §8). */
    @Override
    public void seeAlso(ListedTopic topic, List<SeeAlsoLine> lines) {
        seeAlsoPages.add(List.copyOf(lines));
    }

    /**
     * Closes the table of the topic just drawn, and writes its page and each of its subtopics' under the website's
     * opening and closing, each listed as a page (ADR-415 §2, §3, §8).
     */
    @Override
    public void topicEnded() throws IOException {
        index.append("</table>\n");
        TopicLayout layout = new TopicLayout(topicDrawn, topicCount, subtopics, clustersDrawn);
        for (ListedSubtopic subtopic : subtopics) {
            List<TopicPages.Row> inIt = rows.stream()
                    .filter(row -> subtopic.equals(subtopicOf(row.recorded())))
                    .toList();
            writePage(subtopicFolderPath(subtopic), subtopic.name(), "../../",
                    page -> TopicPages.subtopicContent(page, layout, subtopic, inIt, language));
        }
        writePage(topicFolder, topicName, "../",
                page -> TopicPages.topicContent(
                        page, layout, overview, rows, language, handOn -> seeAlsoPages.forEach(handOn)));
    }

    /**
     * Writes the page {@code index.html} of {@code folder} under the website's opening and closing, {@code up} being the
     * way from it up to the website's root, and lists it as a page.
     */
    private void writePage(String folder, String title, String up, Content content) throws IOException {
        Path part = directory.resolve(folder).resolve(Deliverable.INDEX_FILE_NAME + Deliverable.PART_SUFFIX);
        try (Writer page = Files.newBufferedWriter(part, StandardCharsets.UTF_8)) {
            page.append(WebsitePage.opening(title, up, language));
            content.writeTo(page);
            page.append(WebsitePage.closing());
        }
        String digest = ListedFile.sha256Of(part);
        long size = Files.size(part);
        moves.intoPlace(part, directory.resolve(folder).resolve(Deliverable.INDEX_FILE_NAME));
        String path = folder + "/" + Deliverable.INDEX_FILE_NAME;
        listed(new ListedFile(path, path, size, digest, ListedFile.PAGE));
    }

    /**
     * Writes the index, the search's index, the script and the theme, lists them and {@code manifest}, {@code
     * documents.csv}, which the website carries beside its pages (ADR-333 §3), and then the list itself, once every file
     * it names is in place. The list's held entries are let go.
     */
    @Override
    public void finish(Path manifest) throws IOException {
        index.append(WebsitePage.closing());
        index.close();
        search.append("\n];\n");
        search.close();
        listed(inPlace(Deliverable.INDEX_FILE_NAME, ListedFile.PAGE));
        listed(inPlace(SEARCH_INDEX_FILE_NAME, ListedFile.ASSET));
        listed(shipped(SCRIPT_FILE_NAME));
        listed(shipped(THEME_FILE_NAME));
        listed(new ListedFile(
                Deliverable.MANIFEST_FILE_NAME,
                "../" + Deliverable.MANIFEST_FILE_NAME,
                Files.size(manifest),
                ListedFile.sha256Of(manifest),
                ListedFile.LISTING));
        rest.close();
        Path restPart = directory.resolve(REST_OF_THE_LIST);
        Path listPart = directory.resolve(ListedFile.LIST_FILE_NAME + Deliverable.PART_SUFFIX);
        try (OutputStream list = Files.newOutputStream(listPart)) {
            list.write(("{\"format\":" + FORMAT + ",\"files\":[\n").getBytes(StandardCharsets.UTF_8));
            Files.copy(restPart, list);
            list.write("\n]}\n".getBytes(StandardCharsets.UTF_8));
        }
        Files.delete(restPart);
        moves.intoPlace(listPart, directory.resolve(ListedFile.LIST_FILE_NAME));
    }

    /** Closes what is still open, where the drawing failed before {@link #finish}; nothing is deleted. */
    @Override
    public void close() throws IOException {
        index.close();
        search.close();
        rest.close();
    }

    /** The folder of {@code recorded} from the website's root: in its subtopic's where it has one, otherwise its topic's. */
    private String clusterFolderPath(ListedTopic topic, RecordedCluster recorded) {
        ListedSubtopic within = subtopicOf(recorded);
        return (within == null ? topicFolder : subtopicFolderPath(within)) + "/"
                + IndexPage.clusterFolderName(recorded, IndexPage.widthOf(topic.clusterCount()));
    }

    /** Moves the {@code .part} of {@code name} into place and lists it. */
    private ListedFile inPlace(String name, String kind) throws IOException {
        Path part = directory.resolve(name + Deliverable.PART_SUFFIX);
        String digest = ListedFile.sha256Of(part);
        long size = Files.size(part);
        moves.intoPlace(part, directory.resolve(name));
        return new ListedFile(name, name, size, digest, kind);
    }

    /** Writes the script or the theme the jar ships, byte for byte, and lists it. */
    private ListedFile shipped(String name) throws IOException {
        byte[] bytes;
        try (InputStream in = WebsiteDrawing.class.getResourceAsStream(SHIPPED + name)) {
            if (in == null) {
                throw new IOException("the jar ships no " + name);
            }
            bytes = in.readAllBytes();
        }
        Files.write(directory.resolve(name + Deliverable.PART_SUFFIX), bytes);
        return inPlace(name, ListedFile.ASSET);
    }

    private void listed(ListedFile file) throws IOException {
        rest.append(firstListed ? "" : ",\n").append(JSON.writeValueAsString(file));
        firstListed = false;
    }
}
