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

    /** The version this module is on. Version 7 re-keyed a table rather than adding one (ADR-097). */
    private static final int CURRENT_VERSION = 7;

    /** The table version 7 re-keyed, from the occurrence and the seed set to the path and the seed set. */
    private static final String THE_RE_KEYED_TABLE = "relevance_label";

    /** The first half of that key: the path relative to the corpus root (ADR-051). */
    private static final String PATH_COLUMN = "path";

    /** The second half: the seed folder the answer was given about, itself a canonical path. */
    private static final String SEED_SET_COLUMN = "seed_set";

    /** What the key stopped being, and what the table must now hold no column for at all. */
    private static final String OCCURRENCE_COLUMN = "occurrence_id";

    /** The table version 6 arrived with, still described by the version above (ADR-087, ADR-045, #109). */
    private static final String TABLE_VERSION_SIX_ADDED = "document_cluster";

    /** The table version 4 arrived with, still described by the version above (ADR-020, #108). */
    private static final String TABLE_VERSION_FOUR_ADDED = "relevance_score";

    /** The table version 3 arrived with, still described by the version above (ADR-084, ADR-085, #107). */
    private static final String TABLE_VERSION_THREE_ADDED = "vector";

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
    @DisplayName("VERSION is the literal 7, and relevance_label is keyed by path because of it")
    @Issue("130")
    @Link(name = "ADR-020", url = Adr.RELEVANCE_SCORING_FUNCTION, type = "adr")
    @Link(name = "ADR-097", url = Adr.A_LABEL_IS_KEYED_BY_PATH_AND_SEED_SET, type = "adr")
    void versionIsTheRelevanceLabelReKeyLiterally() {
        claim(
                "the version and the change it names arrived together, so a later change to these tables"
                        + " made without a bump would leave this constant already committed to the wrong"
                        + " value. The literal 7 is stated here rather than read back off the constant,"
                        + " which would assert nothing",
                () -> assertThat(EmbeddingSchema.VERSION).isEqualTo(CURRENT_VERSION));
        claim(
                "relevance_label is keyed by the path and the seed set, and by nothing else. This"
                        + " version added no table -- it re-keyed that one, and a shape change is a bump"
                        + " whether or not a table arrives with it: a database written under the version"
                        + " before holds label rows keyed the old way, and the version is the only thing"
                        + " that refuses to read them",
                () -> assertThat(primaryKeyOf(THE_RE_KEYED_TABLE))
                        .containsExactly(PATH_COLUMN, SEED_SET_COLUMN));
        claim(
                "and it holds no column referencing file_occurrence at all. An occurrence id is"
                        + " per-walk, so a label keyed by one joins to nothing the next invocation scores"
                        + " -- the answers would sit in the table and stop being findable, which is worse"
                        + " than losing them because nothing reports their absence",
                () -> assertThat(columnsOf(THE_RE_KEYED_TABLE)).doesNotContain(OCCURRENCE_COLUMN));
        claim(
                "all six tables this version describes are present: a version describes every table this"
                        + " part of the system owns, not only the one that last moved",
                () -> assertThat(tableNames())
                        .contains(THE_RE_KEYED_TABLE)
                        .contains(TABLE_VERSION_SIX_ADDED)
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

    /** The primary key columns of {@code table}, in the order the key declares them. */
    private List<String> primaryKeyOf(String table) {
        return jdbcTemplate.queryForList(
                "SELECT name FROM pragma_table_info(?) WHERE pk > 0 ORDER BY pk", String.class, table);
    }

    /** Every column of {@code table}, so a claim can say what is absent as well as what is present. */
    private List<String> columnsOf(String table) {
        return jdbcTemplate.queryForList("SELECT name FROM pragma_table_info(?)", String.class, table);
    }

}
