package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.ui.ProfileKey;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
     * Model kinds no starter on the classpath serves today (ADR-195). Each stays named and set to none
     * so that a starter added later is not switched on by default and does not ask for credentials.
     */
    private static final List<String> SWITCHED_OFF = List.of(
            "spring.ai.model.image",
            "spring.ai.model.moderation",
            "spring.ai.model.audio.transcription",
            "spring.ai.model.audio.speech");

    /** The engine that serves, named for both the capabilities this application actually uses. */
    private static final List<String> SERVED_BY_OLLAMA = List.of("spring.ai.model.chat", "spring.ai.model.embedding");

    /** Where the embedding model's shipped default lives, and where Spring AI itself reads it (ADR-298). */
    private static final String EMBEDDING_MODEL = "spring.ai.ollama.embedding.options.model";

    /**
     * ADR-298 §1 as ADR-353 §7 holds it: a name is shipped, one name with no space in it, and which one is the shipped
     * file's alone. No test names the model, so a changed default changes this file and the record, and no test.
     */
    @Test
    @Story("The embedding model ships with a default")
    @DisplayName("The model documents are scored with is named, as one name, and is not blank")
    @Link(name = "ADR-298", url = Adr.THE_EMBEDDING_MODEL_SHIPS_WITH_A_DEFAULT, type = "adr")
    @Link(name = "ADR-353", url = Adr.THE_EMBEDDING_MODELS_DEFAULT_IS_QWEN3_EMBEDDING_8B_BY_ITS_FILE_ON_HUGGING_FACE, type = "adr")
    void namesTheEmbeddingModel() throws IOException {
        Map<Object, Object> properties = shippedProperties();

        claim(
                "a name is shipped, so a corpus whose profile names no model is scored rather than stopped at the"
                        + " question of which model to score with, and it is one name with no space in it, as the"
                        + " serving engine reads a model's name",
                () -> assertThat(properties.get(EMBEDDING_MODEL))
                        .asString()
                        .isNotBlank()
                        .doesNotContainAnyWhitespaces());
    }

    /**
     * ADR-377 §4: the comparing model's tooltip names the model Vespera comes with, which is the shipped file's, so a
     * changed default that leaves the tooltip behind fails here.
     */
    @Test
    @Story("The embedding model ships with a default")
    @DisplayName("The comparing model's tooltip names the model Vespera comes with")
    @Issue("802")
    @Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
    void theComparingModelsTooltipNamesTheShippedModel() throws IOException {
        Object shipped = shippedProperties().get(EMBEDDING_MODEL);

        claim("the comparing model's tooltip holds the name of the embedding model the shipped file names",
                () -> assertThat(ProfileKey.EMBEDDING_MODEL.tooltip()).contains(String.valueOf(shipped)));
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
