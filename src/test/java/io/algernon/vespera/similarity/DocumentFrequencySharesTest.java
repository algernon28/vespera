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
 * The rows of stage 3's page the boilerplate floor is read off, as {@code similarity} computes them (ADR-262
 * §2): one line per share of the documents rounded down to the hundredth, with the passages on it, the fewest
 * documents any of them is found in, and the documents measured.
 *
 * <p>Following the page's rule is held against {@link BoilerplateShingles}, the read stage 4 applies the floor
 * with (ADR-260): the share a line shows, read as the profile reads a value, marks that line and every line
 * after it and none before.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Redundancy")
@Feature("Boilerplate")
@Link(name = "ADR-262", url = Adr.STAGE_3_WRITES_THE_PAGE_THE_BOILERPLATE_FLOOR_IS_READ_OFF, type = "adr")
@Link(name = "ADR-260", url = Adr.A_SHINGLE_REACHES_THE_BOILERPLATE_FLOOR_BY_EXACT_DECIMAL_ARITHMETIC, type = "adr")
@Link(name = "ADR-074", url = Adr.STAGE_3_MEASURES_SHINGLE_DOCUMENT_FREQUENCY, type = "adr")
class DocumentFrequencySharesTest {

    /** The largest corpus the properties below run over. */
    private static final int LARGEST_CORPUS = 300;

    /** A granularity other than the one stage 4 reads, whose rows the page leaves out. */
    private static final String ANOTHER_GRANULARITY = "word:7";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int corpora;

    @Test
    @Story("The page the boilerplate floor is read off")
    @DisplayName("Each line is a share rounded down to the hundredth, with its passages and the fewest documents")
    void eachLineIsAShareRoundedDownWithItsPassagesAndTheFewestDocuments() {
        RunId census = census(200);
        frequencies(census, ShingleParameters.DEFAULT.identity(), 2, 2, 2, 3, 133, 133, 200);
        jdbcTemplate.update(
                "INSERT INTO shingle_corpus_size (run_id, shingle_parameter_identity, shingled_document_count)"
                        + " VALUES (?, ?, ?)",
                census.value(),
                ANOTHER_GRANULARITY,
                50);
        frequencies(census, ANOTHER_GRANULARITY, 40);

        DocumentFrequency.Shares shares = sharesOf(census);

        claim(
                "the documents measured are the 200 of the granularity stage 4 reads, not the 50 of another",
                () -> assertThat(shares.shingledDocumentCount()).isEqualTo(200L));
        claim(
                "2 and 3 of 200 both round down to 0.01, so they are one line of four passages starting at 2;"
                        + " 133 of 200, 0.665, rounds down to 0.66; 200 of 200 is 1.00; and the passage of the"
                        + " other granularity, in 40 of its 50 documents, is on no line",
                () -> assertThat(shares.lines())
                        .withFailMessage("the lines were %s", shares.lines())
                        .containsExactly(
                                new DocumentFrequency.ShareLine(new BigDecimal("0.01"), 4, 2),
                                new DocumentFrequency.ShareLine(new BigDecimal("0.66"), 2, 133),
                                new DocumentFrequency.ShareLine(new BigDecimal("1.00"), 1, 200)));
    }

    @Test
    @Story("The page the boilerplate floor is read off")
    @DisplayName("A run that measured no document has no line, and measured 0 documents")
    void aRunThatMeasuredNoDocumentHasNoLine() {
        RunId census = census(0);

        DocumentFrequency.Shares shares = sharesOf(census);

        claim(
                "with no shingle_corpus_size row for the run, no document was measured",
                () -> assertThat(shares.shingledDocumentCount()).isZero());
        claim("and there is no line to show", () -> assertThat(shares.lines()).isEmpty());
    }

    @Test
    @Story("The page the boilerplate floor is read off")
    @DisplayName("For every N up to 300, each line starts at the first count whose share rounds down to it")
    void everyLineStartsAtTheFirstCountWhoseShareRoundsDownToIt() {
        List<String> misses = new ArrayList<>();
        for (int n = 2; n <= LARGEST_CORPUS; n++) {
            RunId census = corpus(n);
            List<DocumentFrequency.ShareLine> expected = new ArrayList<>();
            for (long k = 2; k <= n; k++) {
                BigDecimal share = roundedDown(k, n);
                DocumentFrequency.ShareLine last = expected.isEmpty() ? null : expected.getLast();
                if (last != null && last.share().equals(share)) {
                    expected.set(expected.size() - 1,
                            new DocumentFrequency.ShareLine(share, last.shingles() + 1, last.fewestDocuments()));
                } else {
                    expected.add(new DocumentFrequency.ShareLine(share, 1, k));
                }
            }
            DocumentFrequency.Shares shares = sharesOf(census);
            if (shares.shingledDocumentCount() != n || !shares.lines().equals(expected)) {
                misses.add("N " + n + ": " + shares);
            }
        }

        claim(
                "over one passage per count from 2 to N, the lines are the shares 100 × k / N rounded down, in"
                        + " ascending order, each holding the counts that round down to it and starting at the"
                        + " first of them",
                () -> assertThat(misses).withFailMessage(missed(misses)).isEmpty());
    }

    @Test
    @Story("The page the boilerplate floor is read off")
    @DisplayName("A floor set to the share a line shows marks that line and every line after it, and none before")
    void aFloorSetToALinesShareMarksThatLineAndTheLinesAfterIt() {
        List<String> misses = new ArrayList<>();
        for (int n = 2; n <= LARGEST_CORPUS; n++) {
            RunId census = corpus(n);
            for (DocumentFrequency.ShareLine line : sharesOf(census).lines()) {
                String floor = line.share().toPlainString();
                Set<Long> boilerplate = new BoilerplateShingles(jdbcTemplate).resolve(census, Double.parseDouble(floor));
                Set<Long> expected = LongStream.rangeClosed(line.fewestDocuments(), n)
                        .boxed()
                        .collect(TreeSet::new, Set::add, Set::addAll);
                if (!boilerplate.equals(expected)) {
                    misses.add("floor " + floor + " of " + n + ": marked " + new TreeSet<>(boilerplate)
                            + ", not " + line.fewestDocuments() + " to " + n);
                }
            }
        }

        claim(
                "the share a line of the page shows, written into the profile as it is shown, marks the passages"
                        + " from that line's fewest documents to N as boilerplate, and none in fewer",
                () -> assertThat(misses).withFailMessage(missed(misses)).isEmpty());
    }

    /** 100 × k / N rounded down, divided by 100, at two decimal places: what a line shows. */
    private static BigDecimal roundedDown(long k, int n) {
        return BigDecimal.valueOf(k).divide(BigDecimal.valueOf(n), 2, RoundingMode.FLOOR);
    }

    private static String missed(List<String> misses) {
        return misses.size() + " corpora or floors disagreed, among them " + misses.subList(0, Math.min(10, misses.size()));
    }

    private DocumentFrequency.Shares sharesOf(RunId census) {
        return new DocumentFrequency(jdbcTemplate, new Ledger(jdbcTemplate)).shares(census);
    }

    /**
     * A stage-3 run over {@code n} documents of the default granularity, with one passage per count from 2 to n,
     * the hash being the count, so the set {@link BoilerplateShingles} resolves names the counts marked.
     */
    private RunId corpus(int n) {
        RunId census = census(n);
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

    /**
     * A stage-3 run, with {@code n} shingled documents of the default granularity recorded where {@code n} is
     * above 0, and none recorded otherwise.
     */
    private RunId census(int n) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus"));
        RunId census = ledger.runs()
                .startRun("content-census", "shares", "{\"corpus\":" + (++corpora) + "}", walkId, List.of());
        if (n > 0) {
            jdbcTemplate.update(
                    "INSERT INTO shingle_corpus_size (run_id, shingle_parameter_identity, shingled_document_count)"
                            + " VALUES (?, ?, ?)",
                    census.value(),
                    ShingleParameters.DEFAULT.identity(),
                    n);
        }
        return census;
    }

    /** One passage of {@code granularity} per count given, its hash being its position in the list. */
    private void frequencies(RunId census, String granularity, long... documentCounts) {
        List<Object[]> rows = new ArrayList<>();
        for (int hash = 0; hash < documentCounts.length; hash++) {
            rows.add(new Object[] {census.value(), granularity, hash, documentCounts[hash], documentCounts[hash]});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO shingle_document_frequency"
                        + " (run_id, shingle_parameter_identity, shingle_hash, document_count, total_count)"
                        + " VALUES (?, ?, ?, ?, ?)",
                rows);
    }
}
