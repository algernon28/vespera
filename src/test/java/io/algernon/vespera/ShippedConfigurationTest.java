package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * What the shipped configuration must still say, read as flattened properties rather than as text.
 *
 * <p><b>This exists because of a defect a whole test suite could not see.</b> Adding a key under
 * {@code spring.ai.ollama} between two keys of {@code spring.ai.model} left the file valid YAML and
 * every unit test green, while quietly re-parenting three of the second mapping's keys onto the
 * first. The engines those keys switch off came back on, one of them demanded an API key, and the
 * application context stopped loading — which only the integration tests could notice, because they
 * are the only ones that build the whole context.
 *
 * <p>A nesting mistake is the one editing error this file invites and the one nothing else catches:
 * indentation carries all the meaning, a wrong level is still well-formed, and the keys that go
 * missing are the ones whose job is to keep something from happening. Reading the file the way Spring
 * reads it and naming the properties that must survive turns a three-minute failure that needs Docker
 * into an immediate one that needs nothing.
 */
@Epic("Architecture")
@Feature("Shipped configuration")
@Issue("179")
@Link(name = "ADR-012", url = Adr.EXTRACTION_ENGINE_IS_CONFIGURABLE, type = "adr")
@Link(name = "ADR-114", url = Adr.THE_GENERATION_MODEL_IS_CONFIGURATION_WITH_A_DEFAULT, type = "adr")
class ShippedConfigurationTest {

    /** The file the application ships with, layered over by {@code application-test.yaml} at test time. */
    private static final String SHIPPED = "application.yaml";

    /**
     * The engines that must stay switched off. Each is a Spring AI starter on the classpath for a
     * reason other than being used by default; left unnamed, its auto-configuration activates and the
     * OpenAI ones ask for credentials nobody has configured.
     */
    private static final List<String> SWITCHED_OFF = List.of(
            "spring.ai.model.image",
            "spring.ai.model.moderation",
            "spring.ai.model.audio.transcription",
            "spring.ai.model.audio.speech");

    /** What an engine is set to when it is switched off. */
    private static final String OFF = "none";

    /** The engine that serves, named for both the capabilities this application actually uses. */
    private static final List<String> SERVED_BY_OLLAMA = List.of("spring.ai.model.chat", "spring.ai.model.embedding");

    /** Ollama's own name for the engine, as the two keys above select it. */
    private static final String OLLAMA = "ollama";

    /** Where the generation model's shipped default lives, and where Spring AI itself reads it. */
    private static final String GENERATION_MODEL = "spring.ai.ollama.chat.options.model";

    /** The datasource URL, and the only place the database lock wait is set (ADR-127). */
    private static final String DATASOURCE_URL = "spring.datasource.url";

    /**
     * Five minutes, in milliseconds: the lock wait the shipped configuration names. Named here
     * because the claim below states the number and a reader has to be told where it comes from.
     */
    private static final long LOCK_WAIT_MILLIS = 300_000L;

    /** SQLite's own name for the lock wait, as the datasource URL must carry it. */
    private static final String LOCK_WAIT_PARAMETER = "busy_timeout=" + LOCK_WAIT_MILLIS;

    /** The other URL parameter the datasource must keep, so foreign key constraints are not ignored. */
    private static final String FOREIGN_KEYS_PARAMETER = "foreign_keys=on";

    /** Where the shipped URL points its database, resolved to a real directory by the driver test. */
    private static final String WORKING_DIRECTORY_PLACEHOLDER = "${vespera.working-dir}";

    @Test
    @Story("An engine nobody asked for stays switched off")
    @DisplayName("Every engine the application does not use is still named as unused")
    void keepsTheUnusedEnginesSwitchedOff() throws IOException {
        Map<Object, Object> properties = shippedProperties();

        claim(
                "each of the " + SWITCHED_OFF.size() + " engines this application does not use is still"
                        + " named and still set to \"" + OFF + "\": an engine left unnamed configures itself,"
                        + " and the ones on the classpath for other reasons ask for credentials on startup"
                        + " -- so a missing key here is not a missing setting but an application that will"
                        + " not start",
                () -> assertThat(SWITCHED_OFF).allSatisfy(key -> assertThat(properties)
                        .containsEntry(key, OFF)));
    }

    @Test
    @Story("An engine nobody asked for stays switched off")
    @DisplayName("The engine that does serve is still named for both the things it serves")
    void namesTheServingEngineForBothCapabilities() throws IOException {
        Map<Object, Object> properties = shippedProperties();

        claim(
                "both capabilities this application uses still name their engine, so which runtime serves"
                        + " stays a matter of configuration rather than of whichever starter happens to be"
                        + " on the classpath",
                () -> assertThat(SERVED_BY_OLLAMA).allSatisfy(key -> assertThat(properties)
                        .containsEntry(key, OLLAMA)));
    }

    @Test
    @Story("The generation model ships with a default")
    @DisplayName("The model the connecting text is written with is named, and is not blank")
    void namesTheGenerationModel() throws IOException {
        Map<Object, Object> properties = shippedProperties();

        claim(
                "a name is shipped, because this is the one value the operator is never asked to supply:"
                        + " unset everywhere, the run refuses rather than composing an identity around a"
                        + " model nobody named",
                () -> assertThat(properties.get(GENERATION_MODEL))
                        .asString()
                        .isNotBlank());
    }

    /**
     * ADR-127: the five-minute wait a stage needs on a contended database lock lives in the
     * datasource URL as SQLite's own {@code busy_timeout}, not in Hikari's {@code connection-timeout},
     * which waits for a connection from the pool and never sees the lock. Before this, the URL set
     * no busy timeout, so a contended write failed on the driver's three-second default while the
     * configuration promised five minutes.
     */
    @Test
    @Story("A stage waiting on the database lock waits rather than failing")
    @DisplayName("The shipped datasource URL names the wait SQLite applies to a contended lock")
    @Issue("214")
    @Link(name = "ADR-127", url = Adr.A_DATABASE_LOCK_WAIT_IS_SQLITES_BUSY_TIMEOUT, type = "adr")
    void namesTheLockWaitInTheDatasourceUrl() throws IOException {
        String url = String.valueOf(shippedProperties().get(DATASOURCE_URL));

        claim(
                "the datasource URL still turns foreign key constraints on: SQLite ignores them per"
                        + " connection unless told otherwise, and the walk_id references in schema.sql"
                        + " would silently accept orphans without it",
                () -> assertThat(url).contains(FOREIGN_KEYS_PARAMETER));
        claim(
                "and it names SQLite's own busy timeout at five minutes — " + LOCK_WAIT_MILLIS
                        + " ms — because that, and not Hikari's connection timeout, is the wait a"
                        + " contended write observes: left at the driver's three-second default, a stage"
                        + " gives up on a lock after three seconds where the configuration promises five"
                        + " minutes, and a run that fails costs more than one that waits",
                () -> assertThat(url).contains(LOCK_WAIT_PARAMETER));
    }

    /**
     * ADR-127's behavioural half: the URL parameter is SQLite's own knob, so a connection opened with
     * the shipped URL reports the value. The text claim above would pass on a parameter the driver
     * did not read, and this is what fails instead.
     */
    @Test
    @Story("A stage waiting on the database lock waits rather than failing")
    @DisplayName("The wait the shipped URL names is the one the database driver reports")
    @Issue("214")
    @Link(name = "ADR-127", url = Adr.A_DATABASE_LOCK_WAIT_IS_SQLITES_BUSY_TIMEOUT, type = "adr")
    void theDriverReportsTheLockWaitTheUrlNames(@TempDir Path workingDirectory) throws Exception {
        String url = String.valueOf(shippedProperties().get(DATASOURCE_URL))
                .replace(WORKING_DIRECTORY_PLACEHOLDER, workingDirectory.toString().replace('\\', '/'));

        try (Connection connection = DriverManager.getConnection(url)) {
            claim(
                    "a connection opened with the shipped URL reports a " + LOCK_WAIT_MILLIS
                            + " ms busy timeout, so the value is the one SQLite's driver reads rather"
                            + " than a setting only Hikari understands; a claim on the URL text alone"
                            + " would pass while the write still failed after three seconds",
                    () -> assertThat(busyTimeoutMillis(connection)).isEqualTo(LOCK_WAIT_MILLIS));
        }
    }

    /** What SQLite reports as this connection's lock wait, in milliseconds. */
    private static long busyTimeoutMillis(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA busy_timeout")) {
            result.next();
            return result.getLong(1);
        }
    }

    /** The shipped file flattened to properties, exactly as Spring reads it — nesting and all. */
    private static Map<Object, Object> shippedProperties() throws IOException {
        ClassPathResource resource = new ClassPathResource(SHIPPED);
        try (InputStream ignored = resource.getInputStream()) {
            YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(resource);
            yaml.afterPropertiesSet();
            return yaml.getObject();
        }
    }
}
