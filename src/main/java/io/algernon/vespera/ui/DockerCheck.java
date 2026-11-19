package io.algernon.vespera.ui;

/**
 * What the window's check found (ADR-430 §2, §3, §12): the five rows of the header's list, each answered, not answered,
 * or not asked. The Docker daemon is not asked where neither container is on this machine, and a model is not asked
 * while Ollama does not answer or where no source names it.
 *
 * @param daemon the Docker daemon
 * @param docling the Docling container
 * @param ollama the Ollama container
 * @param embeddingModel whether Ollama has the embedding model in force
 * @param generativeModel whether Ollama has the generative model in force
 */
record DockerCheck(Mark daemon, Container docling, Container ollama, Mark embeddingModel, Mark generativeModel) {

    /** What a row reads: {@code OK}, {@code KO}, or a dash where it is not asked. */
    enum Mark { OK, KO, NOT_ASKED }

    /** Where a container was found: this machine, another, or a host that cannot be resolved (ADR-430 §10). */
    enum Where { THIS_MACHINE, ANOTHER_MACHINE, NOT_RESOLVED }

    /**
     * One container: whether it answers, where it was found, and the address it was asked at.
     *
     * @param mark {@code OK} where it answers, {@code KO} where not, and {@code KO} unasked where its host cannot be resolved
     * @param where where its host resolves to
     * @param address its base URL as written
     */
    record Container(Mark mark, Where where, String address) {
    }
}
