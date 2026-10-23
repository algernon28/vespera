package io.algernon.vespera.pipeline;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link SidecarHealth} whose answers a test sets (ADR-322): each sidecar answers or not, and docling-serve may
 * fail a number of checks before it answers. It counts every question, so a test can claim nothing was asked.
 */
final class ScriptedSidecarHealth implements SidecarHealth {

    private volatile boolean doclingAnswers;
    private volatile boolean ollamaAnswers;
    private volatile int doclingFailsFirst;
    private final AtomicInteger doclingAsked = new AtomicInteger();
    private final AtomicInteger ollamaAsked = new AtomicInteger();

    static ScriptedSidecarHealth bothAnswering() {
        ScriptedSidecarHealth health = new ScriptedSidecarHealth();
        health.answering(true, true);
        return health;
    }

    static ScriptedSidecarHealth neitherAnswering() {
        return new ScriptedSidecarHealth();
    }

    /** Ollama answers throughout; docling-serve fails its first {@code checks} checks and answers every one after. */
    static ScriptedSidecarHealth doclingAnsweringAfter(int checks) {
        ScriptedSidecarHealth health = bothAnswering();
        health.doclingFailsFirst = checks;
        return health;
    }

    /** From now on, docling-serve answers as {@code docling} says and Ollama as {@code ollama} says. */
    void answering(boolean docling, boolean ollama) {
        doclingAnswers = docling;
        ollamaAnswers = ollama;
    }

    int questionsAsked() {
        return doclingAsked.get() + ollamaAsked.get();
    }

    @Override
    public boolean doclingAnswers() {
        return doclingAsked.incrementAndGet() > doclingFailsFirst && doclingAnswers;
    }

    @Override
    public boolean ollamaAnswers() {
        ollamaAsked.incrementAndGet();
        return ollamaAnswers;
    }
}
