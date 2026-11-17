package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.ReplacingMove;
import io.algernon.vespera.engine.synthesis.ArrangedCluster;
import io.algernon.vespera.engine.synthesis.ClusterLabel;
import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.FilenameStem;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import io.algernon.vespera.engine.synthesis.TopicKey;
import io.algernon.vespera.engine.synthesis.Unwritten;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * An arrangement, its writing and its survivors held as lists, answering {@link ArrangedSurvivors} from them, and
 * what the tests of the tree read back out of what was written (ADR-274).
 *
 * <p>{@code src/main} reads one topic at a time and holds no such lists (ADR-223, ADR-409 section 13). A test's
 * arrangement is a handful of clusters, so it may hold all of it. A topic is the clusters sharing a topic key, in the
 * order listed, its name the one the first listed survivor under it carries; the survivors come in the order listed.
 * These fixtures put the survivors a seed won under one undivided topic of that seed's ({@link #topicOf}), named by the
 * seed's filename stem ({@link #topicNameOf}).
 *
 * <p>Public, with what a plugin's tests draw with, since the Confluence rendering's tests moved into {@code
 * confluence} with the rendering (ADR-333 §1) and draw the same fixtures through {@code Deliverable}'s public door.
 */
public final class ListedTree {

    /** A run id of the shape the ledger mints, which names the tree (ADR-103). */
    public static final String RUN_ID = "a".repeat(48) + "0123456789abcdef";

    /** The walk the run read, stated on the index. */
    public static final long WALK = 7L;

    private ListedTree() {}

    /** The source over these lists, every survivor in one page, its writing unfinished and in English. */
    public static ArrangedSurvivors of(
            List<RecordedCluster> arrangement,
            Map<ClusterSlot, SynthesisDoc> written,
            List<ListedSurvivor> survivors,
            Map<ClusterSlot, Unwritten> unwritten) {
        return of(arrangement, written, survivors, unwritten, false, DeliverableLanguage.ENGLISH);
    }

    /**
     * The source over these lists, every survivor in one page, {@code writingFinished} saying whether the generation
     * step's work is recorded finished (ADR-319 §5.1) and {@code language} the deliverable's language (ADR-321).
     */
    public static ArrangedSurvivors of(
            List<RecordedCluster> arrangement,
            Map<ClusterSlot, SynthesisDoc> written,
            List<ListedSurvivor> survivors,
            Map<ClusterSlot, Unwritten> unwritten,
            boolean writingFinished,
            DeliverableLanguage language) {
        Map<ClusterSlot, ArrangedCluster> placeByCluster = new LinkedHashMap<>();
        for (RecordedCluster recorded : arrangement) {
            placeByCluster.put(slotOf(recorded), recorded.cluster());
        }
        Consumer<Consumer<List<ListedSurvivor>>> everySurvivorInOnePage = page -> page.accept(List.copyOf(survivors));
        return ArrangedSurvivors.reading(
                () -> topicsOf(arrangement, survivors),
                survivors::size,
                topic -> arrangement.stream()
                        .filter(recorded -> recorded.cluster().topic().equals(topic.topic()))
                        .toList(),
                topic -> survivors.stream()
                        .filter(survivor -> survivor.topic().equals(topic.topic()))
                        .toList(),
                cluster -> Optional.ofNullable(written.get(cluster)),
                cluster -> Optional.ofNullable(unwritten.get(cluster)),
                clusters -> {
                    Map<ClusterSlot, ArrangedCluster> places = new LinkedHashMap<>();
                    clusters.forEach(cluster -> {
                        if (placeByCluster.containsKey(cluster)) {
                            places.put(cluster, placeByCluster.get(cluster));
                        }
                    });
                    return places;
                },
                everySurvivorInOnePage,
                everySurvivorInOnePage,
                writingFinished,
                language);
    }

    /** The tree written from these lists over {@code corpusRoot}, with no pictures and no progress heard. */
    static Path write(
            Path workingDirectory,
            Path corpusRoot,
            List<RecordedCluster> arrangement,
            Map<ClusterSlot, SynthesisDoc> written,
            List<ListedSurvivor> survivors,
            Map<ClusterSlot, Unwritten> unwritten) {
        return write(workingDirectory, corpusRoot, arrangement, written, survivors, unwritten, SurvivorPictures.none());
    }

    /** The tree written from these lists over {@code corpusRoot}, each survivor's pictures from {@code pictures}. */
    static Path write(
            Path workingDirectory,
            Path corpusRoot,
            List<RecordedCluster> arrangement,
            Map<ClusterSlot, SynthesisDoc> written,
            List<ListedSurvivor> survivors,
            Map<ClusterSlot, Unwritten> unwritten,
            SurvivorPictures pictures) {
        return Deliverable.writeTo(
                workingDirectory,
                DeliverableRoot.UNNAMED,
                provenance(corpusRoot),
                of(arrangement, written, survivors, unwritten),
                pictures,
                DeliverableProgress.NONE);
    }

    /**
     * The tree written from these lists over {@code corpusRoot}, each cluster's page split where it is past {@code
     * ceiling} (ADR-303), each survivor's pictures from {@code pictures}, {@code progress} hearing what the writer tells.
     */
    static Path writeWithCeiling(
            Path workingDirectory,
            Path corpusRoot,
            List<RecordedCluster> arrangement,
            Map<ClusterSlot, SynthesisDoc> written,
            List<ListedSurvivor> survivors,
            Map<ClusterSlot, Unwritten> unwritten,
            SurvivorPictures pictures,
            ClusterPageCeiling ceiling,
            DeliverableProgress progress) {
        return Deliverable.writeTo(
                workingDirectory,
                DeliverableRoot.UNNAMED,
                provenance(corpusRoot),
                of(arrangement, written, survivors, unwritten),
                ListedCodeSets.NONE,
                pictures,
                ceiling,
                progress);
    }

    /**
     * As {@link #writeWithCeiling}, {@code plugins} drawing a rendering of their own beside the tree (ADR-333 §2, §4),
     * through the public way in {@code pipeline} hands the plugin beans to.
     */
    static Path writeWithCeiling(
            Path workingDirectory,
            Path corpusRoot,
            List<RecordedCluster> arrangement,
            Map<ClusterSlot, SynthesisDoc> written,
            List<ListedSurvivor> survivors,
            Map<ClusterSlot, Unwritten> unwritten,
            SurvivorPictures pictures,
            ClusterPageCeiling ceiling,
            List<Plugin> plugins) {
        return Deliverable.writeTo(
                workingDirectory,
                DeliverableRoot.UNNAMED,
                provenance(corpusRoot),
                of(arrangement, written, survivors, unwritten),
                ListedCodeSets.NONE,
                pictures,
                ceiling,
                plugins,
                DeliverableProgress.NONE);
    }

    /** As {@link #writeWithCeiling}, every page put in place by {@code moves}. */
    static Path writeWithCeiling(
            Path workingDirectory,
            Path corpusRoot,
            List<RecordedCluster> arrangement,
            Map<ClusterSlot, SynthesisDoc> written,
            List<ListedSurvivor> survivors,
            Map<ClusterSlot, Unwritten> unwritten,
            ClusterPageCeiling ceiling,
            DeliverableProgress progress,
            ReplacingMove moves) {
        return Deliverable.writeTo(
                workingDirectory,
                DeliverableRoot.UNNAMED,
                provenance(corpusRoot),
                of(arrangement, written, survivors, unwritten),
                ListedCodeSets.NONE,
                SurvivorPictures.none(),
                ceiling,
                progress,
                moves);
    }

    /** As {@link #write}, the drive's free bytes answered by {@code usableSpace} each time the writer asks. */
    static Path writeWithFreeSpace(
            Path workingDirectory,
            Path corpusRoot,
            List<RecordedCluster> arrangement,
            Map<ClusterSlot, SynthesisDoc> written,
            List<ListedSurvivor> survivors,
            LongSupplier usableSpace) {
        return Deliverable.writeTo(
                workingDirectory,
                DeliverableRoot.UNNAMED,
                provenance(corpusRoot),
                of(arrangement, written, survivors, Map.of()),
                SurvivorPictures.none(),
                DeliverableProgress.NONE,
                ReplacingMove.ATOMIC,
                usableSpace);
    }

    /** What produced the tree: the run, the walk, the archive's root and one profile key. */
    public static DeliverableProvenance provenance(Path corpusRoot) {
        return new DeliverableProvenance(RUN_ID, WALK, corpusRoot.toString(), List.of(new NamedValue("seedFolder", "seeds")));
    }

    /**
     * The topic these fixtures put every survivor won by {@code seed} under: one topic for each winning seed, undivided,
     * keyed by a path no seed has, so that a rendering that named a topic's folder or heading from its key and not its
     * name is told apart (ADR-409 sections 1 and 10).
     */
    public static TopicKey topicOf(OccurrenceId seed) {
        return new TopicKey("topic-of-seed-" + seed.value(), 0);
    }

    /**
     * The name these fixtures give the topic a survivor of {@code seedPath}'s seed is under: the seed's filename stem,
     * as a topic's name falls back to it (ADR-407 section 7), so that the folders drawn are named as the seed's
     * partition's were.
     */
    public static String topicNameOf(String seedPath) {
        return FilenameStem.of(seedPath);
    }

    /** One cluster of the topic of {@code seed}, placed first among the topics, its topic undivided. */
    public static RecordedCluster cluster(OccurrenceId seed, int ordinal, String label, int clusterOrder, int documentCount) {
        return new RecordedCluster(
                new ArrangedCluster(topicOf(seed), OptionalInt.empty(), ordinal, documentCount, 1, 0, clusterOrder),
                new ClusterLabel(label));
    }

    /**
     * One survivor won by {@code seed}, placed under its topic, named from {@code seedPath}, in the cluster of {@code
     * ordinal}.
     */
    public static ListedSurvivor survivor(
            long occurrence, String path, String contentHash, OccurrenceId seed, String seedPath, int ordinal, double score) {
        return new ListedSurvivor(
                new OccurrenceId(occurrence),
                new OccurrencePath(path),
                contentHash,
                seed,
                topicOf(seed),
                topicNameOf(seedPath),
                OptionalInt.empty(),
                ordinal,
                score);
    }

    /** The key a cluster is asked for by. */
    public static ClusterSlot slotOf(RecordedCluster recorded) {
        return ClusterSlot.of(recorded);
    }

    /** The key of the cluster of {@code ordinal} under the topic of {@code seed}. */
    public static ClusterSlot slotOf(OccurrenceId seed, int ordinal) {
        return new ClusterSlot(topicOf(seed), OptionalInt.empty(), ordinal);
    }

    /** The writing over a cluster, its call having sent {@code sent} in citation order. */
    public static SynthesisDoc writing(String title, String prose, long... sent) {
        List<OccurrenceId> occurrences = new ArrayList<>();
        for (long occurrence : sent) {
            occurrences.add(new OccurrenceId(occurrence));
        }
        return new SynthesisDoc(title, prose, occurrences);
    }

    /** {@code bytes}' SHA-256 as 64 lower-case hexadecimal characters, computed here with the JDK. */
    public static String sha256Of(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }

    /**
     * A real image of {@code format}, {@code png}, {@code jpg} or {@code gif}, {@code width} by {@code height}, written
     * by the JDK, so its bytes begin with the signature of its kind; its colours differ pixel by pixel.
     */
    public static byte[] anImage(String format, int width, int height) {
        BufferedImage image = new BufferedImage(
                width, height, format.equals("gif") ? BufferedImage.TYPE_BYTE_INDEXED : BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, (x * 37 + y * 91) << 8 | (x * 13));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("the JDK writes no " + format);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** Every regular file beneath {@code directory}, as paths relative to it with {@code /} between their parts. */
    public static List<String> filesUnder(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.walk(directory)) {
            return entries.filter(Files::isRegularFile)
                    .map(file -> directory.relativize(file).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    /**
     * The rows of {@code documents.csv}, header first, each split on commas outside double quotes and a doubled
     * quote read as one, the way a program loading the file splits it.
     */
    static List<List<String>> manifestOf(Path tree) throws IOException {
        return csvOf(tree.resolve("documents.csv"));
    }

    /**
     * The rows of the CSV file {@code file}, header first, each split as {@link #manifestOf} splits {@code
     * documents.csv}'s: {@code code.csv} is quoted as it is (ADR-296 §5).
     */
    static List<List<String>> csvOf(Path file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            if (!line.isBlank()) {
                rows.add(cellsOf(line));
            }
        }
        return rows;
    }

    /** The manifest's cells of one column, keyed by each row's {@code path}, read by the header's names. */
    static Map<String, String> columnByPath(Path tree, String column) throws IOException {
        List<List<String>> rows = manifestOf(tree);
        int path = rows.getFirst().indexOf("path");
        int wanted = rows.getFirst().indexOf(column);
        if (wanted < 0) {
            throw new IllegalStateException("documents.csv has no column " + column + ": " + rows.getFirst());
        }
        Map<String, String> byPath = new LinkedHashMap<>();
        for (List<String> row : rows.subList(1, rows.size())) {
            byPath.put(row.get(path), row.get(wanted));
        }
        return byPath;
    }

    private static List<String> cellsOf(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int at = 0; at < line.length(); at++) {
            char c = line.charAt(at);
            if (c == '"' && quoted && at + 1 < line.length() && line.charAt(at + 1) == '"') {
                cell.append('"');
                at++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    /**
     * One row for each topic the arrangement names, in the order its first cluster is listed, named as the first listed
     * survivor under it names it. A topic no survivor names has no name, and {@link ListedTopic} refuses it.
     */
    static List<ListedTopic> topicsOf(List<RecordedCluster> arrangement, List<ListedSurvivor> survivors) {
        Map<TopicKey, String> names = new LinkedHashMap<>();
        for (ListedSurvivor survivor : survivors) {
            names.putIfAbsent(survivor.topic(), survivor.topicName());
        }
        Map<TopicKey, List<RecordedCluster>> byTopic = new LinkedHashMap<>();
        for (RecordedCluster recorded : arrangement) {
            byTopic.computeIfAbsent(recorded.cluster().topic(), topic -> new ArrayList<>()).add(recorded);
        }
        List<ListedTopic> topics = new ArrayList<>();
        byTopic.forEach((topic, clusters) -> topics.add(new ListedTopic(
                topic, names.get(topic), clusters.getFirst().cluster().topicOrder(), clusters.size())));
        return topics;
    }
}
