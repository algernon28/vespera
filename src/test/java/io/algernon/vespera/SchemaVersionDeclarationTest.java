package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.engine.ledger.SchemaVersionGuard;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * <p>{@link io.algernon.vespera.engine.ledger.SchemaVersionGuard} already refuses a module whose recorded
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
     * The modules owning tables in {@code schema.sql}, and therefore the modules owing a version: read off the
     * line above each table that names its owner, so that a module given its first table owes a version from
     * that commit on without anyone adding it to a list here.
     *
     * <p>{@code profile} owns no tables — it is a YAML file (ADR-061) — and {@code pipeline} composes the others
     * and owns none of its own, so neither is named by a line of the schema.
     */
    private static final Path THE_SCHEMA = Path.of("src", "main", "resources", "schema.sql");

    /** The line directly above a table of the schema that names the module owning it. */
    private static final Pattern AN_OWNER = Pattern.compile("^-- owner: ([a-z]+)$", Pattern.MULTILINE);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A module states the schema it was built against")
    @DisplayName("Every module that owns tables has declared the schema version it expects")
    void everyModuleOwningTablesDeclaresItsSchemaVersion() throws IOException {
        Set<String> owningTables = new TreeSet<>();
        Matcher owner = AN_OWNER.matcher(Files.readString(THE_SCHEMA));
        while (owner.find()) {
            owningTables.add(owner.group(1));
        }
        claim("the schema names the owner of its tables", () -> assertThat(owningTables).isNotEmpty());

        claim(
                "the modules that recorded a version are exactly the modules that own tables — a name"
                        + " missing here is a module reading a database it never checked, and a name"
                        + " unexpected here is a version claimed for tables nobody owns",
                () -> assertThat(modulesDeclaringAVersion())
                        .containsExactlyInAnyOrderElementsOf(owningTables));
    }

    private List<String> modulesDeclaringAVersion() {
        return jdbcTemplate.queryForList("SELECT module FROM schema_version", String.class);
    }
}
