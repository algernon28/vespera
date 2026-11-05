package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.EffectiveValuesForTests;
import io.algernon.controlpanel.EffectiveValuesForTests.Said;
import io.algernon.controlpanel.ProfileKey;
import io.algernon.vespera.Adr;
import io.algernon.vespera.synthesis.ClusterSynthesis;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * The control panel parses {@code effective-values.properties} itself, linking no class of the jar (ADR-285 §3 as
 * amended by ADR-330 §1); this is the contract that holds its reading, {@code EffectiveValues}, to what the jar's writer,
 * {@link EffectiveValuesFile}, writes, on a test class path that sees both. Each expected reading is written out whole,
 * so a key the writer leaves out, or a source it does not name, fails it.
 *
 * <p>The sources stand in for the layers Spring Boot gives a process, under the names it gives them;
 * {@code TheJarSaysWhereEachValueCameFromInvocationTest} holds those names on a real context.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class TheControlPanelReadsTheEffectiveValuesTheJarWritesTest {

    private static final String THE_JARS_OWN_FILE =
            "Config resource 'class path resource [application.yaml]' via location 'optional:classpath:/'";

    private static final String ANOTHER_FILE =
            "Config resource 'file [application-local.yaml]' via location 'optional:file:./'";

    private static final String THE_COMMAND_LINE = "commandLineArgs";

    private static final String A_SYSTEM_PROPERTY = "systemProperties";

    private static final String EMBEDDING_MODEL = "spring.ai.ollama.embedding.options.model";

    /**
     * The embedding model this test's layer of the jar's own file names: a name of the test's own, with a slash and a
     * colon in it as a model file's name on a registry has, and not the one the jar ships (ADR-346 §7).
     */
    private static final String AN_EMBEDDING_MODEL_OF_THE_JARS_FILE = "a.registry/an-owner/a-comparing-model:Q4";

    private static final String GENERATION_MODEL = "spring.ai.ollama.chat.options.model";

    /**
     * The generation model this test's layer of the jar's own file names, which the label model falls back to: a name
     * of the test's own, not the one the jar ships (ADR-346 §7).
     */
    private static final String A_GENERATION_MODEL_OF_THE_JARS_FILE = "a-writing-model:8b";

    private static final String IMAGE = "vespera.docling.image";

    private static final String START = "vespera.sidecars.start";

    private static final String WAIT_AT_MOST = "vespera.sidecars.wait-at-most";

    private static final String PROGRESS_INTERVAL = "vespera.progress-interval";

    private static final String OPEN_BROWSER = "vespera.open-browser";

    private static final String DOCLING_ADDRESS = "vespera.docling.base-url";

    private static final String OLLAMA_ADDRESS = "spring.ai.ollama.base-url";

    /** The label model, said under the launch value's own key (ADR-334 §2). */
    private static final String LABEL_MODEL = "vespera.label.model";

    /** One layer of configuration: the name a process gives it, and what it names. */
    private record Layer(String name, Map<String, Object> values) {

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * What every file says though nothing names it: the two values, the two addresses and the label model the code
     * holds, the last since ADR-334 §2.
     */
    private static Map<String, Said> whatTheCodeHolds() {
        Map<String, Said> said = new LinkedHashMap<>();
        said.put("generationContextWindow", new Said(String.valueOf(ClusterSynthesis.CONTEXT_WINDOW), "code"));
        said.put("deliverableLanguage", new Said(DeliverableLanguage.DEFAULT.code(), "code"));
        said.put(DOCLING_ADDRESS, new Said(SidecarStartSettings.DEFAULT_DOCLING, "code"));
        said.put(OLLAMA_ADDRESS, new Said(SidecarStartSettings.DEFAULT_OLLAMA, "code"));
        said.put(LABEL_MODEL, new Said(EffectiveValuesFile.THE_CODES_LABEL_MODEL, "code"));
        return said;
    }

    private static Map<String, Said> whatTheCodeHoldsAnd(Map<String, Said> others) {
        Map<String, Said> said = whatTheCodeHolds();
        said.putAll(others);
        return said;
    }

    /** The presets of the shipped file, each said under its own name but the resolution, said under the profile's. */
    private static final Map<String, String> THE_PRESETS = Map.ofEntries(
            Map.entry("vespera.clustering.neighbours", "15"),
            Map.entry("vespera.docling.ocr-preset", "rapidocr"),
            Map.entry("vespera.docling.timeouts-in-a-row", "3"),
            Map.entry("vespera.redundancy.shingle-words", "5"),
            Map.entry("vespera.redundancy.minhash.permutations", "128"),
            Map.entry("vespera.redundancy.minhash.bands", "16"),
            Map.entry("vespera.redundancy.minhash.rows-per-band", "8"),
            Map.entry("vespera.redundancy.minhash.seed", "6840335614489015382"),
            Map.entry("vespera.redundancy.near-duplicate-jaccard", "0.8"),
            Map.entry("vespera.redundancy.containment-index", "0.95"),
            Map.entry("vespera.redundancy.rare-shingle-sample", "32"),
            Map.entry("vespera.redundancy.rare-shingle-hits", "24"),
            Map.entry("vespera.relevance.top-similarities", "3"),
            Map.entry("vespera.relevance.sample.bands", "5"),
            Map.entry("vespera.relevance.sample.per-band", "12"));

    /** A layer naming every key the application resolves but Ollama's address, which the shipped file does not name. */
    private static Layer theShippedFile() {
        Map<String, Object> values = new LinkedHashMap<>(Map.of(
                EMBEDDING_MODEL, AN_EMBEDDING_MODEL_OF_THE_JARS_FILE,
                GENERATION_MODEL, A_GENERATION_MODEL_OF_THE_JARS_FILE,
                IMAGE, "vespera/docling:v1.32.0",
                START, "true",
                WAIT_AT_MOST, "5m",
                PROGRESS_INTERVAL, "30s",
                OPEN_BROWSER, "true",
                DOCLING_ADDRESS, "http://localhost:5001",
                "vespera.clustering.resolution", "1.0"));
        values.putAll(THE_PRESETS);
        return new Layer(THE_JARS_OWN_FILE, values);
    }

    /** What the shipped file's presets are said as: the resolution under the profile's name, the rest under their own. */
    private static Map<String, Said> thePresetsAsSaid() {
        Map<String, Said> said = new LinkedHashMap<>();
        said.put("clusterResolution", new Said("1.0", "shipped"));
        THE_PRESETS.forEach((key, value) -> said.put(key, new Said(value, "shipped")));
        return said;
    }

    /** Each shape the jar writes: a value of each source, a value left out, an address cut, and characters it escapes. */
    static Stream<Arguments> whatTheJarWrites() {
        return Stream.of(
                Arguments.of("nothing named, the code's values alone", List.of(), whatTheCodeHolds()),
                Arguments.of("every value the shipped file names", List.of(theShippedFile()),
                        whatTheCodeHoldsAnd(thePresetsAnd(Map.of(
                                "embeddingModel", new Said(AN_EMBEDDING_MODEL_OF_THE_JARS_FILE, "shipped"),
                                "generationModel", new Said(A_GENERATION_MODEL_OF_THE_JARS_FILE, "shipped"),
                                IMAGE, new Said("vespera/docling:v1.32.0", "shipped"),
                                START, new Said("true", "shipped"),
                                WAIT_AT_MOST, new Said("5m", "shipped"),
                                PROGRESS_INTERVAL, new Said("30s", "shipped"),
                                OPEN_BROWSER, new Said("true", "shipped"),
                                DOCLING_ADDRESS, new Said("http://localhost:5001", "shipped"),
                                LABEL_MODEL, new Said(A_GENERATION_MODEL_OF_THE_JARS_FILE, "shipped"))))),
                Arguments.of("the label model named itself, over the generation model it would fall back to",
                        List.of(theShippedFile(), new Layer(A_SYSTEM_PROPERTY, Map.of(LABEL_MODEL, "a-label-model:4b"))),
                        whatTheCodeHoldsAnd(thePresetsAnd(Map.of(
                                "embeddingModel", new Said(AN_EMBEDDING_MODEL_OF_THE_JARS_FILE, "shipped"),
                                "generationModel", new Said(A_GENERATION_MODEL_OF_THE_JARS_FILE, "shipped"),
                                IMAGE, new Said("vespera/docling:v1.32.0", "shipped"),
                                START, new Said("true", "shipped"),
                                WAIT_AT_MOST, new Said("5m", "shipped"),
                                PROGRESS_INTERVAL, new Said("30s", "shipped"),
                                OPEN_BROWSER, new Said("true", "shipped"),
                                DOCLING_ADDRESS, new Said("http://localhost:5001", "shipped"),
                                LABEL_MODEL, new Said("a-label-model:4b", "environment"))))),
                Arguments.of("a value of each other source",
                        List.of(new Layer(ANOTHER_FILE, Map.of(EMBEDDING_MODEL, "a-model-of-this-machine")),
                                new Layer(A_SYSTEM_PROPERTY, Map.of(IMAGE, "an-image-of-this-machine")),
                                new Layer(THE_COMMAND_LINE, Map.of(WAIT_AT_MOST, "9s"))),
                        whatTheCodeHoldsAnd(Map.of(
                                "embeddingModel", new Said("a-model-of-this-machine", "local-file"),
                                IMAGE, new Said("an-image-of-this-machine", "environment"),
                                WAIT_AT_MOST, new Said("9s", "command-line")))),
                Arguments.of("a model bound blank, left out with its source",
                        List.of(new Layer(THE_JARS_OWN_FILE, Map.of(EMBEDDING_MODEL, "  ", GENERATION_MODEL, A_GENERATION_MODEL_OF_THE_JARS_FILE))),
                        whatTheCodeHoldsAnd(Map.of("generationModel", new Said(A_GENERATION_MODEL_OF_THE_JARS_FILE, "shipped"),
                                LABEL_MODEL, new Said(A_GENERATION_MODEL_OF_THE_JARS_FILE, "shipped")))),
                Arguments.of("addresses cut to scheme, host and port",
                        List.of(new Layer(ANOTHER_FILE, Map.of(
                                OLLAMA_ADDRESS, "http://someone:a-password@ollama.example:11434/api?token=a-token",
                                DOCLING_ADDRESS, "https://docling.example/convert"))),
                        whatTheCodeHoldsAnd(Map.of(
                                OLLAMA_ADDRESS, new Said("http://ollama.example:11434", "local-file"),
                                DOCLING_ADDRESS, new Said("https://docling.example", "local-file")))),
                Arguments.of("an address with no host, left out with its source",
                        List.of(new Layer(ANOTHER_FILE, Map.of(DOCLING_ADDRESS, "localhost:5001"))),
                        withoutTheDoclingAddress()),
                Arguments.of("characters a properties file escapes, and one outside ASCII",
                        List.of(new Layer(ANOTHER_FILE, Map.of(
                                EMBEDDING_MODEL, "modèllo:latest",
                                GENERATION_MODEL, "#a=model:one two",
                                IMAGE, " registry.example:5000/a\\b!"))),
                        whatTheCodeHoldsAnd(Map.of(
                                "embeddingModel", new Said("modèllo:latest", "local-file"),
                                "generationModel", new Said("#a=model:one two", "local-file"),
                                LABEL_MODEL, new Said("#a=model:one two", "local-file"),
                                IMAGE, new Said(" registry.example:5000/a\\b!", "local-file")))));
    }

    private static Map<String, Said> thePresetsAnd(Map<String, Said> others) {
        Map<String, Said> said = thePresetsAsSaid();
        said.putAll(others);
        return said;
    }

    private static Map<String, Said> withoutTheDoclingAddress() {
        Map<String, Said> said = whatTheCodeHolds();
        said.remove(DOCLING_ADDRESS);
        return said;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("whatTheJarWrites")
    @Story("The control panel shows the value in use and where it came from")
    @DisplayName("What the jar writes of the values beneath the profile, the control panel reads as the same, each with its source")
    void whatTheJarWritesTheControlPanelReads(String shape, List<Layer> layers, Map<String, Said> expected,
            @TempDir Path workingDirectory) {
        new EffectiveValuesFile(environmentOf(layers), workingDirectory).write();
        claim("the jar wrote the file", () -> assertThat(workingDirectory.resolve(EffectiveValuesFile.FILE_NAME))
                .isRegularFile());

        Optional<Map<String, Said>> read = EffectiveValuesForTests.read(workingDirectory);

        claim("the control panel reads the file under the name the jar writes it",
                () -> assertThat(EffectiveValuesForTests.FILE_NAME).isEqualTo(EffectiveValuesFile.FILE_NAME));
        claim("and reads " + shape + " as exactly these values, each with its source, the moment written being no value",
                () -> assertThat(read).hasValueSatisfying(said ->
                        assertThat(said).containsExactlyInAnyOrderEntriesOf(expected)));
    }

    /** A key the two spell differently would read {@code unset} on the page, though the jar has said what it uses. */
    @Test
    @Story("The control panel shows the value in use and where it came from")
    @DisplayName("The profile values the jar says lie beneath the profile are the ones the control panel says have a value beneath them")
    void theProfilesKeysTheJarNamesAreTheOnesTheControlPanelSaysHaveAValueBeneath(@TempDir Path workingDirectory) {
        new EffectiveValuesFile(environmentOf(List.of(theShippedFile())), workingDirectory).write();
        Map<String, Said> read = EffectiveValuesForTests.read(workingDirectory).orElseThrow();
        List<String> namedByTheJar = Arrays.stream(ProfileKey.values())
                .map(ProfileKey::key)
                .filter(read::containsKey)
                .toList();
        List<String> sayingTheyHaveOne = Arrays.stream(ProfileKey.values())
                .filter(ProfileKey::hasAValueBeneath)
                .map(ProfileKey::key)
                .toList();

        claim("the profile values the file names are, in the profile's order, those with a value beneath them",
                () -> assertThat(namedByTheJar).isNotEmpty().containsExactlyElementsOf(sayingTheyHaveOne));
    }

    private static MockEnvironment environmentOf(List<Layer> layers) {
        MockEnvironment environment = new MockEnvironment();
        for (Layer layer : layers) {
            environment.getPropertySources().addLast(new MapPropertySource(layer.name(), layer.values()));
        }
        return environment;
    }
}
