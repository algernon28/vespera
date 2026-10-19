package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.buttonNames;
import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.controlNames;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The next act, {@code /next} (ADR-302 §8): after the control panel's own child ended with 0, each value
 * {@code next-invocation.properties} wants is asked for with its provenance, its cost and the report that informs it,
 * and saved into the profile; answers wanted lead to the questions and to {@code label --auto}; and the arrangement to
 * approve is offered by its name.
 *
 * <p>Each test has the child end with 0 beside a {@code next-invocation.properties} of its own, as the jar leaves it.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("561")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
class NextScreenTest {

    /** The two values the first stop of a run wants, in the order the file names them. */
    private static final String SEED_AND_FLOOR = """
            invocation=run
            wants=seedFolder boilerplateDocumentFrequencyFloor
            answer=false
            """;

    /** Answers to the questions wanted, and no value. */
    private static final String ANSWERS = """
            invocation=label
            wants=
            answer=true
            """;

    /** The arrangement this invocation arrived at, to approve: twelve hexadecimal characters. */
    private static final String ARRANGEMENT = "0123456789ab";

    /** The approval wanted, naming the arrangement. */
    private static final String APPROVAL = """
            invocation=run
            wants=arrangementApproved
            answer=false
            arrangement=%s
            """.formatted(ARRANGEMENT);

    @TempDir
    Path folder;

    @Test
    @Story("The operator writes the values the next invocation wants")
    @DisplayName("Each value the next run wants is asked for in order, with its provenance, its cost and the report it is read off")
    void eachWantedValueIsAskedWithItsProvenanceItsCostAndItsReport() throws IOException, InterruptedException {
        Path workingDirectory = workingDirectoryWanting(SEED_AND_FLOOR);
        try (ScreenServer screens = ScreenServer.start(folder, values(workingDirectory))) {
            Page page = screens.afterARunHasEnded().go("/next").page();
            Element next = form(page, "next");
            Element floorsReport = byId(page, "report-boilerplateDocumentFrequencyFloor");

            claim("the next act is a run",
                    () -> assertThat(byId(page, "next-invocation").attr("data-invocation")).isEqualTo("run"));
            claim("the form asks for each value wanted and its provenance, in the order the file names them",
                    () -> assertThat(controlNames(next)).containsExactly("seedFolder", "seedFolder.provenance",
                            "boilerplateDocumentFrequencyFloor", "boilerplateDocumentFrequencyFloor.provenance"));
            claim("its buttons are Save and Save, then Run",
                    () -> assertThat(buttonNames(next)).containsExactly("save", "save-then-run"));
            claim("each value's cost is said beside it",
                    () -> assertThat(List.of(byId(page, "cost-seedFolder").text().isBlank(),
                                    byId(page, "cost-boilerplateDocumentFrequencyFloor").text().isBlank()))
                            .containsExactly(false, false));
            claim("the floor is linked to the report it is read off, opening in a tab of its own, and the seed folder"
                            + " to none",
                    () -> assertThat(List.of(floorsReport.attr("href"), floorsReport.attr("target"),
                                    page.document().select("#report-seedFolder").size()))
                            .containsExactly("/report/document-frequency.html", "_blank", 0));
        }
    }

    @Test
    @Story("The operator writes the values the next invocation wants")
    @DisplayName("Saving the values the next run wants writes each into the profile with its provenance")
    void theWantedValuesAreSavedIntoTheProfile() throws IOException, InterruptedException {
        Path workingDirectory = workingDirectoryWanting(SEED_AND_FLOOR);
        Path seedFolder = Files.createDirectories(folder.resolve("seeds"));
        try (ScreenServer screens = ScreenServer.start(folder, values(workingDirectory))) {
            Page landed = screens.afterARunHasEnded().go("/next").press("next", "save", Map.of(
                    "seedFolder", seedFolder.toString(),
                    "seedFolder.provenance", "the operator's exemplars",
                    "boilerplateDocumentFrequencyFloor", "0.35",
                    "boilerplateDocumentFrequencyFloor.provenance", "read off the document frequency page")).page();
            Map<ProfileKey, ProfileEdit.Stated> profile = ProfileEdit.read(
                    Files.readString(workingDirectory.resolve(ProfileEdit.FILE_NAME), StandardCharsets.UTF_8));

            claim("saving lands back on the next act's page",
                    () -> assertThat(landed.address().getPath()).isEqualTo("/next"));
            claim("and the profile holds both values, each with the provenance given",
                    () -> assertThat(List.of(profile.get(ProfileKey.SEED_FOLDER),
                                    profile.get(ProfileKey.BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR)))
                            .containsExactly(
                                    new ProfileEdit.Stated(seedFolder.toString(), "the operator's exemplars"),
                                    new ProfileEdit.Stated("0.35", "read off the document frequency page")));
        }
    }

    @Test
    @Story("The operator answers the relevance questions")
    @DisplayName("Answers wanted lead to the questions and to the local model's answering, and ask for no value")
    void answersWantedLeadToTheQuestionsAndToLabelAuto() throws IOException, InterruptedException {
        Path workingDirectory = workingDirectoryWanting(ANSWERS);
        try (ScreenServer screens = ScreenServer.start(folder, values(workingDirectory))) {
            Page page = screens.afterARunHasEnded().go("/next").page();

            claim("the next act is a label",
                    () -> assertThat(byId(page, "next-invocation").attr("data-invocation")).isEqualTo("label"));
            claim("the questions are linked",
                    () -> assertThat(byId(page, "labels-link").attr("href")).isEqualTo("/labels"));
            claim("the local model's answering is a form of its own with one button",
                    () -> assertThat(List.of(form(page, "label-auto").attr("action"),
                                    buttonNames(form(page, "label-auto"))))
                            .containsExactly("/label-auto", List.of("label-auto")));
            claim("and no value is asked for",
                    () -> assertThat(page.document().select("form[name=next]")).isEmpty());
        }
    }

    @Test
    @Story("The operator approves the arrangement")
    @DisplayName("The arrangement to approve is offered by its name, beside its report and the path of its page on disk")
    void theArrangementToApproveIsOfferedByItsName() throws IOException, InterruptedException {
        Path workingDirectory = workingDirectoryWanting(APPROVAL);
        try (ScreenServer screens = ScreenServer.start(folder, values(workingDirectory))) {
            Page page = screens.afterARunHasEnded().go("/next").page();
            Element next = form(page, "next");

            claim("the approval is asked for, filled with the name of the arrangement to approve, its provenance empty",
                    () -> assertThat(List.of(next.select("[name=arrangementApproved]").val(),
                                    next.select("[name='arrangementApproved.provenance']").val()))
                            .containsExactly(ARRANGEMENT, ""));
            claim("beside it, the arrangement's report, and the absolute path of its page, whose links open from disk",
                    () -> assertThat(List.of(byId(page, "report-arrangementApproved").attr("href"),
                                    byId(page, "arrangement-file").text()))
                            .containsExactly("/report/arrangement.html",
                                    workingDirectory.resolve("arrangement.html").toAbsolutePath().toString()));
        }
    }

    private Path workingDirectoryWanting(String nextInvocation) throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Files.writeString(workingDirectory.resolve(NextInvocation.FILE_NAME), nextInvocation, StandardCharsets.UTF_8);
        ScreenServer.childWill(workingDirectory, "exit 0 the closing line");
        return workingDirectory;
    }

    private LaunchValues values(Path workingDirectory) throws IOException {
        return ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), workingDirectory);
    }
}
