package io.algernon.vespera.similarity;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
 * assumes something does. This class instead loads at most one document's shingle set at a time (see
 * {@link ShingleSetCache}), and asks the database for candidate occurrence ids and set sizes directly.
 *
 * <p>Near-duplication resolves first, over connected components of pairs at or above {@link
 * RedundancyThresholds#nearDuplicateJaccard()}; containment resolves second, only over what survives
 * that first phase, so no pointer this class writes ever names an occurrence this same pass already
 * removed (ADR-079).
 */
@Component
public class RedundancyResolution {

    /**
     * How many boilerplate-stripped shingle sets {@link ShingleSetCache} keeps at once. Bounded
     * deliberately: a near-duplicate component or a containment scan only ever revisits a handful of
     * documents repeatedly (a survivor, scored against every other member of its component; a document
     * A, scored against a short candidate list), never the whole corpus, so a small bound is enough to
     * avoid re-reading the same set twice without drifting back toward holding everything.
     */
    private static final int SHINGLE_SET_CACHE_CAPACITY = 64;

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
            Set<Long> a = shingleSets.get(pair.a());
            Set<Long> b = shingleSets.get(pair.b());
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
            Set<Long> survivorSet = shingleSets.get(survivor);
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

        for (long a : signedOccurrenceIds) {
            if (removed.contains(a)) {
                continue;
            }
            Set<Long> setA = shingleSets.get(a);
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
                // against b's raw shingle count, which is always >= its true boilerplate-stripped size, so
                // it can only ever admit an extra candidate for exact scoring to reject, never wrongly
                // exclude a true one. The same "retrieval overshoots, scoring corrects" shape ADR-081
                // already accepts for LSH banding, applied here to the |B| > |A| guard.
                if (rawShingleSetSize(stage2RunId, b) <= setA.size()) {
                    continue;
                }
                Set<Long> setB = shingleSets.get(b);
                if (setB.size() <= setA.size()) {
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

    private static List<Long> rarestHashes(Set<Long> setA, Map<Long, Integer> documentFrequency, int sampleSize) {
        return setA.stream()
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
     * {@code b}'s raw (not boilerplate-stripped) distinct shingle count — a {@code COUNT(DISTINCT ...)}
     * query, never a materialised set, used only as the cheap upper-bound pre-filter {@link
     * #resolveContainment} documents at its call site.
     */
    private int rawShingleSetSize(RunId stage2RunId, long occurrenceId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT shingle_hash) FROM shingle"
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

    private static double jaccard(Set<Long> a, Set<Long> b) {
        int intersection = intersectionSize(a, b);
        int union = a.size() + b.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private static double containment(Set<Long> a, Set<Long> b) {
        if (a.isEmpty()) {
            return 0.0;
        }
        return (double) intersectionSize(a, b) / a.size();
    }

    private static int intersectionSize(Set<Long> a, Set<Long> b) {
        Set<Long> smaller = a.size() <= b.size() ? a : b;
        Set<Long> larger = a.size() <= b.size() ? b : a;
        int count = 0;
        for (long hash : smaller) {
            if (larger.contains(hash)) {
                count++;
            }
        }
        return count;
    }

    /**
     * A small bounded cache of one document's boilerplate-stripped shingle set at a time, loaded from
     * {@code shingle} on demand rather than for the whole corpus up front (see the class-level note on
     * why). Exact scoring only ever needs the pair currently being judged, or a survivor scored
     * repeatedly against every other member of its own component — never the corpus at large — so a
     * small least-recently-used bound is enough to avoid re-issuing the same query inside one
     * component or one containment scan, without drifting back toward holding everything.
     */
    private final class ShingleSetCache {

        private final RunId stage2RunId;
        private final Set<Long> boilerplateHashes;
        private final Map<Long, Set<Long>> cache;

        ShingleSetCache(RunId stage2RunId, Set<Long> boilerplateHashes) {
            this.stage2RunId = stage2RunId;
            this.boilerplateHashes = boilerplateHashes;
            this.cache = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Set<Long>> eldest) {
                    return size() > SHINGLE_SET_CACHE_CAPACITY;
                }
            };
        }

        Set<Long> get(long occurrenceId) {
            return cache.computeIfAbsent(occurrenceId, this::load);
        }

        private Set<Long> load(long occurrenceId) {
            Set<Long> distinctive = new HashSet<>();
            jdbcTemplate.query(
                    "SELECT DISTINCT shingle_hash FROM shingle"
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
            return distinctive;
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
