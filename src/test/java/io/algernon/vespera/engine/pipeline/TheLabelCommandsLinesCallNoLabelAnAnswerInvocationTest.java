package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.embedding.LabelQuestion;
import io.algernon.vespera.engine.embedding.RelevanceLabeller;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * The lines {@code vespera label} and {@code vespera label --auto} end on, and the counts {@code report.json} keeps of
 * them, call no relevance label an answer and no document to label a question (ADR-424 §3), the counts still under
 * {@code label.answers} (ADR-424 §8). Driven through the commands an operator types, over a scored corpus of one
 * document; the labeller is a scripted double that would say relevant, and is asked nothing here, the one document
 * being labelled by a person first.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, AutoLabelling.class,
        TheLabelCommandsLinesCallNoLabelAnAnswerInvocationTest.Beans.class})
@Epic("Relevance")
@Feature("Labelling")
@Link(name = "ADR-424", url = Adr.NO_TEXT_FOR_THE_OPERATOR_CALLS_A_LABEL_AN_ANSWER, type = "adr")
@Issue("879")
@ExtendWith(OutputCaptureExtension.class)
class TheLabelCommandsLinesCallNoLabelAnAnswerInvocationTest {

    private static final String LABEL_FILE = "relevance-labels.yaml";

    private static final String THE_LABELLER = "scripted-labeller";

    /** The counts {@code vespera label --auto} keeps, in the order the report holds them. */
    private static final List<String> THE_AUTO_COUNTS =
            List.of("relevant", "not relevant", "left blank", "labelled by a person");

    @TestConfiguration
    static class Beans {
        @Bean
        RelevanceLabeller scriptedLabeller() {
            return new RelevanceLabeller() {
                @Override
                public String identity() {
                    return THE_LABELLER;
                }

                @Override
                public Optional<String> refusal() {
                    return Optional.empty();
                }

                @Override
                public Optional<Boolean> answer(LabelQuestion question) {
                    return Optional.of(true);
                }
            };
        }
    }

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Test
    @Story("A relevance label is a label wherever the operator reads of it")
    @DisplayName("Recording a label, labelling with the local model, and a label typed and not recorded are each said in the words of labels")
    void theLabelCommandsLinesSayLabels(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());
        String seedSet = Walk.canonicalRoot(seeds).toString();
        Path labels = workingDirectory.resolve(LABEL_FILE);

        Files.writeString(labels, Files.readString(labels).replace("relevant: null", "relevant: false"));
        int beforeTheLabel = output.getAll().length();
        cli.run("label");
        String recorded = output.getAll().substring(beforeTheLabel);

        int beforeTheModel = output.getAll().length();
        cli.run("label", "--auto");
        String labelled = output.getAll().substring(beforeTheModel);
        JsonNode report = TheReportFile.in(workingDirectory);
        List<String> counted = new ArrayList<>();
        report.get("label").get("answers").forEach(count -> counted.add(count.get("words").asString()));

        Files.writeString(labels, Files.readString(labels).replace("relevant: false", "relevant: true"));
        int beforeTheRefusal = output.getAll().length();
        cli.run("label", "--auto");
        String refused = output.getAll().substring(beforeTheRefusal);

        claim("recording the one document's label says one label was recorded about the seed set, new",
                () -> assertThat(recorded).contains("recorded 1 label(s) about the seed set at " + seedSet
                        + ": 1 new, 0 changed, 0 unchanged"));
        claim("the local model, asked nothing, says it labelled none of the one document, which was already labelled by"
                        + " a person",
                () -> assertThat(labelled).contains("labelled 0 of 1 document(s) with " + THE_LABELLER + ": 0 relevant,"
                        + " 0 not relevant; 0 left blank because no opening could be read or the model gave no answer;"
                        + " 1 already labelled by a person and left as they are"));
        claim("its report counts " + THE_AUTO_COUNTS + " in that order, under the field the report has always kept them"
                        + " in",
                () -> assertThat(counted).containsExactlyElementsOf(THE_AUTO_COUNTS));
        claim("a label typed into the file and not recorded makes the local model's command refuse, saying one label is"
                        + " not yet recorded, after the words every refusal of it begins with",
                () -> assertThat(refused).contains("vespera label --auto recorded nothing: the label file holds 1"
                        + " label(s) not yet recorded; run vespera label first"));
    }
}
