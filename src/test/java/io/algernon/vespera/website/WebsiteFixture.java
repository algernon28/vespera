package io.algernon.vespera.website;

import static io.algernon.vespera.deliverable.ListedTree.anImage;
import static io.algernon.vespera.deliverable.ListedTree.cluster;
import static io.algernon.vespera.deliverable.ListedTree.sha256Of;
import static io.algernon.vespera.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.deliverable.ListedTree.survivor;
import static io.algernon.vespera.deliverable.ListedTree.writing;

import io.algernon.vespera.deliverable.ClusterPageCeiling;
import io.algernon.vespera.deliverable.Deliverable;
import io.algernon.vespera.deliverable.DeliverableProgress;
import io.algernon.vespera.deliverable.DeliverableRoot;
import io.algernon.vespera.deliverable.ListedCodeSets;
import io.algernon.vespera.deliverable.ListedPicture;
import io.algernon.vespera.deliverable.ListedSurvivor;
import io.algernon.vespera.deliverable.ListedTree;
import io.algernon.vespera.deliverable.Plugin;
import io.algernon.vespera.deliverable.SurvivorPictures;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.synthesis.ClusterSlot;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.algernon.vespera.synthesis.Unwritten;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The website rendering's tests' one fixture (ADR-333 §3): a tree drawn through {@code Deliverable}'s public way in
 * with the website rendering's plugin, from lists held here over an archive written here, and what those tests read
 * back out of the rendering and out of a website a publish refreshed. Every text in it is synthetic.
 *
 * <p>One seed partition, {@code seeds/Fire Safety.docx}, of two groups: the doors, written over and citing its
 * documents, the first carrying a PNG picture, one gone from the archive, and one whose name carries a space; and the
 * dampers, nothing written over it, its one document a PNG image whose name carries a {@code #}. The second document's
 * bytes in the archive carry {@link #A_MARKED_SENTENCE}, which stands for a document's extracted text: nothing of the
 * website may carry it.
 *
 * <p><b>What a test reads of {@code files.json}.</b> ADR-333 §3 names each entry's fields, {@code path}, {@code
 * source}, {@code size}, {@code sha256} and {@code kind}, and not the name of the array that holds them: {@link
 * #listedIn} reads every object of the file that carries the five, wherever it lies.
 */
final class WebsiteFixture {

    /** The website rendering's folder at the tree's root, and the website's in the working directory (ADR-333 §3, §7). */
    static final String WEBSITE = "website";

    /** The rendering's list (ADR-333 §3). */
    static final String FILES_JSON = "files.json";

    /** The website's own list of what its publishes wrote (ADR-333 §7). */
    static final String VESPERA_WEBSITE_JSON = "vespera-website.json";

    /** The search's index, set by a script and never fetched (ADR-333 §9). */
    static final String SEARCH_INDEX = "search-index.js";

    static final OccurrenceId THE_SEED = new OccurrenceId(1);

    static final String SEED_PATH = "seeds/Fire Safety.docx";

    /** The seed partition's name as the seed's stem gives it. */
    static final String PARTITION_NAME = "Fire Safety";

    static final String PARTITION_FOLDER = "1-fire-safety";

    static final String DOORS_LABEL = "Fire doors";

    static final String DAMPERS_LABEL = "Smoke dampers";

    static final String DOORS_FOLDER = PARTITION_FOLDER + "/1-fire-doors";

    static final String DAMPERS_FOLDER = PARTITION_FOLDER + "/2-smoke-dampers";

    static final String DOORS_TITLE = "Fire doors and their inspection";

    /** Two sentences of the writing over the doors, each citing a document. */
    static final String FIRST_SENTENCE = "Doors close by themselves";

    static final String SECOND_SENTENCE = "they are inspected twice a year";

    static final String WRITING = FIRST_SENTENCE + " [1], and " + SECOND_SENTENCE + " [2].";

    /** The doors' documents and the dampers' one, by their paths in the archive, as their entries show them. */
    static final String DOOR_SURVEY = "reports/door survey.docx";

    static final String INSPECTION_LOG = "reports/inspection-log.docx";

    static final String GONE = "reports/gone-from-the-archive.docx";

    static final String DAMPER_SCAN = "scans/damper #2.png";

    /** A sentence of the second document's text, which no file of the website may carry (ADR-333 §9). */
    static final String A_MARKED_SENTENCE = "MARKED-7f3a1c the latch failed on the third floor";

    static final byte[] DOOR_SURVEY_BYTES = bytes("the door survey");

    static final byte[] INSPECTION_LOG_BYTES = bytes("the inspection log. " + A_MARKED_SENTENCE + ".");

    static final byte[] DAMPER_SCAN_BYTES = anImage("png", 6, 4);

    static final byte[] THE_DIAGRAM = anImage("png", 4, 3);

    static final RecordedCluster DOORS = cluster(THE_SEED, 0, DOORS_LABEL, 1, 3);

    static final RecordedCluster DAMPERS = cluster(THE_SEED, 1, DAMPERS_LABEL, 2, 1);

    /** The website rendering's plugin, the one plugin a tree here is drawn with. */
    static final List<Plugin> THE_WEBSITE_PLUGIN = List.of(new WebsitePlugin());

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private WebsiteFixture() {}

    /** The archive under {@code base}, written once: each document's bytes at its path. */
    static Path archiveIn(Path base) throws IOException {
        Path archive = base.resolve("archive");
        write(archive.resolve(DOOR_SURVEY), DOOR_SURVEY_BYTES);
        write(archive.resolve(INSPECTION_LOG), INSPECTION_LOG_BYTES);
        write(archive.resolve(DAMPER_SCAN), DAMPER_SCAN_BYTES);
        return archive;
    }

    /** The doors' three survivors and the dampers' one, the third gone from the archive. */
    static List<ListedSurvivor> survivors() {
        return List.of(
                survivor(10, DOOR_SURVEY, sha256Of(DOOR_SURVEY_BYTES), THE_SEED, SEED_PATH, 0, 0.9),
                survivor(11, INSPECTION_LOG, sha256Of(INSPECTION_LOG_BYTES), THE_SEED, SEED_PATH, 0, 0.8),
                survivor(12, GONE, sha256Of(bytes("never in the archive")), THE_SEED, SEED_PATH, 0, 0.7),
                survivor(20, DAMPER_SCAN, sha256Of(DAMPER_SCAN_BYTES), THE_SEED, SEED_PATH, 1, 0.6));
    }

    /** The writing over the doors, citing the first two documents. */
    static Map<ClusterSlot, SynthesisDoc> written(String prose) {
        return Map.of(slotOf(DOORS), writing(DOORS_TITLE, prose, 10, 11));
    }

    /** The first document carries the diagram, a PNG; no other document carries a picture. */
    static SurvivorPictures pictures() {
        return survivor -> survivor.occurrence().value() == 10
                ? List.of(new ListedPicture("image/png", THE_DIAGRAM, false, "a diagram of the doors"))
                : List.of();
    }

    /** The fixture drawn in {@code base}: its archive, a working directory and its tree, the website plugin drawing. */
    static Path drawn(Path base) throws IOException {
        return drawn(base, List.of(DOORS, DAMPERS), written(WRITING), survivors(), ListedCodeSets.NONE);
    }

    /**
     * The tree of {@code arrangement}, {@code written} and {@code survivors} drawn in {@code base/work} over the
     * archive in {@code base}, the dampers unwritten for want of a document it could send, with {@code code}'s sets,
     * in English, the website plugin drawing beside it.
     */
    static Path drawn(
            Path base,
            List<RecordedCluster> arrangement,
            Map<ClusterSlot, SynthesisDoc> written,
            List<ListedSurvivor> survivors,
            ListedCodeSets code)
            throws IOException {
        Path archive = archiveIn(base);
        Map<ClusterSlot, Unwritten> unwritten = arrangement.contains(DAMPERS)
                ? Map.of(slotOf(DAMPERS), Unwritten.NO_SENDABLE_DOCUMENT)
                : Map.of();
        return Deliverable.writeTo(
                base.resolve("work"),
                DeliverableRoot.UNNAMED,
                ListedTree.provenance(archive),
                ListedTree.of(arrangement, written, survivors, unwritten, true, DeliverableLanguage.ENGLISH),
                code,
                pictures(),
                new ClusterPageCeiling.Unset(),
                THE_WEBSITE_PLUGIN,
                DeliverableProgress.NONE);
    }

    /** The website rendering of {@code tree}: its folder at the tree's root. */
    static Path renderingOf(Path tree) {
        return tree.resolve(WEBSITE);
    }

    /** One publish of {@code tree}'s website rendering to {@code workingDirectory}'s website, its lines dropped. */
    static Plugin.Delivery published(Path tree, Path workingDirectory) {
        return new WebsitePlugin().deliver(renderingOf(tree), ListedTree.RUN_ID, workingDirectory, line -> {});
    }

    /** One entry of the rendering's list (ADR-333 §3). */
    record Listed(String path, String source, long size, String sha256, String kind) {}

    /** Every entry {@code files.json} in {@code rendering} lists, by its path in the website, in the list's order. */
    static Map<String, Listed> listedIn(Path rendering) throws IOException {
        Map<String, Listed> listed = new LinkedHashMap<>();
        collect(JSON.readTree(rendering.resolve(FILES_JSON).toFile()), listed);
        return listed;
    }

    private static void collect(JsonNode node, Map<String, Listed> listed) {
        if (node.isObject() && Stream.of("path", "source", "size", "sha256", "kind").allMatch(node::has)) {
            listed.put(node.get("path").asString(), new Listed(
                    node.get("path").asString(),
                    node.get("source").asString(),
                    node.get("size").asLong(),
                    node.get("sha256").asString(),
                    node.get("kind").asString()));
            return;
        }
        if (node.isObject() || node.isArray()) {
            node.valueStream().forEach(child -> collect(child, listed));
        }
    }

    /** Every regular file beneath {@code folder}, by its path relative to it with {@code /} between its names. */
    static Map<String, Path> filesUnder(Path folder) throws IOException {
        Map<String, Path> files = new TreeMap<>();
        if (!Files.isDirectory(folder)) {
            return files;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                files.put(folder.relativize(file).toString().replace('\\', '/'), file);
            }
        }
        return files;
    }

    /** The pages beneath {@code folder}, by their paths relative to it, sorted. */
    static List<String> pagesUnder(Path folder) throws IOException {
        return new ArrayList<>(filesUnder(folder).keySet()).stream().filter(name -> name.endsWith(".html")).toList();
    }

    /** {@code file}'s SHA-256, as 64 lower-case hexadecimal characters. */
    static String sha256(Path file) throws IOException {
        return sha256Of(Files.readAllBytes(file));
    }

    static String textOf(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static void write(Path file, byte[] content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }
}
