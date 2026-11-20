package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Explanations are captions on mouse over, never text in the window (ADR-430 §8): each button of the action row and of
 * the Labels section stands on a wrapper that carries its caption and is never disabled with it, since a disabled control
 * shows none; each row of the header's check says in its caption what it is and where its container was found (§10);
 * and every text that explained a control is gone from the window and is that control's caption, its words kept.
 *
 * <p>The window is given a {@link RecordingDockerControl} whose check the test sets. A caption is read as JavaFX keeps
 * it, the tooltip installed on the node.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("889")
@Link(name = "ADR-430", url = Adr.THE_CONTROL_PANEL_CHECKS_DOCKER_AND_STARTS_NOTHING_UNTIL_START_DOCKER, type = "adr")
class ExplanationsAreCaptionsOnMouseOverTest {

    private static final DockerCheck.Mark OK = DockerCheck.Mark.OK;

    private static final DockerCheck.Mark KO = DockerCheck.Mark.KO;

    private static final DockerCheck.Mark NOT_ASKED = DockerCheck.Mark.NOT_ASKED;

    /** The buttons that stand on wrappers, by their ids. */
    private static final List<String> WRAPPED = List.of("run", "stop", "start-docker", "shut-down-docker", "label",
            "label-auto");

    private static final String STOPS_CAPTION = "Stops the running invocation. Asks first.";

    private static final String START_DOCKERS_CAPTION = "Starts Docker and the containers on this machine and waits for"
            + " them, then downloads a model Ollama lacks where vespera.models.pull is on. Off while they all show OK,"
            + " while Docker starts or shuts down or a model downloads, while an invocation runs, and where Docling"
            + " and Ollama are both on another machine.";

    private static final String SHUT_DOWNS_CAPTION = "Stops the containers, then Docker and every other container it"
            + " runs. Asks first.";

    /** Save my labels' words and Auto Label's, the label model's field blank, as ADR-413 §3 has them. */
    private static final String SAVE_MY_LABELS_WORDS = "Reads relevance-labels.yaml in the working directory, where"
            + " each document's relevant is set to true or false, and saves those labels for the next Run. It reads no"
            + " document and sets no label itself.";

    private static final String AUTO_LABEL_WORDS = "The local model reads the documents, sets their labels and the"
            + " cut-off, and never replaces a label you set.";

    private static final String THE_TREES_WORDS = "Untick a folder holding nothing you want. An unticked folder is never"
            + " read, and a change makes the next walk another, so every run after it is new.";

    private static final String THE_FOLDERS_NOT_RELEVANT_WORDS = "Folders not relevant are still read: the documents"
            + " of relevance-labels.yaml below them are labelled not relevant, and no run changes.";

    /** The launch values whose one-line hint stood beneath their field, each now its box's caption. */
    private static final List<String> FIELDS = List.of("vespera.corpus-root", "vespera.working-dir",
            "vespera.deliverable-root", "vespera.docling.base-url", "spring.ai.ollama.base-url", "vespera.label.model");

    private static final String DOCLING_IMAGE = "vespera.docling.image";

    /** How far apart two sizes or places may be and still be the same: a pixel. */
    private static final double ONE_PIXEL = 1.0;

    @TempDir
    Path folder;

    @Test
    @Link(name = "ADR-433", url = Adr.DOCLING_AND_OLLAMA_ARE_CONTAINERS_WHERE_VESPERA_RUNS_THEM, type = "adr")
    @Issue("891")
    @Story("Explanations are captions on mouse over")
    @DisplayName("Run, Stop, Start Docker, Shut down Docker, Save my labels and Auto Label each stand alone on a wrapper that carries its caption, is not disabled with it, and is the control's own size with the control at its corner")
    void eachButtonOfTheActionRowStandsOnAWrapperThatCarriesItsCaption() throws Exception {
        record Wrapped(String wrapper, int holds, boolean captioned, boolean controlDisabled, boolean wrapperDisabled) {
        }
        record Sized(double wrapperWidth, double wrapperHeight, double controlWidth, double controlHeight,
                double controlX, double controlY) {
        }
        Map<String, Wrapped> read;
        Map<String, Sized> sized = new LinkedHashMap<>();
        Map<String, String> captions;
        try (WindowForTests window = shown(new RecordingDockerControl())) {
            read = window.read(root -> {
                Map<String, Wrapped> each = new LinkedHashMap<>();
                for (String id : WRAPPED) {
                    Node control = WindowForTests.node(root, id, Node.class);
                    Parent wrapper = control.getParent();
                    each.put(id, new Wrapped(wrapper.getId(), wrapper.getChildrenUnmodifiable().size(),
                            WindowForTests.caption(wrapper) != null && !WindowForTests.caption(wrapper).isBlank(),
                            control.isDisabled(), wrapper.isDisabled()));
                }
                return each;
            });
            // the Labels section opened, so that its wrappers are laid out where the operator sees them
            window.act(root -> WindowForTests.section(root, "labels").setExpanded(true));
            window.act(root -> {
                for (String id : WRAPPED) {
                    Node control = WindowForTests.node(root, id, Node.class);
                    Node wrapper = WindowForTests.node(root, id + "-captioned", Node.class);
                    sized.put(id, new Sized(wrapper.getLayoutBounds().getWidth(), wrapper.getLayoutBounds().getHeight(),
                            control.getLayoutBounds().getWidth(), control.getLayoutBounds().getHeight(),
                            control.getLayoutX() + control.getLayoutBounds().getMinX(),
                            control.getLayoutY() + control.getLayoutBounds().getMinY()));
                }
            });
            captions = window.read(root -> Map.of(
                    "stop", WindowForTests.caption(root, "stop-captioned"),
                    "start-docker", WindowForTests.caption(root, "start-docker-captioned"),
                    "shut-down-docker", WindowForTests.caption(root, "shut-down-docker-captioned")));
        }

        claim("each stands on a wrapper named for it, <id>-captioned, which holds it alone and carries a caption",
                () -> assertThat(read).allSatisfy((id, wrapped) -> assertThat(List.of(wrapped.wrapper(), wrapped.holds(),
                        wrapped.captioned())).as("%s's wrapper", id).containsExactly(id + "-captioned", 1, true)));
        claim("Stop, disabled with no invocation running, stands on a wrapper that is not, so its caption shows",
                () -> assertThat(List.of(read.get("stop").controlDisabled(), read.get("stop").wrapperDisabled()))
                        .containsExactly(true, false));
        claim("and no wrapper is disabled", () -> assertThat(read.values()).noneMatch(Wrapped::wrapperDisabled));
        claim("each wrapper is as wide and as high as its control, within a pixel, and holds it at its top left corner,"
                        + " 0,0, so that a wrapper moves no control from where it stood without one, in the action row"
                        + " and in the Labels section alike",
                () -> assertThat(sized).hasSize(WRAPPED.size()).allSatisfy((id, at) -> {
                    assertThat(at.wrapperWidth()).as("%s's wrapper's width: %s", id, at)
                            .isCloseTo(at.controlWidth(), offset(ONE_PIXEL));
                    assertThat(at.wrapperHeight()).as("%s's wrapper's height: %s", id, at)
                            .isCloseTo(at.controlHeight(), offset(ONE_PIXEL));
                    assertThat(at.controlX()).as("%s's x in its wrapper: %s", id, at).isCloseTo(0, offset(ONE_PIXEL));
                    assertThat(at.controlY()).as("%s's y in its wrapper: %s", id, at).isCloseTo(0, offset(ONE_PIXEL));
                }));
        claim("Stop's, Start Docker's and Shut down Docker's captions say what each does and, for Start Docker, when it"
                        + " is off",
                () -> assertThat(captions).containsExactlyInAnyOrderEntriesOf(Map.of("stop", STOPS_CAPTION,
                        "start-docker", START_DOCKERS_CAPTION, "shut-down-docker", SHUT_DOWNS_CAPTION)));
    }

    @Test
    @Link(name = "ADR-433", url = Adr.DOCLING_AND_OLLAMA_ARE_CONTAINERS_WHERE_VESPERA_RUNS_THEM, type = "adr")
    @Issue("891")
    @Story("Explanations are captions on mouse over")
    @DisplayName("Each row of the header's check says in its caption what it is, the container's address and where it was found, and the model's name")
    void eachHeaderRowsCaptionSaysWhatItIsAndWhereItWasFound() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl().checkAnswering(new DockerCheck(OK,
                new DockerCheck.Service(OK, DockerCheck.Where.THIS_MACHINE, "http://localhost:5001"),
                new DockerCheck.Service(OK, DockerCheck.Where.ANOTHER_MACHINE, "http://gpu-box.lan:11434"),
                OK, KO));
        List<String> here;
        List<String> elsewhere;
        try (WindowForTests window = shown(docker)) {
            here = window.awaiting(ExplanationsAreCaptionsOnMouseOverTest::rowCaptions,
                    read -> read.get(2) != null && read.get(2).contains("gpu-box.lan"));
            docker.checkAnswering(new DockerCheck(NOT_ASKED,
                    new DockerCheck.Service(KO, DockerCheck.Where.NOT_RESOLVED, "http://docling.invalid:5001"),
                    new DockerCheck.Service(KO, DockerCheck.Where.ANOTHER_MACHINE, "http://gpu-box.lan:11434"),
                    NOT_ASKED, NOT_ASKED));
            elsewhere = window.awaiting(ExplanationsAreCaptionsOnMouseOverTest::rowCaptions,
                    read -> read.get(1) != null && read.get(1).contains("docling.invalid"));
        }

        claim("with Docling here and Ollama on another machine, the daemon's caption says what it is, Docling's that it"
                        + " is on this machine at its address, Ollama's that it is on another machine and not started,"
                        + " each model's its name, and the missing one's that clicking KO downloads it",
                () -> assertThat(here).containsExactly(
                        "Docker daemon: the Docker engine the containers on this machine run in",
                        "Docling: this machine, http://localhost:5001",
                        "Ollama: gpu-box.lan, another machine; Vespera does not start it",
                        "Embedding model: " + TheWindowChecksAndStartsNothingAsItOpensTest.EMBEDDING_MODEL,
                        "Generative model: " + TheWindowChecksAndStartsNothingAsItOpensTest.GENERATIVE_MODEL
                                + "; click KO to download it into Ollama"));
        claim("with neither here, the daemon's says it is not asked, Docling's that its host cannot be resolved, and"
                        + " each model's that it is asked once Ollama shows OK",
                () -> assertThat(elsewhere).containsExactly(
                        "Docker daemon: not asked, Docling and Ollama are both on another machine",
                        "Docling: docling.invalid cannot be resolved; Vespera does not ask it or start it",
                        "Ollama: gpu-box.lan, another machine; Vespera does not start it",
                        "Embedding model: " + TheWindowChecksAndStartsNothingAsItOpensTest.EMBEDDING_MODEL
                                + "; asked once Ollama shows OK",
                        "Generative model: " + TheWindowChecksAndStartsNothingAsItOpensTest.GENERATIVE_MODEL
                                + "; asked once Ollama shows OK"));
    }

    @Test
    @Story("Explanations are captions on mouse over")
    @DisplayName("Every text that explained a control is gone from the window and is that control's caption, its words kept")
    void theTextsThatExplainedAControlAreNowItsCaption() throws Exception {
        List<String> formerTexts;
        Map<String, String> captions;
        List<String> texts;
        try (WindowForTests window = shown(new RecordingDockerControl())) {
            formerTexts = window.read(root -> {
                List<String> ids = new java.util.ArrayList<>(List.of("label-words", "label-auto-words",
                        "hint-" + DOCLING_IMAGE));
                FIELDS.forEach(property -> ids.add("hint-" + property));
                return ids.stream().filter(id -> HeadlessToolkit.nodeWithId(root, id).isPresent()
                        || HeadlessToolkit.nodeWithId(inclusions(root), id).isPresent()).toList();
            });
            captions = window.read(root -> {
                WindowForTests.node(root, "vespera.label.model", TextInputControl.class).setText("");
                Map<String, String> each = new LinkedHashMap<>();
                each.put("label-captioned", WindowForTests.caption(root, "label-captioned"));
                each.put("label-auto-captioned", WindowForTests.caption(root, "label-auto-captioned"));
                FIELDS.forEach(property -> each.put("field-" + property,
                        WindowForTests.caption(root, "field-" + property)));
                each.put("docling-images", WindowForTests.caption(root, "docling-images"));
                each.put("inclusions-tree", WindowForTests.caption(
                        HeadlessToolkit.nodeWithId(inclusions(root), "inclusions-tree").orElseThrow()));
                each.put("not-relevant-list", WindowForTests.caption(
                        HeadlessToolkit.nodeWithId(inclusions(root), "not-relevant-list").orElseThrow()));
                return each;
            });
            texts = window.read(root -> {
                List<String> shown = new java.util.ArrayList<>(textsUnder(root));
                shown.addAll(textsUnder(inclusions(root)));
                return shown;
            });
        }
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("label-captioned", SAVE_MY_LABELS_WORDS);
        expected.put("label-auto-captioned", AUTO_LABEL_WORDS);
        FIELDS.forEach(property -> expected.put("field-" + property, LaunchValues.hintOf(property)));
        expected.put("docling-images", LaunchValues.hintOf(DOCLING_IMAGE));
        expected.put("inclusions-tree", THE_TREES_WORDS);
        expected.put("not-relevant-list", THE_FOLDERS_NOT_RELEVANT_WORDS);

        claim("no node of the window is the label commands' captions or a field's hint any more",
                () -> assertThat(formerTexts).isEmpty());
        claim("each of those words, and the two of the Folders section's tree and folders not relevant, is the caption of"
                        + " the control it explained",
                () -> assertThat(captions).containsExactlyEntriesOf(expected));
        claim("and none of them is shown as text anywhere in the window",
                () -> assertThat(texts).doesNotContainAnyElementsOf(expected.values()));
    }

    @Test
    @Story("Explanations are captions on mouse over")
    @DisplayName("A running step's Stop carries what a stop there costs as its caption, and no text of its row says it")
    void aStepsStopCarriesWhatAStopThereCosts() throws Exception {
        String cost = "A stop here costs " + StopCosts.costOf("census") + ".";
        String caption;
        List<String> rowTexts;
        try (WindowForTests window = WindowForTests.readingEverySecond(folder)) {
            window.reportWritten(folder, "{\"ended\": false, \"steps\": [{\"name\": \"census\", \"state\": \"running\","
                    + " \"done\": null, \"total\": null}], \"charts\": []}");
            caption = window.awaiting(root -> {
                Button stopHere = WindowForTests.every(root, "step-census", Button.class).getFirst();
                return stopHere.isVisible() ? WindowForTests.caption(stopHere) : null;
            }, read -> read != null);
            rowTexts = window.read(root -> textsUnder(WindowForTests.node(root, "step-census", Node.class)));
        }

        claim("the census running, its Stop's caption says what a stop there costs",
                () -> assertThat(caption).isEqualTo(cost));
        claim("and no text of its row says it", () -> assertThat(rowTexts).doesNotContain(cost));
    }

    private WindowForTests shown(RecordingDockerControl docker) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime(), docker);
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("working")).toString(), "", "", "", "", "", ""));
        state.useSources(TheWindowChecksAndStartsNothingAsItOpensTest.withModels());
        return WindowForTests.shown(state);
    }

    /** Each row's caption, by the id of its name, in the order of the list. */
    private static List<String> rowCaptions(Parent root) {
        return TheWindowChecksAndStartsNothingAsItOpensTest.ROWS.stream()
                .map(id -> WindowForTests.caption(root, id)).toList();
    }

    /** The sidebar's Folders section's content, which holds the tree of the folders to read (ADR-432 §9). */
    private static Parent inclusions(Parent root) {
        return (Parent) WindowForTests.section(root, "folders").getContent();
    }

    /** Every text shown under {@code node}, a label's or a text control's, none blank. */
    private static List<String> textsUnder(Node node) {
        return HeadlessToolkit.everyNodeUnder(node).stream()
                .map(each -> each instanceof TextInputControl text ? text.getText()
                        : each instanceof Labeled labeled ? labeled.getText() : null)
                .filter(text -> text != null && !text.isBlank()).toList();
    }
}
