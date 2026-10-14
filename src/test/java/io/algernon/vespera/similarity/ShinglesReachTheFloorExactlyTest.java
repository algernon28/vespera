package io.algernon.vespera.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
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

    /** The largest corpus the properties below run over. */
    private static final int LARGEST_CORPUS = 500;

    /** How many floors, written at random, the property over any written floor tries. */
    private static final int WRITTEN_FLOORS = 20_000;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int corpora;

    @Test
    @Story("The floor is a proportion of the shingled corpus")
    @DisplayName("At a floor of 0.28 of 25 documents, a shingle in 7 is boilerplate and one in 6 is not")
    void aShingleInExactlyTheFloorsShareIsBoilerplate() {
        RunId census = corpus(25);

        Set<Long> boilerplate = resolve(census, "0.28");

        claim(
                "7 of 25 is 0.28 exactly, so a shingle in 7 of the 25 documents reaches a floor of 0.28; the"
                        + " product 0.28 × 25 is 7, not the 7.000000000000001 a double makes of it",
                () -> assertThat(boilerplate).contains(7L));
        claim("a shingle in 8 of the 25 documents is past the floor", () -> assertThat(boilerplate)
                .contains(8L));
        claim("a shingle in 6 of the 25 documents, 0.24 of them, is below the floor", () -> assertThat(
                        boilerplate)
                .doesNotContain(6L));
    }

    @Test
    @Story("The floor is a proportion of the shingled corpus")
    @DisplayName("At a floor of 0.07 of 100 documents, a shingle in 7 is boilerplate and one in 6 is not")
    void sevenOfAHundredReachAFloorOfSevenHundredths() {
        RunId census = corpus(100);

        Set<Long> boilerplate = resolve(census, "0.07");

        claim(
                "7 of 100 is 0.07 exactly, so a shingle in 7 of the 100 documents reaches a floor of 0.07",
                () -> assertThat(boilerplate).contains(7L));
        claim("a shingle in 6 of the 100 documents is below it", () -> assertThat(boilerplate)
                .doesNotContain(6L));
    }

    @Test
    @Story("The floor is a proportion of the shingled corpus")
    @DisplayName("Every exact share k / N of N up to 500 marks the shingles in k documents or more")
    void everyExactShareMarksTheShinglesInThatManyDocumentsOrMore() {
        List<String> misses = new ArrayList<>();
        for (int n = 2; n <= LARGEST_CORPUS; n++) {
            if (!terminates(n)) {
                continue;
            }
            RunId census = corpus(n);
            for (int k = 2; k <= n; k++) {
                String floor = BigDecimal.valueOf(k).divide(BigDecimal.valueOf(n)).toPlainString();
                check(census, n, floor, k, misses);
            }
        }

        claim(
                "a floor written as k / N exactly marks a shingle in k of N documents, and every one in"
                        + " more, as boilerplate, and none in fewer, for every N up to " + LARGEST_CORPUS
                        + " whose k / N terminates",
                () -> assertThat(misses).withFailMessage(missed(misses)).isEmpty());
    }

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

    @Test
    @Story("The floor is a proportion of the shingled corpus")
    @DisplayName("The share the README's command prints marks that line's shingles and every line's after it")
    void theSharePrintedForALineMarksThatLineAndTheLinesAfterIt() {
        List<String> misses = new ArrayList<>();
        for (int n = 2; n <= LARGEST_CORPUS; n++) {
            RunId census = corpus(n);
            Set<Integer> printed = new TreeSet<>();
            for (int k = 2; k <= n; k++) {
                printed.add(100 * k / n);
            }
            for (int hundredths : printed) {
                // The command prints 100 * k / N in integer arithmetic, divided by 100.0.
                String floor = Double.toString(hundredths / 100.0);
                int firstOnTheLine = 2;
                while (100 * firstOnTheLine / n < hundredths) {
                    firstOnTheLine++;
                }
                check(census, n, floor, firstOnTheLine, misses);
            }
        }

        claim(
                "set to the share a line prints, rounded down to the hundredth, the floor marks the shingles"
                        + " counted on that line and on every line after it, and none on the lines before",
                () -> assertThat(misses).withFailMessage(missed(misses)).isEmpty());
    }

    @Test
    @Story("The floor is a proportion of the shingled corpus")
    @DisplayName("A floor of 0 or below marks every stored shingle, one of 1 those in every document, one above 1 none")
    void theEdgesOfTheFloor() {
        RunId census = corpus(25);
        Set<Long> everyStored = LongStream.rangeClosed(2, 25).boxed().collect(TreeSet::new, Set::add, Set::addAll);

        claim("a floor of 0 marks every shingle stage 3 stored a row for", () -> assertThat(resolve(census, "0"))
                .isEqualTo(everyStored));
        claim("a floor below 0 marks the same", () -> assertThat(resolve(census, "-0.1"))
                .isEqualTo(everyStored));
        claim("-Infinity is below 0 and marks the same", () -> assertThat(resolve(census, "-Infinity"))
                .isEqualTo(everyStored));
        claim("a floor of 1 marks only a shingle found in every document", () -> assertThat(resolve(census, "1"))
                .containsExactly(25L));
        claim("a floor above 1 marks nothing", () -> assertThat(resolve(census, "1.5"))
                .isEmpty());
        claim("a floor whose product with the corpus is past any count marks nothing", () -> assertThat(
                        resolve(census, "1e300"))
                .isEmpty());
        claim("Infinity is above 1 and marks nothing", () -> assertThat(resolve(census, "Infinity"))
                .isEmpty());
        claim(
                "NaN is no share of anything and marks nothing, the direction that removes no document",
                () -> assertThat(resolve(census, "NaN")).isEmpty());
    }

    /** The record's rule, as the oracle: ceil(floor × N) in exact decimal arithmetic. */
    private static long exactCutoff(String floor, int n) {
        return new BigDecimal(floor)
                .multiply(BigDecimal.valueOf(n))
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
    }

    /** Whether k / N is a terminating decimal for every k: N has no prime factor but 2 and 5. */
    private static boolean terminates(int n) {
        while (n % 2 == 0) {
            n /= 2;
        }
        while (n % 5 == 0) {
            n /= 5;
        }
        return n == 1;
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
        return new BoilerplateShingles(jdbcTemplate).resolve(census, Double.parseDouble(floor));
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
                ShingleParameters.DEFAULT.identity(),
                n);
        List<Object[]> rows = new ArrayList<>();
        for (long count = 2; count <= n; count++) {
            rows.add(new Object[] {census.value(), ShingleParameters.DEFAULT.identity(), count, count, count});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO shingle_document_frequency"
                        + " (run_id, shingle_parameter_identity, shingle_hash, document_count, total_count)"
                        + " VALUES (?, ?, ?, ?, ?)",
                rows);
        return census;
    }
}
