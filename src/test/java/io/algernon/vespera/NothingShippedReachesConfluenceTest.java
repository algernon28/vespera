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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * Neither the jar nor the control panel sends the HTML tree or the Confluence rendering to Confluence, holds a
 * credential for it, or offers a command for a load (ADR-304).
 *
 * <p>An uploader cannot be written without naming where it sends: a REST path of Confluence's or the host of an
 * Atlassian Cloud site. It cannot be configured without a key for its credential, and it cannot be offered on the
 * command line without a third subcommand. Each of those is read here from the files, with no Docker and no
 * network. The whole production tree is read, so the control panel's classes, which lie outside the nine modules,
 * are read with the jar's. A Confluence rendering drawn as files names neither a REST path nor a host, so ADR-301's
 * renderer does not turn this red. It holds the uploader written plainly; a path or a host built from pieces at run
 * time is beyond a static check.
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

    /** Where the shipped configuration lies: every YAML file under it is read. */
    private static final Path SHIPPED_RESOURCES = SHIPPED_TREE.resolve("resources");

    /**
     * What a call to Confluence names, compared without regard to case: the REST API of a Data Center site and of
     * a Cloud site under {@code /wiki}, its second version on Cloud, and the domain every Cloud site lies under.
     */
    private static final List<String> WHERE_AN_UPLOADER_SENDS = List.of("/rest/api/", "/wiki/api/v2", "atlassian.net");

    /**
     * A key of the shipped configuration that would hold a credential: it ends, after a dot or a hyphen, in a
     * token, a personal access token, a password, a user name, an API key, a secret, credentials or a bearer.
     * {@code max-tokens}, a count, does not match.
     */
    private static final Pattern A_CREDENTIALS_KEY = Pattern.compile(
            "(?i)(^|[.-])(token|personal-access-token|pat|password|user-?name|api-?key|secret|credentials?|bearer)$");

    /** A key of the shipped configuration that names Confluence or Atlassian, as a base URL's key would. */
    private static final Pattern A_CONFLUENCE_KEY = Pattern.compile("(?i)confluence|atlassian");

    /** The Maven {@code groupId} Atlassian's client libraries are declared under, however it is spaced. */
    private static final Pattern ATLASSIANS_GROUP = Pattern.compile("(?s)<groupId>\\s*com\\.atlassian");

    /**
     * The options each command declares, its own and its mixins': {@code vespera} none but the standard help
     * options, {@code run} the working directory, {@code label} the local labeller and the working directory.
     */
    private static final Map<String, Set<String>> THE_OPTIONS = Map.of(
            "vespera", Set.of(),
            "run", Set.of("--db-dir"),
            "label", Set.of("--auto", "--db-dir"));

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
    @DisplayName("No shipped configuration file holds a key for a credential or a key naming Confluence")
    void theShippedConfigurationHoldsNoCredential() throws IOException {
        Map<String, Map<Object, Object>> files = shippedConfigurations();
        Set<String> credentialKeys = new TreeSet<>();
        Set<String> confluenceKeys = new TreeSet<>();
        files.forEach((file, properties) -> properties.keySet().stream().map(String::valueOf).forEach(key -> {
            if (A_CREDENTIALS_KEY.matcher(key).find()) {
                credentialKeys.add(file + ": " + key);
            }
            if (A_CONFLUENCE_KEY.matcher(key).find()) {
                confluenceKeys.add(file + ": " + key);
            }
        }));

        claim(
                "the shipped configuration was read, " + SHIPPED_CONFIGURATION + " among it, and holds keys, so an"
                        + " empty answer below is not an empty walk",
                () -> assertThat(files).hasEntrySatisfying(
                        SHIPPED_RESOURCES.resolve(SHIPPED_CONFIGURATION).toString().replace('\\', '/'),
                        properties -> assertThat(properties).isNotEmpty()));
        claim(
                "no key ends in a token, a personal access token, a password, a user name, an API key, a secret,"
                        + " credentials or a bearer: the services it addresses are the two on the operator's own"
                        + " machine",
                () -> assertThat(credentialKeys).as("keys that would hold a credential").isEmpty());
        claim(
                "and no key names Confluence or Atlassian, as the key of a site's address would",
                () -> assertThat(confluenceKeys).as("keys naming Confluence or Atlassian").isEmpty());
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
                () -> assertThat(ATLASSIANS_GROUP.matcher(pom).find()).isFalse());
    }

    @Test
    @Story("No command loads the deliverable")
    @DisplayName("The command offers run and label and no third subcommand, and no command has an option beyond its own")
    void theCommandHasRunAndLabelAndNoOther() {
        Command vespera = VesperaCommand.class.getAnnotation(Command.class);
        Map<String, Set<String>> options = new LinkedHashMap<>();
        options.put(vespera.name(), optionsOf(VesperaCommand.class));
        for (Class<?> subcommand : vespera.subcommands()) {
            options.put(subcommand.getAnnotation(Command.class).name(), optionsOf(subcommand));
        }
        List<String> subcommands = Arrays.stream(vespera.subcommands())
                .map(subcommand -> subcommand.getAnnotation(Command.class).name())
                .toList();

        claim(
                "the command's subcommands are run and label, and none other, so no subcommand loads the files it"
                        + " draws anywhere",
                () -> assertThat(subcommands).containsExactlyInAnyOrder("run", "label"));
        claim(
                "each command declares exactly the options it has: vespera none of its own, run --db-dir, label --auto"
                        + " and --db-dir, so no option turns a load on",
                () -> assertThat(options).containsExactlyInAnyOrderEntriesOf(THE_OPTIONS));
    }

    /** The names of the options a command class declares, on its fields and methods and through its mixins. */
    private static Set<String> optionsOf(Class<?> command) {
        Set<String> names = new TreeSet<>();
        Stream.concat(Arrays.stream(command.getDeclaredFields()), Arrays.stream(command.getDeclaredMethods()))
                .map(AccessibleObject.class::cast)
                .forEach(member -> {
                    Option option = member.getAnnotation(Option.class);
                    if (option != null) {
                        names.addAll(List.of(option.names()));
                    }
                    if (member.isAnnotationPresent(Mixin.class) && member instanceof Field mixin) {
                        names.addAll(optionsOf(mixin.getType()));
                    }
                });
        return names;
    }

    private static List<Path> filesUnder(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).sorted().toList();
        }
    }

    /** Every YAML file under the shipped resources, by its path, flattened to properties as Spring reads it. */
    private static Map<String, Map<Object, Object>> shippedConfigurations() throws IOException {
        Map<String, Map<Object, Object>> read = new TreeMap<>();
        for (Path file : filesUnder(SHIPPED_RESOURCES)) {
            String name = file.getFileName().toString();
            if (name.endsWith(".yaml") || name.endsWith(".yml")) {
                YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
                yaml.setResources(new FileSystemResource(file));
                yaml.afterPropertiesSet();
                read.put(file.toString().replace('\\', '/'), yaml.getObject());
            }
        }
        return read;
    }
}
