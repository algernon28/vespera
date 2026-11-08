package io.algernon.vespera.engine.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.SchemaVersionGuard;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
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
                Arguments.of(Named.of(
                        "the version before the cache key table", THE_VERSION_BEFORE_THE_CACHE_KEY_TABLE)));
    }
}
