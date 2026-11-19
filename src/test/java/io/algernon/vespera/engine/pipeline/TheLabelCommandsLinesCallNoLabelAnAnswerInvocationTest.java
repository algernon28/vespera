package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.embedding.LabelQuestion;
import io.algernon.vespera.engine.embedding.RelevanceLabeller;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.profile.TextValue;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * document, or of two of which one is labelled; the labeller is a scripted double that says relevant, or stops where a
 * test has it stop.
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

    /** Why the scripted labeller stops, where a test has it stop. */
    private static final String WHY_IT_STOPPED = "the scripted labeller was stopped by this test";

    /**
     * Whether the scripted labeller stops when asked, as a local model gone away does. Static, as the bean is built
     * once for every test of the class; set back before and after each test, since class order differs from one
     * machine to the next.
     */
    private static volatile boolean theLabellerStops;

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
                    if (theLabellerStops) {
                        throw new IllegalStateException(WHY_IT_STOPPED);
                    }
                    return Optional.of(true);
                }
            };
        }
    }

    @BeforeEach
    void theLabellerGoesOnFromTheStart() {
        theLabellerStops = false;
    }

    @AfterEach
    void theLabellerGoesOnAfterwards() {
        theLabellerStops = false;
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

    @Test
    @Story("A relevance label is a label wherever the operator reads of it")
    @DisplayName("Documents still blank, nothing new, no file, the labeller stopped and no seed folder are each said in the words of labels")
    void theLabelCommandsOtherLinesSayLabels(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        Files.writeString(root.resolve("first.txt"), "a first corpus document");
        Files.writeString(root.resolve("second.txt"), "a second corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());
        String seedSet = Walk.canonicalRoot(seeds).toString();
        Path labels = workingDirectory.resolve(LABEL_FILE);
        Files.writeString(labels, Files.readString(labels).replaceFirst("relevant: null", "relevant: false"));

        String recorded = linesOf(output, () -> cli.run("label"));
        String nothingNew = linesOf(output, () -> cli.run("label"));
        String noFile = linesOf(output, () -> cli.run("label", root.resolve("no-such-label-file.yaml").toString()));
        theLabellerStops = true;
        String stopped = linesOf(output, () -> cli.run("label", "--auto"));
        theLabellerStops = false;
        profileStore.save(ProfileFixture.profileFrom(profileStore.load()).seedFolder(TextValue.unset()).build());
        String noSeedFolder = linesOf(output, () -> cli.run("label"));

        claim("the premise: the run drew both documents into the label file, and one was labelled",
                () -> assertThat(recorded).contains("recorded 1 label(s) about the seed set at " + seedSet
                        + ": 1 new, 0 changed, 0 unchanged"));
        claim("recording it says the other document is still blank",
                () -> assertThat(recorded).contains("; 1 document(s) are still blank"));
        claim("recording the same file again says one label is unchanged, none new, and the other document still blank",
                () -> assertThat(nothingNew).contains("recorded nothing new about the seed set at " + seedSet
                        + ": 1 label(s) unchanged, none new; 1 document(s) are still blank"));
        claim("a label file that is not there is refused, asking for the documents in the file to be labelled",
                () -> assertThat(noFile).contains("run vespera against the corpus first, label the documents in the"
                        + " file, then run this again."));
        claim("a labeller that stops is said not to have answered, the labels recorded before it stopped kept",
                () -> assertThat(stopped).contains(THE_LABELLER + " did not answer: " + WHY_IT_STOPPED
                        + ". Labels recorded before it stopped are kept; run the same command again."));
        claim("and a profile naming no seed folder leaves no seed set for the labels to be about",
                () -> assertThat(noSeedFolder).contains("no seed folder is named in the profile, so there is no seed"
                        + " set for these labels to be about. Nothing was recorded."));
    }

    /** What {@code command} wrote to the console and the log, and nothing written before it. */
    private static String linesOf(CapturedOutput output, Runnable command) {
        int before = output.getAll().length();
        command.run();
        return output.getAll().substring(before);
    }
}
