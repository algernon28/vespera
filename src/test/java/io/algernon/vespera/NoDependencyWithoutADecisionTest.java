package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The four pom entries no recorded decision required are gone, with the compiler configuration that
 * only ran annotation processors nothing used (ADR-216 section 2, amending ADR-046).
 *
 * <p>ADR-046 has the pom carry what a recorded decision requires. The actuator starter and its test
 * starter, the batch test starter and Lombok were read against every record and against the tree at
 * {@code 3fc6f5d}: no record names them, no class uses them, and no property configures them. The
 * compiler plugin's only configuration was two annotation-processor paths, Lombok's and Spring Boot's
 * configuration processor, and the tree holds no Lombok annotation and no configuration-properties
 * class for either to process.
 *
 * <p>Read from the pom and the classpath, as {@link ChromaIsRemovedTest} reads them. Written before the
 * change it pins: against the tree it was written on, every test here fails, naming what is still there.
 */
@Epic("Architecture")
@Feature("Dependency policy")
@Issue("352")
@Link(name = "ADR-216", url = Adr.NOTHING_SHIPS_THAT_NO_DECISION_REQUIRES_AND_NOTHING_CALLS, type = "adr")
class NoDependencyWithoutADecisionTest {

    /** The pom at the repository root, which is where Maven runs the tests from. */
    private static final Path POM = Path.of("pom.xml");

    /**
     * The four entries, each by the artifact id the pom declared it under: the actuator starter, its test
     * starter, the batch test starter and Lombok.
     */
    private static final List<String> ENTRIES_NO_DECISION_REQUIRES = List.of(
            "spring-boot-starter-actuator",
            "spring-boot-starter-actuator-test",
            "spring-boot-starter-batch-test",
            "lombok");

    /** The compiler plugin, whose only configuration was the processor paths below. */
    private static final String COMPILER_PLUGIN = "maven-compiler-plugin";

    /** Where an annotation processor is named to the compiler. */
    private static final String PROCESSOR_PATHS = "annotationProcessorPaths";

    /** Spring Boot's configuration processor, the second of the two processors, which had nothing to read. */
    private static final String CONFIGURATION_PROCESSOR = "spring-boot-configuration-processor";

    /**
     * Classes from the jars those entries brought, which nothing else brings: the actuator's endpoint
     * annotation, the actuator's auto-configuration, the metrics auto-configuration the actuator starter
     * pulled in, the observation test registry its test starter pulled in, the batch test utilities, and a
     * Lombok annotation.
     */
    private static final List<String> CLASSES_THOSE_ENTRIES_BROUGHT = List.of(
            "org.springframework.boot.actuate.endpoint.annotation.Endpoint",
            "org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration",
            "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
            "io.micrometer.observation.tck.TestObservationRegistry",
            "org.springframework.batch.test.JobOperatorTestUtils",
            "lombok.Getter");

    @Test
    @Story("The build carries only what a decision requires")
    @DisplayName("The build declares neither the actuator nor its test starter, the batch test starter, or Lombok")
    void thePomDeclaresNoneOfTheFourEntries() throws IOException {
        String pom = Files.readString(POM);

        claim(
                "none of the " + ENTRIES_NO_DECISION_REQUIRES.size() + " dependencies nothing in the code uses and no"
                        + " decision asks for is declared any more: the actuator starter, its test starter, the batch"
                        + " test starter and Lombok",
                () -> assertThat(ENTRIES_NO_DECISION_REQUIRES)
                        .as("entries the pom still declares")
                        .noneMatch(artifact -> pom.contains("<artifactId>" + artifact + "</artifactId>")));
    }

    @Test
    @Story("The build carries only what a decision requires")
    @DisplayName("The build configures no annotation processor, there being nothing for one to process")
    void thePomConfiguresNoAnnotationProcessor() throws IOException {
        String pom = Files.readString(POM);

        claim(
                "the build names no annotation processor to the compiler: no class carries an annotation either"
                        + " processor it named would act on",
                () -> assertThat(pom).doesNotContain("<" + PROCESSOR_PATHS + ">"));
        claim(
                "and Spring Boot's configuration processor is named nowhere in it, since no class declares"
                        + " configuration properties for it to describe",
                () -> assertThat(pom).doesNotContain(CONFIGURATION_PROCESSOR));
        claim(
                "so the compiler plugin, whose only configuration was those processors, is not declared either and"
                        + " compiles with the defaults the parent gives it",
                () -> assertThat(pom).doesNotContain("<artifactId>" + COMPILER_PLUGIN + "</artifactId>"));
    }

    @Test
    @Story("The build carries only what a decision requires")
    @DisplayName("No class those entries brought is on the classpath")
    void noClassThoseEntriesBroughtIsOnTheClasspath() {
        claim(
                "none of the " + CLASSES_THOSE_ENTRIES_BROUGHT.size() + " classes taken from the jars the four entries"
                        + " brought is on the classpath the tests run on, which contains the runtime classpath the"
                        + " application runs on, so no other dependency still brings them",
                () -> assertThat(CLASSES_THOSE_ENTRIES_BROUGHT)
                        .as("classes those entries brought that are still on the classpath")
                        .noneMatch(NoDependencyWithoutADecisionTest::isOnTheClasspath));
    }

    /** Whether {@code className} can be found, without loading or initialising it. */
    private static boolean isOnTheClasspath(String className) {
        return NoDependencyWithoutADecisionTest.class.getClassLoader().getResource(className.replace('.', '/') + ".class")
                != null;
    }
}
