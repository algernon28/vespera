package io.algernon.vespera.pipeline;

/** Whether each sidecar answers right now (ADR-322 §3): the question the starter asks before and after it acts. */
interface SidecarHealth {

    /** docling-serve answers {@code GET /health} with a 2xx. */
    boolean doclingAnswers();

    /** Ollama answers {@code GET /api/version} with a 2xx. */
    boolean ollamaAnswers();
}
