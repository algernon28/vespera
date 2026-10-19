package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.pipeline.VesperaCommand;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import picocli.CommandLine.Command;

/**
 * Neither the jar nor the control panel sends the HTML tree or the Confluence rendering to Confluence, holds a
 * credential for it, or offers a command for a load (ADR-304).
 *
 * <p>An uploader cannot be written without naming where it sends: a REST path of Confluence's or the host of an
 * Atlassian Cloud site. It cannot be configured without a key for its credential, and it cannot be offered on the
 * command line without a third subcommand. Each of those is read here from the files, with no Docker and no
 * network. The whole production tree is read, so the control panel's classes, which lie outside the nine modules,
 * are read with the jar's. A Confluence rendering drawn as files names neither a REST path nor a host, so ADR-301's
 * renderer does not turn this red.
 *
 * <p>That the control panel names no type of the JDK's HTTP client is held by {@code
 * TheControlPanelLinksNoClassOfTheJarTest}, under ADR-285, and that it starts only {@code run} and {@code label} by
 * {@code ChildCommandTest}, under ADR-278.
 */
@Epic("Architecture")
@Feature("Nothing leaves the machine")
@Issue("608")
@Link(name = "ADR-304", url = Adr.VESPERA_NEVER_SENDS_THE_TREE_TO_CONFLUENCE, type = "adr")
class NothingShippedReachesConfluenceTest {

    /** The production tree: the jar's sources, the control panel's, and every resource either packs. */
    private static final Path SHIPPED_TREE = Path.of("src", "main");

    /** The pom at the repository root, which is where Maven runs the tests from. */
    private static final Path POM = Path.of("pom.xml");

    /** The file the application ships with, read as Spring reads it. */
    private static final String SHIPPED_CONFIGURATION = "application.yaml";

    /**
     * What a call to Confluence names, compared without regard to case: the REST API of a Data Center site and of
     * a Cloud site under {@code /wiki}, its second version on Cloud, and the domain every Cloud site lies under.
     */
    private static final List<String> WHERE_AN_UPLOADER_SENDS = List.of("/rest/api/", "/wiki/api/v2", "atlassian.net");

    /**
     * A key of the shipped configuration that would hold a credential: its last segment a token, a personal access
     * token, a password, a user name, an API key or a secret.
     */
    private static final Pattern A_CREDENTIALS_KEY = Pattern.compile(
            "(?i)(^|\\.)(token|personal-access-token|pat|password|username|api-key|apikey|secret)$");

    /** The Maven {@code groupId} Atlassian's client libraries are declared under. */
    private static final String ATLASSIANS_GROUP = "<groupId>com.atlassian";

    @Test
    @Story("No class sends the deliverable to Confluence")
    @DisplayName("No shipped file names a Confluence REST path or an Atlassian Cloud host")
    void noShippedFileNamesAConfluenceRestPathOrAtlassiansHost() throws IOException {
        List<Path> files = filesUnder(SHIPPED_TREE);
        Set<String> naming = new TreeSet<>();
        for (Path file : files) {
            String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
            if (WHERE_AN_UPLOADER_SENDS.stream().anyMatch(text::contains)) {
                naming.add(file.toString().replace('\\', '/'));
            }
        }

        claim(
                "the production tree was read, the shipped configuration and the control panel's classes among it, so"
                        + " an empty answer below is not an empty walk",
                () -> assertThat(files)
                        .anyMatch(file -> file.endsWith(SHIPPED_CONFIGURATION))
                        .anyMatch(file -> file.toString().replace('\\', '/').contains("io/algernon/controlpanel/")));
        claim(
                "no file under it names any of the " + WHERE_AN_UPLOADER_SENDS.size() + " things a call to Confluence"
                        + " names: its REST path, its second API's path on a Cloud site, or the Cloud sites' domain",
                () -> assertThat(naming).as("files naming one").isEmpty());
    }

    @Test
    @Story("No credential for Confluence is held")
    @DisplayName("The shipped configuration holds no key for a token, a password, a user name, an API key or a secret")
    void theShippedConfigurationHoldsNoCredential() throws IOException {
        Map<Object, Object> properties = shippedProperties();
        Set<String> credentialKeys = new TreeSet<>();
        for (Object key : properties.keySet()) {
            if (A_CREDENTIALS_KEY.matcher(String.valueOf(key)).find()) {
                credentialKeys.add(String.valueOf(key));
            }
        }

        claim(
                "the shipped configuration was read, so an empty answer below is not an empty file",
                () -> assertThat(properties).isNotEmpty());
        claim(
                "it holds no key whose last part names a token, a personal access token, a password, a user name, an"
                        + " API key or a secret: the services it addresses are the two on the operator's own machine",
                () -> assertThat(credentialKeys).as("keys that would hold a credential").isEmpty());
    }

    @Test
    @Story("No client for Confluence is built in")
    @DisplayName("The build declares no Atlassian library")
    void theBuildDeclaresNoAtlassianArtifact() throws IOException {
        String pom = Files.readString(POM);

        claim(
                "the build was read, so an empty answer below is not an empty file",
                () -> assertThat(pom).contains("<artifactId>vespera</artifactId>"));
        claim(
                "the build declares nothing published under com.atlassian, where Atlassian's own clients for Confluence"
                        + " are",
                () -> assertThat(pom).doesNotContain(ATLASSIANS_GROUP));
    }

    @Test
    @Story("No command loads the deliverable")
    @DisplayName("The command offers run and label, and no third subcommand")
    void theCommandHasRunAndLabelAndNoOther() {
        Command vespera = VesperaCommand.class.getAnnotation(Command.class);
        List<String> subcommands = Arrays.stream(vespera.subcommands())
                .map(subcommand -> subcommand.getAnnotation(Command.class).name())
                .toList();

        claim(
                "the command's subcommands are run and then label, and none other, so no subcommand loads the files it"
                        + " draws anywhere",
                () -> assertThat(subcommands).containsExactly("run", "label"));
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
