package io.algernon.vespera;

import io.qameta.allure.Allure;
import io.qameta.allure.Allure.ThrowableRunnableVoid;
import org.opentest4j.AssertionFailedError;

/**
 * One report step per claim a test makes, and one readable failure when a claim does not hold.
 *
 * <p>Why this exists rather than {@code allure-assertj}: that adapter weaves AssertJ and derives
 * the steps itself, producing a parent step named {@code assert <the actual value>} with the
 * fluent calls beneath it — {@code assert 1234}, then {@code has size 1}. The shape says what was
 * executed, not what was being claimed, and the numbers in it are the test data with no indication
 * of where they came from. Naming the step here is the only way to make the report read as a list
 * of claims, so {@code allure-assertj} is deliberately not a dependency.
 *
 * <p>Lives in the root package on purpose. A test-support class in a package of its own would read
 * as a further module to {@code ApplicationModules}, which {@code ModuleBoundariesTest} would then
 * fail on.
 */
public final class TestSteps {

    private TestSteps() {
    }

    /**
     * Records {@code claim} as a report step and runs the assertion inside it. The step carries the
     * claim as its name; if the assertion fails, the claim is also prepended to the failure, so the
     * message says what was expected in words before it says which values differed.
     *
     * <p>AssertJ on its own reports only the comparison — {@code Expecting empty but was: ["x"]}
     * says nothing about why empty was the right answer, and it is the message that reaches the
     * console, CI and the report's failure banner. Prepending the claim is what makes it legible,
     * and it costs nothing at the call site because the claim is already there.
     *
     * <p>The wrapping happens inside the step, so the step's own error carries the claim too. That
     * repeats the step name, which looks redundant with the step expanded — but the report also
     * shows that error on its own, where {@code expected: 1235L but was: 1234L} with nothing
     * attached to it means nothing at all. A message has to stand up alone.
     *
     * <p>Where AssertJ threw an {@link AssertionFailedError} carrying an expected and an actual
     * value, a new one is built with the same pair, so an IDE can still offer its side-by-side
     * comparison. The original is kept as the cause either way.
     *
     * @param claim what the assertion establishes, phrased so a reader needs nothing else —
     *              including where any number in it comes from
     * @param check the assertion itself
     */
    public static void claim(final String claim, final ThrowableRunnableVoid check) {
        Allure.step(claim, () -> {
            try {
                check.run();
            } catch (AssertionError failure) {
                throw described(claim, failure);
            }
        });
    }

    private static AssertionError described(final String claim, final AssertionError failure) {
        final String message = claim + System.lineSeparator() + System.lineSeparator() + failure.getMessage();
        if (failure instanceof AssertionFailedError typed
                && typed.isExpectedDefined()
                && typed.isActualDefined()) {
            return new AssertionFailedError(
                    message, typed.getExpected().getValue(), typed.getActual().getValue(), failure);
        }
        return new AssertionError(message, failure);
    }
}
