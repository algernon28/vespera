package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the jar carries of Vespera's configuration (ADR-397 §2): nothing Spring's configuration loading reads, and a
 * shipped copy of each file it keeps in the folder beside the jar, under {@link TheShippedCopy#FOLDER}, byte for byte
 * the repository's source of it; and the repository's root, the current directory of every test, holds no file Spring
 * would read there.
 *
 * <p>It reads the compiled classes of {@code src/main}, the folder the application class was loaded from, which is
 * what the build packs into the jar, and not the test classes: the test profile's own files lie there and are no part
 * of the jar. A checkout built before ADR-397 keeps the old files in those classes until it is built clean once.
 */
@Epic("Architecture")
@Feature("Shipped configuration")
@Issue("836")
@Link(name = "ADR-397", url = Adr.THE_YAML_CONFIGURATION_LIES_BESIDE_THE_JAR, type = "adr")
class TheJarCarriesNoConfigurationSpringReadsTest {

    /** The names Spring's configuration loading reads with no profile active, at each place it reads. */
    private static final List<String> READ_BY_SPRING = List.of("application.yaml", "application.yml", "application.properties");

    /** The two places of the class path Spring's configuration loading reads: its root, and {@code config/} below it. */
    private static final List<String> PLACES_SPRING_READS = List.of("", "config/");

    /** The folder the jar carried the compose files in before they moved beside it. */
    private static final String THE_OLD_FOLDER = "containers";

    /** The file beside the jar that has no shipped copy, being the operator's alone. */
    private static final String THE_LOCAL_FILE = "application-local.yaml";

    @Test
    @Story("The configuration lies beside the jar")
    @DisplayName("The jar carries no configuration file Spring would read: none at the root of its classes, none under config/, and no local settings file anywhere")
    void theJarCarriesNoFileSpringsConfigurationLoadingReads() throws URISyntaxException, IOException {
        Path classes = theJarsClasses();
        List<String> found = namesSpringReadsUnder(classes);
        List<String> localFiles;
        try (Stream<Path> paths = Files.walk(classes)) {
            localFiles = paths.filter(path -> path.getFileName().toString().equals(THE_LOCAL_FILE))
                    .map(path -> classes.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }

        claim("the folder read is the jar's compiled classes, checked by the application class it must hold",
                () -> assertThat(classes.resolve("io/algernon/vespera/VesperaApplication.class")).isRegularFile());
        claim("none of the " + READ_BY_SPRING.size() + " names Spring reads with no profile lies at its root or"
                        + " under config/, so the configuration Spring reads is never the jar's own",
                () -> assertThat(found).as("configuration files Spring would read from the jar").isEmpty());
        claim("and no " + THE_LOCAL_FILE + " lies anywhere in the jar's classes, at any depth, the local settings"
                        + " being the operator's alone",
                () -> assertThat(localFiles).as("local settings files carried in the jar's classes").isEmpty());
    }

    @Test
    @Story("The configuration lies beside the jar")
    @DisplayName("The repository's root holds no configuration file Spring would read, being the current directory of every test")
    void theRepositorysRootHoldsNoFileSpringReads() {
        List<String> found = namesSpringReadsUnder(TheShippedCopy.REPOSITORY);

        claim("the repository's root, where Spring reads by default and above application-test.yaml in every test"
                        + " context, holds none of the names Spring reads there or under config/; application.yaml's"
                        + " source lies below src/main",
                () -> {
                    assertThat(found).as("configuration files Spring would read in the repository's root").isEmpty();
                    assertThat(TheShippedCopy.APPLICATION_YAML_SOURCE).isRegularFile();
                });
    }

    @Test
    @Story("The configuration lies beside the jar")
    @DisplayName("The jar carries a copy of each file it keeps beside itself, byte for byte the repository's, and none of the local file")
    void theJarCarriesTheRepositorysFilesAsItsShippedCopies() throws IOException, URISyntaxException {
        Path shipped = theJarsClasses().resolve(TheShippedCopy.FOLDER);
        List<String> expected = TheShippedCopy.namesInTheRepository();
        List<String> differing = new ArrayList<>();
        for (String name : expected) {
            Path copy = shipped.resolve(name);
            Path source = TheShippedCopy.sourceOf(name);
            if (!Files.isRegularFile(copy) || !Files.isRegularFile(source) || Files.mismatch(copy, source) != -1) {
                differing.add(name);
            }
        }

        claim("the repository holds the build context the compose files name, beside them at its root",
                () -> assertThat(expected).anyMatch(name -> name.startsWith(TheShippedCopy.BUILD_CONTEXT + "/")));
        claim("each of the three YAML files and each file of the build context is carried under "
                        + TheShippedCopy.FOLDER + ", the same bytes as the repository's source of it",
                () -> assertThat(differing).as("files missing at either place, or differing").isEmpty());
        claim("the jar carries nothing under " + TheShippedCopy.FOLDER + " but those, the operator's local file"
                        + " among what it does not carry",
                () -> assertThat(TheShippedCopy.filesUnder(shipped)).containsExactlyInAnyOrderElementsOf(expected)
                        .doesNotContain(THE_LOCAL_FILE));
        claim("and not the folder it carried the compose files in before, from which it wrote them to a temporary"
                        + " folder at each start",
                () -> assertThat(theJarsClasses().resolve(THE_OLD_FOLDER)).doesNotExist());
    }

    private static List<String> namesSpringReadsUnder(Path folder) {
        List<String> found = new ArrayList<>();
        for (String place : PLACES_SPRING_READS) {
            for (String name : READ_BY_SPRING) {
                if (Files.exists(folder.resolve(place + name))) {
                    found.add(place + name);
                }
            }
        }
        return found;
    }

    /** The compiled classes of {@code src/main}: the folder the application class was loaded from. */
    private static Path theJarsClasses() throws URISyntaxException {
        return Path.of(VesperaApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
