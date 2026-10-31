package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.controlNames;
import static io.algernon.controlpanel.ScreenServer.form;
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
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The launch page, of the values that are this machine's and not the profile's (ADR-330 §11, §12): the Docling image is
 * checked against what the jar last used where no launch value names one, the image the sidecar runs and the two the
 * jar's compose files build are offered, and the keys of {@code application.yaml} the control panel does not edit are
 * shown with where each came from. The image stays a launch value, written into no file of the working directory.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_CLUSTERING_SHIPS_AS_PRESETS, type = "adr")
@Disabled("ADR-330: awaits the launch page's check of the image against the jar's file, its list of images, and machine-values")
class LaunchImageScreenTest {

    /** The file the jar writes for the control panel, in the working directory. */
    private static final String EFFECTIVE_VALUES = "effective-values.properties";

    /** The image the processor build runs, which the jar ships as the one it expects. */
    private static final String PROCESSOR_IMAGE = "vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2";

    /** The image the graphics card's build runs, which the sidecar here says it runs. */
    private static final String CARD_IMAGE = "vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0-r2";

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
            theJarSaid(work, "vespera.docling.image=" + PROCESSOR_IMAGE + "\nvespera.docling.image.source=shipped\n"
                    + "docling.images=" + PROCESSOR_IMAGE + " " + CARD_IMAGE + "\n");
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
    @DisplayName("The image the sidecar runs and the two the jar builds are offered in the field, and the one picked is held as the launch value")
    void theImagesAreOfferedAndTheOnePickedIsHeldAsALaunchValue() throws IOException {
        Path work = Files.createDirectories(folder.resolve("work"));
        HttpServer docling = aSidecarRunning(CARD_IMAGE);
        try {
            String address = "http://127.0.0.1:" + docling.getAddress().getPort();
            theJarSaid(work, "docling.images=" + PROCESSOR_IMAGE + " " + CARD_IMAGE + "\n");
            LaunchValues values = new LaunchValues(corpus().toString(), work.toString(), "", "", address, "", "", "");
            try (ScreenServer screens = ScreenServer.start(folder, values)) {
                PanelBrowser browser = screens.browser();
                Element launch = form(browser.page(), "launch");

                claim("the field offers each image once, the sidecar's first",
                        () -> assertThat(byId(browser.page(), "docling-images").select("option").eachAttr("value"))
                                .containsExactly(CARD_IMAGE, PROCESSOR_IMAGE));
                claim("and is tied to that list, still free for a name typed by hand",
                        () -> assertThat(List.of(launch.select("[name='vespera.docling.image']").attr("list"),
                                        launch.select("[name='vespera.docling.image']").attr("type")))
                                .containsExactly("docling-images", "text"));

                Page checked = browser.press("launch", "check", Map.of("vespera.docling.image", CARD_IMAGE)).page();
                claim("picked, the check finds it matches, and no file of the working folder holds it",
                        () -> assertThat(List.of(byId(checked, "docling-check").attr("data-check"),
                                        Files.exists(work.resolve("application.yaml")),
                                        Files.exists(work.resolve("application-local.yaml"))))
                                .containsExactly("matches", false, false));
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
        theJarSaid(work, """
                vespera.sidecars.start=false
                vespera.sidecars.start.source=environment
                vespera.sidecars.wait-at-most=180s
                vespera.sidecars.wait-at-most.source=shipped
                vespera.progress-interval=5s
                vespera.progress-interval.source=shipped
                vespera.open-browser=true
                vespera.open-browser.source=shipped
                vespera.clustering.neighbours=15
                vespera.clustering.neighbours.source=shipped
                """);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpus(), work))) {
            Page page = screens.browser().page();
            List<List<String>> shown = byId(page, "machine-values").select("li").stream()
                    .map(item -> List.of(item.attr("data-key"), item.attr("data-value"), item.attr("data-source")))
                    .toList();

            claim("each is shown with its value and where it came from, in the one order the page gives them",
                    () -> assertThat(shown).containsExactly(
                            List.of("vespera.sidecars.start", "false", "environment"),
                            List.of("vespera.sidecars.wait-at-most", "180s", "shipped"),
                            List.of("vespera.progress-interval", "5s", "shipped"),
                            List.of("vespera.open-browser", "true", "shipped"),
                            List.of("vespera.clustering.neighbours", "15", "shipped")));
            claim("and no form on the page has a control for one",
                    () -> assertThat(page.document().select("form").stream()
                                    .flatMap(each -> controlNames(each).stream()).toList())
                            .doesNotContain("vespera.sidecars.start", "vespera.sidecars.wait-at-most",
                                    "vespera.progress-interval", "vespera.open-browser",
                                    "vespera.clustering.neighbours"));
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
