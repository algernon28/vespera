package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every field and every section of the drawer is described in plain words, the technical key beside the name as
 * secondary text (ADR-340 §7). The words are the record's, read whole: each launch value's name and hint, each value of
 * the profile's name and its line of what it is, and each section's line.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
class EveryFieldIsNamedInPlainWordsScreenTest {

    /** Each launch value, by its name, with its name in plain words and what it is. */
    private static final Map<String, List<String>> LAUNCH_VALUES = launchValues();

    /** Each value of the profile, by its key, with its name in plain words and what it is. */
    private static final Map<String, List<String>> PROFILE_VALUES = profileValues();

    @TempDir
    Path folder;

    @Test
    @Story("The operator reads what each value is in plain words")
    @DisplayName("Each launch value is named in plain words, its key beside its name, and says what it is")
    void eachLaunchValueIsNamedInPlainWordsWithItsKeyBeside() throws IOException {
        try (ScreenServer screens = aServer()) {
            Page home = screens.browser().page();
            Element values = form(home, "launch-values");
            Map<String, List<String>> shown = new LinkedHashMap<>();
            for (String name : LAUNCH_VALUES.keySet()) {
                Element control = controlOf(values, name);
                Element label = labelOf(home, control);
                shown.put(name, List.of(label.ownText(), keyBeside(label),
                        byId(home, control.attr("aria-describedby")).text()));
            }
            Map<String, List<String>> expected = new LinkedHashMap<>();
            LAUNCH_VALUES.forEach((name, words) -> expected.put(name, List.of(words.get(0), name, words.get(1))));

            claim("each of the seven is labelled by its plain name with its key beside it, and its hint says what it is",
                    () -> assertThat(shown).containsExactlyEntriesOf(expected));
        }
    }

    @Test
    @Story("The operator reads what each value is in plain words")
    @DisplayName("Each value of the run settings is named in plain words, its key beside its name, and says what it is")
    void eachValueOfTheProfileIsNamedInPlainWordsWithItsKeyBeside() throws IOException {
        try (ScreenServer screens = aServer()) {
            Page drawer = screens.browser().go("/?open=values-profile").page();
            Element profile = form(drawer, "profile");
            Map<String, List<String>> shown = new LinkedHashMap<>();
            for (String key : PROFILE_VALUES.keySet()) {
                Element label = labelOf(drawer, controlOf(profile, key));
                shown.put(key, List.of(label.ownText(), keyBeside(label), byId(drawer, "about-" + key).text()));
            }
            Map<String, List<String>> expected = new LinkedHashMap<>();
            PROFILE_VALUES.forEach((key, words) -> expected.put(key, List.of(words.get(0), key, words.get(1))));

            claim("each of the twenty-one is labelled by its plain name with its key beside it, and says what it is",
                    () -> assertThat(shown).containsExactlyEntriesOf(expected));
        }
    }

    @Test
    @Story("The operator reads what each value is in plain words")
    @DisplayName("Each section of the drawer says in one plain line what it holds")
    void eachSectionOfTheDrawerSaysWhatItHoldsInPlainWords() throws IOException {
        try (ScreenServer screens = aServer()) {
            Page home = screens.browser().page();
            Map<String, String> shown = new LinkedHashMap<>();
            for (String section : sections().keySet()) {
                shown.put(section, byId(home, "about-" + section).text());
            }

            claim("each of the five sections says what it holds, in these words",
                    () -> assertThat(shown).containsExactlyEntriesOf(sections()));
        }
    }

    private static Element controlOf(Element form, String name) {
        Element control = form.selectFirst("[name='" + name + "']:not([type=hidden])");
        if (control == null) {
            throw new AssertionError("the form '" + form.attr("name") + "' holds no control named '" + name + "'");
        }
        return control;
    }

    private static Element labelOf(Page page, Element control) {
        Element label = page.document().selectFirst("label[for='" + control.id() + "']");
        if (label == null) {
            throw new AssertionError("no label names the control '" + control.attr("name") + "'");
        }
        return label;
    }

    /** The key written beside a label's name, as secondary text; empty where none is. */
    private static String keyBeside(Element label) {
        Element key = label.selectFirst("small.key");
        return key == null ? "" : key.text();
    }

    private static Map<String, List<String>> launchValues() {
        Map<String, List<String>> words = new LinkedHashMap<>();
        words.put("vespera.corpus-root", List.of("Documents folder",
                "The folder with the documents to process. Vespera only reads it and never changes anything in it."));
        words.put("vespera.working-dir", List.of("Work folder",
                "Where Vespera keeps its database, its reports and profile.yaml. Left empty, it is the folder this"
                        + " machine is set to use, or else the .vespera folder of the folder the control panel was"
                        + " started from, as shown. If it is not there yet, the first value you save makes it."));
        words.put("vespera.deliverable-root", List.of("Output folder",
                "Leave it empty to put each run's pages in a folder of their own under the work folder, as shown; a"
                        + " folder named here holds one run's pages, so name an empty one."));
        words.put("vespera.docling.image", List.of("Converter build",
                "Which build of the document converter to use: GPU where this machine has a graphics card, CPU"
                        + " otherwise."));
        words.put("vespera.docling.base-url", List.of("Converter address",
                "Where the document converter answers. Change it only if you moved it."));
        words.put("spring.ai.ollama.base-url", List.of("Local models address", "Where the local AI models answer."));
        words.put("vespera.label.model", List.of("Answering model",
                "The local model that answers the relevance questions when you press Label with Autolabel ticked."));
        return words;
    }

    private static Map<String, List<String>> profileValues() {
        Map<String, List<String>> words = new LinkedHashMap<>();
        words.put("seedFolder", List.of("Example documents",
                "Documents you already know belong in what you keep; everything else is measured against them."));
        words.put("degenerateOutputConfidenceFloor", List.of("Lowest text quality",
                "A file whose extracted text the converter rates below this, from 0 to 1, is set aside: its text is too"
                        + " poor to use."));
        words.put("degenerateOutputWordFloor", List.of("Fewest words in a file",
                "A file holding fewer words than this once its text is extracted is set aside."));
        words.put("boilerplateDocumentFrequencyFloor", List.of("Repeated text",
                "A passage found in at least this share of the documents, from 0 to 1, is taken for template text and"
                        + " not compared."));
        words.put("embeddingModel", List.of("Comparing model",
                "The local model that turns each text into numbers, so that documents can be compared."));
        words.put("relevanceScoreFloor", List.of("Relevance cut-off",
                "A document less like your example documents than this is left out."));
        words.put("arrangementApproved", List.of("Approved grouping",
                "The grouping you approve for writing, by its name, or auto to approve every grouping proposed."));
        words.put("generationModel", List.of("Writing model",
                "The local model that writes the text connecting the documents of each page."));
        words.put("generationContextWindow", List.of("Writing model's reading size",
                "How many tokens of a group the writing model reads in one go."));
        words.put("clusterPageByteCeiling", List.of("Largest page",
                "A group whose page would be larger than this many bytes is split over several pages."));
        words.put("logTimestampShareFloor", List.of("Log detection",
                "A text file in which at least this share of lines begin with a date or a time, from 0 to 1, is taken"
                        + " for a log and left out."));
        words.put("extractionAttempt", List.of("Ask again about failed files",
                "Raise it by one to ask the converter again about the files it did not answer about."));
        words.put("codeNameEndings", List.of("Code file endings",
                "File name endings that mark a file as code, kept apart from the documents."));
        words.put("outOfScopeNameEndings", List.of("Endings left out",
                "File name endings that mark a file as no document at all, so that it is left out."));
        words.put("translationSimilarityFloor", List.of("Translation match",
                "How alike, from 0 to 1, two documents in different languages must be to be taken for one."));
        words.put("preferredLanguages", List.of("Preferred languages",
                "Which language's copy to keep, first choice first, of a document held in several."));
        words.put("configurationSettingShareFloor", List.of("Configuration file detection",
                "A text file in which at least this share of lines are a program's settings, from 0 to 1, is taken for"
                        + " a configuration file and left out, unless it lies in a set of code."));
        words.put("deliverableLanguage", List.of("Language of the pages",
                "The language the finished pages are written in, English or Italian."));
        words.put("clusterResolution", List.of("Grouping detail",
                "How finely the documents under each example divide into groups: higher makes more, smaller groups."));
        words.put("codeSetFolders", List.of("Code folders elsewhere",
                "Folders of code elsewhere on this machine, read with the documents and kept as code sets of their"
                        + " own."));
        words.put("foldersNotWalked", List.of("Folders not read",
                "Folders of the documents folder that Vespera never reads. Untick them under Folders to process."));
        return words;
    }

    private static Map<String, String> sections() {
        Map<String, String> words = new LinkedHashMap<>();
        words.put("values-folders",
                "Where the documents are, where Vespera keeps its work, and where the finished pages go.");
        words.put("values-services",
                "The document converter and the local models Vespera asks, and where they answer.");
        words.put("values-corpus", "Every folder of the documents folder is read. Untick a folder to leave it unread;"
                + " tick Code on a folder of code to name its code set.");
        words.put("values-relevance", "Folders of what you are not keeping: every relevance question about a file below"
                + " one is answered not relevant, and every other relevant.");
        words.put("values-profile", "The values of profile.yaml in the work folder. An empty field uses the value shown"
                + " in it; changing a value may make the next run redo some steps.");
        return words;
    }

    private ScreenServer aServer() throws IOException {
        return ScreenServer.start(folder, ScreenServer.values(Files.createDirectories(folder.resolve("corpus")),
                Files.createDirectories(folder.resolve("work"))));
    }
}
