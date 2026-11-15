package io.algernon.vespera.engine.pipeline;

/**
 * STAND-IN, to be deleted by the change that builds ADR-397, with {@link BesideTheJar}'s stand-in beside it: the
 * refusal of a start whose folder beside the jar cannot be written (ADR-397 §3), here only so that the tests compile.
 */
public final class BesideTheJarUnwritableException extends RuntimeException {

    BesideTheJarUnwritableException(String line, Throwable cause) {
        super(line, cause);
    }
}
