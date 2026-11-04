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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
@Link(name = "ADR-139", url = Adr.A_REFUSED_CONVERSION_LEAVES_A_FAULT_ROW, type = "adr")
@Link(name = "ADR-206", url = Adr.STAGE_2_RECORDS_ITS_EXTRACTION_CACHE_KEY, type = "adr")
class ExtractionSchemaTest {

    /** The version a working directory records when it was written before the cache key table (ADR-139). */
    private static final int THE_VERSION_BEFORE_THE_CACHE_KEY_TABLE = 5;

    /** The version the cache key table came with (ADR-206 section 9). */
    private static final int THE_VERSION_WITH_IT = 6;

    /** A version no build has written yet: one after {@link #THE_VERSION_WITH_IT}. */
    private static final int THE_VERSION_AFTER_IT = 7;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest(name = "{0}")
    @MethodSource("versionsOtherThanThisBuilds")
    @Issue("349")
    @Story("A module states the schema it was built against")
    @DisplayName("extraction refuses to start against a database recording a version other than its own")
    void refusesToStartAgainstAMismatchedDatabase(int recorded) {
        jdbcTemplate.update(
                "INSERT INTO schema_version (module, version) VALUES (?, ?)", ExtractionSchema.MODULE, recorded);

        claim(
                "a database recording version " + recorded + " is refused before anything reads or writes a row of"
                        + " extraction's, by extraction's own check and in the words every refusal of a version"
                        + " uses: the module, the version found and the version " + THE_VERSION_WITH_IT + " this"
                        + " build expects. A database of the version before the cache key table holds no row saying"
                        + " which conversion belongs to which document, and none can be filled in without reading"
                        + " every file again, so the way forward is a new working directory",
                () -> assertThatThrownBy(() -> new ExtractionSchema(new SchemaVersionGuard(jdbcTemplate)))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(ExtractionSchema.MODULE)
                        .hasMessageContaining(String.valueOf(recorded))
                        .hasMessageContaining(String.valueOf(THE_VERSION_WITH_IT)));
    }

    static Stream<Arguments> versionsOtherThanThisBuilds() {
        return Stream.of(
                Arguments.of(Named.of("a version after this build's", THE_VERSION_AFTER_IT)),
                Arguments.of(Named.of(
                        "the version before the cache key table", THE_VERSION_BEFORE_THE_CACHE_KEY_TABLE)));
    }

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("extraction records its own version on first use, independent of any other module's row")
    void recordsItsOwnVersionOnFirstUse() {
        new ExtractionSchema(new SchemaVersionGuard(jdbcTemplate));

        claim(
                "the version recorded is exactly " + THE_VERSION_WITH_IT + ", the extraction_cache_key bump -- not"
                        + " a value borrowed from ledger, corpus or similarity's own rows",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT version FROM schema_version WHERE module = ?",
                                Integer.class,
                                ExtractionSchema.MODULE))
                        .isEqualTo(THE_VERSION_WITH_IT));
    }

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("VERSION is the literal 6, extraction_cache_key came with it, and extraction_fault is still there")
    @Issue("349")
    void versionIsTheCacheKeyTableLiterally() {
        claim(
                "the version and the change it names arrived together, so a later table or column"
                        + " changed without a bump would leave this constant already committed to the"
                        + " wrong value",
                () -> assertThat(ExtractionSchema.VERSION).isEqualTo(THE_VERSION_WITH_IT));
        claim(
                "extraction_cache_key is present in the schema this VERSION claims to describe, which is"
                        + " what a database written before it does not have: no row there says which"
                        + " conversion on record belongs to which document, so a database of the version"
                        + " before is refused rather than read",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                                String.class,
                                "extraction_cache_key"))
                        .containsExactly("extraction_cache_key"));
        claim(
                "extraction_fault, the table the version before this one came with, is still present:"
                        + " in a database written before that one, a refused conversion left no"
                        + " row there at all, so the occurrence read as one no stage had removed",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                                String.class,
                                "extraction_fault"))
                        .isEqualTo("extraction_fault"));
        claim(
                "and it is keyed by the occurrence and the run together, which is what lets a stopped"
                        + " invocation's rows be discarded and written again rather than have the second"
                        + " attempt collide with the first",
                () -> assertThat(jdbcTemplate.queryForList(
                                "SELECT name FROM pragma_table_info('extraction_fault') WHERE pk > 0"
                                        + " ORDER BY pk",
                                String.class))
                        .containsExactly("occurrence_id", "run_id"));
    }
}
