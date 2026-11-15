package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.TheShippedCopy;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.ConfigurableEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * The folder beside the jar (ADR-397): which folder it is (§1); a file missing there written from the jar's shipped
 * copy, and never over a file there, {@code application-local.yaml} never (§3); a folder that cannot be written refusing
 * the start (§3); Spring reading {@code application.yaml} and {@code application-local.yaml} there, and a location
 * named on the command line, as a system property or in the environment read in their place (§4); each file that
 * differs from its shipped copy named by its keys, the shipped copy written beside it as {@code <name>.new} and the
 * file left as it is (§5); and the compose files read where they lie there (§6).
 *
 * <p>Every folder beside the jar is a temporary folder of the test's own, never the folder of a jar or the repository;
 * the repository is read only for the names of the files its build context holds. Spring is started with no system
 * property and no environment variable of the machine running the test.
 */
@Epic("Operation")
@Feature("Shipped configuration")
@Issue("836")
@Link(name = "ADR-397", url = Adr.THE_YAML_CONFIGURATION_LIES_BESIDE_THE_JAR, type = "adr")
class BesideTheJarTest {

    /** The file beside the jar that is the operator's alone, and has no shipped copy. */
    private static final String THE_LOCAL_FILE = "application-local.yaml";

    /** What the operator's own files hold, in the test of a file already there. */
    private static final String THE_OPERATORS_OWN = "# the operator's own\nvespera:\n  a-key-of-the-operators: its own value\n";

    /** A key the operator adds to {@code application.yaml}, at its top. */
    private static final String ADDED_BY_THE_OPERATOR = "a-key-of-the-operators";

    /** A key the operator changes in {@code application.yaml}. */
    private static final String CHANGED_BY_THE_OPERATOR = "vespera.progress-interval";

    /** A key the operator removes from {@code application.yaml}. */
    private static final String REMOVED_BY_THE_OPERATOR = Presets.TIMEOUTS_IN_A_ROW;

    /** The image the operator names for a service of {@code compose.yaml}. */
    private static final String THE_OPERATORS_IMAGE = "vespera/an-image-of-the-operators:1";

    /** A line that changes nothing a YAML file says, and that changes a file of the build context's bytes. */
    private static final String A_COMMENT_OF_THE_OPERATORS = "\n# a line of the operator's own\n";

    /** What each property the Spring tests read is said to be where nothing names it. */
    private static final String NOTHING = "(nothing)";

    @TempDir
    Path besideTheJar;

    @TempDir
    Path elsewhere;

    @Test
    @Story("The configuration lies beside the jar")
    @DisplayName("A file missing beside the jar is written there from the jar's own copy, byte for byte, and the local settings file never is")
    void aMissingFileIsWrittenFromTheJarsCopyAndTheLocalFileNever() throws IOException {
        List<String> expected = TheShippedCopy.namesInTheRepository();

        List<String> written = new BesideTheJar(besideTheJar).writeWhatIsMissing();

        claim("the jar keeps its copies in the resource folder the tests read them from",
                () -> assertThat(BesideTheJar.RESOURCE_FOLDER).isEqualTo(TheShippedCopy.FOLDER));
        claim("every file the jar keeps beside itself was written, in the order the three YAML files, then the build"
                        + " context's files by name",
                () -> assertThat(written).containsExactlyElementsOf(expected));
        claim("each is the jar's own copy, byte for byte", () -> assertThat(differingFromTheJarsCopy(expected)).isEmpty());
        claim("the local settings file was not written", () -> assertThat(besideTheJar.resolve(THE_LOCAL_FILE))
                .doesNotExist());
        claim("a second start finds nothing missing, and writes nothing", () -> assertThat(
                new BesideTheJar(besideTheJar).writeWhatIsMissing()).isEmpty());
        claim("and finds nothing that differs from the jar's own, so writes no .new file",
                () -> {
                    assertThat(new BesideTheJar(besideTheJar).writeNewWhereDrifted()).isEmpty();
                    assertThat(filesEndingIn(BesideTheJar.NEW_SUFFIX)).isEmpty();
                });
    }

    @Test
    @Story("The configuration lies beside the jar")
    @DisplayName("A file already beside the jar is never written over, whatever it holds, and only the missing ones are written")
    void aFileAlreadyBesideTheJarIsNeverWrittenOver() throws IOException {
        List<String> expected = TheShippedCopy.namesInTheRepository();
        String aFileOfTheBuildContext = expected.get(TheShippedCopy.YAML_FILES.size());
        List<String> theOperators = List.of("application.yaml", "compose.yaml", aFileOfTheBuildContext);
        for (String name : theOperators) {
            Files.createDirectories(besideTheJar.resolve(name).getParent());
            Files.writeString(besideTheJar.resolve(name), THE_OPERATORS_OWN);
        }
        List<String> theRest = new ArrayList<>(expected);
        theRest.removeAll(theOperators);

        List<String> written = new BesideTheJar(besideTheJar).writeWhatIsMissing();

        claim("only the files that were missing were written, in their order",
                () -> assertThat(written).containsExactlyElementsOf(theRest));
        claim("they are the jar's own copies", () -> assertThat(differingFromTheJarsCopy(theRest)).isEmpty());
        claim("and the operator's " + theOperators.size() + " files hold what the operator wrote, untouched",
                () -> assertThat(theOperators).allSatisfy(name -> assertThat(besideTheJar.resolve(name))
                        .hasContent(THE_OPERATORS_OWN)));
    }

    @Test
    @Story("An upgrade says what differs and changes nothing")
    @DisplayName("A file that differs from the jar's own is named with the keys that differ, the jar's own is written beside it as .new, and the file is left as it is")
    void aFileThatDiffersIsNamedByItsKeysAndTheJarsCopyIsWrittenBesideIt() throws IOException {
        BesideTheJar folder = new BesideTheJar(besideTheJar);
        folder.writeWhatIsMissing();
        String application = TheOperatorsEdits.without(TheOperatorsEdits.setting(
                TheOperatorsEdits.shipped("application.yaml"), CHANGED_BY_THE_OPERATOR, "7s"), REMOVED_BY_THE_OPERATOR)
                + "\n" + ADDED_BY_THE_OPERATOR + ": added\n";
        String shippedCompose = TheOperatorsEdits.shipped("compose.yaml");
        Map.Entry<String, String> service = aServiceWhoseImageIsWrittenOnce(shippedCompose);
        String compose = shippedCompose.replace(service.getValue(), THE_OPERATORS_IMAGE);
        String gpu = TheOperatorsEdits.shipped("compose.gpu.yaml") + A_COMMENT_OF_THE_OPERATORS;
        String aFileOfTheBuildContext = TheShippedCopy.namesInTheRepository().get(TheShippedCopy.YAML_FILES.size());
        byte[] buildContextFile = (new String(theJarsCopy(aFileOfTheBuildContext), StandardCharsets.UTF_8)
                + A_COMMENT_OF_THE_OPERATORS).getBytes(StandardCharsets.UTF_8);
        Files.writeString(besideTheJar.resolve("application.yaml"), application);
        Files.writeString(besideTheJar.resolve("compose.yaml"), compose);
        Files.writeString(besideTheJar.resolve("compose.gpu.yaml"), gpu);
        Files.write(besideTheJar.resolve(aFileOfTheBuildContext), buildContextFile);
        String imageKey = "services." + service.getKey() + ".image";

        List<BesideTheJar.Drift> drift = folder.writeNewWhereDrifted();

        claim("the three files that differ are named, in the order they are written, and the GPU file, whose one change"
                        + " is a comment, is not",
                () -> assertThat(drift).extracting(BesideTheJar.Drift::file)
                        .containsExactly("application.yaml", "compose.yaml", aFileOfTheBuildContext));
        claim("application.yaml is named with the key added, the key removed and the key changed, in the order of"
                        + " their names, and no value; compose.yaml with its service's image; the build context's file"
                        + " with no key, its bytes being what differs",
                () -> assertThat(drift).extracting(BesideTheJar.Drift::keys).containsExactly(
                        List.of(ADDED_BY_THE_OPERATOR, REMOVED_BY_THE_OPERATOR, CHANGED_BY_THE_OPERATOR),
                        List.of(imageKey),
                        List.of()));
        claim("each is said in one line, naming the file, its keys, and the file the jar's own is written to",
                () -> assertThat(drift).extracting(BesideTheJar.Drift::line).containsExactly(
                        "application.yaml beside the jar differs from this jar's own at " + ADDED_BY_THE_OPERATOR
                                + ", " + REMOVED_BY_THE_OPERATOR + ", " + CHANGED_BY_THE_OPERATOR
                                + "; this jar's is written beside it as application.yaml.new",
                        "compose.yaml beside the jar differs from this jar's own at " + imageKey
                                + "; this jar's is written beside it as compose.yaml.new",
                        aFileOfTheBuildContext + " beside the jar differs from this jar's own; this jar's is written"
                                + " beside it as " + aFileOfTheBuildContext + ".new"));
        claim("beside each, the jar's own copy is written as <name>.new, byte for byte, and none beside the GPU file",
                () -> {
                    assertThat(differingFromTheJarsCopy(List.of("application.yaml", "compose.yaml",
                            aFileOfTheBuildContext), BesideTheJar.NEW_SUFFIX)).isEmpty();
                    assertThat(besideTheJar.resolve("compose.gpu.yaml" + BesideTheJar.NEW_SUFFIX)).doesNotExist();
                });
        claim("and every file the operator changed holds what the operator wrote",
                () -> {
                    assertThat(besideTheJar.resolve("application.yaml")).hasContent(application);
                    assertThat(besideTheJar.resolve("compose.yaml")).hasContent(compose);
                    assertThat(besideTheJar.resolve("compose.gpu.yaml")).hasContent(gpu);
                    assertThat(besideTheJar.resolve(aFileOfTheBuildContext)).hasBinaryContent(buildContextFile);
                });
    }

    @Test
    @Story("The configuration lies beside the jar")
    @DisplayName("A folder beside the jar that cannot be written refuses the start in one line naming the file and the folder")
    void aFolderThatCannotBeWrittenRefusesTheStartNamingIt() throws IOException {
        Path notAFolder = besideTheJar.resolve("a file where the folder should be");
        Files.writeString(notAFolder, "");

        Throwable refused = catchThrowable(() -> new BesideTheJar(notAFolder).writeWhatIsMissing());

        claim("the start is refused, in the line naming the first file, the folder and how to put it right",
                () -> assertThat(refused)
                        .isInstanceOf(BesideTheJarUnwritableException.class)
                        .message()
                        .startsWith("Vespera cannot write application.yaml in the folder beside the jar, " + notAFolder
                                + " (")
                        .endsWith("); make that folder one this user can write, and start Vespera again.")
                        .doesNotContain("\n"));
    }

    @Test
    @Story("The configuration lies beside the jar")
    @DisplayName("The folder beside the jar is the folder the jar lies in, the folder above compiled classes, and the current directory where the code source is neither")
    void theFolderIsTheJarsTheOneAboveTheClassesOrTheCurrentDirectory() throws IOException {
        Path jar = Files.writeString(besideTheJar.resolve("vespera.jar"), "");
        Path target = Files.createDirectories(besideTheJar.resolve("target"));
        Path classes = Files.createDirectories(target.resolve("classes"));
        Path current = elsewhere;

        claim("a jar, as java -jar loads the application from, is the folder it lies in",
                () -> assertThat(BesideTheJar.folderOf(jar, current)).isEqualTo(besideTheJar));
        claim("a folder of compiled classes, as an IDE's run or a test loads it from, is the folder above it, where the"
                        + " build writes the jar, and never the classes themselves, which are on the class path",
                () -> assertThat(BesideTheJar.folderOf(classes, current)).isEqualTo(target));
        claim("a code source that is not there is the current directory",
                () -> assertThat(BesideTheJar.folderOf(besideTheJar.resolve("not-there.jar"), current))
                        .isEqualTo(current));
    }

    @Test
    @Story("The configuration lies beside the jar")
    @DisplayName("Spring reads application.yaml beside the jar, with the local settings file over it")
    void springReadsTheConfigurationAndTheLocalFileBesideTheJar() throws IOException {
        theFilesOfThisTestBesideTheJarAndElsewhere();

        List<String> read = readBySpring(new String[0], Map.of(), Map.of());

        claim("a value application.yaml beside the jar names is read, and one the local settings file names over it"
                        + " is the local file's",
                () -> assertThat(read).containsExactly("beside the jar", "the local file"));
    }

    @Test
    @Story("The configuration lies beside the jar")
    @DisplayName("A location named on the command line, as a system property or in the environment is read in place of the folder beside the jar")
    void aLocationNamedElsewhereIsReadInPlaceOfTheFolderBesideTheJar() throws IOException {
        theFilesOfThisTestBesideTheJarAndElsewhere();
        String location = "optional:" + elsewhere.toUri();
        List<String> namedElsewhere = List.of("named elsewhere", NOTHING);

        claim("named on the command line, it is the one read, and neither file beside the jar is",
                () -> assertThat(readBySpring(new String[] {"--spring.config.location=" + location}, Map.of(), Map.of()))
                        .containsExactlyElementsOf(namedElsewhere));
        claim("so it is as a system property",
                () -> assertThat(readBySpring(new String[0], Map.of("spring.config.location", location), Map.of()))
                        .containsExactlyElementsOf(namedElsewhere));
        claim("and as the environment variable SPRING_CONFIG_LOCATION",
                () -> assertThat(readBySpring(new String[0], Map.of(), Map.of("SPRING_CONFIG_LOCATION", location)))
                        .containsExactlyElementsOf(namedElsewhere));
    }

    @Test
    @Story("The configuration lies beside the jar")
    @DisplayName("The compose files are read where they lie beside the jar, an image the operator wrote there being the one read, and none is deleted")
    void theComposeFilesAreReadBesideTheJar() throws IOException {
        BesideTheJar folder = new BesideTheJar(besideTheJar);
        folder.writeWhatIsMissing();
        Path composeFile = besideTheJar.resolve("compose.yaml");
        claim("the compose files were written beside the jar from the jar's own copies",
                () -> assertThat(List.of(composeFile, besideTheJar.resolve("compose.gpu.yaml")))
                        .allSatisfy(file -> assertThat(file).isRegularFile()));
        String shippedImage = ComposeFiles.in(besideTheJar).cpuImage();
        Files.writeString(composeFile, Files.readString(composeFile).replace(shippedImage, THE_OPERATORS_IMAGE));

        String read;
        Path readIn;
        try (ComposeFiles files = folder.composeFiles()) {
            read = files.cpuImage();
            readIn = files.folder();
        }

        claim("the files are read in the folder beside the jar, and not written out anywhere else",
                () -> assertThat(readIn).isEqualTo(besideTheJar));
        claim("the converter's image is the one the operator wrote there", () -> assertThat(read)
                .isEqualTo(THE_OPERATORS_IMAGE));
        claim("and once read, both compose files are still there", () -> assertThat(List.of(composeFile,
                besideTheJar.resolve("compose.gpu.yaml"))).allSatisfy(file -> assertThat(file).isRegularFile()));
    }

    /**
     * Beside the jar, an {@code application.yaml} naming two keys and an {@code application-local.yaml} naming the
     * second; elsewhere, an {@code application.yaml} naming the first alone.
     */
    private void theFilesOfThisTestBesideTheJarAndElsewhere() throws IOException {
        Files.writeString(besideTheJar.resolve("application.yaml"),
                "vespera:\n  a-key-of-this-test: beside the jar\n  another-key-of-this-test: beside the jar\n");
        Files.writeString(besideTheJar.resolve(THE_LOCAL_FILE), "vespera:\n  another-key-of-this-test: the local file\n");
        Files.writeString(elsewhere.resolve("application.yaml"), "vespera:\n  a-key-of-this-test: named elsewhere\n");
    }

    /** The two keys of this test as Spring reads them for a start pointed at the folder beside the jar. */
    private List<String> readBySpring(String[] arguments, Map<String, Object> systemProperties,
            Map<String, Object> environmentVariables) {
        return SpringsOwnReading.read(new BesideTheJar(besideTheJar)::pointSpringAt, arguments,
                new HashMap<>(systemProperties), new HashMap<>(environmentVariables), BesideTheJarTest::theTwoKeys);
    }

    private static List<String> theTwoKeys(ConfigurableEnvironment environment) {
        return Arrays.asList(
                environment.getProperty("vespera.a-key-of-this-test", NOTHING),
                environment.getProperty("vespera.another-key-of-this-test", NOTHING));
    }

    /** The first service of {@code compose} whose image's name is written once in its text, and that image. */
    private static Map.Entry<String, String> aServiceWhoseImageIsWrittenOnce(String compose) {
        JsonNode services = YAMLMapper.builder().build().readTree(compose).path("services");
        for (Map.Entry<String, JsonNode> service : services.properties()) {
            JsonNode image = service.getValue().path("image");
            if (image.isString() && compose.indexOf(image.asString()) == compose.lastIndexOf(image.asString())) {
                return Map.entry(service.getKey(), image.asString());
            }
        }
        claim("compose.yaml has a service whose image is written once, which the operator changes",
                () -> assertThat(services.isEmpty()).as("compose.yaml has no service").isFalse());
        throw new IllegalStateException("no service of compose.yaml has an image written once");
    }

    /** The files below the folder beside the jar whose names end in {@code suffix}. */
    private List<Path> filesEndingIn(String suffix) throws IOException {
        try (Stream<Path> paths = Files.walk(besideTheJar)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(suffix)).toList();
        }
    }

    /** The names, of those given, whose file beside the jar, its name followed by {@code suffix}, is not the jar's copy. */
    private List<String> differingFromTheJarsCopy(List<String> names, String suffix) throws IOException {
        List<String> differing = new ArrayList<>();
        for (String name : names) {
            Path file = besideTheJar.resolve(name + suffix);
            if (!Files.isRegularFile(file) || !Arrays.equals(Files.readAllBytes(file), theJarsCopy(name))) {
                differing.add(name + suffix);
            }
        }
        return differing;
    }

    private List<String> differingFromTheJarsCopy(List<String> names) throws IOException {
        return differingFromTheJarsCopy(names, "");
    }

    private static byte[] theJarsCopy(String name) throws IOException {
        try (InputStream in = TheShippedCopy.of(name).getInputStream()) {
            return in.readAllBytes();
        }
    }
}
