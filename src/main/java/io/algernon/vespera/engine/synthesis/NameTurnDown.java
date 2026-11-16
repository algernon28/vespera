package io.algernon.vespera.engine.synthesis;

/** Why an answer was turned down and the topic took its fallback name (ADR-407 section 6). */
public enum NameTurnDown {
    NOTHING_SENT,
    QUESTION_CUT,
    RAN_OUT_OF_ROOM,
    NOT_THE_SHAPE_ASKED,
    EMPTY,
    NOT_ONE_SHORT_LINE,
    SAME_AS_AN_EARLIER_NAME
}
