package io.algernon.vespera.publish.website;

import io.algernon.vespera.engine.deliverable.ClusterPage;
import io.algernon.vespera.engine.deliverable.Copies;
import io.algernon.vespera.engine.deliverable.Deliverable;
import io.algernon.vespera.engine.deliverable.Drawing;
import io.algernon.vespera.engine.deliverable.Entries;
import io.algernon.vespera.engine.deliverable.FixedWords;
import io.algernon.vespera.engine.deliverable.Html;
import io.algernon.vespera.engine.deliverable.IndexPage;
import io.algernon.vespera.engine.deliverable.ListedPartition;
import io.algernon.vespera.engine.deliverable.ListedSurvivor;
import io.algernon.vespera.engine.ledger.ReplacingMove;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.FilenameStem;
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
 * <p><b>It follows the HTML tree's shape</b>: {@code index.html} at the root, and for each seed partition a folder and
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

    /** One cluster of the search's index (ADR-333 §9); the title and the writing are empty where nothing was written. */
    record SearchCluster(String partition, String title, String label, String writing, String link,
            List<SearchEntry> entries) {}

    private final Path directory;
    private final ReplacingMove moves;
    private final DeliverableLanguage language;
    private final Set<String> written;
    private final Writer index;
    private final Writer search;
    private final Writer rest;
    private boolean firstSearchRow = true;
    private boolean firstListed = true;

    /** The seed partition being drawn: its folder and its name. */
    private String partitionFolder;
    private String partitionName;

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

    /** Starts a seed partition's heading and table in the index, and its folder, named as the HTML tree's. */
    @Override
    public void partitionStarted(ListedPartition partition, int partitionCount) throws IOException {
        partitionFolder = IndexPage.partitionDirectoryName(partition, partitionCount);
        partitionName = FilenameStem.of(partition.seedPath());
        Files.createDirectories(directory.resolve(partitionFolder));
        written.add(DIRECTORY_NAME + "/" + partitionFolder);
        index.append("<h2>").append(Html.escape(partition.seedPath())).append("</h2>\n<table>\n")
                .append(FixedWords.clusterTableHeadRow(language));
    }

    /**
     * Opens one cluster's folder under {@code website/} and the entries its HTML page written whole writes for it too
     * ({@link WebsiteEntries}).
     *
     * @param clusterFolder the cluster's folder in the HTML tree, where its copies stand
     */
    @Override
    public Entries entriesOf(
            ListedPartition partition, RecordedCluster recorded, List<ListedSurvivor> members, Path clusterFolder,
            Copies copies)
            throws IOException {
        String folderPath = clusterFolderPath(partition, recorded);
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
            ListedPartition partition,
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
        String folderPath = clusterFolderPath(partition, recorded);
        Path folder = directory.resolve(folderPath);
        int pages = breaks.map(ClusterPage.PageBreaks::pages).orElse(1);
        for (int page = 1; page <= pages; page++) {
            String name = ClusterPage.nameOfPage(page);
            Path part = folder.resolve(name + Deliverable.PART_SUFFIX);
            List<ListedFile> files;
            try (OutputStream out = Files.newOutputStream(part)) {
                files = WebsitePage.write(
                        out, recorded, doc.orElse(null), why, entryCount, entries, breaks.orElse(null), page,
                        writingFinished, "../../", language);
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
                partitionName,
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
    }

    /** Closes the table of the seed partition just drawn. */
    @Override
    public void partitionEnded() throws IOException {
        index.append("</table>\n");
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

    private String clusterFolderPath(ListedPartition partition, RecordedCluster recorded) {
        return partitionFolder + "/" + IndexPage.clusterFolderName(recorded, IndexPage.widthOf(partition.clusterCount()));
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
