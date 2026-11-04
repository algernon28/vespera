package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.buttonNames;
import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.controlNames;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The profile page is one form of every value of {@code profile.yaml}, with one Save and one Reset (ADR-334 §7): each
 * key's controls are named by the key, filled from what the file states, a key the next page asks for among them; a
 * save writes every value changed in one write, or none where one is refused, holding what was typed; and Reset puts
 * the form back to the file as last saved, writing nothing and taking no lock.
 *
 * <p>The keys are {@link ProfileKey}'s, iterated, so the test holds of a key added to the profile.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
class TheProfileIsOneFormScreenTest {

    /** The one form of the page. */
    private static final String FORM = "profile";

    /** A profile in the jar's form, of a floor and an attempt, each with its provenance. */
    private static final String TWO_KEYS = """
            ---
            relevanceScoreFloor:
              value: "0.4"
              provenance: "read off the sample of sixty"
              measurement: null
            extractionAttempt:
              value: "2"
              provenance: "a second attempt"
              measurement: null
            """;

    /** The words a key is refused with where its value moved and its provenance did not, as ADR-330 §5 gives them. */
    private static final String THE_OLD_REASON = "its value changed and its provenance did not";

    @TempDir
    Path folder;

    @Test
    @Story("The operator edits the profile")
    @DisplayName("The profile page is one form holding every value, in the profile's order, each as the file states it, with Save and Reset")
    void theProfileIsOneFormOfEveryValueWithSaveAndReset() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, TWO_KEYS);
        List<String> expected = new ArrayList<>();
        for (String key : keys()) {
            expected.addAll(List.of(key + ".drawn", key, key + ".provenance"));
        }
        try (ScreenServer screens = start(work)) {
            Page page = screens.browser().go("/profile").page();
            Element profile = form(page, FORM);

            claim("the drawer's run settings hold one form, the profile's",
                    () -> assertThat(byId(page, "values-profile").select("form").eachAttr("name")).containsExactly(FORM));
            claim("it holds, for every value in the profile's order, what the page drew it from, the value and its"
                            + " provenance, each named by the value",
                    () -> assertThat(controlNames(profile)).containsExactlyElementsOf(expected));
            claim("its buttons are Save and Reset",
                    () -> assertThat(buttonNames(profile)).containsExactly("save", "reset"));
            claim("and each value and provenance the file states stands in its control",
                    () -> assertThat(List.of(control(page, "relevanceScoreFloor").val(),
                                    control(page, "relevanceScoreFloor.provenance").val(),
                                    control(page, "extractionAttempt").val()))
                            .containsExactly("0.4", "read off the sample of sixty", "2"));
        }
    }

    @Test
    @Story("The operator edits the profile")
    @DisplayName("A value the next run asks for on the next page stands in the profile's form too")
    void aValueTheNextInvocationWantsStandsInTheFormToo() throws IOException, InterruptedException {
        Path work = workingDirectory();
        Files.writeString(work.resolve(NextInvocation.FILE_NAME), """
                invocation=run
                wants=seedFolder boilerplateDocumentFrequencyFloor
                answer=false
                """, UTF_8);
        ScreenServer.childWill(work, "exit 0 the closing line");
        try (ScreenServer screens = start(work)) {
            Page page = screens.afterARunHasEnded().go("/profile").page();

            claim("the form holds every value, the two the next run wants among them",
                    () -> assertThat(controlNames(form(page, FORM)))
                            .contains("seedFolder", "boilerplateDocumentFrequencyFloor")
                            .containsAll(keys()));
        }
    }

    @Test
    @Story("The operator edits the profile")
    @DisplayName("Save writes every value changed, each with its reason, and leaves every other byte of the file")
    void aSaveWritesEveryValueChangedAndNothingElse() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, TWO_KEYS);
        try (ScreenServer screens = start(work)) {
            Page landed = screens.browser().go("/profile").press(FORM, "save", Map.of(
                    "relevanceScoreFloor", "0.5", "relevanceScoreFloor.provenance", "read off the second sample",
                    "extractionAttempt", "3", "extractionAttempt.provenance", "a third attempt")).page();

            claim("both are written, and every other byte is as it was",
                    () -> assertThat(profileText(work)).isEqualTo(TWO_KEYS
                            .replace("value: \"0.4\"", "value: \"0.5\"")
                            .replace("read off the sample of sixty", "read off the second sample")
                            .replace("value: \"2\"", "value: \"3\"")
                            .replace("a second attempt", "a third attempt")));
            claim("and the save lands on the profile page, at the first value written",
                    () -> assertThat(List.of(landed.address().getPath(), String.valueOf(landed.address().getQuery()),
                                    String.valueOf(landed.address().getFragment())))
                            .containsExactly("/", "open=values-profile", "key-relevanceScoreFloor"));
        }
    }

    @Test
    @Story("The operator edits the profile")
    @DisplayName("A save with one value refused writes none of them, says which, and keeps everything typed in the form")
    void aSaveWithOneValueRefusedWritesNoneAndKeepsWhatWasTyped() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, TWO_KEYS);
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            Page refused = browser.press(FORM, "save", Map.of(
                    "relevanceScoreFloor", "0.5",
                    "extractionAttempt", "3", "extractionAttempt.provenance", "a third attempt")).page();

            claim("the floor, changed under its old provenance, is named as not saved, with the reason",
                    () -> assertThat(byId(refused, "outcome").text())
                            .contains("relevanceScoreFloor is not saved", THE_OLD_REASON));
            claim("the attempt, though it could have been written, is not: the file is as it was",
                    () -> assertThat(profileText(work)).isEqualTo(TWO_KEYS));
            claim("and both values typed stand in the form, so nothing typed is lost",
                    () -> assertThat(List.of(control(refused, "relevanceScoreFloor").val(),
                                    control(refused, "extractionAttempt").val(),
                                    control(refused, "extractionAttempt.provenance").val()))
                            .containsExactly("0.5", "3", "a third attempt"));
        }
    }

    @Test
    @Story("The operator edits the profile")
    @DisplayName("Reset puts the whole form back to the file as last saved, writes nothing and takes no lock")
    void resetPutsTheFormBackToTheFileAsLastSaved() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, TWO_KEYS);
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            browser.press(FORM, "save", Map.of("relevanceScoreFloor", "0.5", "extractionAttempt", "3"));

            Page reset = browser.press(FORM, "reset", Map.of()).page();

            claim("Reset lands on the run settings of the home page's drawer",
                    () -> assertThat(List.of(reset.address().getPath(), String.valueOf(reset.address().getQuery())))
                            .containsExactly("/", "open=values-profile"));
            claim("every control holds what the file states, and nothing that was typed",
                    () -> assertThat(List.of(control(reset, "relevanceScoreFloor").val(),
                                    control(reset, "relevanceScoreFloor.provenance").val(),
                                    control(reset, "extractionAttempt").val()))
                            .containsExactly("0.4", "read off the sample of sixty", "2"));
            claim("and so does the page drawn after it: what was held is let go",
                    () -> assertThat(control(browser.go("/profile").page(), "extractionAttempt").val()).isEqualTo("2"));
            claim("the file is as it was",
                    () -> assertThat(profileText(work)).isEqualTo(TWO_KEYS));
            claim("and no lock was taken, not even its holder line written",
                    () -> assertThat(work.resolve("vespera.lock")).doesNotExist());
        }
    }

    @Test
    @Story("The operator edits the profile")
    @DisplayName("A language the file does not state is a choice of none, chosen, so a save of other values leaves it unset")
    void anUnstatedLanguageIsLeftUnsetByASaveOfOtherValues() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, TWO_KEYS);
        String language = ProfileKey.DELIVERABLE_LANGUAGE.key();
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            Element select = form(browser.page(), FORM).selectFirst("select[name=" + language + "]");

            claim("the language's choices begin with none, which is the one chosen",
                    () -> assertThat(List.of(select.select("option").eachAttr("value"),
                                    select.select("option[selected]").eachAttr("value")))
                            .containsExactly(List.of("", "en", "it"), List.of("")));

            browser.press(FORM, "save", Map.of("extractionAttempt", "3", "extractionAttempt.provenance", "a third attempt"));

            claim("and a save of another value writes it and leaves the language out of the file",
                    () -> assertThat(profileText(work)).contains("a third attempt").doesNotContain(language));
        }
    }

    /**
     * The choice of none is offered only where the file states no language (ADR-334 §7): where it states one, a save of
     * the form keeps a language, as the file does.
     */
    @Test
    @Story("The operator edits the profile")
    @DisplayName("A language the file states is offered without the choice of none, the one stated chosen")
    void aStatedLanguageIsOfferedWithoutTheChoiceOfNone() throws IOException {
        Path work = workingDirectory();
        String language = ProfileKey.DELIVERABLE_LANGUAGE.key();
        theProfileIs(work, TWO_KEYS + language + """
                :
                  value: "en"
                  provenance: "the readers read English"
                  measurement: null
                """);
        try (ScreenServer screens = start(work)) {
            Element select = form(screens.browser().go("/profile").page(), FORM)
                    .selectFirst("select[name=" + language + "]");

            claim("the language's choices are the languages alone, and the one the file states is chosen",
                    () -> assertThat(List.of(select.select("option").eachAttr("value"),
                                    select.select("option[selected]").eachAttr("value")))
                            .containsExactly(List.of("en", "it"), List.of("en")));
        }
    }

    /** The one control of the profile's form named {@code name}; a test fails naming it where there is not one. */
    private static Element control(Page page, String name) {
        List<Element> named = form(page, FORM).select("input, select, textarea").stream()
                .filter(each -> each.attr("name").equals(name))
                .toList();
        if (named.size() != 1) {
            throw new AssertionError("the form '" + FORM + "' holds " + named.size() + " controls named '" + name + "'");
        }
        return named.getFirst();
    }

    private static List<String> keys() {
        return Arrays.stream(ProfileKey.values()).map(ProfileKey::key).toList();
    }

    private static void theProfileIs(Path workingDirectory, String text) throws IOException {
        Files.writeString(workingDirectory.resolve(ProfileEdit.FILE_NAME), text, UTF_8);
    }

    private static String profileText(Path workingDirectory) {
        try {
            return Files.readString(workingDirectory.resolve(ProfileEdit.FILE_NAME), UTF_8);
        } catch (IOException noFile) {
            return "";
        }
    }

    private Path workingDirectory() throws IOException {
        return Files.createDirectories(folder.resolve("work"));
    }

    private ScreenServer start(Path workingDirectory) throws IOException {
        return ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), workingDirectory));
    }
}
