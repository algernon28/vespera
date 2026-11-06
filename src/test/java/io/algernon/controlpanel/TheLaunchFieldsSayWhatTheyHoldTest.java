package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Each launch value's field carries a real example and a one-line hint (ADR-331 §8, which ADR-342 keeps), from
 * {@code LaunchValues}, where the words the launch page held now live (ADR-344 §8). The examples and hints are ADR-331
 * §8's table's, written out here; the Docling image's example is the CPU image the table names by its section. The
 * answering model's example names a model, so it is held by its form alone, as ADR-353 §7 has every model held.
 */
@Link(name = "ADR-353", url = Adr.THE_EMBEDDING_MODELS_DEFAULT_IS_QWEN3_EMBEDDING_8B_BY_ITS_FILE_ON_HUGGING_FACE, type = "adr")
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-331", url = Adr.THE_CONTROL_PANEL_OPENS_A_WINDOW_OF_ITS_OWN_AND_ITS_PAGES_ARE_A_COCKPIT, type = "adr")
class TheLaunchFieldsSayWhatTheyHoldTest {

    /** The seven launch values, by their properties (ADR-292 §1). */
    private static final List<String> THE_SEVEN = List.of("vespera.corpus-root", "vespera.working-dir",
            "vespera.deliverable-root", "vespera.docling.image", "vespera.docling.base-url",
            "spring.ai.ollama.base-url", "vespera.label.model");

    /** The answering model's launch value, whose example names a model, and so is held by its form (ADR-353 §7). */
    private static final String LABEL_MODEL = "vespera.label.model";

    @Test
    @Story("The operator fills the launch values")
    @DisplayName("Each launch value's field has the example and the hint the record gives it")
    void eachLaunchFieldHasItsExampleAndHint() {
        Map<String, String> examples = new TreeMap<>();
        Map<String, String> hints = new TreeMap<>();
        for (String property : THE_SEVEN) {
            examples.put(property, LaunchValues.exampleOf(property));
            hints.put(property, LaunchValues.hintOf(property));
        }

        String labelModelsExample = examples.remove(LABEL_MODEL);
        claim("each field's example but the answering model's is the table's, the Docling image's the CPU image",
                () -> assertThat(examples).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "vespera.corpus-root", "D:\\archive\\corpus",
                        "vespera.working-dir", "D:\\vespera\\work",
                        "vespera.deliverable-root", "D:\\vespera\\deliverable",
                        "vespera.docling.image", "vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2",
                        "vespera.docling.base-url", "http://localhost:5001",
                        "spring.ai.ollama.base-url", "http://localhost:11434")));
        claim("and the answering model's example is a model's name, one name with no space in it; which model it names"
                        + " no test holds",
                () -> assertThat(labelModelsExample).isNotBlank().doesNotContainAnyWhitespaces());
        claim("and each field's hint is the table's",
                () -> assertThat(hints).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "vespera.corpus-root", "The folder of documents Vespera reads. It is never changed.",
                        "vespera.working-dir", "Where the database, the reports and profile.yaml live. Create it if it is"
                                + " not there yet.",
                        "vespera.deliverable-root", "Where the finished pages go. Leave it empty to keep them in the"
                                + " working directory.",
                        "vespera.docling.image", "The image the converter runs from. The two offered are the ones this"
                                + " build knows.",
                        "vespera.docling.base-url", "Where the converter answers.",
                        "spring.ai.ollama.base-url", "Where the local models answer.",
                        "vespera.label.model", "The local model that answers the questions when you ask it to.")));
    }
}
