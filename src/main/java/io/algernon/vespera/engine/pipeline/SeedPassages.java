package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.embedding.SeedChunks;
import io.algernon.vespera.engine.embedding.SeedVectors;
import io.algernon.vespera.engine.embedding.UnusableSeed;
import io.algernon.vespera.engine.embedding.UnusableSeeds;
import io.algernon.vespera.engine.extraction.ChunkingRule;
import io.algernon.vespera.engine.extraction.ExtractionCacheKeys;
import io.algernon.vespera.engine.extraction.HybridChunker;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.ListedPages;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.RunId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What the seeds' passages are read from, for the two steps that find the topics from them: stage 5's scoring step,
 * where they are found (ADR-399 section 6), and the arrangement step, which names them (ADR-403 section 1). Each reads
 * the same rows through this one class, built by each from the beans it holds, so that the topics the second names are the topics the first found.
 */
class SeedPassages {

    private final Ledger ledger;
    private final HybridChunker hybridChunker;
    private final UnusableSeeds unusableSeeds;
    private final ExtractionCacheKeys cacheKeys;

    SeedPassages(Ledger ledger, HybridChunker hybridChunker, UnusableSeeds unusableSeeds, JdbcTemplate jdbcTemplate) {
        this.ledger = ledger;
        this.hybridChunker = hybridChunker;
        this.unusableSeeds = unusableSeeds;
        this.cacheKeys = new ExtractionCacheKeys(jdbcTemplate);
    }

    /**
     * Every usable seed's own content hash, read once from the key seed extraction recorded under the
     * measurement run (ADR-206 section 3), and how many chunks the chunker cut it into, so {@link
     * RelevanceScoring} never has to touch a file. Which seeds that leaves out is {@code embedding}'s
     * rule (ADR-231 section 2a), not this method's.
     */
    Map<OccurrenceId, SeedChunks> contentHashes(
            SeedGate.SeedWalk seedWalk, RunId measurementRun, String stage) {
        Set<OccurrenceId> allSeeds = TimedStatement.of(
                stage, "reading", "read", "the seed walk's occurrences", () -> {
                    Set<OccurrenceId> ids = new HashSet<>();
                    for (OccurrenceId id : ledger.occurrences().occurrencesOf(seedWalk.walkId())) {
                        ids.add(id);
                    }
                    return ids;
                });
        Set<OccurrenceId> unusable = TimedStatement.of(stage, "reading", "read", "the unusable seeds", () -> unusableSeeds.forRun(measurementRun))
                .stream()
                .map(UnusableSeed::occurrenceId)
                .collect(Collectors.toSet());
        allSeeds.removeAll(unusable);

        // Asked a page of seeds to a statement, the keys and then their chunk counts (ADR-245), and counted as
        // each page is read, so that the counter moves while the reads are made (ADR-246).
        StageProgress read =
                StageProgress.over(stage.replaceFirst("\\)$", ", seed cache keys read)"), allSeeds.size());
        Map<OccurrenceId, SeedChunks> contentHashes = new LinkedHashMap<>();
        ListedPages.each(allSeeds, page -> {
            Map<OccurrenceId, String> keys = cacheKeys.requireForOccurrences(page, measurementRun);
            Map<String, Integer> chunkCounts =
                    hybridChunker.chunkCounts(new HashSet<>(keys.values()), ChunkingRule.DEFAULT);
            for (OccurrenceId seedOccurrenceId : page) {
                String contentHash = keys.get(seedOccurrenceId);
                contentHashes.put(
                        seedOccurrenceId, new SeedChunks(contentHash, chunkCounts.getOrDefault(contentHash, 0)));
                read.itemDone();
            }
        });
        return contentHashes;
    }

    /**
     * The seeds' vectors by each seed's path in the seed walk, for ADR-399's topics: no embedding call and no read of
     * its own beyond the paths. A seed with no resident vector has no passage.
     */
    Map<String, SeedVectors> vectorsByPath(
            Map<OccurrenceId, SeedChunks> seedContentHashes,
            Map<OccurrenceId, List<float[]>> residentSeedVectors) {
        Map<OccurrenceId, OccurrencePath> paths = ledger.occurrences().pathsOf(residentSeedVectors.keySet());
        Map<String, SeedVectors> bySeedPath = new LinkedHashMap<>();
        for (Map.Entry<OccurrenceId, List<float[]>> seed : residentSeedVectors.entrySet()) {
            OccurrencePath path = paths.get(seed.getKey());
            SeedChunks chunks = seedContentHashes.get(seed.getKey());
            if (path != null && chunks != null) {
                bySeedPath.put(path.value(), new SeedVectors(chunks.contentHash(), seed.getValue()));
            }
        }
        return bySeedPath;
    }
}
