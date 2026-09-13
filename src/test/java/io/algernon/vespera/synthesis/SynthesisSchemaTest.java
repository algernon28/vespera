package io.algernon.vespera.synthesis;

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
 * {@code synthesis} states and is refused against its own schema version, independently of every other
 * module's (ADR-059) — the direct sibling of {@code EmbeddingSchemaTest} and {@code
 * SimilaritySchemaTest}, and pinned separately from the guard's generic behaviour so that a change to
 * the {@code cluster} table which forgets to bump the constant is caught by this module's own check
 * rather than by a stale green build somewhere else.
 *
 * <p>It matters most for the newest module in the tree. {@code synthesis} arrived after databases in
 * the field already existed, so every one of them records no row for it at all until the first run
 * that reaches this stage writes one.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Arrangement")
@Feature("Schema versioning")
@Issue("175")
@Link(name = "ADR-059", url = Adr.SCHEMA_VERSION_IS_ONE_ROW_PER_MODULE, type = "adr")
@Link(name = "ADR-105", url = Adr.STAGE_6A_NAMES_THE_ARRANGEMENT_STAGE_5_BUILT, type = "adr")
class SynthesisSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("synthesis refuses to start against a database recording a version other than its own")
    void refusesToStartAgainstAMismatchedDatabase() {
        int somethingOtherThanSynthesisExpects = SynthesisSchema.VERSION + 1;
        jdbcTemplate.update(
                "INSERT INTO schema_version (module, version) VALUES (?, ?)",
                SynthesisSchema.MODULE,
                somethingOtherThanSynthesisExpects);

        claim(
                "this module's own check runs independently of every other module's, and refuses before"
                        + " anything reads or writes a row about how the documents are arranged",
                () -> assertThatThrownBy(() -> new SynthesisSchema(new SchemaVersionGuard(jdbcTemplate)))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(SynthesisSchema.MODULE)
                        .hasMessageContaining(String.valueOf(SynthesisSchema.VERSION))
                        .hasMessageContaining(String.valueOf(somethingOtherThanSynthesisExpects)));
    }

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("synthesis records its own version on first use, independent of any other module's row")
    void recordsItsOwnVersionOnFirstUse() {
        new SynthesisSchema(new SchemaVersionGuard(jdbcTemplate));

        claim(
                "the version recorded is this module's first, the one the arrangement table arrived in --"
                        + " not a value borrowed from a module that happens to have been in the database"
                        + " longer",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT version FROM schema_version WHERE module = ?",
                                Integer.class,
                                SynthesisSchema.MODULE))
                        .isEqualTo(SynthesisSchema.VERSION));
    }
}
