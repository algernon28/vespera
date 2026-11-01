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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Where {@code effective-values.properties} says each value came from (ADR-330 §1), for the three layers that can name
 * a value in place of the jar's own file. Each layer here is the one Spring builds in a started jar: the property source
 * of the command line, under the name and of the type {@code SpringApplication} gives it, since a slice is given no
 * arguments; the system properties, which are the {@code environment} row beside a variable no test can set in its own
 * process; and another file of application configuration, read as {@code application-local.yaml} is.
 *
 * <p>One invocation, made once for the class, and each test reads one layer. Each first says the layer did name the
 * value, so that a red test means the file and not a setup that named nothing. A system property, being the JVM's, is
 * set for this class's context alone and taken back after it.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@ContextConfiguration(initializers = TheJarSaysWhereEachValueCameFromInvocationTest.TheCommandLine.class)
@TestPropertySource(properties = "spring.config.additional-location=classpath:/effective-values-local-file/")
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("ADR-330: awaits the writer of effective-values.properties")
class TheJarSaysWhereEachValueCameFromInvocationTest {

    /** The value the command line names, against the shipped true. */
    private static final String ON_THE_COMMAND_LINE = "vespera.open-browser";

    private static final String THE_COMMAND_LINES_VALUE = "false";

    /** The value a system property names, against the shipped 180s. */
    private static final String IN_THE_ENVIRONMENT = "vespera.sidecars.wait-at-most";

    private static final String THE_ENVIRONMENTS_VALUE = "7s";

    /** The value {@code effective-values-local-file/application.yaml} names, against the shipped 5s. */
    private static final String IN_ANOTHER_FILE = "vespera.progress-interval";

    private static final String THE_OTHER_FILES_VALUE = "9s";

    /** Adds the property source of the command line, as {@code SpringApplication} adds it to a started jar. */
    static final class TheCommandLine implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            context.getEnvironment().getPropertySources().addFirst(new SimpleCommandLinePropertySource(
                    "--" + ON_THE_COMMAND_LINE + "=" + THE_COMMAND_LINES_VALUE));
        }
    }

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        System.setProperty(IN_THE_ENVIRONMENT, THE_ENVIRONMENTS_VALUE);
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @AfterAll
    static void takeTheSystemPropertyBack() {
        System.clearProperty(IN_THE_ENVIRONMENT);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private Environment environment;

    /** Made once for the class: a method's own {@code @BeforeEach} is the first place that has both the beans and the folders. */
    private static boolean invoked;

    @BeforeEach
    void oneInvocation() throws IOException {
        if (!invoked) {
            invoked = true;
            Files.writeString(root.resolve("corpus-0.txt"), "a corpus document");
            profileStore.save(ProfileFixture.profile().build());
            cli.run("run", root.toString());
        }
    }

    private Properties theFile() throws IOException {
        claim("the invocation wrote the file, beside the database",
                () -> assertThat(workingDirectory.resolve("effective-values.properties")).exists());
        Properties said = new Properties();
        try (Reader reader = Files.newBufferedReader(workingDirectory.resolve("effective-values.properties"),
                StandardCharsets.UTF_8)) {
            said.load(reader);
        }
        return said;
    }

    private void thisLayerNamesIt(String key, String value) {
        claim("the configuration names " + value + " for " + key + ", the precondition of this test",
                () -> assertThat(environment.getProperty(key)).isEqualTo(value));
    }

    private static List<String> valueAndSource(Properties said, String key) {
        return List.of(String.valueOf(said.getProperty(key)), String.valueOf(said.getProperty(key + ".source")));
    }

    @Test
    @Story("The jar says what it would use")
    @DisplayName("A value named on the command line says it came from the command line")
    void aValueOnTheCommandLineSaysSo() throws IOException {
        thisLayerNamesIt(ON_THE_COMMAND_LINE, THE_COMMAND_LINES_VALUE);
        Properties file = theFile();

        claim("the value, and that the command line named it",
                () -> assertThat(valueAndSource(file, ON_THE_COMMAND_LINE))
                        .containsExactly(THE_COMMAND_LINES_VALUE, "command-line"));
    }

    @Test
    @Story("The jar says what it would use")
    @DisplayName("A value named by a system property says it came from the environment")
    void aValueInTheEnvironmentSaysSo() throws IOException {
        thisLayerNamesIt(IN_THE_ENVIRONMENT, THE_ENVIRONMENTS_VALUE);
        Properties file = theFile();

        claim("the value, and that the environment named it",
                () -> assertThat(valueAndSource(file, IN_THE_ENVIRONMENT))
                        .containsExactly(THE_ENVIRONMENTS_VALUE, "environment"));
    }

    @Test
    @Story("The jar says what it would use")
    @DisplayName("A value named by another file of application configuration says it came from such a file")
    void aValueInAnotherFileSaysSo() throws IOException {
        thisLayerNamesIt(IN_ANOTHER_FILE, THE_OTHER_FILES_VALUE);
        Properties file = theFile();

        claim("the value, and that a local file named it",
                () -> assertThat(valueAndSource(file, IN_ANOTHER_FILE))
                        .containsExactly(THE_OTHER_FILES_VALUE, "local-file"));
    }
}
