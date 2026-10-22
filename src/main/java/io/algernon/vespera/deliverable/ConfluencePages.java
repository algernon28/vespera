package io.algernon.vespera.deliverable;

import io.algernon.vespera.ledger.ReplacingMove;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.algernon.vespera.synthesis.FilenameStem;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.algernon.vespera.synthesis.Unwritten;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Confluence rendering, drawn under {@value #DIRECTORY_NAME}{@code /} at the tree's root by every drawing beside
 * the HTML tree (ADR-301, ADR-312), and its page list, {@value #PAGE_LIST_FILE_NAME} (ADR-316).
 *
 * <p><b>It follows the HTML tree's shape</b> (ADR-312 §1): the root page's body and the page list directly in {@code
 * confluence/}, a folder for each seed partition and within it for each cluster, named as the HTML tree names them,
 * each holding its page's body, {@value #BODY_NAME} or {@code page-<k>.xml} for page k of a split cluster, and its
 * pictures' files (ADR-314). A copy is not written again: the page list names it at its place in the HTML tree
 * (ADR-316 §2).
 *
 * <p><b>It is appended to as the HTML tree is</b> (ADR-223 §6): a cluster's pages are written as its HTML pages are, a
 * seed partition's page when its last cluster is drawn, and the root page and the page list last, once {@code
 * documents.csv} stands (ADR-315 §3). What is held is one seed partition's rows; the root page's body and the page
 * list's entries after the root's are written to their {@code .part} files as they come.
 *
 * <p><b>Nothing names the corpus root, the working directory or a profile value</b> (ADR-315 §1, ADR-316 §2), and
 * every page's title is ADR-310's. Every body, file and the page list is written through {@code .part} and moved into
 * place.
 */
final class ConfluencePages implements AutoCloseable {

    /** Where the rendering lies, at the tree's root (ADR-312 §1). */
    static final String DIRECTORY_NAME = "confluence";

    /** The page list, directly in {@code confluence/} (ADR-316 §1). */
    static final String PAGE_LIST_FILE_NAME = "pages.json";

    /** A page's storage body, or a split cluster's first page's. */
    static final String BODY_NAME = "page.xml";

    /** The page list's format (ADR-316 §2). */
    private static final int FORMAT = 1;

    /** The root page's one sentence (ADR-315 §2). */
    static final String THE_ROOT_PAGES_SENTENCE = "Each group's page carries a checked copy of every document it lists."
            + " documents.csv lists every document of this run.";

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

    /** The seed partition being drawn, and its rows, held until its last cluster is drawn. */
    private String partitionKey;
    private String partitionTitle;
    private String partitionFolder;
    private final StringBuilder partitionRows = new StringBuilder();
    private final List<ListedPage> clusterPages = new ArrayList<>();

    /**
     * Opens the rendering in {@code tree}, writing the root page's opening, and adds every folder it writes to {@code
     * written}, by its path from the tree's root, so ADR-280 §4's count reads them as drawn (ADR-312 §4).
     */
    ConfluencePages(Path tree, String runId, ReplacingMove moves, Set<String> written, DeliverableLanguage language)
            throws IOException {
        this.directory = tree.resolve(DIRECTORY_NAME);
        this.runId = runId;
        this.marker = ConfluenceTitles.markerOf(runId);
        this.moves = moves;
        this.language = language;
        this.written = written;
        Files.createDirectories(directory);
        written.add(DIRECTORY_NAME);
        this.root = Files.newBufferedWriter(directory.resolve(BODY_NAME + Deliverable.PART_SUFFIX), StandardCharsets.UTF_8);
        this.rest = Files.newBufferedWriter(directory.resolve(REST_OF_THE_LIST), StandardCharsets.UTF_8);
        root.append("<h1>").append(FixedWords.theDeliverable(language)).append(Html.escape(runId))
                .append("</h1>\n<p>")
                .append(Html.escape(FixedWords.rootSentence(language))).append("</p>\n");
    }

    /** An {@code ac:link} to the page titled {@code title}, reading {@code text}, which it escapes (ADR-309). */
    static String link(String title, String text) {
        return "<ac:link><ri:page ri:content-title=\"" + Html.escape(title) + "\" /><ac:link-body>" + Html.escape(text)
                + "</ac:link-body></ac:link>";
    }

    /** Starts a seed partition's page: its folder, named as the HTML tree's (ADR-312 §1). */
    void partitionStarted(ListedPartition partition, int partitionCount) throws IOException {
        String place = IndexPage.pad(partition.partitionOrder(), IndexPage.widthOf(partitionCount));
        partitionFolder = IndexPage.partitionDirectoryName(partition, partitionCount);
        partitionKey = runId + ":" + place;
        partitionTitle = ConfluenceTitles.partition(FilenameStem.of(partition.seedPath()), place, marker);
        Files.createDirectories(directory.resolve(partitionFolder));
        written.add(DIRECTORY_NAME + "/" + partitionFolder);
        partitionRows.setLength(0);
        clusterPages.clear();
    }

    /**
     * Opens one cluster's folder under {@code confluence/} and the entries its HTML page written whole writes for it too
     * ({@link ConfluenceEntries}).
     *
     * @param clusterFolder the cluster's folder in the HTML tree, where its copies stand
     */
    ConfluenceEntries entriesOf(
            ListedPartition partition, RecordedCluster recorded, List<ListedSurvivor> members, Path clusterFolder,
            Copies copies)
            throws IOException {
        String folderPath = partitionFolder + "/" + IndexPage.clusterFolderName(recorded, IndexPage.widthOf(partition.clusterCount()));
        Path folder = directory.resolve(folderPath);
        Files.createDirectories(folder);
        written.add(DIRECTORY_NAME + "/" + folderPath);
        return new ConfluenceEntries(folder, folderPath, clusterFolder, copies, members, moves, language);
    }

    /**
     * Writes one cluster's page, or pages where {@code breaks} splits its HTML page (ADR-303), from {@code entries}, which
     * it closes and deletes, and its row of its seed partition's table and the root page's.
     */
    void clusterDrawn(
            ListedPartition partition,
            RecordedCluster recorded,
            Optional<SynthesisDoc> doc,
            Unwritten why,
            int entryCount,
            Optional<ClusterPage.PageBreaks> breaks,
            ConfluenceEntries entries,
            boolean writingFinished)
            throws IOException {
        entries.close();
        int clusterWidth = IndexPage.widthOf(partition.clusterCount());
        String folderPath = partitionFolder + "/" + IndexPage.clusterFolderName(recorded, clusterWidth);
        Path folder = directory.resolve(folderPath);
        String place = partitionKey.substring(runId.length() + 1) + "."
                + IndexPage.pad(recorded.cluster().clusterOrder(), clusterWidth);
        String text = doc.map(SynthesisDoc::title).orElse(recorded.label().value());
        int pages = breaks.map(ClusterPage.PageBreaks::pages).orElse(1);
        String firstKey = runId + ":" + place;
        for (int page = 1; page <= pages; page++) {
            String body = page == 1 ? BODY_NAME : "page-" + page + ".xml";
            Path part = folder.resolve(body + Deliverable.PART_SUFFIX);
            List<PageFile> files;
            try (OutputStream out = Files.newOutputStream(part)) {
                files = ConfluencePage.write(
                        out, recorded, doc.orElse(null), why, entryCount, entries, breaks.orElse(null), page,
                        k -> ConfluenceTitles.cluster(text, place, k, marker, language), writingFinished, language);
            }
            String digest = sha256Of(part);
            moves.intoPlace(part, folder.resolve(body));
            clusterPages.add(new ListedPage(
                    page == 1 ? firstKey : firstKey + "." + page,
                    page == 1 ? partitionKey : firstKey,
                    ConfluenceTitles.cluster(text, place, page, marker, language),
                    folderPath + "/" + body,
                    digest,
                    files));
        }
        Files.delete(entries.file());
        partitionRows.append("<tr><td>")
                .append(link(ConfluenceTitles.cluster(text, place, 1, marker, language), recorded.label().value()))
                .append("</td><td>")
                .append(recorded.cluster().documentCount())
                .append("</td><td>")
                .append(doc.isEmpty()
                        ? "<em>" + Html.escape(FixedWords.nothingWasWrittenOverIt(language)) + "</em>"
                        : Html.escape(doc.get().title()))
                .append("</td></tr>\n");
    }

    /**
     * Writes the seed partition's page, its table of groups, and appends the same to the root page under a link to it;
     * then lists the seed partition's page and its clusters' pages, parents first (ADR-316 §2).
     */
    void partitionEnded() throws IOException {
        String table = "<table><tbody>\n" + FixedWords.groupTableHeadRow(language) + partitionRows
                + "</tbody></table>\n";
        Path part = directory.resolve(partitionFolder).resolve(BODY_NAME + Deliverable.PART_SUFFIX);
        Files.writeString(part, table, StandardCharsets.UTF_8);
        String digest = sha256Of(part);
        moves.intoPlace(part, directory.resolve(partitionFolder).resolve(BODY_NAME));
        root.append("<h2>").append(link(partitionTitle, partitionTitle)).append("</h2>\n").append(table);
        listed(new ListedPage(partitionKey, rootKey(), partitionTitle, partitionFolder + "/" + BODY_NAME, digest, List.of()));
        for (ListedPage page : clusterPages) {
            listed(page);
        }
        clusterPages.clear();
    }

    /**
     * Writes the root page, carrying {@code manifest} as a file (ADR-315 §3), and then the page list, once every page and
     * file it names is written (ADR-316 §1). The page list's held entries are let go.
     */
    void finish(Path manifest) throws IOException {
        root.close();
        rest.close();
        Path rootPart = directory.resolve(BODY_NAME + Deliverable.PART_SUFFIX);
        String rootDigest = sha256Of(rootPart);
        moves.intoPlace(rootPart, directory.resolve(BODY_NAME));
        ListedPage rootPage = new ListedPage(
                rootKey(),
                null,
                ConfluenceTitles.root(marker),
                BODY_NAME,
                rootDigest,
                List.of(new PageFile(
                        Deliverable.MANIFEST_FILE_NAME, "../" + Deliverable.MANIFEST_FILE_NAME, PageFile.LISTING,
                        Files.size(manifest), sha256Of(manifest))));
        Path restPart = directory.resolve(REST_OF_THE_LIST);
        Path listPart = directory.resolve(PAGE_LIST_FILE_NAME + Deliverable.PART_SUFFIX);
        try (OutputStream list = Files.newOutputStream(listPart)) {
            list.write(("{\"format\":" + FORMAT + ",\"generationRun\":" + JSON.writeValueAsString(runId) + ",\"marker\":"
                    + JSON.writeValueAsString(marker) + ",\"pages\":[\n" + JSON.writeValueAsString(rootPage))
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
        return runId + ":root";
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
