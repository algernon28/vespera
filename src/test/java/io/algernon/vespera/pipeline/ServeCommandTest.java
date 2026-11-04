package io.algernon.vespera.pipeline;

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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;
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
 * creating nothing, a {@code --db-dir} that disagrees with a set {@code vespera.working-dir}, a working directory
 * holding no published website and a port in use. That a working directory not there is refused and not made, on the
 * packaged jar, is {@code ServeHoldsNoLockIT}'s.
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
class ServeCommandTest {

    /** The port {@code vespera serve} listens at where nothing names one (ADR-333 §8). */
    private static final int THE_PORT_WHERE_NOTHING_NAMES_ONE = 24332;

    /** The property naming the port, read from the arguments, the system properties and the environment. */
    private static final String THE_PORT = "vespera.serve.port";

    /** The property naming the working directory, and the one {@code --db-dir=} sets. */
    private static final String WORKING_DIR = "vespera.working-dir";

    private static final String DB_DIR = "db-dir";

    /** {@code application.yaml}'s line for the working directory, which {@code ServeCommand} reads as Spring does. */
    private static final String THE_SHIPPED_LINE = "${db-dir:${user.dir}/.vespera}";

    private static final Path SHIPPED_RESOURCES = Path.of("src", "main", "resources");

    private static final Path APPLICATION_YAML = SHIPPED_RESOURCES.resolve("application.yaml");

    /** The website's folder in the working directory, and the file a publish to it leaves there (ADR-333 §7). */
    private static final String WEBSITE = "website";

    private static final String VESPERA_WEBSITE_JSON = "vespera-website.json";

    /** {@code WorkingDirectoryOption.disagreement}'s line after the two folders (ADR-333 §8, §15). */
    private static final String THE_REST_OF_THE_DISAGREEMENT = " --db-dir has to be given as --db-dir=<path>, because"
            + " it is read as the db-dir property vespera.working-dir is resolved from, before this command is parsed";

    /** Generous over a JVM that starts no context; the point is only that it ends. */
    private static final long A_START_WITHOUT_SPRING_SECONDS = 60;

    /** A refusal returns at once; a command that served instead would never return, and this ends the test. */
    private static final long A_REFUSAL_SECONDS = 30;

    @TempDir
    Path base;

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("serve as the first argument is handed on with the arguments after it, and any other first argument is left to Spring")
    void mainHandsServeAndTheArgumentsAfterItOnAndEveryOtherToSpring() {
        claim("serve alone is handed on with no argument after it",
                () -> assertThat(ServeCommand.handedOn("serve")).contains(List.of()));
        claim("serve and what follows it is handed on as the arguments after serve, in their order",
                () -> assertThat(ServeCommand.handedOn("serve", "--db-dir=elsewhere", "--vespera.serve.port=1"))
                        .contains(List.of("--db-dir=elsewhere", "--vespera.serve.port=1")));
        List<List<String>> leftToSpring = List.of(
                List.of(),
                List.of("run", "serve"),
                List.of("label"),
                List.of("publish", "--to", "website"),
                List.of("--help"),
                List.of("--version"),
                List.of("--db-dir=elsewhere", "serve"),
                List.of("Serve"),
                List.of("serve-it"));
        claim("and every other first argument, serve written later, in another case or inside another word among them,"
                        + " is left to Spring",
                () -> assertThat(leftToSpring).allSatisfy(arguments ->
                        assertThat(ServeCommand.handedOn(arguments.toArray(String[]::new)))
                                .as("what is handed on of %s", arguments)
                                .isEmpty()));
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("The jar's main serves with no Spring context: it answers in serve's one line, and makes nothing in the working directory")
    void theJarsMainHandsServeOnBeforeSpringIsBuilt() throws Exception {
        Path workingDirectory = Files.createDirectories(base.resolve("holds-no-website"));
        Path printed = base.resolve("printed.txt");
        ProcessBuilder builder = new ProcessBuilder(
                        ProcessHandle.current().info().command()
                                .orElseGet(() -> Path.of(System.getProperty("java.home"), "bin", "java").toString()),
                        "-Dstdout.encoding=UTF-8",
                        "-Dstderr.encoding=UTF-8",
                        "-D" + WORKING_DIR + "=" + workingDirectory,
                        "-cp",
                        System.getProperty("java.class.path"),
                        VesperaApplication.class.getName(),
                        "serve")
                .redirectErrorStream(true)
                .redirectOutput(printed.toFile());
        // Nothing opens a browser on the machine running the tests (ADR-266 section 2).
        builder.environment().put("CI", "true");
        Process process = builder.start();
        boolean finished = process.waitFor(A_START_WITHOUT_SPRING_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor();
        }
        List<String> lines = Files.readAllLines(printed, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();

        claim("the process ends within " + A_START_WITHOUT_SPRING_SECONDS + " seconds, with exit code 1",
                () -> {
                    assertThat(finished).isTrue();
                    assertThat(process.exitValue()).isEqualTo(CommandLine.ExitCode.SOFTWARE);
                });
        claim("having printed serve's own refusal and nothing else: no banner, no line of a context starting",
                () -> assertThat(lines).containsExactly("vespera serve has nothing to serve: "
                        + workingDirectory.resolve(WEBSITE) + " holds no published website. Run vespera publish --to"
                        + " website first."));
        claim("and the working directory is as it was, empty: no vespera.db, no vespera.lock, no logs folder",
                () -> assertThat(filesUnder(workingDirectory)).isEmpty());
    }

    /** Where a value is set: on the command line, as a system property or in the environment. */
    enum Source {
        ARGUMENTS,
        SYSTEM_PROPERTIES,
        ENVIRONMENT
    }

    /** One value set for a case: the property, where it is set, and the folder of {@code base} it names. */
    record Setting(String property, Source source, String folder) {}

    static Stream<Arguments> whereTheWorkingDirectoryIsNamed() {
        return Stream.of(
                Arguments.of(Named.of("--db-dir= on the command line", List.of(
                        new Setting(DB_DIR, Source.ARGUMENTS, "typed"))), "typed"),
                Arguments.of(Named.of("db-dir as a system property", List.of(
                        new Setting(DB_DIR, Source.SYSTEM_PROPERTIES, "a-property"))), "a-property"),
                Arguments.of(Named.of("--db-dir= on the command line over db-dir as a system property", List.of(
                        new Setting(DB_DIR, Source.SYSTEM_PROPERTIES, "a-property"),
                        new Setting(DB_DIR, Source.ARGUMENTS, "typed"))), "typed"),
                Arguments.of(Named.of("VESPERA_WORKING_DIR in the environment", List.of(
                        new Setting(WORKING_DIR, Source.ENVIRONMENT, "the-environment"))), "the-environment"),
                Arguments.of(Named.of("vespera.working-dir as a system property over the environment", List.of(
                        new Setting(WORKING_DIR, Source.ENVIRONMENT, "the-environment"),
                        new Setting(WORKING_DIR, Source.SYSTEM_PROPERTIES, "a-property"))), "a-property"),
                Arguments.of(Named.of("--vespera.working-dir= on the command line over a system property", List.of(
                        new Setting(WORKING_DIR, Source.SYSTEM_PROPERTIES, "a-property"),
                        new Setting(WORKING_DIR, Source.ARGUMENTS, "typed"))), "typed"),
                Arguments.of(Named.of("vespera.working-dir in the environment over --db-dir= on the command line", List.of(
                        new Setting(DB_DIR, Source.ARGUMENTS, "typed"),
                        new Setting(WORKING_DIR, Source.ENVIRONMENT, "the-environment"))), "the-environment"),
                Arguments.of(Named.of("vespera.working-dir as a system property over db-dir as one", List.of(
                        new Setting(DB_DIR, Source.SYSTEM_PROPERTIES, "named-by-db-dir"),
                        new Setting(WORKING_DIR, Source.SYSTEM_PROPERTIES, "named-by-working-dir"))),
                        "named-by-working-dir"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("whereTheWorkingDirectoryIsNamed")
    @Story("The website is served on loopback alone")
    @DisplayName("The working directory is vespera.working-dir, else db-dir, each from the arguments, then the system properties, then the environment, as Spring reads the shipped line")
    void theWorkingDirectoryResolvesAsTheShippedLineDoes(List<Setting> settings, String expected) throws IOException {
        Sources sources = sourcesOf(settings);
        Path named = base.resolve(expected);

        Path served = sources.command().workingDirectory(sources.argumentArray());
        Path bySpring = Path.of(sources.springReading(WORKING_DIR, null));

        claim("vespera serve takes the working directory the settings name, by the order of the three sources and"
                        + " vespera.working-dir before db-dir",
                () -> assertThat(served.toAbsolutePath().normalize()).isEqualTo(named.toAbsolutePath().normalize()));
        claim("which is the one Spring reads application.yaml's line as, over the same three sources",
                () -> assertThat(bySpring.toAbsolutePath().normalize()).isEqualTo(named.toAbsolutePath().normalize()));
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("Where nothing names the working directory, vespera serve takes .vespera under the folder it was started in, as application.yaml's line has it")
    void theDefaultIsTheShippedLines() throws IOException {
        Map<Object, Object> shipped = yamlOf(APPLICATION_YAML);
        Sources sources = sourcesOf(List.of());
        Path startedIn = base.resolve("started-here");

        Path served = sources.command().workingDirectory();
        Path bySpring = Path.of(sources.springReading(WORKING_DIR, null));

        claim("application.yaml names the working directory by the line vespera serve reads as its own",
                () -> assertThat(shipped).containsEntry(WORKING_DIR, THE_SHIPPED_LINE));
        claim("and with nothing set, vespera serve takes .vespera under the folder it was started in",
                () -> assertThat(served.toAbsolutePath().normalize())
                        .isEqualTo(startedIn.resolve(".vespera").toAbsolutePath().normalize()));
        claim("which is what Spring reads that line as with nothing set",
                () -> assertThat(bySpring.toAbsolutePath().normalize()).isEqualTo(served.toAbsolutePath().normalize()));
    }

    static Stream<Arguments> whereThePortIsNamed() {
        return Stream.of(
                Arguments.of(Named.of("nothing", List.<Setting>of()), THE_PORT_WHERE_NOTHING_NAMES_ONE),
                Arguments.of(Named.of("--vespera.serve.port= on the command line", List.of(
                        new Setting(THE_PORT, Source.ARGUMENTS, "24401"))), 24401),
                Arguments.of(Named.of("a system property", List.of(
                        new Setting(THE_PORT, Source.SYSTEM_PROPERTIES, "24402"))), 24402),
                Arguments.of(Named.of("VESPERA_SERVE_PORT in the environment", List.of(
                        new Setting(THE_PORT, Source.ENVIRONMENT, "24403"))), 24403),
                Arguments.of(Named.of("the command line over a system property", List.of(
                        new Setting(THE_PORT, Source.SYSTEM_PROPERTIES, "24402"),
                        new Setting(THE_PORT, Source.ARGUMENTS, "24401"))), 24401),
                Arguments.of(Named.of("a system property over the environment", List.of(
                        new Setting(THE_PORT, Source.ENVIRONMENT, "24403"),
                        new Setting(THE_PORT, Source.SYSTEM_PROPERTIES, "24402"))), 24402));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("whereThePortIsNamed")
    @Story("The website is served on loopback alone")
    @DisplayName("The port is vespera.serve.port from the arguments, then the system properties, then the environment, and 24332 where nothing names one")
    void thePortResolvesFromTheThreeSourcesInSpringsOrder(List<Setting> settings, int expected) throws IOException {
        Sources sources = sourcesOf(settings);

        int served = sources.command().port(sources.argumentArray());
        String bySpring = sources.springReading(THE_PORT, String.valueOf(THE_PORT_WHERE_NOTHING_NAMES_ONE));

        claim("vespera serve listens at the port the settings name, " + THE_PORT_WHERE_NOTHING_NAMES_ONE
                        + " where none does",
                () -> assertThat(served).isEqualTo(expected));
        claim("which is the one Spring reads over the same three sources in its order",
                () -> assertThat(Integer.parseInt(bySpring)).isEqualTo(expected));
    }

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
    @DisplayName("A port another process listens at is refused, exit 1, in one line naming the port and the property that names another")
    void aPortInUseIsRefused() throws IOException {
        published(base.resolve("the-working-directory"));
        try (ServerSocket taken = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int port = taken.getLocalPort();
            Sources sources = sourcesOf(List.of(
                    new Setting(WORKING_DIR, Source.SYSTEM_PROPERTIES, "the-working-directory"),
                    new Setting(THE_PORT, Source.SYSTEM_PROPERTIES, String.valueOf(port))));

            int exit = sources.command().call();

            claim("the command ends with exit code 1",
                    () -> assertThat(exit).isEqualTo(CommandLine.ExitCode.SOFTWARE));
            claim("in the one line naming the port taken and the property that names another, on standard error",
                    () -> {
                        assertThat(sources.errors()).containsExactly("vespera serve could not listen on port " + port
                                + ": it is in use. Name another in vespera.serve.port.");
                        assertThat(sources.output()).isEmpty();
                    });
        }
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("vespera's help lists serve beside the other commands")
    void helpListsServe() {
        CommandLine vespera = new CommandLine(VesperaCommand.class, WITHOUT_ITS_COLLABORATORS);
        String usage = vespera.getUsageMessage(CommandLine.Help.Ansi.OFF);

        claim("vespera's usage lists serve as a command with a description, as it lists run, label and publish",
                () -> assertThat(usage).containsPattern("(?m)^\\s+serve\\s+\\S")
                        .containsPattern("(?m)^\\s+publish\\s+\\S"));
        claim("and serve's own usage gives --db-dir, written as --db-dir=<path>",
                () -> assertThat(vespera.getSubcommands().get("serve").getUsageMessage(CommandLine.Help.Ansi.OFF))
                        .contains("--db-dir=<path>"));
    }

    @Test
    @Story("The website is served on loopback alone")
    @DisplayName("No shipped configuration file sets the port, since vespera serve reads none")
    void noShippedConfigurationSetsThePort() throws IOException {
        Map<String, Map<Object, Object>> shipped = new TreeMap<>();
        try (Stream<Path> files = Files.walk(SHIPPED_RESOURCES)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".yaml") || file.toString().endsWith(".yml"))
                    .toList()) {
                shipped.put(file.toString().replace('\\', '/'), yamlOf(file));
            }
        }

        claim("the shipped configuration was read, application.yaml among it, so an empty answer below is not an empty"
                        + " walk",
                () -> assertThat(shipped).containsKey(APPLICATION_YAML.toString().replace('\\', '/')));
        claim("and no shipped YAML file sets vespera.serve.port, which vespera serve would never read there",
                () -> assertThat(shipped.values()).allSatisfy(properties ->
                        assertThat(properties).doesNotContainKey(THE_PORT)));
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

    /** The environment's name for a property, as ADR-333 §8 names the two: {@code VESPERA_WORKING_DIR}, {@code VESPERA_SERVE_PORT}. */
    private static String environmentNameOf(String property) {
        return switch (property) {
            case WORKING_DIR -> "VESPERA_WORKING_DIR";
            case THE_PORT -> "VESPERA_SERVE_PORT";
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

        String[] argumentArray() {
            return arguments.toArray(String[]::new);
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

        /**
         * What Spring reads {@code property} as, over the arguments, the system properties and the environment in its
         * order, then {@code application.yaml} as shipped, {@code fallback} where none names it.
         */
        String springReading(String property, String fallback) throws IOException {
            MutablePropertySources sources = new MutablePropertySources();
            sources.addLast(new SimpleCommandLinePropertySource(argumentArray()));
            sources.addLast(new PropertiesPropertySource("systemProperties", systemProperties));
            sources.addLast(new SystemEnvironmentPropertySource("systemEnvironment", new HashMap<>(environment)));
            Properties shipped = new Properties();
            shipped.setProperty(WORKING_DIR, String.valueOf(yamlOf(APPLICATION_YAML).get(WORKING_DIR)));
            sources.addLast(new PropertiesPropertySource("application.yaml", shipped));
            String read = new PropertySourcesPropertyResolver(sources).getProperty(property);
            return Optional.ofNullable(read).orElse(fallback);
        }

        private static List<String> linesOf(ByteArrayOutputStream stream) {
            return stream.toString(StandardCharsets.UTF_8).lines().filter(line -> !line.isBlank()).toList();
        }
    }

    /** A YAML file flattened to properties as Spring reads it, its placeholders left as written. */
    private static Map<Object, Object> yamlOf(Path file) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(file));
        yaml.afterPropertiesSet();
        return yaml.getObject();
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
