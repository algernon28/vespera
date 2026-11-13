package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A list value is tags (ADR-377 §7): each item of {@code codeNameEndings}, {@code outOfScopeNameEndings} and
 * {@code preferredLanguages} a tag with a remove button, one box after the last where Enter adds what is typed, Backspace
 * in the empty box removing the last tag, and a Save keeping what is still typed. Each window is shown on the Headless
 * platform, the Profile tab selected.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("805")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class AListValueIsTagsTest {

    /** The list value every test but the last works on. */
    private static final ProfileKey ENDINGS = ProfileKey.CODE_NAME_ENDINGS;

    /** The ids of its row's parts start so. */
    private static final String ID = "profile-" + ENDINGS.key();

    /** A provenance the operator wrote by hand. */
    private static final String HAND_WRITTEN = "the endings our build uses";

    /** What profile.yaml states for the list in most tests: three items, split by a space and by a comma. */
    private static final String THREE = ".java .sql, .kt";

    @Test
    @Story("A list value is tags")
    @DisplayName("Each item the file lists is a tag, in the file's order, and the box to add one comes last")
    void eachItemTheFileListsIsATagInItsOrder(@TempDir Path folder) throws Exception {
        Tags tags;
        boolean[] kinds = new boolean[2];
        try (WindowForTests window = windowOver(folder, stated(THREE))) {
            tags = window.read(AListValueIsTagsTest::tagsOf);
            window.act(root -> {
                Node list = WindowForTests.node(root, ID, Node.class);
                kinds[0] = list instanceof FlowPane;
                List<Node> children = ((Pane) list).getChildrenUnmodifiable();
                kinds[1] = children.getLast() instanceof TextField box && (ID + ".add").equals(box.getId());
            });
        }

        claim("the list is a flow of tags", () -> assertThat(kinds[0]).isTrue());
        claim("it holds three tags, numbered from 1, reading .java, .sql and .kt in that order, each a one-line"
                        + " selectable text",
                () -> {
                    assertThat(tags.ids()).containsExactly(ID + ".tag-1", ID + ".tag-2", ID + ".tag-3");
                    assertThat(tags.items()).containsExactly(".java", ".sql", ".kt");
                    assertThat(tags.itemsAreLines()).isTrue();
                });
        claim("the box to add an item is a text field, the last thing the list holds",
                () -> assertThat(kinds[1]).isTrue());
    }

    @Test
    @Story("A list value is tags")
    @DisplayName("Enter adds each piece typed as a tag, once, after the last, and empties the box; Enter in an empty box adds nothing")
    void enterAddsEachPieceTypedAsATagOnceAndEmptiesTheBox(@TempDir Path folder) throws Exception {
        Tags added;
        Tags afterAnEmptyEnter;
        String[] box = new String[1];
        try (WindowForTests window = windowOver(folder, stated(".java"))) {
            window.act(root -> {
                TextField field = box(root);
                field.setText(".py, .java .rs");
                press(field, KeyCode.ENTER);
            });
            added = window.read(AListValueIsTagsTest::tagsOf);
            box[0] = window.read(root -> box(root).getText());
            window.act(root -> press(box(root), KeyCode.ENTER));
            afterAnEmptyEnter = window.read(AListValueIsTagsTest::tagsOf);
        }

        claim("the pieces typed are added after the last tag in the order typed, and .java, a tag already, not twice",
                () -> assertThat(added.items()).containsExactly(".java", ".py", ".rs"));
        claim("the box is emptied", () -> assertThat(box[0]).isEmpty());
        claim("Enter in the empty box adds nothing",
                () -> assertThat(afterAnEmptyEnter.items()).containsExactly(".java", ".py", ".rs"));
    }

    @Test
    @Story("A list value is tags")
    @DisplayName("A tag's remove button removes it, the others keep their order, and the box takes the focus")
    void aTagIsRemovedByItsButton(@TempDir Path folder) throws Exception {
        Tags left;
        boolean[] focused = new boolean[1];
        try (WindowForTests window = windowOver(folder, stated(THREE))) {
            window.act(root -> WindowForTests.node(root, ID + ".remove-2", Button.class).fire());
            left = window.read(AListValueIsTagsTest::tagsOf);
            focused[0] = window.read(root -> box(root).isFocused());
        }

        claim("the second tag is gone and the others stand in their order, numbered again from 1",
                () -> {
                    assertThat(left.items()).containsExactly(".java", ".kt");
                    assertThat(left.ids()).containsExactly(ID + ".tag-1", ID + ".tag-2");
                });
        claim("the box holds the focus", () -> assertThat(focused[0]).isTrue());
    }

    @Test
    @Story("A list value is tags")
    @DisplayName("Backspace in the empty box removes the last tag, and in a box holding text deletes a character alone")
    void backspaceInTheEmptyBoxRemovesTheLastTag(@TempDir Path folder) throws Exception {
        Tags afterTheEmptyBox;
        Tags afterTheTypedBox;
        String[] box = new String[1];
        try (WindowForTests window = windowOver(folder, stated(THREE))) {
            window.act(root -> {
                TextField field = box(root);
                field.requestFocus();
                press(field, KeyCode.BACK_SPACE);
            });
            afterTheEmptyBox = window.read(AListValueIsTagsTest::tagsOf);
            window.act(root -> {
                TextField field = box(root);
                field.setText(".p");
                field.positionCaret(field.getLength());
                press(field, KeyCode.BACK_SPACE);
            });
            afterTheTypedBox = window.read(AListValueIsTagsTest::tagsOf);
            box[0] = window.read(root -> box(root).getText());
        }

        claim("Backspace in the empty box removes the last tag",
                () -> assertThat(afterTheEmptyBox.items()).containsExactly(".java", ".sql"));
        claim("Backspace in the box holding .p removes no tag and leaves the box reading .",
                () -> {
                    assertThat(afterTheTypedBox.items()).containsExactly(".java", ".sql");
                    assertThat(box[0]).isEqualTo(".");
                });
    }

    @Test
    @Story("A list value is tags")
    @DisplayName("Each remove button is reached by Tab, in the tags' order before the box, and Space removes its tag")
    void eachRemoveButtonIsReachedByTabAndRemovesItsTagFromTheKeyboard(@TempDir Path folder) throws Exception {
        List<String> reachedByTab;
        Tags left;
        try (WindowForTests window = windowOver(folder, stated(THREE))) {
            reachedByTab = window.read(AListValueIsTagsTest::reachedByTab);
            window.act(root -> {
                Button remove = WindowForTests.node(root, ID + ".remove-1", Button.class);
                remove.requestFocus();
                Event.fireEvent(remove, key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
                Event.fireEvent(remove, key(KeyEvent.KEY_RELEASED, KeyCode.SPACE));
            });
            left = window.read(AListValueIsTagsTest::tagsOf);
        }

        claim("what the list holds that Tab reaches is each remove button in the tags' order, then the box",
                () -> assertThat(reachedByTab).containsExactly(ID + ".remove-1", ID + ".remove-2", ID + ".remove-3",
                        ID + ".add"));
        claim("Space on the first remove button, focused, removes the first tag",
                () -> assertThat(left.items()).containsExactly(".sql", ".kt"));
    }

    @Test
    @Story("A list value is tags")
    @DisplayName("A Save writes the tags joined by one space and what is still typed in the box; a list left alone writes nothing")
    void aSaveWritesTheTagsJoinedByOneSpaceAndWhatIsStillTyped(@TempDir Path folder) throws Exception {
        String changed;
        try (WindowForTests window = windowOver(folder, stated(".java .sql"))) {
            window.act(root -> {
                box(root).setText(".kt");
                WindowForTests.node(root, "profile-save", Button.class).fire();
            });
            changed = Files.readString(folder.resolve("working").resolve(ProfileEdit.FILE_NAME), StandardCharsets.UTF_8);
        }
        Path second = Files.createDirectories(folder.resolve("left-alone"));
        String leftAlone = stated(THREE);
        String[] outcome = new String[1];
        String afterwards;
        try (WindowForTests window = windowOver(second, leftAlone)) {
            window.act(root -> WindowForTests.node(root, "profile-save", Button.class).fire());
            outcome[0] = window.read(root -> textOf(WindowForTests.node(root, "profile-outcome", Node.class)));
            afterwards = Files.readString(second.resolve("working").resolve(ProfileEdit.FILE_NAME),
                    StandardCharsets.UTF_8);
        }
        ProfileEdit.Stated saved = ProfileEdit.read(changed).get(ENDINGS);

        claim("profile.yaml states the tags and the piece still typed, joined by one space, set in the control panel",
                () -> {
                    assertThat(saved.value()).isEqualTo(".java .sql .kt");
                    assertThat(saved.provenance()).isEqualTo(ProfileEdit.FILLED_IN_PROVENANCE);
                });
        claim("a list left as the file states it writes nothing, and the Save says so",
                () -> {
                    assertThat(afterwards).isEqualTo(leftAlone);
                    assertThat(outcome[0]).isEqualTo("nothing was written: every value is as profile.yaml states it");
                });
    }

    @Test
    @Story("A list value is tags")
    @DisplayName("The three list values are tags, each box prompting with its key's first example, and the list field and its + are gone")
    void theThreeListValuesAreTagsAndTheListBoxAndThePlusAreGone(@TempDir Path folder) throws Exception {
        Map<ProfileKey, String> prompts = new LinkedHashMap<>();
        List<String> notAFlow = new ArrayList<>();
        List<String> plusFound = new ArrayList<>();
        Map<ProfileKey, String> expected = new LinkedHashMap<>();
        expected.put(ProfileKey.CODE_NAME_ENDINGS, "e.g. .java, then Enter");
        expected.put(ProfileKey.OUT_OF_SCOPE_NAME_ENDINGS, "e.g. .ok, then Enter");
        expected.put(ProfileKey.PREFERRED_LANGUAGES, "e.g. it, then Enter");
        try (WindowForTests window = windowOver(folder, "")) {
            window.act(root -> {
                for (ProfileKey key : expected.keySet()) {
                    String id = "profile-" + key.key();
                    if (!(WindowForTests.node(root, id, Node.class) instanceof FlowPane)) {
                        notAFlow.add(id);
                    }
                    prompts.put(key, WindowForTests.node(root, id + ".add", TextField.class).getPromptText());
                    HeadlessToolkit.nodeWithId(root, id + ".plus").ifPresent(found -> plusFound.add(id + ".plus"));
                }
            });
        }

        claim("each of the three lists is a flow of tags", () -> assertThat(notAFlow).isEmpty());
        claim("each box prompts with the first piece of its key's example, then Enter",
                () -> assertThat(prompts).containsExactlyEntriesOf(expected));
        claim("no list has a + button", () -> assertThat(plusFound).isEmpty());
    }

    // ---- the window and its tags

    /** The window over a working directory holding {@code profile} as its profile.yaml, the Profile tab selected. */
    private static WindowForTests windowOver(Path folder, String profile) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        if (!profile.isEmpty()) {
            Files.writeString(working.resolve(ProfileEdit.FILE_NAME), profile, StandardCharsets.UTF_8);
        }
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(corpus.toString(), working.toString(), "", "", "", "", "", ""));
        WindowForTests window = WindowForTests.shown(state);
        try {
            window.act(root -> {
                Tab profileTab = HeadlessToolkit.tabWithId(root, "profile").orElseThrow();
                profileTab.getTabPane().getSelectionModel().select(profileTab);
            });
            return window;
        } catch (Exception | Error failed) {
            window.close();
            throw failed;
        }
    }

    /** A profile.yaml stating {@code value} for the list, with a provenance the operator wrote. */
    private static String stated(String value) {
        return ProfileEdit.rewrite(Optional.empty(), ENDINGS, value, HAND_WRITTEN);
    }

    private static TextField box(Parent root) {
        return WindowForTests.node(root, ID + ".add", TextField.class);
    }

    /** What the list's tags are: their ids, their items, and whether each item is a one-line selectable text. */
    private record Tags(List<String> ids, List<String> items, boolean itemsAreLines) {
    }

    private static Tags tagsOf(Parent root) {
        List<String> ids = new ArrayList<>();
        List<String> items = new ArrayList<>();
        boolean lines = true;
        for (Node child : WindowForTests.node(root, ID, Pane.class).getChildrenUnmodifiable()) {
            if (child instanceof HBox tag && tag.getStyleClass().contains("tag")) {
                ids.add(tag.getId());
                Node item = tag.getChildrenUnmodifiable().getFirst();
                lines &= item instanceof SelectableText.Line;
                items.add(((TextInputControl) item).getText());
            }
        }
        return new Tags(ids, items, lines);
    }

    /** The ids of what Tab reaches in the list, in the order it reaches them. */
    private static List<String> reachedByTab(Parent root) {
        List<String> reached = new ArrayList<>();
        for (Node child : WindowForTests.node(root, ID, Pane.class).getChildrenUnmodifiable()) {
            List<Node> within = child instanceof HBox tag ? tag.getChildrenUnmodifiable() : List.of(child);
            within.stream().filter(Node::isFocusTraversable).forEach(node -> reached.add(node.getId()));
        }
        return reached;
    }

    private static void press(Node node, KeyCode code) {
        Event.fireEvent(node, key(KeyEvent.KEY_PRESSED, code));
        Event.fireEvent(node, key(KeyEvent.KEY_RELEASED, code));
    }

    private static KeyEvent key(javafx.event.EventType<KeyEvent> type, KeyCode code) {
        return new KeyEvent(type, "", "", code, false, false, false, false);
    }

    private static String textOf(Node node) {
        return node instanceof TextInputControl input ? input.getText()
                : node instanceof javafx.scene.control.Labeled labeled ? labeled.getText() : null;
    }
}
