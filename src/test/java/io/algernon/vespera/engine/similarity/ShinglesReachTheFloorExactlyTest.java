package io.algernon.vespera.engine.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * A shingle is boilerplate where its document count is at least the floor times the shingled documents,
 * with the product taken in exact decimal arithmetic (ADR-260): the cutoff is {@code ceil(floor × N)}.
 *
 * <p>Each floor is written as text and read the way {@code NumericValue} reads a profile value, with
 * {@code Double.parseDouble}, then handed to {@link BoilerplateShingles#resolve}. Every corpus carries one
 * hash per document count from 2 to N, the hash being the count, so the set resolved names the counts that
 * reached the floor.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Redundancy")
@Feature("Boilerplate")
@Link(name = "ADR-260", url = Adr.A_SHINGLE_REACHES_THE_BOILERPLATE_FLOOR_BY_EXACT_DECIMAL_ARITHMETIC, type = "adr")
@Link(name = "ADR-080", url = Adr.THE_BOILERPLATE_FLOOR_IS_A_GATE, type = "adr")
@Link(name = "ADR-074", url = Adr.STAGE_3_MEASURES_SHINGLE_DOCUMENT_FREQUENCY, type = "adr")
class ShinglesReachTheFloorExactlyTest {

    /** The window the application ships: five words (vespera.redundancy.shingle-words). */
    private static final ShingleParameters FIVE_WORD_SHINGLES = new ShingleParameters(5);

    /** The largest corpus the properties below run over. */
    private static final int LARGEST_CORPUS = 500;

    /** How many floors, written at random, the property over any written floor tries. */
    private static final int WRITTEN_FLOORS = 20_000;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int corpora;

    @Test
    @Story("The floor is a proportion of the shingled corpus")
    @DisplayName("Any floor written in decimals marks the shingles in ceil(floor × N) documents or more")
    void anyWrittenFloorMarksTheShinglesAtOrPastItsProductWithTheCorpus() {
        Random random = new Random(259);
        RunId[] censuses = new RunId[LARGEST_CORPUS + 1];
        List<String> misses = new ArrayList<>();
        for (int i = 0; i < WRITTEN_FLOORS; i++) {
            int n = 2 + random.nextInt(LARGEST_CORPUS - 1);
            int places = 1 + random.nextInt(6);
            String floor = BigDecimal.valueOf(random.nextLong((long) Math.pow(10, places) + 1), places)
                    .toPlainString();
            if (censuses[n] == null) {
                censuses[n] = corpus(n);
            }
            check(censuses[n], n, floor, exactCutoff(floor, n), misses);
        }

        claim(
                "a floor of any decimal between 0 and 1 marks a shingle in c of N documents as boilerplate"
                        + " exactly when c is at least ceil(floor × N), the product taken in exact decimal"
                        + " arithmetic",
                () -> assertThat(misses).withFailMessage(missed(misses)).isEmpty());
    }

    /** The record's rule, as the oracle: ceil(floor × N) in exact decimal arithmetic. */
    private static long exactCutoff(String floor, int n) {
        return new BigDecimal(floor)
                .multiply(BigDecimal.valueOf(n))
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
    }

    private void check(RunId census, int n, String floor, long cutoff, List<String> misses) {
        Set<Long> boilerplate = resolve(census, floor);
        Set<Long> expected = LongStream.rangeClosed(Math.max(2, cutoff), n)
                .boxed()
                .collect(TreeSet::new, Set::add, Set::addAll);
        if (!boilerplate.equals(expected)) {
            long lowest = boilerplate.stream().mapToLong(Long::longValue).min().orElse(n + 1L);
            misses.add("floor " + floor + " of " + n + ": the first count marked was " + lowest + ", not "
                    + Math.max(2, cutoff));
        }
    }

    private static String missed(List<String> misses) {
        return misses.size() + " floors marked other counts than ceil(floor × N), among them "
                + misses.subList(0, Math.min(10, misses.size()));
    }

    /** The floor as the profile's text, read as {@code NumericValue} reads it (ADR-120). */
    private Set<Long> resolve(RunId census, String floor) {
        return new BoilerplateShingles(jdbcTemplate).resolve(census, Double.parseDouble(floor), FIVE_WORD_SHINGLES);
    }

    /** A stage-3 run over {@code n} shingled documents, carrying one hash per document count from 2 to n. */
    private RunId corpus(int n) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus"));
        RunId census =
                ledger.runs().startRun("content-census", "exact-floor", "{\"corpus\":" + (++corpora) + "}", walkId, List.of());
        jdbcTemplate.update(
                "INSERT INTO shingle_corpus_size (run_id, shingle_parameter_identity, shingled_document_count)"
                        + " VALUES (?, ?, ?)",
                census.value(),
                FIVE_WORD_SHINGLES.identity(),
                n);
        List<Object[]> rows = new ArrayList<>();
        for (long count = 2; count <= n; count++) {
            rows.add(new Object[] {census.value(), FIVE_WORD_SHINGLES.identity(), count, count, count});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO shingle_document_frequency"
                        + " (run_id, shingle_parameter_identity, shingle_hash, document_count, total_count)"
                        + " VALUES (?, ?, ?, ?, ?)",
                rows);
        return census;
    }
}
