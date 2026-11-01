package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which command the control panel's opener starts: a window of a Chromium browser, with no address bar, where one is
 * found, and otherwise the system's own command for an address, as it stands (ADR-331 §1, keeping ADR-325 §7). It is read
 * off the one function that chooses, handed the system's name, its environment and a check of what exists, with no
 * display, no disk and no process.
 *
 * <p>{@code SystemBrowserOpener.command} is held as it stands by {@code SystemBrowserOpenerTest} and by the test that
 * compares it with the jar's. The paths are built with {@link Path#of} as the function builds them, so each system's
 * table is read the same on the Windows and the Linux job.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-331", url = Adr.THE_CONTROL_PANEL_OPENS_A_WINDOW_OF_ITS_OWN_AND_ITS_PAGES_ARE_A_COCKPIT, type = "adr")
class TheControlPanelOpensAWindowOfItsOwnTest {

    /** The address the control panel prints: its root at the fixed port. */
    private static final URI ADDRESS = URI.create("http://127.0.0.1:24291/");

    /** What a Chromium browser is given to open the address in a window with no address bar. */
    private static final String AS_A_WINDOW = "--app=" + ADDRESS;

    private static final String WINDOWS = "Windows 11";

    private static final String MAC = "Mac OS X";

    private static final String LINUX = "Linux";

    private static final String PROGRAM_FILES_X86 = "C:\\Program Files (x86)";

    private static final String PROGRAM_FILES = "C:\\Program Files";

    private static final String LOCAL_APP_DATA = "C:\\Users\\someone\\AppData\\Local";

    /** Windows's three variables that name where a browser is installed. */
    private static final Map<String, String> ON_WINDOWS = Map.of("ProgramFiles(x86)", PROGRAM_FILES_X86, "ProgramFiles",
            PROGRAM_FILES, "LOCALAPPDATA", LOCAL_APP_DATA);

    /** A display, and two folders on the path, in that order. */
    private static final Map<String, String> ON_LINUX = Map.of("DISPLAY", ":0", "PATH", "/usr/local/bin:/usr/bin");

    /** Begins as an absolute folder on Windows does, and holds a character {@code Path.of} refuses on every system. */
    private static final String A_FOLDER_THE_SYSTEM_CANNOT_HOLD = "C:\\Program\0Files";

    private static final Predicate<Path> NOTHING_EXISTS = path -> false;

    private static final Predicate<Path> ANYTHING_EXISTS = path -> true;

    @Test
    @Story("The control panel opens a window of its own")
    @DisplayName("On Windows the first of Edge and Chrome found, in the order of the two program folders and then Chrome in the user's own, is opened as a window")
    void onWindowsTheFirstBrowserFoundIsOpenedAsAWindowInTheRecordsOrder() {
        List<Path> inOrder = List.of(edgeUnder(PROGRAM_FILES_X86), chromeUnder(PROGRAM_FILES_X86),
                edgeUnder(PROGRAM_FILES), chromeUnder(PROGRAM_FILES), chromeUnder(LOCAL_APP_DATA));
        List<Optional<List<String>>> expected = new ArrayList<>();
        List<Optional<List<String>>> chosen = new ArrayList<>();
        for (int first = 0; first < inOrder.size(); first++) {
            List<Path> present = inOrder.subList(first, inOrder.size());
            expected.add(Optional.of(List.of(inOrder.get(first).toString(), AS_A_WINDOW)));
            chosen.add(windowCommand(WINDOWS, ON_WINDOWS, present::contains));
        }

        claim("with each browser and those after it installed, the one that comes first is chosen, and it is handed"
                        + " the address as one argument that opens a window with no address bar",
                () -> assertThat(chosen).isEqualTo(expected));
    }

    @Test
    @Story("The control panel opens a window of its own")
    @DisplayName("On macOS an installed Edge, or else Chrome, is opened as a window through open")
    void onMacOsAnInstalledBrowserIsOpenedAsAWindowThroughOpen() {
        Path edge = Path.of("/Applications/Microsoft Edge.app");
        Path chrome = Path.of("/Applications/Google Chrome.app");

        claim("with both installed, Edge is asked through open for a new window of it, the address as one argument",
                () -> assertThat(windowCommand(MAC, Map.of(), List.of(edge, chrome)::contains))
                        .contains(List.of("open", "-na", "Microsoft Edge", "--args", AS_A_WINDOW)));
        claim("with Chrome alone installed, Chrome is",
                () -> assertThat(windowCommand(MAC, Map.of(), List.of(chrome)::contains))
                        .contains(List.of("open", "-na", "Google Chrome", "--args", AS_A_WINDOW)));
    }

    @Test
    @Story("The control panel opens a window of its own")
    @DisplayName("On any other system with a display, the first of chromium, chromium-browser, google-chrome and microsoft-edge found on the path is opened as a window")
    void onAnyOtherSystemTheFirstBrowserOnThePathIsOpenedAsAWindow() {
        List<String> names = List.of("chromium", "chromium-browser", "google-chrome", "microsoft-edge");
        List<Optional<List<String>>> expected = new ArrayList<>();
        List<Optional<List<String>>> chosen = new ArrayList<>();
        for (int first = 0; first < names.size(); first++) {
            Path found = Path.of("/usr/bin", names.get(first));
            List<Path> present = names.subList(first, names.size()).stream()
                    .map(name -> Path.of("/usr/bin", name)).toList();
            expected.add(Optional.of(List.of(found.toString(), AS_A_WINDOW)));
            chosen.add(windowCommand(LINUX, ON_LINUX, present::contains));
        }
        Path inTheFirstFolder = Path.of("/usr/local/bin", "google-chrome");
        List<Path> inBoth = List.of(inTheFirstFolder, Path.of("/usr/bin", "google-chrome"));

        claim("with each name and those after it on the path, the one that comes first is chosen, by the path it was"
                        + " found at, and handed the address as one argument",
                () -> assertThat(chosen).isEqualTo(expected));
        claim("a name found in both folders of the path is taken from the one the path names first, and the name"
                        + " counts before the folder",
                () -> {
                    assertThat(windowCommand(LINUX, ON_LINUX, inBoth::contains))
                            .contains(List.of(inTheFirstFolder.toString(), AS_A_WINDOW));
                    assertThat(windowCommand(LINUX, ON_LINUX,
                            List.of(inTheFirstFolder, Path.of("/usr/bin", "chromium"))::contains))
                            .contains(List.of(Path.of("/usr/bin", "chromium").toString(), AS_A_WINDOW));
                });
    }

    @Test
    @Story("The control panel opens a window of its own")
    @DisplayName("Where no such browser is found, each system's own command is chosen, as it stands")
    void whereNoBrowserIsFoundTheSystemsOwnCommandIsChosen() {
        claim("on Windows, with nothing installed, the shell's own handler for an address",
                () -> assertThat(windowCommand(WINDOWS, ON_WINDOWS, NOTHING_EXISTS))
                        .isEqualTo(SystemBrowserOpener.command(ADDRESS, WINDOWS, ON_WINDOWS))
                        .contains(List.of("rundll32", "url.dll,FileProtocolHandler", ADDRESS.toString())));
        claim("on macOS, open with the address alone",
                () -> assertThat(windowCommand(MAC, Map.of(), NOTHING_EXISTS))
                        .contains(List.of("open", ADDRESS.toString())));
        claim("on Linux with a display, xdg-open",
                () -> assertThat(windowCommand(LINUX, ON_LINUX, NOTHING_EXISTS))
                        .contains(List.of("xdg-open", ADDRESS.toString())));
        claim("a variable that is absent or blank names no folder to look in, though the check here says everything"
                        + " exists",
                () -> {
                    assertThat(windowCommand(WINDOWS, Map.of(), ANYTHING_EXISTS))
                            .contains(List.of("rundll32", "url.dll,FileProtocolHandler", ADDRESS.toString()));
                    assertThat(windowCommand(WINDOWS,
                            Map.of("ProgramFiles(x86)", " ", "ProgramFiles", "", "LOCALAPPDATA", " "), ANYTHING_EXISTS))
                            .contains(List.of("rundll32", "url.dll,FileProtocolHandler", ADDRESS.toString()));
                    assertThat(windowCommand(LINUX, Map.of("DISPLAY", ":0"), ANYTHING_EXISTS))
                            .contains(List.of("xdg-open", ADDRESS.toString()));
                    assertThat(windowCommand(LINUX, Map.of("DISPLAY", ":0", "PATH", " "), ANYTHING_EXISTS))
                            .contains(List.of("xdg-open", ADDRESS.toString()));
                });
    }

    /**
     * A relative folder is read against wherever the control panel was started, so a {@code chromium} lying there would
     * be started. Each system's folders are told by their text, on the Windows and the Linux job alike.
     */
    @Test
    @Story("The control panel opens a window of its own")
    @DisplayName("Nothing is looked for under a relative folder: of the path's folders and Windows's three variables, only one that is absolute is looked in, though the check here says everything exists")
    void nothingIsLookedForUnderARelativeFolder() {
        claim("relative folders named first on the path are passed over for the absolute one after them",
                () -> assertThat(windowCommand(LINUX, Map.of("DISPLAY", ":0", "PATH", ".:bin:/usr/bin"), ANYTHING_EXISTS))
                        .contains(List.of(Path.of("/usr/bin", "chromium").toString(), AS_A_WINDOW)));
        claim("a path of relative folders and empty elements alone offers no browser",
                () -> assertThat(windowCommand(LINUX, Map.of("DISPLAY", ":0", "PATH", ".:bin::"), ANYTHING_EXISTS))
                        .contains(List.of("xdg-open", ADDRESS.toString())));
        claim("on Windows a relative program folder is passed over for the absolute one after it",
                () -> assertThat(windowCommand(WINDOWS, Map.of("ProgramFiles(x86)", "Programs", "ProgramFiles",
                        PROGRAM_FILES), ANYTHING_EXISTS))
                        .contains(List.of(edgeUnder(PROGRAM_FILES).toString(), AS_A_WINDOW)));
        claim("and with all three relative, a drive with no separator among them, no browser is offered",
                () -> assertThat(windowCommand(WINDOWS, Map.of("ProgramFiles(x86)", "Programs", "ProgramFiles",
                        "C:relative", "LOCALAPPDATA", "."), ANYTHING_EXISTS))
                        .contains(List.of("rundll32", "url.dll,FileProtocolHandler", ADDRESS.toString())));
        claim("a network share begins with no drive letter, so it names no folder either, and no host is asked",
                () -> assertThat(windowCommand(WINDOWS, Map.of("ProgramFiles", "\\\\host\\share"), ANYTHING_EXISTS))
                        .contains(List.of("rundll32", "url.dll,FileProtocolHandler", ADDRESS.toString())));
    }

    /**
     * Windows holds one variable of a name whatever its case, and {@code System.getenv()} hands the opener a map that
     * does not: started from Git Bash it holds {@code PROGRAMFILES} and no {@code ProgramFiles}. {@code PATH} is a name
     * of the other systems, where case tells two variables apart.
     */
    @Test
    @Story("The control panel opens a window of its own")
    @DisplayName("On Windows the three variables are read whatever their case, the record's order standing; on any other system the path's name is read exact")
    void onWindowsTheThreeVariablesAreReadWhateverTheirCase() {
        Path chromeInProgramFiles = chromeUnder(PROGRAM_FILES);
        List<Path> inBoth = List.of(edgeUnder(PROGRAM_FILES_X86), edgeUnder(PROGRAM_FILES));

        claim("each of the three, in a case other than the record's, names its folder",
                () -> {
                    assertThat(windowCommand(WINDOWS, Map.of("PROGRAMFILES", PROGRAM_FILES),
                            List.of(chromeInProgramFiles)::contains))
                            .contains(List.of(chromeInProgramFiles.toString(), AS_A_WINDOW));
                    assertThat(windowCommand(WINDOWS, Map.of("programfiles(x86)", PROGRAM_FILES_X86),
                            List.of(edgeUnder(PROGRAM_FILES_X86))::contains))
                            .contains(List.of(edgeUnder(PROGRAM_FILES_X86).toString(), AS_A_WINDOW));
                    assertThat(windowCommand(WINDOWS, Map.of("LocalAppData", LOCAL_APP_DATA),
                            List.of(chromeUnder(LOCAL_APP_DATA))::contains))
                            .contains(List.of(chromeUnder(LOCAL_APP_DATA).toString(), AS_A_WINDOW));
                });
        claim("the case changes nothing of the order: the folder of ProgramFiles(x86) still comes first",
                () -> assertThat(windowCommand(WINDOWS, Map.of("PROGRAMFILES(X86)", PROGRAM_FILES_X86, "PROGRAMFILES",
                        PROGRAM_FILES), inBoth::contains))
                        .contains(List.of(edgeUnder(PROGRAM_FILES_X86).toString(), AS_A_WINDOW)));
        claim("nor of what names no folder: a relative one in another case is passed over",
                () -> assertThat(windowCommand(WINDOWS, Map.of("PROGRAMFILES", "Programs"), ANYTHING_EXISTS))
                        .contains(List.of("rundll32", "url.dll,FileProtocolHandler", ADDRESS.toString())));
        claim("and off Windows a variable called Path is not PATH",
                () -> assertThat(windowCommand(LINUX, Map.of("DISPLAY", ":0", "Path", "/usr/bin"), ANYTHING_EXISTS))
                        .contains(List.of("xdg-open", ADDRESS.toString())));
    }

    /**
     * A variable holding a character {@code Path.of} refuses throws nothing out of the choice: the start's opener would
     * otherwise open nothing, on a machine with a default browser. {@code NUL} is refused on the Windows and the Linux job
     * alike; a double quote, which the Windows job alone refuses, is the same case.
     */
    @Test
    @Story("The control panel opens a window of its own")
    @DisplayName("On Windows a program folder variable the system cannot hold as a path finds no browser there, the others are read as ever, and with none found the default browser still opens")
    void aVariableTheSystemCannotHoldAsAPathFindsNoBrowserThere() {
        String theSystemsOwn = "rundll32";
        for (String variable : List.of("ProgramFiles(x86)", "ProgramFiles", "LOCALAPPDATA")) {
            claim(variable + " holding it alone, though the check here says everything exists: the system's own command",
                    () -> assertThat(windowCommand(WINDOWS, Map.of(variable, A_FOLDER_THE_SYSTEM_CANNOT_HOLD), ANYTHING_EXISTS))
                            .contains(List.of(theSystemsOwn, "url.dll,FileProtocolHandler", ADDRESS.toString())));
        }
        claim("all three holding it: the system's own command",
                () -> assertThat(windowCommand(WINDOWS, Map.of("ProgramFiles(x86)", A_FOLDER_THE_SYSTEM_CANNOT_HOLD,
                        "ProgramFiles", A_FOLDER_THE_SYSTEM_CANNOT_HOLD, "LOCALAPPDATA", A_FOLDER_THE_SYSTEM_CANNOT_HOLD),
                        ANYTHING_EXISTS))
                        .contains(List.of(theSystemsOwn, "url.dll,FileProtocolHandler", ADDRESS.toString())));
        claim("ProgramFiles(x86) holding it is passed over for the folder of ProgramFiles after it",
                () -> assertThat(windowCommand(WINDOWS, Map.of("ProgramFiles(x86)", A_FOLDER_THE_SYSTEM_CANNOT_HOLD,
                        "ProgramFiles", PROGRAM_FILES), ANYTHING_EXISTS))
                        .contains(List.of(edgeUnder(PROGRAM_FILES).toString(), AS_A_WINDOW)));
        claim("ProgramFiles holding it is passed over for Chrome in the user's own folder after it",
                () -> assertThat(windowCommand(WINDOWS, Map.of("ProgramFiles", A_FOLDER_THE_SYSTEM_CANNOT_HOLD,
                        "LOCALAPPDATA", LOCAL_APP_DATA), ANYTHING_EXISTS))
                        .contains(List.of(chromeUnder(LOCAL_APP_DATA).toString(), AS_A_WINDOW)));
        claim("and a folder before it that holds a browser is still the one chosen, the later variable never mattering",
                () -> assertThat(windowCommand(WINDOWS, Map.of("ProgramFiles(x86)", PROGRAM_FILES_X86,
                        "LOCALAPPDATA", A_FOLDER_THE_SYSTEM_CANNOT_HOLD), ANYTHING_EXISTS))
                        .contains(List.of(edgeUnder(PROGRAM_FILES_X86).toString(), AS_A_WINDOW)));
    }

    @Test
    @Story("Nothing is opened where nobody could see it")
    @DisplayName("Under CI on any system, and on Linux with no display, nothing is opened though a browser is there")
    void underCiOrWithNoDisplayNothingIsOpenedThoughABrowserIsThere() {
        Map<String, String> underCi = Map.of("CI", "true", "DISPLAY", ":0", "PATH", "/usr/bin", "ProgramFiles",
                PROGRAM_FILES);

        for (String system : List.of(WINDOWS, MAC, LINUX)) {
            claim("on " + system + " with CI set, no command is chosen",
                    () -> assertThat(windowCommand(system, underCi, ANYTHING_EXISTS)).isEmpty());
        }
        claim("on Linux with neither display variable set, or set blank, no command is chosen",
                () -> {
                    assertThat(windowCommand(LINUX, Map.of("PATH", "/usr/bin"), ANYTHING_EXISTS)).isEmpty();
                    assertThat(windowCommand(LINUX, Map.of("PATH", "/usr/bin", "DISPLAY", " ", "WAYLAND_DISPLAY", ""),
                            ANYTHING_EXISTS)).isEmpty();
                });
    }

    /**
     * The tests above hold the function; this holds that the opener goes through it, since {@code open} starts a process
     * and so is run by no test. Its compiled calls are read, as the tests of the server's classes read theirs.
     */
    @Test
    @Story("The control panel opens a window of its own")
    @DisplayName("The opener starts what that one function chooses, asking the system for its name, its whole environment and what exists")
    void theOpenerStartsWhatTheFunctionChooses() {
        JavaClass opener = new ClassFileImporter().importClass(SystemBrowserOpener.class);
        List<String> called = opener.getMethod("open", URI.class).getMethodCallsFromSelf().stream()
                .map(JavaMethodCall::getName)
                .toList();
        List<String> calledByFullName = opener.getMethod("open", URI.class).getMethodCallsFromSelf().stream()
                .map(call -> call.getTarget().getFullName())
                .toList();

        claim("open calls the function that chooses a window, and does not ask the system's own command itself",
                () -> assertThat(called).contains("windowCommand").doesNotContain("command"));
        claim("it asks the system for a property, the system's name among them, and for its whole environment",
                () -> assertThat(calledByFullName).contains("java.lang.System.getProperty(java.lang.String)",
                        "java.lang.System.getenv()"));
        claim("and what it hands the function as what exists is the file system's own check",
                () -> assertThat(calledByFullName).anyMatch(call -> call.startsWith("java.nio.file.Files.exists(")));
    }

    private static Path edgeUnder(String folder) {
        return Path.of(folder, "Microsoft", "Edge", "Application", "msedge.exe");
    }

    private static Path chromeUnder(String folder) {
        return Path.of(folder, "Google", "Chrome", "Application", "chrome.exe");
    }

    /** {@code SystemBrowserOpener.windowCommand} for {@link #ADDRESS}, the one address of these tests. */
    private static Optional<List<String>> windowCommand(String system, Map<String, String> environment,
            Predicate<Path> exists) {
        return SystemBrowserOpener.windowCommand(ADDRESS, system, environment, exists);
    }
}
