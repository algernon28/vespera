package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Spinner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The download panel (ADR-430 §7): clicking a model's KO opens it at the window's top right and downloads every model the
 * check found missing, one at a time, each with its name, a bar and its words; Hide takes it away and the download goes
 * on, the model's row then reading KO and its percent, which brings it back when clicked, the list's columns not moving
 * (§2); Cancel stops it; a lost connection brings it back with the reason and Retry; and once every model is done it
 * offers Close. Run is off while a download runs, and the window checks at once after one stops (§4, §6). Each download
 * is bounded by the {@code vespera.models.pull-stalled-at-most} Command options holds (§7, §12).
 *
 * <p>The window is given a {@link RecordingDockerControl}, whose downloads the test drives; nothing is asked of Ollama.
 * The sizes are the test's own, in GB of 1,024³ bytes.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("889")
@Link(name = "ADR-430", url = Adr.THE_CONTROL_PANEL_CHECKS_DOCKER_AND_STARTS_NOTHING_UNTIL_START_DOCKER, type = "adr")
class TheDownloadPanelTest {

    private static final DockerCheck.Mark OK = DockerCheck.Mark.OK;

    private static final DockerCheck.Mark KO = DockerCheck.Mark.KO;

    /** A GB as the panel counts it: 1,024³ bytes. */
    private static final long GB = 1024L * 1024 * 1024;

    /** A model of five GB, two and a half of them downloaded: half of it. */
    private static final long TOTAL = 5 * GB;

    private static final long HALF = TOTAL / 2;

    private static final String EMBEDDING = TheWindowChecksAndStartsNothingAsItOpensTest.EMBEDDING_MODEL;

    private static final String GENERATIVE = TheWindowChecksAndStartsNothingAsItOpensTest.GENERATIVE_MODEL;

    /**
     * What the test sets Command options' {@code vespera.models.pull-stalled-at-most} to, in seconds: neither the jar's
     * 600 nor anything a source names.
     */
    private static final int STALLED_SECONDS = 42;

    /** Within this of a download's stop, a check is the one done at once, not the clock's, which comes every 5 s. */
    private static final Duration AT_ONCE = Duration.ofMillis(2500);

    @TempDir
    Path folder;

    @Test
    @Story("A download panel shows a model's download")
    @DisplayName("Clicking a model's KO opens the panel and downloads each missing model one at a time, with its name, its bar and its words, Run off meanwhile, and the window checks at once after the first is done")
    void clickingAModelsKoDownloadsEachMissingModelOneAtATime() throws Exception {
        RecordingDockerControl docker = bothModelsMissing();
        record Panel(boolean shown, String embeddingName, String embeddingWords, double embeddingBar,
                String generativeWords, String runCaption, boolean runEnabled) {
        }
        Panel halfway;
        int askedBeforeTheFirstWasDone;
        String embeddingDone;
        Duration checkedAfterTheFirst;
        try (WindowForTests window = shown(docker)) {
            clickTheKoOf(window, "check-embedding-model");
            WindowForTests.within(() -> !docker.downloads.isEmpty());
            docker.downloads.getFirst().progress(HALF, TOTAL);
            halfway = window.awaiting(root -> new Panel(WindowForTests.showing(root, "download-panel"),
                    WindowForTests.textUnder(root, "download-embedding-model-name"),
                    text(root, "download-embedding-model-words"),
                    WindowForTests.node(root, "download-embedding-model-bar", ProgressBar.class).getProgress(),
                    text(root, "download-generative-model-words"), WindowForTests.caption(root, "run-captioned"),
                    !WindowForTests.node(root, "run", Button.class).isDisabled()),
                    read -> read.embeddingWords().contains("downloading"));
            askedBeforeTheFirstWasDone = docker.downloads.size();
            TheWindowChecksAndStartsNothingAsItOpensTest.firstCheckAfter(docker, Instant.now());
            Instant firstDone = Instant.now();
            docker.downloads.getFirst().done();
            WindowForTests.within(() -> docker.downloads.size() == 2);
            checkedAfterTheFirst = TheWindowChecksAndStartsNothingAsItOpensTest.firstCheckAfter(docker, firstDone);
            embeddingDone = window.awaiting(root -> text(root, "download-embedding-model-words"),
                    words -> words.endsWith("done"));
        }

        claim("the panel is shown, and names the embedding model", () -> {
            assertThat(halfway.shown()).isTrue();
            assertThat(halfway.embeddingName()).contains(EMBEDDING);
        });
        claim("half of five GB downloaded, its words read 2.5 of 5.0 GB · downloading and its bar is half full",
                () -> assertThat(List.of(halfway.embeddingWords(), halfway.embeddingBar()))
                        .containsExactly("2.5 of 5.0 GB · downloading", 0.5));
        claim("the generative model, not yet begun, reads waiting",
                () -> assertThat(halfway.generativeWords()).isEqualTo("waiting"));
        claim("one download was asked for while the first ran, and the second only once it was done, in that order",
                () -> {
                    assertThat(askedBeforeTheFirstWasDone).isOne();
                    assertThat(docker.downloads).extracting(each -> each.model).containsExactly(EMBEDDING, GENERATIVE);
                });
        claim("the embedding model, done, reads 5.0 of 5.0 GB · done",
                () -> assertThat(embeddingDone).isEqualTo("5.0 of 5.0 GB · done"));
        claim("the window checked within " + AT_ONCE.toMillis() + " ms of the first download's end, the second"
                        + " still to come: each download stopping is a check at once, not only the last",
                () -> assertThat(checkedAfterTheFirst).isLessThan(AT_ONCE));
        claim("and Run was off while it downloaded, its caption saying so",
                () -> assertThat(List.of(halfway.runEnabled(), halfway.runCaption()))
                        .containsExactly(false, "Off until the model downloads finish."));
    }

    @Test
    @Story("A download panel shows a model's download")
    @DisplayName("Hide takes the panel away and the download goes on, the row reading KO and its percent without moving the list, and clicking it brings the panel back; the panel moves nothing")
    void hideKeepsItDownloadingTheRowReadsKoAndItsPercentAndClickingItReopensThePanel() throws Exception {
        RecordingDockerControl docker = bothModelsMissing();
        Header before;
        Header shownOver;
        Header hidden;
        boolean hiddenShown;
        String row;
        boolean reopened;
        try (WindowForTests window = shown(docker)) {
            window.awaiting(root -> text(root, "check-embedding-model-mark"), "KO"::equals);
            before = window.read(TheDownloadPanelTest::header);
            clickTheKoOf(window, "check-embedding-model");
            WindowForTests.within(() -> !docker.downloads.isEmpty());
            docker.downloads.getFirst().progress(HALF, TOTAL);
            window.awaiting(root -> WindowForTests.showing(root, "download-panel"), Boolean::booleanValue);
            shownOver = window.read(TheDownloadPanelTest::header);
            window.act(root -> WindowForTests.node(root, "download-hide", Button.class).fire());
            hiddenShown = window.read(root -> WindowForTests.showing(root, "download-panel"));
            row = window.awaiting(root -> text(root, "check-embedding-model-mark") + " "
                    + text(root, "check-embedding-model-progress"), read -> read.endsWith("%"));
            hidden = window.read(TheDownloadPanelTest::header);
            window.act(root -> WindowForTests.click(WindowForTests.node(root, "check-embedding-model-progress",
                    Node.class)));
            reopened = window.awaiting(root -> WindowForTests.showing(root, "download-panel"), Boolean::booleanValue);
        }

        claim("Hide took the panel away", () -> assertThat(hiddenShown).isFalse());
        claim("and the download went on: it was not cancelled, and no second was asked for",
                () -> assertThat(List.of(docker.downloads.getFirst().cancelled(), docker.downloads.size()))
                        .containsExactly(false, 1));
        claim("the embedding model's row reads KO ↓ 50%, half of it downloaded",
                () -> assertThat(row).isEqualTo("KO ↓ 50%"));
        claim("and the list stands where it stood, as wide, its models' column unmoved",
                () -> assertThat(List.of(hidden.listX(), hidden.listWidth(), hidden.modelsColumnX()))
                        .containsExactly(before.listX(), before.listWidth(), before.modelsColumnX()));
        claim("the panel, shown, moved nothing: the action row stood where it stands without it",
                () -> assertThat(shownOver.actions()).isEqualTo(hidden.actions()));
        claim("and clicking the percent brought the panel back", () -> assertThat(reopened).isTrue());
    }

    @Test
    @Story("A download panel shows a model's download")
    @DisplayName("Cancel stops the download, drops the one waiting, closes the panel, and the window checks at once")
    void cancelStopsTheDownloadAndClosesThePanel() throws Exception {
        RecordingDockerControl docker = bothModelsMissing();
        boolean shownAfter;
        Duration checkedAfter;
        try (WindowForTests window = shown(docker)) {
            clickTheKoOf(window, "check-embedding-model");
            WindowForTests.within(() -> !docker.downloads.isEmpty());
            docker.downloads.getFirst().progress(HALF, TOTAL);
            window.awaiting(root -> WindowForTests.showing(root, "download-panel"), Boolean::booleanValue);
            TheWindowChecksAndStartsNothingAsItOpensTest.firstCheckAfter(docker, Instant.now());
            Instant cancelled = Instant.now();
            window.act(root -> WindowForTests.node(root, "download-cancel", Button.class).fire());
            shownAfter = window.awaiting(root -> WindowForTests.showing(root, "download-panel"), shown -> !shown);
            checkedAfter = TheWindowChecksAndStartsNothingAsItOpensTest.firstCheckAfter(docker, cancelled);
        }

        claim("the download running was cancelled", () -> assertThat(docker.downloads.getFirst().cancelled()).isTrue());
        claim("the generative model, waiting, was never asked for",
                () -> assertThat(docker.downloads).extracting(each -> each.model).containsExactly(EMBEDDING));
        claim("the panel closed", () -> assertThat(shownAfter).isFalse());
        claim("and the window checked within " + AT_ONCE.toMillis() + " ms of the cancel",
                () -> assertThat(checkedAfter).isLessThan(AT_ONCE));
    }

    @Test
    @Story("A download panel shows a model's download")
    @DisplayName("A lost connection brings the hidden panel back with the reason and Retry, and Retry downloads the same model again")
    void aLostConnectionReopensThePanelWithTheReasonAndRetryDownloadsAgain() throws Exception {
        RecordingDockerControl docker = bothModelsMissing();
        String reason = "the connection to Ollama was lost: Connection reset";
        boolean shownAgain;
        String said;
        boolean retryShown;
        try (WindowForTests window = shown(docker)) {
            clickTheKoOf(window, "check-embedding-model");
            WindowForTests.within(() -> !docker.downloads.isEmpty());
            docker.downloads.getFirst().progress(HALF, TOTAL);
            window.awaiting(root -> WindowForTests.showing(root, "download-panel"), Boolean::booleanValue);
            window.act(root -> WindowForTests.node(root, "download-hide", Button.class).fire());
            window.awaiting(root -> WindowForTests.showing(root, "download-panel"), shown -> !shown);
            docker.downloads.getFirst().lost(reason);
            shownAgain = window.awaiting(root -> WindowForTests.showing(root, "download-panel"), Boolean::booleanValue);
            said = window.read(root -> WindowForTests.textUnder(root, "download-reason"));
            retryShown = window.read(root -> WindowForTests.showing(root, "download-retry"));
            window.act(root -> WindowForTests.node(root, "download-retry", Button.class).fire());
            WindowForTests.within(() -> docker.downloads.size() == 2);
        }

        claim("the lost connection brought the hidden panel back", () -> assertThat(shownAgain).isTrue());
        claim("saying the reason the download gave, whole", () -> assertThat(said).isEqualTo(reason));
        claim("with Retry", () -> assertThat(retryShown).isTrue());
        claim("and Retry asked for the embedding model again",
                () -> assertThat(docker.downloads).extracting(each -> each.model).containsExactly(EMBEDDING, EMBEDDING));
    }

    @Test
    @Story("A download panel shows a model's download")
    @DisplayName("Once every model is downloaded the panel offers Close, which takes it away, and the window checks at once")
    void whenEveryModelIsDownloadedItOffersCloseAndTheWindowChecksAgain() throws Exception {
        RecordingDockerControl docker = bothModelsMissing();
        boolean closeOffered;
        boolean shownAfterClose;
        Duration checkedAfter;
        try (WindowForTests window = shown(docker)) {
            clickTheKoOf(window, "check-generative-model");
            WindowForTests.within(() -> !docker.downloads.isEmpty());
            docker.downloads.getFirst().progress(TOTAL, TOTAL);
            docker.downloads.getFirst().done();
            WindowForTests.within(() -> docker.downloads.size() == 2);
            docker.downloads.get(1).progress(HALF, TOTAL);
            TheWindowChecksAndStartsNothingAsItOpensTest.firstCheckAfter(docker, Instant.now());
            Instant allDone = Instant.now();
            docker.downloads.get(1).progress(TOTAL, TOTAL);
            docker.downloads.get(1).done();
            closeOffered = window.awaiting(root -> WindowForTests.showing(root, "download-close"), Boolean::booleanValue);
            checkedAfter = TheWindowChecksAndStartsNothingAsItOpensTest.firstCheckAfter(docker, allDone);
            window.act(root -> WindowForTests.node(root, "download-close", Button.class).fire());
            shownAfterClose = window.awaiting(root -> WindowForTests.showing(root, "download-panel"), shown -> !shown);
        }

        claim("clicking the generative model's KO downloaded both missing models, the embedding model first",
                () -> assertThat(docker.downloads).extracting(each -> each.model).containsExactly(EMBEDDING, GENERATIVE));
        claim("once both were done, the panel offered Close", () -> assertThat(closeOffered).isTrue());
        claim("and the window checked within " + AT_ONCE.toMillis() + " ms of the last one's end",
                () -> assertThat(checkedAfter).isLessThan(AT_ONCE));
        claim("Close took the panel away", () -> assertThat(shownAfterClose).isFalse());
    }

    @Test
    @Story("A download panel shows a model's download")
    @DisplayName("Each download is bounded by the vespera.models.pull-stalled-at-most Command options holds, as the operator set it")
    void eachDownloadIsBoundedByTheStalledAtMostCommandOptionsHolds() throws Exception {
        RecordingDockerControl docker = bothModelsMissing();
        try (WindowForTests window = shown(docker)) {
            window.act(root -> stalledAtMostOf(root).getValueFactory().setValue(STALLED_SECONDS));
            clickTheKoOf(window, "check-embedding-model");
            WindowForTests.within(() -> !docker.downloads.isEmpty());
            docker.downloads.getFirst().done();
            WindowForTests.within(() -> docker.downloads.size() == 2);
        }

        claim("vespera.models.pull-stalled-at-most set to " + STALLED_SECONDS + " s in Command options, both downloads,"
                        + " the embedding model's and then the generative model's, were given " + STALLED_SECONDS + " s"
                        + " as the time a download may stand still",
                () -> assertThat(docker.downloads).extracting(each -> each.model, each -> each.stalledAtMost)
                        .containsExactly(tuple(EMBEDDING, Duration.ofSeconds(STALLED_SECONDS)),
                                tuple(GENERATIVE, Duration.ofSeconds(STALLED_SECONDS))));
    }

    @SuppressWarnings("unchecked")
    private static Spinner<Integer> stalledAtMostOf(Parent root) {
        return WindowForTests.node(root, "vespera.models.pull-stalled-at-most", Spinner.class);
    }

    /** A check whose every row answers but both models, which Ollama has not. */
    private static RecordingDockerControl bothModelsMissing() {
        return new RecordingDockerControl().checkAnswering(RecordingDockerControl.everyRow(OK, OK, OK, KO, KO));
    }

    private WindowForTests shown(RecordingDockerControl docker) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime(), docker);
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("working")).toString(), "", "", "", "", "", ""));
        state.useSources(TheWindowChecksAndStartsNothingAsItOpensTest.withModels());
        return WindowForTests.shown(state);
    }

    /** Clicks the KO of {@code row} once the check has drawn it. */
    private static void clickTheKoOf(WindowForTests window, String row) throws Exception {
        window.awaiting(root -> text(root, row + "-mark"), "KO"::equals);
        window.act(root -> WindowForTests.click(WindowForTests.node(root, row + "-mark", Node.class)));
    }

    /** Where the list stands in the header and how wide it is, where its models' column stands, and the action row in the scene. */
    private record Header(double listX, double listWidth, double modelsColumnX, Bounds actions) {
    }

    private static Header header(Parent root) {
        Node list = WindowForTests.node(root, "docker-check", Node.class);
        Node models = WindowForTests.node(root, "check-embedding-model", Node.class);
        Node actions = WindowForTests.node(root, "actions", Node.class);
        return new Header(list.getBoundsInParent().getMinX(), list.getLayoutBounds().getWidth(),
                models.getBoundsInParent().getMinX(), actions.localToScene(actions.getLayoutBounds()));
    }

    /** What the node whose id is {@code id} shows, a label's text or a text control's. */
    private static String text(Parent root, String id) {
        return WindowForTests.textUnder(root, id);
    }
}
