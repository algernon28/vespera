package io.algernon.vespera.synthesis;

import java.util.List;

/**
 * Where {@link Deliverable} asks for a survivor's pictures (ADR-149, #285), so it can be given the
 * real chain -- through {@code pipeline}, into {@code extraction}'s cache -- without naming either.
 *
 * <p>{@link #of} returns the survivor's pictures with pixels, in reading order, empty where it has
 * none. It may be asked about the same survivor more than once, in the two passes {@link
 * Deliverable} makes over the tree it writes, and must give the same answer each time. It is handed the
 * survivor as the tree lists it, so that the content hash the listing read is the key its pictures are
 * looked up by, and not a lookup of its own (ADR-241).
 *
 * <p>An implementation must not keep pixels between calls: {@link Deliverable}'s memory bound
 * (ADR-149 §9) is that at most one document's pictures are held at a time, and that bound depends on
 * every {@code of} call decoding fresh rather than returning something kept from an earlier one.
 */
@FunctionalInterface
public interface SurvivorPictures {

    /** The survivor's pictures with pixels, in reading order, or empty where it carries none. */
    List<ListedPicture> of(ListedSurvivor survivor);

    /** A source that answers empty for every survivor: the tree written before pictures existed. */
    static SurvivorPictures none() {
        return survivor -> List.of();
    }
}
