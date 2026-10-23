package io.algernon.vespera.deliverable;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.ReplacingMove;
import io.algernon.vespera.synthesis.ArrangedCluster;
import io.algernon.vespera.synthesis.ClusterLabel;
import io.algernon.vespera.synthesis.ClusterSlot;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.algernon.vespera.synthesis.Unwritten;
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
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * An arrangement, its writing and its survivors held as lists, answering {@link ArrangedSurvivors} from them, and
 * what the tests of the tree read back out of what was written (ADR-274).
 *
 * <p>{@code src/main} reads one seed partition at a time and holds no such lists (ADR-223). A test's arrangement is a
 * handful of clusters, so it may hold all of it. A partition is the clusters sharing a winning seed, in the order
 * listed, its seed's path the one the first listed survivor of that seed carries; the survivors come in the order
 * listed.
 */
final class ListedTree {

    /** A run id of the shape the ledger mints, which names the tree (ADR-103). */
    static final String RUN_ID = "a".repeat(48) + "0123456789abcdef";

    /** The walk the run read, stated on the index. */
    static final long WALK = 7L;

    private ListedTree() {}

    /** The source over these lists, every survivor in one page, its writing unfinished and in English. */
    static ArrangedSurvivors of(
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
    static ArrangedSurvivors of(
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
                () -> partitionsOf(arrangement, survivors),
                survivors::size,
                partition -> arrangement.stream()
                        .filter(recorded -> recorded.cluster().winningSeed().equals(partition.winningSeed()))
                        .toList(),
                partition -> survivors.stream()
                        .filter(survivor -> survivor.winningSeed().equals(partition.winningSeed()))
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
    static DeliverableProvenance provenance(Path corpusRoot) {
        return new DeliverableProvenance(RUN_ID, WALK, corpusRoot.toString(), List.of(new NamedValue("seedFolder", "seeds")));
    }

    /** One cluster of {@code seed}'s partition, placed first among the partitions. */
    static RecordedCluster cluster(OccurrenceId seed, int ordinal, String label, int clusterOrder, int documentCount) {
        return new RecordedCluster(new ArrangedCluster(seed, ordinal, documentCount, 1, clusterOrder), new ClusterLabel(label));
    }

    /** One survivor of {@code seed}'s partition, in the cluster of {@code ordinal}. */
    static ListedSurvivor survivor(
            long occurrence, String path, String contentHash, OccurrenceId seed, String seedPath, int ordinal, double score) {
        return new ListedSurvivor(
                new OccurrenceId(occurrence), new OccurrencePath(path), contentHash, seed, seedPath, ordinal, score);
    }

    /** The key a cluster is asked for by. */
    static ClusterSlot slotOf(RecordedCluster recorded) {
        return new ClusterSlot(recorded.cluster().winningSeed(), recorded.cluster().ordinal());
    }

    /** The writing over a cluster, its call having sent {@code sent} in citation order. */
    static SynthesisDoc writing(String title, String prose, long... sent) {
        List<OccurrenceId> occurrences = new ArrayList<>();
        for (long occurrence : sent) {
            occurrences.add(new OccurrenceId(occurrence));
        }
        return new SynthesisDoc(title, prose, occurrences);
    }

    /** {@code bytes}' SHA-256 as 64 lower-case hexadecimal characters, computed here with the JDK. */
    static String sha256Of(byte[] bytes) {
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
    static byte[] anImage(String format, int width, int height) {
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
    static List<String> filesUnder(Path directory) throws IOException {
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

    private static List<ListedPartition> partitionsOf(List<RecordedCluster> arrangement, List<ListedSurvivor> survivors) {
        Map<OccurrenceId, String> seedPaths = new LinkedHashMap<>();
        for (ListedSurvivor survivor : survivors) {
            seedPaths.putIfAbsent(survivor.winningSeed(), survivor.seedPath());
        }
        Map<OccurrenceId, List<RecordedCluster>> bySeed = new LinkedHashMap<>();
        for (RecordedCluster recorded : arrangement) {
            bySeed.computeIfAbsent(recorded.cluster().winningSeed(), seed -> new ArrayList<>()).add(recorded);
        }
        List<ListedPartition> partitions = new ArrayList<>();
        bySeed.forEach((seed, clusters) -> partitions.add(new ListedPartition(
                seed, seedPaths.get(seed), clusters.getFirst().cluster().partitionOrder(), clusters.size())));
        return partitions;
    }
}
