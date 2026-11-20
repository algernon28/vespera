package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.profile.Profile;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.objenesis.ObjenesisStd;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.Model.CommandSpec;

/**
 * What the command line says of the label file calls no relevance label an answer and no entry of the file a question
 * (ADR-424 §2, §3, §4): {@code vespera label}'s usage, the refusals of a label file that cannot be recorded, the closing
 * lines that ask for labels or count them, and the label section of {@code report.html}, each read where it is made,
 * with no application context. The lines {@code LabelIngestion} and {@code AutoLabelling} write need a ledger, and are
 * held by {@code TheLabelCommandsLinesCallNoLabelAnAnswerInvocationTest}.
 */
@Epic("Relevance")
@Feature("Labelling")
@Link(name = "ADR-424", url = Adr.NO_TEXT_FOR_THE_OPERATOR_CALLS_A_LABEL_AN_ANSWER, type = "adr")
@Issue("879")
class TheLabelCommandCallsNoLabelAnAnswerTest {

    /** Builds each command without its collaborators, as {@code TheControlPanelAccountsForEveryArgumentOfTheJarsCommandsTest} does. */
    private static final IFactory WITHOUT_ITS_COLLABORATORS = new IFactory() {
        private final ObjenesisStd objenesis = new ObjenesisStd();

        @Override
        public <K> K create(Class<K> type) throws Exception {
            return type.isAnnotationPresent(Command.class)
                    ? objenesis.newInstance(type)
                    : CommandLine.defaultFactory().create(type);
        }
    };

    /** Answer and question in any of their forms, as whole words. */
    private static final Pattern AN_ANSWER_OR_A_QUESTION = Pattern.compile("(?i)\\b(answer|question)\\w*");

    private static final String THE_LABEL_COMMANDS_DESCRIPTION = "Records the labels set in the relevance label file.";

    private static final String THE_AUTO_OPTIONS_DESCRIPTION = "Have the local model label the documents in the label"
            + " file, then set relevanceScoreFloor by the rule that loses no documentation (ADR-197). A label a person"
            + " set is never replaced.";

    /** The run a label file has to name to be read, and another one. */
    private static final String THE_RUN = "9f2c41ab";

    private static final String ANOTHER_RUN = "0011ffee";

    private static final String THE_EMBEDDER = "model=a-comparing-model;digest=abc;dtype=F16;dimension=384;instruction=none";

    /** The one entry of the label files written here, still blank. */
    private static final int ONE_DOCUMENT_STILL_WAITING = 1;

    /** The model resolved for an invocation, as {@code GenerationModel} would hand it over. */
    private static final String THE_GENERATION_MODEL = "a-generation-model:8b";

    /** No arrangement was written: the relevance floor is unset, so nothing could be. */
    private static final String NOTHING_ARRANGED = null;

    private static final boolean NO_APPROVAL_MATCHES = false;

    /** No label recorded against the seed set yet. */
    private static final int NO_LABEL_RECORDED = 0;

    /** One label recorded, for the singular. */
    private static final int ONE_LABEL_RECORDED = 1;

    /** The shipped sample, every document of it labelled. */
    private static final int SIXTY_LABELS_RECORDED = 60;

    private static final boolean THE_LABEL_FILE_WRITTEN = true;

    private static final boolean NO_LABEL_FILE_WRITTEN = false;

    /** The label section of the shipped template, whole: its id and keys as they were, its words labels. */
    private static final String THE_LABEL_SECTION = """
            <section data-section="answers">
            <h2>Labels recorded</h2>
            <p class="caption">The labels this invocation counted, by their words.</p>
            <div class="table-card" title="The labels this invocation counted, by their words.">
            <table><tr><th>Labels</th><th>Count</th></tr>
            {{#drawn.answers}}<tr><td>{{words}}</td><td class="count">{{count}}</td></tr>
            {{/drawn.answers}}</table></div>
            </section>
            """;

    /** The clause of the next invocation's line where the next invocation is a label. */
    private static final String LABELS_ARE_WANTED = "{{#drawn.next.answer}} Labels are wanted.{{/drawn.next.answer}}";

    @Test
    @Story("A relevance label is a label wherever the operator reads of it")
    @DisplayName("The label command's usage says it records the labels set in the label file, and the local model labels the documents")
    void theUsageSaysLabels() {
        CommandSpec label = new CommandLine(VesperaCommand.class, WITHOUT_ITS_COLLABORATORS)
                .getSubcommands().get("label").getCommandSpec();

        claim("the label command is described as recording the labels set in the relevance label file",
                () -> assertThat(String.join(" ", label.usageMessage().description()))
                        .isEqualTo(THE_LABEL_COMMANDS_DESCRIPTION));
        claim("and its option for the local model as having the model label the documents, never replacing a label a"
                        + " person set",
                () -> assertThat(String.join(" ", label.findOption("--auto").description()))
                        .isEqualTo(THE_AUTO_OPTIONS_DESCRIPTION));
    }

    @Test
    @Story("A relevance label is a label wherever the operator reads of it")
    @DisplayName("A label file naming no run, naming another run, or holding no label yet is refused in the words of labels")
    void aLabelFileRefusedSaysLabels() {
        String namingNoRun = reasonRefused(aFile("").replace("generatedUnderRun: \"\"\n", ""));
        String namingAnotherRun = reasonRefused(aFile(ANOTHER_RUN).replace("relevant: null", "relevant: true"));
        String holdingNoLabel = reasonRefused(aFile(THE_RUN));

        claim("a file naming no run is refused as one whose sample cannot be told, as it was labelled against",
                () -> assertThat(namingNoRun).isEqualTo("the label file names no run, so there is no way to tell which"
                        + " sample it was labelled against; regenerate it and label the new file"));
        claim("a file naming another run is refused as labels about another sample, naming both runs",
                () -> assertThat(namingAnotherRun).isEqualTo("the label file was generated under run " + ANOTHER_RUN
                        + ", but the current sample is run " + THE_RUN + "; labels about one sample are not labels"
                        + " about another, so nothing in it was recorded"));
        claim("a file whose " + ONE_DOCUMENT_STILL_WAITING + " document is still blank is refused as holding no label"
                        + " yet",
                () -> assertThat(holdingNoLabel).isEqualTo("the label file has no labels in it yet: "
                        + ONE_DOCUMENT_STILL_WAITING + " document(s) are still waiting, so nothing was recorded"));
    }

    @Test
    @Story("A relevance label is a label wherever the operator reads of it")
    @DisplayName("The closing line asks for the documents to be labelled, says when none were written, and counts the labels recorded")
    void theClosingLinesSayLabels() {
        Profile beforeTheFloor = ProfileFixture.profile()
                .seedFolder("/corpus/exemplars", "recorded by the operator")
                .boilerplateDocumentFrequencyFloor("0.4", "recorded by the operator")
                .embeddingModel("a-third-comparing-model", "recorded by the operator")
                .build();

        String labelsWanted = NextAction.line(beforeTheFloor, NO_LABEL_RECORDED, THE_LABEL_FILE_WRITTEN,
                NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES);
        String noneWritten = NextAction.line(beforeTheFloor, NO_LABEL_RECORDED, NO_LABEL_FILE_WRITTEN,
                NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES);
        String oneRecorded = NextAction.line(beforeTheFloor, ONE_LABEL_RECORDED, THE_LABEL_FILE_WRITTEN,
                NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES);
        String sixtyRecorded = NextAction.line(beforeTheFloor, SIXTY_LABELS_RECORDED, THE_LABEL_FILE_WRITTEN,
                NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES);

        claim("with the label file written and no label recorded, the line ends by asking for the documents in it to be"
                        + " labelled and then recorded",
                () -> assertThat(labelsWanted).endsWith(" Next: label the documents in relevance-labels.yaml --"
                        + " relevance-labelling.html shows each one and what your labels are for -- then record them"
                        + " with vespera label."));
        claim("with no label file written, the line says there is nothing to label yet",
                () -> assertThat(noneWritten).endsWith(" No documents to label were written this invocation, so there"
                        + " is nothing to label yet. Next: fix what the gated line above names, and run again."));
        claim("with " + ONE_LABEL_RECORDED + " label recorded, the line says one label is",
                () -> assertThat(oneRecorded).contains(", and 1 label is recorded"));
        claim("with " + SIXTY_LABELS_RECORDED + " labels recorded, the line says sixty labels are",
                () -> assertThat(sixtyRecorded).contains(", and 60 labels are recorded"));
        claim("and none of the four lines says answer or question, in any form",
                () -> assertThat(java.util.List.of(labelsWanted, noneWritten, oneRecorded, sixtyRecorded))
                        .noneMatch(line -> AN_ANSWER_OR_A_QUESTION.matcher(line).find()));
    }

    @Test
    @Story("A relevance label is a label wherever the operator reads of it")
    @DisplayName("The invocation report's section of a label command is headed Labels recorded, and its next line says labels are wanted")
    void theReportsLabelSectionSaysLabels() throws IOException {
        String template;
        try (InputStream in = ReportHtml.class.getResourceAsStream(ReportHtml.TEMPLATE_RESOURCE)) {
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }

        claim("the label section is headed Labels recorded, captioned and titled as the labels counted, its first column"
                        + " Labels, under the section id and keys it had",
                () -> assertThat(template).contains(THE_LABEL_SECTION));
        claim("and the line naming the next invocation says labels are wanted where they are",
                () -> assertThat(template).contains(LABELS_ARE_WANTED));
    }

    /** The reason {@code yaml} is refused for, read against {@link #THE_RUN}; empty where it is not refused. */
    private static String reasonRefused(String yaml) {
        return LabelFileReader.read(yaml, THE_RUN) instanceof LabelFileReader.Refused refused ? refused.reason() : "";
    }

    /** A label file naming {@code runId}, of one entry still blank. */
    private static String aFile(String runId) {
        return """
                generatedUnderRun: "%s"
                generatedUnderEmbedder: "%s"
                documents:
                - path: "reports/q1.pdf"
                  score: 0.42
                  band: 0
                  closestSeed: "seeds/exemplar.pdf"
                  relevant: null
                """
                .formatted(runId, THE_EMBEDDER);
    }
}
