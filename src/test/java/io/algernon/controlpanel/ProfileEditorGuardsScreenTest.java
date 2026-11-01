package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What ADR-330 keeps of the control panel as it stands, and building the record could undo: each passes today, carries
 * no {@code @Disabled}, and goes red where §5, §11 or §13 is built the wrong way.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class ProfileEditorGuardsScreenTest {

    /** The image picked, which is only ever a launch value. */
    private static final String AN_IMAGE = "vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0-r2";

    @TempDir
    Path folder;

    @Test
    @Story("The operator writes the values the next invocation wants")
    @DisplayName("Save, then Run starts the run only once every value it was given is in the file")
    void saveThenRunStartsTheRunOnceEveryKeyIsWritten() throws IOException, InterruptedException {
        Path work = Files.createDirectories(folder.resolve("work"));
        Path seeds = Files.createDirectories(folder.resolve("seeds"));
        Files.writeString(work.resolve(NextInvocation.FILE_NAME), """
                invocation=run
                wants=seedFolder boilerplateDocumentFrequencyFloor
                answer=false
                """, UTF_8);
        ScreenServer.childWill(work, "exit 0 the closing line");
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpus(), work))) {
            PanelBrowser browser = screens.afterARunHasEnded().go("/next");
            ScreenServer.childWill(work, JarForScreenTests.COPY_THE_PROFILE + "exit 0 the closing line");

            browser.press("next", "save-then-run", Map.of(
                    "seedFolder", seeds.toString(),
                    "seedFolder.provenance", "the operator's exemplars",
                    "boilerplateDocumentFrequencyFloor", "0.35",
                    "boilerplateDocumentFrequencyFloor.provenance", "read off the document frequency page"));
            ScreenServer.whenTheChildHasEnded(browser);

            Map<ProfileKey, ProfileEdit.Stated> found =
                    ProfileEdit.read(Files.readString(work.resolve(JarForScreenTests.THE_PROFILE_IT_FOUND), UTF_8));
            claim("the child, as it started, found both values in the file, the last written included",
                    () -> assertThat(List.of(found.get(ProfileKey.SEED_FOLDER),
                                    found.get(ProfileKey.BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR)))
                            .containsExactly(
                                    new ProfileEdit.Stated(seeds.toString(), "the operator's exemplars"),
                                    new ProfileEdit.Stated("0.35", "read off the document frequency page")));
        }
    }

    @Test
    @Story("The operator repairs a profile not in the jar's form")
    @DisplayName("The repair's address refuses a form posted from another site, before anything handles it")
    void theRepairsAddressRefusesAFormFromAnotherSite() throws IOException {
        Path work = Files.createDirectories(folder.resolve("work"));
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpus(), work))) {
            InetSocketAddress server = screens.server().address();
            RawHttp.Response posted = RawHttp.send(server, "POST", "/profile-repair",
                    List.of("Host: 127.0.0.1:" + server.getPort(), "Origin: http://evil.example"));

            claim("it is refused as a form from another site is, whatever the address",
                    () -> assertThat(posted.status()).isEqualTo(403));
        }
    }

    @Test
    @Story("The operator is not caught by an image the sidecar does not run")
    @DisplayName("The image picked is kept as a launch value as an invocation starts, and no file of the working folder holds it")
    void theImagePickedIsHeldAsALaunchValueAndInNoFileOfTheWorkingDirectory() throws IOException, InterruptedException {
        Path work = Files.createDirectories(folder.resolve("work"));
        ScreenServer.childWill(work, "exit 0 the closing line");
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpus(), work))) {
            PanelBrowser browser = screens.browser().press("launch", "run", Map.of("vespera.docling.image", AN_IMAGE));
            ScreenServer.whenTheChildHasEnded(browser);

            String user = System.getProperty("user.name");
            LaunchValues kept = LaunchValues.load(LaunchValues.fileIn(folder.resolve("tmp"), user), user, line -> {
                throw new AssertionError(line);
            });
            claim("the launch values' own file holds it, for the next start",
                    () -> assertThat(kept.doclingImage()).isEqualTo(AN_IMAGE));
            claim("and no file of the working folder does",
                    () -> assertThat(filesOf(work)).noneMatch(file -> holds(file, AN_IMAGE)));
        }
    }

    private Path corpus() throws IOException {
        return Files.createDirectories(folder.resolve("corpus"));
    }

    private static List<Path> filesOf(Path workingDirectory) throws IOException {
        try (Stream<Path> files = Files.walk(workingDirectory)) {
            return files.filter(Files::isRegularFile).toList();
        }
    }

    private static boolean holds(Path file, String text) {
        try {
            return new String(Files.readAllBytes(file), UTF_8).contains(text);
        } catch (IOException e) {
            throw new AssertionError("the file " + file + " could not be read", e);
        }
    }
}
