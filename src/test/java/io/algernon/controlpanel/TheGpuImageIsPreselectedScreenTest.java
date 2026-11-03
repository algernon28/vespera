package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel preselects the GPU image (ADR-334 §11): where no launch value names a Docling image, the launch
 * form's field holds {@code compose.gpu.yaml}'s image, the list offers it first and the processor image after it, and a
 * Run gives the child the GPU image; the jar's own default, in {@code application.yaml}, stays the processor image.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
class TheGpuImageIsPreselectedScreenTest {

    private static final String IMAGE_FIELD = "vespera.docling.image";

    private static final String GPU = ControlPanelPage.DOCLING_IMAGE_GPU;

    private static final String CPU = ControlPanelPage.DOCLING_IMAGE_CPU;

    /** The jar's own configuration, which the control panel's choice does not change. */
    private static final Path APPLICATION_YAML = Path.of("src", "main", "resources", "application.yaml");

    @TempDir
    Path folder;

    @Test
    @Story("The operator runs on the graphics card without choosing it")
    @DisplayName("With no image named, the launch page holds the GPU image, offers it first and the processor image after it, and says the control panel chose it")
    void theGpuImageIsPreselectedAndListedFirst() throws IOException {
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpus(), work()))) {
            Page page = screens.browser().page();

            claim("the field holds the GPU image",
                    () -> assertThat(form(page, "launch").select("[name='" + IMAGE_FIELD + "']").val()).isEqualTo(GPU));
            claim("the list offers the GPU image first, then the processor image, each by its label",
                    () -> assertThat(byId(page, "docling-images").select("option"))
                            .extracting(option -> option.attr("label"), option -> option.attr("value"))
                            .containsExactly(tuple("GPU", GPU), tuple("CPU", CPU)));
            claim("and beside it the page says the control panel chose it",
                    () -> assertThat(List.of(byId(page, "in-use-" + IMAGE_FIELD).attr("data-value"),
                                    byId(page, "in-use-" + IMAGE_FIELD).attr("data-source")))
                            .containsExactly(GPU, "preselected"));
        }
    }

    @Test
    @Story("The operator runs on the graphics card without choosing it")
    @DisplayName("A Run with the image left as the page holds it gives the child the GPU image, and the jar's own default stays the processor image")
    void aRunGivesTheChildTheGpuImage() throws IOException, InterruptedException {
        Path work = work();
        ScreenServer.childWill(work, "exit 0 the closing line");
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpus(), work))) {
            screens.afterARunHasEnded();
            LaunchValues stored = LaunchValues.load(LaunchValues.fileIn(folder.resolve("tmp"),
                    System.getProperty("user.name")), System.getProperty("user.name"), line -> {
                    });

            claim("the launch values stored as the child started name the GPU image",
                    () -> assertThat(stored.doclingImage()).isEqualTo(GPU));
            claim("and the child is given it in its environment",
                    () -> assertThat(stored.toChildEnvironment()).containsEntry("VESPERA_DOCLING_IMAGE", GPU));
        }
        claim("the jar's own configuration still names the processor image",
                () -> assertThat(Files.readString(APPLICATION_YAML, UTF_8)).contains("image: " + CPU));
    }

    @Test
    @Story("The operator runs on the graphics card without choosing it")
    @DisplayName("The processor image picked is held and passed as picked")
    void theProcessorImagePickedIsHeld() throws IOException {
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpus(), work()))) {
            Page checked = screens.browser().press("launch", "check", Map.of(IMAGE_FIELD, CPU)).page();

            claim("the field holds the processor image, named by the operator",
                    () -> assertThat(List.of(form(checked, "launch").select("[name='" + IMAGE_FIELD + "']").val(),
                                    byId(checked, "in-use-" + IMAGE_FIELD).attr("data-source")))
                            .containsExactly(CPU, "launch-value"));
        }
    }

    private Path corpus() throws IOException {
        return Files.createDirectories(folder.resolve("corpus"));
    }

    private Path work() throws IOException {
        return Files.createDirectories(folder.resolve("work"));
    }
}
