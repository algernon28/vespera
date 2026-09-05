package io.algernon.vespera.similarity;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code similarity} states and is refused against its own schema version, independently of every
 * other module's (ADR-059) -- pinned separately from {@link SchemaVersionGuardTest}'s generic
 * behaviour so a change to {@code SimilaritySchema.VERSION} that forgets to bump the constant, or a
 * database still recording version 1 (before {@code shingle_document_frequency} and
 * {@code shingle_corpus_size} existed), is caught by this module's own check rather than by a stale
 * green build elsewhere.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Schema versioning")
@Issue("58")
@Link(name = "ADR-059", url = Adr.SCHEMA_VERSION_IS_ONE_ROW_PER_MODULE, type = "adr")
@Link(name = "ADR-074", url = Adr.STAGE_3_MEASURES_SHINGLE_DOCUMENT_FREQUENCY, type = "adr")
class SimilaritySchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("similarity refuses to start against a database recording a version other than its own")
    void refusesToStartAgainstAMismatchedDatabase() {
        int somethingOtherThanSimilarityExpects = SimilaritySchema.VERSION + 1;
        jdbcTemplate.update(
                "INSERT INTO schema_version (module, version) VALUES (?, ?)",
                SimilaritySchema.MODULE,
                somethingOtherThanSimilarityExpects);

        claim(
                "similarity's own check runs independently of every other module's, and refuses before"
                        + " anything reads or writes shingle_document_frequency or shingle_corpus_size",
                () -> assertThatThrownBy(() -> new SimilaritySchema(new SchemaVersionGuard(jdbcTemplate)))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(SimilaritySchema.MODULE)
                        .hasMessageContaining(String.valueOf(SimilaritySchema.VERSION))
                        .hasMessageContaining(String.valueOf(somethingOtherThanSimilarityExpects)));
    }

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("similarity records its own version on first use, independent of any other module's row")
    void recordsItsOwnVersionOnFirstUse() {
        new SimilaritySchema(new SchemaVersionGuard(jdbcTemplate));

        claim(
                "the version recorded is exactly VERSION 2, the shingle-document-frequency bump -- not a"
                        + " value borrowed from ledger, corpus or extraction's own rows",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT version FROM schema_version WHERE module = ?",
                                Integer.class,
                                SimilaritySchema.MODULE))
                        .isEqualTo(SimilaritySchema.VERSION));
    }
}
