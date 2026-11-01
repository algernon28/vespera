package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumingThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.embedding.LabelQuestion;
import io.algernon.vespera.embedding.RelevanceLabeller;
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
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The file the control panel reads for what the jar would use where the profile names nothing (ADR-330 §1), reached
 * through real invocations: {@code vespera run} and {@code vespera label}, with or without {@code --auto}, write it
 * before their first step, of a closed list of keys holding no path, no credential and nothing of a document. That an
 * invocation refused at its start leaves it as it was is {@code ARefusedInvocationWritesNoEffectiveValuesInvocationTest}'s,
 * that {@code vespera publish} writes none is {@code VesperaPublishWritesNoEffectiveValuesInvocationTest}'s, and where each value came from is {@code
 * TheJarSaysWhereEachValueCameFromInvocationTest}'s.
 *
 * <p>A claim of the source {@code shipped} fails where the shell names the value, so the image's, which runs on this
 * machine set by {@code VESPERA_DOCLING_IMAGE}, is made only where that variable is not set.
 *
 * <p>Each of the two addresses is set here with what the file must leave out, so that a jar writing the property as it
 * stands fails. The working directory is shared by the class's methods, as every invocation test's is, so each begins
 * from a profile of its own.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, AutoLabelling.class, TheJarWritesItsEffectiveValuesInvocationTest.Beans.class})
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("awaits the writer of the values file")
class TheJarWritesItsEffectiveValuesInvocationTest {

    /** The file's name, beside the database file. */
    private static final String FILE_NAME = "effective-values.properties";

    /** What must not reach the file: a user and a password an address may carry, and a path and a query after it. */
    private static final List<String> LEFT_OUT = List.of("a-user", "a-password", "/under/a/path", "a-query");

    /** The model service's address as the operator may have set it. */
    private static final String OLLAMA_ADDRESS = "http://a-user:a-password@localhost:11434/under/a/path?a-query=1";

    /** The converter's address, likewise. */
    private static final String DOCLING_ADDRESS = "http://a-user:a-password@localhost:5001/under/a/path?a-query=1";

    /** The fourteen presets of the converter, of redundancy and of relevance (ADR-330 §9), as the application resolves each. */
    private static final Map<String, String> THE_TECHNICAL_PRESETS = Map.ofEntries(
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

    /** The keys ADR-330 §1 lets into the file, each value's own and its source's, and no other. */
    private static final Set<String> THE_CLOSED_LIST = Stream.concat(Stream.of(
            "written-at",
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
                    THE_TECHNICAL_PRESETS.keySet().stream().flatMap(key -> Stream.of(key, key + ".source")))
            .collect(Collectors.toUnmodifiableSet());

    /** Above every measured document frequency here, so stage 4's gate is open. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model the scripted embedder answers for. */
    private static final String EMBEDDING_MODEL = "qwen3-embedding:0.6b";

    /** Answers every question, so that {@code vespera label --auto} has a local model to ask here. */
    @TestConfiguration
    static class Beans {
        @Bean
        RelevanceLabeller scriptedLabeller() {
            return new RelevanceLabeller() {
                @Override
                public String identity() {
                    return "scripted-labeller";
                }

                @Override
                public Optional<String> refusal() {
                    return Optional.empty();
                }

                @Override
                public Optional<Boolean> answer(LabelQuestion question) {
                    return Optional.of(true);
                }
            };
        }
    }

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @TempDir
    static Path seeds;

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
        claim("the embedding model is left out with its source, because the test's own configuration blanks it, so"
                        + " the file never says an empty value",
                () -> assertThat(said).doesNotContainKeys("embeddingModel", "embeddingModel.source"));
        claim("a value of the application's own file is there, as the shipped file holds it",
                () -> assertThat(List.of(said.getProperty("vespera.open-browser"),
                                said.getProperty("vespera.open-browser.source")))
                        .containsExactly("true", "shipped"));
        assumingThat(System.getenv("VESPERA_DOCLING_IMAGE") == null,
                () -> claim("the image the launch page checks against, as the shipped file holds it",
                        () -> assertThat(List.of(said.getProperty("vespera.docling.image"),
                                        said.getProperty("vespera.docling.image.source")))
                                .containsExactly(theShippedImage(), "shipped")));
        claim("whether the sidecars are started, as the shipped file holds it",
                () -> assertThat(List.of(said.getProperty("vespera.sidecars.start"),
                                said.getProperty("vespera.sidecars.start.source")))
                        .containsExactly("true", "shipped"));
        claim("the language the code falls back to, and that the code carries it",
                () -> assertThat(List.of(said.getProperty("deliverableLanguage"),
                                said.getProperty("deliverableLanguage.source")))
                        .containsExactly("en", "code"));
        claim("and every key the file holds is on the closed list",
                () -> assertThat(said.stringPropertyNames()).isSubsetOf(THE_CLOSED_LIST));
    }

    @Test
    @Story("The jar says what it would use")
    @DisplayName("The file says when it was written, with no source")
    void theMomentIsSaid() throws IOException {
        cli.run("run", root.toString());
        Properties said = theFile();

        claim("the moment it was written, with an offset, so it is one moment wherever it is read",
                () -> assertThat(OffsetDateTime.parse(said.getProperty("written-at"))).isNotNull());
        claim("and no source: it is no value of configuration",
                () -> assertThat(said).doesNotContainKeys("written-at.source"));
    }

    @Test
    @Disabled("awaits the presets in the shipped application file")
    @Story("The jar says what it would use")
    @DisplayName("The presets the application ships are in the file at the values the shipped file holds")
    void thePresetsAreWrittenAsTheShippedFileHoldsThem() throws IOException {
        cli.run("run", root.toString());
        Properties said = theFile();

        claim("the neighbours and the resolution, at what ships, the resolution under the profile's own name",
                () -> assertThat(List.of(said.getProperty("vespera.clustering.neighbours"),
                                said.getProperty("clusterResolution")))
                        .containsExactly("15", "1.0"));
        Map<String, String> values = new HashMap<>();
        Map<String, String> sources = new HashMap<>();
        THE_TECHNICAL_PRESETS.keySet().forEach(key -> {
            values.put(key, said.getProperty(key));
            sources.put(key, said.getProperty(key + ".source"));
        });
        claim("each of the " + THE_TECHNICAL_PRESETS.size() + " presets of the converter, of redundancy and of relevance"
                        + " is at the value that ships",
                () -> assertThat(values).containsExactlyInAnyOrderEntriesOf(THE_TECHNICAL_PRESETS));
        claim("and each came from the shipped file",
                () -> assertThat(sources.values()).containsOnly("shipped"));
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
    @DisplayName("Labelling the answers in the file writes it too, with no run before it having to")
    void aLabelWritesTheFile(@TempDir Path aCorpusOfItsOwn) throws IOException {
        aScoredCorpusIn(aCorpusOfItsOwn);
        Path labelFile = workingDirectory.resolve("relevance-labels.yaml");
        Files.writeString(labelFile, Files.readString(labelFile, StandardCharsets.UTF_8)
                .replace("relevant: null", "relevant: true"), StandardCharsets.UTF_8);
        Files.deleteIfExists(workingDirectory.resolve(FILE_NAME));

        cli.run("label");

        claim("the label recorded its answers",
                () -> assertThat(cli.getExitCode()).isZero());
        Properties said = theFile();
        claim("and wrote the file, with what applies",
                () -> assertThat(said.getProperty("generationModel")).isEqualTo("qwen3:8b"));
    }

    @Test
    @Story("The jar says what it would use")
    @DisplayName("Labelling by the local model writes it too")
    void aLabelByTheLocalModelWritesTheFile(@TempDir Path aCorpusOfItsOwn) throws IOException {
        aScoredCorpusIn(aCorpusOfItsOwn);
        Files.deleteIfExists(workingDirectory.resolve(FILE_NAME));

        cli.run("label", "--auto");

        claim("the local model's labelling recorded its answers",
                () -> assertThat(cli.getExitCode()).isZero());
        Properties said = theFile();
        claim("and wrote the file, with what applies",
                () -> assertThat(said.getProperty("generationModel")).isEqualTo("qwen3:8b"));
    }

    @Test
    @Story("The jar says what it would use")
    @DisplayName("A label that started and found no answers to record has still written it, having said what it would use before it looked")
    void aLabelThatFindsNoAnswersHasWrittenTheFile(@TempDir Path aCorpusOfItsOwn) throws IOException {
        aScoredCorpusIn(aCorpusOfItsOwn);
        Files.deleteIfExists(workingDirectory.resolve(FILE_NAME));

        cli.run("label");

        claim("the label recorded nothing, the file having no answers in it",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim("and the file is there",
                () -> assertThat(workingDirectory.resolve(FILE_NAME)).exists());
    }

    /**
     * Every gate open and the job run over a corpus root of its own, so the label file asks about this one. A file of a
     * name of its own too, because an answer is kept by the path below the root and the seed set, so one recorded by
     * another test here would show in this file.
     */
    private void aScoredCorpusIn(Path corpus) throws IOException {
        Files.writeString(corpus.resolve(corpus.getFileName() + ".txt"), "another corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL, "set by this test, so the embedding-model gate is open")
                .build());
        cli.run("run", corpus.toString());
    }

    /** Read from the shipped file, so a change of the image fails {@code DoclingSidecarImageTest} and not this. */
    private static String theShippedImage() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        return yaml.getObject().getProperty("vespera.docling.image");
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
