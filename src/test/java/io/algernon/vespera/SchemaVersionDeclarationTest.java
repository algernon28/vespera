package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.ledger.SchemaVersionGuard;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * ADR-059's other half: every module that owns tables states the schema it was built against.
 *
 * <p>{@link io.algernon.vespera.ledger.SchemaVersionGuard} already refuses a module whose recorded
 * version is not the one its code expects, and {@code SchemaVersionGuardTest} pins that refusal. But
 * a module that never calls the guard at all is refused by nothing: it reads and writes a database
 * it has made no claim about, and the first sign of trouble is a column that is not there. That is
 * the gap this covers — not a wrong version, but no version.
 *
 * <p>It is written as a scan rather than a list of imports on purpose. Naming the components here
 * would make the test pass by construction, because the component that is missing is exactly the one
 * nobody would think to name.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(SchemaVersionGuard.class)
@ComponentScan(
        basePackages = "io.algernon.vespera",
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io[.]algernon[.]vespera[.].*Schema"))
@Epic("Architecture")
@Feature("Schema versioning")
@Issue("9")
@Link(name = "ADR-059", url = Adr.SCHEMA_VERSION_IS_ONE_ROW_PER_MODULE, type = "adr")
class SchemaVersionDeclarationTest {

    /**
     * The modules owning tables in {@code schema.sql}, and therefore the modules owing a version.
     *
     * <p>{@code profile} is absent because it owns no tables — it is a YAML file (ADR-061), and a
     * schema version for it would be a claim about nothing. {@code pipeline} is absent because it
     * composes the others and owns no tables of its own. Add a module here in the same commit that
     * gives it its first table.
     */
    private static final Set<String> MODULES_OWNING_TABLES =
            Set.of("ledger", "corpus", "extraction", "similarity");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("Every module that owns tables has declared the schema version it expects")
    void everyModuleOwningTablesDeclaresItsSchemaVersion() {
        claim(
                "the modules that recorded a version are exactly the modules that own tables — a name"
                        + " missing here is a module reading a database it never checked, and a name"
                        + " unexpected here is a version claimed for tables nobody owns",
                () -> assertThat(modulesDeclaringAVersion())
                        .containsExactlyInAnyOrderElementsOf(MODULES_OWNING_TABLES));
    }

    private List<String> modulesDeclaringAVersion() {
        return jdbcTemplate.queryForList("SELECT module FROM schema_version", String.class);
    }
}
