package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.algernon.vespera.TheShippedCopy;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.ConfigurableEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * The folder beside the jar (ADR-397): which folder it is (§1); a file missing there written from the jar's shipped
 * copy, and never over a file there, {@code application-local.yaml} never, two starts preparing one folder at once
 * both going on and leaving the shipped copy and no {@code .part} file, and each file written, as each {@code .new},
 * taking the mode a file this user creates in its folder takes (§3); a folder that cannot be written refusing
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

    /** How a file being written beside the jar ends, before it is moved into place (§3). */
    private static final String WHILE_IT_IS_WRITTEN = ".part";

    /** How many fresh folders two starts prepare at once, so that a clash between them is met rather than missed. */
    private static final int FOLDERS_TWO_STARTS_PREPARE_AT_ONCE = 50;

    /** The file of the build context the Docling image's build copies in and runs, as a user other than the owner. */
    private static final String THE_REPORT_SCRIPT = "docker/docling-serve/report_image.py";

    /** The name of the file this user creates in a folder, whose mode a file written there is compared with. */
    private static final String A_FILE_THIS_USER_CREATES = "a file this user creates";

    /** The folder the jar lies in, its name holding a space and a bang, which a code source's URL writes %20 and %21. */
    private static final String A_FOLDER_WITH_A_SPACE_AND_A_BANG = "a folder with a space and a bang!";

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
                    assertThat(new BesideTheJar(besideTheJar).writeNewWhereDifferent()).isEmpty();
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
    @Story("The configuration lies beside the jar")
    @DisplayName("Two starts preparing one folder at once both go on, and leave every file the jar's own copy, byte for byte, and no .part file")
    void twoStartsPreparingOneFolderAtOnceLeaveEveryFileTheShippedCopyAndNoPartFile() throws Exception {
        List<String> expected = TheShippedCopy.namesInTheRepository();
        List<String> refused = new ArrayList<>();
        List<String> said = new ArrayList<>();
        List<String> notTheJarsCopy = new ArrayList<>();
        List<Path> leftBehind = new ArrayList<>();
        ExecutorService twoStarts = Executors.newFixedThreadPool(2);
        try {
            for (int time = 1; time <= FOLDERS_TWO_STARTS_PREPARE_AT_ONCE; time++) {
                Path folder = Files.createDirectories(besideTheJar.resolve("folder " + time));
                // Each start counts itself in and waits for the other, so both prepare the folder at the same moment.
                CountDownLatch together = new CountDownLatch(2);
                Callable<List<String>> aStart = () -> {
                    together.countDown();
                    together.await();
                    return new BesideTheJar(folder).prepare();
                };
                List<Future<List<String>>> starts = List.of(twoStarts.submit(aStart), twoStarts.submit(aStart));
                for (Future<List<String>> start : starts) {
                    try {
                        for (String line : start.get(1, TimeUnit.MINUTES)) {
                            said.add(folder.getFileName() + ": " + line);
                        }
                    } catch (ExecutionException thrown) {
                        refused.add(folder.getFileName() + ": " + thrown.getCause());
                    } catch (TimeoutException neverEnded) {
                        refused.add(folder.getFileName() + ": a start that did not end within a minute");
                    }
                }
                for (String name : differingFromTheJarsCopy(folder, expected, "")) {
                    notTheJarsCopy.add(folder.getFileName() + ": " + name);
                }
                leftBehind.addAll(filesEndingIn(folder, WHILE_IT_IS_WRITTEN));
            }
        } finally {
            twoStarts.shutdownNow();
        }

        claim("in " + FOLDERS_TWO_STARTS_PREPARE_AT_ONCE + " fresh folders each prepared by two starts at once, neither"
                        + " start is refused",
                () -> assertThat(refused).as("the starts refused, by folder, with what they threw").isEmpty());
        claim("every file the jar keeps beside itself is its own copy, byte for byte, in every folder",
                () -> assertThat(notTheJarsCopy).as("the files missing or not the jar's own copy, by folder").isEmpty());
        claim("no file being written is left behind under a name ending " + WHILE_IT_IS_WRITTEN,
                () -> assertThat(leftBehind).as("the files left ending " + WHILE_IT_IS_WRITTEN).isEmpty());
        claim("and neither start finds a file that differs from the jar's own, so neither says a line",
                () -> assertThat(said).as("the lines said, by folder").isEmpty());
    }

    @Test
    @Story("The configuration lies beside the jar")
    @DisplayName("Every file written beside the jar, and every .new, takes the mode a file this user creates in that folder takes")
    void aFileWrittenBesideTheJarTakesTheModeOfAFileTheUserCreatesThere() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "this file system keeps no POSIX mode, as NTFS keeps none");
        List<String> expected = TheShippedCopy.namesInTheRepository();
        BesideTheJar folder = new BesideTheJar(besideTheJar);

        List<String> written = folder.writeWhatIsMissing();
        for (String name : written) {
            // Each file changed where it lies, which keeps its mode, so that each differs and has its .new written.
            String change = name.endsWith(".yaml") ? "\n" + ADDED_BY_THE_OPERATOR + ": added\n" : A_COMMENT_OF_THE_OPERATORS;
            Files.writeString(besideTheJar.resolve(name), change, StandardOpenOption.APPEND);
        }
        List<String> newFiles = folder.writeNewWhereDifferent().stream()
                .map(difference -> difference.file() + BesideTheJar.NEW_SUFFIX).toList();
        Map<Path, Set<PosixFilePermission>> theUsersMode = new HashMap<>();
        List<String> writtenInAnotherMode = inAnotherMode(written, theUsersMode);
        List<String> newInAnotherMode = inAnotherMode(newFiles, theUsersMode);
        Path theScript = besideTheJar.resolve(THE_REPORT_SCRIPT);
        Set<PosixFilePermission> readableByOthers = theUsersMode(theScript.getParent(), theUsersMode)
                .contains(PosixFilePermission.OTHERS_READ) ? Set.of(PosixFilePermission.OTHERS_READ) : Set.of();

        claim("every file the jar keeps beside itself was written, and each, once changed, has its .new written",
                () -> {
                    assertThat(expected).as("the files the jar keeps beside itself").contains(THE_REPORT_SCRIPT);
                    assertThat(written).containsExactlyElementsOf(expected);
                    assertThat(newFiles).containsExactlyElementsOf(
                            expected.stream().map(name -> name + BesideTheJar.NEW_SUFFIX).toList());
                });
        claim("each file written beside the jar has the mode a file this user creates in its folder has",
                () -> assertThat(writtenInAnotherMode).as("the files written in another mode, with both modes")
                        .isEmpty());
        claim("and so has each .new",
                () -> assertThat(newInAnotherMode).as("the .new files written in another mode, with both modes")
                        .isEmpty());
        claim("in particular " + THE_REPORT_SCRIPT + ", which the Docling image's build copies in, and its .new are"
                        + " readable by others wherever a file this user creates there is",
                () -> assertThat(List.of(theScript, besideTheJar.resolve(THE_REPORT_SCRIPT + BesideTheJar.NEW_SUFFIX)))
                        .allSatisfy(file -> assertThat(Files.getPosixFilePermissions(file))
                                .as("the mode of %s", besideTheJar.relativize(file))
                                .containsAll(readableByOthers)));
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

        List<BesideTheJar.Difference> differences = folder.writeNewWhereDifferent();

        claim("the three files that differ are named, in the order they are written, and the GPU file, whose one change"
                        + " is a comment, is not",
                () -> assertThat(differences).extracting(BesideTheJar.Difference::file)
                        .containsExactly("application.yaml", "compose.yaml", aFileOfTheBuildContext));
        claim("application.yaml is named with the key added, the key removed and the key changed, in the order of"
                        + " their names, and no value; compose.yaml with its service's image; the build context's file"
                        + " with no key, its bytes being what differs",
                () -> assertThat(differences).extracting(BesideTheJar.Difference::keys).containsExactly(
                        List.of(ADDED_BY_THE_OPERATOR, REMOVED_BY_THE_OPERATOR, CHANGED_BY_THE_OPERATOR),
                        List.of(imageKey),
                        List.of()));
        claim("each is said in one line, naming the file, its keys, and the file the jar's own is written to",
                () -> assertThat(differences).extracting(BesideTheJar.Difference::line).containsExactly(
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
    @Story("An upgrade says what differs and changes nothing")
    @DisplayName("A .new that cannot be written is said among the lines, naming it and why, and the start goes on with the operator's file")
    void aNewFileThatCannotBeWrittenIsSaidAmongTheLinesNamingItAndWhy() throws IOException {
        BesideTheJar folder = new BesideTheJar(besideTheJar);
        folder.writeWhatIsMissing();
        String application = TheOperatorsEdits.setting(TheOperatorsEdits.shipped("application.yaml"),
                CHANGED_BY_THE_OPERATOR, "7s");
        Files.writeString(besideTheJar.resolve("application.yaml"), application);
        Path inTheWay = Files.createDirectories(besideTheJar.resolve("application.yaml" + BesideTheJar.NEW_SUFFIX));
        Path theOperatorsFileInIt = Files.writeString(inTheWay.resolve("a file of the operator's"), "kept");

        List<String> lines = new ArrayList<>();
        Throwable refused = catchThrowable(() -> lines.addAll(folder.prepare()));

        claim("the start is not refused: the operator's file is the one in use whether or not the .new is written",
                () -> assertThat(refused).as("what prepare() threw").isNull());
        claim("one line, among the lines said once the logging is up, names application.yaml, the key that differs,"
                        + " the .new that cannot be written and why, on one line",
                () -> assertThat(lines).singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                        .startsWith("application.yaml beside the jar differs from this jar's own at "
                                + CHANGED_BY_THE_OPERATOR + "; this jar's cannot be written beside it as"
                                + " application.yaml.new (")
                        .endsWith(")")
                        .doesNotContain("\n")
                        .doesNotEndWith("()"));
        claim("and what stood in the way, and the operator's file, are as the operator left them",
                () -> {
                    assertThat(theOperatorsFileInIt).hasContent("kept");
                    assertThat(besideTheJar.resolve("application.yaml")).hasContent(application);
                });
    }

    @Test
    @Story("An upgrade says what differs and changes nothing")
    @DisplayName("An application.yaml beside the jar that does not load differs at no key: the jar's own is written beside it as .new and the line names no key")
    void aYamlFileThatDoesNotLoadDiffersAtNoKey() throws IOException {
        BesideTheJar folder = new BesideTheJar(besideTheJar);
        folder.writeWhatIsMissing();
        String doesNotLoad = "vespera: [a list the operator never closed\n";
        Files.writeString(besideTheJar.resolve("application.yaml"), doesNotLoad);

        List<BesideTheJar.Difference> differences = folder.writeNewWhereDifferent();

        claim("application.yaml alone is named, with no key, in the line of a file that differs at no key",
                () -> assertThat(differences).extracting(BesideTheJar.Difference::file, BesideTheJar.Difference::keys,
                        BesideTheJar.Difference::line).containsExactly(org.assertj.core.groups.Tuple.tuple(
                                "application.yaml", List.of(), "application.yaml beside the jar differs from this"
                                        + " jar's own; this jar's is written beside it as application.yaml.new")));
        claim("the jar's own copy is written beside it as application.yaml.new, byte for byte",
                () -> assertThat(differingFromTheJarsCopy(List.of("application.yaml"), BesideTheJar.NEW_SUFFIX))
                        .isEmpty());
        claim("and the file that does not load is left as the operator wrote it",
                () -> assertThat(besideTheJar.resolve("application.yaml")).hasContent(doesNotLoad));
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
    @DisplayName("A code source given as a URL is the folder its file lies in, a nested jar's that of its outer jar, and the current directory where the URL names no file")
    void aCodeSourceUrlThatNamesNoFileIsTheCurrentDirectory() throws IOException {
        Path jar = Files.writeString(besideTheJar.resolve("vespera.jar"), "");
        Path theJarsFolder = Files.createDirectories(besideTheJar.resolve(A_FOLDER_WITH_A_SPACE_AND_A_BANG));
        Path thePackagedJar = Files.writeString(theJarsFolder.resolve("vespera.jar"), "");
        // As Spring Boot's loader writes a jar's path in a URL: its raw path, a space %20, and a bang %21, so that no
        // bang of the path is taken for the one that ends the jar's name.
        String thePackagedJarsPath = thePackagedJar.toUri().getRawPath().replace("!", "%21");
        Path current = elsewhere;
        URL aJarsUrl = jar.toUri().toURL();
        URL theRuntimesImage = URI.create("jrt:/java.base").toURL();
        URL anOpaqueUrl = URI.create("mailto:nobody@example.invalid").toURL();
        URL theNestedClasses = asTheLoaderGivesIt("jar:nested:" + thePackagedJarsPath + "/!BOOT-INF/classes/!/");
        URL theJarsRoot = asTheLoaderGivesIt("jar:file:" + thePackagedJarsPath + "!/");

        claim("the packaged jar's path is written in its URLs with its space as %20 and its bang as %21",
                () -> assertThat(thePackagedJarsPath).contains("%20").contains("%21").doesNotContain(" ", "!"));
        claim("a file: URL of a jar is the folder the jar lies in",
                () -> assertThat(BesideTheJar.folderOf(aJarsUrl, current)).isEqualTo(besideTheJar));
        claim("the classes nested in the packaged jar, as Spring Boot's loader names their code source, are the folder"
                        + " the outer jar lies in, and not the current directory",
                () -> assertThat(BesideTheJar.folderOf(theNestedClasses, current))
                        .as("the folder of %s", theNestedClasses).isEqualTo(theJarsFolder));
        claim("and so is the root of the jar named by a jar:file: URL",
                () -> assertThat(BesideTheJar.folderOf(theJarsRoot, current))
                        .as("the folder of %s", theJarsRoot).isEqualTo(theJarsFolder));
        claim("a jrt: URL, the runtime's own image, is the current directory, and not the folder above it",
                () -> assertThat(BesideTheJar.folderOf(theRuntimesImage, current)).isEqualTo(current));
        claim("and so is an opaque URL, which names no path at all",
                () -> assertThat(BesideTheJar.folderOf(anOpaqueUrl, current)).isEqualTo(current));
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
    @DisplayName("A folder beside the jar whose name holds a comma is read as one folder: application.yaml there, the local settings file over it")
    void aFolderWhoseNameHoldsACommaIsReadAsOneFolder() throws IOException {
        Path withAComma = Files.createDirectories(besideTheJar.resolve("a,b"));
        theFilesOfThisTestIn(withAComma);

        List<String> read = readBySpring(withAComma, new String[0], Map.of(), Map.of());

        claim("the comma in the folder's name does not split the location: application.yaml there is read, and the"
                        + " local settings file there over it",
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
        theFilesOfThisTestIn(besideTheJar);
        Files.writeString(elsewhere.resolve("application.yaml"), "vespera:\n  a-key-of-this-test: named elsewhere\n");
    }

    /** In {@code folder}, an {@code application.yaml} naming two keys and an {@code application-local.yaml} naming the second. */
    private static void theFilesOfThisTestIn(Path folder) throws IOException {
        Files.writeString(folder.resolve("application.yaml"),
                "vespera:\n  a-key-of-this-test: beside the jar\n  another-key-of-this-test: beside the jar\n");
        Files.writeString(folder.resolve(THE_LOCAL_FILE), "vespera:\n  another-key-of-this-test: the local file\n");
    }

    /** The two keys of this test as Spring reads them for a start pointed at the folder beside the jar. */
    private List<String> readBySpring(String[] arguments, Map<String, Object> systemProperties,
            Map<String, Object> environmentVariables) {
        return readBySpring(besideTheJar, arguments, systemProperties, environmentVariables);
    }

    /** The two keys of this test as Spring reads them for a start pointed at {@code folder} as the folder beside the jar. */
    private static List<String> readBySpring(Path folder, String[] arguments, Map<String, Object> systemProperties,
            Map<String, Object> environmentVariables) {
        return SpringsOwnReading.read(new BesideTheJar(folder)::pointSpringAt, arguments,
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

    /**
     * The URL of {@code spec} as Spring Boot's loader gives a code source, its text kept as written. The JDK's own
     * handler of {@code jar:} refuses a {@code nested:} jar, whose handler only the packaged jar's loader installs, so
     * the URL is given a handler that keeps the text and opens nothing.
     */
    private static URL asTheLoaderGivesIt(String spec) throws IOException {
        URL url = URL.of(URI.create(spec), new URLStreamHandler() {
            @Override
            protected void parseURL(URL u, String text, int start, int limit) {
                setURL(u, u.getProtocol(), null, -1, null, null, text.substring(start, limit), null, null);
            }

            @Override
            protected URLConnection openConnection(URL u) {
                throw new UnsupportedOperationException("a code source of this test is never opened: " + u);
            }
        });
        claim("the URL " + spec + " keeps its text as written",
                () -> assertThat(url.toString()).isEqualTo(spec));
        return url;
    }

    /**
     * Of the files given, below the folder beside the jar, those whose mode is not that of a file this user creates in
     * the same folder, each with both modes.
     */
    private List<String> inAnotherMode(List<String> names, Map<Path, Set<PosixFilePermission>> theUsersMode)
            throws IOException {
        List<String> inAnotherMode = new ArrayList<>();
        for (String name : names) {
            Path file = besideTheJar.resolve(name);
            Set<PosixFilePermission> its = Files.getPosixFilePermissions(file);
            Set<PosixFilePermission> theUsers = theUsersMode(file.getParent(), theUsersMode);
            if (!its.equals(theUsers)) {
                inAnotherMode.add(name + " is " + PosixFilePermissions.toString(its) + " where a file this user"
                        + " creates is " + PosixFilePermissions.toString(theUsers));
            }
        }
        return inAnotherMode;
    }

    /** The mode of a file this user creates in {@code folder} with {@link Files#createFile}, created once per folder. */
    private static Set<PosixFilePermission> theUsersMode(Path folder, Map<Path, Set<PosixFilePermission>> theUsersMode) {
        return theUsersMode.computeIfAbsent(folder, inIt -> {
            try {
                return Files.getPosixFilePermissions(Files.createFile(inIt.resolve(A_FILE_THIS_USER_CREATES)));
            } catch (IOException uncreated) {
                throw new UncheckedIOException(uncreated);
            }
        });
    }

    /** The files below the folder beside the jar whose names end in {@code suffix}. */
    private List<Path> filesEndingIn(String suffix) throws IOException {
        return filesEndingIn(besideTheJar, suffix);
    }

    /** The files below {@code folder} whose names end in {@code suffix}. */
    private static List<Path> filesEndingIn(Path folder, String suffix) throws IOException {
        try (Stream<Path> paths = Files.walk(folder)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(suffix)).toList();
        }
    }

    /** The names, of those given, whose file beside the jar, its name followed by {@code suffix}, is not the jar's copy. */
    private List<String> differingFromTheJarsCopy(List<String> names, String suffix) throws IOException {
        return differingFromTheJarsCopy(besideTheJar, names, suffix);
    }

    /** The names, of those given, whose file in {@code folder}, its name followed by {@code suffix}, is not the jar's copy. */
    private static List<String> differingFromTheJarsCopy(Path folder, List<String> names, String suffix)
            throws IOException {
        List<String> differing = new ArrayList<>();
        for (String name : names) {
            Path file = folder.resolve(name + suffix);
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
