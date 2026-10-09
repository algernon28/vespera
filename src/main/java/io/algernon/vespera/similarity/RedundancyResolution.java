package io.algernon.vespera.similarity;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.StatementSteps;
import io.algernon.vespera.ledger.VerdictKind;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;
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
 * (spec #75 §3). It exists only from stage 4b on: stage 2 writes shingles without it and stage 4b builds
 * it, whole, before this class's first read (ADR-182; {@link ShingleHashIndex}). An in-memory posting list built once and read many times was the first shape this class
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
 *
 * <p><b>Nothing here holds a set of every signed occurrence, of every band row, of every pair, of every
 * removal or of every frequency (ADR-220 section 4).</b> The signed occurrences are counted once and read a
 * page of 1,000 at a time; the pairs of a page are found by one statement and let go before the next page;
 * whether an occurrence is signed or was removed is asked of the database by key; an occurrence's rarest
 * shingles are found with its own rows; and what is still held is the components of the pairs at or above
 * the cut with their members' profiles, and the shingle-set cache within its budget.
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

    /** The most occurrences one page, or one statement naming occurrences, holds: the ledger's own page. */
    static final int OCCURRENCES_IN_A_PAGE = 1_000;

    /**
     * What a raw shingle count is charged against {@link #SHINGLE_SET_CACHE_BUDGET_HASHES}, in hashes: the 75
     * bytes a map entry for it was measured to hold, at 8 bytes a hash, rounded up (ADR-220 section 4).
     */
    static final long RAW_COUNT_CHARGE_HASHES = 10;

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
     * loses to another. {@code alphanumericCounts} is how the survivor rule learns how much text each
     * member of a component holds (ADR-209 section 3.2).
     */
    public void resolve(
            RunId stage4RunId,
            RunId stage3RunId,
            RunId stage2RunId,
            Set<Long> boilerplateHashes,
            AlphanumericCounts alphanumericCounts) {
        resolve(
                stage4RunId, stage3RunId, stage2RunId, boilerplateHashes, alphanumericCounts, ResolutionProgress.NONE);
    }

    /**
     * As {@link #resolve(RunId, RunId, RunId, Set, AlphanumericCounts)}, and tells {@code progress} about each loop (ADR-192
     * section 5): once before its first item with its total, zero included, and after each item. It also
     * tells it about the two reads among the loops (ADR-193 section 7, ADR-204 sections 3 and 4, ADR-220
     * section 4), each started once before it and ended once after it, and not ended where it throws: the
     * signed occurrences, counted, first of all, started with the span of the run's rows or with an empty
     * total where it holds none; then the near-duplicates' extraction metrics (only where a component holds
     * a member), timed. Returns before any loop, having reported the first read alone, when no occurrence is
     * signed.
     */
    public void resolve(
            RunId stage4RunId,
            RunId stage3RunId,
            RunId stage2RunId,
            Set<Long> boilerplateHashes,
            AlphanumericCounts alphanumericCounts,
            ResolutionProgress progress) {
        long signedOccurrences = countSignedOccurrences(stage4RunId, progress);
        if (signedOccurrences == 0) {
            return;
        }

        ShingleSetCache shingleSets = new ShingleSetCache(stage2RunId, boilerplateHashes);
        resolveNearDuplicates(stage4RunId, stage2RunId, signedOccurrences, shingleSets, alphanumericCounts, progress);
        resolveContainment(
                stage4RunId, stage3RunId, stage2RunId, boilerplateHashes, signedOccurrences, shingleSets, progress);
    }

    /**
     * How many occurrences a signature exists for under {@code stage4RunId} — the universe candidate
     * generation and resolution both range over. An occurrence absent here is exactly the all-boilerplate
     * case (ADR-080): {@link RedundancySignatures} wrote it no signature row, so it is silently absent
     * from candidate generation entirely rather than compared against with an empty set. Counted off {@code
     * minhash_signature} as the rows are read, and none kept (ADR-220 section 4): the passes below go through
     * the signed occurrences a page at a time.
     */
    private long countSignedOccurrences(RunId stage4RunId, ResolutionProgress progress) {
        // Counted by SQLite's progress handler (ADR-193): the one read of this class whose rows are all
        // returned and whose total is the span of the run's rowids. It runs on the connection the template
        // hands over and is never handed back to it.
        progress.statementStarting(SimilarityStatement.SIGNED_OCCURRENCES, spanOfRun("minhash_signature", stage4RunId));
        long signed = StatementSteps.counted(
                jdbcTemplate,
                steps -> progress.stepsTaken(SimilarityStatement.SIGNED_OCCURRENCES, steps),
                connection -> {
                    long found = 0;
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT DISTINCT occurrence_id FROM minhash_signature WHERE run_id = ?")) {
                        statement.setString(1, stage4RunId.value());
                        try (ResultSet resultSet = statement.executeQuery()) {
                            while (resultSet.next()) {
                                found++;
                            }
                        }
                    }
                    return found;
                });
        progress.statementEnded(SimilarityStatement.SIGNED_OCCURRENCES);
        return signed;
    }

    /**
     * Hands the signed occurrences of {@code stage4RunId} to {@code page}, a page of up to {@value
     * #OCCURRENCES_IN_A_PAGE} at a time in the order their signatures were written, each page once its
     * statement has finished (ADR-220 section 4). Planned by {@code minhash_signature_by_run_id (run_id=? AND
     * rowid>?)}, sorting nothing.
     */
    private void eachPageOfSigned(RunId stage4RunId, Consumer<List<Long>> page) {
        long after = Long.MIN_VALUE;
        while (true) {
            List<SignedRow> rows = jdbcTemplate.query(
                    "SELECT rowid, occurrence_id FROM minhash_signature WHERE run_id = ? AND rowid > ?"
                            + " ORDER BY rowid LIMIT " + OCCURRENCES_IN_A_PAGE,
                    (resultSet, rowNumber) -> new SignedRow(resultSet.getLong(1), resultSet.getLong(2)),
                    stage4RunId.value(),
                    after);
            if (!rows.isEmpty()) {
                page.accept(rows.stream().map(SignedRow::occurrenceId).toList());
                after = rows.getLast().rowid();
            }
            if (rows.size() < OCCURRENCES_IN_A_PAGE) {
                return;
            }
        }
    }

    /**
     * The span of {@code run}'s rowids in {@code table}, greatest less least plus one, as two statements and
     * never one (ADR-191 section 2): each is one descent of the index on {@code run_id} alone. Empty for a
     * run that holds no row. {@code table} is a constant of this class, never a caller's text.
     */
    private OptionalLong spanOfRun(String table, RunId run) {
        Long least = jdbcTemplate.queryForObject(
                "SELECT MIN(rowid) FROM " + table + " WHERE run_id = ?", Long.class, run.value());
        Long greatest = jdbcTemplate.queryForObject(
                "SELECT MAX(rowid) FROM " + table + " WHERE run_id = ?", Long.class, run.value());
        if (least == null || greatest == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(greatest - least + 1);
    }

    // -- Phase 1: near-duplication -------------------------------------------------------------

    private void resolveNearDuplicates(
            RunId stage4RunId,
            RunId stage2RunId,
            long signedOccurrences,
            ShingleSetCache shingleSets,
            AlphanumericCounts alphanumericCounts,
            ResolutionProgress progress) {
        RedundancyThresholds thresholds = RedundancyThresholds.DEFAULT;

        UnionFind unionFind = new UnionFind();
        progress.toScorePairs(signedOccurrences);
        eachPageOfSigned(stage4RunId, page -> {
            for (PairKey pair : candidatePairsOf(stage4RunId, page)) {
                long[] a = shingleSets.get(pair.a());
                long[] b = shingleSets.get(pair.b());
                if (jaccard(a, b) >= thresholds.nearDuplicateJaccard()) {
                    unionFind.union(pair.a(), pair.b());
                }
            }
            // Told together, once every pair whose lesser member is in the page has been scored.
            for (int scored = 0; scored < page.size(); scored++) {
                progress.candidatesScored();
            }
        });

        Set<Long> componentMembers = new HashSet<>();
        List<Set<Long>> resolvableComponents = new ArrayList<>();
        for (Set<Long> component : unionFind.components()) {
            resolvableComponents.add(component);
            componentMembers.addAll(component);
        }

        Map<Long, OccurrenceProfile> profiles = loadOccurrenceProfiles(stage2RunId, componentMembers, alphanumericCounts, progress);
        progress.toResolveComponents(resolvableComponents.size());
        // Summed and announced once: every member of every component but its survivor (ADR-192 section 5).
        progress.toWriteNearDuplicateVerdicts(componentMembers.size() - resolvableComponents.size());
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
                progress.nearDuplicateVerdictWritten();
            }
            progress.componentResolved();
        }
    }

    /**
     * The near-duplicate candidates of a page of signed occurrences: every pair sharing a bucket whose lesser
     * member is in {@code page}, each once (ADR-220 section 4). {@code CROSS JOIN} fixes the order SQLite
     * reads the two sides in, the page's rows by the table's key and then the bucket by its index; a plain
     * join was measured to read every band row of the run for every page.
     */
    private List<PairKey> candidatePairsOf(RunId stage4RunId, List<Long> page) {
        List<Object> arguments = new ArrayList<>();
        arguments.add(stage4RunId.value());
        arguments.addAll(page);
        return jdbcTemplate.query(
                "SELECT DISTINCT a.occurrence_id AS lesser, b.occurrence_id AS greater"
                        + " FROM signature_band a CROSS JOIN signature_band b"
                        + " ON b.run_id = a.run_id AND b.band_ordinal = a.band_ordinal"
                        + " AND b.band_hash = a.band_hash AND b.occurrence_id > a.occurrence_id"
                        + " WHERE a.run_id = ? AND a.occurrence_id IN (" + placeholders(page.size()) + ")",
                (resultSet, rowNumber) -> new PairKey(resultSet.getLong("lesser"), resultSet.getLong("greater")),
                arguments.toArray());
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    private static long survivorOf(Set<Long> component, Map<Long, OccurrenceProfile> profiles) {
        return component.stream()
                .min(Comparator.<Long>comparingLong(id -> -profiles.get(id).alphanumericCharCount())
                        .thenComparing(id -> profiles.get(id).creationTime())
                        .thenComparing(id -> profiles.get(id).path()))
                .orElseThrow();
    }

    /** Only the columns the survivor rule needs, for only the occurrences a component actually names. */
    private Map<Long, OccurrenceProfile> loadOccurrenceProfiles(
            RunId stage2RunId,
            Set<Long> occurrenceIds,
            AlphanumericCounts alphanumericCounts,
            ResolutionProgress progress) {
        progress.toReadProfiles(occurrenceIds.size());
        if (occurrenceIds.isEmpty()) {
            return Map.of();
        }
        // Timed: an IN list has no cheap total (ADR-193 section 1). The statement is extraction's (ADR-209).
        progress.statementStarting(SimilarityStatement.NEAR_DUPLICATE_METRICS, OptionalLong.empty());
        Map<OccurrenceId, Long> counts = alphanumericCounts.recordedUnder(
                stage2RunId, occurrenceIds.stream().map(OccurrenceId::new).toList());
        progress.statementEnded(SimilarityStatement.NEAR_DUPLICATE_METRICS);

        Map<Long, OccurrenceProfile> profiles = new HashMap<>();
        for (long occurrenceId : occurrenceIds) {
            long alphanumericCharCount = counts.getOrDefault(new OccurrenceId(occurrenceId), 0L);
            OccurrenceFacts facts = ledger.occurrences().factsFor(new OccurrenceId(occurrenceId))
                    .orElseThrow(() ->
                            new IllegalStateException("no file_occurrence recorded for id " + occurrenceId));
            profiles.put(
                    occurrenceId,
                    new OccurrenceProfile(alphanumericCharCount, facts.creationTime(), facts.path().value()));
            progress.profileRead();
        }
        return profiles;
    }

    // -- Phase 2: containment, over what phase 1 left standing ---------------------------------

    private void resolveContainment(
            RunId stage4RunId,
            RunId stage3RunId,
            RunId stage2RunId,
            Set<Long> boilerplateHashes,
            long signedOccurrences,
            ShingleSetCache shingleSets,
            ResolutionProgress progress) {
        RedundancyThresholds thresholds = RedundancyThresholds.DEFAULT;
        progress.toCheckForContainment(signedOccurrences);

        eachPageOfSigned(stage4RunId, page -> {
            // Asked of the database, a page at a time, what phase 1 removed (ADR-220 section 4).
            Set<Long> removedInThePage = removedAmong(stage4RunId, page);
            for (long a : page) {
                if (removedInThePage.contains(a)) {
                    progress.checkedForContainment();
                    continue;
                }
                long[] setA = shingleSets.get(a);
                List<Long> rareHashes = rarestHashes(
                        stage3RunId, stage2RunId, a, boilerplateHashes, thresholds.rareShingleSampleSize());
                if (rareHashes.isEmpty()) {
                    progress.checkedForContainment();
                    continue;
                }

                Set<Long> candidates = containmentCandidates(stage2RunId, rareHashes, thresholds.rareShingleHitCount());
                // Which of A's candidates are signed, and which phase 1 removed, asked by key.
                List<Long> others = candidates.stream().filter(b -> b != a).toList();
                Set<Long> signed = signedAmong(stage4RunId, others);
                Set<Long> removedCandidates = removedAmong(stage4RunId, others);

                long bestContainer = -1;
                double bestScore = -1;
                for (long b : candidates) {
                    // Every path out of one candidate is counted, the ones that skip it included.
                    boolean skipped = b == a || removedCandidates.contains(b) || !signed.contains(b);
                    if (skipped) {
                        progress.containmentCandidateGoneThrough();
                        continue;
                    }
                    // A cheap, deliberately approximate pre-filter (a COUNT, not a fetched set): it compares
                    // against b's raw shingle row count, which is always >= its true boilerplate-stripped size, so
                    // it can only ever admit an extra candidate for exact scoring to reject, never wrongly
                    // exclude a true one. The same "retrieval overshoots, scoring corrects" shape ADR-081
                    // already accepts for LSH banding, applied here to the |B| > |A| guard.
                    if (shingleSets.rawSize(b) <= setA.length) {
                        progress.containmentCandidateGoneThrough();
                        continue;
                    }
                    long[] setB = shingleSets.get(b);
                    if (setB.length <= setA.length) {
                        progress.containmentCandidateGoneThrough();
                        continue;
                    }
                    double containmentScore = containment(setA, setB);
                    if (containmentScore < thresholds.containmentIndex()) {
                        progress.containmentCandidateGoneThrough();
                        continue;
                    }
                    // Multiple containers passing the cut is not addressed by ADR-079/081: this class picks
                    // the highest-scoring one, ties broken by the lowest occurrence id, purely for a
                    // deterministic single row under redundant_with's (occurrence_id, run_id) primary key.
                    if (containmentScore > bestScore || (containmentScore == bestScore && b < bestContainer)) {
                        bestScore = containmentScore;
                        bestContainer = b;
                    }
                    progress.containmentCandidateGoneThrough();
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
                progress.checkedForContainment();
            }
        });
    }

    /**
     * The hashes of {@code occurrenceId} that stage 3 counted, rarest first, at most {@code sampleSize} of
     * them (ADR-220 section 4): found with the occurrence's own shingle rows and the frequency rows they
     * name, in one statement that orders them by their document count and then by the hash, so nothing of
     * stage 3's measurement is held. A hash in {@code boilerplateHashes} is passed over and a hash with no
     * frequency row is in neither, which is the set the sort of the occurrence's distinctive hashes by a
     * map of every frequency gave. Both tables are this module's.
     */
    private List<Long> rarestHashes(
            RunId stage3RunId, RunId stage2RunId, long occurrenceId, Set<Long> boilerplateHashes, int sampleSize) {
        return jdbcTemplate.query(
                "SELECT f.shingle_hash FROM shingle s JOIN shingle_document_frequency f"
                        + " ON f.run_id = ? AND f.shingle_parameter_identity = s.shingle_parameter_identity"
                        + " AND f.shingle_hash = s.shingle_hash"
                        + " WHERE s.occurrence_id = ? AND s.run_id = ? AND s.shingle_parameter_identity = ?"
                        + " GROUP BY f.shingle_hash ORDER BY f.document_count, f.shingle_hash",
                resultSet -> {
                    List<Long> taken = new ArrayList<>(sampleSize);
                    while (taken.size() < sampleSize && resultSet.next()) {
                        long hash = resultSet.getLong(1);
                        if (!boilerplateHashes.contains(hash)) {
                            taken.add(hash);
                        }
                    }
                    return taken;
                },
                stage3RunId.value(),
                occurrenceId,
                stage2RunId.value(),
                ShingleParameters.DEFAULT.identity());
    }

    /** Which of {@code occurrences} a signature exists for under {@code stage4RunId}, asked by key. */
    private Set<Long> signedAmong(RunId stage4RunId, Collection<Long> occurrences) {
        return among("SELECT occurrence_id FROM minhash_signature WHERE run_id = ?", stage4RunId, occurrences);
    }

    /**
     * Which of {@code occurrences} phase 1 removed as near-duplicates under {@code stage4RunId}, asked by key
     * of {@code redundant_with}, this module's: a containment verdict phase 2 writes is not among them.
     */
    private Set<Long> removedAmong(RunId stage4RunId, Collection<Long> occurrences) {
        return among(
                "SELECT occurrence_id FROM redundant_with WHERE run_id = ? AND relation = 'near-duplicate'",
                stage4RunId,
                occurrences);
    }

    /** {@code select} narrowed to a list of occurrences, in statements of at most one page of them. */
    private Set<Long> among(String select, RunId stage4RunId, Collection<Long> occurrences) {
        Set<Long> found = new HashSet<>();
        List<Long> all = List.copyOf(occurrences);
        for (int from = 0; from < all.size(); from += OCCURRENCES_IN_A_PAGE) {
            List<Long> batch = all.subList(from, Math.min(all.size(), from + OCCURRENCES_IN_A_PAGE));
            List<Object> arguments = new ArrayList<>();
            arguments.add(stage4RunId.value());
            arguments.addAll(batch);
            jdbcTemplate.query(
                    select + " AND occurrence_id IN (" + placeholders(batch.size()) + ")",
                    resultSet -> {
                        found.add(resultSet.getLong("occurrence_id"));
                    },
                    arguments.toArray());
        }
        return found;
    }

    /**
     * Containment candidates for one document A's {@code rareHashes} (ADR-081): a single {@code GROUP BY}
     * over {@code shingle}, using the {@code shingle_by_hash} index on {@code (run_id,
     * shingle_parameter_identity, shingle_hash)} — the lookup that index exists for. The index is there
     * because stage 4b builds it before this class runs, not because stage 2 kept it (ADR-182): without
     * it SQLite reads every row of the run for each call, 2.4 s on a 10,000,000-row table. Nothing here holds a
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
        ledger.verdicts().verdict(new OccurrenceId(occurrenceId), stage4RunId, VerdictKind.REDUNDANT_WITH, reason);
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
     * rather than for the whole corpus up front (see the class-level note on why), and the raw counts of
     * shingle rows the containment pre-filter asks for, both kept while they fit {@link
     * #SHINGLE_SET_CACHE_BUDGET_HASHES}, least recently used evicted first, sets and counts alike (ADR-220
     * section 4).
     *
     * <p>A set is a sorted, duplicate-free {@code long[]}: 8 bytes a hash, where a {@code HashSet<Long>}
     * costs several times that, and two of them intersect in one merge pass. The budget counts hashes
     * rather than documents because a document's set can be anything from a few dozen hashes to a
     * spreadsheet's hundreds of thousands (#277). A raw count is charged {@link #RAW_COUNT_CHARGE_HASHES}.
     * The entry just asked for is always kept, even alone over the budget, since scoring is about to read it.
     */
    private final class ShingleSetCache {

        private final RunId stage2RunId;
        private final Set<Long> boilerplateHashes;
        private final LinkedHashMap<CacheKey, Object> cache = new LinkedHashMap<>(16, 0.75f, true);
        private long hashesHeld;

        ShingleSetCache(RunId stage2RunId, Set<Long> boilerplateHashes) {
            this.stage2RunId = stage2RunId;
            this.boilerplateHashes = boilerplateHashes;
        }

        long[] get(long occurrenceId) {
            CacheKey key = new CacheKey(occurrenceId, false);
            Object held = cache.get(key);
            if (held != null) {
                return (long[]) held;
            }
            long[] loaded = load(occurrenceId);
            keep(key, loaded, loaded.length);
            return loaded;
        }

        /**
         * {@code occurrenceId}'s raw shingle row count, repeats and boilerplate included -- a {@code COUNT(*)},
         * never a materialised set, used only as the cheap upper-bound pre-filter {@link #resolveContainment}
         * documents at its call site, and remembered for the pass (#277). A row count is never below the
         * distinct, boilerplate-stripped size that pre-filter stands in for, so it can only admit a candidate,
         * never wrongly exclude one.
         *
         * <p>{@code COUNT(*)} rather than {@code COUNT(DISTINCT shingle_hash)}: the distinct count makes
         * SQLite read the whole run through {@code shingle_by_hash}, five seconds a call on a 2.2-million-row
         * table, where this one is answered from {@code shingle_by_occurrence} alone (#277).
         */
        int rawSize(long occurrenceId) {
            CacheKey key = new CacheKey(occurrenceId, true);
            Object held = cache.get(key);
            if (held != null) {
                return (Integer) held;
            }
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM shingle"
                            + " WHERE occurrence_id = ? AND run_id = ? AND shingle_parameter_identity = ?",
                    Integer.class,
                    occurrenceId,
                    stage2RunId.value(),
                    ShingleParameters.DEFAULT.identity());
            int size = count == null ? 0 : count;
            keep(key, size, RAW_COUNT_CHARGE_HASHES);
            return size;
        }

        private void keep(CacheKey key, Object value, long chargeInHashes) {
            cache.put(key, value);
            hashesHeld += chargeInHashes;
            Iterator<Map.Entry<CacheKey, Object>> eldestFirst = cache.entrySet().iterator();
            while (hashesHeld > SHINGLE_SET_CACHE_BUDGET_HASHES && cache.size() > 1) {
                Map.Entry<CacheKey, Object> eldest = eldestFirst.next();
                hashesHeld -= eldest.getKey().raw() ? RAW_COUNT_CHARGE_HASHES : ((long[]) eldest.getValue()).length;
                eldestFirst.remove();
            }
        }

        private long[] load(long occurrenceId) {
            LongArray distinctive = new LongArray();
            // Not DISTINCT: asked for it, and with shingle_by_hash built (stage 4b builds it before this
            // runs, ADR-182), SQLite reads the whole run through it instead of this document's rows
            // through shingle_by_occurrence (#277). The sort below makes them distinct.
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

    /** What the cache is keyed by: an occurrence, and whether the entry is its raw count and not its set. */
    private record CacheKey(long occurrenceId, boolean raw) {}

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

    /** One row of a page of the signed occurrences: the number it is paged by, and the occurrence. */
    private record SignedRow(long rowid, long occurrenceId) {}

    /** A candidate pair, lesser occurrence first, as the page's statement returns it (ADR-220 section 4). */
    private record PairKey(long a, long b) {}

    /** What the near-duplicate survivor rule reads about one occurrence (ADR-079). */
    private record OccurrenceProfile(long alphanumericCharCount, Instant creationTime, String path) {}
}
