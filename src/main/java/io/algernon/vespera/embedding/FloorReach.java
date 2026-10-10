package io.algernon.vespera.embedding;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * What the relevance floor lets stage 5e do (ADR-088, ADR-118, ADR-226 moving ADR-222's rules 2 and 3),
 * answered as the two actions the step takes and not as a classification of the floor.
 *
 * <p>A threshold is a number on a scale and the embedder identity the vectors carry is the scale. The
 * answers recorded for the seed set carry the identity that was on screen when each was given, so a
 * number read off another scale, or off several, removes nothing. Nothing checks that a floor was ever
 * labelled (ADR-088): no answers at all applies it.
 *
 * <p>Where the vectors carry no single embedder identity nothing is decided either way: no removal is
 * made and none already standing is withdrawn, and the answers are not read.
 */
public final class FloorReach {

    /** What a below-threshold removal records as its reason. */
    public static final String REASON = "relevance score below the floor set in the profile";

    private final boolean singleIdentity;
    private final OptionalDouble floor;
    private final List<String> answeredUnder;
    private final OptionalDouble removesBelow;

    private FloorReach(
            boolean singleIdentity, OptionalDouble floor, List<String> answeredUnder, OptionalDouble removesBelow) {
        this.singleIdentity = singleIdentity;
        this.floor = floor;
        this.answeredUnder = answeredUnder;
        this.removesBelow = removesBelow;
    }

    /**
     * The reach for a run whose vectors carry {@code embedderIdentity}. {@code answers} is read once,
     * here, and only where there is an identity and a number.
     */
    public static FloorReach of(
            Optional<String> embedderIdentity, OptionalDouble floor, Supplier<List<RelevanceLabel>> answers) {
        if (embedderIdentity.isEmpty()) {
            return new FloorReach(false, floor, List.of(), OptionalDouble.empty());
        }
        if (floor.isEmpty()) {
            return new FloorReach(true, floor, List.of(), OptionalDouble.empty());
        }
        List<String> answeredUnder =
                answers.get().stream().map(RelevanceLabel::embedderIdentity).distinct().toList();
        boolean onThisScale = answeredUnder.isEmpty() || answeredUnder.equals(List.of(embedderIdentity.get()));
        return new FloorReach(true, floor, answeredUnder, onThisScale ? floor : OptionalDouble.empty());
    }

    /** False only where there is no single embedder identity. */
    public boolean withdrawsStandingRemovals() {
        return singleIdentity;
    }

    /** Present only for a number on this run's own scale. */
    public OptionalDouble removesBelow() {
        return removesBelow;
    }

    /** The number it was handed. */
    public OptionalDouble floor() {
        return floor;
    }

    /** The distinct identities of the answers in the order read; empty where they were not read. */
    public List<String> answeredUnder() {
        return answeredUnder;
    }
}
