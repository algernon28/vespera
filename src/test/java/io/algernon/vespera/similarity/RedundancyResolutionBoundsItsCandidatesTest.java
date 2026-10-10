package io.algernon.vespera.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.StatementLog;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stage 4b sorts nothing, and holds nothing, that grows with the signed occurrences sharing one band value or
 * with the occurrences carrying one occurrence's rarest hashes, and what it removes is what it removed before
 * (ADR-225). Until ADR-225 one statement found a page's candidate pairs and sorted them all to make them
 * distinct, the same pairs were then held as a list, and one statement grouped every row carrying any of an
 * occurrence's rarest hashes and answered a set of every candidate.
 *
 * <p><b>The ledger is written by hand</b>, signatures and band rows included, so that which occurrences share a
 * band value is this file's to state and not MinHash's to decide (ADR-081 makes retrieval probabilistic). Each
 * thing the acceptance of #476 names is in it once:
 *
 * <ul>
 *   <li>one band value shared by 1,050 signed occurrences, more than a page of 1,000, of which three are a
 *       chain: the first resembles the second, the second the third, and the first does not resemble the third;
 *   <li>a pair that shares three band values and is a near-duplicate, and a pair that shares two and is not;
 *   <li>one occurrence wholly inside 1,108 others, more than a page of 1,000: an unsigned one of the lowest id,
 *       one that is removed as a near-duplicate first, three that hold all but one of its shingles, 1,100
 *       unsigned ones, and two signed ones of the highest ids that hold all of it and so tie.
 * </ul>
 *
 * <p>The first test passes before ADR-225 is built and after it: that is the acceptance's "the verdicts are the
 * same". The second fails until it is built. Both read one resolution, made once for the class.
 */
@Epic("Redundancy")
@Feature("Resolution")
@Issue("476")
@Link(name = "ADR-225", url = Adr.STAGE_4B_READS_ITS_CANDIDATES_A_THOUSAND_AT_A_TIME, type = "adr")
@Link(name = "ADR-079", url = Adr.REDUNDANT_WITH_COVERS_NEAR_DUPLICATION_AND_CONTAINMENT, type = "adr")
@Link(name = "ADR-081", url = Adr.MINHASH_RETRIEVES_SHINGLE_SETS_JUDGE, type = "adr")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedundancyResolutionBoundsItsCandidatesTest {

    /** The most rows one statement takes, and the most occurrences one statement names. */
    private static final int A_PAGE = 1_000;

    /** Signed occurrences sharing one band value: more than a page, so its rows are read in two statements. */
    private static final int SHARING_ONE_BAND_VALUE = 1_050;

    /** Where in that family the three of the chain stand: in its first page, in the middle, and past the page. */
    private static final int FIRST_OF_THE_CHAIN = 5;

    private static final int SECOND_OF_THE_CHAIN = 600;

    private static final int THIRD_OF_THE_CHAIN = 1_040;

    /** Shingles of each occurrence of the family, its own or the chain's. */
    private static final int FAMILY_SHINGLES = 10;

    /** The first and the second of the chain share 9 shingles of the 11 between them, and so do the second and the third. */
    private static final double NEIGHBOURS_IN_THE_CHAIN = 9.0 / 11.0;

    /** The first and the third share 8 of the 12 between them: below the cut of 0.80, and the score written. */
    private static final double ENDS_OF_THE_CHAIN = 8.0 / 12.0;

    /** Shingles of each of the two pairs; the near-duplicate pair shares 19 of the 21 between them. */
    private static final int PAIR_SHINGLES = 20;

    private static final double THE_PAIR_SHARING_THREE_BANDS = 19.0 / 21.0;

    /** How far the second of the pair that is not a near-duplicate is shifted: 10 shared of 30. */
    private static final int SHIFT_OF_THE_UNLIKE_PAIR = 10;

    /** The contained occurrence's whole shingle set, every one carried by all or all but one of its containers. */
    private static final int CONTAINED_SHINGLES = 40;

    /** What the container removed as a near-duplicate shares with the occurrence it is redundant with. */
    private static final int SHARED_WITH_ITS_NEAR_DUPLICATE = 400;

    /** That pair shares 400 of the 460 between them. */
    private static final double THE_REMOVED_CONTAINER = 400.0 / 460.0;

    /** Shingles of its own that each container holds beside the contained occurrence's. */
    private static final int OWN_SHINGLES_OF_A_CONTAINER = 20;

    /** Signed containers that hold all but one of the contained occurrence's shingles: 39 of 40, a score of 0.975. */
    private static final int ALL_BUT_ONE = 3;

    private static final int OWN_SHINGLES_OF_ALL_BUT_ONE = 10;

    /** Unsigned occurrences that hold all but one of them too: with the rest, more candidates than a page. */
    private static final int UNSIGNED_CARRIERS = 1_100;

    private static final int OWN_SHINGLES_OF_A_CARRIER = 5;

    /** The unsigned container, the removed one, the three, the 1,100 and the two that tie. */
    private static final int CONTAINERS = 1 + 1 + ALL_BUT_ONE + UNSIGNED_CARRIERS + 2;

    /** Every shingle of the contained occurrence is in the container it is redundant with. */
    private static final double WHOLLY_CONTAINED = 1.0;

    /** What is removed: two of the chain, one of the pair, the removed container, and the contained occurrence. */
    private static final int REMOVED = 5;

    private static final int BANDS = 16;

    /** Alphanumeric characters of extracted text, the survivor rule's measure: more survives. */
    private static final long LEAST_TEXT = 1_000L;

    private static final long MORE_TEXT = 2_000L;

    private static final long MOST_TEXT = 3_000L;

    private static final String NEAR_DUPLICATE = "near-duplicate";

    private static final String CONTAINED_IN = "contained-in";

    /** What a plan says where the statement keeps rows in temporary storage to sort them. */
    private static final String SORTS = "TEMP B-TREE";

    private static final Pattern A_LIST = Pattern.compile("IN \\(([^)]*)\\)");

    private PoolOfTwo pool;
    private RunId stage2;
    private RunId stage4;
    private List<String> said;

    private final List<Planted> planted = new ArrayList<>();
    private List<Long> ids;

    private int unsignedContainer;
    private int removedContainer;
    private int itsNearDuplicate;
    private int firstOfTheChain;
    private int secondOfTheChain;
    private int thirdOfTheChain;
    private int lesserOfThePair;
    private int greaterOfThePair;
    private int contained;
    private int firstThatHoldsAll;

    @BeforeAll
    void resolveALedgerWrittenByHand(@TempDir Path folder) throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        JdbcTemplate jdbcTemplate = pool.jdbcTemplate();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-bounded-candidates"));
        stage2 = ledger.runs().startRun("extraction", "s2-225", "{}", walk, List.of());
        RunId stage3 = ledger.runs().startRun("content-census", "s3-225", "{}", walk, List.of(stage2));
        stage4 = ledger.runs().startRun("content-redundancy", "s4-225", "{}", walk, List.of(stage3));

        plant();
        write(walk, jdbcTemplate);
        new TransactionTemplate(new JdbcTransactionManager(jdbcTemplate.getDataSource()))
                .executeWithoutResult(status -> new DocumentFrequency(jdbcTemplate, ledger).measure(stage3, stage2));
        jdbcTemplate.execute(TheRunsHashIndex.statementFor(stage2.value()));

        StatementLog log = new StatementLog(jdbcTemplate.getDataSource());
        new RedundancyResolution(log.jdbcTemplate(), new Ledger(log.jdbcTemplate()))
                .resolve(stage4, stage3, stage2, Set.of(), RecordedAlphanumericCounts.over(log.jdbcTemplate()));
        said = log.said().stream().distinct().toList();
    }

    @AfterAll
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("Redundancy resolution holds no list that grows with how many documents resemble one another")
    @DisplayName("The documents removed, what each is redundant with, and the score written are what they were before the candidates were read a thousand at a time")
    void removesWhatItRemovedBefore() {
        List<Removed> removed = pool.jdbcTemplate()
                .query(
                        "SELECT occurrence_id, redundant_with_occurrence_id, relation, score FROM redundant_with"
                                + " WHERE run_id = ? ORDER BY occurrence_id",
                        (resultSet, rowNumber) -> new Removed(
                                resultSet.getLong(1), resultSet.getLong(2), resultSet.getString(3), resultSet.getDouble(4)),
                        stage4.value());

        claim(
                "of three documents among " + SHARING_ONE_BAND_VALUE + " that share one band value, where the first"
                        + " resembles the second and the second the third, the two with less text are removed as"
                        + " near-duplicates of the third, the first with the score it has against the third, 8"
                        + " shared word sequences of 12, though that is below the cut and the two were only joined"
                        + " through the second",
                () -> assertThat(removed)
                        .contains(
                                new Removed(id(firstOfTheChain), id(thirdOfTheChain), NEAR_DUPLICATE, ENDS_OF_THE_CHAIN),
                                new Removed(id(secondOfTheChain), id(thirdOfTheChain), NEAR_DUPLICATE, NEIGHBOURS_IN_THE_CHAIN)));
        claim(
                "of two documents that share three band values and 19 word sequences of 21, the one with less text"
                        + " is removed once, however many band values offered the pair",
                () -> assertThat(removed)
                        .contains(new Removed(
                                id(lesserOfThePair), id(greaterOfThePair), NEAR_DUPLICATE, THE_PAIR_SHARING_THREE_BANDS)));
        claim(
                "a document that holds all of another's text and is itself a near-duplicate of a third is removed"
                        + " as that, sharing 400 word sequences of 460",
                () -> assertThat(removed)
                        .contains(new Removed(
                                id(removedContainer), id(itsNearDuplicate), NEAR_DUPLICATE, THE_REMOVED_CONTAINER)));
        claim(
                "the document wholly inside " + CONTAINERS + " others is redundant with the first, in the order"
                        + " they were recorded, of the two that hold all of it and are still standing: not with"
                        + " the unsigned one recorded before every other, not with the one removed as a"
                        + " near-duplicate, and not with any of those that hold all but one of its "
                        + CONTAINED_SHINGLES + " word sequences",
                () -> assertThat(removed)
                        .contains(new Removed(id(contained), id(firstThatHoldsAll), CONTAINED_IN, WHOLLY_CONTAINED)));
        claim(
                "and nothing else is removed: those " + REMOVED + " documents, and not the pair that shares two"
                        + " band values and a third of its word sequences",
                () -> assertThat(removed).hasSize(REMOVED));
        claim(
                "each of the " + REMOVED + " carries the verdict that removes it, and no other document does",
                () -> assertThat(pool.jdbcTemplate()
                                .queryForList(
                                        "SELECT occurrence_id FROM verdict WHERE run_id = ? AND kind = 'REDUNDANT_WITH'"
                                                + " ORDER BY occurrence_id",
                                        Long.class,
                                        stage4.value()))
                        .containsExactlyElementsOf(removed.stream().map(Removed::occurrence).toList()));
    }

    @Test
    @Story("Redundancy resolution holds no list that grows with how many documents resemble one another")
    @DisplayName("Redundancy resolution reads the documents that share a band value, and those that carry a rare word sequence, a thousand at a time, and sorts none of them")
    void readsItsCandidatesAThousandAtATimeAndSortsNone() {
        List<String> reads = said.stream()
                .filter(sql -> sql.stripLeading().startsWith("SELECT"))
                .toList();
        List<String> ofTheBands = reads.stream().filter(sql -> sql.contains("FROM signature_band")).toList();
        List<String> ofOneHash = reads.stream().filter(sql -> sql.contains("shingle_hash = ?")).toList();

        claim(
                "the only reads the database sorts in temporary storage are those of one document's rarest word"
                        + " sequences, which hold one document's rows: no read of the band values and none of"
                        + " the documents carrying a word sequence is sorted",
                () -> assertThat(reads.stream()
                                .filter(sql -> !sql.contains("shingle_document_frequency"))
                                .filter(sql -> planOf(sql).stream().anyMatch(step -> step.contains(SORTS)))
                                .toList())
                        .isEmpty());
        claim(
                "every read of the band values names at most " + A_PAGE + " documents, or one band value of which"
                        + " it takes at most " + A_PAGE + " rows going on from the last row read",
                () -> assertThat(ofTheBands)
                        .isNotEmpty()
                        .allSatisfy(sql -> assertThat(placeholdersInItsList(sql) >= 1 && placeholdersInItsList(sql) <= A_PAGE
                                        || sql.contains("band_hash = ?") && sql.contains("rowid > ?")
                                                && sql.endsWith(" LIMIT " + A_PAGE))
                                .as("a read of the band values that names a page of documents or one band value: %s", sql)
                                .isTrue()));
        claim(
                "the documents carrying one of a document's rarest word sequences are read for that word sequence"
                        + " alone, at most " + A_PAGE + " at a time, going on from the last document read",
                () -> assertThat(ofOneHash)
                        .isNotEmpty()
                        .allSatisfy(sql -> assertThat(sql)
                                .contains("occurrence_id > ?")
                                .endsWith(" LIMIT " + A_PAGE)));
        claim(
                "and they come from the index built for this extraction, already in order and with no row of the"
                        + " table read",
                () -> assertThat(ofOneHash)
                        .isNotEmpty()
                        .allSatisfy(sql -> assertThat(planOf(sql))
                                .anyMatch(step -> step.contains("COVERING INDEX " + TheRunsHashIndex.NAME)
                                        && step.contains("shingle_hash=?"))));
        claim(
                "no read asks the database to count, for every document at once, how many of the rare word"
                        + " sequences it carries",
                () -> assertThat(reads).noneMatch(sql -> sql.contains("GROUP BY occurrence_id")));
        claim(
                "every read of one document's own word sequences goes through the index on the document, though"
                        + " the index built for this extraction holds every column it asks for",
                () -> assertThat(reads.stream()
                                .filter(sql -> sql.contains("FROM shingle") && sql.contains("occurrence_id = ?"))
                                .toList())
                        .isNotEmpty()
                        .allSatisfy(sql -> assertThat(planOf(sql))
                                .anyMatch(step -> step.contains("shingle_by_occurrence"))
                                .noneMatch(step -> step.contains(TheRunsHashIndex.NAME))));
        claim(
                "and no read names more than " + A_PAGE + " documents, the " + CONTAINERS + " that hold one"
                        + " document's text included",
                () -> assertThat(reads).allSatisfy(sql -> assertThat(placeholdersInItsList(sql)).isLessThanOrEqualTo(A_PAGE)));
    }

    // -- The ledger ---------------------------------------------------------------------------------

    /** One occurrence to write: its shingles, its text, and the band values it shares, where it is signed. */
    private record Planted(Set<Long> shingles, long text, boolean signed, long[] sharedBands) {}

    /** A row of {@code redundant_with}. */
    private record Removed(long occurrence, long redundantWith, String relation, double score) {}

    private long id(int plantedAt) {
        return ids.get(plantedAt);
    }

    /** Puts the occurrences in the order they are recorded in, which is the order of their ids. */
    private void plant() {
        Set<Long> held = hashes(1_000, CONTAINED_SHINGLES);
        Set<Long> sharedWithItsNearDuplicate = hashes(2_000, SHARED_WITH_ITS_NEAR_DUPLICATE);

        unsignedContainer = add(with(held, hashes(10_000, OWN_SHINGLES_OF_A_CONTAINER)), LEAST_TEXT, false);
        removedContainer = add(with(held, sharedWithItsNearDuplicate), LEAST_TEXT, true, band(6, 60));
        itsNearDuplicate = add(
                with(sharedWithItsNearDuplicate, hashes(11_000, OWN_SHINGLES_OF_A_CONTAINER)), MOST_TEXT, true, band(6, 60));

        for (int member = 0; member < SHARING_ONE_BAND_VALUE; member++) {
            if (member == FIRST_OF_THE_CHAIN) {
                firstOfTheChain = add(hashes(5_000, FAMILY_SHINGLES), LEAST_TEXT, true, band(0, 1));
            } else if (member == SECOND_OF_THE_CHAIN) {
                secondOfTheChain = add(with(hashes(5_000, FAMILY_SHINGLES - 1), Set.of(5_010L)), MORE_TEXT, true, band(0, 1));
            } else if (member == THIRD_OF_THE_CHAIN) {
                thirdOfTheChain = add(
                        with(hashes(5_001, FAMILY_SHINGLES - 2), Set.of(5_010L, 5_011L)), MOST_TEXT, true, band(0, 1));
            } else {
                add(hashes(1_000_000L + member * 100L, FAMILY_SHINGLES), LEAST_TEXT, true, band(0, 1));
            }
        }

        long[] threeBands = bands(new int[] {1, 2, 3}, 10);
        lesserOfThePair = add(hashes(6_000, PAIR_SHINGLES), LEAST_TEXT, true, threeBands);
        greaterOfThePair = add(hashes(6_001, PAIR_SHINGLES), MOST_TEXT, true, threeBands);
        long[] twoBands = bands(new int[] {4, 5}, 40);
        add(hashes(7_000, PAIR_SHINGLES), LEAST_TEXT, true, twoBands);
        add(hashes(7_000 + SHIFT_OF_THE_UNLIKE_PAIR, PAIR_SHINGLES), MOST_TEXT, true, twoBands);

        contained = add(held, LEAST_TEXT, true);
        for (int one = 0; one < ALL_BUT_ONE; one++) {
            add(with(allButOne(held, one), hashes(20_000 + one * 100L, OWN_SHINGLES_OF_ALL_BUT_ONE)), LEAST_TEXT, true);
        }
        for (int carrier = 0; carrier < UNSIGNED_CARRIERS; carrier++) {
            add(
                    with(allButOne(held, carrier), hashes(2_000_000L + carrier * 100L, OWN_SHINGLES_OF_A_CARRIER)),
                    LEAST_TEXT,
                    false);
        }
        firstThatHoldsAll = add(with(held, hashes(30_000, OWN_SHINGLES_OF_A_CONTAINER)), LEAST_TEXT, true);
        add(with(held, hashes(31_000, OWN_SHINGLES_OF_A_CONTAINER)), LEAST_TEXT, true);
    }

    private int add(Set<Long> shingles, long text, boolean signed, long[]... sharedBands) {
        long[] shared = new long[BANDS];
        for (long[] bands : sharedBands) {
            for (int band = 0; band < BANDS; band++) {
                if (bands[band] != 0) {
                    shared[band] = bands[band];
                }
            }
        }
        planted.add(new Planted(shingles, text, signed, shared));
        return planted.size() - 1;
    }

    /** One band's shared value, the others left to be the occurrence's own. */
    private static long[] band(int band, long value) {
        return bands(new int[] {band}, value);
    }

    private static long[] bands(int[] which, long firstValue) {
        long[] shared = new long[BANDS];
        for (int band : which) {
            shared[band] = firstValue + band;
        }
        return shared;
    }

    private static Set<Long> hashes(long from, int count) {
        Set<Long> hashes = new LinkedHashSet<>();
        for (long i = 0; i < count; i++) {
            hashes.add(from + i);
        }
        return hashes;
    }

    private static Set<Long> with(Set<Long> some, Set<Long> more) {
        Set<Long> both = new LinkedHashSet<>(some);
        both.addAll(more);
        return both;
    }

    /** {@code all} less one of its hashes, a different one as {@code which} goes round. */
    private static Set<Long> allButOne(Set<Long> all, int which) {
        List<Long> kept = new ArrayList<>(all);
        kept.remove(which % kept.size());
        return new LinkedHashSet<>(kept);
    }

    private void write(WalkId walk, JdbcTemplate jdbcTemplate) throws SQLException {
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement occurrence = connection.prepareStatement(
                    "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                            + " VALUES (?, ?, 1, '2026-01-01T00:00:00Z', '2025-01-01T00:00:00Z')")) {
                for (int i = 0; i < planted.size(); i++) {
                    occurrence.setLong(1, walk.value());
                    occurrence.setString(2, String.format("d%05d.pdf", i));
                    occurrence.addBatch();
                }
                occurrence.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
        ids = jdbcTemplate.queryForList(
                "SELECT id FROM file_occurrence WHERE walk_id = ? ORDER BY id", Long.class, walk.value());
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement shingle = connection.prepareStatement(
                            "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                                    + " VALUES (?, ?, ?, ?)");
                    PreparedStatement metric = connection.prepareStatement(
                            "INSERT INTO extraction_metric (occurrence_id, run_id, status, processing_time,"
                                    + " character_count, alphanumeric_char_count, word_count,"
                                    + " word_character_length_total, vowelless_word_count, single_character_word_count)"
                                    + " VALUES (?, ?, 'success', 0.5, ?, ?, 1, 1, 0, 0)");
                    PreparedStatement signature = connection.prepareStatement(
                            "INSERT INTO minhash_signature (occurrence_id, run_id, signature_identity, signature)"
                                    + " VALUES (?, ?, 'written-by-hand', ?)");
                    PreparedStatement band = connection.prepareStatement(
                            "INSERT INTO signature_band (occurrence_id, run_id, band_ordinal, band_hash)"
                                    + " VALUES (?, ?, ?, ?)")) {
                for (int i = 0; i < planted.size(); i++) {
                    Planted one = planted.get(i);
                    for (long hash : one.shingles()) {
                        shingle.setLong(1, ids.get(i));
                        shingle.setString(2, stage2.value());
                        shingle.setString(3, ShingleParameters.DEFAULT.identity());
                        shingle.setLong(4, hash);
                        shingle.addBatch();
                    }
                    metric.setLong(1, ids.get(i));
                    metric.setString(2, stage2.value());
                    metric.setLong(3, one.text());
                    metric.setLong(4, one.text());
                    metric.addBatch();
                    if (!one.signed()) {
                        continue;
                    }
                    signature.setLong(1, ids.get(i));
                    signature.setString(2, stage4.value());
                    signature.setBytes(3, new byte[] {0});
                    signature.addBatch();
                    for (int ordinal = 0; ordinal < BANDS; ordinal++) {
                        band.setLong(1, ids.get(i));
                        band.setString(2, stage4.value());
                        band.setInt(3, ordinal);
                        // A band value no other occurrence has, unless one is shared: far above the shared ones.
                        band.setLong(4, one.sharedBands()[ordinal] != 0 ? one.sharedBands()[ordinal] : 1_000_000L + i * 100L + ordinal);
                        band.addBatch();
                    }
                }
                shingle.executeBatch();
                metric.executeBatch();
                signature.executeBatch();
                band.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
    }

    /**
     * The plan SQLite gives {@code sql} with every value bound to the stage-2 run: a value changes no plan here
     * but the run's, by which SQLite decides whether the index built for that run may be used (ADR-221 section 2).
     */
    private List<String> planOf(String sql) {
        Object[] arguments = Collections.nCopies((int) sql.chars().filter(c -> c == '?').count(), (Object) stage2.value())
                .toArray();
        return pool.jdbcTemplate()
                .query("EXPLAIN QUERY PLAN " + sql, (resultSet, rowNumber) -> resultSet.getString("detail"), arguments);
    }

    /** The placeholders in the statement's longest bracketed list after {@code IN}; none where it has none. */
    private static int placeholdersInItsList(String sql) {
        int most = 0;
        Matcher list = A_LIST.matcher(sql);
        while (list.find()) {
            most = Math.max(most, (int) list.group(1).chars().filter(character -> character == '?').count());
        }
        return most;
    }
}
