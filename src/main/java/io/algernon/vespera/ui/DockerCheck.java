package io.algernon.vespera.ui;

import java.util.List;

/**
 * What the window's check found (ADR-430 §2, §3, §12, ADR-440 §6, §7, §8): the five rows of the header's list, each answered, not answered,
 * or not asked. The Docker daemon is not asked where neither Docling nor Ollama is on this machine, and a model is not asked
 * while Ollama does not answer or where no source names it.
 *
 * @param daemon the Docker daemon
 * @param docling Docling, where it runs
 * @param ollama Ollama, where it runs
 * @param embeddingModel whether Ollama has the embedding model in force
 * @param generativeModel whether Ollama has the generative model in force
 * @param doclingImage the image Docling's {@code GET /version} named where Docling answered, empty where none was kept
 *     (ADR-440 §6)
 * @param ollamaModels the names Ollama's {@code GET /api/tags} listed, in its order, none where Ollama did not answer
 *     (ADR-440 §7)
 */
record DockerCheck(Mark daemon, Service docling, Service ollama, Mark embeddingModel, Mark generativeModel,
        String doclingImage, List<String> ollamaModels) {

    /** A check that kept no image and no names (ADR-440 §8). */
    DockerCheck(Mark daemon, Service docling, Service ollama, Mark embeddingModel, Mark generativeModel) {
        this(daemon, docling, ollama, embeddingModel, generativeModel, "", List.of());
    }

    DockerCheck {
        doclingImage = doclingImage == null ? "" : doclingImage;
        ollamaModels = ollamaModels == null ? List.of() : List.copyOf(ollamaModels);
    }

    /** What a row reads: {@code OK}, {@code KO}, or a dash where it is not asked. */
    enum Mark { OK, KO, NOT_ASKED }

    /** Where Docling or Ollama was found: this machine, another, or a host that cannot be resolved (ADR-430 §10). */
    enum Where { THIS_MACHINE, ANOTHER_MACHINE, NOT_RESOLVED }

    /**
     * One of Docling and Ollama, wherever it runs: whether it answers, where it was found, and the address it was asked at.
     *
     * @param mark {@code OK} where it answers, {@code KO} where not, and {@code KO} unasked where its host cannot be resolved
     * @param where where its host resolves to
     * @param address its base URL as written
     */
    record Service(Mark mark, Where where, String address) {
    }
}
