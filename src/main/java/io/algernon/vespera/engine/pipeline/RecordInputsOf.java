package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.deliverable.ListedSurvivor;
import io.algernon.vespera.engine.deliverable.RecordInputs;
import io.algernon.vespera.engine.embedding.ModelArtefact;
import io.algernon.vespera.engine.embedding.RelevanceDistribution;
import io.algernon.vespera.engine.embedding.StoredVectors;
import io.algernon.vespera.engine.embedding.WritingEmbedder;
import io.algernon.vespera.engine.extraction.Chunk;
import io.algernon.vespera.engine.extraction.ChunkingRule;
import io.algernon.vespera.engine.extraction.HybridChunker;
import io.algernon.vespera.engine.extraction.StoredChunks;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * What composes the retrieval records beyond the arrangement (ADR-434 §4, §7), over {@code extraction} and {@code
 * embedding}: the scoring run's embedder identity, the vector of a writing, and a member's passages with their stored
 * vectors. It holds the rule of no stage, and decides nothing: the identities are the ones the scoring run read.
 *
 * <p>The embedding model's artefact is asked of Ollama once a drawing, before the first writing that is embedded
 * (ADR-434 §7).
 */
@Component
class RecordInputsOf {

    private final StageRuns stageRuns;
    private final RelevanceDistribution relevanceDistribution;
    private final HybridChunker hybridChunker;
    private final StoredChunks storedChunks;
    private final StoredVectors storedVectors;
    private final WritingEmbedder writingEmbedder;

    RecordInputsOf(
            StageRuns stageRuns,
            RelevanceDistribution relevanceDistribution,
            HybridChunker hybridChunker,
            StoredChunks storedChunks,
            StoredVectors storedVectors,
            WritingEmbedder writingEmbedder) {
        this.stageRuns = stageRuns;
        this.relevanceDistribution = relevanceDistribution;
        this.hybridChunker = hybridChunker;
        this.storedChunks = storedChunks;
        this.storedVectors = storedVectors;
        this.writingEmbedder = writingEmbedder;
    }

    /** The inputs of one drawing. */
    RecordInputs forOneDrawing() {
        return new RecordInputs() {
            private String identity;
            private boolean artefactAsked;

            @Override
            public String embedderIdentity() {
                if (identity == null) {
                    String model = stageRuns.embeddingModelName();
                    identity = relevanceDistribution
                            .embedderIdentityFor(model, stageRuns.embeddingModelArtefact())
                            .orElseThrow(() -> new IllegalStateException("no vector is stored under the scoring run's"
                                    + " embedding model " + model + ", so no record has a vector to carry"));
                }
                return identity;
            }

            @Override
            public float[] vectorOf(String writing) {
                String embedder = embedderIdentity();
                return writingEmbedder.kept(writing, embedder).orElseGet(() -> {
                    String model = stageRuns.embeddingModelName();
                    if (!artefactAsked) {
                        ModelArtefact scoring = stageRuns.embeddingModelArtefact();
                        writingEmbedder.requireArtefact(model, scoring);
                        artefactAsked = true;
                    }
                    return writingEmbedder.embedAndKeep(writing, model, embedder);
                });
            }

            @Override
            public void passagesOf(ListedSurvivor member, PassageConsumer passages) throws IOException {
                String embedder = embedderIdentity();
                String chunker = hybridChunker.identity();
                String rule = ChunkingRule.DEFAULT.identity().value();
                List<Chunk> chunks = storedChunks.of(member.contentHash(), chunker, rule);
                List<float[]> vectors = storedVectors.of(member.contentHash(), chunker, rule, embedder);
                if (vectors.size() < chunks.size()) {
                    throw new IllegalStateException("occurrence " + member.occurrence() + " has " + chunks.size()
                            + " chunks and " + vectors.size() + " stored vectors under the scoring run's identity");
                }
                for (int at = 0; at < chunks.size(); at++) {
                    Chunk chunk = chunks.get(at);
                    passages.accept(chunk.ordinal(), chunk.text(), vectors.get(at));
                }
            }
        };
    }
}
