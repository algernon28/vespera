package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How the token's file is read and written (ADR-323 §2): never throwing, each line handed to a consumer, and no line
 * holding the token.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-323", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
class ControlPanelTokenTest {

    private static final String USER = System.getProperty("user.name");

    /** A token as a start draws one: 43 characters of base64url. */
    private static final String TOKEN = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFG";

    @TempDir
    Path folder;

    @Test
    @Story("The control panel keeps its token while a tab comes back holding it")
    @DisplayName("A token written is read back, and a token file that cannot be read, bytes that are no token or a folder in its place, gives no token and one line naming the file and not the token")
    void aTokenFileThatCannotBeReadIsReplacedWithOneLine() throws IOException {
        Path file = ControlPanelToken.fileIn(folder, USER);
        List<String> written = new ArrayList<>();
        ControlPanelToken.write(file, USER, TOKEN, written::add);
        List<String> readBack = new ArrayList<>();
        Optional<String> token = ControlPanelToken.read(file, USER, readBack::add);

        String notAToken = "this is not a token, but it is long enough to be mistaken for one at a glance";
        Files.writeString(file, notAToken, StandardCharsets.UTF_8);
        List<String> junk = new ArrayList<>();
        Optional<String> fromJunk = ControlPanelToken.read(file, USER, junk::add);

        Files.delete(file);
        Files.createDirectory(file);
        List<String> inPlace = new ArrayList<>();
        Optional<String> fromFolder = ControlPanelToken.read(file, USER, inPlace::add);

        claim("a token written to a folder that did not exist is read back whole, with nothing said",
                () -> assertThat(List.of(token, written, readBack)).containsExactly(Optional.of(TOKEN), List.of(),
                        List.of()));
        claim("bytes that are not a token give none, and one line naming the file and not what it holds",
                () -> {
                    assertThat(fromJunk).isEmpty();
                    assertThat(junk).hasSize(1).allSatisfy(line -> assertThat(line)
                            .startsWith("the control panel could not read its token at " + file + ", and draws a new one: ")
                            .doesNotContain(notAToken));
                });
        claim("a folder in the file's place gives none, and one line naming the file",
                () -> {
                    assertThat(fromFolder).isEmpty();
                    assertThat(inPlace).hasSize(1).allSatisfy(line -> assertThat(line)
                            .startsWith("the control panel could not read its token at " + file));
                });
    }

    @Test
    @Story("The control panel keeps its token while a tab comes back holding it")
    @DisplayName("Where POSIX permissions apply, a folder others can write in is neither read nor written, and one line names the folder")
    void aFolderOthersCanWriteInIsNeitherReadNorWritten() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "this file system has no POSIX permissions");
        Path file = ControlPanelToken.fileIn(folder, USER);
        Path own = file.getParent();
        Files.createDirectories(own);
        Files.writeString(file, TOKEN, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(own, PosixFilePermissions.fromString("rwxrwxrwx"));
        List<String> reading = new ArrayList<>();
        Optional<String> token = ControlPanelToken.read(file, USER, reading::add);
        List<String> writing = new ArrayList<>();
        ControlPanelToken.write(file, USER, "z".repeat(43), writing::add);
        String stillHeld = Files.readString(file, StandardCharsets.UTF_8);
        String line = "the control panel neither reads nor keeps its token in " + own
                + ", which is not a folder only this user can write in";

        claim("no token is read from it, and one line names the folder", () -> assertThat(List.of(token, reading))
                .containsExactly(Optional.empty(), List.of(line)));
        claim("none is written to it, the file keeping what it held, and the same line is said",
                () -> assertThat(List.of(stillHeld, writing)).containsExactly(TOKEN, List.of(line)));
    }
}
