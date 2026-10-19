package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.algernon.vespera.pipeline.VesperaCommand;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * The launch values the control panel remembers (ADR-292): which they are and the jar's property each is keyed by
 * (§1), the one UTF-8 file that holds them, written whole (§2), in a folder of the user's own under the temp directory
 * that is neither read nor written where it is not (§3), the five that reach the child's environment (§4), what an
 * absent or unreadable file and an empty field come to (§5), and the last seed folder that reaches the child nowhere
 * (§6).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("564")
@Link(name = "ADR-292", url = Adr.THE_CONTROL_PANEL_REMEMBERS_ITS_LAUNCH_VALUES, type = "adr")
class LaunchValuesTest {

    /** The user running the tests, who owns every folder a test makes, as the operator owns the panel's. */
    private static final String USER = System.getProperty("user.name");

    private static final String FILE_NAME = "launch-values.properties";

    /** Every launch value named, and the last seed folder, two of them holding characters outside Latin-1. */
    private static final LaunchValues EVERY_VALUE = new LaunchValues("/archive/łódź", "/work/漢字", "/out",
            "vespera/docling-serve-cuda:v1", "http://localhost:5002", "http://localhost:11435", "qwen3:14b",
            "/seeds/łódź");

    /** The deliverable root relative, so that it must be made absolute before it reaches the child. */
    private static final String RELATIVE_DELIVERABLE_ROOT = "out";

    private static final String RUN_NEEDS_BOTH = "Run needs the corpus root and the working directory";

    private static final String RUN_NEEDS_THE_CORPUS_ROOT = "Run needs the corpus root";

    private static final String RUN_NEEDS_THE_WORKING_DIRECTORY = "Run needs the working directory";

    private static final String LABEL_NEEDS_THE_WORKING_DIRECTORY = "Label needs the working directory";

    private static final String NO_DOCLING_IMAGE = "no Docling image is named, so the jar expects the sidecar to run"
            + " its own default image, and stops before converting anything where it runs another";

    @Test
    @Story("The control panel remembers its launch values")
    @DisplayName("The file is launch-values.properties in a folder named for the user under the temp directory, the name made safe")
    void theFileIsInAFolderNamedForTheUserUnderTheTempDirectory(@TempDir Path temp) {
        claim("for a plain user name, the file is vespera-control-panel-<name>/launch-values.properties under the temp"
                        + " directory",
                () -> assertThat(LaunchValues.fileIn(temp, "a.b_c-d9"))
                        .isEqualTo(temp.resolve("vespera-control-panel-a.b_c-d9").resolve(FILE_NAME)));
        claim("every character outside letters A to Z, digits, dot, underscore and hyphen is written as an underscore",
                () -> assertThat(LaunchValues.fileIn(temp, "DOMAIN\\Jo Doe/élan"))
                        .isEqualTo(temp.resolve("vespera-control-panel-DOMAIN_Jo_Doe__lan").resolve(FILE_NAME)));
        claim("and an empty user name is written as one underscore",
                () -> assertThat(LaunchValues.fileIn(temp, ""))
                        .isEqualTo(temp.resolve("vespera-control-panel-_").resolve(FILE_NAME)));
    }

    @Test
    @Story("The control panel remembers its launch values")
    @DisplayName("A value that is null, empty or only whitespace is the same as none, and any other is kept as typed")
    void aBlankValueIsTheSameAsNone() {
        LaunchValues blank = new LaunchValues(null, "", "   ", "\t", " \n", null, "", null);
        LaunchValues spaced = new LaunchValues(" /archive ", "", "", "", "", "", "", "");

        claim("null, empty and whitespace-only values are each the empty value, as if none were given",
                () -> assertThat(blank).isEqualTo(LaunchValues.none()));
        claim("and none is every value empty",
                () -> assertThat(LaunchValues.none())
                        .isEqualTo(new LaunchValues("", "", "", "", "", "", "", "")));
        claim("a value that is not blank is kept as typed, its surrounding spaces included",
                () -> assertThat(spaced.corpusRoot()).isEqualTo(" /archive "));
    }

    @Test
    @Story("The control panel remembers its launch values")
    @DisplayName("What is stored is read back whole, in UTF-8, characters outside Latin-1 written as themselves")
    void whatIsStoredIsReadBackWholeInUtf8(@TempDir Path temp) throws IOException {
        Path file = LaunchValues.fileIn(temp, USER);
        List<String> said = new ArrayList<>();
        EVERY_VALUE.store(file, USER, said::add);

        claim("storing said nothing",
                () -> assertThat(said).isEmpty());
        claim("loading gives back the same eight values",
                () -> assertThat(LaunchValues.load(file, USER, said::add)).isEqualTo(EVERY_VALUE));
        claim("the file reads as UTF-8, holding the characters themselves and no \\u escape",
                () -> assertThat(Files.readString(file, UTF_8)).contains("łódź", "漢字").doesNotContain("\\u"));
    }

    @Test
    @Story("The control panel remembers its launch values")
    @DisplayName("The file holds the jar's property name for each value not blank, the last seed folder's key, and nothing else")
    void theFileHoldsTheJarsPropertyNamesForTheValuesNotBlankAndNothingElse(@TempDir Path temp) throws IOException {
        Path file = LaunchValues.fileIn(temp, USER);
        new LaunchValues("/archive", "/work", "", "image:v1", "http://docling", "http://ollama", " ", "/seeds")
                .store(file, USER, line -> { });

        claim("the file holds each value not blank under the jar's own property, and the last seed folder under"
                        + " last-seed-folder; the blank deliverable root and label model are not written",
                () -> assertThat(propertiesOf(file)).isEqualTo(Map.of(
                        "vespera.corpus-root", "/archive",
                        "vespera.working-dir", "/work",
                        "vespera.docling.image", "image:v1",
                        "vespera.docling.base-url", "http://docling",
                        "spring.ai.ollama.base-url", "http://ollama",
                        "last-seed-folder", "/seeds")));
    }

    @Test
    @Story("The control panel remembers its launch values")
    @DisplayName("A second store replaces the first one's values whole and leaves no other file in the folder")
    void storingReplacesTheFileWholeAndLeavesNothingBesideIt(@TempDir Path temp) throws IOException {
        Path file = LaunchValues.fileIn(temp, USER);
        LaunchValues second = new LaunchValues("/other-archive", "/other-work", "", "", "", "", "", "");
        EVERY_VALUE.store(file, USER, line -> { });
        second.store(file, USER, line -> { });

        claim("the values loaded are the second store's, none of the first's left among them",
                () -> assertThat(LaunchValues.load(file, USER, line -> { })).isEqualTo(second));
        claim("and the folder holds the one file, no temporary file beside it",
                () -> assertThat(namesIn(file.getParent())).containsExactly(FILE_NAME));
    }

    @Test
    @Story("The control panel remembers its launch values")
    @DisplayName("A key the control panel does not know is ignored as it is read and not written back")
    void aKeyTheControlPanelDoesNotKnowIsIgnoredAndNotWrittenBack(@TempDir Path temp) throws IOException {
        Path file = LaunchValues.fileIn(temp, USER);
        ownFolder(file.getParent());
        Files.writeString(file, "vespera.label.model=qwen3:14b\nvespera.progress-interval=1s\n"
                + "spring.datasource.url=jdbc:sqlite:elsewhere\nsomething=else\n", UTF_8);

        LaunchValues loaded = LaunchValues.load(file, USER, line -> { });
        loaded.store(file, USER, line -> { });

        claim("only the label model is read, the progress interval, the datasource and the unknown key ignored",
                () -> assertThat(loaded).isEqualTo(new LaunchValues("", "", "", "", "", "", "qwen3:14b", "")));
        claim("and stored again, the file holds the label model alone",
                () -> assertThat(propertiesOf(file)).isEqualTo(Map.of("vespera.label.model", "qwen3:14b")));
    }

    @Test
    @Story("The control panel starts with every field empty when it remembers nothing")
    @DisplayName("A file that is missing, in a folder that is missing or not, starts every field empty and says nothing")
    void aMissingFileStartsEveryFieldEmptyAndSaysNothing(@TempDir Path temp) throws IOException {
        Path file = LaunchValues.fileIn(temp, USER);
        List<String> said = new ArrayList<>();

        LaunchValues withNoFolder = LaunchValues.load(file, USER, said::add);
        ownFolder(file.getParent());
        LaunchValues withAFolder = LaunchValues.load(file, USER, said::add);

        claim("with no folder, every field starts empty",
                () -> assertThat(withNoFolder).isEqualTo(LaunchValues.none()));
        claim("with the folder and no file, every field starts empty",
                () -> assertThat(withAFolder).isEqualTo(LaunchValues.none()));
        claim("and neither says a line",
                () -> assertThat(said).isEmpty());
    }

    @Test
    @Story("The control panel starts with every field empty when it remembers nothing")
    @DisplayName("A file that cannot be read starts every field empty, with one line naming the file")
    void aFileThatCannotBeReadStartsEveryFieldEmptyWithOneLine(@TempDir Path temp) throws IOException {
        Path notUtf8 = fileInAFolderOfItsOwn(temp, "not-utf-8");
        Files.write(notUtf8, new byte[] {'v', 'e', 's', 'p', 'e', 'r', 'a', '.', 'l', 'a', 'b', 'e', 'l', '.', 'm', 'o',
                'd', 'e', 'l', '=', (byte) 0xC3, (byte) 0x28});
        Path badEscape = fileInAFolderOfItsOwn(temp, "bad-escape");
        Files.writeString(badEscape, "vespera.label.model=\\uZZZZ\n", UTF_8);
        Path aFolder = fileInAFolderOfItsOwn(temp, "a-folder");
        Files.createDirectory(aFolder);

        for (Path file : List.of(notUtf8, badEscape, aFolder)) {
            List<String> said = new ArrayList<>();
            LaunchValues loaded = LaunchValues.load(file, USER, said::add);

            claim("a file that cannot be read, " + file.getParent().getFileName() + ", starts every field empty",
                    () -> assertThat(loaded).isEqualTo(LaunchValues.none()));
            claim("and says one line naming the file, that every field starts empty, and why",
                    () -> assertThat(said).singleElement().asString().startsWith(
                            "the control panel could not read its launch values at " + file
                                    + ", and every field starts empty: "));
        }
    }

    @Test
    @Story("The control panel keeps its launch values from other users")
    @DisplayName("Where POSIX permissions apply, the folder is created rwx------ and the file rw-------")
    void theFolderAndTheFileAreTheUsersAloneOnPosix(@TempDir Path temp) throws IOException {
        assumePosix();
        Path file = LaunchValues.fileIn(temp, USER);
        EVERY_VALUE.store(file, USER, line -> { });

        claim("the folder the store created can be read, written and entered by its owner alone",
                () -> assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file.getParent())))
                        .isEqualTo("rwx------"));
        claim("and the file can be read and written by its owner alone",
                () -> assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
                        .isEqualTo("rw-------"));
    }

    @Test
    @Story("The control panel keeps its launch values from other users")
    @DisplayName("Where POSIX permissions apply, a folder others can write in is neither read nor written, with one line naming it")
    void aFolderOthersCanWriteInIsNeitherReadNorWritten(@TempDir Path temp) throws IOException {
        assumePosix();
        Path file = LaunchValues.fileIn(temp, USER);
        Path folder = Files.createDirectory(file.getParent());
        Files.writeString(file, "vespera.corpus-root=/planted\n", UTF_8);
        Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwxrwxrwx"));
        String refusal = refusalOf(folder);
        List<String> saidOnLoad = new ArrayList<>();
        List<String> saidOnStore = new ArrayList<>();

        LaunchValues loaded = LaunchValues.load(file, owner(folder), saidOnLoad::add);
        EVERY_VALUE.store(file, owner(folder), saidOnStore::add);

        claim("the folder is refused, with the line naming it",
                () -> assertThat(LaunchValues.refusalOfFolder(folder, owner(folder))).contains(refusal));
        claim("loading reads nothing of the file there, every field empty, and says the one line",
                () -> {
                    assertThat(loaded).isEqualTo(LaunchValues.none());
                    assertThat(saidOnLoad).containsExactly(refusal);
                });
        claim("storing writes nothing there, the file as it was and nothing beside it, and says the one line",
                () -> {
                    assertThat(Files.readString(file, UTF_8)).isEqualTo("vespera.corpus-root=/planted\n");
                    assertThat(namesIn(folder)).containsExactly(FILE_NAME);
                    assertThat(saidOnStore).containsExactly(refusal);
                });
    }

    @Test
    @Story("The control panel keeps its launch values from other users")
    @DisplayName("Where POSIX permissions apply, a folder another user owns is refused, and the owner's own folder is not")
    void aFolderAnotherUserOwnsIsNeitherReadNorWritten(@TempDir Path temp) throws IOException {
        assumePosix();
        Path folder = ownFolder(LaunchValues.fileIn(temp, USER).getParent());
        Path aFile = Files.createFile(temp.resolve("not-a-folder"));

        claim("the folder, owner-only, is accepted for the user who owns it",
                () -> assertThat(LaunchValues.refusalOfFolder(folder, owner(folder))).isEmpty());
        claim("and refused for any other user's name, with the line naming it",
                () -> assertThat(LaunchValues.refusalOfFolder(folder, owner(folder) + "-someone-else"))
                        .contains(refusalOf(folder)));
        claim("a file where the folder should be is refused too",
                () -> assertThat(LaunchValues.refusalOfFolder(aFile, owner(aFile))).contains(refusalOf(aFile)));
        claim("and a folder that does not exist is not refused, since the store creates it",
                () -> assertThat(LaunchValues.refusalOfFolder(temp.resolve("absent"), USER)).isEmpty());
    }

    @Test
    @Story("The control panel starts with every field empty when it remembers nothing")
    @DisplayName("A store that cannot write the file says one line naming it, and throws nothing")
    void whatCannotBeKeptIsSaidAndNotThrown(@TempDir Path temp) throws IOException {
        Path file = LaunchValues.fileIn(temp, USER);
        ownFolder(file.getParent());
        Files.createDirectory(file);
        Files.createFile(file.resolve("in-the-way"));
        List<String> said = new ArrayList<>();

        claim("storing over a folder that is not empty, where the file should be, throws nothing",
                () -> assertThatCode(() -> EVERY_VALUE.store(file, USER, said::add)).doesNotThrowAnyException());
        claim("and says one line naming the file and why it could not be kept",
                () -> assertThat(said).singleElement().asString()
                        .startsWith("the control panel could not keep its launch values at " + file + ": "));
    }

    @Test
    @Story("The control panel passes its launch values to the jar")
    @DisplayName("Five values reach the child's environment under the jar's variable names, the deliverable root absolute, the empty ones not at all")
    void fiveValuesReachTheChildsEnvironmentUnderTheJarsVariableNames() {
        LaunchValues relative = new LaunchValues("/archive", "/work", RELATIVE_DELIVERABLE_ROOT, "image:v1",
                "http://docling", "http://ollama", "qwen3:14b", "/seeds");
        LaunchValues noImageNoModel = new LaunchValues("/archive", "/work", "", "", "http://docling", "", "", "");

        claim("each of the five is its variable, the deliverable root made absolute, and nothing of the corpus root,"
                        + " the working directory or the last seed folder",
                () -> assertThat(relative.toChildEnvironment()).isEqualTo(Map.of(
                        "VESPERA_DELIVERABLE_ROOT", Path.of(RELATIVE_DELIVERABLE_ROOT).toAbsolutePath().toString(),
                        "VESPERA_DOCLING_IMAGE", "image:v1",
                        "VESPERA_DOCLING_BASE_URL", "http://docling",
                        "SPRING_AI_OLLAMA_BASE_URL", "http://ollama",
                        "VESPERA_LABEL_MODEL", "qwen3:14b")));
        claim("an empty value is not passed at all, so the jar's own default applies",
                () -> assertThat(noImageNoModel.toChildEnvironment())
                        .isEqualTo(Map.of("VESPERA_DOCLING_BASE_URL", "http://docling")));
        claim("and with every value empty, nothing is passed",
                () -> assertThat(LaunchValues.none().toChildEnvironment()).isEmpty());
    }

    @Test
    @Story("The control panel passes its launch values to the jar")
    @DisplayName("Each variable is read by Spring's relaxed binding as the property the jar reads")
    void eachVariableIsReadByTheJarAsItsProperty() {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new SystemEnvironmentPropertySource("the child's environment",
                new HashMap<>(EVERY_VALUE.toChildEnvironment())));
        PropertySourcesPropertyResolver jar = new PropertySourcesPropertyResolver(sources);

        claim("the deliverable root, the Docling image, Docling's and Ollama's addresses and the label model each"
                        + " resolve, under the property the jar reads, to the value passed",
                () -> {
                    assertThat(jar.getProperty(VesperaCommand.Run.DELIVERABLE_ROOT_PROPERTY))
                            .isEqualTo(Path.of(EVERY_VALUE.deliverableRoot()).toAbsolutePath().toString());
                    assertThat(jar.getProperty("vespera.docling.image")).isEqualTo(EVERY_VALUE.doclingImage());
                    assertThat(jar.getProperty("vespera.docling.base-url")).isEqualTo(EVERY_VALUE.doclingBaseUrl());
                    assertThat(jar.getProperty("spring.ai.ollama.base-url")).isEqualTo(EVERY_VALUE.ollamaBaseUrl());
                    assertThat(jar.getProperty("vespera.label.model")).isEqualTo(EVERY_VALUE.labelModel());
                });
    }

    @Test
    @Story("The control panel refuses what it cannot start")
    @DisplayName("Run is refused without the corpus root or the working directory, and not with both")
    void runIsRefusedWithoutTheCorpusRootOrTheWorkingDirectory() {
        claim("with neither, Run needs both",
                () -> assertThat(withRootAndDirectory(EVERY_VALUE, "", "").refusalToRun()).contains(RUN_NEEDS_BOTH));
        claim("with no corpus root, Run needs the corpus root",
                () -> assertThat(withRootAndDirectory(EVERY_VALUE, "", "/work").refusalToRun())
                        .contains(RUN_NEEDS_THE_CORPUS_ROOT));
        claim("with no working directory, Run needs the working directory",
                () -> assertThat(withRootAndDirectory(EVERY_VALUE, "/archive", "").refusalToRun())
                        .contains(RUN_NEEDS_THE_WORKING_DIRECTORY));
        claim("with both, and every other value empty, Run is not refused",
                () -> assertThat(withRootAndDirectory(LaunchValues.none(), "/archive", "/work").refusalToRun())
                        .isEmpty());
    }

    @Test
    @Story("The control panel refuses what it cannot start")
    @DisplayName("Label is refused without the working directory, whatever the corpus root, and not with it")
    void labelIsRefusedWithoutTheWorkingDirectory() {
        claim("with no working directory, Label needs it, a corpus root or none",
                () -> {
                    assertThat(withRootAndDirectory(EVERY_VALUE, "/archive", "").refusalToLabel())
                            .contains(LABEL_NEEDS_THE_WORKING_DIRECTORY);
                    assertThat(withRootAndDirectory(EVERY_VALUE, "", "").refusalToLabel())
                            .contains(LABEL_NEEDS_THE_WORKING_DIRECTORY);
                });
        claim("with the working directory and no corpus root, Label is not refused",
                () -> assertThat(withRootAndDirectory(LaunchValues.none(), "", "/work").refusalToLabel()).isEmpty());
    }

    @Test
    @Story("The control panel refuses what it cannot start")
    @DisplayName("An empty Docling image is warned of before Run, and no other empty value is")
    void anEmptyDoclingImageIsWarnedOfAndNothingElse() {
        LaunchValues onlyTheTwo = withRootAndDirectory(LaunchValues.none(), "/archive", "/work");
        LaunchValues onlyTheTwoAndAnImage = new LaunchValues("/archive", "/work", "", "image:v1", "", "", "", "");

        claim("with no Docling image, Run carries the one warning",
                () -> assertThat(onlyTheTwo.warningsToRun()).containsExactly(NO_DOCLING_IMAGE));
        claim("with one, and every other value but the two empty, it carries none",
                () -> assertThat(onlyTheTwoAndAnImage.warningsToRun()).isEmpty());
        claim("and with every value named, none",
                () -> assertThat(EVERY_VALUE.warningsToRun()).isEmpty());
    }

    private static String refusalOf(Path folder) {
        return "the control panel neither reads nor keeps its launch values in " + folder
                + ", which is not a folder only this user can write in";
    }

    private static Path fileInAFolderOfItsOwn(Path temp, String name) throws IOException {
        return ownFolder(temp.resolve(name)).resolve(FILE_NAME);
    }

    private static String owner(Path path) throws IOException {
        return Files.getOwner(path).getName();
    }

    private static void assumePosix() {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "the file system has no POSIX permissions");
    }

    private static List<String> namesIn(Path folder) throws IOException {
        try (Stream<Path> listed = Files.list(folder)) {
            return listed.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static Map<String, String> propertiesOf(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, UTF_8)) {
            properties.load(reader);
        }
        Map<String, String> read = new HashMap<>();
        properties.stringPropertyNames().forEach(key -> read.put(key, properties.getProperty(key)));
        return read;
    }

    /** The values given, with the corpus root and the working directory replaced. */
    private static LaunchValues withRootAndDirectory(LaunchValues values, String corpusRoot, String workingDirectory) {
        return new LaunchValues(corpusRoot, workingDirectory, values.deliverableRoot(), values.doclingImage(),
                values.doclingBaseUrl(), values.ollamaBaseUrl(), values.labelModel(), values.lastSeedFolder());
    }

    /**
     * A folder created owner-only where POSIX permissions apply, as the store creates its own, so that no umask makes
     * it one others can write in.
     */
    private static Path ownFolder(Path folder) throws IOException {
        Files.createDirectories(folder);
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwx------"));
        }
        return folder;
    }
}
