package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.buttonNames;
import static io.algernon.controlpanel.ScreenServer.byId;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where the converter runs another image than the one Run would use, the launch page says so before Run, in the
 * preflight's row naming both, and offers the image the converter runs with a button that puts it in the field and
 * starts nothing (ADR-334 §2). With no image named, the image Run would use is the GPU image the control panel
 * preselects (§11), so a converter running the processor build is the case met on a machine with no card.
 *
 * <p>The converter is a stand-in answering {@code /version} as the sidecar does. No {@code effective-values.properties}
 * is written: the working directory is one no invocation has used yet.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
class TheImageTheConverterRunsIsOfferedScreenTest {

    /** The image the control panel preselects, the graphics card's. */
    private static final String PRESELECTED = ControlPanelPage.DOCLING_IMAGE_GPU;

    /** The processor build, which the converter here runs. */
    private static final String RUNNING = ControlPanelPage.DOCLING_IMAGE_CPU;

    private static final String IMAGE_FIELD = "vespera.docling.image";

    private static final String USE_THE_RUNNING_IMAGE = "use-running-image";

    @TempDir
    Path folder;

    @Test
    @Story("The operator is not caught by an image the converter does not run")
    @DisplayName("A converter running another image than the one Run would use is said in the preflight, naming both, and its image is offered and put in the field")
    void anotherImageRunningIsSaidBeforeRunAndOffered() throws IOException {
        Path work = Files.createDirectories(folder.resolve("work"));
        HttpServer docling = aConverterRunning(RUNNING);
        try {
            try (ScreenServer screens = ScreenServer.start(folder, valuesAskingAt(docling, work))) {
                PanelBrowser browser = screens.browser();
                Page page = browser.page();
                Element row = byId(page, "preflight").selectFirst("[data-row=docling]");

                claim("the check compares the converter's image with the one the field holds, and finds another",
                        () -> assertThat(byId(page, "docling-check").attr("data-check")).isEqualTo("another-image"));
                claim("the preflight's row is red, and names both images",
                        () -> {
                            assertThat(row.attr("data-state")).isEqualTo("bad");
                            assertThat(row.text()).contains(RUNNING, PRESELECTED);
                        });
                claim("and the launch form offers the image the converter runs",
                        () -> assertThat(buttonNames(form(page, "launch"))).contains(USE_THE_RUNNING_IMAGE));

                Page offered = browser.press("launch", USE_THE_RUNNING_IMAGE, Map.of()).page();

                claim("pressing it lands back on the launch page with the converter's image in the field",
                        () -> assertThat(List.of(offered.address().getPath(),
                                        form(offered, "launch").select("[name='" + IMAGE_FIELD + "']").val()))
                                .containsExactly("/", RUNNING));
                claim("the check then finds they match, and the offer is gone",
                        () -> {
                            assertThat(byId(offered, "docling-check").attr("data-check")).isEqualTo("matches");
                            assertThat(buttonNames(form(offered, "launch"))).doesNotContain(USE_THE_RUNNING_IMAGE);
                        });
                claim("and nothing was started or written: the working directory, which a child's start would write"
                                + " into, is still empty",
                        () -> {
                            try (var entries = Files.list(work)) {
                                assertThat(entries.toList()).isEmpty();
                            }
                        });
            }
        } finally {
            docling.stop(0);
        }
    }

    @Test
    @Story("The operator is not caught by an image the converter does not run")
    @DisplayName("A converter running the image Run would use is not said against, and no image is offered")
    void theImageRunWouldUseRunningIsNotSaidAgainst() throws IOException {
        Path work = Files.createDirectories(folder.resolve("work"));
        HttpServer docling = aConverterRunning(PRESELECTED);
        try {
            try (ScreenServer screens = ScreenServer.start(folder, valuesAskingAt(docling, work))) {
                Page page = screens.browser().page();

                claim("the check finds the converter runs the image Run would use",
                        () -> assertThat(byId(page, "docling-check").attr("data-check")).isEqualTo("matches"));
                claim("its row is not red",
                        () -> assertThat(byId(page, "preflight").selectFirst("[data-row=docling]").attr("data-state"))
                                .isNotEqualTo("bad"));
                claim("and nothing is offered",
                        () -> assertThat(buttonNames(form(page, "launch"))).doesNotContain(USE_THE_RUNNING_IMAGE));
            }
        } finally {
            docling.stop(0);
        }
    }

    /** Launch values naming the corpus root, the working directory and the converter's address, no image named. */
    private LaunchValues valuesAskingAt(HttpServer docling, Path work) throws IOException {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        return new LaunchValues(corpus.toString(), work.toString(), "", "",
                "http://127.0.0.1:" + docling.getAddress().getPort(), "", "", "");
    }

    private static HttpServer aConverterRunning(String image) throws IOException {
        HttpServer converter = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        byte[] answer = ("{\"vespera-image\": \"" + image + "\"}").getBytes(UTF_8);
        converter.createContext("/version", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, answer.length);
            try (var out = exchange.getResponseBody()) {
                out.write(answer);
            }
        });
        converter.start();
        return converter;
    }
}
