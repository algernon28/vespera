package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.buttonNames;
import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.controlNames;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.algernon.vespera.pipeline.CorpusRootLock;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The launch page, {@code /} (ADR-302 §2, §4, §5, §6): the seven launch values by their keys with Run, Label and
 * Check, the holder lines, the seed folder's own form, the sidecars read, and the free space.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("561")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
class LaunchScreenTest {

    /** The seven launch values' keys, in the order the launch form holds their controls. */
    private static final List<String> THE_SEVEN_KEYS = List.of("vespera.corpus-root", "vespera.working-dir",
            "vespera.deliverable-root", "vespera.docling.image", "vespera.docling.base-url",
            "spring.ai.ollama.base-url", "vespera.label.model");

    /** The line the launch values give where Run is pressed with no corpus root. */
    private static final String RUN_NEEDS_THE_CORPUS_ROOT = "Run needs the corpus root";

    /** The image the launch values name, which the stub Docling does not run. */
    private static final String IMAGE_NAMED = "ghcr.io/docling-project/docling-serve-cpu:v1.0.0";

    /** The image the stub Docling says it runs. */
    private static final String IMAGE_RUN = "ghcr.io/docling-project/docling-serve-cu128:v1.0.0";

    /** The label model the launch values name, which the stub Ollama does not serve. */
    private static final String MODEL_NOT_SERVED = "llama3.1:70b";

    @TempDir
    Path folder;

    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("The launch form holds the seven values by their names, as last started, with Run, Label and Check")
    void theLaunchFormHoldsTheSevenLaunchValuesByTheirKeysAsLastStarted() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path deliverableRoot = Files.createDirectories(folder.resolve("deliverables"));
        String nobody = "http://127.0.0.1:" + aPortNobodyListensOn();
        LaunchValues values = new LaunchValues(corpusRoot.toString(), workingDirectory.toString(),
                deliverableRoot.toString(), IMAGE_NAMED, nobody, nobody, MODEL_NOT_SERVED, "");
        try (ScreenServer screens = ScreenServer.start(folder, values)) {
            Page page = screens.browser().page();
            Element launch = form(page, "launch");

            claim("the launch form is posted to its own path",
                    () -> assertThat(List.of(launch.attr("method").toLowerCase(), launch.attr("action")))
                            .containsExactly("post", "/launch"));
            claim("it holds one control per launch value, named by the value's key, in their order",
                    () -> assertThat(controlNames(launch)).containsExactlyElementsOf(THE_SEVEN_KEYS));
            claim("each control holds the value the last invocation was started with",
                    () -> assertThat(THE_SEVEN_KEYS.stream().map(key -> launch.select("[name='" + key + "']").val()))
                            .containsExactly(corpusRoot.toString(), workingDirectory.toString(),
                                    deliverableRoot.toString(), IMAGE_NAMED, nobody, nobody, MODEL_NOT_SERVED));
            claim("its buttons are Run, Label and Check, in that order",
                    () -> assertThat(buttonNames(launch)).containsExactly("run", "label", "check"));
        }
    }

    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("The free space of the working folder's drive and of the deliverable folder's is shown, each in bytes")
    void theFreeSpaceOfTheWorkingDirectorysAndTheDeliverableRootsDrivesIsShown() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path deliverableRoot = Files.createDirectories(folder.resolve("deliverables"));
        LaunchValues values = new LaunchValues(corpusRoot.toString(), workingDirectory.toString(),
                deliverableRoot.toString(), "", "", "", "", "");
        try (ScreenServer screens = ScreenServer.start(folder, values)) {
            Page page = screens.browser().page();

            claim("the free space names the working folder's drive and the deliverable folder's, in that order, each"
                            + " with its usable bytes as a whole number",
                    () -> assertThat(byId(page, "free-space").select("[data-folder]"))
                            .extracting(item -> item.attr("data-folder"),
                                    item -> item.attr("data-usable-bytes").matches("\\d+"))
                            .containsExactly(
                                    tuple("working-directory", true),
                                    tuple("deliverable-root", true)));
            claim("beside them, the rules the space needed is read off, as text",
                    () -> assertThat(byId(page, "free-space-rules").text()).isNotBlank());
        }
    }

    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("Run with no corpus folder is refused with the line naming what it needs, and starts nothing")
    void runIsRefusedWithTheLineOfTheValueItNeeds() throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        LaunchValues values = new LaunchValues("", workingDirectory.toString(), "", "", "", "", "", "");
        try (ScreenServer screens = ScreenServer.start(folder, values)) {
            PanelBrowser browser = screens.browser();
            Page landed = browser.press("launch", "run", Map.of()).page();
            Page invocation = browser.go("/invocation").page();

            claim("pressing Run lands back on the launch page, with the line naming the corpus folder it needs",
                    () -> assertThat(List.of(landed.address().getPath(), byId(landed, "outcome").text()))
                            .containsExactly("/", RUN_NEEDS_THE_CORPUS_ROOT));
            claim("and no child was started",
                    () -> assertThat(byId(invocation, "child").attr("data-ending")).isEqualTo("none"));
        }
    }

    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("While another invocation holds the working folder, its line is shown and Run and Label are disabled")
    void runAndLabelAreDisabledWhileAnotherInvocationHoldsTheWorkingDirectory() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        String holderLine = WorkingDirectoryWrite.holderLine(ProcessHandle.current().pid(),
                OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS), "profile.yaml");
        Files.writeString(workingDirectory.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME),
                holderLine + System.lineSeparator(), StandardCharsets.UTF_8);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            Page page = screens.browser().page();
            Element launch = form(page, "launch");

            claim("the holder line of the working folder's lock, held by this live process, is shown whole",
                    () -> assertThat(byId(page, "holder-working-directory").text()).isEqualTo(holderLine));
            claim("Run and Label are disabled and Check is not",
                    () -> assertThat(List.of("run", "label", "check").stream()
                                    .map(button -> launch.select("[name='" + button + "']").hasAttr("disabled")))
                            .containsExactly(true, true, false));
        }
    }

    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("Run pressed after another invocation took the working folder is refused with a line naming it, and starts nothing")
    void runPressedOnceTheWorkingDirectoryIsHeldIsRefusedWithALineOfItsOwn() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        String holderLine = WorkingDirectoryWrite.holderLine(ProcessHandle.current().pid(),
                OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS), "profile.yaml");
        ScreenServer.childWill(workingDirectory, "sleep");
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser();
            Files.writeString(workingDirectory.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME),
                    holderLine + System.lineSeparator(), StandardCharsets.UTF_8);
            Page landed = browser.press("launch", "run", Map.of()).page();
            Page invocation = browser.go("/invocation").page();

            claim("Run, pressed on a page shown before the working folder was taken, lands back on the launch page with"
                            + " the line naming the folder and the line of the invocation holding it",
                    () -> assertThat(List.of(landed.address().getPath(), byId(landed, "outcome").text()))
                            .containsExactly("/", "Run is not started: the working directory " + workingDirectory
                                    + " is held by another Vespera invocation (" + holderLine + ")"));
            claim("and no child was started",
                    () -> assertThat(byId(invocation, "child").attr("data-ending")).isEqualTo("none"));
        }
    }

    /**
     * The corpus-root lock's file is named as the jar's {@code CorpusRootLock} names it (ADR-279), in the folder the
     * control panel reads it in, so the test writes it where the jar would and the control panel has to find it there.
     */
    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("While another invocation walks the corpus folder, its line is shown and Run and Label are disabled")
    void runAndLabelAreDisabledWhileAnotherInvocationHoldsTheCorpusRoot() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path anotherWorkingDirectory = Files.createDirectories(folder.resolve("another"));
        String holderLine = "pid=" + ProcessHandle.current().pid()
                + " started=" + OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                + " command=run " + corpusRoot + " root=" + CorpusRootLock.key(corpusRoot)
                + " working-directory=" + anotherWorkingDirectory;
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            Files.writeString(
                    screens.corpusRootLockFolder().resolve(CorpusRootLock.fileName(CorpusRootLock.key(corpusRoot))),
                    holderLine + System.lineSeparator(), StandardCharsets.UTF_8);
            Page page = screens.browser().page();
            Element launch = form(page, "launch");

            claim("the holder line of the corpus folder's lock, held by this live process, is shown whole, and the"
                            + " working folder's lock is held by nobody",
                    () -> assertThat(List.of(byId(page, "holder-corpus-root").text(),
                                    byId(page, "holder-working-directory").text()))
                            .containsExactly(holderLine, ""));
            claim("Run and Label are disabled and Check is not",
                    () -> assertThat(List.of("run", "label", "check").stream()
                                    .map(button -> launch.select("[name='" + button + "']").hasAttr("disabled")))
                            .containsExactly(true, true, false));
        }
    }

    @Test
    @Story("The operator writes the values the next invocation wants")
    @DisplayName("The seed folder has a form of its own, filled with the last one, and saving it writes it into the profile with its provenance")
    void theSeedFolderIsAFormOfItsOwnFilledFromTheLastSeedFolderAndSavedIntoTheProfile() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path seedFolder = Files.createDirectories(folder.resolve("seeds"));
        String provenance = "the exemplars the operator chose";
        LaunchValues values = new LaunchValues(corpusRoot.toString(), workingDirectory.toString(), "", "", "", "",
                "", seedFolder.toString());
        try (ScreenServer screens = ScreenServer.start(folder, values)) {
            PanelBrowser browser = screens.browser();
            Element seedForm = form(browser.page(), "seed-folder");

            claim("the seed folder's form holds the folder and its provenance, and one Save",
                    () -> assertThat(List.of(controlNames(seedForm), buttonNames(seedForm)))
                            .containsExactly(List.of("seedFolder", "provenance"), List.of("save")));
            claim("the folder is filled with the last seed folder, and the provenance is left for the operator",
                    () -> assertThat(List.of(seedForm.select("[name=seedFolder]").val(),
                                    seedForm.select("[name=provenance]").val()))
                            .containsExactly(seedFolder.toString(), ""));

            Page landed = browser.press("seed-folder", "save", Map.of("provenance", provenance)).page();
            Map<ProfileKey, ProfileEdit.Stated> profile = ProfileEdit.read(
                    Files.readString(workingDirectory.resolve(ProfileEdit.FILE_NAME), StandardCharsets.UTF_8));

            claim("saving lands back on the launch page",
                    () -> assertThat(landed.address().getPath()).isEqualTo("/"));
            claim("and the profile in the working folder holds the seed folder with the provenance given",
                    () -> assertThat(profile.get(ProfileKey.SEED_FOLDER))
                            .isEqualTo(new ProfileEdit.Stated(seedFolder.toString(), provenance)));
        }
    }

    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("A converter running another image, or a model service not serving the model named, is warned of beside Run, which stays enabled")
    void aSidecarRunningAnotherImageOrServingNoNamedModelIsWarnedOfAndRunStaysEnabled() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        HttpServer sidecars = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        sidecars.createContext("/version", exchange -> answer(exchange, "{\"vespera-image\": \"" + IMAGE_RUN + "\"}"));
        sidecars.createContext("/api/tags",
                exchange -> answer(exchange, "{\"models\": [{\"name\": \"qwen3:8b\", \"size\": 5}]}"));
        sidecars.start();
        try {
            String stub = "http://127.0.0.1:" + sidecars.getAddress().getPort();
            String nobody = "http://127.0.0.1:" + aPortNobodyListensOn();
            LaunchValues answering = new LaunchValues(corpusRoot.toString(), workingDirectory.toString(), "",
                    IMAGE_NAMED, stub, stub, MODEL_NOT_SERVED, "");
            try (ScreenServer screens = ScreenServer.start(folder, answering)) {
                PanelBrowser browser = screens.browser();
                Page page = browser.page();

                claim("the converter's check says it runs another image, and the model service's that a model named"
                                + " is missing",
                        () -> assertThat(List.of(byId(page, "docling-check").attr("data-check"),
                                        byId(page, "ollama-check").attr("data-check")))
                                .containsExactly("another-image", "missing"));
                claim("each is one warning beside Run, the two and no other",
                        () -> assertThat(byId(page, "warnings").select("li")).hasSize(2));
                claim("and Run stays enabled",
                        () -> assertThat(form(page, "launch").select("[name=run]").hasAttr("disabled")).isFalse());

                Page checked = browser.press("launch", "check",
                        Map.of("vespera.docling.base-url", nobody, "spring.ai.ollama.base-url", nobody)).page();
                claim("checked again at addresses nobody answers, both are shown as not answering",
                        () -> assertThat(List.of(byId(checked, "docling-check").attr("data-check"),
                                        byId(checked, "ollama-check").attr("data-check")))
                                .containsExactly("not-answering", "not-answering"));
            }
        } finally {
            sidecars.stop(0);
        }
    }

    /**
     * No {@code GET} of the control panel changes anything (ADR-302 §1): another site's page can open any of its
     * addresses with the cookie, so a {@code GET} carrying a form's fields in its query neither writes nor starts.
     */
    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("Opening any page of the control panel, the paths forms post to among them and with a form's fields in the address, writes nothing and starts nothing")
    void noGetOfAnyPageWritesAFileOrStartsAChild() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path seedFolder = Files.createDirectories(folder.resolve("seeds"));
        Files.writeString(workingDirectory.resolve(ProfileEdit.FILE_NAME), "---\n", StandardCharsets.UTF_8);
        Files.writeString(workingDirectory.resolve(CodeSetRenames.FILE_NAME), "--- []\n", StandardCharsets.UTF_8);
        ScreenServer.childWill(workingDirectory, "sleep");
        List<String> asked = List.of("/", "/invocation", "/stop", "/next", "/labels", "/code-sets", "/profile",
                "/launch", "/launch?run=",
                "/seed-folder?seedFolder=" + URLEncoder.encode(seedFolder.toString(), StandardCharsets.UTF_8)
                        + "&provenance=p&save=",
                "/label-auto?label-auto=", "/stop?stop=", "/next?save-then-run=",
                "/code-sets?new-folder=app&new-name=app&save=",
                "/profile?key=extractionAttempt&value=2&provenance=p&save=");
        Map<Path, byte[]> before = contents(workingDirectory);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser();
            List<Integer> statuses = new ArrayList<>();
            for (String path : asked) {
                statuses.add(browser.go(path).page().status());
            }
            Page invocation = browser.go("/invocation").page();
            Map<Path, byte[]> after = contents(workingDirectory);

            claim("each address is answered, after any redirect, with a page",
                    () -> assertThat(statuses).containsOnly(200).hasSize(asked.size()));
            claim("the working folder holds the same files, byte for byte, as before",
                    () -> assertThat(after.keySet()).containsExactlyInAnyOrderElementsOf(before.keySet())
                            .allSatisfy(file -> assertThat(after.get(file)).isEqualTo(before.get(file))));
            claim("and no child was started",
                    () -> assertThat(byId(invocation, "child").attr("data-ending")).isEqualTo("none"));
        }
    }

    private static Map<Path, byte[]> contents(Path folder) throws IOException {
        Map<Path, byte[]> contents = new TreeMap<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.toList()) {
                contents.put(file.getFileName(), Files.readAllBytes(file));
            }
        }
        return contents;
    }

    private static int aPortNobodyListensOn() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static void answer(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
