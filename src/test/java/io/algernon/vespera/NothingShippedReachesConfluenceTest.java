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
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.objenesis.ObjenesisStd;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

/**
 * Only the {@code confluence} module reaches Confluence, no shipped file holds a credential for it, and the one command
 * that sends is {@code publish} (ADR-308 §1, §3, §5, §7).
 *
 * <p>A call to Confluence cannot be written without naming where it sends: a REST path of Confluence's or the host of
 * an Atlassian Cloud site. Each of those is read here from the files, with no Docker and no network, and is found under
 * {@code io/algernon/vespera/confluence/} alone, in its sources and its resources. The whole production tree is read,
 * so the control panel's classes, which lie outside the ten modules, are read with the jar's. It holds a call written
 * plainly; a path or a host built from pieces at run time is beyond a static check.
 *
 * <p>That the control panel names no type of the JDK's HTTP client is held by {@code
 * TheControlPanelLinksNoClassOfTheJarTest}, under ADR-285, and that it starts only {@code run}, {@code label} and
 * {@code publish} by {@code ChildCommandTest}, under ADR-278 and ADR-308 §6.
 */
@Epic("Architecture")
@Feature("Nothing leaves the machine")
@Issue("633")
@Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
class NothingShippedReachesConfluenceTest {

    /** The production tree: the jar's sources, the control panel's, and every resource either packs. */
    private static final Path SHIPPED_TREE = Path.of("src", "main");

    /** The one module's folder, in the sources and the resources alike, that may name where a call to Confluence goes. */
    private static final String THE_PUBLISHERS_FOLDER = "io/algernon/vespera/confluence/";

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
     * A key of the shipped configuration for a Confluence or Atlassian site's address: one naming either and ending,
     * after a dot or a hyphen, in a URL, a URI, a base URL, a host, a site, an address or an endpoint. A key that
     * names Confluence for anything else, as the edition a rendering is drawn for would, does not match.
     */
    private static final Pattern A_CONFLUENCE_ADDRESS_KEY =
            Pattern.compile("(?i)(confluence|atlassian).*[.-](url|uri|base-?url|host|site|address|endpoint)$");

    /**
     * A key of the shipped configuration for a Confluence or Atlassian credential: one naming either and ending,
     * after a dot or a hyphen, in a token, a personal access token, a password, a user name, an API key, a secret,
     * credentials or a bearer. A sidecar's own key is ADR-084's and is not matched.
     */
    private static final Pattern A_CONFLUENCE_CREDENTIALS_KEY = Pattern.compile(
            "(?i)(confluence|atlassian).*[.-](token|personal-access-token|pat|password|user-?name|api-?key|secret"
                    + "|credentials?|bearer)$");

    /** The Maven {@code groupId} Atlassian's client libraries are declared under, however it is spaced. */
    private static final Pattern ATLASSIANS_GROUP = Pattern.compile("(?s)<groupId>\\s*com\\.atlassian");

    /**
     * The options of each command of the tree, beside the standard help ones, by their longest names: {@code
     * vespera} none, {@code run} the working directory, {@code label} the local labeller and the working directory,
     * {@code publish} the working directory.
     */
    private static final Map<String, Set<String>> THE_OPTIONS = Map.of(
            "vespera", Set.of(),
            "run", Set.of("--db-dir"),
            "label", Set.of("--auto", "--db-dir"),
            "publish", Set.of("--db-dir"));

    /**
     * Builds each command without calling its constructor, whose collaborators are the application's, so that
     * picocli reads the commands' model, mixins included, with no application context; anything else, a mixin
     * among them, is built as picocli builds it.
     */
    private static final IFactory WITHOUT_ITS_COLLABORATORS = new IFactory() {
        private final ObjenesisStd objenesis = new ObjenesisStd();

        @Override
        public <K> K create(Class<K> type) throws Exception {
            return type.isAnnotationPresent(Command.class)
                    ? objenesis.newInstance(type)
                    : CommandLine.defaultFactory().create(type);
        }
    };

    @Test
    @Story("Only the publisher sends to Confluence")
    @DisplayName("No shipped file outside the confluence module names a Confluence REST path or an Atlassian Cloud host")
    void onlyTheConfluenceModuleNamesAConfluenceRestPath() throws IOException {
        List<Path> files = filesUnder(SHIPPED_TREE);
        Set<String> naming = new TreeSet<>();
        Set<String> namingInThePublisher = new TreeSet<>();
        for (Path file : files) {
            String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
            if (WHERE_AN_UPLOADER_SENDS.stream().anyMatch(text::contains)) {
                String path = file.toString().replace('\\', '/');
                (path.contains(THE_PUBLISHERS_FOLDER) ? namingInThePublisher : naming).add(path);
            }
        }

        claim(
                "the production tree was read, the shipped configuration and the control panel's classes among it, and"
                        + " the publisher names Confluence's REST paths, so an empty answer below is not an empty walk",
                () -> {
                    assertThat(files)
                            .anyMatch(file -> file.endsWith(SHIPPED_CONFIGURATION))
                            .anyMatch(file -> file.toString().replace('\\', '/').contains("io/algernon/controlpanel/"));
                    assertThat(namingInThePublisher).as("files of the publisher naming one").isNotEmpty();
                });
        claim(
                "no file outside " + THE_PUBLISHERS_FOLDER + " names any of the " + WHERE_AN_UPLOADER_SENDS.size()
                        + " things a call to Confluence names: its REST path, its second API's path on a Cloud site, or"
                        + " the Cloud sites' domain",
                () -> assertThat(naming).as("files naming one").isEmpty());
    }

    @Test
    @Story("No credential for Confluence is held")
    @DisplayName("No shipped configuration file holds a key for a Confluence site's address or credential")
    void theShippedConfigurationHoldsNoCredential() throws IOException {
        Map<String, Map<Object, Object>> files = shippedConfigurations();
        Set<String> addressKeys = new TreeSet<>();
        Set<String> credentialKeys = new TreeSet<>();
        files.forEach((file, properties) -> properties.keySet().stream().map(String::valueOf).forEach(key -> {
            if (A_CONFLUENCE_ADDRESS_KEY.matcher(key).find()) {
                addressKeys.add(file + ": " + key);
            }
            if (A_CONFLUENCE_CREDENTIALS_KEY.matcher(key).find()) {
                credentialKeys.add(file + ": " + key);
            }
        }));

        claim(
                "the shipped configuration was read, " + SHIPPED_CONFIGURATION + " among it, and holds keys, so an"
                        + " empty answer below is not an empty walk",
                () -> assertThat(files).hasEntrySatisfying(
                        SHIPPED_RESOURCES.resolve(SHIPPED_CONFIGURATION).toString().replace('\\', '/'),
                        properties -> assertThat(properties).isNotEmpty()));
        claim(
                "no key names Confluence or Atlassian and ends in a URL, a URI, a base URL, a host, a site, an address"
                        + " or an endpoint, as the key of a site's address would",
                () -> assertThat(addressKeys).as("keys for a Confluence or Atlassian site's address").isEmpty());
        claim(
                "and no key names Confluence or Atlassian and ends in a token, a personal access token, a password, a"
                        + " user name, an API key, a secret, credentials or a bearer, as the key of a credential for"
                        + " one would",
                () -> assertThat(credentialKeys).as("keys for a Confluence or Atlassian credential").isEmpty());
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
    @Story("Only the publisher sends to Confluence")
    @DisplayName("The command tree is run, label and publish under vespera, with no other command and no option beyond the ones it has")
    void theCommandHasRunLabelAndPublish() {
        CommandSpec vespera = new CommandLine(VesperaCommand.class, WITHOUT_ITS_COLLABORATORS).getCommandSpec();
        Map<String, Set<String>> options = new LinkedHashMap<>();
        Map<String, Set<String>> subcommands = new LinkedHashMap<>();
        walk(vespera, options, subcommands);

        claim(
                "the tree was read through the command's own model, so the answers below are about the command an"
                        + " operator types, its mixins and argument groups included",
                () -> assertThat(vespera.name()).isEqualTo("vespera"));
        claim(
                "vespera has run, label and publish, and none has a subcommand of its own, so publish is the one"
                        + " command that sends the rendering anywhere",
                () -> assertThat(subcommands).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "vespera", Set.of("run", "label", "publish"),
                        "run", Set.of(),
                        "label", Set.of(),
                        "publish", Set.of())));
        claim(
                "each command has exactly the options it has beside the standard help ones: vespera none, run --db-dir,"
                        + " label --auto and --db-dir, publish --db-dir, so no option names a site, a space or a"
                        + " credential",
                () -> assertThat(options).containsExactlyInAnyOrderEntriesOf(THE_OPTIONS));
    }

    /**
     * Every command under {@code command}, itself included, by its name: its options' longest names, the standard
     * help ones left out, and the names of its subcommands.
     */
    private static void walk(CommandSpec command, Map<String, Set<String>> options, Map<String, Set<String>> subcommands) {
        options.put(command.name(), command.options().stream()
                .filter(option -> !option.usageHelp() && !option.versionHelp())
                .map(OptionSpec::longestName)
                .collect(Collectors.toCollection(TreeSet::new)));
        subcommands.put(command.name(), new TreeSet<>(command.subcommands().keySet()));
        command.subcommands().values().forEach(sub -> walk(sub.getCommandSpec(), options, subcommands));
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
