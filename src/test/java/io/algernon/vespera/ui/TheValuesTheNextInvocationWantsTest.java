package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.algernon.vespera.ui.ProfileEdit.Stated;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Each value the next invocation wants is edited in place in {@code wanted}, with its reports (ADR-344 §1, §6, keeping
 * ADR-294 and ADR-306 §3): a report of the key that the working directory holds is a link there, opened in its own tab
 * of the window, as on the key's row of the Profile tab. A wanted value the profile states none for is offered what
 * the next page offered (ADR-294 §4, ADR-292 §6): the arrangement the file names, as the approval, and the last seed
 * folder, as the seed folder.
 *
 * <p>The child is {@link EndingJar}'s, which ends at once as an invocation that ended does, so that the window reads
 * {@code next-invocation.properties}, which the test wrote as the jar writes it.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-294", url = Adr.THE_CONTROL_PANEL_LEARNS_WHAT_THE_NEXT_INVOCATION_WANTS_FROM_A_FILE, type = "adr")
@Link(name = "ADR-306", url = Adr.THE_CONTROL_PANELS_OWN_HOLDER_LINE_IS_HELD_BY_NOBODY, type = "adr")
class TheValuesTheNextInvocationWantsTest {

    /** The report the boilerplate floor is read off, as the README's table of values names it. */
    private static final String ITS_REPORT = "document-frequency";

    /** The JSON the working directory holds for that report: the window draws the report from it. */
    private static final String ITS_JSON = "{\"format\":1,\"analysisReport\":\"document-frequency\","
            + "\"title\":\"Document frequency\",\"writtenAt\":\"2026-05-01T10:00:00Z\",\"headline\":[],\"sections\":[]}";

    /** The short name of an arrangement, as {@code next-invocation.properties} gives it: twelve characters, 0 to 9 or a to f. */
    private static final String AN_ARRANGEMENT = "0123456789ab";

    @Test
    @Story("The control panel shows what the next invocation wants")
    @DisplayName("A report of a wanted value that the working directory holds is a link, and opens that report inside the window")
    void aReportTheWorkingDirectoryHoldsIsALinkBesideAWantedValue(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        Files.writeString(workingDirectory.resolve(NextInvocation.FILE_NAME),
                "invocation=run\nwants=boilerplateDocumentFrequencyFloor\nanswer=false\n", StandardCharsets.UTF_8);
        Files.writeString(workingDirectory.resolve(Reports.jsonOf(ITS_REPORT)), ITS_JSON, StandardCharsets.UTF_8);
        ControlPanelState state = WindowForTests.aState(folder, EndingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                workingDirectory.toString(), "", "", "", "", "", ""));

        List<String> links;
        Optional<WindowForTests.ReportTab> opened;
        List<URI> handed;
        try (WindowForTests window = WindowForTests.shown(state)) {
            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            assumeThat(refusal).as("the child that ends at once is started").isEmpty();
            boolean drawn = window.awaiting(
                    root -> HeadlessToolkit.nodeWithId(root, "wanted-row-boilerplateDocumentFrequencyFloor").isPresent(),
                    Boolean::booleanValue);
            assumeThat(drawn).as("the wanted value's row is drawn once the invocation has ended").isTrue();
            links = window.read(root -> WindowForTests.every(root, "wanted", Hyperlink.class).stream()
                    .map(Hyperlink::getText).toList());
            window.act(root -> WindowForTests.pressReportLink(root, "wanted", ITS_REPORT));
            opened = window.read(root -> WindowForTests.reportTab(root, ITS_REPORT));
            handed = List.copyOf(window.dialogs.handed);
        }

        claim("beside the wanted boilerplate floor, the report it is read off, which the working directory holds, is a"
                        + " link",
                () -> assertThat(links).containsExactly(ITS_REPORT));
        claim("and pressing it opens that report of the working directory in its own tab of the window, selected",
                () -> assertThat(opened).hasValueSatisfying(tab -> assertThat(tab.selected()).isTrue()));
        claim("and hands nothing to the system to open, so no browser is started",
                () -> assertThat(handed).isEmpty());
    }

    /**
     * Where the next invocation wants the approval and names the arrangement it arrived at, that name is put into
     * {@code arrangementApproved} (ADR-294 §4, which ADR-342 keeps), as the next page put it: the profile stating no
     * approval, the wanted value's control is offered the name, and the wanted values' Save writes it with the filled-in
     * note (ADR-343 §2).
     */
    @Test
    @Story("The control panel shows what the next invocation wants")
    @DisplayName("The arrangement the next invocation names is offered as the approval it wants, and Save writes it with the filled-in note")
    @Link(name = "ADR-343", url = Adr.THE_NOTE_SAVED_WITH_A_PROFILE_VALUE_IS_FILLED_IN, type = "adr")
    void theArrangementTheNextInvocationNamesIsOfferedAsTheApprovalItWants(@TempDir Path folder) throws Exception {
        Path workingDirectory = aWorkingDirectoryStatingAModel(folder);
        Files.writeString(workingDirectory.resolve(NextInvocation.FILE_NAME), "invocation=run\nwants=arrangementApproved\n"
                + "answer=false\narrangement=" + AN_ARRANGEMENT + "\n", StandardCharsets.UTF_8);
        ControlPanelState state = WindowForTests.aState(folder, EndingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                workingDirectory.toString(), "", "", "", "", "", ""));

        String offered;
        try (WindowForTests window = WindowForTests.shown(state)) {
            endedAndWanting(window, "arrangementApproved");
            offered = window.read(root -> WindowForTests.node(root, "wanted-arrangementApproved", ComboBox.class)
                    .getEditor().getText());
            window.act(root -> WindowForTests.node(root, "wanted-save", Button.class).fire());
        }

        claim("the profile stating no approval, the wanted approval is offered the arrangement the next invocation names",
                () -> assertThat(offered).isEqualTo(AN_ARRANGEMENT));
        claim("and the wanted values' Save writes that name as the approval, with the filled-in note",
                () -> assertThat(statedIn(workingDirectory)).containsEntry(ProfileKey.ARRANGEMENT_APPROVED,
                        new Stated(AN_ARRANGEMENT, ProfileEdit.FILLED_IN_NOTE)));
    }

    /**
     * Where the next invocation wants the seed folder and the profile states none, the wanted value's control is offered
     * the last seed folder the launch values hold (ADR-292 §6), as the Seed folder section's is and as the next page's
     * was.
     */
    @Test
    @Story("The control panel shows what the next invocation wants")
    @DisplayName("The last seed folder is offered as the seed folder the next invocation wants, where the profile states none")
    @Link(name = "ADR-292", url = Adr.THE_CONTROL_PANEL_REMEMBERS_ITS_LAUNCH_VALUES, type = "adr")
    void theLastSeedFolderIsOfferedAsTheSeedFolderItWants(@TempDir Path folder) throws Exception {
        Path workingDirectory = aWorkingDirectoryStatingAModel(folder);
        Files.writeString(workingDirectory.resolve(NextInvocation.FILE_NAME),
                "invocation=run\nwants=seedFolder\nanswer=false\n", StandardCharsets.UTF_8);
        Path lastSeedFolder = Files.createDirectories(folder.resolve("seeds"));
        ControlPanelState state = WindowForTests.aState(folder, EndingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                workingDirectory.toString(), "", "", "", "", "", lastSeedFolder.toString()));

        String offered;
        try (WindowForTests window = WindowForTests.shown(state)) {
            endedAndWanting(window, "seedFolder");
            offered = window.read(root -> WindowForTests.node(root, "wanted-seedFolder", TextField.class).getText());
        }

        claim("the profile stating no seed folder, the wanted seed folder is offered the last one the launch values hold",
                () -> assertThat(offered).isEqualTo(lastSeedFolder.toString()));
    }

    /** Starts the child that ends at once and waits until the wanted value {@code key}'s row is drawn. */
    private static void endedAndWanting(WindowForTests window, String key) throws Exception {
        Optional<String> refusal = window.state.start(ControlPanelState.Start.RUN, new ArrayList<>());
        assumeThat(refusal).as("the child that ends at once is started").isEmpty();
        boolean drawn = window.awaiting(root -> HeadlessToolkit.nodeWithId(root, "wanted-row-" + key).isPresent(),
                Boolean::booleanValue);
        assumeThat(drawn).as("the wanted value's row is drawn once the invocation has ended").isTrue();
    }

    /** A working directory under {@code folder} whose profile states the generation model and nothing else. */
    private static Path aWorkingDirectoryStatingAModel(Path folder) throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        Files.writeString(workingDirectory.resolve(ProfileEdit.FILE_NAME), ProfileEdit.rewrite(Optional.empty(),
                ProfileKey.GENERATION_MODEL, "a-writing-model:8b", "measured on the seed set"), StandardCharsets.UTF_8);
        return workingDirectory;
    }

    private static Map<ProfileKey, Stated> statedIn(Path workingDirectory) throws IOException {
        return ProfileEdit.read(Files.readString(workingDirectory.resolve(ProfileEdit.FILE_NAME), StandardCharsets.UTF_8));
    }
}
