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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The profile edited in a window of its own (ADR-388): Profile is the sidebar's first section, holding Open profile,
 * which opens the one Profile window, a stage apart from the cockpit's, or brings it to the front; its size and place
 * are kept by the cockpit and written nowhere; closing it, or the cockpit, with an edit unsaved asks Save, Discard or
 * Cancel; the section and the window's title carry the badge; a line naming the Profile window offers a button opening
 * it; and the window follows the cockpit's theme.
 *
 * <p>Each test shows the cockpit as the control panel shows it, on the Headless platform (ADR-342 §6), with dialogs the
 * test answers, and finds the Profile window among the toolkit's windows by the form its root holds. A window is closed
 * as the system's close button closes it, by a close request a handler may consume.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("799")
@Link(name = "ADR-388", url = Adr.THE_PROFILE_IS_EDITED_IN_A_WINDOW_OF_ITS_OWN, type = "adr")
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

    /** Where the test moves the Profile window to, and how large it makes it: inside the Headless platform's screen. */
    private static final Placed MOVED_TO = new Placed(40, 30, 700, 500);

    /** The window's own line after a parent's title warning is cancelled, naming the Profile window (ADR-388 §6). */
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
            afterSave = showing(WindowForTests.closeAsTheOperatorDoes(window.stage), profile);
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
            afterDiscard = showing(WindowForTests.closeAsTheOperatorDoes(window.stage), profile);
            askedOnDiscard = window.dialogs.choices.size();
        }

        Path unedited = Files.createDirectories(folder.resolve("unedited"));
        boolean[] withNoEdit;
        int askedWithNoEdit;
        try (WindowForTests window = WindowForTests.shown(aStateOver(unedited))) {
            window.openProfile();
            Stage profile = window.profileWindow();
            withNoEdit = showing(WindowForTests.closeAsTheOperatorDoes(window.stage), profile);
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
        return Files.writeString(working.resolve(ProfileEdit.FILE_NAME), ProfileEdit.rewrite(Optional.empty(),
                ProfileKey.CONFLUENCE_SPACE_KEY, SPACE, THE_TESTS_PROVENANCE), StandardCharsets.UTF_8);
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
    private static boolean[] showing(boolean cockpitShowing, Stage profile) throws Exception {
        return new boolean[] {cockpitShowing, HeadlessToolkit.onTheToolkit(profile::isShowing)};
    }

    private static Placed placeOf(Stage window) {
        return new Placed(window.getX(), window.getY(), window.getWidth(), window.getHeight());
    }
}
