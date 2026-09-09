package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.SchemaVersionGuard;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code embedding} states and is refused against its own schema version, independently of every
 * other module's (ADR-059).
 *
 * <p>The sibling of {@code ExtractionSchemaTest} and {@code SimilaritySchemaTest}, and the reason it
 * is a class of its own rather than one more name in {@code SchemaVersionDeclarationTest}: that test
 * asserts a version row <em>exists</em> for every module owning tables, which is a different question
 * from whether a mismatch is refused. A module could record its version and refuse nothing.
 *
 * <p>That extraction's own version did <em>not</em> move when the seed set arrived is asserted where it
 * belongs, by that module's own test committing to a literal of its own — the seed pass reuses
 * extraction's extractor, chunker and both caches, and adds no table to it. The seed side's own
 * {@code extraction_metric} rows (ADR-092) do not move it either: they land in a table that module
 * already owns, under a run id its shape already accommodates.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Schema versioning")
@Issue("104")
@Link(name = "ADR-059", url = Adr.SCHEMA_VERSION_IS_ONE_ROW_PER_MODULE, type = "adr")
@Link(name = "ADR-083", url = Adr.THE_SEED_SET_IS_EXTRACTED_BY_STAGE_5, type = "adr")
class EmbeddingSchemaTest {

    /** The version this module is on, and the table that arrived with it. */
    private static final int CURRENT_VERSION = 5;

    private static final String TABLE_THIS_VERSION_ADDED = "relevance_label";

    /** The table version 3 arrived with, still described by the version above (ADR-084, ADR-085, #107). */
    private static final String TABLE_VERSION_FOUR_ADDED = "relevance_score";

    private static final String TABLE_VERSION_THREE_ADDED = "vector";

    /** The table version 2 arrived with, still described by the version above (ADR-086, #106). */
    private static final String TABLE_VERSION_TWO_ADDED = "seed_corpus_comparison";

    /** The table version 1 arrived with, still described by the version above (ADR-083). */
    private static final String TABLE_VERSION_ONE_ADDED = "unusable_seed";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("embedding refuses to start against a database recording a version other than its own")
    void refusesToStartAgainstAMismatchedDatabase() {
        int somethingOtherThanEmbeddingExpects = EmbeddingSchema.VERSION + 1;
        jdbcTemplate.update(
                "INSERT INTO schema_version (module, version) VALUES (?, ?)",
                EmbeddingSchema.MODULE,
                somethingOtherThanEmbeddingExpects);

        claim(
                "this module's own check runs independently of every other module's, and refuses before"
                        + " anything reads or writes the seed set's tables",
                () -> assertThatThrownBy(() -> new EmbeddingSchema(new SchemaVersionGuard(jdbcTemplate)))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(EmbeddingSchema.MODULE)
                        .hasMessageContaining(String.valueOf(EmbeddingSchema.VERSION))
                        .hasMessageContaining(String.valueOf(somethingOtherThanEmbeddingExpects)));
    }

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("embedding records its own version on first use, independent of any other module's row")
    void recordsItsOwnVersionOnFirstUse() {
        new EmbeddingSchema(new SchemaVersionGuard(jdbcTemplate));

        claim(
                "the version recorded is this part of the system's own, not a value borrowed from the"
                        + " four that owned tables before it",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT version FROM schema_version WHERE module = ?",
                                Integer.class,
                                EmbeddingSchema.MODULE))
                        .isEqualTo(EmbeddingSchema.VERSION));
    }

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("VERSION is the literal 5, and relevance_label is the table that came with it")
    @Issue("108")
    @Link(name = "ADR-020", url = Adr.RELEVANCE_SCORING_FUNCTION, type = "adr")
    void versionIsTheRelevanceLabelTableLiterally() {
        claim(
                "the version and the table it names arrived together, so a later table added without a"
                        + " bump would leave this constant already committed to the wrong value. The"
                        + " literal 5 is stated here rather than read back off the constant, which would"
                        + " assert nothing",
                () -> assertThat(EmbeddingSchema.VERSION).isEqualTo(CURRENT_VERSION));
        claim(
                "relevance_label is present in the schema this version claims to describe, and so are"
                        + " relevance_score, vector, seed_corpus_comparison and unusable_seed: a version"
                        + " describes every table this part of the system owns, not only the newest one",
                () -> assertThat(tableNames())
                        .contains(TABLE_THIS_VERSION_ADDED)
                        .contains(TABLE_VERSION_FOUR_ADDED)
                        .contains(TABLE_VERSION_THREE_ADDED)
                        .contains(TABLE_VERSION_TWO_ADDED)
                        .contains(TABLE_VERSION_ONE_ADDED));
    }

    /** Every table the database this test runs against actually holds. */
    private List<String> tableNames() {
        return jdbcTemplate.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'table'", String.class);
    }

}
