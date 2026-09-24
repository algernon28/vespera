package io.algernon.vespera.similarity;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code similarity}'s corpus-wide half of stage 4 (ADR-079, ADR-081, ADR-082): candidate generation
 * over the signature-band index and the rare-shingle index, exact scoring against the stored
 * (boilerplate-stripped) shingle sets — never against the signatures themselves, since signatures
 * retrieve and shingle sets judge — and the two-phase resolution that writes {@code redundant-with}.
 *
 * <p><b>Candidate generation happens in SQL, against {@code shingle}, never by holding the corpus in a
 * heap-resident index.</b> {@code shingle} is the largest table in the database (its own schema.sql
 * comment says so), and {@code shingle_by_hash} — the index on {@code (run_id, shingle_parameter_identity,
 * shingle_hash)} — exists specifically so containment retrieval is a {@code GROUP BY ... HAVING} over it
 * (spec #75 §3). An in-memory posting list built once and read many times was the first shape this class
 * took, and it was rejected here rather than merely not considered: it would hold the whole shingle
 * corpus as a {@code Map<Long, List<Long>>} for the run of a single tasklet, which is exactly the "whole
 * corpus in memory" ADR-082's own "no scale or throughput test" note does not excuse, and it would leave
 * {@code shingle_by_hash} unread — an index nothing queries is worse than no index, since the next reader
 * assumes something does. This class instead loads documents' shingle sets on demand, within a fixed
 * budget (see {@link ShingleSetCache}), and asks the database for candidate occurrence ids and set
 * sizes directly.
 *
 * <p>Near-duplication resolves first, over connected components of pairs at or above {@link
 * RedundancyThresholds#nearDuplicateJaccard()}; containment resolves second, only over what survives
 * that first phase, so no pointer this class writes ever names an occurrence this same pass already
 * removed (ADR-079).
 */
@Component
public class RedundancyResolution {

    /**
     * How many shingle hashes {@link ShingleSetCache} holds at once, across every set it keeps: 16 Mi,
     * which is 128 MiB of {@code long}. Bounded deliberately, so the pass never drifts back toward
     * holding a corpus of any size. Bounded by hashes rather than by documents (#277): a count of 64
     * documents was sized on prose, and once tables were read (ADR-145) it evicted a legacy corpus's
     * whole 468 thousand distinct hashes over and over, when the budget below holds them all at once.
     */
    static final long SHINGLE_SET_CACHE_BUDGET_HASHES = 16L * 1024 * 1024;

    private final JdbcTemplate jdbcTemplate;
    private final Ledger ledger;

    public RedundancyResolution(JdbcTemplate jdbcTemplate, Ledger ledger) {
        this.jdbcTemplate = jdbcTemplate;
        this.ledger = ledger;
    }

    /**
     * Resolves every {@code redundant-with} verdict for {@code stage4RunId}: reads {@code stage2RunId}'s
     * shingle rows and {@code stage3RunId}'s document-frequency measurement, both stripped by {@code
     * boilerplateHashes}, and writes one verdict plus one {@code redundant_with} row per occurrence that
     * loses to another.
     */
    public void resolve(RunId stage4RunId, RunId stage3RunId, RunId stage2RunId, Set<Long> boilerplateHashes) {
        Set<Long> signedOccurrenceIds = loadSignedOccurrenceIds(stage4RunId);
        if (signedOccurrenceIds.isEmpty()) {
            return;
        }

        ShingleSetCache shingleSets = new ShingleSetCache(stage2RunId, boilerplateHashes);
        Set<Long> removed = resolveNearDuplicates(stage4RunId, stage2RunId, signedOccurrenceIds, shingleSets);
        resolveContainment(stage4RunId, stage3RunId, stage2RunId, signedOccurrenceIds, shingleSets, removed);
    }

    /**
     * Every occurrence a signature exists for under {@code stage4RunId} — the universe candidate
     * generation and resolution both range over. An occurrence absent here is exactly the all-boilerplate
     * case (ADR-080): {@link RedundancySignatures} wrote it no signature row, so it is silently absent
     * from candidate generation entirely rather than compared against with an empty set. Reading this off
     * {@code minhash_signature} rather than {@code shingle} is deliberate: it is one id per document, not
     * a whole shingle set, so holding the result in memory costs nothing like holding every document's
     * shingle set would (the class-level note explains why that alternative was rejected).
     */
    private Set<Long> loadSignedOccurrenceIds(RunId stage4RunId) {
        Set<Long> ids = new HashSet<>();
        jdbcTemplate.query(
                "SELECT DISTINCT occurrence_id FROM minhash_signature WHERE run_id = ?",
                resultSet -> {
                    ids.add(resultSet.getLong("occurrence_id"));
                },
                stage4RunId.value());
        return ids;
    }

    // -- Phase 1: near-duplication -------------------------------------------------------------

    private Set<Long> resolveNearDuplicates(
            RunId stage4RunId, RunId stage2RunId, Set<Long> signedOccurrenceIds, ShingleSetCache shingleSets) {
        RedundancyThresholds thresholds = RedundancyThresholds.DEFAULT;

        UnionFind unionFind = new UnionFind(signedOccurrenceIds);
        for (PairKey pair : nearDuplicateCandidates(stage4RunId)) {
            long[] a = shingleSets.get(pair.a());
            long[] b = shingleSets.get(pair.b());
            if (jaccard(a, b) >= thresholds.nearDuplicateJaccard()) {
                unionFind.union(pair.a(), pair.b());
            }
        }

        Set<Long> componentMembers = new HashSet<>();
        List<Set<Long>> resolvableComponents = new ArrayList<>();
        for (Set<Long> component : unionFind.components()) {
            if (component.size() < 2) {
                continue;
            }
            resolvableComponents.add(component);
            componentMembers.addAll(component);
        }

        Map<Long, OccurrenceProfile> profiles = loadOccurrenceProfiles(stage2RunId, componentMembers);
        Set<Long> removed = new HashSet<>();
        for (Set<Long> component : resolvableComponents) {
            // The survivor rule (ADR-079) picks one member of the component; every other member is then
            // scored directly against that survivor here, not against whichever neighbour first linked it
            // into the component. A three-member component might connect A-B and B-C without A-B ever
            // having been retrieved as a candidate itself; recomputing A's score against the actual
            // survivor, from the sets already in hand, is what keeps every written score honestly "the
            // exact similarity to the occurrence this row points at" rather than an edge that happened to
            // exist (ADR-082: no verdict may rest on anything other than an exact value).
            long survivor = survivorOf(component, profiles);
            long[] survivorSet = shingleSets.get(survivor);
            for (long member : component) {
                if (member == survivor) {
                    continue;
                }
                double score = jaccard(shingleSets.get(member), survivorSet);
                writeVerdict(
                        stage4RunId,
                        member,
                        survivor,
                        "near-duplicate",
                        score,
                        "near-duplicate of occurrence %d at Jaccard %.4f".formatted(survivor, score));
                removed.add(member);
            }
        }
        return removed;
    }

    /** Near-duplicate candidates: pairs sharing a bucket, read directly off {@code signature_band}. */
    private Set<PairKey> nearDuplicateCandidates(RunId stage4RunId) {
        Map<BandBucket, List<Long>> buckets = new HashMap<>();
        jdbcTemplate.query(
                "SELECT band_ordinal, band_hash, occurrence_id FROM signature_band WHERE run_id = ?",
                resultSet -> {
                    BandBucket bucket =
                            new BandBucket(resultSet.getInt("band_ordinal"), resultSet.getLong("band_hash"));
                    buckets.computeIfAbsent(bucket, ignored -> new ArrayList<>())
                            .add(resultSet.getLong("occurrence_id"));
                },
                stage4RunId.value());

        Set<PairKey> pairs = new HashSet<>();
        for (List<Long> occurrenceIds : buckets.values()) {
            if (occurrenceIds.size() < 2) {
                continue;
            }
            for (int i = 0; i < occurrenceIds.size(); i++) {
                for (int j = i + 1; j < occurrenceIds.size(); j++) {
                    pairs.add(PairKey.of(occurrenceIds.get(i), occurrenceIds.get(j)));
                }
            }
        }
        return pairs;
    }

    private static long survivorOf(Set<Long> component, Map<Long, OccurrenceProfile> profiles) {
        return component.stream()
                .min(Comparator.<Long>comparingLong(id -> -profiles.get(id).alphanumericCharCount())
                        .thenComparing(id -> profiles.get(id).creationTime())
                        .thenComparing(id -> profiles.get(id).path()))
                .orElseThrow();
    }

    /** Only the columns the survivor rule needs, for only the occurrences a component actually names. */
    private Map<Long, OccurrenceProfile> loadOccurrenceProfiles(RunId stage2RunId, Set<Long> occurrenceIds) {
        if (occurrenceIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> alphanumericCounts = new HashMap<>();
        String placeholders = occurrenceIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(stage2RunId.value());
        args.addAll(occurrenceIds);
        jdbcTemplate.query(
                "SELECT occurrence_id, alphanumeric_char_count FROM extraction_metric"
                        + " WHERE run_id = ? AND occurrence_id IN (" + placeholders + ")",
                resultSet -> {
                    alphanumericCounts.put(
                            resultSet.getLong("occurrence_id"), resultSet.getLong("alphanumeric_char_count"));
                },
                args.toArray());

        Map<Long, OccurrenceProfile> profiles = new HashMap<>();
        for (long occurrenceId : occurrenceIds) {
            long alphanumericCharCount = alphanumericCounts.getOrDefault(occurrenceId, 0L);
            OccurrenceFacts facts = ledger.factsFor(new OccurrenceId(occurrenceId))
                    .orElseThrow(() ->
                            new IllegalStateException("no file_occurrence recorded for id " + occurrenceId));
            profiles.put(
                    occurrenceId,
                    new OccurrenceProfile(alphanumericCharCount, facts.creationTime(), facts.path().value()));
        }
        return profiles;
    }

    // -- Phase 2: containment, over what phase 1 left standing ---------------------------------

    private void resolveContainment(
            RunId stage4RunId,
            RunId stage3RunId,
            RunId stage2RunId,
            Set<Long> signedOccurrenceIds,
            ShingleSetCache shingleSets,
            Set<Long> removed) {
        RedundancyThresholds thresholds = RedundancyThresholds.DEFAULT;
        Map<Long, Integer> documentFrequency = loadDocumentFrequency(stage3RunId);
        // One count per document for the whole pass, not one per pair: counting a spreadsheet's hundred
        // thousand shingles again for every document that names it as a candidate is what made this
        // pass run for hours once tables were read (ADR-145).
        Map<Long, Integer> rawSizes = new HashMap<>();

        for (long a : signedOccurrenceIds) {
            if (removed.contains(a)) {
                continue;
            }
            long[] setA = shingleSets.get(a);
            List<Long> rareHashes = rarestHashes(setA, documentFrequency, thresholds.rareShingleSampleSize());
            if (rareHashes.isEmpty()) {
                continue;
            }

            Set<Long> candidates = containmentCandidates(stage2RunId, rareHashes, thresholds.rareShingleHitCount());

            long bestContainer = -1;
            double bestScore = -1;
            for (long b : candidates) {
                if (b == a || removed.contains(b) || !signedOccurrenceIds.contains(b)) {
                    continue;
                }
                // A cheap, deliberately approximate pre-filter (a COUNT, not a fetched set): it compares
                // against b's raw shingle row count, which is always >= its true boilerplate-stripped size, so
                // it can only ever admit an extra candidate for exact scoring to reject, never wrongly
                // exclude a true one. The same "retrieval overshoots, scoring corrects" shape ADR-081
                // already accepts for LSH banding, applied here to the |B| > |A| guard.
                if (rawSizes.computeIfAbsent(b, id -> rawShingleSetSize(stage2RunId, id)) <= setA.length) {
                    continue;
                }
                long[] setB = shingleSets.get(b);
                if (setB.length <= setA.length) {
                    continue;
                }
                double containmentScore = containment(setA, setB);
                if (containmentScore < thresholds.containmentIndex()) {
                    continue;
                }
                // Multiple containers passing the cut is not addressed by ADR-079/081: this class picks
                // the highest-scoring one, ties broken by the lowest occurrence id, purely for a
                // deterministic single row under redundant_with's (occurrence_id, run_id) primary key.
                if (containmentScore > bestScore || (containmentScore == bestScore && b < bestContainer)) {
                    bestScore = containmentScore;
                    bestContainer = b;
                }
            }

            if (bestContainer >= 0) {
                writeVerdict(
                        stage4RunId,
                        a,
                        bestContainer,
                        "contained-in",
                        bestScore,
                        "contained in occurrence %d at containment %.4f".formatted(bestContainer, bestScore));
            }
        }
    }

    private static List<Long> rarestHashes(long[] setA, Map<Long, Integer> documentFrequency, int sampleSize) {
        return Arrays.stream(setA)
                .boxed()
                .filter(documentFrequency::containsKey)
                .sorted(Comparator.<Long>comparingInt(documentFrequency::get).thenComparingLong(Long::longValue))
                .limit(sampleSize)
                .toList();
    }

    /**
     * Containment candidates for one document A's {@code rareHashes} (ADR-081): a single {@code GROUP BY}
     * over {@code shingle}, using the {@code shingle_by_hash} index on {@code (run_id,
     * shingle_parameter_identity, shingle_hash)} — the lookup that index exists for. Nothing here holds a
     * posting list in memory; the database counts the hits and only rows meeting {@code hitThreshold}
     * come back at all.
     */
    private Set<Long> containmentCandidates(RunId stage2RunId, List<Long> rareHashes, int hitThreshold) {
        String placeholders = rareHashes.stream().map(hash -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(stage2RunId.value());
        args.add(ShingleParameters.DEFAULT.identity());
        args.addAll(rareHashes);
        args.add(hitThreshold);

        Set<Long> candidates = new HashSet<>();
        jdbcTemplate.query(
                "SELECT occurrence_id FROM shingle"
                        + " WHERE run_id = ? AND shingle_parameter_identity = ? AND shingle_hash IN (" + placeholders
                        + ")"
                        + " GROUP BY occurrence_id HAVING COUNT(DISTINCT shingle_hash) >= ?",
                resultSet -> {
                    candidates.add(resultSet.getLong("occurrence_id"));
                },
                args.toArray());
        return candidates;
    }

    /**
     * {@code b}'s raw shingle row count, repeats and boilerplate included — a {@code COUNT(*)}, never a
     * materialised set, used only as the cheap upper-bound pre-filter {@link #resolveContainment}
     * documents at its call site. A row count is never below the distinct, boilerplate-stripped size
     * that pre-filter stands in for, so it can only admit a candidate, never wrongly exclude one.
     *
     * <p>{@code COUNT(*)} rather than {@code COUNT(DISTINCT shingle_hash)}: the distinct count makes
     * SQLite read the whole run through {@code shingle_by_hash}, five seconds a call on a 2.2-million-row
     * table, where this one is answered from {@code shingle_by_occurrence} alone (#277).
     */
    private int rawShingleSetSize(RunId stage2RunId, long occurrenceId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shingle"
                        + " WHERE occurrence_id = ? AND run_id = ? AND shingle_parameter_identity = ?",
                Integer.class,
                occurrenceId,
                stage2RunId.value(),
                ShingleParameters.DEFAULT.identity());
        return count == null ? 0 : count;
    }

    private Map<Long, Integer> loadDocumentFrequency(RunId stage3RunId) {
        Map<Long, Integer> counts = new HashMap<>();
        jdbcTemplate.query(
                "SELECT shingle_hash, document_count FROM shingle_document_frequency"
                        + " WHERE run_id = ? AND shingle_parameter_identity = ?",
                resultSet -> {
                    counts.put(resultSet.getLong("shingle_hash"), resultSet.getInt("document_count"));
                },
                stage3RunId.value(),
                ShingleParameters.DEFAULT.identity());
        return counts;
    }

    // -- Shared reads and writes -----------------------------------------------------------------

    /**
     * Deletes every {@code redundant_with} row recorded under {@code stage4RunId} — the discard half
     * of ADR-115/ADR-116, for a step whose completion under this run is not recorded. The {@code
     * redundant-with} verdicts themselves are the ledger's own rows, discarded separately by the
     * caller that owns {@code verdict} (ADR-041).
     */
    public void discardForRun(RunId stage4RunId) {
        jdbcTemplate.update("DELETE FROM redundant_with WHERE run_id = ?", stage4RunId.value());
    }

    private void writeVerdict(
            RunId stage4RunId, long occurrenceId, long redundantWithOccurrenceId, String relation, double score, String reason) {
        ledger.verdict(new OccurrenceId(occurrenceId), stage4RunId, VerdictKind.REDUNDANT_WITH, reason);
        jdbcTemplate.update(
                "INSERT INTO redundant_with (occurrence_id, run_id, redundant_with_occurrence_id, relation, score)"
                        + " VALUES (?, ?, ?, ?, ?)",
                occurrenceId,
                stage4RunId.value(),
                redundantWithOccurrenceId,
                relation,
                score);
    }

    private static double jaccard(long[] a, long[] b) {
        int intersection = intersectionSize(a, b);
        int union = a.length + b.length - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private static double containment(long[] a, long[] b) {
        if (a.length == 0) {
            return 0.0;
        }
        return (double) intersectionSize(a, b) / a.length;
    }

    /** Two sorted, duplicate-free sets' shared hashes, counted in one merge pass over both. */
    private static int intersectionSize(long[] a, long[] b) {
        int count = 0;
        int i = 0;
        int j = 0;
        while (i < a.length && j < b.length) {
            int order = Long.compare(a[i], b[j]);
            if (order == 0) {
                count++;
                i++;
                j++;
            } else if (order < 0) {
                i++;
            } else {
                j++;
            }
        }
        return count;
    }

    /**
     * The boilerplate-stripped shingle sets exact scoring reads, loaded from {@code shingle} on demand
     * rather than for the whole corpus up front (see the class-level note on why), and kept while they
     * fit {@link #SHINGLE_SET_CACHE_BUDGET_HASHES}, least recently used evicted first.
     *
     * <p>A set is a sorted, duplicate-free {@code long[]}: 8 bytes a hash, where a {@code HashSet<Long>}
     * costs several times that, and two of them intersect in one merge pass. The budget counts hashes
     * rather than documents because a document's set can be anything from a few dozen hashes to a
     * spreadsheet's hundreds of thousands (#277). The set just asked for is always kept, even alone over
     * the budget, since scoring is about to read it.
     */
    private final class ShingleSetCache {

        private final RunId stage2RunId;
        private final Set<Long> boilerplateHashes;
        private final LinkedHashMap<Long, long[]> cache = new LinkedHashMap<>(16, 0.75f, true);
        private long hashesHeld;

        ShingleSetCache(RunId stage2RunId, Set<Long> boilerplateHashes) {
            this.stage2RunId = stage2RunId;
            this.boilerplateHashes = boilerplateHashes;
        }

        long[] get(long occurrenceId) {
            long[] held = cache.get(occurrenceId);
            if (held != null) {
                return held;
            }
            long[] loaded = load(occurrenceId);
            cache.put(occurrenceId, loaded);
            hashesHeld += loaded.length;
            Iterator<Map.Entry<Long, long[]>> eldestFirst = cache.entrySet().iterator();
            while (hashesHeld > SHINGLE_SET_CACHE_BUDGET_HASHES && cache.size() > 1) {
                hashesHeld -= eldestFirst.next().getValue().length;
                eldestFirst.remove();
            }
            return loaded;
        }

        private long[] load(long occurrenceId) {
            LongArray distinctive = new LongArray();
            // Not DISTINCT: asked for it, SQLite reads the whole run through shingle_by_hash instead of this
            // document's rows through shingle_by_occurrence (#277). The sort below makes them distinct.
            jdbcTemplate.query(
                    "SELECT shingle_hash FROM shingle"
                            + " WHERE occurrence_id = ? AND run_id = ? AND shingle_parameter_identity = ?",
                    resultSet -> {
                        long hash = resultSet.getLong("shingle_hash");
                        if (!boilerplateHashes.contains(hash)) {
                            distinctive.add(hash);
                        }
                    },
                    occurrenceId,
                    stage2RunId.value(),
                    ShingleParameters.DEFAULT.identity());
            long[] sorted = distinctive.toArray();
            Arrays.sort(sorted);
            return withoutRepeats(sorted);
        }
    }

    /** {@code sorted} with each run of equal values kept once. */
    private static long[] withoutRepeats(long[] sorted) {
        int kept = 0;
        for (int i = 0; i < sorted.length; i++) {
            if (i == 0 || sorted[i] != sorted[i - 1]) {
                sorted[kept++] = sorted[i];
            }
        }
        return Arrays.copyOf(sorted, kept);
    }

    /** A growable array of primitive longs, so a set of hundreds of thousands of hashes is never boxed. */
    private static final class LongArray {

        private long[] values = new long[256];
        private int size;

        void add(long value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = value;
        }

        long[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }

    /** One LSH bucket: a band ordinal and the band hash every occurrence in the bucket shares. */
    private record BandBucket(int bandOrdinal, long bandHash) {}

    /** An unordered pair of occurrence ids, normalised so {@code (x, y)} and {@code (y, x)} collide. */
    private record PairKey(long a, long b) {
        static PairKey of(long x, long y) {
            return x < y ? new PairKey(x, y) : new PairKey(y, x);
        }
    }

    /** What the near-duplicate survivor rule reads about one occurrence (ADR-079). */
    private record OccurrenceProfile(long alphanumericCharCount, Instant creationTime, String path) {}
}
