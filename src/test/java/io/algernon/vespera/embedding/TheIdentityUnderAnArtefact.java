package io.algernon.vespera.embedding;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Asks {@link RelevanceDistribution} for the embedder identity the vectors carry under an embedding model's
 * name and the artefact a run names for it, the read ADR-228 owes (#488).
 *
 * <p>By reflection, because the tests that ask were written before the method: until the change that builds
 * ADR-228, {@code RelevanceDistribution} answers by the model's name alone, and a test naming the method of
 * two parameters would not compile. A method that is not there fails the test that asked, in words.
 */
final class TheIdentityUnderAnArtefact {

    private TheIdentityUnderAnArtefact() {}

    /** What {@code embedderIdentityFor(modelName, new ModelArtefact(digest, weightDtype))} answers. */
    @SuppressWarnings("unchecked")
    static Optional<String> read(RelevanceDistribution distribution, String modelName, String digest, String weightDtype) {
        Method read;
        try {
            read = RelevanceDistribution.class.getMethod("embedderIdentityFor", String.class, ModelArtefact.class);
        } catch (NoSuchMethodException notBuilt) {
            throw new AssertionError(
                    "RelevanceDistribution declares no public embedderIdentityFor(String, ModelArtefact): the"
                            + " identity is still read by the embedding model's name alone",
                    notBuilt);
        }
        try {
            return (Optional<String>) read.invoke(distribution, modelName, new ModelArtefact(digest, weightDtype));
        } catch (InvocationTargetException thrown) {
            throw new AssertionError("reading the identity threw", thrown.getCause());
        } catch (IllegalAccessException closed) {
            throw new AssertionError("embedderIdentityFor(String, ModelArtefact) is not public", closed);
        }
    }
}
