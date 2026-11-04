package io.algernon.vespera.deliverable;

import static io.algernon.vespera.deliverable.ListedTree.anImage;
import static io.algernon.vespera.deliverable.ListedTree.cluster;
import static io.algernon.vespera.deliverable.ListedTree.sha256Of;
import static io.algernon.vespera.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.deliverable.ListedTree.survivor;
import static io.algernon.vespera.deliverable.ListedTree.writing;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.Unwritten;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * One seed partition of two clusters, drawn so that every call a plugin's drawing can hear is heard (ADR-333 §2): the
 * first written over its two documents, the first of them an image whose copy its entry shows, carrying a picture it
 * shows and one it cannot, so that its entry tells of a copy shown, a picture and a picture not shown; the second
 * unwritten, its answer turned down for its shape. Every fixture is synthetic: an archive written here, a run id of
 * the ledger's shape.
 */
final class TwoClustersWithPictures {

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/Fire Safety.docx";

    static final RecordedCluster DOORS = cluster(THE_SEED, 0, "Fire doors", 1, 2);

    static final RecordedCluster DAMPERS = cluster(THE_SEED, 1, "Smoke dampers", 2, 1);

    /** The image document: its copy is a PNG, so its entry shows it. */
    private static final byte[] A_SCAN = anImage("png", 5, 5);

    /** The picture the image document carries that its entry shows. */
    private static final byte[] A_DIAGRAM = anImage("png", 4, 3);

    /** Bytes that are no PNG, under a PNG's type: the entry counts the picture as not shown (ADR-286 §1). */
    private static final byte[] NOT_A_PICTURE = "not a picture".getBytes(StandardCharsets.UTF_8);

    private TwoClustersWithPictures() {}

    /** The fixture drawn under {@code base}, with {@code plugins} drawing beside the tree, {@code progress} told. */
    static Path draw(Path base, List<Plugin> plugins, DeliverableProgress progress) throws IOException {
        Path archive = base.resolve("archive");
        Files.createDirectories(archive.resolve("reports"));
        Files.createDirectories(archive.resolve("notes"));
        Files.write(archive.resolve("reports/scan.png"), A_SCAN);
        Files.writeString(archive.resolve("reports/b.txt"), "the second document");
        Files.writeString(archive.resolve("notes/damper.txt"), "a damper");
        List<ListedSurvivor> survivors = List.of(
                survivor(10, "reports/scan.png", sha256Of(A_SCAN), THE_SEED, SEED_PATH, 0, 0.9),
                survivor(11, "reports/b.txt", sha256Of(bytes("the second document")), THE_SEED, SEED_PATH, 0, 0.8),
                survivor(20, "notes/damper.txt", sha256Of(bytes("a damper")), THE_SEED, SEED_PATH, 1, 0.5));
        SurvivorPictures pictures = survivor -> survivor.occurrence().value() == 10
                ? List.of(
                        new ListedPicture("image/png", A_DIAGRAM, false, "a diagram"),
                        new ListedPicture("image/png", NOT_A_PICTURE, false, "a picture that is none"))
                : List.of();
        return Deliverable.writeTo(
                base.resolve("work"),
                DeliverableRoot.UNNAMED,
                ListedTree.provenance(archive),
                ListedTree.of(
                        List.of(DOORS, DAMPERS),
                        Map.of(slotOf(DOORS), writing("Fire doors and their inspection", "Doors [1] and frames [2].", 10, 11)),
                        survivors,
                        Map.of(slotOf(DAMPERS), Unwritten.FAULT_SCHEMA_VIOLATION)),
                ListedCodeSets.NONE,
                pictures,
                new ClusterPageCeiling.Unset(),
                plugins,
                progress);
    }

    /** Every call one plugin hears over this fixture, in order, the closing of its entries left out. */
    static final List<String> EVERY_CALL = List.of(
            ScriptedPlugin.DRAWING,
            ScriptedPlugin.PARTITION_STARTED + " 1/1",
            ScriptedPlugin.ENTRIES_OF + " 1 of 2",
            ScriptedPlugin.START + " 1 held",
            ScriptedPlugin.COPY_SHOWN,
            ScriptedPlugin.PICTURE + " png",
            ScriptedPlugin.NOT_SHOWN + " 1",
            ScriptedPlugin.END,
            ScriptedPlugin.START + " 2 held",
            ScriptedPlugin.END,
            ScriptedPlugin.CLUSTER_DRAWN + " 1 of 2 written with its entries",
            ScriptedPlugin.ENTRIES_OF + " 2 of 1",
            ScriptedPlugin.START + " 1 held",
            ScriptedPlugin.END,
            ScriptedPlugin.CLUSTER_DRAWN + " 2 of 1 unwritten with its entries",
            ScriptedPlugin.PARTITION_ENDED,
            ScriptedPlugin.FINISH + " " + Deliverable.MANIFEST_FILE_NAME + " standing",
            ScriptedPlugin.CLOSE);

    /** The calls of {@code heard} but the closing of a cluster's entries, which a drawing may pass on more than once. */
    static List<String> withoutEntriesClosed(List<String> heard) {
        return heard.stream().filter(call -> !call.endsWith(ScriptedPlugin.ENTRIES_CLOSED)).toList();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
