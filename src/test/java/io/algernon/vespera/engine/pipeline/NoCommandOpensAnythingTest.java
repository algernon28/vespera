package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
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
import tools.jackson.databind.JsonNode;

/**
 * No command of the jar opens a browser, a file or a window, and the report says so (ADR-362 section 2): the one start
 * of a program the jar still makes that looks like opening is Docker Desktop's, for the sidecars (ADR-323 section 5),
 * and it is no file opened.
 *
 * <p>What a class opens is read off its compiled form, as {@code ShippedClasses} reads what a class names: the
 * detector looks at constants loaded and types referred to, never at the text of a source. The control keeps it
 * honest, run over {@link OpenersUnderTestFixtures}.
 */
@CascadeSliceTest
@Import({
    StubbedExtractionBeans.class,
    NoCommandOpensAnythingTest.AStarterOnADesktopWhereTheEngineIsDown.class,
    NoCommandOpensAnythingTest.AnAnswerForTheDisplay.class
})
@Epic("Pipeline")
@Feature("Nothing is opened")
@Issue("760")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class NoCommandOpensAnythingTest {

    /** The class that starts Docker Desktop, and is the one class the detector exempts by name. */
    private static final String HOST_SYSTEM = "io.algernon.vespera.engine.pipeline.HostSystem";

    private static final Set<String> NAMING_A_PROGRAM = Set.of("rundll32", "xdg-open", "cmd");

    private static final String OPEN = "open";

    /** The control panel's package and the entry point, which are no part of the jar's own (ADR-285). */
    private static final String THE_CONTROL_PANEL = "io/algernon/vespera/ui/";

    private static final String THE_ENTRY_POINT = "io/algernon/vespera/EntryPoint";

    private static final Set<String> THE_FOUR_CONSTANTS = Set.of("rundll32", "xdg-open", "cmd", OPEN);

    private static final Set<String> TYPES_THAT_OPEN = Set.of("java/awt/Desktop", "javafx/application/HostServices");

    private static final Set<String> TYPES_THAT_START_A_PROCESS = Set.of(
            "java/lang/ProcessBuilder",
            "io/algernon/vespera/engine/pipeline/ProcessRunner",
            "io/algernon/vespera/engine/pipeline/SystemProcessRunner",
            "io/algernon/vespera/engine/pipeline/SidecarStarter");

    private static final List<String> DESKTOP_START = List.of("docker", "desktop", "start", "--detach");

    private static final List<String> OPEN_DOCKER_APP = List.of("open", "-a", "Docker");

    /** Engine checks the engine fails before it answers, so Vespera starts it. */
    private static final int ENGINE_CHECKS_FAILED = 2;

    private static final ScriptedSidecarHealth HEALTH = ScriptedSidecarHealth.neitherAnswering();

    private static volatile ScriptedDocker docker = ScriptedDocker.engineRunning();

    private static final AtomicReference<DisplayPresence.Answer> DISPLAY = new AtomicReference<>();

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path lockFolder;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @BeforeEach
    void aProfileThatLoadsAndNothingAnswering() {
        profileStore.save(ProfileFixture.profile().build());
        HEALTH.answering(false, false);
        docker = ScriptedDocker.engineRunning();
        DISPLAY.set(new DisplayPresence.Answer(true, "display-variable", null));
    }

    @AfterEach
    void noHeadlessLeftOver() {
        System.clearProperty(DisplayPresence.HEADLESS_PROPERTY);
    }

    /** The display the application is given, answering as the test says. */
    @TestConfiguration
    static class AnAnswerForTheDisplay {

        @Bean
        InvocationReport.Display scriptedDisplay() {
            return DISPLAY::get;
        }
    }

    /** The production starter on a Mac whose Docker engine is down, run through whatever {@link #docker} is. */
    @TestConfiguration
    static class AStarterOnADesktopWhereTheEngineIsDown {

        @Bean
        SidecarStarter sidecarStarter() {
            Path repository = Path.of("").toAbsolutePath();
            SidecarStartSettings settings = new SidecarStartSettings(
                    true,
                    URI.create("http://localhost:5001"),
                    URI.create("http://localhost:11434"),
                    ComposeFiles.in(repository).cpuImage(),
                    Duration.ofSeconds(1));
            ProcessRunner theCurrentScript = new ProcessRunner() {
                @Override
                public ProcessOutcome run(List<String> command, Duration timeout)
                        throws IOException, CommandOutputFileException {
                    return docker.run(command, timeout);
                }

                @Override
                public ProcessOutcome runToTheEnd(List<String> command, Consumer<String> eachLine) throws IOException {
                    return docker.runToTheEnd(command, eachLine);
                }
            };
            return new SidecarStarter(settings, theCurrentScript, HEALTH, HostSystem.MAC,
                    () -> ComposeFiles.in(repository), lockFolder, Duration.ofMillis(20));
        }
    }

    @Test
    @Story("No class of the jar names a way to open a file")
    @DisplayName("No class of the jar but the one that starts Docker Desktop names a way to open a file, and the control finds exactly the three fixtures that do")
    void noClassOfTheJarNamesAWayToOpenAFile() throws Exception {
        List<ClassModel> shipped = shippedClasses();
        List<String> offenders = new ArrayList<>();
        for (ClassModel compiled : shipped) {
            String name = compiled.thisClass().asInternalName().replace('/', '.');
            if (!name.equals(HOST_SYSTEM) && namesAWayToOpenAFile(compiled)) {
                offenders.add(name);
            }
        }

        List<String> found = new ArrayList<>();
        for (Class<?> fixture : OpenersUnderTestFixtures.class.getDeclaredClasses()) {
            if (namesAWayToOpenAFile(compiledForm(fixture))) {
                found.add(fixture.getSimpleName());
            }
        }

        claim("the scan read the jar's compiled classes, more than the one it is held on, so an empty scan is not an"
                        + " answer of no offender",
                () -> assertThat(shipped).hasSizeGreaterThan(1).anyMatch(compiled ->
                        compiled.thisClass().asInternalName().equals(HOST_SYSTEM.replace('.', '/'))));
        claim("and no class of the jar but HostSystem references java.awt.Desktop or HostServices, loads rundll32,"
                        + " xdg-open or cmd, or loads open and starts a process",
                () -> assertThat(offenders).isEmpty());
        claim("the control run over fixtures reports the one that starts a process on open, the one that runs open"
                        + " through the runner and the one that loads rundll32, and neither the one that holds open as a"
                        + " key nor the one that starts a process on none of the four",
                () -> assertThat(found).containsExactlyInAnyOrder(
                        "StartsAProcessOnOpen", "RunsOpenThroughTheRunner", "LoadsRundll32"));
        claim("the constants of HostSystem, which starts Docker Desktop, are open and cmd and no other of the four",
                () -> assertThat(constantsOfTheFour(shipped.stream()
                                .filter(compiled -> compiled.thisClass().asInternalName().equals(HOST_SYSTEM.replace('.', '/')))
                                .findFirst()
                                .orElseThrow()))
                        .containsExactlyInAnyOrder(OPEN, "cmd"));
    }

    private static boolean namesAWayToOpenAFile(ClassModel compiled) {
        Set<String> types = typesOf(compiled);
        Set<String> constants = constantsOfTheFour(compiled);
        if (types.stream().anyMatch(TYPES_THAT_OPEN::contains)
                || constants.stream().anyMatch(NAMING_A_PROGRAM::contains)) {
            return true;
        }
        return constants.contains(OPEN)
                && (types.stream().anyMatch(TYPES_THAT_START_A_PROCESS::contains) || callsRuntimeExec(compiled));
    }

    private static Set<String> typesOf(ClassModel compiled) {
        Set<String> types = new TreeSet<>();
        for (PoolEntry entry : compiled.constantPool()) {
            if (entry instanceof ClassEntry type) {
                types.add(type.asInternalName());
            }
        }
        return types;
    }

    /** Which of the four constants the class loads, as an {@code ldc}. */
    private static Set<String> constantsOfTheFour(ClassModel compiled) {
        Set<String> loaded = new TreeSet<>();
        for (MethodModel method : compiled.methods()) {
            for (CodeElement element : method.code().map(CodeModel::elementList).orElse(List.of())) {
                if (element instanceof ConstantInstruction.LoadConstantInstruction load
                        && load.constantValue() instanceof String text
                        && THE_FOUR_CONSTANTS.contains(text)) {
                    loaded.add(text);
                }
            }
        }
        return loaded;
    }

    private static boolean callsRuntimeExec(ClassModel compiled) {
        for (PoolEntry entry : compiled.constantPool()) {
            if (entry instanceof MethodRefEntry call
                    && call.owner().asInternalName().equals("java/lang/Runtime")
                    && call.name().equalsString("exec")) {
                return true;
            }
            if (entry instanceof InterfaceMethodRefEntry call
                    && call.owner().asInternalName().equals("java/lang/Runtime")
                    && call.name().equalsString("exec")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every class of the jar compiled from {@code src/main}: the control panel's and the entry point's are left out,
     * as ADR-285 defines the jar's own. The window's hand-off of a document to the system (ADR-362 section 10) is the
     * control panel's, and no command of the jar.
     */
    private static List<ClassModel> shippedClasses() throws Exception {
        Path root = Path.of(HostSystem.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<ClassModel> models = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                ClassModel compiled = ClassFile.of().parse(Files.readAllBytes(file));
                String name = compiled.thisClass().asInternalName();
                if (!name.startsWith(THE_CONTROL_PANEL) && !name.startsWith(THE_ENTRY_POINT)) {
                    models.add(compiled);
                }
            }
        }
        return models;
    }

    private static ClassModel compiledForm(Class<?> type) throws IOException {
        try (InputStream in = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            return ClassFile.of().parse(in.readAllBytes());
        }
    }
}
