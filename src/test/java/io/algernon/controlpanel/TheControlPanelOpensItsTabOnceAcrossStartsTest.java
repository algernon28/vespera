package io.algernon.controlpanel;

import static io.algernon.controlpanel.ControlPanelAsStarted.BOUND;
import static io.algernon.controlpanel.ControlPanelAsStarted.IN_YOUR_BROWSER;
import static io.algernon.controlpanel.ControlPanelAsStarted.OPEN;
import static io.algernon.controlpanel.ControlPanelAsStarted.WILL_START;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.controlpanel.ControlPanelAsStarted.Line;
import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel as the operator starts it, twice where the test needs a second start (ADR-325 §1 to §4): the port
 * it listens at, the address of its root with no token, the start that waits for a tab of its last start, the tab that
 * comes back to it, and the address a start no tab comes back to prints.
 *
 * <p>Each start is {@link ControlPanelAsStarted}: {@code java -jar} on the control panel's own classes, with {@code
 * CI=1}, so that nothing opens a browser, and a temporary directory of the test's own in place of the user's. The test
 * that needs a tab plays it: a {@link PanelBrowser} that opened the first start's address, polling {@code /alive} on
 * {@link RawHttp}, as the control panel's script does, and reloading once it is answered with another mark.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-325", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
class TheControlPanelOpensItsTabOnceAcrossStartsTest {

    /** The port the control panel listens at where the environment names none. */
    private static final int THE_FIXED_PORT = 24291;

    /** A start at a port of its own says it waits for a tab, before it waits. */
    private static final String WAITING = "the control panel waits up to 5 seconds for a tab it opened before";

    /** A start a tab came back to says so, at its own port, in place of an address. */
    private static final String CAME_BACK =
            "a tab it opened before came back to http://127.0.0.1:%d/, so the control panel opens no other";

    /** What a start says on standard error where the port it would listen at is taken. */
    private static final String TAKEN = "the port %d is taken, so the control panel listens at a port the system"
            + " picked, and no tab it opened before can come back to it";

    /** What a start says on standard error where the variable names no port. */
    private static final String REFUSED =
            "VESPERA_CONTROL_PANEL_PORT is %s, and the control panel listens only at a port from 0 to 65535";

    /** Three values that name no port: a word, one past the highest port, and a negative number. */
    private static final List<String> NO_PORT = List.of("twenty-four", "65536", "-1");

    /** The control panel's exit code where it refuses to start. */
    private static final int REFUSED_EXIT = 1;

    /** How long a start at a port of its own waits for a tab to come back. */
    private static final Duration TAB_BOUND = Duration.ofSeconds(5);

    /** What reading two lines through a pipe may take off the time between them, at most. */
    private static final Duration READING_SLACK = Duration.ofSeconds(1);

    /** How long the tab waits after a poll has settled before it polls again, as the script does. */
    private static final Duration POLL_EVERY = Duration.ofSeconds(1);

    /** How long the tab polls for a later start before the test gives up on it. */
    private static final Duration POLLING_BOUND = Duration.ofSeconds(20);

    /** How long the test watches for an address after the line saying a tab came back. */
    private static final Duration NOTHING_LATER = Duration.ofSeconds(2);

    /** The address line of the root at any port, with no query. */
    private static final String ANY_ADDRESS = Pattern.quote(OPEN) + "[0-9]{1,5}" + Pattern.quote("/" + IN_YOUR_BROWSER);

    /** A start's mark: 22 characters of base64url, 16 random bytes with no padding. */
    private static final String MARK = "[A-Za-z0-9_-]{22}";

    /** The one file the folder of the user's own may hold at a start, the launch values, and no other. */
    private static final List<String> LAUNCH_VALUES = List.of("launch-values.properties");

    @TempDir
    Path folder;

    @Test
    @Story("The control panel listens at a port that stays the same from one start to the next")
    @DisplayName("Started with VESPERA_CONTROL_PANEL_PORT naming a free port, the control panel listens at that port")
    void itListensAtThePortTheVariableNames() throws IOException, InterruptedException {
        int port = ControlPanelAsStarted.freePort();
        String address;
        try (ControlPanelAsStarted controlPanel = ControlPanelAsStarted.start(folder, String.valueOf(port))) {
            address = controlPanel.addressLine();
        }

        claim("the address it prints is the root on 127.0.0.1 at the port the variable names, with no query",
                () -> assertThat(address).matches(addressAt(port)));
    }

    @Test
    @Story("The control panel listens at a port that stays the same from one start to the next")
    @DisplayName("Started with no port named, the control panel listens at 24291")
    void withNoPortNamedItListensAt24291() throws IOException, InterruptedException {
        assumeTrue(ControlPanelAsStarted.isFree(THE_FIXED_PORT),
                "port " + THE_FIXED_PORT + " is taken on this machine, so a start there would fall back");
        String address;
        try (ControlPanelAsStarted controlPanel = ControlPanelAsStarted.start(folder, null)) {
            address = controlPanel.addressLine();
        }

        claim("the address it prints is the root on 127.0.0.1 at port " + THE_FIXED_PORT + ", with no query",
                () -> assertThat(address).matches(addressAt(THE_FIXED_PORT)));
    }

    @Test
    @Story("The control panel listens at a port that stays the same from one start to the next")
    @DisplayName("A VESPERA_CONTROL_PANEL_PORT that names no port, a word, 65536 or -1, is refused with one line, and the control panel exits 1")
    void aPortTheVariableCannotNameIsRefusedWithOneLine() throws IOException, InterruptedException {
        List<ControlPanelAsStarted> started = new ArrayList<>();
        List<Object> endings = new ArrayList<>();
        List<List<String>> said = new ArrayList<>();
        List<Boolean> addresses = new ArrayList<>();
        try {
            for (String value : NO_PORT) {
                started.add(ControlPanelAsStarted.start(folder.resolve(started.size() + ""), value));
            }
            long giveUp = System.nanoTime() + BOUND.toNanos();
            for (ControlPanelAsStarted controlPanel : started) {
                Duration left = Duration.ofNanos(Math.max(0, giveUp - System.nanoTime()));
                endings.add(controlPanel.awaitEnd(left) ? controlPanel.exitCode() : "still running");
                said.add(controlPanel.errorLines());
                addresses.add(controlPanel.texts().stream().anyMatch(line -> line.startsWith(OPEN)));
            }
        } finally {
            for (ControlPanelAsStarted controlPanel : started) {
                controlPanel.close();
            }
        }

        claim("each of the " + NO_PORT.size() + " values ends the control panel within " + BOUND.toSeconds()
                        + " seconds, with exit code 1",
                () -> assertThat(endings).containsExactly(REFUSED_EXIT, REFUSED_EXIT, REFUSED_EXIT));
        claim("each writing one line on standard error, naming the value it was given",
                () -> assertThat(said).isEqualTo(NO_PORT.stream()
                        .map(value -> List.of(REFUSED.formatted(value))).toList()));
        claim("and none printing an address to open", () -> assertThat(addresses).containsOnly(false));
    }

    @Test
    @Story("The control panel listens at a port that stays the same from one start to the next")
    @DisplayName("Where the port is taken, the control panel listens at one the system picks, says so on one line, and waits for no tab")
    void aTakenPortIsLeftForOneTheSystemPicksAndWaitsForNoTab() throws IOException, InterruptedException {
        String address;
        List<String> said;
        List<String> lines;
        int taken;
        try (ServerSocket held = new ServerSocket()) {
            held.bind(new InetSocketAddress(ControlPanelAsStarted.loopback(), 0));
            taken = held.getLocalPort();
            try (ControlPanelAsStarted controlPanel = ControlPanelAsStarted.start(folder, String.valueOf(taken))) {
                address = controlPanel.addressLine();
                controlPanel.awaitError(line -> !line.isBlank(), NOTHING_LATER);
                said = controlPanel.errorLines();
                lines = controlPanel.texts();
            }
        }
        int port = taken;

        claim("it writes one line on standard error, naming the port that was taken",
                () -> assertThat(said).containsExactly(TAKEN.formatted(port)));
        claim("and prints the address of the root at another port", () -> assertThat(address)
                .matches(ANY_ADDRESS).doesNotMatch(addressAt(port)));
        claim("without waiting for a tab, since no tab of an earlier start can reach a port the system picked",
                () -> assertThat(lines).doesNotContain(WAITING));
    }

    @Test
    @Story("A tab the control panel opened comes back to its next start")
    @DisplayName("A start no tab comes back to waits 5 seconds, then prints the address of its root, and keeps no file of a token")
    void aStartNoTabComesBackToWaitsTheBoundThenOpensTheRootAndKeepsNoFile() throws IOException, InterruptedException {
        int port = ControlPanelAsStarted.freePort();
        List<Line> lines;
        String address;
        try (ControlPanelAsStarted controlPanel = ControlPanelAsStarted.start(folder, String.valueOf(port))) {
            address = controlPanel.addressLine();
            lines = controlPanel.lines();
        }
        List<String> texts = lines.stream().map(Line::text).toList();
        Optional<Line> waiting = lines.stream().filter(line -> line.text().equals(WAITING)).findFirst();
        Line printed = lines.stream().filter(line -> line.text().equals(address)).findFirst().orElseThrow();
        List<String> kept = ControlPanelAsStarted.filesOfTheUsersOwnFolder(folder);

        claim("it writes the line naming the jar it will start, the line saying it waits for a tab, and the address of"
                        + " its root at its port, with no query, in that order and nothing between them",
                () -> assertThat(texts.subList(0, texts.indexOf(address) + 1))
                        .satisfiesExactly(first -> assertThat(first).startsWith(WILL_START),
                                second -> assertThat(second).isEqualTo(WAITING),
                                third -> assertThat(third).matches(addressAt(port))));
        claim("it wrote the address at least " + TAB_BOUND.minus(READING_SLACK).toSeconds() + " seconds after the"
                        + " waiting line: the " + TAB_BOUND.toSeconds() + " seconds it waits, less "
                        + READING_SLACK.toSeconds() + " for reading the two lines",
                () -> assertThat(waiting).map(line -> Duration.ofNanos(printed.readAtNanos() - line.readAtNanos()))
                        .hasValueSatisfying(between -> assertThat(between)
                                .isGreaterThanOrEqualTo(TAB_BOUND.minus(READING_SLACK))));
        claim("and the folder of the user's own holds no file but the launch values, so no token is kept",
                () -> assertThat(kept).isSubsetOf(LAUNCH_VALUES));
    }

    @Test
    @Story("A tab the control panel opened comes back to its next start")
    @DisplayName("A tab of the last start, polling /alive, finds the next start's mark at the same port, reloads onto it, and the next start opens no other")
    void aTabOfTheLastStartComesBackAndNoOtherIsOpened() throws IOException, InterruptedException {
        int port = ControlPanelAsStarted.freePort();
        PanelBrowser tab;
        String firstMark;
        try (ControlPanelAsStarted first = ControlPanelAsStarted.start(folder, String.valueOf(port))) {
            tab = PanelBrowser.opening(ControlPanelAsStarted.addressIn(first.addressLine()));
            firstMark = tab.page().document().body().attr("data-start");
        }
        String secondMark;
        Optional<Page> reloaded;
        List<String> lines;
        boolean anAddressPrinted;
        try (ControlPanelAsStarted second = ControlPanelAsStarted.start(folder, String.valueOf(port))) {
            secondMark = pollAsTheTab(port, firstMark);
            reloaded = secondMark == null ? Optional.empty() : reload(tab);
            second.awaitLine(line -> line.startsWith(OPEN) || line.equals(CAME_BACK.formatted(port)), BOUND);
            anAddressPrinted = second.awaitLine(line -> line.startsWith(OPEN), NOTHING_LATER).isPresent();
            lines = second.texts();
        }

        claim("the first start's page carried its mark, 22 characters of base64url",
                () -> assertThat(firstMark).matches(MARK));
        claim("once the first start had ended, the tab, polling /alive every second, was answered at the same port"
                        + " with another mark",
                () -> assertThat(secondMark).isNotNull().matches(MARK).isNotEqualTo(firstMark));
        claim("reloaded, the tab is answered by the second start with its page, carrying the second start's mark",
                () -> assertThat(reloaded).map(page -> List.of(page.status(),
                                page.document().body().attr("data-start")))
                        .contains(List.of(200, secondMark)));
        claim("the second start wrote that it waits for a tab, then that the tab came back at its port",
                () -> assertThat(lines.subList(Math.min(1, lines.size()), lines.size()))
                        .containsExactly(WAITING, CAME_BACK.formatted(port)));
        claim("and it printed no address to open, so no other tab is opened", () -> assertThat(anAddressPrinted)
                .isFalse());
    }

    /** The address line ADR-302 §11 gives at {@code port}, as a pattern. */
    private static String addressAt(int port) {
        return Pattern.quote(OPEN + port + "/" + IN_YOUR_BROWSER);
    }

    /**
     * Polls {@code /alive} at {@code port} as the control panel's script does (ADR-325 §4.2): one second after the
     * last poll settled, sent from the page's own origin; and returns the first answer's mark that is not
     * {@code ownMark}, or null once {@link #POLLING_BOUND} has passed.
     */
    private static String pollAsTheTab(int port, String ownMark) throws InterruptedException {
        return Allure.step("poll /alive at port " + port + ", as a tab of the last start does",
                () -> {
                    long giveUp = System.nanoTime() + POLLING_BOUND.toNanos();
                    InetSocketAddress server = new InetSocketAddress(ControlPanelAsStarted.loopback(), port);
                    while (System.nanoTime() < giveUp) {
                        Thread.sleep(POLL_EVERY.toMillis());
                        RawHttp.Response answer;
                        try {
                            answer = RawHttp.send(server, "GET", TabPaths.ALIVE, List.of(
                                    "Host: 127.0.0.1:" + port, "Sec-Fetch-Site: same-origin", "Sec-Fetch-Mode: cors",
                                    "Sec-Fetch-Dest: empty"));
                        } catch (IOException nobodyAnswers) {
                            continue;
                        }
                        if (answer.status() == 200 && answer.body().matches(MARK)
                                && !answer.body().equals(ownMark)) {
                            return answer.body();
                        }
                    }
                    return null;
                });
    }

    /** The tab reloaded, as the script reloads it: the page it is on, asked for again. */
    private static Optional<Page> reload(PanelBrowser tab) {
        try {
            return Optional.of(tab.go(tab.page().address().getPath()).page());
        } catch (Exception noAnswer) {
            return Optional.empty();
        }
    }
}
