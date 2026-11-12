package io.algernon.vespera.publish.confluence;

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
 * A Confluence rendering drawn by hand as stage 6b draws one (ADR-312, ADR-316, ADR-328, ADR-378): a root page carrying
 * {@code documents.csv}, a seed partition's page, and a cluster's page carrying a copy and a picture's file, with the
 * page list naming them, each page keyed by its language's code and its place, titled by its text, its place and the
 * code, and none carrying the run's id. English unless drawn in another language. Every text in it is synthetic.
 */
final class Rendering {

    /** The generation run the rendering is of, and its marker, which only the page list and the publish's lines name. */
    static final String RUN = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    static final String MARKER = "abcdef012345";

    /** A later generation run, drawn over the same places (ADR-328 §4). */
    static final String LATER_RUN = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";

    static final String LATER_MARKER = "1234567890ab";

    /** The generation run of the same arrangement drawn in Italian, another run (ADR-321 §1), and its marker. */
    static final String ITALIAN_RUN = "13579bdf02468ace13579bdf02468ace13579bdf02468ace13579bdf02468ace";

    static final String ITALIAN_MARKER = "13579bdf0246";

    /** The two deliverable languages' codes, as the profile names them (ADR-321 §1). */
    static final String ENGLISH = "en";

    static final String ITALIAN = "it";

    /** The page list's format, whose keys are a language's code and a place (ADR-378 §4). */
    static final int FORMAT = 3;

    /** Each page's key in the English rendering: the code and its place, unpadded (ADR-378 §2). */
    static final String ROOT = "en:root";

    static final String PARTITION = "en:1";

    static final String CLUSTER = "en:1.1";

    /** Each page's title in the English rendering: its text, its place and the code, and no marker (ADR-378 §1). */
    static final String ROOT_TITLE = "Vespera_en";

    static final String PARTITION_TITLE = "Seeds (1 · en)";

    static final String CLUSTER_TITLE = "Payroll and leave (1.1 · en)";

    /** The label every page of the English tree carries (ADR-378 §2). */
    static final String LABEL = "vespera-en";

    /** The texts the titles are drawn from, the same in either language: a seed's stem and a cluster's label. */
    static final String PARTITION_TEXT = "Seeds";

    static final String CLUSTER_TEXT = "Payroll and leave";

    static final String COPY = "leave-policy.pdf";

    static final String PICTURE = "picture-0123456789abcdef.png";

    static final String LISTING = "documents.csv";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Path tree;
    private final String language;
    private final Map<String, String> bodies = new LinkedHashMap<>();
    private String run;
    private String marker;
    private int format = FORMAT;
    private String clusterTitle;

    /**
     * Each body names its page's files by {@code ri:attachment}, and the root page and the seed partition's page link
     * to the page after them by title, as stage 6b draws them (ADR-313 §1, ADR-314, ADR-315, ADR-309): so a body sent
     * before its files, or a link to a page not yet created, is there for a test to see.
     */
    private Rendering(Path tree, String language, String run, String marker) {
        this.tree = tree;
        this.language = language;
        this.run = run;
        this.marker = marker;
        this.clusterTitle = titleOf(CLUSTER_TEXT, "1.1", language);
        bodies.put(key("root"), "<p>The groups.</p>\n<h2>" + pageLink(partitionTitle(), PARTITION_TEXT) + "</h2>\n<p>"
                + fileLink(LISTING) + "</p>");
        bodies.put(key("1"), partitionBody(clusterTitle));
        bodies.put(key("1.1"), "<p>What the group holds.</p>\n<p>" + fileLink(COPY) + "</p>\n<p><ac:image ac:alt=\"a"
                + " chart\"><ri:attachment ri:filename=\"" + PICTURE + "\" /></ac:image></p>");
    }

    /** A title of ADR-378 §1's form: {@code text (place · code)}. */
    static String titleOf(String text, String place, String language) {
        return text + " (" + place + " · " + language + ")";
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

    /** Draws the English rendering in {@code tree}, and gives it. */
    static Rendering drawnIn(Path tree) {
        return drawnIn(tree, ENGLISH);
    }

    /** Draws the rendering in the language {@code language} names in {@code tree}, and gives it. */
    static Rendering drawnIn(Path tree, String language) {
        Rendering rendering = ITALIAN.equals(language)
                ? new Rendering(tree, language, ITALIAN_RUN, ITALIAN_MARKER)
                : new Rendering(tree, language, RUN, MARKER);
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

    /** The generation run this rendering is of. */
    String run() {
        return run;
    }

    /** The page of {@code place}'s key in this rendering's language (ADR-378 §2). */
    String key(String place) {
        return language + ":" + place;
    }

    /** The root page's title, {@code Vespera_<code>} (ADR-378 §1). */
    String rootTitle() {
        return "Vespera_" + language;
    }

    String partitionTitle() {
        return titleOf(PARTITION_TEXT, "1", language);
    }

    String clusterTitle() {
        return clusterTitle;
    }

    /** The label every page of this language's tree carries (ADR-378 §2). */
    String label() {
        return "vespera-" + language;
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
        bodies.remove(key("1.1"));
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
        bodies.put(key("1"), partitionBody(title));
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
            Files.writeString(confluence().resolve("page.xml"), bodies.get(key("root")), StandardCharsets.UTF_8);
            Files.writeString(confluence().resolve("001/page.xml"), bodies.get(key("1")), StandardCharsets.UTF_8);
            List<Object> pages = new ArrayList<>();
            pages.add(page(key("root"), null, rootTitle(), "page.xml",
                    List.of(file(LISTING, "../" + LISTING, "listing"))));
            pages.add(page(key("1"), key("root"), partitionTitle(), "001/page.xml", List.of()));
            if (bodies.containsKey(key("1.1"))) {
                Files.writeString(confluence().resolve("001/001/page.xml"), bodies.get(key("1.1")),
                        StandardCharsets.UTF_8);
                pages.add(page(key("1.1"), key("1"), clusterTitle, "001/001/page.xml", List.of(
                        file(COPY, "../001/001/" + COPY, "copy"), file(PICTURE, "001/001/" + PICTURE, "picture"))));
            }
            Map<String, Object> list = new LinkedHashMap<>();
            list.put("format", format);
            list.put("generationRun", run);
            list.put("marker", marker);
            list.put("language", language);
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
