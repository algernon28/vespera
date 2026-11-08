package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
            "vespera/docling-serve-cuda:v1", "http://localhost:5002", "http://localhost:11435", "a-writing-model:14b",
            "/seeds/łódź");

    /** The deliverable root relative, so that it must be made absolute before it reaches the child. */
    private static final String RELATIVE_DELIVERABLE_ROOT = "out";

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
    @Story("The control panel passes its launch values to the jar")
    @DisplayName("Five values reach the child's environment under the jar's variable names, the deliverable root absolute, the empty ones not at all")
    void fiveValuesReachTheChildsEnvironmentUnderTheJarsVariableNames() {
        LaunchValues relative = new LaunchValues("/archive", "/work", RELATIVE_DELIVERABLE_ROOT, "image:v1",
                "http://docling", "http://ollama", "a-writing-model:14b", "/seeds");
        LaunchValues noImageNoModel = new LaunchValues("/archive", "/work", "", "", "http://docling", "", "", "");

        claim("each of the five is its variable, the deliverable root made absolute, and nothing of the corpus root,"
                        + " the working directory or the last seed folder",
                () -> assertThat(relative.toChildEnvironment()).isEqualTo(Map.of(
                        "VESPERA_DELIVERABLE_ROOT", Path.of(RELATIVE_DELIVERABLE_ROOT).toAbsolutePath().toString(),
                        "VESPERA_DOCLING_IMAGE", "image:v1",
                        "VESPERA_DOCLING_BASE_URL", "http://docling",
                        "SPRING_AI_OLLAMA_BASE_URL", "http://ollama",
                        "VESPERA_LABEL_MODEL", "a-writing-model:14b")));
        claim("an empty value is not passed at all, so the jar's own default applies",
                () -> assertThat(noImageNoModel.toChildEnvironment())
                        .isEqualTo(Map.of("VESPERA_DOCLING_BASE_URL", "http://docling")));
        claim("and with every value empty, nothing is passed",
                () -> assertThat(LaunchValues.none().toChildEnvironment()).isEmpty());
    }

    private static String refusalOf(Path folder) {
        return "the control panel neither reads nor keeps its launch values in " + folder
                + ", which is not a folder only this user can write in";
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
}
