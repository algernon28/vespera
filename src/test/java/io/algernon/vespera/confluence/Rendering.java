package io.algernon.vespera.confluence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * A Confluence rendering drawn by hand as stage 6b draws one (ADR-312, ADR-316, ADR-328): a root page carrying {@code
 * documents.csv}, a seed partition's page, and a cluster's page carrying a copy and a picture's file, with the page
 * list naming them, each page keyed and titled by its place and none carrying the run's id. Every text in it is
 * synthetic.
 */
final class Rendering {

    /** The generation run the rendering is of, and its marker, which only the page list and the publish's lines name. */
    static final String RUN = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    static final String MARKER = "abcdef012345";

    /** A later generation run, drawn over the same places (ADR-328 §4). */
    static final String LATER_RUN = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";

    static final String LATER_MARKER = "1234567890ab";

    /** The page list's format, whose keys are places alone (ADR-328 §3). */
    static final int FORMAT = 2;

    /** Each page's key: its place, unpadded (ADR-328 §3). */
    static final String ROOT = "root";

    static final String PARTITION = "1";

    static final String CLUSTER = "1.1";

    /** Each page's title: its text and its place, and no marker (ADR-328 §1). */
    static final String ROOT_TITLE = "Vespera";

    static final String PARTITION_TITLE = "Seeds (1)";

    static final String CLUSTER_TITLE = "Payroll and leave (1.1)";

    /** The label every page of the tree carries (ADR-328 §4). */
    static final String LABEL = "vespera";

    static final String COPY = "leave-policy.pdf";

    static final String PICTURE = "picture-0123456789abcdef.png";

    static final String LISTING = "documents.csv";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Path tree;
    private final Map<String, String> bodies = new LinkedHashMap<>();
    private String run = RUN;
    private String marker = MARKER;
    private int format = FORMAT;
    private String clusterTitle = CLUSTER_TITLE;

    /**
     * Each body names its page's files by {@code ri:attachment}, and the root page and the seed partition's page link
     * to the page after them by title, as stage 6b draws them (ADR-313 §1, ADR-314, ADR-315, ADR-309): so a body sent
     * before its files, or a link to a page not yet created, is there for a test to see.
     */
    private Rendering(Path tree) {
        this.tree = tree;
        bodies.put(ROOT, "<p>The groups.</p>\n<h2>" + pageLink(PARTITION_TITLE, "Seeds") + "</h2>\n<p>"
                + fileLink(LISTING) + "</p>");
        bodies.put(PARTITION, partitionBody(CLUSTER_TITLE));
        bodies.put(CLUSTER, "<p>What the group holds.</p>\n<p>" + fileLink(COPY) + "</p>\n<p><ac:image ac:alt=\"a"
                + " chart\"><ri:attachment ri:filename=\"" + PICTURE + "\" /></ac:image></p>");
    }

    private static String partitionBody(String clusterTitle) {
        return "<p>The seed partition.</p>\n<p>" + pageLink(clusterTitle, "the group") + "</p>";
    }

    private static String pageLink(String title, String text) {
        return "<ac:link><ri:page ri:content-title=\"" + title + "\" /><ac:link-body>" + text
                + "</ac:link-body></ac:link>";
    }

    private static String fileLink(String name) {
        return "<ac:link><ri:attachment ri:filename=\"" + name + "\" /><ac:link-body>" + name
                + "</ac:link-body></ac:link>";
    }

    /** Draws the rendering in {@code tree}, and gives it. */
    static Rendering drawnIn(Path tree) {
        Rendering rendering = new Rendering(tree);
        try {
            Files.createDirectories(tree.resolve("001/001"));
            Files.createDirectories(tree.resolve("confluence/001/001"));
            Files.writeString(tree.resolve(LISTING), "path,group\r\n", StandardCharsets.UTF_8);
            Files.write(tree.resolve("001/001/" + COPY), "%PDF-1.4 synthetic".getBytes(StandardCharsets.US_ASCII));
            Files.write(tree.resolve("confluence/001/001/" + PICTURE), new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        rendering.write();
        return rendering;
    }

    /** The rendering's folder, {@code confluence/}. */
    Path confluence() {
        return tree.resolve("confluence");
    }

    /** Draws the page {@code key} with another body. */
    Rendering withBody(String key, String body) {
        bodies.put(key, body);
        write();
        return this;
    }

    /** Draws the copy with other bytes. */
    Rendering withCopy(String bytes) {
        try {
            Files.writeString(tree.resolve("001/001/" + COPY), bytes, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        write();
        return this;
    }

    /** Draws the rendering without its cluster's page. */
    Rendering withoutTheCluster() {
        bodies.remove(CLUSTER);
        write();
        return this;
    }

    /**
     * Draws the rendering as the later generation run's, the place {@code 1.1} now holding the group titled {@code
     * title}, the seed partition's page linking it by that title (ADR-328 §4).
     */
    Rendering asTheLaterRunWithTheClusterTitled(String title) {
        run = LATER_RUN;
        marker = LATER_MARKER;
        clusterTitle = title;
        bodies.put(PARTITION, partitionBody(title));
        write();
        return this;
    }

    /** Writes the page list in another format, its pages as they are. */
    Rendering inFormat(int other) {
        format = other;
        write();
        return this;
    }

    private void write() {
        try {
            Files.writeString(confluence().resolve("page.xml"), bodies.get(ROOT), StandardCharsets.UTF_8);
            Files.writeString(confluence().resolve("001/page.xml"), bodies.get(PARTITION), StandardCharsets.UTF_8);
            List<Object> pages = new ArrayList<>();
            pages.add(page(ROOT, null, ROOT_TITLE, "page.xml", List.of(file(LISTING, "../" + LISTING, "listing"))));
            pages.add(page(PARTITION, ROOT, PARTITION_TITLE, "001/page.xml", List.of()));
            if (bodies.containsKey(CLUSTER)) {
                Files.writeString(confluence().resolve("001/001/page.xml"), bodies.get(CLUSTER), StandardCharsets.UTF_8);
                pages.add(page(CLUSTER, PARTITION, clusterTitle, "001/001/page.xml", List.of(
                        file(COPY, "../001/001/" + COPY, "copy"), file(PICTURE, "001/001/" + PICTURE, "picture"))));
            }
            Map<String, Object> list = new LinkedHashMap<>();
            list.put("format", format);
            list.put("generationRun", run);
            list.put("marker", marker);
            list.put("pages", pages);
            Files.write(confluence().resolve(PageList.FILE_NAME), JSON.writeValueAsBytes(list));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, Object> page(String key, String parent, String title, String body, List<Object> files)
            throws IOException {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("key", key);
        page.put("parent", parent);
        page.put("title", title);
        page.put("body", body);
        page.put("bodySha256", Publisher.sha256(confluence().resolve(body)));
        page.put("files", files);
        return page;
    }

    private Map<String, Object> file(String name, String path, String kind) throws IOException {
        Path at = confluence().resolve(path);
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("name", name);
        file.put("path", path);
        file.put("kind", kind);
        file.put("size", Files.size(at));
        file.put("sha256", Publisher.sha256(at));
        return file;
    }
}
