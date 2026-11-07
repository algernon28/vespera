package io.algernon.controlpanel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The four legs the job's steps fold into (ADR-361 §1): ADR-324 §1's table held as text, because the control panel
 * links no class of the jar (ADR-285 §3); {@code pipeline.TheControlPanelsLegsAreTheProgressPagesTest} holds it, and the
 * rules of a leg's state and of which legs are open, to the progress page's.
 */
public final class Legs {

    /** One leg: its name as the progress page writes it and its steps in the order the job runs them. */
    public record Leg(String name, List<String> steps) {
    }

    /** The four legs, in the page's order; their steps together are {@link StopCosts#ROWS}, in its order. */
    public static final List<Leg> ALL = List.of(
            new Leg("deduplication", List.of("census", "byte-level-reduction", "extraction", "content-census",
                    "redundancy-signature", "content-redundancy")),
            new Leg("translation", List.of("seed-extraction", "seed-corpus-comparison", "embedding-scoring",
                    "translation")),
            new Leg("clustering", List.of("relevance-scoring", "relevance-floor", "clustering", "relevance-report")),
            new Leg("generation", List.of("arrangement", "generation")));

    private static final String WAITING = "waiting";
    private static final String NOT_STARTED = "not started";
    private static final String FINISHED = "finished";
    private static final String FAILED = "failed";
    private static final String GATE = "stopped at a gate";
    private static final String RUNNING = "running";

    private Legs() {
    }

    /** Each leg's state in words, by leg name, in {@link #ALL}'s order (ADR-361 §4). */
    public static Map<String, String> statesOf(ProgressFile.Progress progress) {
        Map<String, String> byStep = new HashMap<>();
        progress.steps().forEach(step -> byStep.put(step.name(), step.state()));
        Map<String, String> states = new LinkedHashMap<>();
        for (Leg leg : ALL) {
            List<String> read = leg.steps().stream().map(step -> byStep.getOrDefault(step, WAITING)).toList();
            states.put(leg.name(), stateOf(read, progress.ended()));
        }
        return states;
    }

    private static String stateOf(List<String> read, boolean ended) {
        if (read.stream().allMatch(WAITING::equals)) {
            return WAITING;
        }
        List<String> steps = read.stream().map(each -> each.equals(WAITING) ? NOT_STARTED : each).toList();
        if (steps.stream().allMatch(NOT_STARTED::equals)) {
            return NOT_STARTED;
        }
        if (steps.stream().allMatch(FINISHED::equals)) {
            return FINISHED;
        }
        if (steps.contains(FAILED)) {
            return FAILED;
        }
        if (steps.contains(GATE)) {
            return GATE;
        }
        if (steps.contains(RUNNING)) {
            return RUNNING;
        }
        // some finished and the rest not started: the job is between two steps, or ended before the next
        return ended ? FAILED : RUNNING;
    }

    /** The legs that are open (ADR-361 §5): running or failed, or holding the first step stopped at a gate. */
    public static List<String> openOf(ProgressFile.Progress progress) {
        Map<String, String> states = statesOf(progress);
        Map<String, String> byStep = new HashMap<>();
        progress.steps().forEach(step -> byStep.put(step.name(), step.state()));
        String firstGated = null;
        for (Leg leg : ALL) {
            for (String step : leg.steps()) {
                if (firstGated == null && GATE.equals(byStep.get(step))) {
                    firstGated = leg.name();
                }
            }
        }
        List<String> open = new ArrayList<>();
        for (Leg leg : ALL) {
            String state = states.get(leg.name());
            if (state.equals(RUNNING) || state.equals(FAILED) || leg.name().equals(firstGated)) {
                open.add(leg.name());
            }
        }
        return List.copyOf(open);
    }
}
