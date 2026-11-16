package io.algernon.vespera.engine.synthesis;

/** Why the model's answer was not used for a topic's name: the seven reasons of ADR-403 section 6. */
public enum NameFallbackReason {
    NOTHING_FITS,
    PAST_THE_WINDOW,
    RAN_OUT_OF_ROOM,
    OUT_OF_SHAPE,
    BLANK,
    NOT_ONE_LINE,
    TOO_MANY_WORDS
}
