package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.LabelQuestion;
import io.algernon.vespera.embedding.RelevanceLabeller;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.SidecarVersionReport;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code vespera label --auto} reads each opening from the conversion on record under the extractor identity it composes
 * now, so under any other identity than the one the label file's run was converted under none of the run's openings can
 * be read, and a labeller left with none answers blank (ADR-332 §1). It is refused in one line before a question is
 * asked, even where the cache also holds a conversion under the identity composed now: that is not what the run scored.
 *
 * <p>The application's identity is one bean built once, and a jar makes one invocation; here a context makes several. So
 * a bean of this class stands over it, built again each time it is asked for, through the application's own composition
 * and from what configuration names at that moment: the run here converts under one identity and the label under
 * another, as two invocations of the jar would. The labeller answers as {@code OllamaRelevanceLabeller} does, blank where
 * it is given no opening, so that a question put with none records nothing here either.
 *
 * <p>Each test runs over a corpus of its own, because an answer is kept by the path below the root and the seed set.
 */
@CascadeSliceTest
@Import({
    SeedScriptedExtractionBeans.class,
    AutoLabelling.class,
    ALabelByTheLocalModelUnderAnotherExtractorIsRefusedInvocationTest.Beans.class
})
@ExtendWith(OutputCaptureExtension.class)
@Epic("Relevance")
@Feature("Local labelling")
@Link(name = "ADR-332", url = Adr.THE_LOCAL_MODEL_LABELS_ONLY_UNDER_THE_EXTRACTOR_ITS_RUN_WAS_CONVERTED_UNDER, type = "adr")
class ALabelByTheLocalModelUnderAnotherExtractorIsRefusedInvocationTest {

    /** The OCR preset the run converts under. */
    private static final String THE_RUNS_OCR_PRESET = "rapidocr";

    /** Another preset, one a run could be made under. */
    private static final String ANOTHER_OCR_PRESET = "easyocr";

    /** A preset no run can be made under, which composes an identity all the same. */
    private static final String A_REFUSED_OCR_PRESET = "auto";

    /** The image the run converts under. */
    private static final String THE_RUNS_IMAGE = "vespera/the-image-of-the-run:stub";

    /** Another image, the same presets being named. */
    private static final String ANOTHER_IMAGE = "vespera/another-image:stub";

    /** What the label file says of a document nobody has answered for. */
    private static final String UNANSWERED = "relevant: null";

    /** What a person typing an answer writes in its place. */
    private static final String ANSWERED = "relevant: true";

    /** What a stack frame looks like in the output, which a refusal in one line does not print. */
    private static final String A_STACK_FRAME = "\tat ";

    private static volatile String ocrPreset = THE_RUNS_OCR_PRESET;

    private static volatile String image = THE_RUNS_IMAGE;

    /** Every question the labeller was put, in the order asked. */
    private static final List<LabelQuestion> ASKED = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class Beans {

        /** Answers as the local model does: blank where there is no opening to read. */
        @Bean
        RelevanceLabeller theLabellerAsked() {
            return new RelevanceLabeller() {
                @Override
                public String identity() {
                    return "scripted-labeller";
                }

                @Override
                public Optional<String> refusal() {
                    return Optional.empty();
                }

                @Override
                public Optional<Boolean> answer(LabelQuestion question) {
                    ASKED.add(question);
                    return question.opening().map(opening -> true);
                }
            };
        }

        /** The identity as configuration names it at the moment it is asked for, over the application's one bean. */
        @Bean
        @Primary
        @Scope(BeanDefinition.SCOPE_PROTOTYPE)
        ExtractorIdentity theIdentityConfigurationNamesNow() {
            return composedUnder(ocrPreset, image);
        }
    }

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path seeds;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.docling.ocr-preset", () -> ocrPreset);
        registry.add("vespera.docling.image", () -> image);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private RelevanceLabels relevanceLabels;

    @BeforeEach
    void theRunsIdentityAndNothingAsked() {
        ocrPreset = THE_RUNS_OCR_PRESET;
        image = THE_RUNS_IMAGE;
        ASKED.clear();
    }

    /** One rule, whatever changed: each row is a change of configuration, and each composes another identity. */
    static Stream<Arguments> whatChangedSinceTheRun() {
        return Stream.of(
                Arguments.of("another OCR preset", ANOTHER_OCR_PRESET, THE_RUNS_IMAGE),
                Arguments.of("an OCR preset no run can be made under", A_REFUSED_OCR_PRESET, THE_RUNS_IMAGE),
                Arguments.of("another Docling image", THE_RUNS_OCR_PRESET, ANOTHER_IMAGE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("whatChangedSinceTheRun")
    @Story("The local model answers only from the conversions its run was made under")
    @DisplayName("Labelling by the local model under another converter than the run's is refused in one line, naming both, and nothing is asked")
    void underAnotherIdentityItIsRefusedAndNothingIsAsked(
            String whatChanged, String presetNow, String imageNow, @TempDir Path corpus, CapturedOutput output)
            throws IOException {
        aScoredCorpusIn(corpus);
        Path labelFile = workingDirectory.resolve(RelevanceLabelFile.FILE_NAME);
        String theFileBefore = Files.readString(labelFile, StandardCharsets.UTF_8);
        int recordedBefore = recorded();
        String then = composedUnder(THE_RUNS_OCR_PRESET, THE_RUNS_IMAGE).value();
        String now = composedUnder(presetNow, imageNow).value();
        ocrPreset = presetNow;
        image = imageNow;
        int errBefore = output.getErr().length();
        int allBefore = output.getAll().length();

        cli.run("label", "--auto");

        String expected = theLine(then, now);
        claim("the invocation ends non-zero, under " + whatChanged,
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim("standard error says one line, naming first the identity the run was converted under and then the one"
                        + " composed now, and that nothing was asked",
                () -> assertThat(output.getErr().substring(errBefore).lines().filter(expected::equals).toList())
                        .hasSize(1));
        claim("and no stack trace stands beside it",
                () -> assertThat(output.getAll().substring(allBefore)).doesNotContain(A_STACK_FRAME));
        claim("the labeller was asked nothing", () -> assertThat(ASKED).isEmpty());
        claim("no answer was recorded", () -> assertThat(recorded()).isEqualTo(recordedBefore));
        claim("the label file is as the run wrote it, byte for byte",
                () -> assertThat(labelFile).usingCharset(StandardCharsets.UTF_8).hasContent(theFileBefore));
        claim("and no floor was set",
                () -> assertThat(profileStore.load().relevanceScoreFloor().isSet()).isFalse());
    }

    @Test
    @Story("The local model answers only from the conversions its run was made under")
    @DisplayName("Under the converter the run was made under, every question is put with its opening and answered")
    void underTheSameIdentityEveryQuestionIsAnswered(@TempDir Path corpus) throws IOException {
        aScoredCorpusIn(corpus);
        int questions = unansweredInTheLabelFile();
        int recordedBefore = recorded();

        cli.run("label", "--auto");

        claim("the invocation ends well", () -> assertThat(cli.getExitCode()).isZero());
        claim("the sample asks something, so that what follows can tell",
                () -> assertThat(questions).isPositive());
        claim("each of the " + questions + " questions the file left unanswered was put, with the opening read from"
                        + " the conversion on record",
                () -> {
                    assertThat(ASKED).hasSize(questions);
                    assertThat(ASKED).allSatisfy(question -> assertThat(question.opening()).isPresent());
                });
        claim("and each answer was recorded",
                () -> assertThat(recorded()).isEqualTo(recordedBefore + questions));
    }

    @Test
    @Story("The local model answers only from the conversions its run was made under")
    @DisplayName("Recording answers a person typed reads no conversion, and goes on under another converter than the run's")
    void aPlainLabelUnderAnotherIdentityGoesOn(@TempDir Path corpus) throws IOException {
        aScoredCorpusIn(corpus);
        int questions = unansweredInTheLabelFile();
        int recordedBefore = recorded();
        Path labelFile = workingDirectory.resolve(RelevanceLabelFile.FILE_NAME);
        Files.writeString(labelFile, Files.readString(labelFile, StandardCharsets.UTF_8).replace(UNANSWERED, ANSWERED),
                StandardCharsets.UTF_8);
        ocrPreset = ANOTHER_OCR_PRESET;

        cli.run("label");

        claim("the invocation ends well", () -> assertThat(cli.getExitCode()).isZero());
        claim("each of the " + questions + " answers typed was recorded",
                () -> {
                    assertThat(questions).isPositive();
                    assertThat(recorded()).isEqualTo(recordedBefore + questions);
                });
    }

    /** The record's line, word for word, with the two identities in its order. */
    private static String theLine(String then, String now) {
        return "vespera label --auto recorded nothing: the label file's run was converted under the extractor identity "
                + then + ", and this invocation composes " + now + ", so none of the run's openings can be read under"
                + " it. Nothing was asked. Name what the run was converted under, or run vespera under this one.";
    }

    /** Through the application's own composition, for a sidecar running the image named. */
    private static ExtractorIdentity composedUnder(String preset, String imageNamed) {
        DoclingClient sidecarRunningTheImage = new DoclingClient("unused", preset) {
            @Override
            public Map<String, String> version() {
                return SidecarVersionReport.runningImage(imageNamed);
            }
        };
        return new ExtractionJobConfiguration().extractorIdentity(sidecarRunningTheImage, imageNamed);
    }

    /** Every gate open and the job run over a corpus root of its own, under the identity the run converts under. */
    private void aScoredCorpusIn(Path corpus) throws IOException {
        Files.writeString(corpus.resolve(corpus.getFileName() + ".txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", corpus.toString());
    }

    private int recorded() {
        return relevanceLabels.forSeedSet(Walk.canonicalRoot(seeds).toString()).size();
    }

    private static int unansweredInTheLabelFile() throws IOException {
        return (int) Files.readAllLines(workingDirectory.resolve(RelevanceLabelFile.FILE_NAME), StandardCharsets.UTF_8)
                .stream()
                .filter(line -> line.contains(UNANSWERED))
                .count();
    }
}
