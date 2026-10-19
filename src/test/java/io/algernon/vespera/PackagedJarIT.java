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
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The packaged jar starts no sidecar, because it carries no compose support (ADR-158).
 *
 * <p>The operator starts the sidecars from {@code compose.yaml} and then runs this jar. That split
 * holds only while Spring Boot's Docker Compose support stays out of the jar. Two settings of the
 * Boot plugin's {@code repackage} goal keep it out, each on its own. {@code excludeDockerCompose},
 * {@code true} by default, strips {@code spring-boot-docker-compose} whatever its scope.
 * {@code includeOptional}, {@code false} by default, leaves out every {@code <optional>} dependency.
 * Since ADR-179 neither {@code spring-boot-docker-compose} nor {@code spring-ai-spring-boot-docker-compose}
 * is in {@code pom.xml} at all, so no entry point starts a sidecar; this test guards against either
 * coming back into the jar. A compose support in the jar looks for a {@code compose.yaml} wherever it
 * is launched from, and starts or stops containers on every command. That reverses the decision
 * without a word.
 *
 * <p>This test fails whenever either compose artifact is nested, which is stricter than that. Spring
 * AI's artifact holds only connection-details factories and starts nothing, so letting it in alone
 * leaves the jar starting nothing. The test fails on it anyway, so that any change to what the jar
 * carries here comes back to ADR-158.
 *
 * <p>It is an integration test only because the packaged jar exists only after {@code package}. It
 * needs no Docker daemon, and it launches nothing.
 */
@Epic("Architecture")
@Feature("Sidecars")
@Issue("304")
@Link(name = "ADR-158", url = Adr.THE_PACKAGED_JAR_STARTS_NO_SIDECAR, type = "adr")
@Link(name = "ADR-011", url = Adr.THE_TOOL_OWNS_ITS_SIDECARS, type = "adr")
@Link(name = "ADR-179", url = Adr.NO_ENTRY_POINT_STARTS_THE_SIDECARS, type = "adr")
class PackagedJarIT {

    /** The packaged, executable jar the operator runs, at the path README.md names. */
    private static final Path EXECUTABLE_JAR = Path.of("target", "vespera-0.0.1-SNAPSHOT.jar");

    /** The control panel's jar, which the same build writes beside it (ADR-285 §2). */
    private static final Path CONTROL_PANEL_JAR = Path.of("target", "vespera-0.0.1-SNAPSHOT-control-panel.jar");

    /** Where the Boot plugin puts the application's own classes. */
    private static final String CLASSES = "BOOT-INF/classes/";

    /** The control panel's classes, as a jar names their entries. */
    private static final String CONTROL_PANEL = "io/algernon/controlpanel/";

    /** A class of the control panel each jar is read for. */
    private static final String A_CLASS_OF_THE_CONTROL_PANEL = CONTROL_PANEL + "HolderLine.class";

    /** A class of the jar the Spring Boot jar must hold, so that an absence below is a real one. */
    private static final String A_CLASS_OF_THE_JAR = "io/algernon/vespera/VesperaApplication.class";

    /** What a plain jar carries beside its classes: its manifest and Maven's description of the build. */
    private static final String META_INF = "META-INF/";

    /** The class whose {@code main} the operator's {@code java -jar} on the control panel's jar runs (ADR-288 §1). */
    private static final String CONTROL_PANELS_MAIN_CLASS = "io.algernon.controlpanel.ControlPanel";

    /** The control panel's manifest entry naming the file name of the jar it starts (ADR-288 §1). */
    private static final String VESPERA_JAR = "Vespera-Jar";

    /** The Spring Boot jar's own entry point, which a jar it repackages names as its {@code Main-Class}. */
    private static final String SPRING_BOOTS_JAR_LAUNCHER = "org.springframework.boot.loader.launch.JarLauncher";

    /** Where the Boot plugin puts the jar's dependencies, one nested jar each. */
    private static final String LIBRARIES = "BOOT-INF/lib/";

    /** The two artifacts that bring {@code compose.yaml} up, as the Boot plugin names their jars. */
    private static final List<String> COMPOSE_SUPPORT =
            List.of("spring-boot-docker-compose-", "spring-ai-spring-boot-docker-compose-");

    @Test
    @Story("The operator starts the sidecars, and the jar starts none")
    @DisplayName("The packaged jar carries no Docker Compose support, so running it starts no container")
    void carriesNoComposeSupport() throws IOException {
        claim("the packaged jar exists, since this test reads it and needs verify to have built it",
                () -> assertThat(Files.exists(EXECUTABLE_JAR)).isTrue());

        List<String> libraries = libraries();

        claim("the jar carries its dependencies, so an absence below is a real one and not an empty jar",
                () -> assertThat(libraries).anyMatch(name -> name.startsWith("picocli-")));
        claim("and none of them is Spring Boot's or Spring AI's Docker Compose support, which is what"
                        + " would start compose.yaml from wherever the jar was launched",
                () -> assertThat(libraries)
                        .noneMatch(name -> COMPOSE_SUPPORT.stream().anyMatch(name::startsWith)));
    }

    /**
     * The control panel is built by the same pom, and the Spring Boot jar the operator runs carries none of it
     * (ADR-285 §2).
     */
    @Test
    @Issue("562")
    @Link(name = "ADR-285", url = Adr.THE_CONTROL_PANEL_IS_A_SECOND_JAR_LINKING_NO_CLASS_OF_THE_JAR, type = "adr")
    @Story("The control panel is built beside the jar, not into it")
    @DisplayName("The packaged jar carries the application's classes and none of the control panel's")
    void theSpringBootJarCarriesNoClassOfTheControlPanel() throws IOException {
        List<String> entries = entriesOf(EXECUTABLE_JAR);

        claim("the packaged jar carries the application's own classes, so an absence below is a real one",
                () -> assertThat(entries).contains(CLASSES + A_CLASS_OF_THE_JAR));
        claim("and no class of the control panel",
                () -> assertThat(entries).noneMatch(name -> name.startsWith(CLASSES + CONTROL_PANEL)));
    }

    /**
     * The control panel's jar holds its classes and nothing else of the build's: no class of the jar, no library and
     * no Spring (ADR-285 §2).
     */
    @Test
    @Issue("562")
    @Link(name = "ADR-285", url = Adr.THE_CONTROL_PANEL_IS_A_SECOND_JAR_LINKING_NO_CLASS_OF_THE_JAR, type = "adr")
    @Story("The control panel is built beside the jar, not into it")
    @DisplayName("The control panel's jar carries the control panel's classes and nothing else")
    void theControlPanelJarCarriesOnlyTheControlPanelsClasses() throws IOException {
        claim("the control panel's jar exists, since this test reads it and needs verify to have built it",
                () -> assertThat(Files.exists(CONTROL_PANEL_JAR)).isTrue());
        List<String> entries = entriesOf(CONTROL_PANEL_JAR);

        claim("it carries the control panel's classes",
                () -> assertThat(entries).contains(A_CLASS_OF_THE_CONTROL_PANEL));
        claim("and, beside its manifest and Maven's description of the build, nothing outside the control panel's"
                        + " package: no class of the application, no Spring and no nested library",
                () -> assertThat(entries)
                        .filteredOn(name -> !name.startsWith(META_INF))
                        .allMatch(name -> CONTROL_PANEL.startsWith(name) || name.startsWith(CONTROL_PANEL)));
    }

    /**
     * The operator starts the control panel by {@code java -jar} on its jar, whose manifest names its entry point and
     * the file name of the jar it starts beside it (ADR-288 §1); the Spring Boot jar's manifest is left as it was.
     */
    @Test
    @Issue("567")
    @Link(name = "ADR-288", url = Adr.THE_CONTROL_PANEL_STARTS_THE_JAR_BESIDE_ITS_OWN_WITH_THE_JAVA_RUNNING_IT, type = "adr")
    @Story("The operator starts the control panel with java -jar")
    @DisplayName("The control panel's jar names its main class and the file name of the packaged jar beside it")
    void theControlPanelJarsManifestNamesItsMainClassAndTheJarBesideIt() throws IOException {
        Attributes controlPanel = mainAttributesOf(CONTROL_PANEL_JAR);

        claim("the control panel's jar names " + CONTROL_PANELS_MAIN_CLASS + " as its main class, so java -jar starts it",
                () -> assertThat(controlPanel.getValue(Attributes.Name.MAIN_CLASS)).isEqualTo(CONTROL_PANELS_MAIN_CLASS));
        claim("and names, as " + VESPERA_JAR + ", the file name of the packaged jar the same build writes beside it",
                () -> assertThat(controlPanel.getValue(VESPERA_JAR)).isEqualTo(EXECUTABLE_JAR.getFileName().toString()));

        Attributes springBoot = mainAttributesOf(EXECUTABLE_JAR);

        claim("the packaged jar's main class is still Spring Boot's own, and it names no jar to start",
                () -> {
                    assertThat(springBoot.getValue(Attributes.Name.MAIN_CLASS)).isEqualTo(SPRING_BOOTS_JAR_LAUNCHER);
                    assertThat(springBoot.getValue(VESPERA_JAR)).isNull();
                });
    }

    /** The main section of the jar's manifest. */
    private static Attributes mainAttributesOf(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.getManifest().getMainAttributes();
        }
    }

    /** The name of every file and folder the jar holds. */
    private static List<String> entriesOf(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.stream().map(ZipEntry::getName).toList();
        }
    }

    /** The file name of every jar nested under {@code BOOT-INF/lib/}. */
    private static List<String> libraries() throws IOException {
        try (JarFile jar = new JarFile(EXECUTABLE_JAR.toFile())) {
            return jar.stream()
                    .map(ZipEntry::getName)
                    .filter(name -> name.startsWith(LIBRARIES) && name.endsWith(".jar"))
                    .map(name -> name.substring(LIBRARIES.length()))
                    .toList();
        }
    }
}
