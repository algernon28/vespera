package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.controlNames;
import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The launch page, of the values that are this machine's and not the profile's (ADR-330 §11, §12): the Docling image is
 * checked against what the jar last used where no launch value names one, the image the sidecar runs is shown, and the keys of {@code application.yaml} the control panel does not edit are
 * shown with where each came from. The image stays a launch value, which the control panel writes into no file of the
 * working directory; the jar's own file says it as {@code environment}, and the extractor identity as ever.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("awaits the launch page's check of the image against the file the jar writes, and this machine's values")
class LaunchImageScreenTest {

    /** The file the jar writes for the control panel, in the working directory. */
    private static final String EFFECTIVE_VALUES = "effective-values.properties";

    /** The image the processor build runs, which the jar ships as the one it expects. */
    private static final String PROCESSOR_IMAGE = "vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2";

    /** The image the graphics card's build runs, which the sidecar here says it runs. */
    private static final String CARD_IMAGE = "vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0-r2";

    /**
     * What the page lists of this machine's values, in the order ADR-330 §12 gives: each key, the value the jar said and
     * where it came from. The first five are the application's own; the fourteen after them are the presets of the
     * converter, of redundancy and of relevance, at the values the jar ships.
     */
    private static final List<List<String>> THIS_MACHINES_VALUES = List.of(
            List.of("vespera.sidecars.start", "false", "environment"),
            List.of("vespera.sidecars.wait-at-most", "180s", "shipped"),
            List.of("vespera.progress-interval", "5s", "shipped"),
            List.of("vespera.open-browser", "true", "shipped"),
            List.of("vespera.clustering.neighbours", "15", "shipped"),
            List.of("vespera.docling.ocr-preset", "rapidocr", "shipped"),
            List.of("vespera.docling.timeouts-in-a-row", "3", "shipped"),
            List.of("vespera.redundancy.shingle-words", "5", "shipped"),
            List.of("vespera.redundancy.minhash.permutations", "128", "shipped"),
            List.of("vespera.redundancy.minhash.bands", "16", "shipped"),
            List.of("vespera.redundancy.minhash.rows-per-band", "8", "shipped"),
            List.of("vespera.redundancy.minhash.seed", "6840335614489015382", "shipped"),
            List.of("vespera.redundancy.near-duplicate-jaccard", "0.8", "shipped"),
            List.of("vespera.redundancy.containment-index", "0.95", "shipped"),
            List.of("vespera.redundancy.rare-shingle-sample", "32", "shipped"),
            List.of("vespera.redundancy.rare-shingle-hits", "24", "shipped"),
            List.of("vespera.relevance.top-similarities", "3", "shipped"),
            List.of("vespera.relevance.sample.bands", "5", "shipped"),
            List.of("vespera.relevance.sample.per-band", "12", "shipped"));

    @TempDir
    Path folder;

    @Test
    @Story("The operator is not caught by an image the sidecar does not run")
    @DisplayName("With no image named, the sidecar's is checked against the one the jar last used, and a mismatch is warned of before Run, naming both")
    void anEmptyImageIsCheckedAgainstTheImageTheJarLastUsed() throws IOException {
        Path work = Files.createDirectories(folder.resolve("work"));
        HttpServer docling = aSidecarRunning(CARD_IMAGE);
        try {
            String address = "http://127.0.0.1:" + docling.getAddress().getPort();
            theJarSaid(work, "vespera.docling.image=" + PROCESSOR_IMAGE + "\nvespera.docling.image.source=shipped\n");
            LaunchValues values = new LaunchValues(corpus().toString(), work.toString(), "", "", address, "", "", "");
            try (ScreenServer screens = ScreenServer.start(folder, values)) {
                Page page = screens.browser().page();

                claim("the check says the sidecar runs another image, though no launch value names one",
                        () -> assertThat(byId(page, "docling-check").attr("data-check")).isEqualTo("another-image"));
                claim("the sidecar's image is shown",
                        () -> assertThat(byId(page, "docling-running").attr("data-image")).isEqualTo(CARD_IMAGE));
                claim("the warning beside Run names both",
                        () -> assertThat(byId(page, "warnings").text()).contains(CARD_IMAGE, PROCESSOR_IMAGE));
            }
        } finally {
            docling.stop(0);
        }
    }

    @Test
    @Story("The operator is not caught by an image the sidecar does not run")
    @DisplayName("The image the sidecar runs is shown, and typed into the field it matches")
    void theImagePickedMatchesTheOneTheSidecarRuns() throws IOException {
        Path work = Files.createDirectories(folder.resolve("work"));
        HttpServer docling = aSidecarRunning(CARD_IMAGE);
        try {
            String address = "http://127.0.0.1:" + docling.getAddress().getPort();
            LaunchValues values = new LaunchValues(corpus().toString(), work.toString(), "", "", address, "", "", "");
            try (ScreenServer screens = ScreenServer.start(folder, values)) {
                PanelBrowser browser = screens.browser();

                claim("the sidecar's image is shown beside the field",
                        () -> assertThat(byId(browser.page(), "docling-running").attr("data-image")).isEqualTo(CARD_IMAGE));

                Page checked = browser.press("launch", "check", Map.of("vespera.docling.image", CARD_IMAGE)).page();
                claim("typed, the check finds it matches",
                        () -> assertThat(byId(checked, "docling-check").attr("data-check")).isEqualTo("matches"));
            }
        } finally {
            docling.stop(0);
        }
    }

    @Test
    @Story("The operator is not caught by an image the sidecar does not run")
    @DisplayName("With no address on the launch page, the sidecar is asked what it runs at the address the jar last used")
    void withNoAddressTheSidecarIsAskedWhereTheJarLastAskedIt() throws IOException {
        Path work = Files.createDirectories(folder.resolve("work"));
        HttpServer docling = aSidecarRunning(CARD_IMAGE);
        try {
            String address = "http://127.0.0.1:" + docling.getAddress().getPort();
            theJarSaid(work, "vespera.docling.base-url=" + address + "\nvespera.docling.base-url.source=shipped\n"
                    + "vespera.docling.image=" + PROCESSOR_IMAGE + "\nvespera.docling.image.source=shipped\n");
            try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpus(), work))) {
                Page page = screens.browser().page();

                claim("the sidecar at that address is what is shown, and it is checked against the image the jar named",
                        () -> assertThat(List.of(byId(page, "docling-running").attr("data-image"),
                                        byId(page, "docling-check").attr("data-check")))
                                .containsExactly(CARD_IMAGE, "another-image"));
            }
        } finally {
            docling.stop(0);
        }
    }

    @Test
    @Story("The operator is not caught by an image the sidecar does not run")
    @DisplayName("Before any invocation the sidecar's image is still shown, at the address the launch page names, with nothing to compare it to")
    void withNoFileTheSidecarsImageIsStillShown() throws IOException {
        Path work = Files.createDirectories(folder.resolve("work"));
        HttpServer docling = aSidecarRunning(CARD_IMAGE);
        try {
            String address = "http://127.0.0.1:" + docling.getAddress().getPort();
            LaunchValues values = new LaunchValues(corpus().toString(), work.toString(), "", "", address, "", "", "");
            try (ScreenServer screens = ScreenServer.start(folder, values)) {
                Page page = screens.browser().page();

                claim("the image is shown, and the check stays as it is with nothing to compare it to",
                        () -> assertThat(List.of(byId(page, "docling-running").attr("data-image"),
                                        byId(page, "docling-check").attr("data-check")))
                                .containsExactly(CARD_IMAGE, "not-checked"));
            }
        } finally {
            docling.stop(0);
        }
    }

    @Test
    @Story("The operator sees what this machine runs with")
    @DisplayName("The values of the application's own file are shown with where each came from, and none has a control")
    void whatThisMachineRunsWithIsShownAndNotEdited() throws IOException {
        Path work = Files.createDirectories(folder.resolve("work"));
        // Written in the reverse of the page's order: the order shown is the page's own, not the file's.
        StringBuilder said = new StringBuilder();
        for (List<String> value : THIS_MACHINES_VALUES.reversed()) {
            said.append(value.get(0)).append('=').append(value.get(1)).append('\n')
                    .append(value.get(0)).append(".source=").append(value.get(2)).append('\n');
        }
        theJarSaid(work, said.toString());
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpus(), work))) {
            Page page = screens.browser().page();
            List<List<String>> shown = byId(page, "machine-values").select("li").stream()
                    .map(item -> List.of(item.attr("data-key"), item.attr("data-value"), item.attr("data-source")))
                    .toList();

            claim("each of the " + THIS_MACHINES_VALUES.size() + " is shown with its value and where it came from, in"
                            + " the one order the page gives them",
                    () -> assertThat(shown).containsExactlyElementsOf(THIS_MACHINES_VALUES));
            claim("and no form on the page has a control for one",
                    () -> assertThat(page.document().select("form").stream()
                                    .flatMap(each -> controlNames(each).stream()).toList())
                            .doesNotContainAnyElementsOf(THIS_MACHINES_VALUES.stream().map(List::getFirst).toList()));
        }
    }

    @Test
    @Story("The operator sees what this machine runs with")
    @DisplayName("A value the jar's file does not name is left out of what this machine runs with")
    void aValueTheFileDoesNotNameIsLeftOutOfWhatThisMachineRunsWith() throws IOException {
        Path work = Files.createDirectories(folder.resolve("work"));
        theJarSaid(work, "vespera.progress-interval=5s\nvespera.progress-interval.source=shipped\n");
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpus(), work))) {
            Page page = screens.browser().page();

            claim("the one value the file names is the one listed",
                    () -> assertThat(byId(page, "machine-values").select("li").eachAttr("data-key"))
                            .containsExactly("vespera.progress-interval"));
        }
    }

    private Path corpus() throws IOException {
        return Files.createDirectories(folder.resolve("corpus"));
    }

    private static void theJarSaid(Path workingDirectory, String lines) throws IOException {
        Files.writeString(workingDirectory.resolve(EFFECTIVE_VALUES),
                "written-at=2026-10-31T10:15:30+01:00\n" + lines, UTF_8);
    }

    private static HttpServer aSidecarRunning(String image) throws IOException {
        HttpServer sidecar = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        byte[] answer = ("{\"vespera-image\": \"" + image + "\"}").getBytes(UTF_8);
        sidecar.createContext("/version", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, answer.length);
            try (var out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        sidecar.start();
        return sidecar;
    }
}
