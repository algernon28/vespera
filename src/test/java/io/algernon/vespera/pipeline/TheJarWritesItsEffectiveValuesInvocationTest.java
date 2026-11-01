package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The file the control panel reads for what the jar would use where the profile names nothing (ADR-330 §1), reached
 * through real invocations: {@code vespera run} writes it before its first step, of a closed list of keys holding no
 * path, no credential and nothing of a document, and an invocation refused leaves it as it was.
 *
 * <p>Each of the two addresses is set here with what the file must leave out, so that a jar writing the property as it
 * stands fails. The working directory is shared by the class's methods, as every invocation test's is, so each begins
 * from a profile of its own.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("ADR-330: awaits the writer of effective-values.properties")
class TheJarWritesItsEffectiveValuesInvocationTest {

    /** The file's name, beside the database file. */
    private static final String FILE_NAME = "effective-values.properties";

    /** What must not reach the file: a user and a password an address may carry, and a path and a query after it. */
    private static final List<String> LEFT_OUT = List.of("a-user", "a-password", "/under/a/path", "a-query");

    /** The model service's address as the operator may have set it. */
    private static final String OLLAMA_ADDRESS = "http://a-user:a-password@localhost:11434/under/a/path?a-query=1";

    /** The converter's address, likewise. */
    private static final String DOCLING_ADDRESS = "http://a-user:a-password@localhost:5001/under/a/path?a-query=1";

    /** The fourteen presets of the converter, of redundancy and of relevance (ADR-330 §9), in the record's order. */
    private static final List<String> THE_TECHNICAL_PRESETS = List.of(
            "vespera.docling.ocr-preset", "vespera.docling.timeouts-in-a-row", "vespera.redundancy.shingle-words",
            "vespera.redundancy.minhash.permutations", "vespera.redundancy.minhash.bands",
            "vespera.redundancy.minhash.rows-per-band", "vespera.redundancy.minhash.seed",
            "vespera.redundancy.near-duplicate-jaccard", "vespera.redundancy.containment-index",
            "vespera.redundancy.rare-shingle-sample", "vespera.redundancy.rare-shingle-hits",
            "vespera.relevance.top-similarities", "vespera.relevance.sample.bands", "vespera.relevance.sample.per-band");

    /** The keys ADR-330 §1 lets into the file, each value's own and its source's, and no other. */
    private static final Set<String> THE_CLOSED_LIST = Stream.concat(Stream.of(
            "written-at", "docling.images",
            "embeddingModel", "embeddingModel.source",
            "generationModel", "generationModel.source",
            "generationContextWindow", "generationContextWindow.source",
            "deliverableLanguage", "deliverableLanguage.source",
            "clusterResolution", "clusterResolution.source",
            "vespera.docling.image", "vespera.docling.image.source",
            "vespera.docling.base-url", "vespera.docling.base-url.source",
            "spring.ai.ollama.base-url", "spring.ai.ollama.base-url.source",
            "vespera.sidecars.start", "vespera.sidecars.start.source",
            "vespera.sidecars.wait-at-most", "vespera.sidecars.wait-at-most.source",
            "vespera.progress-interval", "vespera.progress-interval.source",
            "vespera.open-browser", "vespera.open-browser.source",
            "vespera.clustering.neighbours", "vespera.clustering.neighbours.source"),
                    THE_TECHNICAL_PRESETS.stream().flatMap(key -> Stream.of(key, key + ".source")))
            .collect(Collectors.toUnmodifiableSet());

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("spring.ai.ollama.base-url", () -> OLLAMA_ADDRESS);
        registry.add("vespera.docling.base-url", () -> DOCLING_ADDRESS);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @BeforeEach
    void aCorpusAndNothingAnswered() throws IOException {
        Files.writeString(root.resolve("corpus-0.txt"), "a corpus document");
        profileStore.save(ProfileFixture.profile().build());
        Files.deleteIfExists(workingDirectory.resolve(FILE_NAME));
    }

    @Test
    @Story("The jar says what it would use")
    @DisplayName("An invocation writes what applies where the profile names nothing, each value with where it came from, and only the keys on the closed list")
    void anInvocationWritesTheValuesBeneathTheProfile() throws IOException {
        cli.run("run", root.toString());
        Properties said = theFile();

        claim("the generation model the jar ships, and that it ships it",
                () -> assertThat(List.of(said.getProperty("generationModel"), said.getProperty("generationModel.source")))
                        .containsExactly("qwen3:8b", "shipped"));
        claim("the window the code carries, and that the code carries it",
                () -> assertThat(List.of(said.getProperty("generationContextWindow"),
                                said.getProperty("generationContextWindow.source")))
                        .containsExactly("8192", "code"));
        claim("the embedding model is left out, because the test's own configuration blanks it, so nothing names one",
                () -> assertThat(said).doesNotContainKey("embeddingModel"));
        claim("each value of the application's own file is there, as the shipped file holds it",
                () -> assertThat(List.of(said.getProperty("vespera.open-browser"),
                                said.getProperty("vespera.open-browser.source"),
                                said.getProperty("vespera.clustering.neighbours"),
                                said.getProperty("clusterResolution")))
                        .containsExactly("true", "shipped", "15", "1.0"));
        claim("each of the " + THE_TECHNICAL_PRESETS.size() + " presets of the converter, of redundancy and of relevance"
                        + " is there, and each came from the shipped file",
                () -> assertThat(THE_TECHNICAL_PRESETS.stream()
                                .map(key -> List.of(key, String.valueOf(said.getProperty(key) != null),
                                        String.valueOf(said.getProperty(key + ".source"))))
                                .toList())
                        .containsExactlyElementsOf(THE_TECHNICAL_PRESETS.stream()
                                .map(key -> List.of(key, "true", "shipped"))
                                .toList()));
        claim("and every key the file holds is on the closed list",
                () -> assertThat(said.stringPropertyNames()).isSubsetOf(THE_CLOSED_LIST));
    }

    @Test
    @Story("The jar says what it would use")
    @DisplayName("A value the profile names is not what the file says: the file says what lies beneath it, so that clearing the value can be shown")
    void aValueTheProfileNamesStaysOutOfTheFile() throws IOException {
        profileStore.save(ProfileFixture.profile().generationModel("a-named-writing-model:8b", "set by this test").build());

        cli.run("run", root.toString());

        claim("the generation model in the file is the one the jar ships, not the one the profile names",
                () -> assertThat(theFile().getProperty("generationModel")).isEqualTo("qwen3:8b"));
    }

    @Test
    @Story("The jar says what it would use")
    @DisplayName("No credential and no path enters the file: an address is written as its scheme, host and port alone")
    void noCredentialOrPathEntersTheFile() throws IOException {
        cli.run("run", root.toString());
        String text = theFilesText();
        Properties said = theFile();

        claim("nothing a user or an address's tail might hold is in the file",
                () -> assertThat(LEFT_OUT).noneMatch(text::contains));
        claim("the working folder's path is not in it either",
                () -> assertThat(text).doesNotContain(workingDirectory.getFileName().toString()));
        claim("and each address is its scheme, host and port",
                () -> assertThat(List.of(said.getProperty("spring.ai.ollama.base-url"),
                                said.getProperty("vespera.docling.base-url")))
                        .containsExactly("http://localhost:11434", "http://localhost:5001"));
    }

    @Test
    @Story("The jar says what it would use")
    @DisplayName("An invocation refused leaves the file as the last one wrote it")
    void aRefusedInvocationLeavesTheFileAsItWas() throws IOException {
        cli.run("run", root.toString());
        String written = theFilesText();

        cli.run("run");

        claim("the second invocation was refused, having no root to walk",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim("and the file is as the first left it",
                () -> assertThat(theFilesText()).isEqualTo(written));
    }

    private static Properties theFile() throws IOException {
        theFileIsThere();
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(workingDirectory.resolve(FILE_NAME), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static String theFilesText() throws IOException {
        theFileIsThere();
        return Files.readString(workingDirectory.resolve(FILE_NAME), StandardCharsets.UTF_8);
    }

    private static void theFileIsThere() {
        claim("the invocation wrote the file, beside the database",
                () -> assertThat(workingDirectory.resolve(FILE_NAME)).exists());
    }
}
