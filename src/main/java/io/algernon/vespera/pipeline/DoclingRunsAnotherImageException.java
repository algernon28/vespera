package io.algernon.vespera.pipeline;

/**
 * The document converter does not run the image {@code vespera.docling.image} names, or does not say
 * which image it runs (ADR-179 §3).
 *
 * <p>Every conversion is recorded under an identity that carries the configured image (ADR-147), so a
 * converter running another one would have its output cached under a name that is not its own. This is
 * thrown before the identity is composed, which is before any run is minted for the stage and before any
 * conversion is asked for.
 *
 * <p>Named for the fault rather than the comparison that noticed it (ADR-076). It carries no cause: the
 * two image names are the whole of what an operator needs, and its message says both.
 */
final class DoclingRunsAnotherImageException extends RuntimeException {

    /** The sidecar reports {@code reported}, and the configuration names {@code configured}. */
    static DoclingRunsAnotherImageException running(String reported, String configured) {
        return new DoclingRunsAnotherImageException("docling-serve is running `" + reported
                + "`, and `vespera.docling.image` names `" + configured + "`; every conversion is recorded under"
                + " the name configured, so none can be made until the two agree. If `" + reported
                + "` is the image you meant to run, set `VESPERA_DOCLING_IMAGE` to it; otherwise start the"
                + " sidecars again with the compose files that build `" + configured + "`");
    }

    /** The sidecar's {@code /version} carries no {@code vespera-image} entry at all. */
    static DoclingRunsAnotherImageException notReporting(String configured) {
        return new DoclingRunsAnotherImageException("docling-serve does not report which image it runs, and"
                + " `vespera.docling.image` names `" + configured + "`; every conversion is recorded under that"
                + " name, so none can be made until the sidecar says it is that image. A sidecar that does not"
                + " report its image was built from an older Containerfile than this checkout's, or is not"
                + " Vespera's image at all: start the sidecars again with `up -d --build`");
    }

    private DoclingRunsAnotherImageException(String message) {
        super(message);
    }
}
