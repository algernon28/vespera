package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.algernon.vespera.Adr;
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
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Where the topics are found (ADR-399 §6): at the head of the relevance-scoring step, stage 5d, after the resident
 * seed vectors are read and before the first survivor is scored, from those vectors (§1) and with no call to the
 * embedder. A sibling of {@link RelevanceScoringInvocationTest}, on its fixture: one corpus document, one seed, the
 * scripted extraction and embedding beans, and every gate through stage 5 open.
 *
 * <p>Nothing stores the topics yet, that being #832's, so what is read is the step's own line saying how many it
 * found, among the lines around it: each line the application logs is kept, in order, with how many calls {@link
 * EmbeddingScriptedBeans}' embedding double had answered when it was logged. The line counting the first survivor
 * scored is logged once that survivor's score is recorded, and the total being one, it is logged for it.
 *
 * <p>It fails where the topics are found after the survivors are scored, the topic line then following the
 * survivors' line, and where finding them calls the embedder, the count then having moved between the step's first
 * line and its topic line.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Topics")
@Issue("826")
@Link(name = "ADR-399", url = Adr.TOPICS_ARE_COMMUNITIES_OF_THE_SEED_PASSAGES, type = "adr")
class TheTopicsAreFoundAtTheHeadOfRelevanceScoringInvocationTest {

    /** The logger every operator-facing line in this application is written through. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    /** What every line of the relevance-scoring step opens with, its counters' labels included. */
    private static final String THE_STEP = "Stage 5d (relevance scoring";

    /** The seed is one short paragraph, one chunk, so the seed set is one passage, and one passage is one topic. */
    private static final int ONE_TOPIC = 1;

    /** The one seed's vectors read, the counter's line at its one item: the last of the reads the topics come after. */
    private static final String THE_SEED_VECTORS_READ = "Stage 5d (relevance scoring, seed vectors read): 1 of 1 (100%)";

    /** The step's line saying how many topics it found. */
    private static final String THE_TOPICS_FOUND =
            "Stage 5d (relevance scoring) found " + ONE_TOPIC + " topic(s) across the seeds' passages";

    /** The one survivor scored, the counter's line at its one item. */
    private static final String THE_FIRST_SURVIVOR_SCORED =
            "Stage 5d (relevance scoring, corpus survivors): 1 of 1 (100%)";

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

    /** A line the application logged, and how many calls the embedding double had answered by then. */
    private record Logged(String line, int embeddingCallsMade) {}

    /** Keeps each line as it is logged, with the embedding double's count read at that moment. */
    private static final class Timeline extends AppenderBase<ILoggingEvent> {

        private final List<Logged> logged = Collections.synchronizedList(new ArrayList<>());

        @Override
        protected void append(ILoggingEvent event) {
            logged.add(new Logged(event.getFormattedMessage(), EmbeddingScriptedBeans.embeddingCallsMade()));
        }

        List<Logged> logged() {
            synchronized (logged) {
                return List.copyOf(logged);
            }
        }
    }

    private Timeline timeline;
    private ch.qos.logback.classic.Logger applicationLogger;

    @BeforeEach
    void keepTheTimeline() {
        timeline = new Timeline();
        timeline.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(timeline);
    }

    @AfterEach
    void releaseTheTimeline() {
        applicationLogger.detachAppender(timeline);
        timeline.stop();
    }

    @Test
    @Story("The topics are found before any document is scored")
    @DisplayName("The topics are found after the seeds' stored vectors are read and before the first survivor is"
            + " scored, with no call to the embedder")
    void areFoundFromTheResidentSeedVectorsBeforeTheFirstSurvivorIsScored(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());

        List<Logged> logged = timeline.logged();
        claim("the invocation ended well", () -> assertThat(cli.getExitCode()).isZero());
        claim("the step said, once, that it found " + ONE_TOPIC + " topic over the one seed's one passage; then that"
                        + " the seed's stored vectors were read, that the topics were found and that the one survivor"
                        + " was scored came in that order",
                () -> assertThat(logged.stream()
                                .map(Logged::line)
                                .filter(line -> line.equals(THE_SEED_VECTORS_READ)
                                        || line.equals(THE_TOPICS_FOUND)
                                        || line.equals(THE_FIRST_SURVIVOR_SCORED))
                                .toList())
                        .containsExactly(THE_SEED_VECTORS_READ, THE_TOPICS_FOUND, THE_FIRST_SURVIVOR_SCORED));
        claim("the embedder answered no call between the step's first line and the line saying the topics were found",
                () -> assertThat(callsMadeBy(logged, THE_TOPICS_FOUND))
                        .isEqualTo(logged.stream()
                                .filter(entry -> entry.line().startsWith(THE_STEP))
                                .findFirst()
                                .orElseThrow()
                                .embeddingCallsMade()));
    }

    /** How many calls the embedding double had answered when {@code line} was logged. */
    private static int callsMadeBy(List<Logged> logged, String line) {
        return logged.stream()
                .filter(entry -> entry.line().equals(line))
                .findFirst()
                .orElseThrow()
                .embeddingCallsMade();
    }
}
