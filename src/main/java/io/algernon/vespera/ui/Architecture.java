package io.algernon.vespera.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the Architecture window draws and lights (ADR-442): the parts of the reference picture, the wires between them,
 * one table a step of what each step of the job lies in and touches, and the lighting of every part from what the
 * cockpit reads. It links no JavaFX class, so that the lighting is held apart from the drawing; the drawing is
 * {@link ArchitectureView}'s.
 */
final class Architecture {

    /** What a part is, which gives it its colour in the legend (ADR-442 §4). */
    enum Kind { INPUT, CONTROL, STAGE, GATE, STORE, CONTAINER, OUTPUT, CONNECTOR }

    /** A part of the picture: its title, a line saying what it is, its kind and, for a stage, its number. */
    enum Part {
        ARCHIVE("Document archive", "read only", Kind.INPUT, ""),
        SEED_FOLDER("Seed folder", "exemplars, read only", Kind.INPUT, ""),
        CONTROL_PANEL("Control panel", "this window", Kind.CONTROL, ""),
        CLI("Vespera CLI", "run, label, publish", Kind.CONTROL, ""),
        STAGE_0("Census", "path, size, time", Kind.STAGE, "0"),
        STAGE_1("Byte\nreduction", "SHA-256 on size ties", Kind.STAGE, "1"),
        STAGE_2("Extraction", "Docling, cached", Kind.STAGE, "2"),
        STAGE_3("Content\ncensus", "boilerplate, spreads", Kind.STAGE, "3"),
        STAGE_4("Content\nredundancy", "MinHash, LSH", Kind.STAGE, "4"),
        STAGE_5("Relevance", "seeds, scores, groups", Kind.STAGE, "5"),
        STAGE_6A("Arrangement", "topics, ordered groups", Kind.GATE, "6a"),
        STAGE_6B("Generation", "a verified call a group", Kind.GATE, "6b"),
        LEDGER("SQLite ledger", "occurrences, verdicts, runs, caches", Kind.STORE, ""),
        DOCLING("Docling", "conversion, OCR, layout", Kind.CONTAINER, ""),
        OLLAMA("Ollama", "embedding, generation", Kind.CONTAINER, ""),
        GPU("GPU", "optional", Kind.CONTAINER, ""),
        DELIVERABLE("Deliverable", "HTML pages, verified copies", Kind.OUTPUT, ""),
        REPORTS("Reports", "analysis reports", Kind.OUTPUT, ""),
        CONFLUENCE("Confluence", "publish only", Kind.CONNECTOR, ""),
        WEBSITE("Website", "publish only", Kind.CONNECTOR, "");

        private final String title;
        private final String about;
        private final Kind kind;
        private final String number;

        Part(String title, String about, Kind kind, String number) {
            this.title = title;
            this.about = about;
            this.kind = kind;
            this.number = number;
        }

        /** Its title, a line each where it is drawn on more than one. */
        String title() {
            return title;
        }

        /** The title on one line, as the line above the drawing says it. */
        String titleOnOneLine() {
            return title.replace('\n', ' ');
        }

        String about() {
            return about;
        }

        Kind kind() {
            return kind;
        }

        /** The stage's number as the picture writes it, empty for a part that is no stage. */
        String number() {
            return number;
        }

        /** The part's name in its node's id, {@code architecture-<name>}: {@code stage-6a}, {@code seed-folder}. */
        String id() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /** The eight stages, in the picture's order. */
    static final List<Part> STAGES = List.of(Part.STAGE_0, Part.STAGE_1, Part.STAGE_2, Part.STAGE_3, Part.STAGE_4,
            Part.STAGE_5, Part.STAGE_6A, Part.STAGE_6B);

    /** How a part is lit, the word it shows, and its style class (ADR-442 §5). */
    enum Lit {
        IDLE(""),
        WAITING(""),
        RUNNING("▶ running"),
        FINISHED("✓ finished"),
        FAILED("✕ failed"),
        STOPPED("■ stopped"),
        GATE("⏸ at a gate"),
        DOWN("○ down");

        private final String words;

        Lit(String words) {
            this.words = words;
        }

        String words() {
            return words;
        }

        /** The style class the stylesheet draws it by: {@code lit-running}, {@code lit-gate}. */
        String styleClass() {
            return "lit-" + name().toLowerCase(Locale.ROOT);
        }
    }

    /** One step of the job: the stage of the picture it lies in, and the parts it reads, calls or writes beside the ledger. */
    record Step(String name, Part stage, Set<Part> touches) {
    }

    /**
     * What each step of the job touches, as the tasklets and step configurations of {@code engine.pipeline} have it
     * (ADR-442's Context), in the order of {@link StopCosts#ROWS}: a test holds the two to the same steps.
     */
    private static final Map<String, Set<Part>> TOUCHES = touches(
            "census", Set.of(Part.ARCHIVE, Part.SEED_FOLDER),
            "byte-level-reduction", Set.of(Part.ARCHIVE, Part.REPORTS),
            "extraction", Set.of(Part.ARCHIVE, Part.DOCLING, Part.REPORTS),
            "content-census", Set.of(Part.REPORTS),
            "redundancy-signature", Set.of(),
            "content-redundancy", Set.of(),
            "seed-extraction", Set.of(Part.SEED_FOLDER, Part.DOCLING),
            "seed-corpus-comparison", Set.of(Part.REPORTS),
            "embedding-scoring", Set.of(Part.OLLAMA),
            "relevance-scoring", Set.of(Part.OLLAMA),
            "relevance-floor", Set.of(),
            "clustering", Set.of(Part.REPORTS),
            "relevance-report", Set.of(Part.REPORTS),
            "arrangement", Set.of(Part.OLLAMA, Part.REPORTS),
            "generation", Set.of(Part.ARCHIVE, Part.OLLAMA, Part.DELIVERABLE, Part.REPORTS));

    /** The stage of the picture each stage name {@link StopCosts#stageOf} answers lies in. */
    private static final Map<String, Part> STAGE_OF_A_RECORD = Map.ofEntries(
            Map.entry("0", Part.STAGE_0),
            Map.entry("byte-level-reduction", Part.STAGE_1),
            Map.entry("extraction", Part.STAGE_2),
            Map.entry("content-census", Part.STAGE_3),
            Map.entry("content-redundancy", Part.STAGE_4),
            Map.entry("seed-measurement", Part.STAGE_5),
            Map.entry("embedding-scoring", Part.STAGE_5),
            Map.entry("5e", Part.STAGE_5),
            Map.entry("5", Part.STAGE_5),
            Map.entry("arrangement", Part.STAGE_6A),
            Map.entry("generation", Part.STAGE_6B));

    /** The steps of the table, in its order, each with its stage, which is null where the drawing has no stage for it. */
    static final List<Step> STEPS = TOUCHES.entrySet().stream()
            .map(row -> new Step(row.getKey(), STAGE_OF_A_RECORD.get(StopCosts.stageOf(row.getKey())), row.getValue()))
            .toList();

    /** A wire of the picture: its name in its node's id, {@code architecture-wire-<name>}, and the part whose running makes it carry data. */
    record Wire(String name, Part carrier) {
    }

    /** The wires, each carrying data while its carrier runs (ADR-442 §6). */
    static final List<Wire> WIRES = wires();

    private static List<Wire> wires() {
        List<Wire> all = new ArrayList<>(List.of(
                new Wire("archive", Part.ARCHIVE),
                new Wire("seed-folder", Part.SEED_FOLDER),
                new Wire("control-panel", Part.CLI),
                new Wire("cli", Part.LEDGER),
                new Wire("docling", Part.DOCLING),
                new Wire("ollama", Part.OLLAMA),
                new Wire("gpu", Part.GPU),
                new Wire("deliverable", Part.DELIVERABLE),
                new Wire("reports", Part.REPORTS),
                new Wire("confluence", Part.CONFLUENCE),
                new Wire("website", Part.WEBSITE)));
        for (Part stage : STAGES) {
            all.add(new Wire(stage.id(), stage));
        }
        return List.copyOf(all);
    }

    /**
     * What the cockpit holds of its own child: the command it last started, null before any; whether the child is
     * alive; how it ended, empty while it runs or before any; and the destinations Publish names.
     */
    record Child(ControlPanelState.Start started, boolean alive, Optional<HowItEnded> ended, List<String> destinations) {

        /** No child started yet. */
        static final Child NONE = new Child(null, false, Optional.empty(), List.of());

        Child {
            ended = ended == null ? Optional.empty() : ended;
            destinations = destinations == null ? List.of() : List.copyOf(destinations);
        }
    }

    /**
     * What the window lights from: the report the steps chart was last handed and the word a step still running in it
     * reads, null where none (ADR-387 §1); the cockpit's child; the header's last check, null before any; and whether the
     * containers run with {@code compose.gpu.yaml}.
     */
    record Reading(InvocationReportFile.Report report, String endedAs, Child child, DockerCheck check, boolean gpu) {

        Reading {
            report = report == null ? InvocationReportFile.Report.none() : report;
            child = child == null ? Child.NONE : child;
        }
    }

    /**
     * What the window draws: each part's light, the line above the drawing, and how far the running step has got, as
     * {@code 37%}, empty where no step runs or its report gives no count.
     */
    record Lighting(Map<Part, Lit> parts, String now, String percent) {

        Lit of(Part part) {
            return parts.get(part);
        }

        /** Whether {@code wire} carries data now: its carrier runs. */
        boolean flows(Wire wire) {
            return parts.get(wire.carrier()) == Lit.RUNNING;
        }
    }

    private static final String WAITING = "waiting";
    private static final String NOT_STARTED = "not started";
    private static final String RUNNING = "running";
    private static final String FINISHED = "finished";
    private static final String FAILED = "failed";
    private static final String STOPPED = "stopped";
    private static final String GATE = "stopped at a gate";

    private Architecture() {
    }

    /**
     * A stage's light from its steps' states, folded as {@code Legs} folds a leg's (ADR-361 §4), with {@code stopped},
     * the word ADR-387 §1 gives a step still running when Stop ended its child, after failed; {@code ended} is whether
     * the report says its invocation ended.
     */
    static Lit fold(List<String> states, boolean ended) {
        if (states.stream().allMatch(WAITING::equals)) {
            return Lit.WAITING;
        }
        List<String> steps = states.stream().map(each -> each.equals(WAITING) ? NOT_STARTED : each).toList();
        if (steps.stream().allMatch(NOT_STARTED::equals)) {
            return Lit.WAITING;
        }
        if (steps.stream().allMatch(FINISHED::equals)) {
            return Lit.FINISHED;
        }
        if (steps.contains(FAILED)) {
            return Lit.FAILED;
        }
        if (steps.contains(STOPPED)) {
            return Lit.STOPPED;
        }
        if (steps.contains(GATE)) {
            return Lit.GATE;
        }
        if (steps.contains(RUNNING)) {
            return Lit.RUNNING;
        }
        // some finished and the rest not started: the job is between two steps, or ended before the next
        return ended ? Lit.FAILED : Lit.RUNNING;
    }

    /** Every part's light, and the line, from {@code reading} (ADR-442 §3). */
    static Lighting light(Reading reading) {
        Map<String, InvocationReportFile.Step> byName = new HashMap<>();
        reading.report().steps().forEach(step -> byName.put(step.name(), step));
        Map<String, String> states = new LinkedHashMap<>();
        for (Step step : STEPS) {
            InvocationReportFile.Step read = byName.get(step.name());
            String state = read == null ? WAITING : read.state();
            states.put(step.name(), reading.endedAs() != null && state.equals(RUNNING) ? reading.endedAs() : state);
        }
        Step running = STEPS.stream().filter(step -> RUNNING.equals(states.get(step.name()))).findFirst().orElse(null);
        Child child = reading.child();
        ControlPanelState.Start started = child.started();
        boolean labelling = child.alive()
                && (started == ControlPanelState.Start.LABEL || started == ControlPanelState.Start.LABEL_AUTO);
        boolean publishing = child.alive() && started == ControlPanelState.Start.PUBLISH;

        Map<Part, Lit> parts = new EnumMap<>(Part.class);
        for (Part part : Part.values()) {
            parts.put(part, Lit.IDLE);
        }
        for (Part stage : STAGES) {
            List<String> ofIt = STEPS.stream().filter(step -> step.stage() == stage).map(step -> states.get(step.name()))
                    .toList();
            Lit folded = fold(ofIt, reading.report().ended());
            // a stage between two steps of a child that ended reads as the steps chart reads such a leg (ADR-387 §1)
            parts.put(stage, folded == Lit.RUNNING && reading.endedAs() != null ? litOf(reading.endedAs()) : folded);
        }
        Lit howItEnded = child.ended().map(Architecture::litOf).orElse(Lit.IDLE);
        parts.put(Part.CLI, started == null ? Lit.IDLE : child.alive() ? Lit.RUNNING : howItEnded);
        parts.put(Part.LEDGER, running != null || labelling ? Lit.RUNNING : Lit.IDLE);
        for (Part touched : List.of(Part.ARCHIVE, Part.SEED_FOLDER, Part.REPORTS, Part.DELIVERABLE)) {
            parts.put(touched, running != null && running.touches().contains(touched) ? Lit.RUNNING : Lit.IDLE);
        }
        if (publishing) {
            parts.put(Part.DELIVERABLE, Lit.RUNNING);
        }
        DockerCheck check = reading.check();
        parts.put(Part.DOCLING, check != null && check.docling().mark() == DockerCheck.Mark.KO ? Lit.DOWN
                : running != null && running.touches().contains(Part.DOCLING) ? Lit.RUNNING : Lit.IDLE);
        parts.put(Part.OLLAMA, check != null && check.ollama().mark() == DockerCheck.Mark.KO ? Lit.DOWN
                : running != null && running.touches().contains(Part.OLLAMA)
                        || child.alive() && started == ControlPanelState.Start.LABEL_AUTO ? Lit.RUNNING : Lit.IDLE);
        parts.put(Part.GPU, !reading.gpu() ? Lit.DOWN
                : parts.get(Part.DOCLING) == Lit.RUNNING || parts.get(Part.OLLAMA) == Lit.RUNNING ? Lit.RUNNING
                        : Lit.IDLE);
        for (Part destination : List.of(Part.CONFLUENCE, Part.WEBSITE)) {
            boolean named = started == ControlPanelState.Start.PUBLISH
                    && child.destinations().contains(destination.id());
            parts.put(destination, !named ? Lit.IDLE : child.alive() ? Lit.RUNNING : howItEnded);
        }
        parts.put(Part.CONTROL_PANEL, Lit.IDLE);
        String percent = running == null ? "" : reading.report().steps().stream()
                .filter(step -> step.name().equals(running.name())).findFirst()
                .filter(step -> step.done() != null && step.total() != null && step.total() > 0)
                // as the steps chart says it: 99 at most while the step runs (ADR-370 §2)
                .map(step -> Math.min(99L, 100L * step.done() / step.total()) + "%").orElse("");
        return new Lighting(Map.copyOf(parts), now(reading, states, running, percent, parts), percent);
    }

    /** The line above the drawing (ADR-442 §3). */
    private static String now(Reading reading, Map<String, String> states, Step running, String percent,
            Map<Part, Lit> parts) {
        Child child = reading.child();
        if (running != null) {
            StringBuilder line = new StringBuilder("Now: stage ").append(running.stage().number()).append(' ')
                    .append(running.stage().titleOnOneLine()).append(", step ").append(running.name());
            if (!percent.isEmpty()) {
                line.append(' ').append(percent);
            }
            List<String> calls = new ArrayList<>();
            for (Part container : List.of(Part.DOCLING, Part.OLLAMA)) {
                if (parts.get(container) == Lit.RUNNING) {
                    calls.add(container.title());
                }
            }
            if (!calls.isEmpty()) {
                line.append(", using ").append(String.join(" and ", calls));
                if (parts.get(Part.GPU) == Lit.RUNNING) {
                    line.append(" on the GPU");
                }
            }
            return line.append(", writing the ledger").toString();
        }
        if (child.alive()) {
            return switch (child.started()) {
                case RUN -> "Now: vespera run is between two steps.";
                case LABEL -> "Now: vespera label is saving your labels into the ledger.";
                case LABEL_AUTO -> "Now: vespera label --auto is asking Ollama for labels and saving them into the ledger.";
                case PUBLISH -> "Now: vespera publish is sending the deliverable" + to(child.destinations()) + ".";
            };
        }
        if (child.started() != null && child.ended().isPresent()) {
            StringBuilder line = new StringBuilder("Ended: vespera ").append(commandOf(child.started())).append(' ')
                    .append(wordsOf(child.ended().get()));
            STEPS.stream().filter(step -> List.of(FAILED, STOPPED, GATE).contains(states.get(step.name()))).findFirst()
                    .ifPresent(step -> line.append(", at step ").append(step.name()).append(" (stage ")
                            .append(step.stage().number()).append("), ").append(states.get(step.name())));
            return line.append('.').toString();
        }
        return "Now: nothing is running.";
    }

    private static String to(List<String> destinations) {
        List<String> named = new ArrayList<>();
        if (destinations.contains(Part.CONFLUENCE.id())) {
            named.add("Confluence");
        }
        if (destinations.contains(Part.WEBSITE.id())) {
            named.add("the website");
        }
        return named.isEmpty() ? "" : " to " + String.join(" and ", named);
    }

    private static String commandOf(ControlPanelState.Start started) {
        return switch (started) {
            case RUN -> "run";
            case LABEL -> "label";
            case LABEL_AUTO -> "label --auto";
            case PUBLISH -> "publish";
        };
    }

    private static String wordsOf(HowItEnded how) {
        return switch (how) {
            case ENDED -> "finished";
            case FAILED_OR_REFUSED -> "failed or was refused";
            case DEFECT_OF_THE_CONTROL_PANEL -> "ended on a defect of the control panel";
            case STOPPED -> "was stopped by you";
        };
    }

    /** The light of a child that ended so (ADR-278 §5): finished, stopped by Stop, failed otherwise. */
    private static Lit litOf(HowItEnded how) {
        return switch (how) {
            case ENDED -> Lit.FINISHED;
            case STOPPED -> Lit.STOPPED;
            case FAILED_OR_REFUSED, DEFECT_OF_THE_CONTROL_PANEL -> Lit.FAILED;
        };
    }

    /** The light of the word a step still running in an ended child's report reads (ADR-387 §1). */
    private static Lit litOf(String endedAs) {
        return STOPPED.equals(endedAs) ? Lit.STOPPED : Lit.FAILED;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Set<Part>> touches(Object... stepsAndParts) {
        Map<String, Set<Part>> table = new LinkedHashMap<>();
        for (int i = 0; i < stepsAndParts.length; i += 2) {
            table.put((String) stepsAndParts[i], (Set<Part>) stepsAndParts[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(table);
    }
}
