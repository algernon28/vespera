package io.algernon.vespera.engine.pipeline;

import org.opentest4j.TestAbortedException;

/**
 * Work a whole test class shares, done the first time a test asks for it and never again: the value it
 * produced, or the failure it ended in, is handed to every test that asks afterwards (#678).
 *
 * <p><b>Why not a {@code @BeforeAll}.</b> The work is an invocation through the Spring context, and the
 * context of a full-run class is built from a static {@code @TempDir} that JUnit sets only once the class's
 * first test instance exists; a static method run before that has no context to invoke. So a test asks for
 * the work from its own body or its {@code @BeforeEach}, and the first to ask does it.
 *
 * <p><b>A failure is kept, not retried.</b> Doing the work again for the next test would run a second
 * invocation over the same folders, and every test after the first would then be about that second
 * invocation instead of the one it says it reads. So each test that asks after a failure fails with it,
 * naming it as the cause, rather than meeting a missing value.
 *
 * <p>Holds no claim: a claim made inside the shared work would show in the report under whichever test
 * happened to ask first. The work records what it saw, and each test makes its own claims about that.
 *
 * @param <T> what the work leaves for the tests to read
 */
final class Once<T> {

    /** The work itself, which may throw anything a test method may. */
    @FunctionalInterface
    interface Work<T> {
        T run() throws Exception;
    }

    private T value;
    private Throwable failure;
    private boolean done;

    /**
     * What the work left, doing it now if no test has asked before. Whatever the work threw is kept, an error as
     * much as an exception, so no later test meets a value that was never made. An assumption the work found false
     * is thrown again as it was, so every test that reads the work is aborted with it rather than failed.
     */
    T get(Work<T> work) {
        if (!done) {
            done = true;
            try {
                value = work.run();
            } catch (Throwable thrown) {
                failure = thrown;
            }
        }
        if (failure instanceof TestAbortedException aborted) {
            throw aborted;
        }
        if (failure != null) {
            throw new IllegalStateException("the work this class shares failed, so nothing it would have left can be read", failure);
        }
        return value;
    }
}
