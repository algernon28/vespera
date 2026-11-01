package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

/**
 * The pickers that are controls a browser draws, with no script (ADR-331 §4, §7): the models Ollama reports, the two
 * Docling images, a select of the deliverable's two languages, {@code auto} for the approval, and a number field with no
 * range for a number. Each key's control is the same on the profile page and the next act's.
 *
 * <p>A control a browser draws is held by what the page writes: no test runs a browser (ADR-297 §1), so what it offers
 * from a list is the specification's.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-331", url = Adr.THE_CONTROL_PANEL_OPENS_A_WINDOW_OF_ITS_OWN_AND_ITS_PAGES_ARE_A_COCKPIT, type = "adr")
class NativePickersScreenTest {

    /** The models the stub Ollama serves, in the order it answers them, which is neither sorted nor reversed. */
    private static final List<String> MODELS = List.of("qwen3:8b", "bge-m3:latest", "llama3.1:70b");

    /** The list of models a field names. */
    private static final String MODELS_LIST = "models";

    /** The compose file that names the GPU image, at the repository root the tests run from. */
    private static final Path GPU_FILE = Path.of("compose.gpu.yaml");

    /** The service that names it there. */
    private static final String DOCLING_SERVICE = "docling-serve";

    /** Every profile key, as the next act's file names what it wants. */
    private static final String EVERY_KEY = Arrays.stream(ProfileKey.values()).map(ProfileKey::key)
            .collect(Collectors.joining(" "));

    /** An arrangement to approve, twelve hexadecimal characters, for the next act's file that wants its approval. */
    private static final String ARRANGEMENT = "0123456789ab";

    /** The six pages the sidebar leads to and the folder page. */
    private static final int PAGES_THAT_HOLD_A_FORM = 7;

    /** The folder under which the pages below were asked for, once for the class. */
    @TempDir
    static Path folderOfThePages;

    /**
     * By path, the pages the tests that write nothing read, with a next act wanting every key: {@code /profile} before
     * a child ended, while it holds every key's form, and every other after one.
     */
    private static Map<String, Page> pagesWantingEveryKey;

    @TempDir
    Path folder;

    @BeforeAll
    static void askForThePagesWantingEveryKey() throws IOException, InterruptedException {
        Path workingDirectory = wantingEveryKey(folderOfThePages);
        Path corpusRoot = Files.createDirectories(folderOfThePages.resolve("corpus"));
        Map<String, Page> pages = new LinkedHashMap<>();
        try (ScreenServer screens = ScreenServer.start(folderOfThePages,
                ScreenServer.values(corpusRoot, workingDirectory))) {
            pages.put("/profile", screens.browser().go("/profile").page());
            PanelBrowser afterARun = screens.afterARunHasEnded();
            for (String path : List.of("/", "/invocation", "/next", "/labels", "/code-sets",
                    "/folders?for=vespera.corpus-root")) {
                pages.put(path, afterARun.go(path).page());
            }
        }
        pagesWantingEveryKey = pages;
    }

    @Test
    @Story("The operator picks a model")
    @DisplayName("The label model offers the models the model service reports, in its order, though no model is named yet, and that check still says nothing is named")
    void theLabelModelOffersTheModelsOllamaReports() throws IOException {
        try (SidecarsForTests sidecars = SidecarsForTests.answering("an-image", MODELS);
                ScreenServer screens = ScreenServer.start(folder, new LaunchValues("", "", "", "", "",
                        sidecars.address(), "", ""))) {
            Page page = screens.browser().page();

            claim("the label model's field names the list of models",
                    () -> assertThat(form(page, "launch").select("[name='vespera.label.model']").attr("list"))
                            .isEqualTo(MODELS_LIST));
            claim("the list holds each model the service reports, in the order it reports them",
                    () -> assertThat(byId(page, MODELS_LIST).select("option").eachAttr("value"))
                            .containsExactlyElementsOf(MODELS));
            claim("and the check still says not checked, no model being named for it to find",
                    () -> assertThat(byId(page, "ollama-check").attr("data-check")).isEqualTo("not-checked"));
            claim("the service was asked once",
                    () -> assertThat(sidecars.tagsAsked()).isEqualTo(1));
        }
    }

    @Test
    @Story("The operator picks a model")
    @DisplayName("With nobody answering, the list of models is there and offers nothing, and the field is still typed into")
    void withNobodyAnsweringTheListOffersNothing() throws IOException {
        try (ScreenServer screens = ScreenServer.start(folder, new LaunchValues("", "", "", "", "",
                SidecarsForTests.nobody(), "", ""))) {
            Page page = screens.browser().page();

            claim("the list is there, with no model in it",
                    () -> assertThat(byId(page, MODELS_LIST).select("option")).isEmpty());
            claim("and the field is a text field still",
                    () -> assertThat(form(page, "launch").select("[name='vespera.label.model']").attr("type"))
                            .isEqualTo("text"));
        }
    }

    /** Only the launch page asks (ADR-302 §5): a page's models are what it was last told, and a suggestion in any case. */
    @Test
    @Story("The operator picks a model")
    @DisplayName("The embedding model and the generation model offer the same list on the profile page and the next act's, asking the model service nothing more")
    void theEmbeddingAndGenerationModelsOfferTheSameList() throws IOException, InterruptedException {
        Path workingDirectory = wantingEveryKey(folder);
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        try (SidecarsForTests sidecars = SidecarsForTests.answering("an-image", MODELS);
                ScreenServer screens = ScreenServer.start(folder, new LaunchValues(corpusRoot.toString(),
                        workingDirectory.toString(), "", "", "", sidecars.address(), "", ""))) {
            PanelBrowser browser = screens.browser();
            int askedBeforeTheProfilePage = sidecars.tagsAsked();
            Page profile = browser.go("/profile").page();
            int askedAfterTheProfilePage = sidecars.tagsAsked();
            PanelBrowser afterARun = screens.afterARunHasEnded();
            int askedBeforeTheNextPage = sidecars.tagsAsked();
            Page next = afterARun.go("/next").page();
            int askedAfterTheNextPage = sidecars.tagsAsked();

            claim("on the profile page, each names the list, and the list holds the models",
                    () -> {
                        assertThat(List.of(form(profile, "embeddingModel").select("[name=value]").attr("list"),
                                        form(profile, "generationModel").select("[name=value]").attr("list")))
                                .containsOnly(MODELS_LIST);
                        assertThat(byId(profile, MODELS_LIST).select("option").eachAttr("value"))
                                .containsExactlyElementsOf(MODELS);
                    });
            claim("on the next act's page, the same",
                    () -> {
                        assertThat(List.of(form(next, "next").select("[name=embeddingModel]").attr("list"),
                                        form(next, "next").select("[name=generationModel]").attr("list")))
                                .containsOnly(MODELS_LIST);
                        assertThat(byId(next, MODELS_LIST).select("option").eachAttr("value"))
                                .containsExactlyElementsOf(MODELS);
                    });
            claim("and neither page asked the service: the launch page's answer is what they offer",
                    () -> assertThat(List.of(askedAfterTheProfilePage, askedAfterTheNextPage))
                            .containsExactly(askedBeforeTheProfilePage, askedBeforeTheNextPage));
        }
    }

    /**
     * The two values are read from the files that decide them, so that a change of image cannot leave the list behind:
     * the jar's default from {@code application.yaml} and the GPU image from {@code compose.gpu.yaml} (ADR-147, ADR-170).
     */
    @Test
    @Story("The operator picks a converter's image")
    @DisplayName("The Docling image offers the processor image the jar defaults to and the GPU image the GPU compose file runs, each by its label")
    void theDoclingImageOffersTheJarsDefaultAndTheGpuComposeFilesImage() throws IOException {
        String cpu = theJarsDefaultImage();
        String gpu = theGpuImage();
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none())) {
            Page page = screens.browser().page();

            claim("the field names the list of images",
                    () -> assertThat(form(page, "launch").select("[name='vespera.docling.image']").attr("list"))
                            .isEqualTo("docling-images"));
            claim("the list offers the processor image and then the GPU image, each labelled and each whole, as the"
                            + " files that run them name them",
                    () -> assertThat(byId(page, "docling-images").select("option"))
                            .extracting(option -> option.attr("label"), option -> option.attr("value"))
                            .containsExactly(tuple("CPU", cpu), tuple("GPU", gpu)));
        }
    }

    @Test
    @Story("The operator writes a profile value")
    @DisplayName("The deliverable's language is a choice of en and it on both pages, the value the profile holds chosen")
    void theDeliverablesLanguageIsASelectOfTheTwoLanguages() throws IOException, InterruptedException {
        Path workingDirectory = wantingEveryKey(folder);
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        String languageKey = ProfileKey.DELIVERABLE_LANGUAGE.key();
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser().go("/profile");
            Page unset = browser.page();

            claim("the profile page's control is a select of en and it, and of nothing else",
                    () -> assertThat(form(unset, languageKey).select("select[name=value] option").eachAttr("value"))
                            .containsExactly("en", "it"));

            Page landed = browser.press(languageKey, "save", Map.of("value", "it", "provenance", "the readers read it"))
                    .page();

            claim("choosing one writes it into the profile",
                    () -> assertThat(ProfileEdit.read(Files.readString(workingDirectory.resolve(ProfileEdit.FILE_NAME),
                                    StandardCharsets.UTF_8)).get(ProfileKey.DELIVERABLE_LANGUAGE).value())
                            .isEqualTo("it"));
            claim("and the page then shows the value held as the one chosen",
                    () -> assertThat(form(landed, languageKey).select("select[name=value] option[selected]")
                            .eachAttr("value")).containsExactly("it"));

            Page next = screens.afterARunHasEnded().go("/next").page();

            claim("the next act's control is the same select, and shows the same value",
                    () -> {
                        assertThat(form(next, "next").select("select[name=" + languageKey + "] option")
                                .eachAttr("value")).containsExactly("en", "it");
                        assertThat(form(next, "next").select("select[name=" + languageKey + "] option[selected]")
                                .eachAttr("value")).containsExactly("it");
                    });
        }
    }

    @Test
    @Story("The operator writes a profile value")
    @DisplayName("The approval of an arrangement offers auto, on both pages, and the next act's stays filled with the arrangement to approve")
    void theApprovalOffersAuto() {
        String approvalKey = ProfileKey.ARRANGEMENT_APPROVED.key();
        Page profile = pagesWantingEveryKey.get("/profile");
        Page next = pagesWantingEveryKey.get("/next");
        Element onTheProfile = form(profile, approvalKey).select("[name=value]").first();
        Element onTheNext = form(next, "next").select("[name=" + approvalKey + "]").first();

        claim("the field names a list on both pages",
                () -> assertThat(List.of(onTheProfile.attr("list"), onTheNext.attr("list")))
                        .doesNotContain(""));
        claim("on the profile page, that list offers auto and nothing else",
                () -> assertThat(byId(profile, onTheProfile.attr("list")).select("option").eachAttr("value"))
                        .containsExactly("auto"));
        claim("on the next act's page, the same",
                () -> assertThat(byId(next, onTheNext.attr("list")).select("option").eachAttr("value"))
                        .containsExactly("auto"));
        claim("and the next act's field is still filled with the arrangement to approve",
                () -> assertThat(onTheNext.val()).isEqualTo(ARRANGEMENT));
    }

    /** A threshold's range is no decision anyone made, so the page bounds nothing and {@link ProfileEdit} checks it. */
    @Test
    @Story("The operator writes a profile value")
    @DisplayName("A number is a number field, any step, with no least, no most and no slider on any page, and every other value a text field")
    void aNumberIsANumberFieldWithNoRangeAndNoSlider() {
        Page page = pagesWantingEveryKey.get("/profile");
        Map<String, Integer> slidersOnEachPage = new LinkedHashMap<>();
        pagesWantingEveryKey.forEach((path, each) ->
                slidersOnEachPage.put(path, each.document().select("input[type=range]").size()));
        Map<String, List<String>> shapes = new LinkedHashMap<>();
        Map<String, List<String>> expected = new LinkedHashMap<>();
        for (ProfileKey key : ProfileKey.values()) {
            if (key != ProfileKey.DELIVERABLE_LANGUAGE) {
                Element control = form(page, key.key()).select("[name=value]").first();
                shapes.put(key.key(), List.of(control.attr("type"), control.attr("step"), control.attr("min"),
                        control.attr("max")));
                expected.put(key.key(), key.kind() == ProfileKey.Kind.NUMBER ? List.of("number", "any", "", "")
                        : List.of("text", "", "", ""));
            }
        }

        claim("each value's control has the type its kind gives it, a number any step and no bounds",
                () -> assertThat(shapes).containsExactlyEntriesOf(expected));
        claim("and no page holds a slider, of the six the sidebar leads to and the folder page",
                () -> {
                    assertThat(slidersOnEachPage).hasSize(PAGES_THAT_HOLD_A_FORM);
                    assertThat(slidersOnEachPage.values()).containsOnly(0);
                });
    }

    /** A browser does not send a number field what is no number; a post that does is still {@link ProfileEdit}'s to refuse. */
    @Test
    @Story("The operator writes a profile value")
    @DisplayName("A posted number the jar would not read is refused as before, and nothing is written")
    void aPostedValueStillGoesThroughTheProfilesOwnCheck() throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(
                Files.createDirectories(folder.resolve("corpus")), workingDirectory))) {
            String key = ProfileKey.RELEVANCE_SCORE_FLOOR.key();
            Page landed = screens.browser().go("/profile").press(key, "save",
                    Map.of("value", "not a number", "provenance", "a reason")).page();

            claim("the page says the value is refused",
                    () -> assertThat(byId(landed, "outcome").text()).contains(key + " is not saved"));
            claim("and no profile was written",
                    () -> assertThat(Files.exists(workingDirectory.resolve(ProfileEdit.FILE_NAME))).isFalse());
        }
    }

    /** What the operator meets is the same whichever page asks: one control per key, with one example and one list. */
    @Test
    @Story("The operator writes a profile value")
    @DisplayName("Each value's control is the same on the profile page and the next act's: the same element, type, step, list and example")
    void eachValuesControlIsTheSameOnBothPages() {
        Page profile = pagesWantingEveryKey.get("/profile");
        Page next = pagesWantingEveryKey.get("/next");
        Map<String, List<String>> onTheProfile = new LinkedHashMap<>();
        Map<String, List<String>> onTheNext = new LinkedHashMap<>();
        for (ProfileKey key : ProfileKey.values()) {
            onTheProfile.put(key.key(), shapeOf(form(profile, key.key()).select("[name=value]").first()));
            onTheNext.put(key.key(), shapeOf(form(next, "next").select("[name=" + key.key() + "]").first()));
        }

        claim("every value's control is the same on both pages",
                () -> assertThat(onTheNext).containsExactlyEntriesOf(onTheProfile));
    }

    private static List<String> shapeOf(Element control) {
        return List.of(control.tagName(), control.attr("type"), control.attr("step"), control.attr("list"),
                control.attr("placeholder"));
    }

    /** A working directory under {@code under} where a child ending with 0 leaves a next act wanting every key, and an approval among them. */
    private static Path wantingEveryKey(Path under) throws IOException {
        Path workingDirectory = Files.createDirectories(under.resolve("work"));
        Files.writeString(workingDirectory.resolve(NextInvocation.FILE_NAME), """
                invocation=run
                wants=%s
                answer=false
                arrangement=%s
                """.formatted(EVERY_KEY, ARRANGEMENT), StandardCharsets.UTF_8);
        ScreenServer.childWill(workingDirectory, "exit 0 the closing line");
        return workingDirectory;
    }

    @SuppressWarnings("unchecked")
    private static String theJarsDefaultImage() throws IOException {
        try (InputStream in = NativePickersScreenTest.class.getResourceAsStream("/application.yaml")) {
            Map<String, Object> application = new Yaml().load(in);
            Map<String, Object> docling =
                    (Map<String, Object>) ((Map<String, Object>) application.get("vespera")).get("docling");
            return (String) docling.get("image");
        }
    }

    @SuppressWarnings("unchecked")
    private static String theGpuImage() throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(GPU_FILE));
        Map<String, Object> service =
                (Map<String, Object>) ((Map<String, Object>) compose.get("services")).get(DOCLING_SERVICE);
        return (String) service.get("image");
    }
}
