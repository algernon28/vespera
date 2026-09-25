package io.algernon.vespera.pipeline;

import java.util.Optional;

/**
 * Stage 5's gate preamble as one seam (ADR-132): the gates a step consults before it does any work,
 * asked in the ledger's fixed order, answering either open — with the values an open step goes on to
 * use — or shut, with the one sentence that explains which gate stopped it.
 *
 * <p><b>One interface, seven call sites.</b> Each of stage 5's tasklets used to ask {@link
 * EmbeddingModelGate}, {@link SeedGate} and {@link UsableSeedGate} one at a time and word the three
 * shut reasons itself. The reasons are the same three facts everywhere — no model named, no finished
 * seed walk, no usable seed — and seven copies of a sentence is seven chances for one of them to say
 * something the others do not. This is the module those seven now cross: a caller names the gates its
 * step needs, and the answer is a {@link Preamble} it logs without composing a word.
 *
 * <p><b>The gates keep their own logic and their own identity.</b> {@link SeedGate} still chains
 * {@link RedundancyGate}, because stage 4's floor is a condition of stage 5's ancestry rather than a
 * fourth value this module asks for, and {@code SeedGateTest} and {@code EmbeddingModelGateTest} pin
 * those gates directly. This module composes them; it does not replace them. {@link ArrangementGate}
 * is not one of the three at all — it approves a named arrangement, which is a different kind of
 * question from whether a profile value is set.
 *
 * <p><b>Three call sites, because stage 5 has three step shapes.</b> A scoring-half step consults all
 * three gates; the labelling report consults the model and the seed walk but not whether a seed was
 * usable, because a run with no usable seed produced no scores and the report gates on that itself;
 * the seed/corpus comparison runs before the model gate by design (ADR-086, ADR-092) and consults the
 * seed walk and the usable seed. Each entry point asks its gates in the same order and shares the one
 * reason vocabulary, so the shapes differ in which gates they name and in nothing else.
 *
 * <p><b>A fourth reason, asked right after the usable-seed question (ADR-155 section 3).</b> A seed
 * file that would not open while seed extraction's step was unfinished this invocation is a different
 * fact from no seed having produced text, and it shuts the same steps the usable-seed gate does, in
 * the same vocabulary.
 */
final class StageFiveGates {

    private StageFiveGates() {}

    /**
     * The three gates a scoring-half step consults: a model named (ADR-084), a finished seed walk
     * (ADR-064), and a seed that produced text (ADR-083), in that order — and, ADR-155's addition, a
     * seed file that would not open.
     */
    static Preamble modelSeedWalkUsable(
            String subject, EmbeddingModelGate model, SeedGate seeds, UsableSeedGate usable) {
        Preamble open = modelAndSeedWalk(subject, model, seeds);
        if (!open.isOpen()) {
            return open;
        }
        if (!usable.anySeedUsable()) {
            return shut(subject, Gate.USABLE_SEED);
        }
        if (usable.seedFileCouldNotOpen()) {
            return shut(subject, Gate.SEED_FILE_COULD_NOT_OPEN);
        }
        return open;
    }

    /**
     * The two gates the labelling report consults: a model named and a finished seed walk.
     *
     * <p>It does not ask whether a seed was usable. With none, seed extraction minted no run and no
     * survivor carries a relevance score, so the report gates on there being no scores to spread — its
     * own measured reason, not this preamble's (ADR-088).
     */
    static Preamble modelAndSeedWalk(String subject, EmbeddingModelGate model, SeedGate seeds) {
        Optional<String> modelName = model.modelName();
        if (modelName.isEmpty()) {
            return shut(subject, Gate.EMBEDDING_MODEL);
        }
        Optional<SeedGate.SeedWalk> seedWalk = seeds.seedWalk();
        if (seedWalk.isEmpty()) {
            return shut(subject, Gate.SEED_WALK);
        }
        return new Preamble(modelName, seedWalk, Optional.empty());
    }

    /**
     * The two gates the seed/corpus comparison consults: a finished seed walk and a usable seed — and,
     * ADR-155's addition, a seed file that would not open. It runs before the model gate — the
     * comparison reads stored {@code extraction_metric} columns and needs no model (ADR-086,
     * ADR-092) — so it never names one.
     */
    static Preamble seedWalkAndUsable(String subject, SeedGate seeds, UsableSeedGate usable) {
        Optional<SeedGate.SeedWalk> seedWalk = seeds.seedWalk();
        if (seedWalk.isEmpty()) {
            return shut(subject, Gate.SEED_WALK);
        }
        if (!usable.anySeedUsable()) {
            return shut(subject, Gate.USABLE_SEED);
        }
        if (usable.seedFileCouldNotOpen()) {
            return shut(subject, Gate.SEED_FILE_COULD_NOT_OPEN);
        }
        return new Preamble(Optional.empty(), seedWalk, Optional.empty());
    }

    /** One gate, and the clause its own line has always said it shuts with. */
    private enum Gate {
        /** No embedding model named in the profile (ADR-084). */
        EMBEDDING_MODEL("no embedding model is named"),
        /**
         * No seed folder named, stage 4's floor unset, or the seed folder's walk unfinished (ADR-064,
         * ADR-080, ADR-089) — the three causes {@link SeedGate} answers as one.
         */
        SEED_WALK("no seed folder is named, or stage 4's gate is shut, or the seed walk has not finished"),
        /** A seed folder whose documents produced no text (ADR-083). */
        USABLE_SEED("no seed document produced any text"),
        /** A seed file that would not open when seed extraction read it (ADR-155). */
        SEED_FILE_COULD_NOT_OPEN("a seed file could not be opened");

        private final String reason;

        Gate(String reason) {
            this.reason = reason;
        }

        String reason() {
            return reason;
        }
    }

    private static Preamble shut(String subject, Gate gate) {
        return new Preamble(Optional.empty(), Optional.empty(), Optional.of(subject + " is gated: " + gate.reason() + "."));
    }

    /**
     * What a step's preamble answered: the model name and finished seed walk it asked for where their
     * gates are open, and — where one is shut — the sentence that explains it. It is open exactly when
     * it carries no shut sentence.
     */
    record Preamble(Optional<String> modelName, Optional<SeedGate.SeedWalk> seedWalk, Optional<String> shutSentence) {

        /** Whether every gate the step named was open. */
        boolean isOpen() {
            return shutSentence.isEmpty();
        }
    }
}
