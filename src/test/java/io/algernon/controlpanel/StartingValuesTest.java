package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.StartingValues.Source;
import io.algernon.controlpanel.StartingValues.Sources;
import io.algernon.controlpanel.StartingValues.Starting;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Where each field of the window starts, and what of it reaches the child and the launch values file (ADR-344 §3): the
 * first of the launch values file, the environment, {@code .env}, for the Docling image the GPU image, and the jar's
 * {@code application.yaml} that names it; a value passed only where the child would read another without it; and a
 * value remembered only where it came from the launch values file or the operator changed it.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
class StartingValuesTest {

    /** A launch value's property, read from every source but the GPU image. */
    private static final String ADDRESS = "vespera.docling.base-url";

    /** Its variable, as Spring's relaxed binding names it. */
    private static final String ADDRESS_VARIABLE = "VESPERA_DOCLING_BASE_URL";

    /** The Docling image's property, the one the GPU image is preselected for. */
    private static final String IMAGE = "vespera.docling.image";

    /** The CPU image, which the shipped application.yaml and compose.yaml name. */
    private static final String CPU_IMAGE = "vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2";

    /** The jar's own configuration, as the window reads it from its class path. */
    private static final Path SHIPPED_APPLICATION_YAML = Path.of("src", "main", "resources", "application.yaml");

    /** The GPU compose file, whose converter image the window preselects. */
    private static final Path COMPOSE_GPU = Path.of("compose.gpu.yaml");

    @ParameterizedTest(name = "{0}")
    @MethodSource("sourcesInTurn")
    @Story("The window starts from the machine's own configuration")
    @DisplayName("A field starts from the first source that names it: the launch values, the environment, .env, then application.yaml")
    void eachValueStartsFromTheFirstSourceThatNamesIt(Sources sources, Starting expected) {
        claim("the field starts from the first of the launch values file, the environment, .env and application.yaml"
                        + " that names it, and says which",
                () -> assertThat(StartingValues.of(ADDRESS, sources)).isEqualTo(expected));
    }

    static Stream<Arguments> sourcesInTurn() {
        Map<String, String> launch = Map.of(ADDRESS, "http://launched:1");
        Map<String, String> environment = Map.of(ADDRESS_VARIABLE, "http://environment:2");
        Map<String, String> dotEnv = Map.of(ADDRESS_VARIABLE, "http://dot-env:3");
        Map<String, String> yaml = Map.of(ADDRESS, "http://localhost:5001");
        return Stream.of(
                Arguments.of(Named.of("every source names it", new Sources(launch, environment, dotEnv, yaml)),
                        new Starting("http://launched:1", Source.LAUNCH_VALUE)),
                Arguments.of(Named.of("no launch value", new Sources(Map.of(), environment, dotEnv, yaml)),
                        new Starting("http://environment:2", Source.ENVIRONMENT)),
                Arguments.of(Named.of(".env and application.yaml", new Sources(Map.of(), Map.of(), dotEnv, yaml)),
                        new Starting("http://dot-env:3", Source.DOT_ENV)),
                Arguments.of(Named.of("application.yaml alone", new Sources(Map.of(), Map.of(), Map.of(), yaml)),
                        new Starting("http://localhost:5001", Source.APPLICATION_YAML)),
                Arguments.of(Named.of("a blank launch value and a blank variable",
                                new Sources(Map.of(ADDRESS, "  "), Map.of(ADDRESS_VARIABLE, ""), Map.of(), yaml)),
                        new Starting("http://localhost:5001", Source.APPLICATION_YAML)),
                Arguments.of(Named.of("no source", new Sources(Map.of(), Map.of(), Map.of(), Map.of())),
                        new Starting("", Source.NONE)));
    }

    @Test
    @Story("The window starts from the machine's own configuration")
    @DisplayName("The converter's image starts as the GPU image where nothing above application.yaml names one")
    void theDoclingImageIsTheGpuOneBeforeApplicationYaml() throws IOException {
        String gpu = theImageOf(COMPOSE_GPU);
        Map<String, String> yaml = Map.of(IMAGE, CPU_IMAGE);

        claim("with application.yaml alone naming the CPU image, the field starts as the GPU image compose.gpu.yaml"
                        + " runs, said to be the preselection",
                () -> assertThat(StartingValues.of(IMAGE, new Sources(Map.of(), Map.of(), Map.of(), yaml)))
                        .isEqualTo(new Starting(gpu, Source.GPU_PRESELECTION)));
        claim("and an image .env names comes before it",
                () -> assertThat(StartingValues.of(IMAGE,
                                new Sources(Map.of(), Map.of(), Map.of("VESPERA_DOCLING_IMAGE", CPU_IMAGE), yaml)))
                        .isEqualTo(new Starting(CPU_IMAGE, Source.DOT_ENV)));
    }

    @Test
    @Story("The window starts from the machine's own configuration")
    @DisplayName("A property's variable is its name in capitals with each dot and hyphen an underscore")
    void theVariableIsThePropertysRelaxedName() {
        claim("the converter's address is read from VESPERA_DOCLING_BASE_URL and Ollama's from"
                        + " SPRING_AI_OLLAMA_BASE_URL, as Spring's relaxed binding names them",
                () -> assertThat(Stream.of(ADDRESS, "spring.ai.ollama.base-url").map(StartingValues::variableOf))
                        .containsExactly(ADDRESS_VARIABLE, "SPRING_AI_OLLAMA_BASE_URL"));
    }

    @Test
    @Story("The window starts from the machine's own configuration")
    @DisplayName(".env is read as name=value lines, a comment, a blank line and a line without = skipped, quotes around a value taken off")
    void dotEnvIsReadAsNameEqualsValueLines() {
        String text = """
                # where the corpus lies
                VESPERA_CORPUS_ROOT=D:/corpus

                VESPERA_DOCLING_IMAGE = "vespera/docling-serve-cu128"
                VESPERA_LABEL_MODEL='qwen3:8b'
                not a line of a variable
                VESPERA_WORKING_DIR=E:/work=dir
                """;

        claim("each variable is read with its value, the white space around both and matching quotes taken off, a value"
                        + " holding = kept whole, and the comment, the blank line and the line with no = left out",
                () -> assertThat(StartingValues.readDotEnv(text)).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "VESPERA_CORPUS_ROOT", "D:/corpus",
                        "VESPERA_DOCLING_IMAGE", "vespera/docling-serve-cu128",
                        "VESPERA_LABEL_MODEL", "qwen3:8b",
                        "VESPERA_WORKING_DIR", "E:/work=dir")));
    }

    @Test
    @Story("The window starts from the machine's own configuration")
    @DisplayName("application.yaml is read by dotted names, and a value holding a placeholder is not taken")
    void applicationYamlIsReadByDottedNamesAndAPlaceholderIsNotTaken() throws IOException {
        Map<String, String> shipped = StartingValues.readApplicationYaml(
                Files.readString(SHIPPED_APPLICATION_YAML, StandardCharsets.UTF_8));

        claim("the shipped file's values are read by their dotted names, the converter's address and image and whether"
                        + " the sidecars are started among them",
                () -> assertThat(shipped).containsAllEntriesOf(Map.of(
                        ADDRESS, "http://localhost:5001",
                        IMAGE, CPU_IMAGE,
                        "vespera.sidecars.start", "true")));
        claim("and the working directory, whose value is a placeholder Spring resolves, is not taken",
                () -> assertThat(shipped).doesNotContainKey("vespera.working-dir"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("valuesAndWhatTheChildReads")
    @Story("The window passes and keeps only what the operator chose")
    @DisplayName("A value is passed to the child where it is set and either kept or not what the child would read without it")
    void aValueIsPassedWhereItIsKeptOrTheChildWouldReadAnother(String value, Starting starting, Sources sources,
            boolean passed) {
        claim("a value is passed where it is not blank and is either kept, a launch value or one the operator changed,"
                        + " or differs from the environment's, or else application.yaml's, which the child reads with"
                        + " nothing passed",
                () -> assertThat(StartingValues.passed(ADDRESS, value, starting, sources)).isEqualTo(passed));
    }

    static Stream<Arguments> valuesAndWhatTheChildReads() {
        String shipped = "http://localhost:5001";
        Map<String, String> yaml = Map.of(ADDRESS, shipped);
        Sources yamlAlone = new Sources(Map.of(), Map.of(), Map.of(), yaml);
        Sources withTheEnvironment = new Sources(Map.of(), Map.of(ADDRESS_VARIABLE, "http://environment:2"), Map.of(), yaml);
        Sources withDotEnv = new Sources(Map.of(), Map.of(), Map.of(ADDRESS_VARIABLE, "http://dot-env:3"), yaml);
        Starting fromYaml = new Starting(shipped, Source.APPLICATION_YAML);
        return Stream.of(
                Arguments.of(Named.of("application.yaml's own value, left as it started", shipped), fromYaml,
                        yamlAlone, false),
                Arguments.of(Named.of("the environment's own value, left as it started", "http://environment:2"),
                        new Starting("http://environment:2", Source.ENVIRONMENT), withTheEnvironment, false),
                Arguments.of(Named.of(".env's value, which the child does not read", "http://dot-env:3"),
                        new Starting("http://dot-env:3", Source.DOT_ENV), withDotEnv, true),
                Arguments.of(Named.of("a value the operator typed", "http://elsewhere:9"), fromYaml, yamlAlone, true),
                Arguments.of(Named.of("a launch value equal to application.yaml's", shipped),
                        new Starting(shipped, Source.LAUNCH_VALUE), yamlAlone, true),
                Arguments.of(Named.of("a launch value the operator changed back to application.yaml's", shipped),
                        new Starting("http://launched:1", Source.LAUNCH_VALUE), yamlAlone, true),
                Arguments.of(Named.of("an emptied field", "  "), fromYaml, yamlAlone, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("valuesAndWhereTheyStarted")
    @Story("The window passes and keeps only what the operator chose")
    @DisplayName("A value is remembered only where it came from the launch values or the operator changed it")
    void aValueIsRememberedOnlyWhereTheOperatorChoseIt(String value, Starting starting, boolean remembered) {
        claim("a value left as the environment, .env, the GPU preselection or application.yaml started it is not"
                        + " stored, so no shipped default is frozen into the launch values; one the operator changed,"
                        + " or the launch values held, is",
                () -> assertThat(StartingValues.remembered(value, starting)).isEqualTo(remembered));
    }

    static Stream<Arguments> valuesAndWhereTheyStarted() {
        return Stream.of(
                Arguments.of(Named.of("left as application.yaml started it", "http://localhost:5001"),
                        new Starting("http://localhost:5001", Source.APPLICATION_YAML), false),
                Arguments.of(Named.of("left as the environment started it", "http://environment:2"),
                        new Starting("http://environment:2", Source.ENVIRONMENT), false),
                Arguments.of(Named.of("left as .env started it", "http://dot-env:3"),
                        new Starting("http://dot-env:3", Source.DOT_ENV), false),
                Arguments.of(Named.of("the GPU preselection left alone", "the GPU image"),
                        new Starting("the GPU image", Source.GPU_PRESELECTION), false),
                Arguments.of(Named.of("changed from the environment's", "http://elsewhere:9"),
                        new Starting("http://environment:2", Source.ENVIRONMENT), true),
                Arguments.of(Named.of("left as the launch values held it", "http://launched:1"),
                        new Starting("http://launched:1", Source.LAUNCH_VALUE), true),
                Arguments.of(Named.of("emptied", ""), new Starting("http://launched:1", Source.LAUNCH_VALUE), false));
    }

    /** The image the compose file's converter runs, its line {@code image: &docling-image '<image>'}. */
    private static String theImageOf(Path compose) throws IOException {
        return Files.readAllLines(compose, StandardCharsets.UTF_8).stream()
                .map(String::strip)
                .filter(line -> line.startsWith("image: &docling-image"))
                .map(line -> line.substring(line.indexOf('\'') + 1, line.lastIndexOf('\'')))
                .findFirst()
                .orElseThrow(() -> new AssertionError(compose + " names no converter image"));
    }
}
