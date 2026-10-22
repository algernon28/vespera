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
 * A Confluence rendering drawn by hand as stage 6b draws one (ADR-312, ADR-316): a root page carrying {@code
 * documents.csv}, a seed partition's page, and a cluster's page carrying a copy and a picture's file, with the page
 * list naming them. Every text in it is synthetic.
 */
final class Rendering {

    /** The generation run the rendering is of, and its marker. */
    static final String RUN = "abcdef0123456789abcdef0123456789";

    static final String MARKER = "abcdef012345";

    static final String ROOT = RUN + ":root";

    static final String PARTITION = RUN + ":001";

    static final String CLUSTER = RUN + ":001.001";

    static final String ROOT_TITLE = "Vespera " + MARKER;

    static final String PARTITION_TITLE = "Seeds (001 · " + MARKER + ")";

    static final String CLUSTER_TITLE = "Payroll and leave (001.001 · " + MARKER + ")";

    static final String COPY = "leave-policy.pdf";

    static final String PICTURE = "picture-0123456789abcdef.png";

    static final String LISTING = "documents.csv";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Path tree;
    private final Map<String, String> bodies = new LinkedHashMap<>();

    private Rendering(Path tree) {
        this.tree = tree;
        bodies.put(ROOT, "<p>The groups.</p>");
        bodies.put(PARTITION, "<p>The seed partition.</p>");
        bodies.put(CLUSTER, "<p>What the group holds.</p>");
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

    private void write() {
        try {
            Files.writeString(confluence().resolve("page.xml"), bodies.get(ROOT), StandardCharsets.UTF_8);
            Files.writeString(confluence().resolve("001/page.xml"), bodies.get(PARTITION), StandardCharsets.UTF_8);
            List<Object> pages = new ArrayList<>();
            pages.add(page(ROOT, null, ROOT_TITLE, "page.xml", List.of(file(LISTING, "../" + LISTING, "listing"))));
            pages.add(page(PARTITION, ROOT, PARTITION_TITLE, "001/page.xml", List.of()));
            if (bodies.containsKey(CLUSTER)) {
                Files.writeString(confluence().resolve("001/001/page.xml"), bodies.get(CLUSTER), StandardCharsets.UTF_8);
                pages.add(page(CLUSTER, PARTITION, CLUSTER_TITLE, "001/001/page.xml", List.of(
                        file(COPY, "../001/001/" + COPY, "copy"), file(PICTURE, "001/001/" + PICTURE, "picture"))));
            }
            Map<String, Object> list = new LinkedHashMap<>();
            list.put("format", 1);
            list.put("generationRun", RUN);
            list.put("marker", MARKER);
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
