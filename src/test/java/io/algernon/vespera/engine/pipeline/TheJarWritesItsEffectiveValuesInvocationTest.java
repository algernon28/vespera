package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.embedding.LabelQuestion;
import io.algernon.vespera.engine.embedding.RelevanceLabeller;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
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
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The file the control panel reads for what the jar would use where the profile names nothing (ADR-330 §1), reached
 * through real invocations: {@code vespera run} and {@code vespera label}, with or without {@code --auto}, write it
 * before their first step, of a closed list of keys holding no path, no credential and nothing of a document.
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
class TheJarWritesItsEffectiveValuesInvocationTest {

    /** The file's name, beside the database file. */
    private static final String FILE_NAME = "effective-values.properties";

    /** What must not reach the file: a user and a password an address may carry, and a path and a query after it. */
    private static final List<String> LEFT_OUT = List.of("a-user", "a-password", "/under/a/path", "a-query");

    /** The model service's address as the operator may have set it. */
    private static final String OLLAMA_ADDRESS = "http://a-user:a-password@localhost:11434/under/a/path?a-query=1";

    /** The converter's address, likewise. */
    private static final String DOCLING_ADDRESS = "http://a-user:a-password@localhost:5001/under/a/path?a-query=1";

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
                .boilerplateDocumentFrequencyFloor(EveryGateOpen.BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EveryGateOpen.EMBEDDING_MODEL, "set by this test, so the embedding-model gate is open")
                .build());
        cli.run("run", corpus.toString());
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
