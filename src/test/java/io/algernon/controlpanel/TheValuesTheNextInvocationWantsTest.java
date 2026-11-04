package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.Hyperlink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Each value the next invocation wants is edited in place in {@code wanted}, with its reports (ADR-341 §1, §6, keeping
 * ADR-294 and ADR-306 §3): a report of the key that the working directory holds is a link there, opened through the
 * window's dialogs, as on the key's row of the Profile tab.
 *
 * <p>The child is {@link EndingJar}'s, which ends at once as an invocation that ended does, so that the window reads
 * {@code next-invocation.properties}, which the test wrote as the jar writes it.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-341", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-294", url = Adr.THE_CONTROL_PANEL_LEARNS_WHAT_THE_NEXT_INVOCATION_WANTS_FROM_A_FILE, type = "adr")
@Link(name = "ADR-306", url = Adr.THE_CONTROL_PANELS_OWN_HOLDER_LINE_IS_HELD_BY_NOBODY, type = "adr")
class TheValuesTheNextInvocationWantsTest {

    /** The report the boilerplate floor is read off, as the README's table of values names it. */
    private static final String ITS_REPORT = "document-frequency.html";

    @Test
    @Story("The control panel shows what the next invocation wants")
    @DisplayName("A report of a wanted value that the working directory holds is a link, and opens that report")
    void aReportTheWorkingDirectoryHoldsIsALinkBesideAWantedValue(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        Files.writeString(workingDirectory.resolve(NextInvocation.FILE_NAME),
                "invocation=run\nwants=boilerplateDocumentFrequencyFloor\nanswer=false\n", StandardCharsets.UTF_8);
        Files.writeString(workingDirectory.resolve(ITS_REPORT), "<html></html>", StandardCharsets.UTF_8);
        ControlPanelState state = WindowForTests.aState(folder, EndingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                workingDirectory.toString(), "", "", "", "", "", ""));

        List<String> links;
        List<Path> opened;
        try (WindowForTests window = WindowForTests.shown(state)) {
            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            assumeThat(refusal).as("the child that ends at once is started").isEmpty();
            boolean drawn = window.awaiting(
                    root -> HeadlessToolkit.nodeWithId(root, "wanted-row-boilerplateDocumentFrequencyFloor").isPresent(),
                    Boolean::booleanValue);
            assumeThat(drawn).as("the wanted value's row is drawn once the invocation has ended").isTrue();
            links = window.read(root -> WindowForTests.every(root, "wanted", Hyperlink.class).stream()
                    .map(Hyperlink::getText).toList());
            window.act(root -> WindowForTests.every(root, "wanted", Hyperlink.class).stream()
                    .filter(link -> ITS_REPORT.equals(link.getText())).findFirst().ifPresent(Hyperlink::fire));
            opened = List.copyOf(window.dialogs.opened);
        }

        claim("beside the wanted boilerplate floor, the report it is read off, which the working directory holds, is a"
                        + " link",
                () -> assertThat(links).containsExactly(ITS_REPORT));
        claim("and pressing it opens that report of the working directory",
                () -> assertThat(opened).containsExactly(workingDirectory.resolve(ITS_REPORT)));
    }
}
