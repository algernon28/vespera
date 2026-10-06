package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.ModelArtefact;
import io.algernon.vespera.embedding.OllamaClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

/**
 * {@link SeedScriptedExtractionBeans}' sibling for gate 3's scoring step (#107): every chunk embeds to
 * a fixed-length vector, and the model the fixture names is always the one the runtime reports itself
 * serving. The embedding double never refuses, so nothing here exercises {@code ChunkEmbedder}'s
 * split-retry path — that behaviour has its own unit test against a fake that can refuse on purpose.
 *
 * <p><b>The serving runtime, on the other hand, can be told it has never pulled a model</b> (ADR-126,
 * #196). {@code OllamaClient.artefactOf} serves two callers — the embedder identity and stage 6b's
 * generator identity (ADR-110, ADR-114) — and ADR-114's second stop is a resolved name the engine has
 * never pulled. That stop was unreachable while this double answered for every name, which is the
 * defect #196 names. It is scripted by name rather than as a mode of the whole fixture: stage 5
 * composes an embedder identity through this same double, so a runtime that refused everything would
 * stop the cascade three stages before the one a 6b test is about. {@link #servesEveryModel()} is the
 * state it ships in, so nothing that does not script a refusal behaves any differently than before.
 *
 * <p><b>It can also be told a model is forwarded to a hosted service, or that it cannot say</b>
 * (ADR-202, #431). {@code OllamaClient.isRemote} is what {@code /api/show} says about a name, and the
 * check every call to Ollama passes asks it. Scripted by name for the reason above, and answering
 * "this machine" for every name nothing scripted, which is what a model pulled into the local daemon
 * answers. Every name it is asked about is kept, so a test can say whether the check was made and how
 * often. The embedding double counts its calls, so a test can say none was made.
 *
 * <p><b>A name it has never pulled is never pulled for {@code /api/show} either.</b> The real daemon
 * answers 404 there, and since ADR-202 the check asks {@code /api/show} before anything reads {@code
 * /api/tags}, so in production it is the check that meets an unpulled name first and ADR-114's stop in
 * {@code artefactOf} is no longer reached for it. The double answers both endpoints alike so a test of
 * that case runs the path production runs.
 *
 * <p>{@code @TestConfiguration} rather than {@code @Configuration}, for the reason {@link
 * StubbedExtractionBeans} documents at length: left plain, this sits inside the application's
 * component-scan package and would silently replace the real client in every {@code @SpringBootTest}.
 */
@TestConfiguration
class EmbeddingScriptedBeans {

    /** Every vector this fixture's embedding runtime produces has this many components. */
    private static final int DIMENSION = 8;

    /**
     * The manifest digest this fixture's runtime reports, standing in for a real {@code /api/tags}
     * answer.
     *
     * <p>Readable from outside because an identity minted by an invocation is claimed against it: the
     * only way to tell a digest that was read from the serving engine apart from one composed out of
     * whatever else was to hand is to know what the engine said.
     */
    static final String DIGEST = "d34db33f00000000000000000000000000000000000000000000000000000";

    /** The weight dtype this fixture's runtime reports beside the digest. */
    private static final String DTYPE = "F16";

    /**
     * The names this fixture's runtime has never pulled, and refuses to compose an identity from.
     *
     * <p>Held statically for the reason {@link GenerationScriptedBeans}' answers are: the bean is built
     * by the context, and a test cannot reach the instance before the invocation it is scripting.
     * Empty is the shipped state, so a class that scripts nothing sees the fixture it always saw.
     */
    private static final Set<String> NEVER_PULLED = new HashSet<>();

    /** The names this fixture's runtime reports as forwarded to a hosted service, as a cloud model is. */
    private static final Set<String> FORWARDED = new HashSet<>();

    /** The names this fixture's runtime cannot answer {@code /api/show} about at all. */
    private static final Set<String> UNANSWERED = new HashSet<>();

    /** Every name {@code /api/show} has been asked about since the scripts were last dropped, in order. */
    private static final List<String> SHOWN = new ArrayList<>();

    /** How many calls have reached the embedding double since the scripts were last dropped. */
    private static int embeddingCallsMade;

    /**
     * Scripts this fixture's runtime as never having pulled {@code modelName}, so an identity composed
     * around it is refused rather than invented.
     */
    static void hasNeverPulled(String modelName) {
        NEVER_PULLED.add(modelName);
    }

    /**
     * Scripts this fixture's runtime as forwarding {@code modelName} to a hosted service: its {@code
     * /api/show} answer carries {@code remote_host} and {@code remote_model}, whatever the name says.
     */
    static void forwardsToAHostedService(String modelName) {
        FORWARDED.add(modelName);
    }

    /**
     * Scripts this fixture's runtime as unable to answer {@code /api/show} about {@code modelName}, the
     * way a daemon that is down or answers 500 cannot.
     */
    static void cannotSayWhereItRuns(String modelName) {
        UNANSWERED.add(modelName);
    }

    /** Every name {@code /api/show} has been asked about since the scripts were last dropped. */
    static List<String> shownModels() {
        return List.copyOf(SHOWN);
    }

    /** How many calls have reached the embedding double since the scripts were last dropped. */
    static int embeddingCallsMade() {
        return embeddingCallsMade;
    }

    /**
     * Drops every scripted refusal and every count, leaving a runtime that answers for whatever it is
     * asked about and serves every model on this machine.
     *
     * <p>Called from {@code @BeforeEach} as well as {@code @AfterEach} by whoever scripts one. An
     * {@code @AfterEach} alone leaks into the next class when a method dies before reaching it, and the
     * order classes run in is not fixed.
     */
    static void servesEveryModel() {
        NEVER_PULLED.clear();
        FORWARDED.clear();
        UNANSWERED.clear();
        SHOWN.clear();
        embeddingCallsMade = 0;
    }

    @Bean
    EmbeddingModel embeddingModel() {
        return new FixedDimensionEmbeddingModel(DIMENSION);
    }

    /**
     * Never reached over HTTP; the fixed digest and dtype are what an instrument identity is composed
     * from, unless a test has said this runtime never pulled the name being asked about.
     *
     * <p>The refusal is the shape the real client's is, down to the wording, because what is under test
     * is that the caller is stopped by it — a double that refused in some other shape would pin the
     * stop and let the message it is stopped by drift.
     */
    @Bean
    OllamaClient ollamaClient() {
        return new OllamaClient("unused") {
            @Override
            public ModelArtefact artefactOf(String modelName) {
                if (NEVER_PULLED.contains(modelName)) {
                    throw new IllegalStateException("the runtime serves no model named " + modelName
                            + ", so there is nothing to compose an identity from");
                }
                return new ModelArtefact(DIGEST, DTYPE);
            }

            /**
             * What {@code /api/show} says about {@code modelName}. A name this runtime has never pulled
             * is answered as the real daemon answers it, with 404 and {@code model '<name>' not found},
             * raised as the {@code RestClient} behind the real client raises it, so the check meets
             * the never-pulled case in the shape production gives it.
             */
            @Override
            public boolean isRemote(String modelName) {
                SHOWN.add(modelName);
                if (NEVER_PULLED.contains(modelName)) {
                    throw HttpClientErrorException.create(
                            HttpStatus.NOT_FOUND,
                            "Not Found",
                            HttpHeaders.EMPTY,
                            ("{\"error\":\"model '" + modelName + "' not found\"}").getBytes(StandardCharsets.UTF_8),
                            StandardCharsets.UTF_8);
                }
                if (UNANSWERED.contains(modelName)) {
                    throw new IllegalStateException("the runtime answered /api/show for " + modelName
                            + " with a server error");
                }
                return FORWARDED.contains(modelName);
            }
        };
    }

    /** Embeds any text to the same fixed-length vector of ones, never refusing, and counts each call. */
    private static final class FixedDimensionEmbeddingModel implements EmbeddingModel {

        private final int dimension;

        FixedDimensionEmbeddingModel(int dimension) {
            this.dimension = dimension;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            embeddingCallsMade++;
            float[] vector = new float[dimension];
            Arrays.fill(vector, 1f);
            return new EmbeddingResponse(List.of(new Embedding(vector, 0)), new EmbeddingResponseMetadata());
        }

        @Override
        public float[] embed(Document document) {
            throw new UnsupportedOperationException("not used by this fixture");
        }
    }
}
