package io.algernon.vespera.engine.pipeline;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ContainerHealth} whose answers a test sets (ADR-323): each container answers or not, and docling-serve may
 * fail a number of checks before it answers. It counts every question, so a test can claim nothing was asked.
 */
final class ScriptedContainerHealth implements ContainerHealth {

    private volatile boolean doclingAnswers;
    private volatile boolean ollamaAnswers;
    private volatile int doclingFailsFirst;
    private final AtomicInteger doclingAsked = new AtomicInteger();
    private final AtomicInteger ollamaAsked = new AtomicInteger();

    static ScriptedContainerHealth bothAnswering() {
        ScriptedContainerHealth health = new ScriptedContainerHealth();
        health.answering(true, true);
        return health;
    }

    static ScriptedContainerHealth neitherAnswering() {
        return new ScriptedContainerHealth();
    }

    /** Ollama answers throughout; docling-serve fails its first {@code checks} checks and answers every one after. */
    static ScriptedContainerHealth doclingAnsweringAfter(int checks) {
        ScriptedContainerHealth health = bothAnswering();
        health.doclingFailsFirst = checks;
        return health;
    }

    /** From now on, docling-serve answers as {@code docling} says and Ollama as {@code ollama} says. */
    void answering(boolean docling, boolean ollama) {
        doclingAnswers = docling;
        ollamaAnswers = ollama;
    }

    /** From now on, both answer once docling-serve has failed its next {@code checks} checks. */
    void answeringAfter(int checks) {
        doclingFailsFirst = doclingAsked.get() + checks;
        answering(true, true);
    }

    int questionsAsked() {
        return doclingAsked.get() + ollamaAsked.get();
    }

    /** The questions docling-serve was asked, and Ollama, each apart (ADR-430 §10). */
    int doclingQuestions() {
        return doclingAsked.get();
    }

    int ollamaQuestions() {
        return ollamaAsked.get();
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
