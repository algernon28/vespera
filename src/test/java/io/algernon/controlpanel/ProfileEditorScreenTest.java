package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.buttonNames;
import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel as the complete editor of {@code profile.yaml} (ADR-330): {@code /profile} holds every key in one
 * form, each with the value in use and where it came from, what the file states, a control of the key's type, a line
 * saying what the key is, and the measurement's pointer; a save writes only the keys that changed, and none where one
 * is refused; a file not in the jar's form is shown, and repaired where the repair can lose nothing; and a working
 * directory that does not exist is made by the first save.
 *
 * <p>Driven through the pages as a browser reads them (ADR-297). What the jar says of a value is the file ADR-330 §1
 * gives, written here by hand as {@code NextScreenTest} writes {@code next-invocation.properties}; that the jar writes
 * it is {@code pipeline.TheJarWritesItsEffectiveValuesInvocationTest}'s. Each test has a folder of its own, so no
 * profile leaks between them. What a browser does with a control's type, its {@code step}, {@code min} and
 * {@code list}, is no test's (ADR-297's Consequences); the attributes and the server's checks are.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("ADR-330: awaits /profile's one form, its typed controls and values in use, the repair, and the folder a save makes")
class ProfileEditorScreenTest {

    /** The file the jar writes for the control panel, in the working directory. */
    private static final String EFFECTIVE_VALUES = "effective-values.properties";

    /** The embedding model the jar says it ships. */
    private static final String SHIPPED_MODEL = "bge-m3:latest";

    /** The generation model the jar says it ships. */
    private static final String SHIPPED_WRITING_MODEL = "qwen3:8b";

    /** A value the jar reads as nothing, written with a decimal comma, which a number control would blank. */
    private static final String A_DECIMAL_COMMA = "0,4";

    /** The name given to a file by a repair begins with this. */
    private static final String A_REPAIRS_COPY = "profile.yaml.before-repair-";

    /** The new key, which {@code ProfileKey} does not name yet. */
    private static final String CLUSTER_RESOLUTION = "clusterResolution";

    /** A profile in the form the jar writes it, of a floor with a provenance and an attempt with one. */
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

    /**
     * A profile written by hand, which the jar reads and the control panel does not: a comment on line 1, one after the
     * floor's value on line 7, and the floor's provenance on lines 8 and 9, plain, over two lines.
     */
    private static final String NOT_IN_FORM_BUT_CERTAIN = """
            # the floor came from the sample of October
            seedFolder:
              value: null
              provenance: null
              measurement: null
            relevanceScoreFloor:
              value: 0.4 # guessed, then checked
              provenance: read off the page
                of the labels
              measurement:
                source: "relevance-labelling.html"
                refreshedAt: "2026-10-19T10:00:00Z"
            """;

    /** {@link #NOT_IN_FORM_BUT_CERTAIN} once repaired: the two comments gone, the two scalars on a line each. */
    private static final String THE_SAME_REPAIRED = """
            seedFolder:
              value: null
              provenance: null
              measurement: null
            relevanceScoreFloor:
              value: "0.4"
              provenance: "read off the page of the labels"
              measurement:
                source: "relevance-labelling.html"
                refreshedAt: "2026-10-19T10:00:00Z"
            """;

    /** The lines of {@link #NOT_IN_FORM_BUT_CERTAIN} the repair changes: a comment, a comment after a value, two lines. */
    private static final List<String> THE_LINES_IT_CHANGES = List.of("1", "7", "8");

    @TempDir
    Path folder;

    // ---- the value in use

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("A value shows the one in use and its source: the profile's where it names one, the jar's where it does not, and none where nothing does")
    void theValueInUseAndWhereItCameFromAreShown() throws IOException {
        Path work = workingDirectory();
        theJarSaid(work, Map.of("embeddingModel", SHIPPED_MODEL, "embeddingModel.source", "shipped",
                "generationModel", SHIPPED_WRITING_MODEL, "generationModel.source", "shipped",
                "generationContextWindow", "8192", "generationContextWindow.source", "code"));
        theProfileIs(work, """
                ---
                generationModel:
                  value: "a-named-writing-model:8b"
                  provenance: "the operator's own"
                  measurement: null
                """);
        try (ScreenServer screens = start(work)) {
            Page page = screens.browser().go("/profile").page();

            claim("the embedding model the profile leaves unset is the one the jar ships, and says so",
                    () -> assertThat(inUse(page, "embeddingModel")).containsExactly(SHIPPED_MODEL, "shipped"));
            claim("the generation model the profile names is the profile's, whatever the jar ships",
                    () -> assertThat(inUse(page, "generationModel"))
                            .containsExactly("a-named-writing-model:8b", "profile"));
            claim("a number the code carries says it comes from the code",
                    () -> assertThat(inUse(page, "generationContextWindow")).containsExactly("8192", "code"));
            claim("and a floor nothing names is unset, with no value in use",
                    () -> assertThat(inUse(page, "relevanceScoreFloor")).containsExactly("", "unset"));
        }
    }

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("With no file from the jar a value that could have a default is not known, and a file older than the jar beside the control panel is marked stale")
    void anAbsentFileAndAStaleFileAreSaidForWhatTheyAre() throws IOException {
        Path work = workingDirectory();
        try (ScreenServer screens = start(work)) {
            Page none = screens.browser().go("/profile").page();

            claim("before any invocation has said, the embedding model is not known, which is not the same as unset",
                    () -> assertThat(inUse(none, "embeddingModel")).containsExactly("", "not-known"));
            claim("and a floor, which has no default, is unset whatever the jar would say",
                    () -> assertThat(inUse(none, "relevanceScoreFloor")).containsExactly("", "unset"));

            theJarSaid(work, Map.of("embeddingModel", SHIPPED_MODEL, "embeddingModel.source", "shipped"));
            Page fresh = screens.browser().go("/profile").page();
            claim("a file written after the jar beside the control panel is fresh",
                    () -> assertThat(byId(fresh, "in-use-embeddingModel").attr("data-fresh")).isEqualTo("fresh"));

            Files.setLastModifiedTime(work.resolve(EFFECTIVE_VALUES),
                    FileTime.fromMillis(Files.getLastModifiedTime(ScreenServer.runtime().jar()).toMillis() - 60_000));
            Page stale = screens.browser().go("/profile").page();
            claim("a file older than that jar is still shown, marked stale, so the value is not lost and not trusted",
                    () -> assertThat(List.of(byId(stale, "in-use-embeddingModel").attr("data-value"),
                                    byId(stale, "in-use-embeddingModel").attr("data-fresh")))
                            .containsExactly(SHIPPED_MODEL, "stale"));
        }
    }

    // ---- the fields hold what the file states

    @Test
    @Story("The operator edits what the file states")
    @DisplayName("Each field holds the value and provenance the file states, and a value the jar ships stays out of the field, offered only as its placeholder")
    void theFieldsHoldTheFilesOwnWordsAndADefaultStaysOutOfThem() throws IOException {
        Path work = workingDirectory();
        theJarSaid(work, Map.of("embeddingModel", SHIPPED_MODEL, "embeddingModel.source", "shipped"));
        theProfileIs(work, TWO_KEYS);
        try (ScreenServer screens = start(work)) {
            Element profile = form(screens.browser().go("/profile").page(), "profile");

            claim("the floor's field and its provenance hold what the file states",
                    () -> assertThat(List.of(control(profile, "relevanceScoreFloor").val(),
                                    control(profile, "relevanceScoreFloor.provenance").val()))
                            .containsExactly("0.4", "read off the sample of sixty"));
            claim("the embedding model's field is empty, because pressing Save must not write into the profile a value"
                            + " nobody chose, and what the jar ships is its placeholder",
                    () -> assertThat(List.of(control(profile, "embeddingModel").val(),
                                    control(profile, "embeddingModel").attr("placeholder"),
                                    control(profile, "embeddingModel.provenance").val()))
                            .containsExactly("", SHIPPED_MODEL, ""));
        }
    }

    @Test
    @Story("The operator edits what the file states")
    @DisplayName("The next act's fields hold what the file states, and the arrangement to approve takes the place of an earlier one")
    void theNextActsFieldsHoldTheFilesOwnWordsAndTheArrangementToApproveWins() throws IOException, InterruptedException {
        Path work = workingDirectory();
        String arrangement = "0123456789ab";
        Files.writeString(work.resolve(NextInvocation.FILE_NAME), """
                invocation=run
                wants=arrangementApproved
                answer=false
                arrangement=%s
                """.formatted(arrangement), UTF_8);
        ScreenServer.childWill(work, "exit 0 the closing line");
        theProfileIs(work, """
                ---
                arrangementApproved:
                  value: "ba9876543210"
                  provenance: "read the page on Monday"
                  measurement: null
                """);
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.afterARunHasEnded().go("/next");
            Element next = form(browser.page(), "next");

            claim("the value is the arrangement this invocation arrived at, and the provenance the file's own",
                    () -> assertThat(List.of(control(next, "arrangementApproved").val(),
                                    control(next, "arrangementApproved.provenance").val()))
                            .containsExactly(arrangement, "read the page on Monday"));

            String before = profileText(work);
            browser.press("next", "save", Map.of());
            claim("saving it unchanged is refused and writes nothing: the provenance speaks of another value",
                    () -> assertThat(profileText(work)).isEqualTo(before));
        }
    }

    // ---- typed controls

    @Test
    @Story("The operator edits each value in a control of its type")
    @DisplayName("Each value's control is of the type the jar reads it as: numbers by their range, lists a line to each piece, models from a picker, the language from its choices")
    void eachKeysControlIsTypedAsTheJarReadsIt() throws IOException {
        Path work = workingDirectory();
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("seedFolder", "input text");
        expected.put("degenerateOutputConfidenceFloor", "input number step=any min=");
        expected.put("degenerateOutputWordFloor", "input number step=1 min=0");
        expected.put("boilerplateDocumentFrequencyFloor", "input number step=any min=");
        expected.put("embeddingModel", "input text list=models-served");
        expected.put("relevanceScoreFloor", "input number step=any min=");
        expected.put("arrangementApproved", "input text");
        expected.put("generationModel", "input text list=models-served");
        expected.put("generationContextWindow", "input number step=1 min=1282");
        expected.put("clusterPageByteCeiling", "input number step=1 min=1");
        expected.put("logTimestampShareFloor", "input number step=any min=");
        expected.put("extractionAttempt", "input number step=1 min=1");
        expected.put("codeNameEndings", "textarea");
        expected.put("outOfScopeNameEndings", "textarea");
        expected.put("translationSimilarityFloor", "input number step=any min=");
        expected.put("preferredLanguages", "textarea");
        expected.put("configurationSettingShareFloor", "input number step=any min=");
        expected.put("deliverableLanguage", "select ,en,it");
        expected.put(CLUSTER_RESOLUTION, "input number step=any min=");
        try (ScreenServer screens = start(work)) {
            Element profile = form(screens.browser().go("/profile").page(), "profile");
            Map<String, String> typed = new LinkedHashMap<>();
            expected.keySet().forEach(key -> typed.put(key, described(control(profile, key))));

            claim("each of the values has the control its type asks for, a floor with no range because the jar reads a"
                            + " floor past its scale, and a whole number with the range the jar reads",
                    () -> assertThat(typed).containsExactlyEntriesOf(expected));
        }
    }

    @Test
    @Story("The operator edits each value in a control of its type")
    @DisplayName("A value a typed control could not hold is shown as written in one that can, and saving leaves it as it was")
    void aStatedValueATypedControlCouldNotHoldStaysInAControlThatHoldsIt() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, """
                ---
                relevanceScoreFloor:
                  value: "%s"
                  provenance: "typed in a hurry"
                  measurement: null
                deliverableLanguage:
                  value: "fr"
                  provenance: "for the Paris office"
                  measurement: null
                """.formatted(A_DECIMAL_COMMA));
        String before = profileText(work);
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            Page page = browser.page();
            Element profile = form(page, "profile");

            claim("the decimal comma stands in a text control, where a browser keeps it, and not a number control,"
                            + " where a browser would blank it and a save would unset it",
                    () -> assertThat(List.of(control(profile, "relevanceScoreFloor").attr("type"),
                                    control(profile, "relevanceScoreFloor").val()))
                            .containsExactly("text", A_DECIMAL_COMMA));
            claim("the page says the jar cannot read it",
                    () -> assertThat(inUse(page, "relevanceScoreFloor")).containsExactly(A_DECIMAL_COMMA, "unreadable"));
            claim("a language the jar does not read is one of the choices, selected, so it is not lost either",
                    () -> assertThat(control(profile, "deliverableLanguage").select("option[selected]").eachAttr("value"))
                            .containsExactly("fr"));

            browser.press("profile", "save", Map.of());
            claim("and saving with both untouched leaves the file as it was: a bad value is the operator's to fix",
                    () -> assertThat(profileText(work)).isEqualTo(before));
        }
    }

    @Test
    @Story("The operator edits each value in a control of its type")
    @DisplayName("A list is edited one piece to a line and written as one line, and the same pieces however they were separated are no change")
    void aListIsEditedOnePieceToALineAndWrittenAsOneLine() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, """
                ---
                outOfScopeNameEndings:
                  value: ".P3A .P3L"
                  provenance: "payment terminal packages"
                  measurement: null
                preferredLanguages:
                  value: "it, en"
                  provenance: "Italian first"
                  measurement: null
                """);
        String before = profileText(work);
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            Element profile = form(browser.page(), "profile");

            claim("each piece stands on a line of its own, whichever way the file separates them",
                    () -> assertThat(List.of(control(profile, "outOfScopeNameEndings").wholeText().strip(),
                                    control(profile, "preferredLanguages").wholeText().strip()))
                            .containsExactly(".P3A\n.P3L", "it\nen"));

            browser.press("profile", "save", Map.of());
            claim("sent back as lines, the same pieces are no change, so a comma in the file is not rewritten",
                    () -> assertThat(profileText(work)).isEqualTo(before));

            browser.go("/profile").press("profile", "save", Map.of(
                    "outOfScopeNameEndings", ".P3A\n.P3L\n.DGN",
                    "outOfScopeNameEndings.provenance", "and the signature files"));
            claim("a piece added is written with the others on the one line the jar reads",
                    () -> assertThat(ProfileEdit.read(profileText(work)).get(ProfileKey.OUT_OF_SCOPE_NAME_ENDINGS))
                            .isEqualTo(new ProfileEdit.Stated(".P3A .P3L .DGN", "and the signature files")));
        }
    }

    @Test
    @Story("The operator picks a model")
    @DisplayName("The model picker offers what the model service serves, asked at its launch address or, with none, at the one the jar last used")
    void theModelPickerOffersWhatOllamaServes() throws IOException {
        Path work = workingDirectory();
        HttpServer ollama = aModelServiceServing("bge-m3:latest", "qwen3:8b");
        try {
            String address = "http://127.0.0.1:" + ollama.getAddress().getPort();
            LaunchValues named = new LaunchValues(corpus().toString(), work.toString(), "", "", "", address, "", "");
            try (ScreenServer screens = ScreenServer.start(folder, named)) {
                Page page = screens.browser().go("/profile").page();
                claim("the models served are offered, in the order the service answered",
                        () -> assertThat(byId(page, "models-served").select("option").eachAttr("value"))
                                .containsExactly("bge-m3:latest", "qwen3:8b"));
                claim("and the service is said to have answered",
                        () -> assertThat(byId(page, "models-served").attr("data-check")).isEqualTo("answered"));
            }
            theJarSaid(work, Map.of("spring.ai.ollama.base-url", address, "spring.ai.ollama.base-url.source", "shipped"));
            try (ScreenServer screens = start(work)) {
                Page page = screens.browser().go("/profile").page();
                claim("with no address on the launch page, the service is asked at the address the jar last used",
                        () -> assertThat(byId(page, "models-served").select("option").eachAttr("value"))
                                .containsExactly("bge-m3:latest", "qwen3:8b"));
            }
        } finally {
            ollama.stop(0);
        }
    }

    @Test
    @Story("The operator picks a model")
    @DisplayName("With the model service silent the picker offers nothing, says so, and a name typed by hand is still saved")
    void withOllamaSilentThePickerIsEmptyAndAModelIsStillSaved() throws IOException {
        Path work = workingDirectory();
        String nobody = "http://127.0.0.1:" + aPortNobodyListensOn();
        LaunchValues values = new LaunchValues(corpus().toString(), work.toString(), "", "", "", nobody, "", "");
        try (ScreenServer screens = ScreenServer.start(folder, values)) {
            PanelBrowser browser = screens.browser().go("/profile");
            Page page = browser.page();

            claim("the page is answered, its picker empty and saying the service did not answer",
                    () -> assertThat(List.of(page.status(), byId(page, "models-served").attr("data-check"),
                                    byId(page, "models-served").select("option").size()))
                            .containsExactly(200, "not-answering", 0));

            browser.press("profile", "save", Map.of("embeddingModel", "a-model-pulled-later:1b",
                    "embeddingModel.provenance", "the bake-off's winner"));
            claim("and the model typed by hand is saved all the same",
                    () -> assertThat(ProfileEdit.read(profileText(work)).get(ProfileKey.EMBEDDING_MODEL))
                            .isEqualTo(new ProfileEdit.Stated("a-model-pulled-later:1b", "the bake-off's winner")));
        }
    }

    // ---- what a key is, and where its measurement lies

    @Test
    @Story("The operator is told what each value is")
    @DisplayName("Every value has one line of its own saying what it is")
    void everyKeyHasAOneLineDescriptionOfItsOwn() throws IOException {
        try (ScreenServer screens = start(workingDirectory())) {
            Page page = screens.browser().go("/profile").page();
            List<String> lines = keys().stream().map(key -> byId(page, "about-" + key).wholeText()).toList();

            claim("each line says something, and no two say the same",
                    () -> assertThat(lines).doesNotContain("").doesNotHaveDuplicates());
            claim("and each is one line",
                    () -> assertThat(lines).noneMatch(line -> line.contains("\n")));
        }
    }

    @Test
    @Story("The operator is told what each value is")
    @DisplayName("A value the file gives a measurement for shows where the measurement lies and when it was last looked at, and one with none shows nothing")
    void theMeasurementPointerIsShown() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, """
                ---
                seedFolder:
                  value: null
                  provenance: null
                  measurement:
                    source: "walk 3"
                    refreshedAt: "2026-10-19T10:00:00Z"
                extractionAttempt:
                  value: "2"
                  provenance: "a second attempt"
                  measurement: null
                """);
        try (ScreenServer screens = start(work)) {
            Page page = screens.browser().go("/profile").page();

            claim("the seed folder's measurement is shown as the file holds it",
                    () -> assertThat(List.of(byId(page, "measurement-seedFolder").attr("data-source"),
                                    byId(page, "measurement-seedFolder").attr("data-refreshed-at")))
                            .containsExactly("walk 3", "2026-10-19T10:00:00Z"));
            claim("and a value whose measurement is null shows none",
                    () -> assertThat(page.document().getElementById("measurement-extractionAttempt")).isNull());
        }
    }

    // ---- one page, one save

    @Test
    @Story("The operator saves the whole profile at once")
    @DisplayName("Every value stands in one form with one Save, a value the next run wants among them and marked")
    void everyKeyStandsInOneFormWithOneSave() throws IOException, InterruptedException {
        Path work = workingDirectory();
        Files.writeString(work.resolve(NextInvocation.FILE_NAME), """
                invocation=run
                wants=boilerplateDocumentFrequencyFloor
                answer=false
                """, UTF_8);
        ScreenServer.childWill(work, "exit 0 the closing line");
        try (ScreenServer screens = start(work)) {
            Page page = screens.afterARunHasEnded().go("/profile").page();
            Element profile = form(page, "profile");

            claim("one form, with each value in the profile's order, the one the next run wants still among them",
                    () -> assertThat(profile.select("fieldset").eachAttr("id"))
                            .containsExactlyElementsOf(keys().stream().map(key -> "key-" + key).toList()));
            claim("and its one button saves it",
                    () -> assertThat(buttonNames(profile)).containsExactly("save"));
            claim("the value the next run wants is marked, and no other",
                    () -> assertThat(profile.select("fieldset[data-wanted=true]").eachAttr("id"))
                            .containsExactly("key-boilerplateDocumentFrequencyFloor"));
            claim("and it is posted to the page itself",
                    () -> assertThat(List.of(profile.attr("method").toLowerCase(), profile.attr("action")))
                            .containsExactly("post", "/profile"));
        }
    }

    @Test
    @Story("The operator saves the whole profile at once")
    @DisplayName("Only a value that changed is written: nothing changed takes no lock and writes nothing, and one changed leaves every other byte")
    void onlyAChangedKeyIsWrittenAndNothingChangedWritesNothing() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, TWO_KEYS);
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            Page landed = browser.press("profile", "save", Map.of()).page();

            claim("saving with nothing changed lands on the page and says so",
                    () -> assertThat(List.of(landed.address().getPath(), byId(landed, "outcome").text().isBlank()))
                            .containsExactly("/profile", false));
            claim("and writes nothing, not even the holder line of a lock it had no need of",
                    () -> assertThat(List.of(profileText(work), Files.exists(work.resolve("vespera.lock"))))
                            .containsExactly(TWO_KEYS, false));

            browser.press("profile", "save", Map.of("extractionAttempt", "3",
                    "extractionAttempt.provenance", "a third attempt"));
            claim("saving one changed value writes it, every other byte as it was",
                    () -> assertThat(profileText(work)).isEqualTo(TWO_KEYS
                            .replace("value: \"2\"", "value: \"3\"")
                            .replace("a second attempt", "a third attempt")));
        }
    }

    @Test
    @Story("The operator saves the whole profile at once")
    @DisplayName("One value refused writes no value of the save, says which, and shows what was typed again")
    void aRefusedKeyWritesNoKeyOfTheSaveAndKeepsWhatWasTyped() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, TWO_KEYS);
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            Page landed = browser.press("profile", "save", Map.of(
                    "extractionAttempt", "3",
                    "extractionAttempt.provenance", "a third attempt",
                    "relevanceScoreFloor", "not a number",
                    "relevanceScoreFloor.provenance", "guessed")).page();
            Element shown = form(landed, "profile");

            claim("no value is written, though one of the two was good",
                    () -> assertThat(profileText(work)).isEqualTo(TWO_KEYS));
            claim("the refused value says why, and the good one says nothing",
                    () -> assertThat(List.of(landed.document().select("#refused-relevanceScoreFloor").size(),
                                    landed.document().select("#refused-extractionAttempt").size()))
                            .containsExactly(1, 0));
            claim("what was typed stands in the fields, so nothing typed is lost",
                    () -> assertThat(List.of(control(shown, "extractionAttempt").val(),
                                    control(shown, "extractionAttempt.provenance").val(),
                                    control(shown, "relevanceScoreFloor").val(),
                                    control(shown, "relevanceScoreFloor.provenance").val()))
                            .containsExactly("3", "a third attempt", "not a number", "guessed"));

            Element again = form(browser.go("/profile").page(), "profile");
            claim("once: the next page shows what the file states",
                    () -> assertThat(control(again, "extractionAttempt").val()).isEqualTo("2"));
        }
    }

    @Test
    @Story("The operator saves the whole profile at once")
    @DisplayName("A value changed under the provenance of the old one is refused, and so is one with no provenance at all")
    void aValueChangedUnderTheSameProvenanceIsRefused() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, TWO_KEYS);
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            Page kept = browser.press("profile", "save", Map.of("relevanceScoreFloor", "0.5")).page();

            claim("the floor's old provenance says nothing of the new value, so nothing is written, and the floor says why",
                    () -> assertThat(List.of(profileText(work).equals(TWO_KEYS),
                                    kept.document().select("#refused-relevanceScoreFloor").size()))
                            .containsExactly(true, 1));

            Page none = browser.go("/profile").press("profile", "save", Map.of("clusterPageByteCeiling", "1000000")).page();
            claim("a value the file does not hold, given with no provenance, is refused as well",
                    () -> assertThat(List.of(profileText(work).equals(TWO_KEYS),
                                    none.document().select("#refused-clusterPageByteCeiling").size()))
                            .containsExactly(true, 1));
        }
    }

    @Test
    @Story("The operator saves the whole profile at once")
    @DisplayName("A value the file changed since the page was drawn is not written over, whether it was left alone or typed over")
    void aKeyTheFileChangedSinceThePageWasDrawnIsNotWrittenOver() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, TWO_KEYS);
        String behindThePage = TWO_KEYS.replace("value: \"0.4\"", "value: \"0.6\"")
                .replace("read off the sample of sixty", "set by the rule that loses no documentation");
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            theProfileIs(work, behindThePage);
            browser.press("profile", "save", Map.of("extractionAttempt", "3",
                    "extractionAttempt.provenance", "a third attempt"));

            claim("the floor saved behind the page, which the form still holds as it was, is kept, the other value saved",
                    () -> assertThat(profileText(work)).isEqualTo(behindThePage
                            .replace("value: \"2\"", "value: \"3\"")
                            .replace("a second attempt", "a third attempt")));

            browser.go("/profile");
            String now = profileText(work);
            String behindAgain = now.replace("value: \"0.6\"", "value: \"0.7\"");
            theProfileIs(work, behindAgain);
            Page refused = browser.press("profile", "save", Map.of("relevanceScoreFloor", "0.8",
                    "relevanceScoreFloor.provenance", "my own reading")).page();

            claim("typed over, it is refused, and the file keeps the newer value",
                    () -> assertThat(List.of(profileText(work).equals(behindAgain),
                                    refused.document().select("#refused-relevanceScoreFloor").size()))
                            .containsExactly(true, 1));
        }
    }

    @Test
    @Story("The operator saves the whole profile at once")
    @DisplayName("Another invocation holding the working folder writes nothing, says so, and shows what was typed again")
    void aHeldLockWritesNothingAndKeepsWhatWasTyped() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, TWO_KEYS);
        try (ScreenServer screens = start(work);
                FileChannel channel = FileChannel.open(work.resolve("vespera.lock"), StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap("pid=1 started=2026-10-19T10:00:00+02:00 command=run\n".getBytes(UTF_8)));
            FileLock held = channel.tryLock(Long.MAX_VALUE - 1, 1, false);
            Page landed = screens.browser().go("/profile").press("profile", "save", Map.of("extractionAttempt", "3",
                    "extractionAttempt.provenance", "a third attempt")).page();
            held.release();

            claim("nothing is written, and the page says what stopped it",
                    () -> assertThat(List.of(profileText(work).equals(TWO_KEYS), byId(landed, "outcome").text().isBlank()))
                            .containsExactly(true, false));
            claim("and what was typed stands in the field",
                    () -> assertThat(control(form(landed, "profile"), "extractionAttempt").val()).isEqualTo("3"));
        }
    }

    @Test
    @Story("The operator writes the values the next invocation wants")
    @DisplayName("One value the next run wants refused writes none of them")
    void aRefusedKeyOnTheNextActWritesNoKeyOfIt() throws IOException, InterruptedException {
        Path work = workingDirectory();
        Path seeds = Files.createDirectories(folder.resolve("seeds"));
        Files.writeString(work.resolve(NextInvocation.FILE_NAME), """
                invocation=run
                wants=seedFolder boilerplateDocumentFrequencyFloor
                answer=false
                """, UTF_8);
        ScreenServer.childWill(work, "exit 0 the closing line");
        try (ScreenServer screens = start(work)) {
            screens.afterARunHasEnded().go("/next").press("next", "save", Map.of(
                    "seedFolder", seeds.toString(),
                    "seedFolder.provenance", "the operator's exemplars",
                    "boilerplateDocumentFrequencyFloor", "not a number",
                    "boilerplateDocumentFrequencyFloor.provenance", "guessed"));

            claim("the good value before the refused one is not written, so a save is a save of the form as a whole",
                    () -> assertThat(work.resolve(ProfileEdit.FILE_NAME)).doesNotExist());
        }
    }

    // ---- a file not in the jar's form

    @Test
    @Story("The operator repairs a profile not in the jar's form")
    @DisplayName("A file not in the jar's form shows its line and the repair, which keeps every value and provenance as the jar reads them, and a copy of the file")
    void aFileNotInFormShowsItsLineAndARepairThatLosesNothing() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, NOT_IN_FORM_BUT_CERTAIN);
        Profile asTheJarReadsIt = new ProfileStore(work).load();
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            Page problem = browser.page();

            claim("the page names the first line it cannot read, says the repair can be made, and holds the file's text",
                    () -> assertThat(List.of(byId(problem, "form-problem").attr("data-line"),
                                    byId(problem, "form-problem").attr("data-repairable"),
                                    byId(problem, "profile-text").wholeText()))
                            .containsExactly("1", "true", NOT_IN_FORM_BUT_CERTAIN));
            claim("it lists each line the repair changes, so nothing is changed unseen",
                    () -> assertThat(byId(problem, "repair-changes").select("li").eachAttr("data-line"))
                            .containsExactlyElementsOf(THE_LINES_IT_CHANGES));
            claim("and holds no form of values, which could not be saved",
                    () -> assertThat(problem.document().select("form[name=profile]")).isEmpty());
            claim("nothing was written by showing it",
                    () -> assertThat(profileText(work)).isEqualTo(NOT_IN_FORM_BUT_CERTAIN));

            Page repaired = browser.press("repair", "repair", Map.of()).page();

            claim("the file is in the jar's form, every other byte as it was",
                    () -> assertThat(profileText(work)).isEqualTo(THE_SAME_REPAIRED));
            claim("and the jar reads from it what it read before, every value, provenance and measurement",
                    () -> assertThat(new ProfileStore(work).load()).isEqualTo(asTheJarReadsIt));
            claim("the file as it stood is kept beside it, whole, comments included",
                    () -> assertThat(aRepairsCopies(work)).singleElement()
                            .satisfies(copy -> assertThat(Files.readString(copy, UTF_8)).isEqualTo(NOT_IN_FORM_BUT_CERTAIN)));
            claim("the page now holds the form of values, and no problem",
                    () -> assertThat(List.of(repaired.document().select("form[name=profile]").size(),
                                    repaired.document().select("#form-problem").size()))
                            .containsExactly(1, 0));
        }
    }

    @Test
    @Story("The operator repairs a profile not in the jar's form")
    @DisplayName("What the repair cannot read with certainty is left to the operator: a value under a name it does not know, a value given twice, a mapping on one line")
    void whatTheRepairCannotReadIsLeftToTheOperator() throws IOException {
        Path work = workingDirectory();
        Map<String, String> byTheirFirstLine = new LinkedHashMap<>();
        byTheirFirstLine.put("1", "relevanceFloor:\n  value: \"0.4\"\n  provenance: \"mistyped name\"\n");
        byTheirFirstLine.put("4", "extractionAttempt:\n  value: \"2\"\n  provenance: \"first\"\nextractionAttempt:\n"
                + "  value: \"3\"\n  provenance: \"second\"\n");
        byTheirFirstLine.put("2", "---\nseedFolder: {value: \"/seeds\", provenance: \"on one line\"}\n");
        for (Map.Entry<String, String> sample : byTheirFirstLine.entrySet()) {
            theProfileIs(work, sample.getValue());
            try (ScreenServer screens = start(work)) {
                Page page = screens.browser().go("/profile").page();

                claim("line " + sample.getKey() + " is named, the repair is not offered, and the text is shown for the"
                                + " operator to fix",
                        () -> assertThat(List.of(byId(page, "form-problem").attr("data-line"),
                                        byId(page, "form-problem").attr("data-repairable"),
                                        page.document().select("form[name=repair]").size(),
                                        byId(page, "profile-text").wholeText()))
                                .containsExactly(sample.getKey(), "false", 0, sample.getValue()));
                claim("and the file stands as it was",
                        () -> assertThat(profileText(work)).isEqualTo(sample.getValue()));
            }
        }
    }

    @Test
    @Story("The operator repairs a profile not in the jar's form")
    @DisplayName("A bad value is the operator's to fix: the repair keeps it as written, and the page says the jar cannot read it")
    void aBadValueIsTheOperatorsAndNoRepairTouchesIt() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, """
                # mine
                relevanceScoreFloor:
                  value: "%s"
                  provenance: "typed in a hurry"
                  measurement: null
                """.formatted(A_DECIMAL_COMMA));
        try (ScreenServer screens = start(work)) {
            Page repaired = screens.browser().go("/profile").press("repair", "repair", Map.of()).page();

            claim("the value is kept exactly as the operator wrote it, and its provenance",
                    () -> assertThat(ProfileEdit.read(profileText(work)).get(ProfileKey.RELEVANCE_SCORE_FLOOR))
                            .isEqualTo(new ProfileEdit.Stated(A_DECIMAL_COMMA, "typed in a hurry")));
            claim("and the page shows it unreadable, for the operator to fix",
                    () -> assertThat(inUse(repaired, "relevanceScoreFloor"))
                            .containsExactly(A_DECIMAL_COMMA, "unreadable"));
        }
    }

    @Test
    @Story("The operator repairs a profile not in the jar's form")
    @DisplayName("The repair is refused where the file changed since the page listed what it would change")
    void theRepairIsRefusedWhereTheFileChangedSinceThePageWasDrawn() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, NOT_IN_FORM_BUT_CERTAIN);
        String behindThePage = "# another note\n" + NOT_IN_FORM_BUT_CERTAIN;
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            theProfileIs(work, behindThePage);
            Page landed = browser.press("repair", "repair", Map.of()).page();

            claim("the file stands as it was changed, no copy is made, and the page says why",
                    () -> assertThat(List.of(profileText(work).equals(behindThePage), aRepairsCopies(work).size(),
                                    byId(landed, "outcome").text().isBlank()))
                            .containsExactly(true, 0, false));
        }
    }

    @Test
    @Story("The operator repairs a profile not in the jar's form")
    @DisplayName("The repair's address changes nothing when it is only opened, and refuses a form posted from another site")
    void theRepairsAddressWritesNothingOnAGetAndRefusesAnotherSite() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, NOT_IN_FORM_BUT_CERTAIN);
        try (ScreenServer screens = start(work)) {
            Page opened = screens.browser().go("/profile-repair").page();
            InetSocketAddress server = screens.server().address();
            RawHttp.Response posted = RawHttp.send(server, "POST", "/profile-repair",
                    List.of("Host: 127.0.0.1:" + server.getPort(), "Origin: http://evil.example"));

            claim("opened, it lands on the profile's page",
                    () -> assertThat(List.of(opened.status(), opened.address().getPath()))
                            .containsExactly(200, "/profile"));
            claim("posted from another site, it is refused",
                    () -> assertThat(posted.status()).isEqualTo(403));
            claim("and neither wrote anything",
                    () -> assertThat(List.of(profileText(work).equals(NOT_IN_FORM_BUT_CERTAIN), aRepairsCopies(work).size()))
                            .containsExactly(true, 0));
        }
    }

    // ---- create if missing, edit if present

    @Test
    @Story("The first save creates the profile")
    @DisplayName("The first save makes a working folder that does not exist, owner-only, and the file in it holds only the value saved")
    void theFirstSaveCreatesAMissingWorkingDirectoryOwnerOnly() throws IOException {
        Path work = folder.resolve("not-yet").resolve("work");
        try (ScreenServer screens = start(work)) {
            screens.browser().go("/profile").press("profile", "save", Map.of("extractionAttempt", "2",
                    "extractionAttempt.provenance", "a second attempt"));

            claim("the file holds only the value saved, the jar adding the rest as unset at its next load",
                    () -> assertThat(profileText(work)).isEqualTo("""
                            ---
                            extractionAttempt:
                              value: "2"
                              provenance: "a second attempt"
                              measurement: null
                            """));
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                claim("and where the file system has POSIX permissions the folder and the one above it are this user's alone",
                        () -> assertThat(Stream.of(work, work.getParent()).map(ProfileEditorScreenTest::permissions))
                                .containsOnly("rwx------"));
            }
        }
    }

    @Test
    @Story("The first save creates the profile")
    @DisplayName("Opening the page, or saving a value that is refused, creates no folder")
    void aRefusedValueOrAPageOpenedCreatesNoFolder() throws IOException {
        Path work = folder.resolve("not-yet").resolve("work");
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            claim("opening the page writes nothing",
                    () -> assertThat(folder.resolve("not-yet")).doesNotExist());

            Page refused = browser.press("profile", "save", Map.of("extractionAttempt", "not a number",
                    "extractionAttempt.provenance", "a second attempt")).page();
            claim("nor does a refused value, and the page shows the form again with what was typed",
                    () -> assertThat(List.of(Files.exists(folder.resolve("not-yet")),
                                    control(form(refused, "profile"), "extractionAttempt").val()))
                            .containsExactly(false, "not a number"));
        }
    }

    @Test
    @Story("The first save creates the profile")
    @DisplayName("The page says when no profile exists yet and where the first save creates it, and that a folder will be made")
    void thePageSaysWhereTheFirstSaveCreatesTheFile() throws IOException {
        Path missing = folder.resolve("not-yet").resolve("work");
        Path present = workingDirectory();
        try (ScreenServer screens = start(missing)) {
            Element said = byId(screens.browser().go("/profile").page(), "profile-file");
            claim("a working folder that does not exist: neither it nor the file, and the path the file will have",
                    () -> assertThat(List.of(said.attr("data-file"), said.attr("data-folder"), said.attr("data-path")))
                            .containsExactly("absent", "absent", missing.resolve(ProfileEdit.FILE_NAME).toAbsolutePath().toString()));
        }
        try (ScreenServer screens = start(present)) {
            Element said = byId(screens.browser().go("/profile").page(), "profile-file");
            claim("a working folder with no profile in it: the file alone is absent",
                    () -> assertThat(List.of(said.attr("data-file"), said.attr("data-folder")))
                            .containsExactly("absent", "present"));
        }
        theProfileIs(present, TWO_KEYS);
        try (ScreenServer screens = start(present)) {
            Element said = byId(screens.browser().go("/profile").page(), "profile-file");
            claim("a profile that exists: edited where it stands",
                    () -> assertThat(List.of(said.attr("data-file"), said.attr("data-folder")))
                            .containsExactly("present", "present"));
        }
    }

    // ---- the preset the profile may override

    @Test
    @Story("The operator sets how finely documents are grouped")
    @DisplayName("How finely documents are grouped is a value of the profile: above zero, with its cost, and shown as the jar ships it until the profile names one")
    void theGroupingResolutionIsEditedLikeAnyValue() throws IOException {
        Path work = workingDirectory();
        theJarSaid(work, Map.of(CLUSTER_RESOLUTION, "1.0", CLUSTER_RESOLUTION + ".source", "shipped"));
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            Page page = browser.page();

            claim("it is shown as the jar ships it, with its cost beside it",
                    () -> assertThat(List.of(inUse(page, CLUSTER_RESOLUTION),
                                    byId(page, "cost-" + CLUSTER_RESOLUTION).text().isBlank()))
                            .containsExactly(List.of("1.0", "shipped"), false));
            claim("and linked to the one report that shows how long the group pages run",
                    () -> assertThat(byId(page, "report-" + CLUSTER_RESOLUTION).select("a").eachAttr("href"))
                            .containsExactly("/report/cluster-sizes.html"));

            Page refused = browser.press("profile", "save", Map.of(CLUSTER_RESOLUTION, "0",
                    CLUSTER_RESOLUTION + ".provenance", "no groups at all")).page();
            claim("zero, which groups nothing, is refused",
                    () -> assertThat(refused.document().select("#refused-" + CLUSTER_RESOLUTION)).hasSize(1));

            browser.go("/profile").press("profile", "save", Map.of(CLUSTER_RESOLUTION, "2.5",
                    CLUSTER_RESOLUTION + ".provenance", "read off cluster-sizes.html: the pages run too long"));
            claim("and a number above it is written as any value is",
                    () -> assertThat(profileText(work)).contains(CLUSTER_RESOLUTION + ":\n  value: \"2.5\"\n"
                            + "  provenance: \"read off cluster-sizes.html: the pages run too long\"\n"));
        }
    }

    // ---- helpers

    private Path workingDirectory() throws IOException {
        return Files.createDirectories(folder.resolve("work"));
    }

    private Path corpus() throws IOException {
        return Files.createDirectories(folder.resolve("corpus"));
    }

    private ScreenServer start(Path workingDirectory) throws IOException {
        return ScreenServer.start(folder, ScreenServer.values(corpus(), workingDirectory));
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

    /** The file as the jar writes it (ADR-330 §1), holding the keys given. */
    private static void theJarSaid(Path workingDirectory, Map<String, String> values) throws IOException {
        StringBuilder text = new StringBuilder("written-at=2026-10-31T10:15:30+01:00\n");
        values.forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
        Files.writeString(workingDirectory.resolve(EFFECTIVE_VALUES), text.toString(), UTF_8);
    }

    /** What {@code in-use-<key>} says: the value in use, and where it came from. */
    private static List<String> inUse(Page page, String key) {
        Element inUse = byId(page, "in-use-" + key);
        return List.of(inUse.attr("data-value"), inUse.attr("data-source"));
    }

    /** The one control of {@code form} named {@code name}; a test fails naming it where there is not one. */
    private static Element control(Element form, String name) {
        List<Element> named = form.select("input, select, textarea").stream()
                .filter(control -> control.attr("name").equals(name))
                .toList();
        if (named.size() != 1) {
            throw new AssertionError("the form '" + form.attr("name") + "' holds " + named.size() + " controls named '"
                    + name + "'");
        }
        return named.getFirst();
    }

    /** A control in words a test can compare: its element, its type and what bears on what it accepts. */
    private static String described(Element control) {
        if (control.nameIs("textarea")) {
            return "textarea";
        }
        if (control.nameIs("select")) {
            return "select " + String.join(",", control.select("option").eachAttr("value"));
        }
        String type = control.attr("type");
        if (type.equals("number")) {
            return "input number step=" + control.attr("step") + " min=" + control.attr("min");
        }
        return "input " + type + (control.hasAttr("list") ? " list=" + control.attr("list") : "");
    }

    /** The keys in the profile's order, the one the jar does not know yet at the end of them, where it belongs. */
    private static List<String> keys() {
        Stream<String> known = Arrays.stream(ProfileKey.values()).map(ProfileKey::key);
        return Stream.concat(known, Stream.of(CLUSTER_RESOLUTION)).distinct().toList();
    }

    private static List<Path> aRepairsCopies(Path workingDirectory) {
        try (Stream<Path> files = Files.list(workingDirectory)) {
            return files.filter(file -> file.getFileName().toString().startsWith(A_REPAIRS_COPY)).toList();
        } catch (IOException e) {
            throw new AssertionError("the working folder could not be listed", e);
        }
    }

    private static String permissions(Path folder) {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "the file system has no POSIX permissions");
        try {
            return PosixFilePermissions.toString(Files.getPosixFilePermissions(folder));
        } catch (IOException e) {
            throw new AssertionError("the permissions of " + folder + " could not be read", e);
        }
    }

    private static HttpServer aModelServiceServing(String... models) throws IOException {
        HttpServer service = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        String listed = String.join(",", Arrays.stream(models).map(name -> "{\"name\": \"" + name + "\"}").toList());
        service.createContext("/api/tags", exchange -> answer(exchange, "{\"models\": [" + listed + "]}"));
        service.start();
        return service;
    }

    private static void answer(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static int aPortNobodyListensOn() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
