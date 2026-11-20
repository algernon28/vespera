package io.algernon.vespera.ui;

/**
 * What the window's check found (ADR-430 §2, §3, §12): the five rows of the header's list, each answered, not answered,
 * or not asked. The Docker daemon is not asked where neither Docling nor Ollama is on this machine, and a model is not asked
 * while Ollama does not answer or where no source names it.
 *
 * @param daemon the Docker daemon
 * @param docling Docling, where it runs
 * @param ollama Ollama, where it runs
 * @param embeddingModel whether Ollama has the embedding model in force
 * @param generativeModel whether Ollama has the generative model in force
 */
record DockerCheck(Mark daemon, Service docling, Service ollama, Mark embeddingModel, Mark generativeModel) {

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
