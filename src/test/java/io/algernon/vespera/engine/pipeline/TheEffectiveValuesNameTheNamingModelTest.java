package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ui.EffectiveValuesForTests;
import io.algernon.vespera.ui.EffectiveValuesForTests.Said;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * {@code effective-values.properties} names the model that names the topics, {@code vespera.naming.model}, as the
 * arrangement step resolves it (ADR-403 §2, §10): its own key where configuration names it, else
 * {@code spring.ai.ollama.chat.options.model}, each with the source of the layer that named it, else {@code qwen3:8b}
 * from {@code code}; and where it is bound blank, nothing, not the chat model said in its place, since a blank naming
 * model stops the invocation and no invocation uses the chat model for it. The file is read back as the control panel
 * reads it, through {@link EffectiveValuesForTests}, so the key the writer spells is the key the reader finds.
 *
 * <p>Each layer stands in for one Spring Boot gives a process, under the name it gives it: a system property, said
 * {@code environment}, and {@code application-local.yaml}, said {@code local-file}.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("829")
@Link(name = "ADR-403", url = Adr.TOPICS_ARE_NAMED_AT_THE_HEAD_OF_THE_ARRANGEMENT, type = "adr")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class TheEffectiveValuesNameTheNamingModelTest {

    /** The key the file names the model that names the topics under: its own property. */
    private static final String NAMING_MODEL = "vespera.naming.model";

    /** The chat model it falls back to, which the writing step's default is read from. */
    private static final String CHAT_MODEL = "spring.ai.ollama.chat.options.model";

    /** The model the code holds where configuration names neither key: the shipped writing model. */
    private static final String THE_CODES_MODEL = "qwen3:8b";

    /** A chat model of the test's own, named in the local settings file. */
    private static final String A_CHAT_MODEL = "a-writing-model:8b";

    /** A naming model of the test's own, named in the environment. */
    private static final String A_NAMING_MODEL = "a-naming-model:4b";

    /** The name Spring Boot gives a file of application configuration other than the shipped one. */
    private static final String THE_LOCAL_FILE =
            "Config resource 'file [application-local.yaml]' via location 'optional:file:./'";

    /** The name Spring Boot gives the system properties, said as the environment. */
    private static final String THE_ENVIRONMENT = "systemProperties";

    /** One layer of configuration: the name a process gives it, and what it names. */
    private record Layer(String name, Map<String, Object> values) {

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Arguments> whatTheFileSaysOfTheNamingModel() {
        Layer chatModelInTheLocalFile = new Layer(THE_LOCAL_FILE, Map.of(CHAT_MODEL, A_CHAT_MODEL));
        return Stream.of(
                Arguments.of("nothing named: the model the code holds, from the code",
                        List.of(),
                        Optional.of(new Said(THE_CODES_MODEL, "code"))),
                Arguments.of("the chat model alone named: it, from the local settings file",
                        List.of(chatModelInTheLocalFile),
                        Optional.of(new Said(A_CHAT_MODEL, "local-file"))),
                Arguments.of("its own key named over the chat model: it, from the environment",
                        List.of(new Layer(THE_ENVIRONMENT, Map.of(NAMING_MODEL, A_NAMING_MODEL)), chatModelInTheLocalFile),
                        Optional.of(new Said(A_NAMING_MODEL, "environment"))),
                Arguments.of("its own key bound blank over the chat model: nothing, not the chat model",
                        List.of(new Layer(THE_ENVIRONMENT, Map.of(NAMING_MODEL, "  ")), chatModelInTheLocalFile),
                        Optional.empty()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("whatTheFileSaysOfTheNamingModel")
    @Story("The jar says what it would use")
    @DisplayName("The jar says which model names the topics, and where that came from, as the step that names them would resolve it")
    void theFileSaysTheNamingModelAsTheArrangementResolvesIt(String shape, List<Layer> layers, Optional<Said> expected,
            @TempDir Path workingDirectory) {
        MockEnvironment environment = new MockEnvironment();
        for (Layer layer : layers) {
            environment.getPropertySources().addLast(new MapPropertySource(layer.name(), layer.values()));
        }

        new EffectiveValuesFile(environment, workingDirectory).write();
        Map<String, Said> read = EffectiveValuesForTests.read(workingDirectory).orElseThrow(
                () -> new AssertionError("the control panel read no file the jar wrote"));

        claim("with " + shape + ", the file says of the model that names the topics exactly that, with its source",
                () -> assertThat(Optional.ofNullable(read.get(NAMING_MODEL))).isEqualTo(expected));
    }
}
