package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The root page's title is a row of the Profile window (ADR-398 §7): the control panel's table names {@code
 * confluenceRootTitle} last but one, before {@code outputs} (ADR-434 §2), a text entering no run with no value beneath the profile, with its own words; its field
 * follows the email's among the Confluence values; and a title typed and saved is written into {@code profile.yaml}
 * with the provenance a value set in the control panel is saved with (ADR-343 §2).
 *
 * <p>The window's test shows the cockpit as the control panel shows it, on the Headless platform (ADR-342 §6), and
 * opens the Profile window as the operator does, its dialogs answered by the test.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("837")
@Link(name = "ADR-398", url = Adr.THE_CONFLUENCE_ROOT_PAGES_TITLE_IS_A_PROFILE_VALUE, type = "adr")
@Link(name = "ADR-389", url = Adr.THE_PROFILE_IS_EDITED_IN_A_WINDOW_OF_ITS_OWN, type = "adr")
class TheRootPagesTitleIsARowOfTheProfileWindowTest {

    /** The key, as {@code profile.yaml} spells it, and the id the Profile window gives its field. */
    private static final String THE_KEY = "confluenceRootTitle";

    private static final String THE_FIELD = "profile-" + THE_KEY;

    /** What the row says, each as ADR-398 §7 words it. */
    private static final String HINT = "the title of the top page the others go under, {language} standing for the"
            + " language's code; empty is Vespera_{language}";

    private static final String EXAMPLE = "Vespera_{language}";

    private static final String TITLE = "Confluence top page title";

    private static final String SHORT = "The title of the top page Publish puts all your other pages under; {language}"
            + " stands for the language's code.";

    private static final String TOOLTIP = "Text, empty by default, which gives the title Vespera_{language}: Vespera_en"
            + " for pages in English, Vespera_it for pages in Italian. Write {language} where the language's code should"
            + " go, for example Hand-overs {language}; without it, an underscore and the code are added at the end, so"
            + " Hand-overs becomes Hand-overs_en. Each language has its own top page, so its title always carries the"
            + " code. The title can be at most 255 characters once the code is in it; a longer one is refused before"
            + " anything is sent. After a change, the next Publish renames the top page you published before, keeping"
            + " its address and the pages under it, and makes no new one. Publishing only to the website copy does not"
            + " use it.";

    /** The Confluence values' fields in the Profile window, in the order the window holds them. */
    private static final List<String> THE_CONFLUENCE_FIELDS = List.of("profile-confluenceBaseUrl",
            "profile-confluenceSpaceKey", "profile-confluenceFolderId", "profile-confluenceFolderTitle",
            "profile-confluenceEmail", THE_FIELD);

    /** The title the test types, and the provenance a value set in the control panel is saved with (ADR-343 §2). */
    private static final String A_TITLE = "Hand-overs {language}";

    private static final String SET_IN_THE_CONTROL_PANEL = "set in the control panel";

    /** The space's key the working directory's profile names at first, with the test's provenance. */
    private static final String SPACE = "KT";

    private static final String THE_TESTS_PROVENANCE = "set by the test";

    private static final String SAVE = "Save";

    @Test
    @Story("The operator names the root page's title")
    @DisplayName("The control panel's last profile key but one, before the outputs, is the Confluence top page title: text, entering no run, with nothing beneath the profile, and its own hint, example, title, description and tooltip")
    @Link(name = "ADR-434", url = Adr.THE_PROFILES_OUTPUTS_CHOOSE_WHAT_IS_DRAWN_AND_RETRIEVAL_RECORDS_ARE_ONE_JSONL_FILE, type = "adr")
    void itsRowIsATextEnteringNoRunWithItsWords() {
        Optional<ProfileKey> key = ProfileKey.named(THE_KEY);

        claim("the control panel's table of profile keys names confluenceRootTitle last but one, outputs last",
                () -> assertThat(Arrays.stream(ProfileKey.values()).map(ProfileKey::key).toList())
                        .endsWith(THE_KEY, "outputs"));
        claim("its value is text, its change enters no run, and nothing beneath the profile gives it a value",
                () -> assertThat(key.map(it -> List.of(it.kind().name(), it.earliestRun().isPresent(),
                        it.hasAValueBeneath()))).isEqualTo(Optional.of(List.of("TEXT", false, false))));
        claim("and its hint, its example, its title, its description and its tooltip are its own words",
                () -> assertThat(key.map(it -> List.of(it.hint(), it.example(), it.title(), it.shortDescription(),
                        it.tooltip()))).isEqualTo(Optional.of(List.of(HINT, EXAMPLE, TITLE, SHORT, TOOLTIP))));
    }

    @Test
    @Story("The operator names the root page's title")
    @DisplayName("In the profile window the top page title's field follows the sign-in email's, its row titled with its title, and a title typed and saved is written into profile.yaml as set in the control panel")
    void itsFieldFollowsTheEmailsAndASaveWritesItWithItsProvenance(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path profileFile = Files.writeString(working.resolve(ProfileEdit.FILE_NAME), ProfileEdit.rewrite(
                Optional.empty(), ProfileKey.CONFLUENCE_SPACE_KEY, SPACE, THE_TESTS_PROVENANCE), StandardCharsets.UTF_8);
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(),
                "", "", "", "", "", ""));

        List<String> confluenceFields;
        String rowsTitle;
        boolean showingAfterSave = true;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.openProfile();
            confluenceFields = window.readProfile(root -> HeadlessToolkit.everyNodeUnder(root).stream()
                    .filter(TextInputControl.class::isInstance).map(Node::getId)
                    .filter(id -> id != null && id.matches("profile-confluence[A-Za-z]+")).toList());
            rowsTitle = window.readProfile(root -> HeadlessToolkit.nodeWithId(root, THE_FIELD + ".title")
                    .filter(TextInputControl.class::isInstance).map(TextInputControl.class::cast)
                    .map(TextInputControl::getText).orElse("(the window holds no title for " + THE_KEY + ")"));
            if (confluenceFields.contains(THE_FIELD)) {
                window.actOnProfile(root -> WindowForTests.node(root, THE_FIELD, TextInputControl.class)
                        .setText(A_TITLE));
                window.dialogs.chosen = Optional.of(SAVE);
                Stage profile = window.profileWindow();
                showingAfterSave = WindowForTests.closeAsTheOperatorDoes(profile);
            }
        }
        Map<ProfileKey, ProfileEdit.Stated> stated = ProfileEdit.read(Files.readString(profileFile,
                StandardCharsets.UTF_8));
        boolean closedBySave = !showingAfterSave;

        claim("the Profile window's Confluence fields are the site, the space, the parent by its number and by its"
                        + " title, the sign-in email and the top page's title, in that order",
                () -> assertThat(confluenceFields).containsExactlyElementsOf(THE_CONFLUENCE_FIELDS));
        claim("the top page title's row is titled with its title", () -> assertThat(rowsTitle).isEqualTo(TITLE));
        claim("closed with Save after the title was typed, the window closed",
                () -> assertThat(closedBySave).isTrue());
        claim("and profile.yaml states the title typed, as set in the control panel",
                () -> assertThat(ProfileKey.named(THE_KEY).map(stated::get))
                        .isEqualTo(Optional.of(new ProfileEdit.Stated(A_TITLE, SET_IN_THE_CONTROL_PANEL))));
    }
}
