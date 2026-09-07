package io.algernon.vespera.embedding;

/**
 * The whole embedder a stored vector was produced by (ADR-084), composed from what the runtime
 * reports plus what the client sends and never from where it is served — ADR-090's rule, one
 * instrument along (ADR-091).
 *
 * <p>Each part answers a way two vectors can be incomparable under one model name:
 *
 * <ul>
 *   <li>{@code modelName} — as the profile resolves it, tag included, since the tag is part of what
 *       was asked for.
 *   <li>{@code artefactDigest} — the <em>manifest</em> digest {@code /api/tags} reports, over the
 *       layers and the modelfile. It covers strictly more than the weights, which is what makes it
 *       satisfy ADR-084's purpose; a mutable tag re-pulled after upstream republishes therefore
 *       mints a new identity and re-embeds the corpus, correctly and expensively.
 *   <li>{@code weightDtype} — recorded beside the digest so a reader can tell a quantization change
 *       from a modelfile-only one without diffing manifests. It reads {@code F16} for a model nobody
 *       quantized, which is why it is named a dtype rather than a quantization.
 *   <li>{@code outputDimension} — <b>the value sent</b>, which the caller has verified against the
 *       returned vector's length. Reported metadata carries only the model's native dimension, so it
 *       would record 4096 for a run that asked for 1024.
 *   <li>{@code instruction} — {@code null} exactly when none was supplied, rendering the explicit
 *       sentinel {@code none}; supplied text renders under {@code text:} instead. The two spaces are
 *       separate so that an instruction whose text is literally {@code none} cannot compose the same
 *       value as having supplied nothing — the one collision a bare sentinel cannot survive, and it
 *       sits in a primary key. An empty instruction is a value, not an absence (ADR-084), and so
 *       renders {@code text:} rather than the sentinel.
 * </ul>
 *
 * <p>Every part but the instruction refuses a blank, because a blank is what a DTO that silently
 * dropped a field composes. That is the one place silence is unacceptable here: an identity with a
 * hole where a digest belongs files two models' vectors under one name, and nothing downstream can
 * tell afterwards.
 */
public record EmbedderIdentity(
        String modelName, String artefactDigest, String weightDtype, int outputDimension, String instruction) {

    /** What the value reads where no instruction was supplied at all, distinct from an empty one. */
    static final String NO_INSTRUCTION = "none";

    /** What supplied instruction text is rendered under, so no text can impersonate the sentinel. */
    private static final String SUPPLIED_INSTRUCTION = "text:";

    public EmbedderIdentity {
        requireStated("model name", modelName);
        requireStated("artefact digest", artefactDigest);
        requireStated("weight dtype", weightDtype);
        if (outputDimension < 1) {
            throw new IllegalArgumentException(
                    "an embedder produces vectors of at least one component, not " + outputDimension);
        }
    }

    /** The identity of an embedder asked for no instruction at all. */
    public static EmbedderIdentity withoutInstruction(
            String modelName, String artefactDigest, String weightDtype, int outputDimension) {
        return new EmbedderIdentity(modelName, artefactDigest, weightDtype, outputDimension, null);
    }

    /** The identity of an embedder asked for {@code instruction} — possibly empty, which is a value. */
    public static EmbedderIdentity withInstruction(
            String modelName, String artefactDigest, String weightDtype, int outputDimension, String instruction) {
        if (instruction == null) {
            throw new IllegalArgumentException(
                    "an absent instruction is withoutInstruction(), so that absence is never mistaken for a value");
        }
        return new EmbedderIdentity(modelName, artefactDigest, weightDtype, outputDimension, instruction);
    }

    /** The composed string a vector row is keyed under, naming every part rather than summarising them. */
    public String value() {
        return "model=" + modelName
                + ";digest=" + artefactDigest
                + ";dtype=" + weightDtype
                + ";dimension=" + outputDimension
                + ";instruction=" + (instruction == null ? NO_INSTRUCTION : SUPPLIED_INSTRUCTION + instruction);
    }

    private static void requireStated(String part, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("an embedder identity's " + part + " is never blank");
        }
    }
}
