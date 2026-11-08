package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.VesperaApplication;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.objenesis.ObjenesisStd;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;

/**
 * {@code vespera serve} starts with no Spring context, through {@code pipeline.ServeCommand} (ADR-333 §8):
 * {@code VesperaApplication.main} hands {@code serve} and the arguments after it to {@code ServeCommand} before
 * {@code SpringApplication} is built, and every other first argument to Spring; {@code ServeCommand} resolves the
 * working directory as {@code application.yaml}'s {@code vespera.working-dir: ${db-dir:${user.dir}/.vespera}} does,
 * and the port {@code vespera.serve.port} names, 24332 where nothing names one, each from the command's arguments,
 * then the JVM's system properties, then the environment, reading no YAML file; and it refuses in one line, exit 1,
 * creating nothing, a {@code --db-dir} that disagrees with a set {@code vespera.working-dir}, a working directory that
 * is not there, one holding no published website, a port in use and a value for the port that is no port (ADR-341
 * §3). Arguments it cannot read end it with exit 2, a blank value is unset in each of the three sources, a blank
 * {@code --db-dir=} excepted, which the mixin reads as the folder started in and so refuses, and the environment's
 * names are found as Spring relaxes them, {@code DB_DIR} among them (ADR-341 §4, §5).
 *
 * <p>What {@code ServeCommand} reads is handed to it here, a {@link Properties} for the system properties and a map for
 * the environment, so that each source is set alone and the process's own are not touched. Where the working directory
 * and the port come from is held against Spring's own reading of the shipped line over the same three sources, in
 * Spring's order, so that the two cannot drift apart.
 */
@Epic("Operation")
@Feature("The website")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
@Link(name = "ADR-054", url = Adr.CORPUS_IS_ITS_ROOT_PATH, type = "adr")
@Link(name = "ADR-341", url = Adr.SERVE_TELLS_A_PAGE_FROM_A_COPY_BY_THE_LIST_AND_THE_NAME, type = "adr")
class ServeCommandTest {

    /** The property naming the port, read from the arguments, the system properties and the environment. */
    private static final String THE_PORT = "vespera.serve.port";

    /** The property naming the working directory, and the one {@code --db-dir=} sets. */
    private static final String WORKING_DIR = "vespera.working-dir";

    private static final String DB_DIR = "db-dir";

    private static final Path SHIPPED_RESOURCES = Path.of("src", "main", "resources");

    private static final Path APPLICATION_YAML = SHIPPED_RESOURCES.resolve("application.yaml");

    /** The website's folder in the working directory, and the file a publish to it leaves there (ADR-333 §7). */
    private static final String WEBSITE = "website";

    private static final String VESPERA_WEBSITE_JSON = "vespera-website.json";

    /** {@code WorkingDirectoryOption.disagreement}'s line after the two folders (ADR-333 §8, §15). */
    private static final String THE_REST_OF_THE_DISAGREEMENT = " --db-dir has to be given as --db-dir=<path>, because"
            + " it is read as the db-dir property vespera.working-dir is resolved from, before this command is parsed";

    /** A refusal returns at once; a command that served instead would never return, and this ends the test. */
    private static final long A_REFUSAL_SECONDS = 30;

    @TempDir
    Path base;

    /** Where a value is set: on the command line, as a system property or in the environment. */
    enum Source {
        ARGUMENTS,
        SYSTEM_PROPERTIES,
        ENVIRONMENT
    }

    /** One value set for a case: the property, where it is set, and the folder of {@code base} it names. */
    record Setting(String property, Source source, String folder) {}

    @Test
    @Timeout(value = A_REFUSAL_SECONDS, unit = TimeUnit.SECONDS)
    @Story("The website is served on loopback alone")
    @DisplayName("A --db-dir that disagrees with a set vespera.working-dir is refused, exit 1, in the line every other command gives")
    void aDbDirThatDisagreesWithTheWorkingDirectoryIsRefused() throws IOException {
        Path workingDirectory = published(base.resolve("the-working-directory"));
        Path typed = base.resolve("typed-on-the-command-line");
        Sources sources = sourcesOf(List.of(new Setting(WORKING_DIR, Source.SYSTEM_PROPERTIES, "the-working-directory")));

        int exit = sources.command().call("--db-dir=" + typed);

        claim("the command ends with exit code 1, having served nothing",
                () -> assertThat(exit).isEqualTo(CommandLine.ExitCode.SOFTWARE));
        claim("its one line is WorkingDirectoryOption's, naming vespera serve, the folder typed and the working directory"
                        + " in use, on standard error, and nothing is on standard output",
                () -> {
                    assertThat(sources.errors()).containsExactly("vespera serve was given --db-dir " + typed
                            + " but the working directory in use is " + workingDirectory + ";"
                            + THE_REST_OF_THE_DISAGREEMENT);
                    assertThat(sources.output()).isEmpty();
                });
        claim("and the folder typed was not made",
                () -> assertThat(typed).doesNotExist());
    }

    @Test
    @Timeout(value = A_REFUSAL_SECONDS, unit = TimeUnit.SECONDS)
    @Story("The website is served on loopback alone")
    @DisplayName("A working directory holding no published website is refused, exit 1, in one line, and nothing is made in it")
    void aWorkingDirectoryHoldingNoPublishedWebsiteIsRefused() throws IOException {
        Path noFolder = Files.createDirectories(base.resolve("no-website-folder"));
        Path noList = Files.createDirectories(base.resolve("a-website-folder-and-no-list"));
        Files.writeString(Files.createDirectories(noList.resolve(WEBSITE)).resolve("index.html"), "<p>not published</p>");
        Map<Path, Set<String>> before = Map.of(noFolder, filesUnder(noFolder), noList, filesUnder(noList));

        Sources withNoFolder = sourcesOf(List.of(new Setting(WORKING_DIR, Source.SYSTEM_PROPERTIES, "no-website-folder")));
        Sources withNoList =
                sourcesOf(List.of(new Setting(WORKING_DIR, Source.SYSTEM_PROPERTIES, "a-website-folder-and-no-list")));
        int noFolderExit = withNoFolder.command().call();
        int noListExit = withNoList.command().call();

        claim("each ends with exit code 1",
                () -> assertThat(List.of(noFolderExit, noListExit))
                        .containsOnly(CommandLine.ExitCode.SOFTWARE));
        claim("each in the one line naming the website folder, whether there is no such folder or it holds no"
                        + " vespera-website.json, on standard error",
                () -> {
                    assertThat(withNoFolder.errors()).containsExactly(nothingToServe(noFolder));
                    assertThat(withNoList.errors()).containsExactly(nothingToServe(noList));
                    assertThat(withNoFolder.output()).isEmpty();
                    assertThat(withNoList.output()).isEmpty();
                });
        claim("and neither working directory holds anything it did not hold before",
                () -> assertThat(Map.of(noFolder, filesUnder(noFolder), noList, filesUnder(noList)))
                        .isEqualTo(before));
    }

    @Test
    @Timeout(value = A_REFUSAL_SECONDS, unit = TimeUnit.SECONDS)
    @Story("The website is served on loopback alone")
    @DisplayName("A working directory that is not there is refused, exit 1, in one line naming it, and is not made")
    void aWorkingDirectoryThatIsNotThereIsRefusedAndNotMade() {
        Path notThere = base.resolve("not-there");
        Sources sources = sourcesOf(List.of(new Setting(WORKING_DIR, Source.SYSTEM_PROPERTIES, "not-there")));

        int exit = sources.command().call();

        claim("the command ends with exit code 1, having served nothing",
                () -> assertThat(exit).isEqualTo(CommandLine.ExitCode.SOFTWARE));
        claim("in the one line naming the working directory as no folder that is there, on standard error, and nothing"
                        + " on standard output",
                () -> {
                    assertThat(sources.errors()).containsExactly("vespera serve has nothing to serve: " + notThere
                            + " is no folder that is there.");
                    assertThat(sources.output()).isEmpty();
                });
        claim("and the working directory was not made",
                () -> assertThat(notThere).doesNotExist());
    }

    // ---------- helpers ----------

    /**
     * Builds each command without calling its constructor, whose collaborators are the application's, so that picocli
     * reads the commands' model with no application context, as {@code NothingShippedReachesConfluenceTest} builds it.
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

    /** The line refusing a working directory whose website folder holds no published website (ADR-333 §8). */
    private static String nothingToServe(Path workingDirectory) {
        return "vespera serve has nothing to serve: " + workingDirectory.resolve(WEBSITE) + " holds no published"
                + " website. Run vespera publish --to website first.";
    }

    /** {@code workingDirectory}, made, its website folder holding a list of what a publish wrote there, of no file. */
    private static Path published(Path workingDirectory) throws IOException {
        Path website = Files.createDirectories(workingDirectory.resolve(WEBSITE));
        Files.writeString(website.resolve(VESPERA_WEBSITE_JSON), "{\"format\":1,\"files\":[]}\n", StandardCharsets.UTF_8);
        return workingDirectory;
    }

    /**
     * The three sources {@code settings} fill, each folder resolved under {@code base} where the value is one, the
     * system properties holding {@code user.dir} as {@code base/started-here}; the command reading them, and what it
     * writes on standard output and standard error.
     */
    private Sources sourcesOf(List<Setting> settings) {
        List<String> arguments = new ArrayList<>();
        Properties systemProperties = new Properties();
        systemProperties.setProperty("user.dir", base.resolve("started-here").toString());
        Map<String, String> environment = new HashMap<>();
        for (Setting setting : settings) {
            String value = THE_PORT.equals(setting.property()) ? setting.folder() : base.resolve(setting.folder()).toString();
            switch (setting.source()) {
                case ARGUMENTS -> arguments.add("--" + setting.property() + "=" + value);
                case SYSTEM_PROPERTIES -> systemProperties.setProperty(setting.property(), value);
                case ENVIRONMENT -> environment.put(environmentNameOf(setting.property()), value);
            }
        }
        return new Sources(arguments, systemProperties, environment);
    }

    /**
     * The environment's name for a property, as ADR-333 §8 names the two, {@code VESPERA_WORKING_DIR} and {@code
     * VESPERA_SERVE_PORT}, and as Spring relaxes {@code db-dir} (ADR-341 §5).
     */
    private static String environmentNameOf(String property) {
        return switch (property) {
            case WORKING_DIR -> "VESPERA_WORKING_DIR";
            case THE_PORT -> "VESPERA_SERVE_PORT";
            case DB_DIR -> "DB_DIR";
            default -> throw new IllegalArgumentException("no environment name is held for " + property);
        };
    }

    /** What one case sets, the command reading it, and what that command printed. */
    private static final class Sources {
        private final List<String> arguments;
        private final Properties systemProperties;
        private final Map<String, String> environment;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();
        private ServeCommand command;

        Sources(List<String> arguments, Properties systemProperties, Map<String, String> environment) {
            this.arguments = arguments;
            this.systemProperties = systemProperties;
            this.environment = environment;
        }

        ServeCommand command() {
            if (command == null) {
                command = new ServeCommand(
                        systemProperties,
                        Map.copyOf(environment),
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8));
            }
            return command;
        }

        List<String> output() {
            return linesOf(out);
        }

        List<String> errors() {
            return linesOf(err);
        }

        private static List<String> linesOf(ByteArrayOutputStream stream) {
            return stream.toString(StandardCharsets.UTF_8).lines().filter(line -> !line.isBlank()).toList();
        }
    }

    /** Every file and folder beneath {@code folder}, by its path relative to it with {@code /} between its names. */
    private static Set<String> filesUnder(Path folder) throws IOException {
        Set<String> found = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.filter(path -> !path.equals(folder))
                    .forEach(path -> found.add(folder.relativize(path).toString().replace('\\', '/')));
        }
        return found;
    }
}
