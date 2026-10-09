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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * Chroma is gone from the build, from the classpath and from everything under {@code src/main}, and
 * nothing was left behind that exists only for it (ADR-214, superseding ADR-142 and amending ADR-039).
 *
 * <p>Chroma was a disposable projection of the vectors SQLite holds, populated by nothing and read by
 * nothing. Scoring and clustering read SQLite (ADR-085), and generation sends whole documents to the
 * model server (ADR-110, ADR-166). ADR-142 kept the store configured and deferred its connection so
 * that a later reader would find it; ADR-214 withdraws that requirement with the dependency, since
 * keeping a dependency for a reader nobody has decided to build is what ADR-046 rules out. A reader
 * that wants a vector database is a decision of its own, and brings its dependency with it.
 *
 * <p>Read from the files and the classpath, with no Docker. {@link ChromaIsNotASidecarTest} holds
 * {@code compose.yaml}, and {@link TheApplicationStartsWithNoVectorStoreTest} the application context.
 *
 * <p>Written before the change it pins: against the tree it was written on, every test here fails, each
 * naming what is still there.
 */
@Epic("Architecture")
@Feature("Dependency policy")
@Issue("352")
@Link(name = "ADR-214", url = Adr.CHROMA_IS_REMOVED_AND_VECTORS_LIVE_IN_SQLITE_ALONE, type = "adr")
class ChromaIsRemovedTest {

    /** The pom at the repository root, which is where Maven runs the tests from. */
    private static final Path POM = Path.of("pom.xml");

    /** The production tree: the sources, the shipped configuration and every other resource the jar packs. */
    private static final Path SHIPPED_TREE = Path.of("src", "main");

    /**
     * The files besides the production tree that existed partly for Chroma: the pom, both compose files
     * and the workflow that runs the integration tests. Each named it in a declaration or in a comment.
     */
    private static final List<Path> BUILD_FILES = List.of(
            POM,
            Path.of("compose.yaml"),
            Path.of("compose.gpu.yaml"),
            Path.of(".github", "workflows", "build.yml"));

    /** The word searched for, compared without regard to case so that a comment is found as well as a key. */
    private static final String CHROMA = "chroma";

    /** Spring AI's Chroma starter: the client, its auto-configuration and the vector-store abstraction it brings. */
    private static final String CHROMA_STARTER = "spring-ai-starter-vector-store-chroma";

    /** Testcontainers' Chroma module, which only the test container for Chroma used. */
    private static final String CHROMA_TEST_CONTAINER = "testcontainers-chromadb";

    /**
     * The classes the two artifacts above put on the classpath, one from each jar they brought: Spring AI's
     * vector-store interface, its Chroma store, the auto-configuration that built a store bean at start-up,
     * the auto-configuration that observed it, and the Chroma test container.
     */
    private static final List<String> CLASSES_THAT_CAME_WITH_CHROMA = List.of(
            "org.springframework.ai.vectorstore.VectorStore",
            "org.springframework.ai.chroma.vectorstore.ChromaVectorStore",
            "org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreAutoConfiguration",
            "org.springframework.ai.vectorstore.observation.autoconfigure.VectorStoreObservationAutoConfiguration",
            "org.testcontainers.chromadb.ChromaDBContainer");

    /** How a Spring AI vector-store type is written inside a compiled class, in a reference or in a signature. */
    private static final String VECTOR_STORE_TYPES = "org/springframework/ai/vectorstore/";

    /** The configuration class that deferred the store's connection, which goes with the store. */
    private static final String THE_CLASS_THAT_DEFERRED_THE_STORE = "io.algernon.vespera.pipeline.VectorStoreConfiguration";

    /** The file the application ships with, read as Spring reads it. */
    private static final String SHIPPED_CONFIGURATION = "application.yaml";

    /** Where every Spring AI vector-store setting lives, Chroma's included. */
    private static final String VECTOR_STORE_PROPERTIES = "spring.ai.vectorstore";

    @Test
    @Story("Nothing ships for a vector database nobody reads")
    @DisplayName("The build declares neither the Chroma client nor its test container")
    void thePomDeclaresNoChromaArtifact() throws IOException {
        String pom = Files.readString(POM);

        claim(
                "the build does not depend on Spring AI's Chroma starter, which brought the client, the vector"
                        + " store built from it and the auto-configuration that built it at start-up",
                () -> assertThat(pom).doesNotContain("<artifactId>" + CHROMA_STARTER + "</artifactId>"));
        claim(
                "and it does not depend on the Chroma test container either, whose one use was to start a Chroma"
                        + " beside the integration tests",
                () -> assertThat(pom).doesNotContain("<artifactId>" + CHROMA_TEST_CONTAINER + "</artifactId>"));
    }

    @Test
    @Story("Nothing ships for a vector database nobody reads")
    @DisplayName("No vector store, Chroma client or Chroma container class is on the classpath")
    void noClassThatCameWithChromaIsOnTheClasspath() {
        claim(
                "none of the " + CLASSES_THAT_CAME_WITH_CHROMA.size() + " classes the Chroma starter and the Chroma"
                        + " test container brought is on the classpath the tests run on, which contains the"
                        + " runtime classpath the application runs on, so no other dependency still brings them",
                () -> assertThat(CLASSES_THAT_CAME_WITH_CHROMA)
                        .as("classes that came with Chroma and are still on the classpath")
                        .noneMatch(ChromaIsRemovedTest::isOnTheClasspath));
    }

    @Test
    @Story("Nothing ships for a vector database nobody reads")
    @DisplayName("No shipped class names a vector store type, and the class that deferred the store is gone")
    void noShippedClassNamesAVectorStore() throws Exception {
        Map<String, List<String>> names = ShippedClasses.namesByClass();
        Set<String> naming = new TreeSet<>();
        for (Map.Entry<String, List<String>> shipped : names.entrySet()) {
            if (shipped.getValue().stream().anyMatch(ChromaIsRemovedTest::namesAVectorStoreOrChroma)) {
                naming.add(shipped.getKey());
            }
        }

        claim(
                "no shipped class refers to a Spring AI vector store type or to anything named for Chroma, in what"
                        + " it declares, calls or says",
                () -> assertThat(naming).as("shipped classes that still name one").isEmpty());
        claim(
                "and the configuration class that made the vector store wait for its first use is not among the"
                        + " shipped classes, since there is no store left to wait",
                () -> assertThat(names).doesNotContainKey(THE_CLASS_THAT_DEFERRED_THE_STORE));
    }

    @Test
    @Story("Nothing ships for a vector database nobody reads")
    @DisplayName("No file under the production tree mentions Chroma, in code, configuration or comment")
    void nothingInTheProductionTreeMentionsChroma() throws IOException {
        List<Path> files = filesUnder(SHIPPED_TREE);
        Set<String> mentioning = new TreeSet<>();
        for (Path file : files) {
            if (mentionsChroma(file)) {
                mentioning.add(file.toString().replace('\\', '/'));
            }
        }

        claim(
                "the production tree was read, the shipped configuration among it, so an empty answer below is"
                        + " not an empty walk",
                () -> assertThat(files).anyMatch(file -> file.endsWith(SHIPPED_CONFIGURATION)));
        claim(
                "no file under it mentions Chroma, so no setting, class or comment is left describing a vector"
                        + " database the application no longer has",
                () -> assertThat(mentioning).as("files that still mention it").isEmpty());
    }

    @Test
    @Story("Nothing ships for a vector database nobody reads")
    @DisplayName("The shipped configuration sets no vector store property")
    void theShippedConfigurationSetsNoVectorStoreProperty() throws IOException {
        Map<Object, Object> properties = shippedProperties();
        Set<String> vectorStoreKeys = new TreeSet<>();
        for (Object key : properties.keySet()) {
            if (String.valueOf(key).startsWith(VECTOR_STORE_PROPERTIES + ".")) {
                vectorStoreKeys.add(String.valueOf(key));
            }
        }

        claim(
                "the shipped configuration was read, so an empty answer below is not an empty file",
                () -> assertThat(properties).isNotEmpty());
        claim(
                "it sets nothing under " + VECTOR_STORE_PROPERTIES + ": with no vector store on the classpath such"
                        + " a key configures nothing, and it would tell a reader a store exists",
                () -> assertThat(vectorStoreKeys).as("vector store properties still set").isEmpty());
    }

    @Test
    @Story("Nothing ships for a vector database nobody reads")
    @DisplayName("The build files, both compose files and the build workflow, no longer mention Chroma")
    void theBuildFilesNoLongerMentionChroma() throws IOException {
        Set<String> mentioning = new TreeSet<>();
        for (Path file : BUILD_FILES) {
            if (mentionsChroma(file)) {
                mentioning.add(file.toString().replace('\\', '/'));
            }
        }

        claim(
                "none of the " + BUILD_FILES.size() + " files that declared Chroma, started it beside a test or"
                        + " explained either in a comment mentions it any more: the pom, compose.yaml,"
                        + " compose.gpu.yaml and the build workflow",
                () -> assertThat(mentioning).as("build files that still mention it").isEmpty());
    }

    /** Whether {@code className} can be found, without loading or initialising it. */
    private static boolean isOnTheClasspath(String className) {
        return ChromaIsRemovedTest.class.getClassLoader().getResource(className.replace('.', '/') + ".class") != null;
    }

    private static boolean namesAVectorStoreOrChroma(String name) {
        return name.contains(VECTOR_STORE_TYPES) || name.toLowerCase(Locale.ROOT).contains(CHROMA);
    }

    /**
     * Whether the file's bytes hold the word in any case. Read as ISO-8859-1, which maps every byte to one
     * character, so a resource that is not text is read without failing and a text file is read as the
     * ASCII it is written in.
     */
    private static boolean mentionsChroma(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1)
                .toLowerCase(Locale.ROOT)
                .contains(CHROMA);
    }

    private static List<Path> filesUnder(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).sorted().toList();
        }
    }

    /** The shipped file flattened to properties, exactly as Spring reads it. */
    private static Map<Object, Object> shippedProperties() throws IOException {
        ClassPathResource resource = new ClassPathResource(SHIPPED_CONFIGURATION);
        try (InputStream ignored = resource.getInputStream()) {
            YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(resource);
            yaml.afterPropertiesSet();
            return yaml.getObject();
        }
    }
}
