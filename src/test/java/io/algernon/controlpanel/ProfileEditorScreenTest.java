package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel as the editor of every value of {@code profile.yaml} (ADR-330, Part A): {@code /profile} keeps
 * ADR-331's list and its one form for each value, and each form shows the value in use and where it came from, what the
 * file states, a line saying what the key is, and the measurement's pointer; a save of one writes nothing where it is
 * unchanged; a file not in the jar's form is shown, and repaired where the repair can lose nothing; and a working
 * directory that does not exist is made by the first save.
 *
 * <p>Driven through the pages as a browser reads them (ADR-297). What the jar says of a value is the file ADR-330 §1
 * gives, written here by hand as {@code NextScreenTest} writes {@code next-invocation.properties}; that the jar writes
 * it is {@code pipeline.TheJarWritesItsEffectiveValuesInvocationTest}'s. Each test has a folder of its own, so no
 * profile leaks between them. The key Part B adds is {@code TheClusterResolutionEditorScreenTest}'s.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("awaits the profile page's values in use and filled fields, what a save checks, the repair, and the folder a save makes")
class ProfileEditorScreenTest {

    /** The file the jar writes for the control panel, in the working directory. */
    private static final String EFFECTIVE_VALUES = "effective-values.properties";

    /** The embedding model the jar says it ships. */
    private static final String SHIPPED_EMBEDDING_MODEL = "bge-m3:latest";

    /** The generation model the jar says it ships. */
    private static final String SHIPPED_GENERATION_MODEL = "qwen3:8b";

    /** A value the jar reads as nothing, written with a decimal comma, which a number control would blank. */
    private static final String A_DECIMAL_COMMA = "0,4";

    /** The name given to a file by a repair begins with this. */
    private static final String A_REPAIRS_COPY = "profile.yaml.before-repair-";

    /** The arrangement an invocation arrived at, which the next act's form offers. */
    private static final String AN_ARRANGEMENT = "0123456789ab";

    /** The words a key is refused with where its value moved and its provenance did not, as the record gives them. */
    private static final String THE_OLD_REASON = "its value changed and its provenance did not";

    /** The words a key is refused with where the file moved since the page was drawn, as the record gives them. */
    private static final String THE_FILE_MOVED = "profile.yaml has changed this value since the page was drawn";

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

    /** The line each of the repair's three changes to {@link #NOT_IN_FORM_BUT_CERTAIN} starts on, the last over two. */
    private static final List<String> THE_LINES_ITS_CHANGES_START_ON = List.of("1", "7", "8");

    @TempDir
    Path folder;

    // ---- the value in use

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("A value shows the one in use and its source: the profile's where it names one, the jar's where it does not, and none where nothing does")
    void theValueInUseAndWhereItCameFromAreShown() throws IOException {
        Path work = workingDirectory();
        theJarSaid(work, Map.of("embeddingModel", SHIPPED_EMBEDDING_MODEL, "embeddingModel.source", "shipped",
                "generationModel", SHIPPED_GENERATION_MODEL, "generationModel.source", "shipped",
                "generationContextWindow", "8192", "generationContextWindow.source", "code"));
        theProfileIs(work, """
                ---
                generationModel:
                  value: "a-named-generation-model:8b"
                  provenance: "the operator's own"
                  measurement: null
                """);
        try (ScreenServer screens = start(work)) {
            Page page = screens.browser().go("/profile").page();

            claim("the embedding model the profile leaves unset is the one the jar ships, and says so",
                    () -> assertThat(inUse(page, "embeddingModel")).containsExactly(SHIPPED_EMBEDDING_MODEL, "shipped"));
            claim("the generation model the profile names is the profile's, whatever the jar ships",
                    () -> assertThat(inUse(page, "generationModel"))
                            .containsExactly("a-named-generation-model:8b", "profile"));
            claim("a number the code carries says it comes from the code",
                    () -> assertThat(inUse(page, "generationContextWindow")).containsExactly("8192", "code"));
            claim("and a floor nothing names is unset, with no value in use",
                    () -> assertThat(inUse(page, "relevanceScoreFloor")).containsExactly("", "unset"));
        }
    }

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("A value the jar reads is the profile's whatever its provenance says, and saved with neither touched writes nothing")
    void aValueIsReadOnItsOwnWhateverItsProvenance() throws IOException {
        Path work = workingDirectory();
        String profile = """
                ---
                relevanceScoreFloor:
                  value: "0.42"
                  provenance: "set by the rule that loses no documentation, over sixty answers"
                  measurement: null
                degenerateOutputConfidenceFloor:
                  value: "0.3"
                  provenance: null
                  measurement: null
                """;
        theProfileIs(work, profile);
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            Page page = browser.page();

            claim("a floor the local model's rule set is the profile's, though a save would refuse that provenance",
                    () -> assertThat(inUse(page, "relevanceScoreFloor")).containsExactly("0.42", "profile"));
            claim("a value written by hand with no provenance is the profile's too",
                    () -> assertThat(inUse(page, "degenerateOutputConfidenceFloor")).containsExactly("0.3", "profile"));

            Page saved = browser.press("relevanceScoreFloor", "save", Map.of()).page();
            claim("and saving the floor as it stands is no refusal, the value being unchanged",
                    () -> assertThat(byId(saved, "outcome").text()).doesNotContain("is not saved"));
            claim("nor is anything written",
                    () -> assertThat(profileText(work)).isEqualTo(profile));
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

            theJarSaid(work, Map.of("embeddingModel", SHIPPED_EMBEDDING_MODEL, "embeddingModel.source", "shipped"));
            Page fresh = screens.browser().go("/profile").page();
            claim("a file written after the jar beside the control panel is fresh",
                    () -> assertThat(byId(fresh, "in-use-embeddingModel").attr("data-fresh")).isEqualTo("fresh"));

            Files.setLastModifiedTime(work.resolve(EFFECTIVE_VALUES),
                    FileTime.fromMillis(Files.getLastModifiedTime(ScreenServer.runtime().jar()).toMillis() - 60_000));
            Page stale = screens.browser().go("/profile").page();
            claim("a file older than that jar is still shown, marked stale, so the value is not lost and not trusted",
                    () -> assertThat(List.of(byId(stale, "in-use-embeddingModel").attr("data-value"),
                                    byId(stale, "in-use-embeddingModel").attr("data-fresh")))
                            .containsExactly(SHIPPED_EMBEDDING_MODEL, "stale"));
        }
    }

    // ---- the fields hold what the file states

    @Test
    @Story("The operator edits what the file states")
    @DisplayName("Each field holds the value and provenance the file states, and a value the jar ships stays out of the field")
    void theFieldsHoldTheFilesOwnWordsAndADefaultStaysOutOfThem() throws IOException {
        Path work = workingDirectory();
        theJarSaid(work, Map.of("embeddingModel", SHIPPED_EMBEDDING_MODEL, "embeddingModel.source", "shipped"));
        theProfileIs(work, TWO_KEYS);
        try (ScreenServer screens = start(work)) {
            Page page = screens.browser().go("/profile").page();

            claim("the floor's field and its provenance hold what the file states",
                    () -> assertThat(List.of(valueOf(page, "relevanceScoreFloor").val(),
                                    control(form(page, "relevanceScoreFloor"), "provenance").val()))
                            .containsExactly("0.4", "read off the sample of sixty"));
            claim("the embedding model's field is empty, because pressing Save must not write into the profile a value"
                            + " nobody chose",
                    () -> assertThat(List.of(valueOf(page, "embeddingModel").val(),
                                    control(form(page, "embeddingModel"), "provenance").val()))
                            .containsExactly("", ""));
            claim("and its placeholder stays an example, not what the jar ships",
                    () -> assertThat(valueOf(page, "embeddingModel").attr("placeholder")).startsWith("e.g. "));
        }
    }

    @Test
    @Story("The operator edits what the file states")
    @DisplayName("The next act's fields hold what the file states, and the arrangement to approve takes the place of an earlier one")
    void theNextActsFieldsHoldTheFilesOwnWordsAndTheArrangementToApproveWins() throws IOException, InterruptedException {
        Path work = aWorkingDirectoryWantingAnArrangementApprovedOnMonday();
        try (ScreenServer screens = start(work)) {
            Element next = form(screens.afterARunHasEnded().go("/next").page(), "next");

            claim("the value is the arrangement this invocation arrived at, and the provenance the file's own",
                    () -> assertThat(List.of(control(next, "arrangementApproved").val(),
                                    control(next, "arrangementApproved.provenance").val()))
                            .containsExactly(AN_ARRANGEMENT, "read the page on Monday"));
        }
    }

    @Test
    @Story("The operator edits what the file states")
    @DisplayName("Saving the arrangement to approve under the provenance of an earlier one is refused, and writes nothing")
    void theArrangementToApproveUnderAnEarlierOnesProvenanceIsRefused() throws IOException, InterruptedException {
        Path work = aWorkingDirectoryWantingAnArrangementApprovedOnMonday();
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.afterARunHasEnded().go("/next");
            String before = profileText(work);
            Page landed = browser.press("next", "save", Map.of()).page();

            claim("the outcome says why it was not saved, the provenance speaking of another value",
                    () -> assertThat(byId(landed, "outcome").text()).contains(THE_OLD_REASON));
            claim("and nothing is written",
                    () -> assertThat(profileText(work)).isEqualTo(before));
        }
    }

    private Path aWorkingDirectoryWantingAnArrangementApprovedOnMonday() throws IOException {
        Path work = workingDirectory();
        Files.writeString(work.resolve(NextInvocation.FILE_NAME), """
                invocation=run
                wants=arrangementApproved
                answer=false
                arrangement=%s
                """.formatted(AN_ARRANGEMENT), UTF_8);
        ScreenServer.childWill(work, "exit 0 the closing line");
        theProfileIs(work, """
                ---
                arrangementApproved:
                  value: "ba9876543210"
                  provenance: "read the page on Monday"
                  measurement: null
                """);
        return work;
    }

    // ---- typed controls

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

            claim("the decimal comma stands in a text control, where a browser keeps it, and not a number control,"
                            + " where a browser would blank it and a save would unset it",
                    () -> assertThat(List.of(valueOf(page, "relevanceScoreFloor").attr("type"),
                                    valueOf(page, "relevanceScoreFloor").val()))
                            .containsExactly("text", A_DECIMAL_COMMA));
            claim("the page says the jar cannot read it",
                    () -> assertThat(inUse(page, "relevanceScoreFloor")).containsExactly(A_DECIMAL_COMMA, "unreadable"));
            claim("a language the jar does not read is one of the choices, selected, so it is not lost either",
                    () -> assertThat(valueOf(page, "deliverableLanguage").select("option[selected]").eachAttr("value"))
                            .containsExactly("fr"));

            browser.press("relevanceScoreFloor", "save", Map.of());
            browser.go("/profile").press("deliverableLanguage", "save", Map.of());
            claim("and saving each untouched leaves the file as it was: an unreadable value is the operator's to fix",
                    () -> assertThat(profileText(work)).isEqualTo(before));
        }
    }

    @Test
    @Story("The operator edits each value in a control of its type")
    @DisplayName("The same pieces of a list however they are separated are no change, and a piece added is written")
    void theSamePiecesHoweverSeparatedAreNoChange() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, """
                ---
                preferredLanguages:
                  value: "it, en"
                  provenance: "Italian first"
                  measurement: null
                """);
        String before = profileText(work);
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");

            claim("the field holds the list as the file states it",
                    () -> assertThat(valueOf(browser.page(), "preferredLanguages").val()).isEqualTo("it, en"));

            browser.press("preferredLanguages", "save", Map.of("value", "it en"));
            claim("sent back with a space, the same pieces are no change, so a comma in the file is not rewritten",
                    () -> assertThat(profileText(work)).isEqualTo(before));

            browser.go("/profile").press("preferredLanguages", "save", Map.of("value", "it en fr",
                    "provenance", "and French for the Paris office"));
            claim("a piece added is written, with what was typed",
                    () -> assertThat(ProfileEdit.read(profileText(work)).get(ProfileKey.PREFERRED_LANGUAGES))
                            .isEqualTo(new ProfileEdit.Stated("it en fr", "and French for the Paris office")));
        }
    }

    @Test
    @Story("The operator picks a model")
    @DisplayName("With no address on the launch page, the model service is asked where the jar last asked it, and the profile page offers what it said")
    void withNoLaunchAddressOllamaIsAskedWhereTheJarLastAskedIt() throws IOException {
        Path work = workingDirectory();
        HttpServer ollama = anOllamaServing("bge-m3:latest", "qwen3:8b");
        try {
            String address = "http://127.0.0.1:" + ollama.getAddress().getPort();
            theJarSaid(work, Map.of("spring.ai.ollama.base-url", address, "spring.ai.ollama.base-url.source", "code"));
            try (ScreenServer screens = start(work)) {
                PanelBrowser browser = screens.browser();

                claim("the launch page asked at that address and offers the models, in the order answered",
                        () -> assertThat(byId(browser.page(), "models").select("option").eachAttr("value"))
                                .containsExactly("bge-m3:latest", "qwen3:8b"));

                Page profile = browser.go("/profile").page();
                claim("and the profile page offers what the launch page was told",
                        () -> assertThat(byId(profile, "models").select("option").eachAttr("value"))
                                .containsExactly("bge-m3:latest", "qwen3:8b"));
            }
        } finally {
            ollama.stop(0);
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

    @Test
    @Story("The operator is told what each value costs")
    @DisplayName("The cost of a value says whether a sample being answered is drawn again, which every value entering the scoring run does")
    void theCostSaysWhichValuesDrawTheSampleAgain() throws IOException {
        List<String> nearerTheEnd = List.of("arrangementApproved", "generationModel", "generationContextWindow",
                "clusterPageByteCeiling", "deliverableLanguage");
        try (ScreenServer screens = start(workingDirectory())) {
            Page page = screens.browser().go("/profile").page();
            Map<String, String> said = new LinkedHashMap<>();
            Map<String, String> expected = new LinkedHashMap<>();
            for (String key : keys()) {
                said.put(key, byId(page, "cost-" + key).attr("data-sample-drawn-again"));
                expected.put(key, String.valueOf(!nearerTheEnd.contains(key)));
            }

            claim("true of each value whose runs include scoring, and false of the " + nearerTheEnd.size()
                            + " that enter only what comes after it or nothing",
                    () -> assertThat(said).containsExactlyEntriesOf(expected));
        }
    }

    // ---- a save of one value

    @Test
    @Story("The operator saves a value")
    @DisplayName("A value left alone is not written and takes no lock, and one changed leaves every other byte")
    void aValueLeftAloneIsNotWritten() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, TWO_KEYS);
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            Page landed = browser.press("extractionAttempt", "save", Map.of()).page();

            claim("saving it as it stands lands on the page and says so",
                    () -> assertThat(byId(landed, "outcome").text()).isNotBlank().doesNotContain("is not saved"));
            claim("the file is as it was",
                    () -> assertThat(profileText(work)).isEqualTo(TWO_KEYS));
            claim("and not even the holder line of a lock it had no need of is written",
                    () -> assertThat(work.resolve("vespera.lock")).doesNotExist());

            browser.go("/profile").press("extractionAttempt", "save", Map.of("value", "3",
                    "provenance", "a third attempt"));
            claim("saving it changed writes it, every other byte as it was",
                    () -> assertThat(profileText(work)).isEqualTo(TWO_KEYS
                            .replace("value: \"2\"", "value: \"3\"")
                            .replace("a second attempt", "a third attempt")));
        }
    }

    @Test
    @Story("The operator saves a value")
    @DisplayName("A value changed under the provenance of the old one is refused and shown again as typed, and so is one with no provenance at all")
    void aValueChangedUnderTheSameProvenanceIsRefused() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, TWO_KEYS);
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            Page kept = browser.press("relevanceScoreFloor", "save", Map.of("value", "0.5")).page();

            claim("the floor's old provenance says nothing of the new value, and the outcome says why it was not saved",
                    () -> assertThat(byId(kept, "outcome").text()).contains(THE_OLD_REASON));
            claim("nothing is written",
                    () -> assertThat(profileText(work)).isEqualTo(TWO_KEYS));
            claim("what was typed stands in the field, so nothing typed is lost",
                    () -> assertThat(valueOf(kept, "relevanceScoreFloor").val()).isEqualTo("0.5"));
            claim("and on the next page too, until it is written",
                    () -> assertThat(valueOf(browser.go("/profile").page(), "relevanceScoreFloor").val()).isEqualTo("0.5"));
            Page letGo = browser.press("relevanceScoreFloor", "save", Map.of("value", "0.4")).page();
            claim("typed back to what the file states, nothing is written and the field holds it",
                    () -> assertThat(List.of(profileText(work), valueOf(letGo, "relevanceScoreFloor").val()))
                            .containsExactly(TWO_KEYS, "0.4"));

            Page none = browser.go("/profile").press("clusterPageByteCeiling", "save", Map.of("value", "1000000")).page();
            claim("a value the file does not hold, given with no provenance, says it was not saved",
                    () -> assertThat(byId(none, "outcome").text()).contains("clusterPageByteCeiling is not saved"));
            claim("and is not written either",
                    () -> assertThat(profileText(work)).isEqualTo(TWO_KEYS));
        }
    }

    @Test
    @Story("The operator saves a value")
    @DisplayName("A value the file changed since the page was drawn is not written over, whether it was left alone or typed over")
    void aValueTheFileChangedSinceThePageWasDrawnIsNotWrittenOver() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, TWO_KEYS);
        String behindThePage = TWO_KEYS.replace("value: \"0.4\"", "value: \"0.6\"")
                .replace("read off the sample of sixty", "set by the rule that loses no documentation");
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            theProfileIs(work, behindThePage);
            browser.press("relevanceScoreFloor", "save", Map.of());

            claim("the floor saved behind the page, which the form still holds as it was, is kept",
                    () -> assertThat(profileText(work)).isEqualTo(behindThePage));

            browser.go("/profile");
            String behindAgain = behindThePage.replace("value: \"0.6\"", "value: \"0.7\"");
            theProfileIs(work, behindAgain);
            Page refused = browser.press("relevanceScoreFloor", "save", Map.of("value", "0.8",
                    "provenance", "my own reading")).page();

            claim("typed over, the outcome says the file moved",
                    () -> assertThat(byId(refused, "outcome").text()).contains(THE_FILE_MOVED));
            claim("and the file keeps the newer value",
                    () -> assertThat(profileText(work)).isEqualTo(behindAgain));
        }
    }

    @Test
    @Story("The operator saves a value")
    @DisplayName("A provenance the file holds over two lines, which a text field cannot hold, is no change when left alone")
    void aProvenanceOverTwoLinesLeftAloneIsNoChange() throws IOException {
        Path work = workingDirectory();
        String profile = """
                ---
                relevanceScoreFloor:
                  value: "0.4"
                  provenance: "read off\\nthe sample"
                  measurement: null
                """;
        theProfileIs(work, profile);
        try (ScreenServer screens = start(work)) {
            // What a browser sends of a text field: it removes a line feed from what the field holds.
            screens.browser().go("/profile").press("relevanceScoreFloor", "save", Map.of("provenance", "read offthe sample"));

            claim("the file is as it was",
                    () -> assertThat(profileText(work)).isEqualTo(profile));
        }
    }

    @Test
    @Story("The operator saves a value")
    @DisplayName("A seed folder with a space before its name holds it in the field, and is no change when left alone")
    void aSeedFolderLeftAloneKeepsTheSpaceBeforeItsName() throws IOException {
        Path work = workingDirectory();
        String profile = """
                ---
                seedFolder:
                  value: " exemplars"
                  provenance: "as pasted"
                  measurement: null
                """;
        theProfileIs(work, profile);
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");

            claim("the field holds the space, another folder's name without it",
                    () -> assertThat(valueOf(browser.page(), "seedFolder").val()).isEqualTo(" exemplars"));

            browser.press("seedFolder", "save", Map.of());
            claim("and the file is as it was, though no such folder is there to pass a check",
                    () -> assertThat(profileText(work)).isEqualTo(profile));
        }
    }

    @Test
    @Story("The operator saves a value")
    @DisplayName("Another invocation holding the working folder writes nothing, says so, and shows what was typed again")
    void aHeldLockWritesNothingAndKeepsWhatWasTyped() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, TWO_KEYS);
        try (ScreenServer screens = start(work);
                FileChannel channel = FileChannel.open(work.resolve("vespera.lock"), StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap("pid=1 started=2026-10-19T10:00:00+02:00 command=run\n".getBytes(UTF_8)));
            FileLock held = channel.tryLock(Long.MAX_VALUE - 1, 1, false);
            Page landed = screens.browser().go("/profile").press("extractionAttempt", "save", Map.of("value", "3",
                    "provenance", "a third attempt")).page();
            held.release();

            claim("nothing is written",
                    () -> assertThat(profileText(work)).isEqualTo(TWO_KEYS));
            claim("the page says what stopped it",
                    () -> assertThat(byId(landed, "outcome").text()).isNotBlank());
            claim("and what was typed stands in the field",
                    () -> assertThat(valueOf(landed, "extractionAttempt").val()).isEqualTo("3"));
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
            claim("it lists each change by the line it starts on, so nothing is changed unseen",
                    () -> assertThat(byId(problem, "repair-changes").select("li").eachAttr("data-line"))
                            .containsExactlyElementsOf(THE_LINES_ITS_CHANGES_START_ON));
            claim("and holds no form of a value, which could not be saved",
                    () -> assertThat(problem.document().select("form[name=relevanceScoreFloor]")).isEmpty());
            claim("nor the list that would lead to one",
                    () -> assertThat(problem.document().select("#profile-list")).isEmpty());
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
            claim("the page now holds the form of a value, and no problem",
                    () -> assertThat(List.of(repaired.document().select("form[name=relevanceScoreFloor]").size(),
                                    repaired.document().select("#form-problem").size()))
                            .containsExactly(1, 0));
        }
    }

    @Test
    @Story("The operator repairs a profile not in the jar's form")
    @DisplayName("A comment on a value's own line, or among the lines of its measurement, is removed by the repair like any other")
    void aCommentOnAKeysLineOrInAMeasurementIsRemovedToo() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, """
                seedFolder: # the exemplars
                  value: null
                  provenance: null
                  measurement:
                    source: "walk 3" # of the census
                    refreshedAt: "2026-10-19T10:00:00Z"
                """);
        Profile asTheJarReadsIt = new ProfileStore(work).load();
        try (ScreenServer screens = start(work)) {
            screens.browser().go("/profile").press("repair", "repair", Map.of());

            claim("the two comments are gone, and every other byte is as it was",
                    () -> assertThat(profileText(work)).isEqualTo("""
                            seedFolder:
                              value: null
                              provenance: null
                              measurement:
                                source: "walk 3"
                                refreshedAt: "2026-10-19T10:00:00Z"
                            """));
            claim("and the jar reads what it read before",
                    () -> assertThat(new ProfileStore(work).load()).isEqualTo(asTheJarReadsIt));
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
    @DisplayName("An unreadable value is the operator's to fix: the repair keeps it as written, and the page says the jar cannot read it")
    void anUnreadableValueIsTheOperatorsAndNoRepairTouchesIt() throws IOException {
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

            claim("the file stands as it was changed",
                    () -> assertThat(profileText(work)).isEqualTo(behindThePage));
            claim("no copy is made",
                    () -> assertThat(aRepairsCopies(work)).isEmpty());
            claim("and the page says why",
                    () -> assertThat(byId(landed, "outcome").text()).isNotBlank());
        }
    }

    @Test
    @Story("The operator repairs a profile not in the jar's form")
    @DisplayName("The repair's address changes nothing when it is only opened, and lands on the profile's page")
    void theRepairsAddressWritesNothingOnAGet() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, NOT_IN_FORM_BUT_CERTAIN);
        try (ScreenServer screens = start(work)) {
            Page opened = screens.browser().go("/profile-repair").page();

            claim("opened, it lands on the profile's page",
                    () -> assertThat(List.of(opened.status(), opened.address().getPath()))
                            .containsExactly(200, "/profile"));
            claim("it wrote nothing into the file",
                    () -> assertThat(profileText(work)).isEqualTo(NOT_IN_FORM_BUT_CERTAIN));
            claim("and made no copy",
                    () -> assertThat(aRepairsCopies(work)).isEmpty());
        }
    }

    @Test
    @Story("The operator repairs a profile not in the jar's form")
    @DisplayName("A copy of the file that cannot be made leaves the file as it was, and the page says so")
    void aCopyThatCannotBeMadeLeavesTheFileAsItWas() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, NOT_IN_FORM_BUT_CERTAIN);
        Files.writeString(work.resolve("vespera.lock"), "");
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            try (AutoCloseable restored = noNewFileCanBeMadeIn(work)) {
                claim("the folder takes no new file, the precondition of this test",
                        () -> assertThatThrownBy(() -> Files.createFile(work.resolve("a-new-file")))
                                .isInstanceOf(IOException.class));

                Page landed = browser.press("repair", "repair", Map.of()).page();

                claim("the file is as it was, every comment still in it",
                        () -> assertThat(profileText(work)).isEqualTo(NOT_IN_FORM_BUT_CERTAIN));
                claim("no copy lies beside it",
                        () -> assertThat(aRepairsCopies(work)).isEmpty());
                claim("and the page says what stopped the repair",
                        () -> assertThat(byId(landed, "outcome").text()).isNotBlank());
            } catch (AssertionError | IOException | RuntimeException failure) {
                throw failure;
            } catch (Exception closing) {
                throw new AssertionError("the folder's permissions could not be put back", closing);
            }
        }
    }

    /**
     * Has {@code folder} refuse a new file until the result is closed: by its POSIX permissions where it has them, or
     * by an access control entry denying the owner that one right on NTFS. The folder stays readable and its files
     * writable, so what fails is the making of a copy.
     */
    private static AutoCloseable noNewFileCanBeMadeIn(Path folder) throws IOException {
        if (isPosix()) {
            Set<PosixFilePermission> before = Files.getPosixFilePermissions(folder);
            Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("r-x------"));
            return () -> Files.setPosixFilePermissions(folder, before);
        }
        AclFileAttributeView acl = Files.getFileAttributeView(folder, AclFileAttributeView.class);
        assumeTrue(acl != null, "the file system has neither POSIX permissions nor access control lists");
        List<AclEntry> before = acl.getAcl();
        List<AclEntry> denying = new ArrayList<>();
        denying.add(AclEntry.newBuilder().setType(AclEntryType.DENY).setPrincipal(acl.getOwner())
                .setPermissions(AclEntryPermission.ADD_FILE).build());
        denying.addAll(before);
        acl.setAcl(denying);
        return () -> acl.setAcl(before);
    }

    // ---- create if missing, edit if present

    @Test
    @Story("The first save creates the profile")
    @DisplayName("The first save makes a working folder that does not exist, owner-only, and the file in it holds only the value saved")
    void theFirstSaveCreatesAMissingWorkingDirectoryOwnerOnly() throws IOException {
        Path work = folder.resolve("not-yet").resolve("work");
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            browser.press("extractionAttempt", "save", Map.of("value", "2", "provenance", "a second attempt"));

            claim("the file holds only the value saved, the jar adding the rest as unset at its next load",
                    () -> assertThat(profileText(work)).isEqualTo("""
                            ---
                            extractionAttempt:
                              value: "2"
                              provenance: "a second attempt"
                              measurement: null
                            """));

            browser.go("/profile").press("extractionAttempt", "save", Map.of("value", "3", "provenance", "a third attempt"));
            claim("and the owner goes on writing there, one file moved over another",
                    () -> assertThat(ProfileEdit.read(profileText(work)).get(ProfileKey.EXTRACTION_ATTEMPT))
                            .isEqualTo(new ProfileEdit.Stated("3", "a third attempt")));
            if (isPosix()) {
                claim("where the file system has POSIX permissions the folder and the one above it are this user's alone",
                        () -> assertThat(Stream.of(work, work.getParent()).map(ProfileEditorScreenTest::permissions))
                                .containsOnly("rwx------"));
            } else if (Files.getFileStore(work).supportsFileAttributeView("acl")) {
                UserPrincipal thisUser = work.getFileSystem().getUserPrincipalLookupService()
                        .lookupPrincipalByName(System.getProperty("user.name"));
                claim("where it holds access control lists each folder made, and the file, hold one entry, this user's,"
                                + " though what lies above them hands others down",
                        () -> assertThat(Stream.of(work, work.getParent(), work.resolve(ProfileEdit.FILE_NAME))
                                        .map(ProfileEditorScreenTest::entriesOf))
                                .containsOnly(List.<List<Object>>of(List.of(AclEntryType.ALLOW, thisUser))));
            }
        }
    }

    /**
     * A folder another user owns cannot be made on one user's machine: {@code Files.setOwner} to another is refused to a
     * user not elevated. So the comparison is shown by naming another user than the owner, as {@code LaunchValuesTest}
     * does; a second account's being kept out of a folder is shown by nothing here. A process run as administrator owns
     * what it makes as a group, which the rule lets stand, so the test aborts there.
     */
    @Test
    @Story("The first save creates the profile")
    @DisplayName("A working folder whose owner is not the user is refused, wherever the file system names an owner")
    void aFolderOwnedByAnotherIsRefusedWhereAnOwnerCanBeRead() throws IOException {
        Path work = workingDirectory();
        assumeTrue(Files.getFileStore(work).supportsFileAttributeView("owner"), "this volume names no owner to compare");
        assumeFalse(Files.getOwner(work) instanceof GroupPrincipal,
                "this process owns what it makes as a group, as a process run as administrator does");
        String thisUser = System.getProperty("user.name");

        claim("named for another user than its owner, it is refused",
                () -> assertThat(WorkingDirectoryWrite.refusalOfFolder(work, thisUser + "-someone-else", ProfileEdit.FILE_NAME))
                        .isPresent());
        claim("and named for its owner it is not",
                () -> assertThat(WorkingDirectoryWrite.refusalOfFolder(work, thisUser, ProfileEdit.FILE_NAME)).isEmpty());
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

            Page refused = browser.press("extractionAttempt", "save", Map.of("value", "not a number",
                    "provenance", "a second attempt")).page();
            claim("nor does a refused value",
                    () -> assertThat(folder.resolve("not-yet")).doesNotExist());
            claim("and the page shows the form again with what was typed",
                    () -> assertThat(valueOf(refused, "extractionAttempt").val()).isEqualTo("not a number"));
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

    @Test
    @Story("The first save creates the profile")
    @DisplayName("A start refuses a working folder that is not there until a save has made it, and only a save makes it")
    void aStartStillRefusesTheFolderASaveWouldMake() throws IOException {
        Path work = folder.resolve("not-yet").resolve("work");
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser();
            claim("the launch page says the folder is not there",
                    () -> assertThat(byId(browser.page(), "status-vespera.working-dir").attr("data-status"))
                            .isEqualTo("no-such-folder"));

            claim("its row of the preflight is red",
                    () -> assertThat(byId(browser.page(), "preflight").select("[data-row=working-directory]").attr("data-state"))
                            .isEqualTo("bad"));

            Page refused = browser.press("launch", "run", Map.of()).page();
            claim("Run is refused by the line that says so",
                    () -> assertThat(byId(refused, "outcome").text()).contains("is no folder that is there"));
            claim("and starting made nothing",
                    () -> assertThat(folder.resolve("not-yet")).doesNotExist());

            browser.go("/profile").press("extractionAttempt", "save", Map.of("value", "2", "provenance", "a second attempt"));
            Page after = browser.go("/").page();
            claim("once a value is saved, the folder is found",
                    () -> assertThat(byId(after, "status-vespera.working-dir").attr("data-status")).isEqualTo("found"));
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

    /** The control of the value in the form of {@code key}. */
    private static Element valueOf(Page page, String key) {
        return control(form(page, key), "value");
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

    /** The keys in the profile's order. */
    private static List<String> keys() {
        return Arrays.stream(ProfileKey.values()).map(ProfileKey::key).toList();
    }

    private static List<Path> aRepairsCopies(Path workingDirectory) {
        try (Stream<Path> files = Files.list(workingDirectory)) {
            return files.filter(file -> file.getFileName().toString().startsWith(A_REPAIRS_COPY)).toList();
        } catch (IOException e) {
            throw new AssertionError("the working folder could not be listed", e);
        }
    }

    private static boolean isPosix() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    private static String permissions(Path folder) {
        try {
            return PosixFilePermissions.toString(Files.getPosixFilePermissions(folder));
        } catch (IOException e) {
            throw new AssertionError("the permissions of " + folder + " could not be read", e);
        }
    }

    /** Each access control entry of {@code path}: its type and who it names. */
    private static List<List<Object>> entriesOf(Path path) {
        try {
            return Files.getFileAttributeView(path, AclFileAttributeView.class).getAcl().stream()
                    .map(entry -> List.<Object>of(entry.type(), entry.principal()))
                    .toList();
        } catch (IOException e) {
            throw new AssertionError("the entries of " + path + " could not be read", e);
        }
    }

    private static HttpServer anOllamaServing(String... models) throws IOException {
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
}
