package io.algernon.controlpanel;

import java.util.List;

/**
 * What a stop costs at each step of {@code vespera run}, in the order the job runs them (ADR-302 §7, which ADR-339
 * keeps): the window shows the running step's row beside its own Stop (ADR-341 §1, §8). Each step is named as the job
 * names it, as text, because the control panel links no class of the jar (ADR-285 §3); a test holds the names to the
 * job's.
 */
public final class StopCosts {

    /** One step of the job and, in words, what ending the invocation inside it costs. */
    public record StopCost(String step, String cost) {
    }

    /** A row per step of {@code vespera run}, in the order they run. */
    public static final List<StopCost> ROWS = List.of(
            new StopCost("census",
                    "the entries walked since the last commit, about every thousand entries, which are walked again"),
            new StopCost("byte-level-reduction", "the step, done again from the start; quick next to extraction"),
            new StopCost("extraction", "at most the batch open when it stopped; no conversion stored is made again"),
            new StopCost("content-census", "the pass, done again"),
            new StopCost("redundancy-signature",
                    "its own work, done again from the stored text; the other step's finished work stays"),
            new StopCost("content-redundancy", "its own work, done again; the signatures stay"),
            new StopCost("seed-extraction",
                    "its own work, done again, the conversions read back from the extraction cache"),
            new StopCost("seed-corpus-comparison", "its own work, done again"),
            new StopCost("embedding-scoring", "nothing computed: every vector stored is kept"),
            new StopCost("translation", "its decisions, made again"),
            new StopCost("relevance-scoring", "the scores, computed again from the stored vectors"),
            new StopCost("relevance-floor", "nothing: it decides again on every invocation"),
            new StopCost("clustering", "the groups, formed again from the stored vectors"),
            new StopCost("relevance-report", "nothing: its two files are written again on every invocation that reaches it"),
            new StopCost("arrangement", "the arrangement, made again"),
            new StopCost("generation", "the group being written, asked about again; every group written is kept"));

    private StopCosts() {
    }

    /**
     * The stage under whose runs the jar records {@code step}'s completion (ADR-269 section 5, ADR-341 section 1), held
     * as text because the window links no class of the jar (ADR-285 section 3); empty for a step the jar records under no
     * stage's run: the census, the relevance floor and the relevance report.
     */
    public static String stageOf(String step) {
        return switch (step) {
            case "byte-level-reduction" -> "byte-level-reduction";
            case "extraction" -> "extraction";
            case "content-census" -> "content-census";
            case "redundancy-signature", "content-redundancy" -> "content-redundancy";
            case "seed-extraction", "seed-corpus-comparison" -> "seed-measurement";
            case "embedding-scoring", "translation", "relevance-scoring", "clustering" -> "embedding-scoring";
            case "arrangement" -> "arrangement";
            case "generation" -> "generation";
            default -> "";
        };
    }

    /** What a stop costs inside {@code step}; empty text where the table names no such step. */
    public static String costOf(String step) {
        return ROWS.stream().filter(row -> row.step().equals(step)).map(StopCost::cost).findFirst().orElse("");
    }
}
