package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where a value of the effective values came from, once Vespera's configuration lies beside the jar (ADR-397 §7,
 * amending ADR-330 §1): a value Spring reads from a file named {@code application.yaml} at the value the jar's shipped
 * copy holds is {@code shipped}; one the operator changed there, and one {@code application-local.yaml} names, is
 * {@code local-file}.
 *
 * <p>The folder beside the jar is a folder of the test's own, its {@code application.yaml} the shipped copy with one
 * value changed, and Spring is pointed at its two files by the command line, so that what is read is those files
 * alone: no system property and no environment variable of the machine running the test is in the environment.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("836")
@Link(name = "ADR-397", url = Adr.THE_YAML_CONFIGURATION_LIES_BESIDE_THE_JAR, type = "adr")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class TheEffectiveValuesSayWhereAValueBesideTheJarCameFromTest {

    /** A preset left in {@code application.yaml} at the value the jar ships. */
    private static final String LEFT_AS_SHIPPED = Presets.SHINGLE_WORDS;

    /** A preset the operator changed in {@code application.yaml}. */
    private static final String CHANGED_BY_THE_OPERATOR = Presets.NEIGHBOURS_OF_A_DOCUMENT;

    /** What the operator changed it to. */
    private static final String THE_OPERATORS_VALUE = "a-value-of-the-operators";

    /** A value {@code application-local.yaml} names over the one {@code application.yaml} ships. */
    private static final String IN_THE_LOCAL_FILE = "vespera.progress-interval";

    @TempDir
    Path besideTheJar;

    @TempDir
    Path workingDirectory;

    @Test
    @Story("The jar says what it would use")
    @DisplayName("A value of the configuration beside the jar is Vespera's own where it is as shipped, and the local settings' where it was changed or set in the local file")
    void aValueBesideTheJarIsShippedOnlyAtTheShippedValue() throws IOException {
        Files.writeString(besideTheJar.resolve("application.yaml"), TheOperatorsEdits.setting(
                TheOperatorsEdits.shipped("application.yaml"), CHANGED_BY_THE_OPERATOR, THE_OPERATORS_VALUE));
        Files.writeString(besideTheJar.resolve("application-local.yaml"),
                "vespera:\n  " + TheOperatorsEdits.lastPartOf(IN_THE_LOCAL_FILE) + ": 7s\n");
        String folder = besideTheJar.toUri().toString();

        Properties said = SpringsOwnReading.read(
                application -> { },
                new String[] {"--spring.config.location=optional:" + folder + "application.yaml,optional:" + folder
                        + "application-local.yaml"},
                Map.of(), Map.of(),
                environment -> {
                    new EffectiveValuesFile(environment, workingDirectory).write();
                    return theFile();
                });

        claim("the changed value is the one in use, so the file beside the jar was the one read",
                () -> assertThat(said.getProperty(CHANGED_BY_THE_OPERATOR)).isEqualTo(THE_OPERATORS_VALUE));
        claim("a value left as the jar ships it is said to be Vespera's own, the one changed and the one the local file"
                        + " sets each the operator's local settings",
                () -> assertThat(List.of(said.getProperty(LEFT_AS_SHIPPED + ".source", "(no source)"),
                                said.getProperty(CHANGED_BY_THE_OPERATOR + ".source", "(no source)"),
                                said.getProperty(IN_THE_LOCAL_FILE + ".source", "(no source)")))
                        .as("the sources of " + LEFT_AS_SHIPPED + ", " + CHANGED_BY_THE_OPERATOR + " and "
                                + IN_THE_LOCAL_FILE)
                        .containsExactly("shipped", "local-file", "local-file"));
    }

    @Test
    @Story("The jar says what it would use")
    @DisplayName("An application.yaml whose name is spelt in another case, named by the location, is still the file whose values are Vespera's own where they are as shipped")
    void anApplicationYamlSpeltInAnotherCaseIsShippedAtTheShippedValue() throws IOException {
        String spelt = "Application.yaml";
        Files.writeString(besideTheJar.resolve(spelt), TheOperatorsEdits.setting(
                TheOperatorsEdits.shipped("application.yaml"), CHANGED_BY_THE_OPERATOR, THE_OPERATORS_VALUE));
        String folder = besideTheJar.toUri().toString();

        Properties said = SpringsOwnReading.read(
                application -> { },
                new String[] {"--spring.config.location=" + folder + spelt},
                Map.of(), Map.of(),
                environment -> {
                    new EffectiveValuesFile(environment, workingDirectory).write();
                    return theFile();
                });

        claim("the changed value is the one in use, so " + spelt + " was the file read",
                () -> assertThat(said.getProperty(CHANGED_BY_THE_OPERATOR)).isEqualTo(THE_OPERATORS_VALUE));
        claim("a value left as the jar ships it is Vespera's own, the name being compared without regard to case,"
                        + " and the one changed the local settings'",
                () -> assertThat(List.of(said.getProperty(LEFT_AS_SHIPPED + ".source", "(no source)"),
                                said.getProperty(CHANGED_BY_THE_OPERATOR + ".source", "(no source)")))
                        .as("the sources of " + LEFT_AS_SHIPPED + " and " + CHANGED_BY_THE_OPERATOR + " read from "
                                + spelt)
                        .containsExactly("shipped", "local-file"));
    }

    private Properties theFile() {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(workingDirectory.resolve(EffectiveValuesFile.FILE_NAME),
                StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException unread) {
            throw new IllegalStateException("the effective values were not written", unread);
        }
        return properties;
    }
}
