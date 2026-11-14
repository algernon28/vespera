package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The profile edited in a window of its own (ADR-389): Profile is the sidebar's first section, holding Open profile,
 * which opens the one Profile window, a stage apart from the cockpit's, or brings it to the front; its size and place
 * are kept by the cockpit and written nowhere, and at its first size every row's title is shown whole; closing it, or
 * the cockpit, or the cockpit loading the working directory's files again, with an edit unsaved asks Save, Discard or
 * Cancel, and naming another working directory asks Save or Discard alone, a Save or no answer writing into the old one
 * and a Save refused dropping the edit, said in the outcome, the new one loaded after each, while the same folder
 * spelled another way is no other working directory; the question, in the system's dialog, gives the answer pressed and
 * none where it is closed with no button pressed; the section and the window's title carry the badge; a line naming the
 * Profile window offers a button opening it; and the window follows the cockpit's theme.
 *
 * <p>Each test but the system's dialog's shows the cockpit as the control panel shows it, on the Headless platform
 * (ADR-342 §6), with dialogs the test answers, and finds the Profile window among the toolkit's windows by the form its
 * root holds; that one asks through {@code Dialogs.of} over a stage of its own. A window is closed as the system's close
 * button closes it, by a close request a handler may consume.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("799")
@Link(name = "ADR-389", url = Adr.THE_PROFILE_IS_EDITED_IN_A_WINDOW_OF_ITS_OWN, type = "adr")
class TheProfileWindowTest {

    /** The sidebar's sections, in their order: the profile's first, then the seven the cockpit had. */
    private static final List<String> THE_SECTIONS = List.of("profile", "preflight", "folders", "services",
            "seed-folder", "code-sets", "command-options", "reports");

    /** The tabs the cockpit opens with: the Report tab and Inclusions. */
    private static final List<String> THE_TABS = List.of("live-report", "inclusions");

    private static final String PROFILE = "Profile";

    private static final String PROFILE_NEEDING_A_VALUE = "Profile (needs a value)";

    /** The question asked where an edit is unsaved: its title, its whole sentence and its three answers in order. */
    private static final String THE_QUESTIONS_TITLE = "The profile";

    private static final String THE_QUESTION = "The profile has changes not saved in profile.yaml.";

    private static final String SAVE = "Save";

    private static final String DISCARD = "Discard";

    private static final String CANCEL = "Cancel";

    private static final AnsweringDialogs.Chose THE_CHOICE = new AnsweringDialogs.Chose(THE_QUESTIONS_TITLE,
            THE_QUESTION, List.of(SAVE, DISCARD, CANCEL));

    /** The row the tests edit, by the id the form gives its control, and the space's key the file states at first. */
    private static final String THE_FIELD = "profile-" + ProfileKey.CONFLUENCE_SPACE_KEY.key();

    private static final String SPACE = "KT";

    private static final String THE_TESTS_PROVENANCE = "set by the test";

    /** The provenance a value changed in the window is saved with (ADR-343 §2). */
    private static final String SET_IN_THE_CONTROL_PANEL = "set in the control panel";

    /** The space's keys the tests type, one for each edit. */
    private static final String FIRST_EDIT = "KT2";

    private static final String SECOND_EDIT = "KT3";

    private static final String THIRD_EDIT = "KT4";

    /** The key another program writes into the file while the form holds an edit. */
    private static final String WRITTEN_MEANWHILE = "ELSEWHERE";

    /** A Save's refusal where the file changed the value since the form was loaded (ADR-330 §5). */
    private static final String CHANGED_SINCE_LOADED = ProfileKey.CONFLUENCE_SPACE_KEY.key() + " is not saved:"
            + " profile.yaml has changed this value since the page was drawn; look at it and save again";

    /** The folder's title's row, which another program changes while the form holds an edit, by its control's id. */
    private static final String THE_TITLE_FIELD = "profile-" + ProfileKey.CONFLUENCE_FOLDER_TITLE.key();

    /** The folder's titles the other program writes, one before each load whose form the test reads. */
    private static final String TITLE_BEFORE_CANCEL = "Written before Cancel";

    private static final String TITLE_BEFORE_REFUSED_SAVE = "Written before the refused Save";

    private static final String TITLE_WITH_NO_EDIT = "Written with no edit";

    /** Seed folder's row in the control panel's window, by its control's id. */
    private static final String THE_SEED_ROWS_FIELD = "seed-" + ProfileKey.SEED_FOLDER.key();

    /** The question asked where the working directory changes with an edit unsaved: the same words, two answers. */
    private static final AnsweringDialogs.Chose SAVE_OR_DISCARD = new AnsweringDialogs.Chose(THE_QUESTIONS_TITLE,
            THE_QUESTION, List.of(SAVE, DISCARD));

    /** The space's keys the profiles of the second and the third working directory state. */
    private static final String OTHER_SPACE = "OT";

    private static final String THIRD_SPACE = "TH";

    /** The working directory's folder name, {@code working}, in capitals: the same folder where the file system ignores case. */
    private static final String WORKING_IN_CAPITALS = "WORKING";

    /** A width the test makes the Profile window, at which some title is cut, so that its reading is seen to find one. */
    private static final double NARROW = 500;

    /** Where the test moves the Profile window to, and how large it makes it: inside the Headless platform's screen. */
    private static final Placed MOVED_TO = new Placed(40, 30, 700, 500);

    /** The window's own line after a parent's title warning is cancelled, naming the Profile window (ADR-389 §6). */
    private static final String LEFT_AS_IT_WAS = "the publish to Confluence sent nothing: confluenceFolderTitle is left"
            + " as profile.yaml states it; name the parent by confluenceFolderTitle or confluenceFolderId in the Profile"
            + " window, and publish again";

    /** Where a window stands and how large it is. */
    private record Placed(double x, double y, double width, double height) {
    }

    @Test
    @Story("The profile is edited in a window of its own")
    @DisplayName("Profile is the first of the sidebar's eight sections, the tabs are the Report and Inclusions alone, and Open profile opens one window apart from the control panel's, brought back rather than opened again")
    void theProfileIsTheSidebarsFirstSectionAndOpensOneWindowOfItsOwn(@TempDir Path folder) throws Exception {
        List<String> sections;
        String title;
        boolean holdsTheButton;
        List<String> tabs;
        boolean formInTheCockpit;
        int before;
        Stage opened;
        Object owner;
        Modality modality;
        boolean resizable;
        String windowTitle;
        boolean minimized;
        List<Stage> afterPressingAgain;
        boolean stillMinimized;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder))) {
            sections = window.read(root -> WindowForTests.node(root, "sidebar", Accordion.class).getPanes().stream()
                    .map(Node::getId).toList());
            title = window.read(TheProfileWindowTest::sectionTitle);
            holdsTheButton = window.read(root -> HeadlessToolkit.everyNodeUnder(WindowForTests.section(root, "profile"))
                    .stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .anyMatch(button -> "open-profile".equals(button.getId())
                            && "Open profile".equals(button.getText())));
            tabs = window.read(WindowForTests::tabIds);
            formInTheCockpit = window.read(root -> HeadlessToolkit.nodeWithId(root, "profile-form").isPresent());
            before = window.profileWindows().size();
            window.openProfile();
            opened = window.profileWindow();
            owner = HeadlessToolkit.onTheToolkit(opened::getOwner);
            modality = HeadlessToolkit.onTheToolkit(opened::getModality);
            resizable = HeadlessToolkit.onTheToolkit(opened::isResizable);
            windowTitle = HeadlessToolkit.onTheToolkit(opened::getTitle);
            minimized = HeadlessToolkit.onTheToolkit(() -> {
                opened.setIconified(true);
                return opened.isIconified();
            });
            window.openProfile();
            afterPressingAgain = window.profileWindows();
            stillMinimized = HeadlessToolkit.onTheToolkit(opened::isIconified);
        }

        claim("the sidebar holds eight sections, Profile first and then the seven it held before, in their order",
                () -> assertThat(sections).containsExactlyElementsOf(THE_SECTIONS));
        claim("the Profile section is titled Profile and holds the Open profile button",
                () -> {
                    assertThat(title).isEqualTo(PROFILE);
                    assertThat(holdsTheButton).isTrue();
                });
        claim("the tabs are the Report tab and Inclusions, in that order, and the control panel's window holds no part"
                        + " of the profile's form",
                () -> {
                    assertThat(tabs).containsExactlyElementsOf(THE_TABS);
                    assertThat(formInTheCockpit).isFalse();
                });
        claim("no profile window was open before Open profile was pressed",
                () -> assertThat(before).isZero());
        claim("the window it opens has no owner and no modality, so either window is moved and brought in front of the"
                        + " other apart, can be resized, and is titled Profile",
                () -> {
                    assertThat(owner).isNull();
                    assertThat(modality).isEqualTo(Modality.NONE);
                    assertThat(resizable).isTrue();
                    assertThat(windowTitle).isEqualTo(PROFILE);
                });
        claim("the test minimized it", () -> assertThat(minimized).isTrue());
        claim("and Open profile pressed again opens no other, and shows the one open again, no longer minimized",
                () -> {
                    assertThat(afterPressingAgain).containsExactly(opened);
                    assertThat(stillMinimized).isFalse();
                });
    }

    @Test
    @Story("The profile is edited in a window of its own")
    @DisplayName("The profile window opens again where and as large as it was left, and a control panel started again does not open it there")
    void itsPlaceAndSizeAreKeptUntilTheControlPanelExitsAndWrittenNowhere(@TempDir Path folder) throws Exception {
        ControlPanelState state = aStateOver(folder);
        Placed atFirst;
        Placed moved;
        boolean showingAfterClosing;
        Placed openedAgain;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.openProfile();
            Stage profile = window.profileWindow();
            atFirst = HeadlessToolkit.onTheToolkit(() -> placeOf(profile));
            moved = HeadlessToolkit.onTheToolkit(() -> {
                profile.setX(MOVED_TO.x());
                profile.setY(MOVED_TO.y());
                profile.setWidth(MOVED_TO.width());
                profile.setHeight(MOVED_TO.height());
                return placeOf(profile);
            });
            showingAfterClosing = WindowForTests.closeAsTheOperatorDoes(profile);
            window.openProfile();
            Stage again = window.profileWindow();
            openedAgain = HeadlessToolkit.onTheToolkit(() -> placeOf(again));
        }
        Placed inTheNextControlPanel;
        try (WindowForTests next = WindowForTests.shown(aStateOver(folder))) {
            next.openProfile();
            Stage profile = next.profileWindow();
            inTheNextControlPanel = HeadlessToolkit.onTheToolkit(() -> placeOf(profile));
        }

        claim("the test moved the window to x 40, y 30 and made it 700 by 500, which is not where and as large as it"
                        + " opened",
                () -> {
                    assertThat(moved).isEqualTo(MOVED_TO);
                    assertThat(atFirst).isNotEqualTo(MOVED_TO);
                });
        claim("closed with no edit, it closed", () -> assertThat(showingAfterClosing).isFalse());
        claim("opened again, it stands where it was left, as large as it was left",
                () -> assertThat(openedAgain).isEqualTo(MOVED_TO));
        claim("and a control panel started again, over the same launch values file, does not open it there and so"
                        + " large: neither was written anywhere",
                () -> assertThat(inTheNextControlPanel).isNotEqualTo(MOVED_TO));
    }

    @Test
    @Story("The profile is edited in a window of its own")
    @DisplayName("At the size the profile window first opens at, every row's title is shown whole, none cut with an ellipsis")
    void atItsFirstSizeEveryRowsTitleIsShownWhole(@TempDir Path folder) throws Exception {
        List<String> titled;
        List<String> cutAtFirst;
        List<String> cutNarrow;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder))) {
            window.openProfile();
            Stage profile = window.profileWindow();
            titled = window.readProfile(TheProfileWindowTest::keysWithATitle);
            cutAtFirst = window.readProfile(TheProfileWindowTest::titlesCut);
            HeadlessToolkit.onTheToolkit(() -> {
                profile.setWidth(NARROW);
                return null;
            });
            window.readProfile(TheProfileWindowTest::titlesCut);
            cutNarrow = window.readProfile(TheProfileWindowTest::titlesCut);
        }

        claim("every key of the profile has a row titled in the profile window, so that a reading of the titles cannot"
                        + " pass over none",
                () -> assertThat(titled).containsExactlyElementsOf(everyKey()));
        claim("made 500 pixels wide, some title shows the ellipsis of a cut line, so that the reading sees a title cut",
                () -> assertThat(cutNarrow).isNotEmpty());
        claim("at the size the window first opens at, no row's title shows the ellipsis: each is shown whole",
                () -> assertThat(cutAtFirst).isEmpty());
    }

    @Test
    @Story("An edit of the profile is not lost by closing a window")
    @DisplayName("Closing the profile window with an edit unsaved asks Save, Discard or Cancel: Cancel keeps it open, Save writes and closes, a Save refused keeps it open, Discard closes and writes nothing, and with no edit it closes asking nothing")
    void closingTheProfileWindowWithAnEditUnsavedAsksSaveDiscardOrCancel(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path profileFile = aProfileNamingTheSpace(working);
        byte[] asWritten = Files.readAllBytes(profileFile);

        boolean showingAfterCancel;
        String fieldAfterCancel;
        byte[] fileAfterCancel;
        boolean showingAfterSave;
        ProfileEdit.Stated statedAfterSave;
        String fieldOpenedAfterSave;
        boolean showingAfterDiscard;
        byte[] fileBeforeDiscard;
        byte[] fileAfterDiscard;
        String fieldOpenedAfterDiscard;
        boolean showingAfterRefusedSave;
        String outcomeOfRefusedSave;
        ProfileEdit.Stated statedAfterRefusedSave;
        boolean showingAfterSecondDiscard;
        int askedWithEdits;
        boolean showingWithNoEdit;
        int askedInAll;
        List<AnsweringDialogs.Chose> choices;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, working))) {
            window.openProfile();
            Stage profile = window.profileWindow();

            typed(window, FIRST_EDIT);
            window.dialogs.chosen = Optional.of(CANCEL);
            showingAfterCancel = WindowForTests.closeAsTheOperatorDoes(profile);
            fieldAfterCancel = window.readProfile(TheProfileWindowTest::theFieldsText);
            fileAfterCancel = Files.readAllBytes(profileFile);

            window.dialogs.chosen = Optional.of(SAVE);
            showingAfterSave = WindowForTests.closeAsTheOperatorDoes(profile);
            statedAfterSave = spaceIn(profileFile);
            window.openProfile();
            fieldOpenedAfterSave = window.readProfile(TheProfileWindowTest::theFieldsText);

            typed(window, SECOND_EDIT);
            fileBeforeDiscard = Files.readAllBytes(profileFile);
            window.dialogs.chosen = Optional.of(DISCARD);
            showingAfterDiscard = WindowForTests.closeAsTheOperatorDoes(window.profileWindow());
            fileAfterDiscard = Files.readAllBytes(profileFile);
            window.openProfile();
            fieldOpenedAfterDiscard = window.readProfile(TheProfileWindowTest::theFieldsText);

            typed(window, THIRD_EDIT);
            Files.writeString(profileFile, ProfileEdit.rewrite(Optional.of(Files.readString(profileFile,
                    StandardCharsets.UTF_8)), ProfileKey.CONFLUENCE_SPACE_KEY, WRITTEN_MEANWHILE, THE_TESTS_PROVENANCE),
                    StandardCharsets.UTF_8);
            window.dialogs.chosen = Optional.of(SAVE);
            Stage refusing = window.profileWindow();
            showingAfterRefusedSave = WindowForTests.closeAsTheOperatorDoes(refusing);
            outcomeOfRefusedSave = window.readProfile(root -> WindowForTests.node(root, "profile-outcome",
                    TextInputControl.class).getText());
            statedAfterRefusedSave = spaceIn(profileFile);
            window.dialogs.chosen = Optional.of(DISCARD);
            showingAfterSecondDiscard = WindowForTests.closeAsTheOperatorDoes(refusing);
            askedWithEdits = window.dialogs.choices.size();

            window.openProfile();
            showingWithNoEdit = WindowForTests.closeAsTheOperatorDoes(window.profileWindow());
            askedInAll = window.dialogs.choices.size();
            choices = List.copyOf(window.dialogs.choices);
        }

        claim("Cancel keeps the window open, the edit in its field, and the file as the test wrote it",
                () -> {
                    assertThat(showingAfterCancel).isTrue();
                    assertThat(fieldAfterCancel).isEqualTo(FIRST_EDIT);
                    assertThat(fileAfterCancel).isEqualTo(asWritten);
                });
        claim("Save writes the edit, as set in the control panel, closes the window, and the window opened again shows"
                        + " the value saved",
                () -> {
                    assertThat(showingAfterSave).isFalse();
                    assertThat(statedAfterSave).isEqualTo(new ProfileEdit.Stated(FIRST_EDIT, SET_IN_THE_CONTROL_PANEL));
                    assertThat(fieldOpenedAfterSave).isEqualTo(FIRST_EDIT);
                });
        claim("Discard closes the window and writes nothing, and the window opened again shows the file's value, not"
                        + " the edit discarded",
                () -> {
                    assertThat(showingAfterDiscard).isFalse();
                    assertThat(fileAfterDiscard).isEqualTo(fileBeforeDiscard);
                    assertThat(fieldOpenedAfterDiscard).isEqualTo(FIRST_EDIT);
                });
        claim("a Save refused, the file having changed the value since the form was loaded, keeps the window open with"
                        + " the refusal, the file as it was changed; and Discard then closes it",
                () -> {
                    assertThat(showingAfterRefusedSave).isTrue();
                    assertThat(outcomeOfRefusedSave).isEqualTo(CHANGED_SINCE_LOADED);
                    assertThat(statedAfterRefusedSave).isEqualTo(
                            new ProfileEdit.Stated(WRITTEN_MEANWHILE, THE_TESTS_PROVENANCE));
                    assertThat(showingAfterSecondDiscard).isFalse();
                });
        claim("each of the five closings with an edit unsaved asked once, titled The profile, saying the profile has"
                        + " changes not saved in profile.yaml, with Save, Discard and Cancel in that order",
                () -> {
                    assertThat(askedWithEdits).isEqualTo(5);
                    assertThat(choices).allSatisfy(choice -> assertThat(choice).isEqualTo(THE_CHOICE));
                });
        claim("and with no edit the window closed asking nothing",
                () -> {
                    assertThat(showingWithNoEdit).isFalse();
                    assertThat(askedInAll).isEqualTo(askedWithEdits);
                });
    }

    @Test
    @Story("An edit of the profile is not lost by closing a window")
    @DisplayName("Closing the control panel's window closes the profile window, asking Save, Discard or Cancel first where an edit is unsaved, and nothing where none is")
    void closingTheCockpitClosesTheProfileWindowAskingFirstWhereAnEditIsUnsaved(@TempDir Path folder)
            throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path profileFile = aProfileNamingTheSpace(working);
        byte[] asWritten = Files.readAllBytes(profileFile);

        boolean[] afterCancel;
        byte[] fileAfterCancel;
        boolean[] afterSave;
        ProfileEdit.Stated statedAfterSave;
        List<AnsweringDialogs.Chose> askedOnSave;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, working))) {
            window.openProfile();
            Stage profile = window.profileWindow();
            typed(window, FIRST_EDIT);
            window.dialogs.chosen = Optional.of(CANCEL);
            afterCancel = showing(WindowForTests.closeAsTheOperatorDoes(window.stage), profile);
            fileAfterCancel = Files.readAllBytes(profileFile);
            window.dialogs.chosen = Optional.of(SAVE);
            afterSave = closedOnceTheSidecarsAreStopped(window, profile);
            statedAfterSave = spaceIn(profileFile);
            askedOnSave = List.copyOf(window.dialogs.choices);
        }

        Path discarding = Files.createDirectories(folder.resolve("discarding"));
        Path discardingWorking = Files.createDirectories(discarding.resolve("working"));
        Path discardingFile = aProfileNamingTheSpace(discardingWorking);
        byte[] discardingAsWritten = Files.readAllBytes(discardingFile);
        boolean[] afterDiscard;
        int askedOnDiscard;
        try (WindowForTests window = WindowForTests.shown(aStateOver(discarding, discardingWorking))) {
            window.openProfile();
            Stage profile = window.profileWindow();
            typed(window, FIRST_EDIT);
            window.dialogs.chosen = Optional.of(DISCARD);
            afterDiscard = closedOnceTheSidecarsAreStopped(window, profile);
            askedOnDiscard = window.dialogs.choices.size();
        }

        Path unedited = Files.createDirectories(folder.resolve("unedited"));
        boolean[] withNoEdit;
        int askedWithNoEdit;
        try (WindowForTests window = WindowForTests.shown(aStateOver(unedited))) {
            window.openProfile();
            Stage profile = window.profileWindow();
            withNoEdit = closedOnceTheSidecarsAreStopped(window, profile);
            askedWithNoEdit = window.dialogs.choices.size();
        }

        claim("with an edit unsaved, Cancel keeps both windows open and the file as the test wrote it",
                () -> {
                    assertThat(afterCancel).containsExactly(true, true);
                    assertThat(fileAfterCancel).isEqualTo(asWritten);
                });
        claim("Save writes the edit, as set in the control panel, and closes both windows",
                () -> {
                    assertThat(afterSave).containsExactly(false, false);
                    assertThat(statedAfterSave).isEqualTo(new ProfileEdit.Stated(FIRST_EDIT, SET_IN_THE_CONTROL_PANEL));
                });
        claim("each of the two closings was asked once, titled The profile, with Save, Discard and Cancel in that order",
                () -> assertThat(askedOnSave).containsExactly(THE_CHOICE, THE_CHOICE));
        claim("Discard, asked once, closes both windows and writes nothing",
                () -> {
                    assertThat(askedOnDiscard).isEqualTo(1);
                    assertThat(afterDiscard).containsExactly(false, false);
                    assertThat(Files.readAllBytes(discardingFile)).isEqualTo(discardingAsWritten);
                });
        claim("and with no edit, closing the control panel's window closes both, asking nothing",
                () -> {
                    assertThat(withNoEdit).containsExactly(false, false);
                    assertThat(askedWithNoEdit).isZero();
                });
    }

    @Test
    @Story("An edit of the profile is not lost by loading the files again")
    @DisplayName("Loading the working directory's files again with an edit unsaved in the profile window asks Save, Discard or Cancel first: Cancel keeps the edit and loads all but the form, Save writes and then loads, a Save refused keeps the edit as Cancel does, Discard loads, and with no edit it loads asking nothing")
    void loadingTheFilesAgainWithAnEditUnsavedAsksSaveDiscardOrCancelFirst(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path profileFile = aProfileNamingTheSpace(working);
        String seedFolder = Files.createDirectories(folder.resolve("seed")).toString();

        List<String> formAfterCancel;
        String seedRowAfterCancel;
        ProfileEdit.Stated statedAfterCancel;
        int askedOnCancel;
        ProfileEdit.Stated statedAfterSave;
        List<String> formAfterSave;
        List<String> formAfterRefusedSave;
        String outcomeOfRefusedSave;
        ProfileEdit.Stated statedAfterRefusedSave;
        byte[] fileBeforeDiscard;
        byte[] fileAfterDiscard;
        List<String> formAfterDiscard;
        int askedWithEdits;
        List<String> formWithNoEdit;
        int askedInAll;
        List<AnsweringDialogs.Chose> choices;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, working))) {
            window.openProfile();

            typed(window, FIRST_EDIT);
            writtenMeanwhile(profileFile, ProfileKey.CONFLUENCE_FOLDER_TITLE, TITLE_BEFORE_CANCEL);
            writtenMeanwhile(profileFile, ProfileKey.SEED_FOLDER, seedFolder);
            window.dialogs.chosen = Optional.of(CANCEL);
            loadTheFilesAgain(window);
            formAfterCancel = window.readProfile(TheProfileWindowTest::spaceAndTitle);
            seedRowAfterCancel = window.read(root -> WindowForTests.node(root, THE_SEED_ROWS_FIELD,
                    TextInputControl.class).getText());
            statedAfterCancel = spaceIn(profileFile);
            askedOnCancel = window.dialogs.choices.size();

            window.dialogs.chosen = Optional.of(SAVE);
            loadTheFilesAgain(window);
            statedAfterSave = spaceIn(profileFile);
            formAfterSave = window.readProfile(TheProfileWindowTest::spaceAndTitle);

            typed(window, SECOND_EDIT);
            writtenMeanwhile(profileFile, ProfileKey.CONFLUENCE_SPACE_KEY, WRITTEN_MEANWHILE);
            writtenMeanwhile(profileFile, ProfileKey.CONFLUENCE_FOLDER_TITLE, TITLE_BEFORE_REFUSED_SAVE);
            window.dialogs.chosen = Optional.of(SAVE);
            loadTheFilesAgain(window);
            formAfterRefusedSave = window.readProfile(TheProfileWindowTest::spaceAndTitle);
            outcomeOfRefusedSave = window.readProfile(root -> WindowForTests.node(root, "profile-outcome",
                    TextInputControl.class).getText());
            statedAfterRefusedSave = spaceIn(profileFile);

            fileBeforeDiscard = Files.readAllBytes(profileFile);
            window.dialogs.chosen = Optional.of(DISCARD);
            loadTheFilesAgain(window);
            fileAfterDiscard = Files.readAllBytes(profileFile);
            formAfterDiscard = window.readProfile(TheProfileWindowTest::spaceAndTitle);
            askedWithEdits = window.dialogs.choices.size();

            writtenMeanwhile(profileFile, ProfileKey.CONFLUENCE_FOLDER_TITLE, TITLE_WITH_NO_EDIT);
            loadTheFilesAgain(window);
            formWithNoEdit = window.readProfile(TheProfileWindowTest::spaceAndTitle);
            askedInAll = window.dialogs.choices.size();
            choices = List.copyOf(window.dialogs.choices);
        }

        claim("Cancel, asked once, keeps the edit and the form as it was loaded, the folder's title the other program"
                        + " wrote not shown, and writes nothing",
                () -> {
                    assertThat(askedOnCancel).isEqualTo(1);
                    assertThat(formAfterCancel).containsExactly(FIRST_EDIT, "");
                    assertThat(statedAfterCancel).isEqualTo(new ProfileEdit.Stated(SPACE, THE_TESTS_PROVENANCE));
                });
        claim("and loads the files for all but the form: Seed folder's row in the control panel's window shows the seed"
                        + " folder the file now states",
                () -> assertThat(seedRowAfterCancel).isEqualTo(seedFolder));
        claim("Save writes the edit, as set in the control panel, and then loads the files, the form showing the"
                        + " folder's title the other program wrote",
                () -> {
                    assertThat(statedAfterSave).isEqualTo(new ProfileEdit.Stated(FIRST_EDIT, SET_IN_THE_CONTROL_PANEL));
                    assertThat(formAfterSave).containsExactly(FIRST_EDIT, TITLE_BEFORE_CANCEL);
                });
        claim("a Save refused, the file having changed the value since the form was loaded, keeps the edit and the form"
                        + " as Cancel does, with the refusal, the file as it was changed",
                () -> {
                    assertThat(formAfterRefusedSave).containsExactly(SECOND_EDIT, TITLE_BEFORE_CANCEL);
                    assertThat(outcomeOfRefusedSave).isEqualTo(CHANGED_SINCE_LOADED);
                    assertThat(statedAfterRefusedSave).isEqualTo(
                            new ProfileEdit.Stated(WRITTEN_MEANWHILE, THE_TESTS_PROVENANCE));
                });
        claim("Discard writes nothing and loads the files, the form showing the file's values, not the edit",
                () -> {
                    assertThat(fileAfterDiscard).isEqualTo(fileBeforeDiscard);
                    assertThat(formAfterDiscard).containsExactly(WRITTEN_MEANWHILE, TITLE_BEFORE_REFUSED_SAVE);
                });
        claim("each of the four loads with an edit unsaved asked once, titled The profile, saying the profile has"
                        + " changes not saved in profile.yaml, with Save, Discard and Cancel in that order",
                () -> {
                    assertThat(askedWithEdits).isEqualTo(4);
                    assertThat(choices).allSatisfy(choice -> assertThat(choice).isEqualTo(THE_CHOICE));
                });
        claim("and with no edit the files are loaded asking nothing, the form showing the file's values",
                () -> {
                    assertThat(askedInAll).isEqualTo(askedWithEdits);
                    assertThat(formWithNoEdit).containsExactly(WRITTEN_MEANWHILE, TITLE_WITH_NO_EDIT);
                });
    }

    @Test
    @Story("An edit of the profile is not lost by loading the files again")
    @DisplayName("Naming another working directory with an edit unsaved in the profile window asks Save or Discard, with no Cancel: Save writes the edit into the old working directory's profile, Discard writes nothing, the new one's profile is shown after either, and with no edit nothing is asked")
    void changingTheWorkingDirectoryWithAnEditUnsavedAsksSaveOrDiscardAndLoadsTheNewOne(@TempDir Path folder)
            throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path workingFile = aProfileNamingTheSpace(working);
        Path other = Files.createDirectories(folder.resolve("other"));
        Path otherFile = aProfileNaming(other, OTHER_SPACE);
        Path third = Files.createDirectories(folder.resolve("third"));
        Path thirdFile = aProfileNaming(third, THIRD_SPACE);
        byte[] otherAsWritten = Files.readAllBytes(otherFile);
        byte[] thirdAsWritten = Files.readAllBytes(thirdFile);

        int askedOnSave;
        ProfileEdit.Stated statedInTheOldAfterSave;
        byte[] newAfterSave;
        String formAfterSave;
        byte[] oldAfterDiscard;
        byte[] newAfterDiscard;
        String formAfterDiscard;
        List<AnsweringDialogs.Chose> askedWithEdits;
        String formWithNoEdit;
        int askedInAll;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, working))) {
            window.openProfile();

            typed(window, FIRST_EDIT);
            window.dialogs.chosen = Optional.of(SAVE);
            workingDirectoryNamed(window, other);
            askedOnSave = window.dialogs.choices.size();
            statedInTheOldAfterSave = spaceIn(workingFile);
            newAfterSave = Files.readAllBytes(otherFile);
            formAfterSave = window.readProfile(TheProfileWindowTest::theFieldsText);

            typed(window, SECOND_EDIT);
            window.dialogs.chosen = Optional.of(DISCARD);
            workingDirectoryNamed(window, third);
            oldAfterDiscard = Files.readAllBytes(otherFile);
            newAfterDiscard = Files.readAllBytes(thirdFile);
            formAfterDiscard = window.readProfile(TheProfileWindowTest::theFieldsText);
            askedWithEdits = List.copyOf(window.dialogs.choices);

            workingDirectoryNamed(window, working);
            formWithNoEdit = window.readProfile(TheProfileWindowTest::theFieldsText);
            askedInAll = window.dialogs.choices.size();
        }

        claim("Save, asked once, writes the edit, as set in the control panel, into the profile of the working directory"
                        + " the form was loaded from, and nothing into the new one's",
                () -> {
                    assertThat(askedOnSave).isEqualTo(1);
                    assertThat(statedInTheOldAfterSave).isEqualTo(
                            new ProfileEdit.Stated(FIRST_EDIT, SET_IN_THE_CONTROL_PANEL));
                    assertThat(newAfterSave).isEqualTo(otherAsWritten);
                });
        claim("and the form then shows the new working directory's profile",
                () -> assertThat(formAfterSave).isEqualTo(OTHER_SPACE));
        claim("Discard writes nothing, into the old working directory's profile or the new one's, and the form then shows"
                        + " the new one's",
                () -> {
                    assertThat(oldAfterDiscard).isEqualTo(otherAsWritten);
                    assertThat(newAfterDiscard).isEqualTo(thirdAsWritten);
                    assertThat(formAfterDiscard).isEqualTo(THIRD_SPACE);
                });
        claim("each of the two changes with an edit unsaved asked once, titled The profile, saying the profile has"
                        + " changes not saved in profile.yaml, with Save and Discard alone, in that order",
                () -> assertThat(askedWithEdits).containsExactly(SAVE_OR_DISCARD, SAVE_OR_DISCARD));
        claim("and with no edit, the first working directory named again, nothing is asked and the form shows the value"
                        + " Save wrote there",
                () -> {
                    assertThat(askedInAll).isEqualTo(askedWithEdits.size());
                    assertThat(formWithNoEdit).isEqualTo(FIRST_EDIT);
                });
    }

    @Test
    @Story("An edit of the profile is not lost by loading the files again")
    @DisplayName("Naming another working directory with an edit unsaved in the profile window and closing the question with no answer saves the edit into the old working directory's profile, and the new one's profile is shown")
    void changingTheWorkingDirectoryWithTheQuestionClosedWithNoAnswerSavesTheEdit(@TempDir Path folder)
            throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path workingFile = aProfileNamingTheSpace(working);
        Path other = Files.createDirectories(folder.resolve("other"));
        Path otherFile = aProfileNaming(other, OTHER_SPACE);
        byte[] otherAsWritten = Files.readAllBytes(otherFile);

        List<AnsweringDialogs.Chose> asked;
        ProfileEdit.Stated statedInTheOld;
        byte[] theNew;
        String form;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, working))) {
            window.openProfile();
            typed(window, FIRST_EDIT);
            window.dialogs.chosen = Optional.empty();
            workingDirectoryNamed(window, other);
            asked = List.copyOf(window.dialogs.choices);
            statedInTheOld = spaceIn(workingFile);
            theNew = Files.readAllBytes(otherFile);
            form = window.readProfile(TheProfileWindowTest::theFieldsText);
        }

        claim("the change was asked once, titled The profile, saying the profile has changes not saved in profile.yaml,"
                        + " with Save and Discard alone, in that order",
                () -> assertThat(asked).containsExactly(SAVE_OR_DISCARD));
        claim("closed with no answer, it saves as Save does: the edit, as set in the control panel, is written into the"
                        + " profile of the working directory the form was loaded from, and nothing into the new one's",
                () -> {
                    assertThat(statedInTheOld).isEqualTo(new ProfileEdit.Stated(FIRST_EDIT, SET_IN_THE_CONTROL_PANEL));
                    assertThat(theNew).isEqualTo(otherAsWritten);
                });
        claim("and the form then shows the new working directory's profile",
                () -> assertThat(form).isEqualTo(OTHER_SPACE));
    }

    @Test
    @Story("An edit of the profile is not lost by loading the files again")
    @DisplayName("Naming another working directory with an edit unsaved in the profile window, a Save refused because the old profile changed meanwhile drops the edit, the new one's profile is shown, and the control panel's outcome says the refusal whole")
    void aSaveRefusedAtAChangeOfTheWorkingDirectoryDropsTheEditAndSaysTheRefusalInTheOutcome(@TempDir Path folder)
            throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path workingFile = aProfileNamingTheSpace(working);
        Path other = Files.createDirectories(folder.resolve("other"));
        Path otherFile = aProfileNaming(other, OTHER_SPACE);
        byte[] otherAsWritten = Files.readAllBytes(otherFile);

        List<AnsweringDialogs.Chose> asked;
        ProfileEdit.Stated statedInTheOld;
        byte[] theNew;
        String form;
        String outcome;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, working))) {
            window.openProfile();
            typed(window, FIRST_EDIT);
            writtenMeanwhile(workingFile, ProfileKey.CONFLUENCE_SPACE_KEY, WRITTEN_MEANWHILE);
            window.dialogs.chosen = Optional.of(SAVE);
            workingDirectoryNamed(window, other);
            asked = List.copyOf(window.dialogs.choices);
            statedInTheOld = spaceIn(workingFile);
            theNew = Files.readAllBytes(otherFile);
            form = window.readProfile(TheProfileWindowTest::theFieldsText);
            outcome = window.read(root -> WindowForTests.node(root, "outcome", TextInputControl.class).getText());
        }

        claim("the change was asked once, titled The profile, saying the profile has changes not saved in profile.yaml,"
                        + " with Save and Discard alone, in that order",
                () -> assertThat(asked).containsExactly(SAVE_OR_DISCARD));
        claim("the Save, refused because the old profile changed the value since the form was loaded, writes nothing:"
                        + " the old profile is as the other program left it and the new one as the test wrote it",
                () -> {
                    assertThat(statedInTheOld).isEqualTo(new ProfileEdit.Stated(WRITTEN_MEANWHILE, THE_TESTS_PROVENANCE));
                    assertThat(theNew).isEqualTo(otherAsWritten);
                });
        claim("the edit is dropped and the working directory changed all the same: the form shows the new working"
                        + " directory's profile",
                () -> assertThat(form).isEqualTo(OTHER_SPACE));
        claim("and the control panel's outcome holds the refusal, whole",
                () -> assertThat(outcome).isEqualTo(CHANGED_SINCE_LOADED));
    }

    @Test
    @Story("An edit of the profile is not lost by loading the files again")
    @DisplayName("Naming the same working directory another way, with a trailing separator and then through . and .., with an edit unsaved in the profile window asks Save, Discard or Cancel as any loading of the files does, and Cancel writes nothing and keeps the edit")
    void namingTheSameWorkingDirectoryAnotherWayAsksSaveDiscardOrCancelAsAnyLoad(@TempDir Path folder)
            throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path workingFile = aProfileNamingTheSpace(working);
        Path beside = Files.createDirectories(folder.resolve("beside"));
        byte[] asWritten = Files.readAllBytes(workingFile);
        List<String> respellings = List.of(working + File.separator,
                beside.resolve("..").resolve(".").resolve("working").toString());

        List<AnsweringDialogs.Chose> asked;
        byte[] theFile;
        String form;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, working))) {
            window.openProfile();
            typed(window, FIRST_EDIT);
            window.dialogs.chosen = Optional.of(CANCEL);
            for (String respelled : respellings) {
                workingDirectorySpelled(window, respelled);
            }
            asked = List.copyOf(window.dialogs.choices);
            theFile = Files.readAllBytes(workingFile);
            form = window.readProfile(TheProfileWindowTest::theFieldsText);
        }

        claim("each of the two spellings names the folder the form was loaded from, so each is asked about as any loading"
                        + " of the files is: once, titled The profile, saying the profile has changes not saved in"
                        + " profile.yaml, with Save, Discard and Cancel in that order, and never with Save and Discard"
                        + " alone",
                () -> assertThat(asked).containsExactly(THE_CHOICE, THE_CHOICE));
        claim("Cancel writes nothing: the profile is as the test wrote it",
                () -> assertThat(theFile).isEqualTo(asWritten));
        claim("and keeps the edit in the form",
                () -> assertThat(form).isEqualTo(FIRST_EDIT));
    }

    @Test
    @Story("An edit of the profile is not lost by loading the files again")
    @DisplayName("Emptying the working directory field where the folder an empty field stands for is the one the form was loaded from, with an edit unsaved in the profile window, asks Save, Discard or Cancel as any loading of the files does, and Cancel writes nothing and keeps the edit")
    void emptyingTheFieldWhereItStandsForTheSameFolderAsksSaveDiscardOrCancelAsAnyLoad(@TempDir Path folder)
            throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path workingFile = aProfileNamingTheSpace(working);
        byte[] asWritten = Files.readAllBytes(workingFile);
        ControlPanelState state = aStateOver(folder, working);
        state.useWorkingDirectoryDefault(WorkingDirectoryDefault.of(folder,
                Map.of("vespera.working-dir", working.toString()), Map.of()));

        List<AnsweringDialogs.Chose> asked;
        byte[] theFile;
        String form;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.openProfile();
            typed(window, FIRST_EDIT);
            window.dialogs.chosen = Optional.of(CANCEL);
            workingDirectorySpelled(window, "");
            asked = List.copyOf(window.dialogs.choices);
            theFile = Files.readAllBytes(workingFile);
            form = window.readProfile(TheProfileWindowTest::theFieldsText);
        }

        claim("an empty field stands for the folder the form was loaded from, so it is asked about as any loading of the"
                        + " files is: once, titled The profile, saying the profile has changes not saved in profile.yaml,"
                        + " with Save, Discard and Cancel in that order, and never with Save and Discard alone",
                () -> assertThat(asked).containsExactly(THE_CHOICE));
        claim("Cancel writes nothing: the profile is as the test wrote it",
                () -> assertThat(theFile).isEqualTo(asWritten));
        claim("and keeps the edit in the form",
                () -> assertThat(form).isEqualTo(FIRST_EDIT));
    }

    @Test
    @Story("An edit of the profile is not lost by loading the files again")
    @DisplayName("On Windows, naming the working directory with its name in capitals, with an edit unsaved in the profile window, asks Save, Discard or Cancel as any loading of the files does, the file system ignoring case, and Cancel writes nothing and keeps the edit")
    void onWindowsTheWorkingDirectoryNamedInOtherCaseAsksSaveDiscardOrCancelAsAnyLoad(@TempDir Path folder)
            throws Exception {
        assumeThat(System.getProperty("os.name", "")).as("the operating system, whose file system ignores case on"
                + " Windows alone").startsWith("Windows");
        Path working = Files.createDirectories(folder.resolve("working"));
        Path workingFile = aProfileNamingTheSpace(working);
        Path inCapitals = folder.resolve(WORKING_IN_CAPITALS);
        assumeThat(Files.exists(inCapitals) && Files.isSameFile(working, inCapitals))
                .as("the temporary folder's file system finds the working directory by its name in capitals").isTrue();
        byte[] asWritten = Files.readAllBytes(workingFile);

        List<AnsweringDialogs.Chose> asked;
        byte[] theFile;
        String form;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, working))) {
            window.openProfile();
            typed(window, FIRST_EDIT);
            window.dialogs.chosen = Optional.of(CANCEL);
            workingDirectoryNamed(window, inCapitals);
            asked = List.copyOf(window.dialogs.choices);
            theFile = Files.readAllBytes(workingFile);
            form = window.readProfile(TheProfileWindowTest::theFieldsText);
        }

        claim("the name in capitals names the folder the form was loaded from, so it is asked about as any loading of the"
                        + " files is: once, titled The profile, saying the profile has changes not saved in profile.yaml,"
                        + " with Save, Discard and Cancel in that order, and not with Save and Discard alone",
                () -> assertThat(asked).containsExactly(THE_CHOICE));
        claim("Cancel writes nothing: the profile is as the test wrote it",
                () -> assertThat(theFile).isEqualTo(asWritten));
        claim("and keeps the edit in the form",
                () -> assertThat(form).isEqualTo(FIRST_EDIT));
    }

    @Test
    @Story("An edit of the profile is not lost by loading the files again")
    @DisplayName("The profile's question, asked in the system's dialog with Save and Discard or with Save, Discard and Cancel, gives the answer whose button is pressed, and no answer where the dialog is closed by its close button or by Escape with no button pressed")
    void theQuestionGivesTheAnswerPressedAndNoneWhereItIsClosedWithNoButtonPressed() throws Exception {
        List<List<String>> answerLists = List.of(SAVE_OR_DISCARD.answers(), THE_CHOICE.answers());
        Stage owner = HeadlessToolkit.onTheToolkit(() -> {
            Stage made = new Stage();
            made.setScene(new Scene(new VBox()));
            made.show();
            return made;
        });
        Map<List<String>, List<Ended>> pressed = new LinkedHashMap<>();
        Map<List<String>, List<Ended>> closed = new LinkedHashMap<>();
        try {
            for (List<String> answers : answerLists) {
                List<Ended> byButton = new ArrayList<>();
                for (String answer : answers) {
                    byButton.add(askedAndEnded(owner, answers, pane -> pressing(pane, answer)));
                }
                pressed.put(answers, byButton);
                closed.put(answers, List.of(askedAndEnded(owner, answers, TheProfileWindowTest::closeRequested),
                        askedAndEnded(owner, answers, TheProfileWindowTest::escapePressed)));
            }
        } finally {
            HeadlessToolkit.onTheToolkit(() -> {
                owner.close();
                return null;
            });
        }
        List<Ended> eachAnswerGiven = List.of(new Ended(true, Optional.of(SAVE)), new Ended(true, Optional.of(DISCARD)));
        List<Ended> eachOfThreeGiven = List.of(new Ended(true, Optional.of(SAVE)), new Ended(true, Optional.of(DISCARD)),
                new Ended(true, Optional.of(CANCEL)));
        List<Ended> noneGiven = List.of(new Ended(true, Optional.empty()), new Ended(true, Optional.empty()));

        claim("with Save and Discard, pressing each answer's button closes the dialog and gives that answer's words, Save"
                        + " then Discard",
                () -> assertThat(pressed.get(answerLists.get(0))).containsExactlyElementsOf(eachAnswerGiven));
        claim("with Save, Discard and Cancel, pressing each answer's button closes the dialog and gives that answer's"
                        + " words, Save, then Discard, then Cancel",
                () -> assertThat(pressed.get(answerLists.get(1))).containsExactlyElementsOf(eachOfThreeGiven));
        claim("with Save and Discard, the dialog closed by its close button, then by Escape, with no button pressed, closes"
                        + " and gives no answer, and not its last answer, Discard",
                () -> assertThat(closed.get(answerLists.get(0))).containsExactlyElementsOf(noneGiven));
        claim("with Save, Discard and Cancel, the dialog closed by its close button, then by Escape, with no button"
                        + " pressed, closes and gives no answer, and not its last answer, Cancel",
                () -> assertThat(closed.get(answerLists.get(1))).containsExactlyElementsOf(noneGiven));
    }

    @Test
    @Story("The profile is edited in a window of its own")
    @DisplayName("Once the next invocation wants a value, the Profile section and the open profile window's title both say it needs a value")
    void theSectionAndTheWindowsTitleCarryTheBadge(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Files.writeString(working.resolve(NextInvocation.FILE_NAME),
                "invocation=run\nwants=boilerplateDocumentFrequencyFloor\nanswer=false\n", StandardCharsets.UTF_8);
        ControlPanelState state = WindowForTests.aState(folder, EndingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(),
                "", "", "", "", "", ""));

        String sectionBefore;
        String windowBefore;
        String sectionAfter;
        String windowAfter;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.openProfile();
            Stage profile = window.profileWindow();
            sectionBefore = window.read(TheProfileWindowTest::sectionTitle);
            windowBefore = HeadlessToolkit.onTheToolkit(profile::getTitle);
            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            assumeThat(refusal).as("the child that ends at once is started").isEmpty();
            boolean wanted = window.awaiting(
                    root -> HeadlessToolkit.nodeWithId(root, "wanted-row-boilerplateDocumentFrequencyFloor").isPresent(),
                    Boolean::booleanValue);
            assumeThat(wanted).as("the value the next invocation wants is read once the child has ended").isTrue();
            sectionAfter = window.awaiting(TheProfileWindowTest::sectionTitle, PROFILE_NEEDING_A_VALUE::equals);
            windowAfter = HeadlessToolkit.onTheToolkit(profile::getTitle);
        }

        claim("before the next invocation wants a value, the section and the window are titled Profile",
                () -> assertThat(List.of(sectionBefore, windowBefore)).containsExactly(PROFILE, PROFILE));
        claim("once it wants one, both read Profile (needs a value), the window's title following while it is open",
                () -> assertThat(List.of(sectionAfter, windowAfter))
                        .containsExactly(PROFILE_NEEDING_A_VALUE, PROFILE_NEEDING_A_VALUE));
    }

    @Test
    @Story("A refusal naming the profile window opens it")
    @DisplayName("A line naming the profile window, the child's or the window's own, shows Open profile beside the outcome, and pressing it opens the profile window")
    void aLineNamingTheProfileWindowOffersAButtonThatOpensIt(@TempDir Path folder) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder, PrintingJars.refusedPublish());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("working")).toString(), "", "", "", "", "", ""));

        boolean shownBefore;
        String childLine;
        boolean shownAfterTheChild;
        int opened;
        try (WindowForTests window = WindowForTests.shown(state)) {
            shownBefore = window.read(TheProfileWindowTest::buttonShown);
            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            assumeThat(refusal).as("the child printing the refusal is started").isEmpty();
            childLine = window.awaiting(root -> WindowForTests.node(root, "child", TextInputControl.class).getText(),
                    text -> text.contains(ARefusedPublishChildForTests.ITS_LAST_LINE));
            shownAfterTheChild = window.awaiting(TheProfileWindowTest::buttonShown, Boolean::booleanValue);
            window.act(root -> WindowForTests.node(root, "outcome-open-profile", Button.class).fire());
            opened = window.profileWindows().size();
        }

        Path ownLine = Files.createDirectories(folder.resolve("own-line"));
        boolean shownBeforeTheLine;
        boolean shownWithTheLine;
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(ownLine, SleepingJar.runtime()))) {
            shownBeforeTheLine = window.read(TheProfileWindowTest::buttonShown);
            window.act(root -> WindowForTests.node(root, "outcome", TextInputControl.class).setText(LEFT_AS_IT_WAS));
            shownWithTheLine = window.awaiting(TheProfileWindowTest::buttonShown, Boolean::booleanValue);
        }

        claim("before any line names the profile window, no Open profile stands beside the outcome",
                () -> assertThat(List.of(shownBefore, shownBeforeTheLine)).containsExactly(false, false));
        claim("the child's line, a publish refused for a parent named twice, naming the profile window, is shown in the"
                        + " line saying how the child ended",
                () -> assertThat(childLine).contains(ARefusedPublishChildForTests.ITS_LAST_LINE));
        claim("Open profile then stands beside the outcome, and pressing it opens the one profile window",
                () -> {
                    assertThat(shownAfterTheChild).isTrue();
                    assertThat(opened).isEqualTo(1);
                });
        claim("and the window's own line after a parent's title warning is cancelled, naming it too, shows it",
                () -> assertThat(shownWithTheLine).isTrue());
    }

    @Test
    @Story("The profile is edited in a window of its own")
    @DisplayName("The profile window takes the control panel's stylesheet, and its dark theme and back as the switch moves, while it is open")
    void theProfileWindowFollowsTheThemeSwitch(@TempDir Path folder) throws Exception {
        List<String> theCockpitsSheets;
        List<String> itsSheets;
        boolean darkAtFirst;
        boolean darkWithTheCockpit;
        boolean darkAfterLight;
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, SleepingJar.runtime()))) {
            theCockpitsSheets = window.read(root -> List.copyOf(root.getStylesheets()));
            window.openProfile();
            Stage profile = window.profileWindow();
            itsSheets = HeadlessToolkit.onTheToolkit(() -> {
                List<String> sheets = new ArrayList<>(profile.getScene().getStylesheets());
                sheets.addAll(profile.getScene().getRoot().getStylesheets());
                return sheets;
            });
            darkAtFirst = window.readProfile(TheProfileWindowTest::dark);
            window.act(root -> WindowForTests.node(root, "theme-toggle", ToggleButton.class).setSelected(true));
            darkWithTheCockpit = window.readProfile(TheProfileWindowTest::dark);
            window.act(root -> WindowForTests.node(root, "theme-toggle", ToggleButton.class).setSelected(false));
            darkAfterLight = window.readProfile(TheProfileWindowTest::dark);
        }

        claim("the profile window carries the control panel's stylesheet",
                () -> {
                    assertThat(theCockpitsSheets).isNotEmpty();
                    assertThat(itsSheets).containsAll(theCockpitsSheets);
                });
        claim("it opens light with a light control panel, turns dark as the switch turns the control panel dark, and"
                        + " light again with it, nothing reopened",
                () -> assertThat(List.of(darkAtFirst, darkWithTheCockpit, darkAfterLight))
                        .containsExactly(false, true, false));
    }

    // ---- what the tests share

    /** A state holding a corpus root and a working directory under {@code folder} as launch values. */
    private static ControlPanelState aStateOver(Path folder) throws IOException {
        return aStateOver(folder, Files.createDirectories(folder.resolve("working")));
    }

    private static ControlPanelState aStateOver(Path folder, Path working) throws IOException {
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(),
                "", "", "", "", "", ""));
        return state;
    }

    /** The working directory's {@code profile.yaml}, naming the space {@link #SPACE} with {@link #THE_TESTS_PROVENANCE}. */
    private static Path aProfileNamingTheSpace(Path working) throws IOException {
        return aProfileNaming(working, SPACE);
    }

    /** {@code working}'s {@code profile.yaml}, naming the space {@code space} with {@link #THE_TESTS_PROVENANCE}. */
    private static Path aProfileNaming(Path working, String space) throws IOException {
        return Files.writeString(working.resolve(ProfileEdit.FILE_NAME), ProfileEdit.rewrite(Optional.empty(),
                ProfileKey.CONFLUENCE_SPACE_KEY, space, THE_TESTS_PROVENANCE), StandardCharsets.UTF_8);
    }

    /** Names {@code working} in the control panel's working directory field, as the operator does. */
    private static void workingDirectoryNamed(WindowForTests window, Path working) throws Exception {
        workingDirectorySpelled(window, working.toString());
    }

    /** Writes {@code spelled} in the control panel's working directory field, as the operator does. */
    private static void workingDirectorySpelled(WindowForTests window, String spelled) throws Exception {
        window.act(root -> WindowForTests.node(root, "vespera.working-dir", TextInputControl.class).setText(spelled));
    }

    /**
     * How one asking of the system's dialog ended: whether what the test did closed it, and the answer it gave. A dialog
     * the test's act left open is hidden by the test, so that its loop ends, and reads {@code closedByIt} false.
     */
    private record Ended(boolean closedByIt, Optional<String> answer) {
    }

    /** How many turns of the toolkit the test waits for the system's dialog to show. */
    private static final int TURNS = 100;

    /**
     * Asks the profile's question with {@code answers} through the system's dialogs over {@code owner}, and ends it, once
     * it shows, by {@code ending} on its pane: how it ended. Run on the toolkit's thread, where the dialog's own loop
     * runs the ending.
     */
    private static Ended askedAndEnded(Stage owner, List<String> answers, Consumer<DialogPane> ending) throws Exception {
        Dialogs dialogs = Dialogs.of(() -> owner, address -> { });
        return HeadlessToolkit.onTheToolkit(() -> {
            List<Boolean> closedByIt = new ArrayList<>();
            endWhenShown(ending, closedByIt, 0);
            Optional<String> answer = dialogs.choose(THE_QUESTIONS_TITLE, THE_QUESTION, answers);
            if (closedByIt.isEmpty()) {
                throw new AssertionError("no dialog titled " + THE_QUESTIONS_TITLE + " showed");
            }
            return new Ended(closedByIt.getFirst(), answer);
        });
    }

    private static void endWhenShown(Consumer<DialogPane> ending, List<Boolean> closedByIt, int turn) {
        Platform.runLater(() -> {
            Optional<Stage> shown = Window.getWindows().stream()
                    .filter(each -> each instanceof Stage stage && THE_QUESTIONS_TITLE.equals(stage.getTitle()))
                    .filter(each -> each.isShowing() && each.getScene() != null)
                    .map(Stage.class::cast).findFirst();
            Optional<DialogPane> pane = shown.flatMap(stage -> HeadlessToolkit.everyNodeUnder(stage.getScene().getRoot())
                    .stream().filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst());
            if (pane.isEmpty()) {
                if (turn < TURNS) {
                    endWhenShown(ending, closedByIt, turn + 1);
                }
                return;
            }
            ending.accept(pane.get());
            boolean stillShowing = shown.get().isShowing();
            closedByIt.add(!stillShowing);
            if (stillShowing) {
                shown.get().hide();
            }
        });
    }

    /** Presses the button of {@code answer}, as the operator clicks it; the test fails where the dialog has none. */
    private static void pressing(DialogPane pane, String answer) {
        ButtonType type = pane.getButtonTypes().stream().filter(each -> answer.equals(each.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("the dialog has no button " + answer));
        ((Button) pane.lookupButton(type)).fire();
    }

    /** Asks the dialog's window to close as its close button does, pressing no button. */
    private static void closeRequested(DialogPane pane) {
        Window window = pane.getScene().getWindow();
        Event.fireEvent(window, new WindowEvent(window, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    /** Presses Escape in the dialog, at the control holding the focus or, where none does, at its pane. */
    private static void escapePressed(DialogPane pane) {
        Node target = Optional.ofNullable(pane.getScene().getFocusOwner()).orElse(pane);
        Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
        Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.ESCAPE, false, false, false, false));
    }

    /** Every key of the profile, in its order. */
    private static List<String> everyKey() {
        return Arrays.stream(ProfileKey.values()).map(ProfileKey::key).toList();
    }

    /** The keys whose row in the Profile window shows a title, in the profile's order. */
    private static List<String> keysWithATitle(Parent root) {
        return everyKey().stream()
                .filter(key -> HeadlessToolkit.nodeWithId(root, "profile-" + key + ".title")
                        .filter(SelectableText.Line.class::isInstance).isPresent())
                .toList();
    }

    /** The keys whose row's title in the Profile window shows the ellipsis of a cut line, in the profile's order. */
    private static List<String> titlesCut(Parent root) {
        return everyKey().stream()
                .filter(key -> HeadlessToolkit.nodeWithId(root, "profile-" + key + ".title")
                        .filter(SelectableText.Line.class::isInstance).map(SelectableText.Line.class::cast)
                        .map(title -> title.ellipsis().isVisible()).orElse(false))
                .toList();
    }

    /** What {@code profile.yaml} states for the space's key. */
    private static ProfileEdit.Stated spaceIn(Path profileFile) throws IOException {
        return ProfileEdit.read(Files.readString(profileFile, StandardCharsets.UTF_8))
                .get(ProfileKey.CONFLUENCE_SPACE_KEY);
    }

    /** Types {@code value} in the space's field of the Profile window, as the operator does, leaving it unsaved. */
    private static void typed(WindowForTests window, String value) throws Exception {
        window.actOnProfile(root -> WindowForTests.node(root, THE_FIELD, TextInputControl.class).setText(value));
    }

    /** Writes {@code value} for {@code key} into {@code profileFile}, as another program does, with the test's provenance. */
    private static void writtenMeanwhile(Path profileFile, ProfileKey key, String value) throws IOException {
        Files.writeString(profileFile, ProfileEdit.rewrite(Optional.of(Files.readString(profileFile,
                StandardCharsets.UTF_8)), key, value, THE_TESTS_PROVENANCE), StandardCharsets.UTF_8);
    }

    /**
     * Has the control panel load the working directory's files again, as Seed folder's Save does after it saves, which
     * here writes nothing, the row being as the file stated it when it was loaded.
     */
    private static void loadTheFilesAgain(WindowForTests window) throws Exception {
        window.act(root -> {
            WindowForTests.section(root, "seed-folder").setExpanded(true);
            WindowForTests.node(root, "seed-folder-save", Button.class).fire();
        });
    }

    /** What the Profile window's form shows for the space's key and the folder's title, in that order. */
    private static List<String> spaceAndTitle(Parent root) {
        return List.of(theFieldsText(root), WindowForTests.node(root, THE_TITLE_FIELD, TextInputControl.class).getText());
    }

    private static String theFieldsText(Parent root) {
        return WindowForTests.node(root, THE_FIELD, TextInputControl.class).getText();
    }

    private static String sectionTitle(Parent root) {
        return WindowForTests.section(root, "profile").getText();
    }

    /** Whether Open profile stands beside the outcome: there, shown and laid out. */
    private static boolean buttonShown(Parent root) {
        return HeadlessToolkit.nodeWithId(root, "outcome-open-profile")
                .map(button -> button.isVisible() && button.isManaged())
                .orElse(false);
    }

    private static boolean dark(Parent root) {
        return root.getStyleClass().contains("dark");
    }

    /** Whether the control panel's window, which {@code cockpitShowing} says, and {@code profile} are showing, in that order. */
    /**
     * Closes the cockpit as the operator does and waits, within the tests' bound, for it to close: since ADR-390 §5 it
     * stays shown while its child and then the sidecars are stopped, and closes after. Gives whether the cockpit and the
     * Profile window are each still shown.
     */
    private static boolean[] closedOnceTheSidecarsAreStopped(WindowForTests window, Stage profile) throws Exception {
        WindowForTests.closeAsTheOperatorDoes(window.stage);
        WindowForTests.within(() -> {
            try {
                return !HeadlessToolkit.onTheToolkit(window.stage::isShowing);
            } catch (Exception unanswered) {
                throw new IllegalStateException(unanswered);
            }
        });
        return showing(HeadlessToolkit.onTheToolkit(window.stage::isShowing), profile);
    }

    private static boolean[] showing(boolean cockpitShowing, Stage profile) throws Exception {
        return new boolean[] {cockpitShowing, HeadlessToolkit.onTheToolkit(profile::isShowing)};
    }

    private static Placed placeOf(Stage window) {
        return new Placed(window.getX(), window.getY(), window.getWidth(), window.getHeight());
    }
}
