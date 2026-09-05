package io.algernon.vespera.extraction;

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
 * {@code extraction} states and is refused against its own schema version, independently of every
 * other module's (ADR-059) — pinned separately so a change to {@code ExtractionSchema.VERSION} that
 * forgets to bump the constant, or a database still recording version 2 (before {@code
 * confidence_distribution} existed), is caught by this module's own check rather than by a stale green
 * build elsewhere.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Schema versioning")
@Issue("59")
@Link(name = "ADR-059", url = Adr.SCHEMA_VERSION_IS_ONE_ROW_PER_MODULE, type = "adr")
@Link(name = "ADR-075", url = Adr.STAGE_3_WRITES_A_CONFIDENCE_DISTRIBUTION_REPORT, type = "adr")
class ExtractionSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("extraction refuses to start against a database recording a version other than its own")
    void refusesToStartAgainstAMismatchedDatabase() {
        int somethingOtherThanExtractionExpects = ExtractionSchema.VERSION + 1;
        jdbcTemplate.update(
                "INSERT INTO schema_version (module, version) VALUES (?, ?)",
                ExtractionSchema.MODULE,
                somethingOtherThanExtractionExpects);

        claim(
                "extraction's own check runs independently of every other module's, and refuses before"
                        + " anything reads or writes confidence_distribution",
                () -> assertThatThrownBy(() -> new ExtractionSchema(new SchemaVersionGuard(jdbcTemplate)))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(ExtractionSchema.MODULE)
                        .hasMessageContaining(String.valueOf(ExtractionSchema.VERSION))
                        .hasMessageContaining(String.valueOf(somethingOtherThanExtractionExpects)));
    }

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("extraction records its own version on first use, independent of any other module's row")
    void recordsItsOwnVersionOnFirstUse() {
        new ExtractionSchema(new SchemaVersionGuard(jdbcTemplate));

        claim(
                "the version recorded is exactly VERSION 3, the confidence_distribution bump -- not a"
                        + " value borrowed from ledger, corpus or similarity's own rows",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT version FROM schema_version WHERE module = ?",
                                Integer.class,
                                ExtractionSchema.MODULE))
                        .isEqualTo(ExtractionSchema.VERSION));
    }

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("VERSION is the literal 3, and confidence_distribution is the table that came with it")
    void versionIsTheConfidenceDistributionBumpLiterally() {
        claim(
                "the version and the table it names arrived together, so a later table added without a"
                        + " bump would leave this constant already committed to the wrong value",
                () -> assertThat(ExtractionSchema.VERSION).isEqualTo(3));
        claim(
                "confidence_distribution is present in the schema this VERSION claims to describe",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                                String.class,
                                "confidence_distribution"))
                        .isEqualTo("confidence_distribution"));
    }
}
