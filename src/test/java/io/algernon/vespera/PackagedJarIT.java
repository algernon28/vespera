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
 * The packaged jar carries no Spring compose support (ADR-158, ADR-179), it carries the compose files its own
 * starter brings the sidecars up from (ADR-323), and it carries the control panel, started at its entry point with no
 * argument (ADR-342).
 *
 * <p>Spring Boot's Docker Compose support reconciles running containers to the files it is given and stops them at
 * exit, which is what ADR-179 records going wrong; the jar's own starter recreates and stops nothing. That holds only
 * while Spring Boot's Docker Compose support stays out of the jar. Two settings of the
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

    /** The folder the build writes its jars in, which holds no second jar of the control panel's (ADR-342 §5). */
    private static final Path TARGET = Path.of("target");

    /** Where the Boot plugin puts the application's own classes. */
    private static final String CLASSES = "BOOT-INF/classes/";

    /** Where under the jar's classes the compose files the starter reads lie (ADR-323 §4). */
    private static final String SIDECARS = "sidecars/";

    /** The control panel's classes, as a jar names their entries. */
    private static final String CONTROL_PANEL = "io/algernon/vespera/ui/";

    /** A class of the control panel the jar is read for. */
    private static final String A_CLASS_OF_THE_CONTROL_PANEL = CONTROL_PANEL + "HolderLine.class";

    /** A class of the jar the Spring Boot jar must hold, so that an absence below is a real one. */
    private static final String A_CLASS_OF_THE_JAR = "io/algernon/vespera/VesperaApplication.class";

    /** The jar's entry point, in the application package beside its application class, as a jar names its entry (ADR-342 §2). */
    private static final String THE_ENTRY_POINT = "io/algernon/vespera/EntryPoint.class";

    /** The class the Boot launcher hands the start to, which opens the window or runs the command line (ADR-342 §2). */
    private static final String ENTRY_POINT_CLASS = "io.algernon.vespera.EntryPoint";

    /** The manifest entry Spring Boot's launcher reads the class it starts from. */
    private static final String START_CLASS = "Start-Class";

    /** The manifest entry that lets JavaFX load its native libraries with no warning (ADR-342 §2). */
    private static final String ENABLE_NATIVE_ACCESS = "Enable-Native-Access";

    /** The Spring Boot jar's own entry point, which a jar it repackages names as its {@code Main-Class}. */
    private static final String SPRING_BOOTS_JAR_LAUNCHER = "org.springframework.boot.loader.launch.JarLauncher";

    /**
     * The six OpenJFX jars of one platform's classifier, as the Boot plugin names them under {@code BOOT-INF/lib/}:
     * {@code javafx-controls}' three, and {@code javafx-web}, which brings {@code javafx-media} and
     * {@code jdk-jsobject}; the version ADR-342 §1 names, and the classifier of whichever platform built the jar.
     */
    private static final java.util.regex.Pattern JAVAFX_OF_ONE_PLATFORM = java.util.regex.Pattern.compile(
            "(javafx-(base|graphics|controls|media|web)|jdk-jsobject)-26\\.0\\.2-"
                    + "(win|linux|mac|mac-aarch64|linux-aarch64)\\.jar");

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
     * The control panel ships inside the jar (ADR-342 §1, §2): its classes, the jar's entry point, and OpenJFX's six
     * jars of the building platform's classifier, the web jar among them, which a report's viewer is (ADR-344 §6);
     * Spring Boot's launcher stays the {@code Main-Class} and hands the start to the entry point, and the manifest lets
     * JavaFX load its native libraries.
     */
    @Test
    @Issue("700")
    @Link(name = "ADR-342", url = Adr.THE_CONTROL_PANEL_IS_A_JAVAFX_WINDOW_INSIDE_THE_JAR, type = "adr")
    @Story("The control panel ships inside the jar")
    @DisplayName("The packaged jar carries the control panel, JavaFX of one platform with its web viewer, and starts at the entry point")
    void theJarCarriesTheControlPanelAndJavaFxAndStartsAtTheEntryPoint() throws IOException {
        List<String> entries = entriesOf(EXECUTABLE_JAR);
        List<String> javaFx = libraries().stream()
                .filter(name -> (name.startsWith("javafx-") || name.startsWith("jdk-jsobject-")) && name.contains("-26.0.2-"))
                .sorted().toList();
        Attributes manifest = mainAttributesOf(EXECUTABLE_JAR);

        claim("the packaged jar carries the application's classes, the control panel's and the entry point that"
                        + " chooses between them",
                () -> assertThat(entries).contains(CLASSES + A_CLASS_OF_THE_JAR, CLASSES + A_CLASS_OF_THE_CONTROL_PANEL,
                        CLASSES + THE_ENTRY_POINT));
        String classifier = theBuildPlatformsClassifier();
        claim("it carries OpenJFX 26.0.2's base, graphics, controls, media and web, and the JavaScript object classes web"
                        + " needs, each once, of the classifier of the platform it was built on, here " + classifier,
                () -> assertThat(javaFx).allMatch(name -> JAVAFX_OF_ONE_PLATFORM.matcher(name).matches())
                        .containsExactly("javafx-base-26.0.2-" + classifier + ".jar",
                                "javafx-controls-26.0.2-" + classifier + ".jar",
                                "javafx-graphics-26.0.2-" + classifier + ".jar",
                                "javafx-media-26.0.2-" + classifier + ".jar",
                                "javafx-web-26.0.2-" + classifier + ".jar",
                                "jdk-jsobject-26.0.2-" + classifier + ".jar"));
        claim("its main class is still Spring Boot's launcher, which starts the entry point",
                () -> {
                    assertThat(manifest.getValue(Attributes.Name.MAIN_CLASS)).isEqualTo(SPRING_BOOTS_JAR_LAUNCHER);
                    assertThat(manifest.getValue(START_CLASS)).isEqualTo(ENTRY_POINT_CLASS);
                });
        claim("and it lets code on the class path load native libraries, so JavaFX's start writes no warning of it",
                () -> assertThat(manifest.getValue(ENABLE_NATIVE_ACCESS)).isEqualTo("ALL-UNNAMED"));
    }

    /** The build writes one jar: the second jar the control panel was built as is gone (ADR-342 §5). */
    @Test
    @Issue("700")
    @Link(name = "ADR-342", url = Adr.THE_CONTROL_PANEL_IS_A_JAVAFX_WINDOW_INSIDE_THE_JAR, type = "adr")
    @Story("The control panel ships inside the jar")
    @DisplayName("No jar of the control panel's own is built beside the packaged jar")
    void noControlPanelJarIsBuiltBesideIt() throws IOException {
        claim("the packaged jar exists, since this test reads the folder it is built in and needs verify to have built it",
                () -> assertThat(Files.exists(EXECUTABLE_JAR)).isTrue());
        try (java.util.stream.Stream<Path> built = Files.list(TARGET)) {
            List<String> jars = built.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".jar"))
                    .toList();
            claim("and no jar beside it is the control panel's, by the classifier it was built with",
                    () -> assertThat(jars).noneMatch(name -> name.endsWith("-control-panel.jar")));
        }
    }

    /**
     * The OpenJFX classifier of the platform this test runs on, which built the jar: {@code win}, {@code linux},
     * {@code linux-aarch64}, {@code mac} or {@code mac-aarch64}, by {@code os.name} and {@code os.arch} as OpenJFX's
     * own build profiles pick it.
     */
    private static String theBuildPlatformsClassifier() {
        String system = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        boolean arm = System.getProperty("os.arch").equals("aarch64");
        if (system.startsWith("windows")) {
            return "win";
        }
        if (system.startsWith("mac")) {
            return arm ? "mac-aarch64" : "mac";
        }
        return arm ? "linux-aarch64" : "linux";
    }

    /** The main section of the jar's manifest. */
    private static Attributes mainAttributesOf(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.getManifest().getMainAttributes();
        }
    }

    @Test
    @Story("The jar carries the files it starts the sidecars from")
    @DisplayName("The packaged jar carries the two compose files and the converter's build folder, byte for byte as the repository holds them")
    @Link(name = "ADR-323", url = Adr.VESPERA_STARTS_DOCKER_AND_THE_SIDECARS_AND_STAGE_2_WAITS_FOR_A_STARTING_CONVERTER, type = "adr")
    void carriesTheComposeFilesTheStarterReads() throws IOException {
        java.util.Map<String, byte[]> repository = new java.util.TreeMap<>();
        for (String name : List.of("compose.yaml", "compose.gpu.yaml")) {
            repository.put(name, Files.readAllBytes(Path.of(name)));
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(Path.of("docker"))) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                repository.put(file.toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
        java.util.Map<String, byte[]> carried = new java.util.TreeMap<>();
        try (JarFile jar = new JarFile(EXECUTABLE_JAR.toFile())) {
            jar.stream()
                    .filter(entry -> !entry.isDirectory() && entry.getName().startsWith(CLASSES + SIDECARS))
                    .forEach(entry -> {
                        try (java.io.InputStream bytes = jar.getInputStream(entry)) {
                            carried.put(entry.getName().substring((CLASSES + SIDECARS).length()), bytes.readAllBytes());
                        } catch (IOException unreadable) {
                            throw new java.io.UncheckedIOException(unreadable);
                        }
                    });
        }

        claim("the jar carries, under " + CLASSES + SIDECARS + ", exactly the files of compose.yaml, compose.gpu.yaml"
                        + " and docker/ by the same relative paths",
                () -> assertThat(carried.keySet()).containsExactlyElementsOf(repository.keySet()));
        claim("and each with the repository's bytes, so no resource filter rewrote one",
                () -> assertThat(carried).allSatisfy((name, bytes) -> assertThat(bytes)
                        .as(name)
                        .isEqualTo(repository.get(name))));
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
