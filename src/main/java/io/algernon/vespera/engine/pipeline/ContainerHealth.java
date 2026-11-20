package io.algernon.vespera.engine.pipeline;

/** Whether each container answers right now (ADR-323 §3): the question the starter asks before and after it acts. */
interface ContainerHealth {

    /** docling-serve answers {@code GET /health} with a 2xx. */
    boolean doclingAnswers();

    /** Ollama answers {@code GET /api/version} with a 2xx. */
    boolean ollamaAnswers();
}
