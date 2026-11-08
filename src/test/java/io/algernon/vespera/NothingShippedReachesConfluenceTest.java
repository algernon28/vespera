package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
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
     * The options of each command of the tree, beside the standard help ones, by their longest names: {@code
     * vespera} none, {@code run} the working directory, {@code label} the local labeller and the working directory,
     * {@code publish} the working directory and the destinations, {@code --to}, which names plugins and never a site,
     * a space or a credential (ADR-333 §6), {@code serve} the working directory alone, its port being a property and
     * no option (ADR-333 §8).
     */
    private static final Map<String, Set<String>> THE_OPTIONS = Map.of(
            "vespera", Set.of(),
            "run", Set.of("--db-dir"),
            "label", Set.of("--auto", "--db-dir"),
            "publish", Set.of("--db-dir", "--to"),
            "serve", Set.of("--db-dir"));

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
