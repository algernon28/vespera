package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ui.Architecture.Lit;
import io.algernon.vespera.ui.Architecture.Part;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the Architecture window lights, apart from its drawing (ADR-442 §2, §3): the table of what each step of the job
 * lies in and touches, held to the job's steps; a stage folding its steps as a leg does; and each part lit by a run, a
 * label and a publish, by Docker's check and by the converter's image, with the line above the drawing saying it.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("878")
@Link(name = "ADR-442", url = Adr.THE_ARCHITECTURE_WINDOW_LIGHTS_WHAT_RUNS, type = "adr")
class TheArchitectureTest {

    /**
     * What each step touches beside the ledger, as the tasklets and step configurations have it (ADR-442's Context),
     * written here apart from the class's own table so that a change of either is seen.
     */
    private static final Map<String, Set<Part>> AS_THE_CODE_HAS_IT = asTheCodeHasIt();

    /** The stage of the picture each step lies in, as the jar records the step's completion. */
    private static final Map<String, Part> STAGE_OF_EACH_STEP = Map.ofEntries(
            Map.entry("census", Part.STAGE_0),
            Map.entry("byte-level-reduction", Part.STAGE_1),
            Map.entry("extraction", Part.STAGE_2),
            Map.entry("content-census", Part.STAGE_3),
            Map.entry("redundancy-signature", Part.STAGE_4),
            Map.entry("content-redundancy", Part.STAGE_4),
            Map.entry("seed-extraction", Part.STAGE_5),
            Map.entry("seed-corpus-comparison", Part.STAGE_5),
            Map.entry("embedding-scoring", Part.STAGE_5),
            Map.entry("relevance-scoring", Part.STAGE_5),
            Map.entry("relevance-floor", Part.STAGE_5),
            Map.entry("clustering", Part.STAGE_5),
            Map.entry("relevance-report", Part.STAGE_5),
            Map.entry("arrangement", Part.STAGE_6A),
            Map.entry("generation", Part.STAGE_6B));

    /** Extraction's counter in the report the run lights from: 37 of 100, so the line reads 37%. */
    private static final long DONE = 37;

    private static final long TOTAL = 100;

    /** The check of a machine whose two containers answer. */
    private static final DockerCheck BOTH_ANSWER = check(DockerCheck.Mark.OK, DockerCheck.Mark.OK);

    @Test
    @Story("The Architecture window lights what runs")
    @DisplayName("The drawing's steps are the job's steps, in order, each in a stage the drawing places, and what each touches is what the code reads, calls and writes")
    void theDrawingsStepsAreTheJobsStepsEachInADrawnStage() {
        List<String> theJobs = StopCosts.ROWS.stream().map(StopCosts.StopCost::step).toList();
        List<String> theDrawings = Architecture.STEPS.stream().map(Architecture.Step::name).toList();
        Map<String, Part> stages = new LinkedHashMap<>();
        Map<String, Set<Part>> touches = new LinkedHashMap<>();
        Architecture.STEPS.forEach(step -> {
            stages.put(step.name(), step.stage());
            touches.put(step.name(), step.touches());
        });
        List<Part> stagesHoldingAStep = Architecture.STAGES.stream()
                .filter(stage -> Architecture.STEPS.stream().anyMatch(step -> step.stage() == stage)).toList();

        claim("the drawing's steps are the job's fifteen steps, in the order the job runs them",
                () -> assertThat(theDrawings).containsExactlyElementsOf(theJobs));
        claim("each step lies in the stage of the picture the jar records its completion under",
                () -> assertThat(stages).containsExactlyInAnyOrderEntriesOf(STAGE_OF_EACH_STEP));
        claim("each of the eight stages drawn holds a step",
                () -> assertThat(stagesHoldingAStep).containsExactlyElementsOf(Architecture.STAGES));
        claim("what each step touches beside the ledger is what its code reads, calls and writes",
                () -> assertThat(touches).containsExactlyInAnyOrderEntriesOf(AS_THE_CODE_HAS_IT));
    }

    @Test
    @Story("The Architecture window lights what runs")
    @DisplayName("A stage of the drawing folds its steps' states as a part of the steps chart folds them, for every state a step can be in")
    void aStageFoldsItsStepsAsALegDoes() {
        List<InvocationReportFile.Report> reports = List.of(
                report(false, Map.of()),
                report(false, Map.of("census", "finished")),
                report(true, Map.of("census", "finished")),
                report(false, Map.of("census", "finished", "byte-level-reduction", "finished", "extraction",
                        "finished", "content-census", "finished", "redundancy-signature", "finished",
                        "content-redundancy", "finished")),
                report(false, Map.of("census", "finished", "byte-level-reduction", "running")),
                report(true, Map.of("census", "finished", "byte-level-reduction", "failed")),
                report(true, Map.of("seed-extraction", "finished", "seed-corpus-comparison", "stopped at a gate")),
                report(false, Map.of("seed-extraction", "finished", "seed-corpus-comparison", "finished",
                        "embedding-scoring", "running")),
                report(true, Map.of("clustering", "finished", "relevance-report", "finished", "arrangement",
                        "stopped at a gate")));
        List<String> disagreeing = new ArrayList<>();
        for (InvocationReportFile.Report report : reports) {
            Map<String, String> theLegs = Legs.statesOf(report);
            Map<String, String> byStep = new LinkedHashMap<>();
            report.steps().forEach(step -> byStep.put(step.name(), step.state()));
            for (Legs.Leg leg : Legs.ALL) {
                List<String> states = leg.steps().stream().map(step -> byStep.getOrDefault(step, "waiting")).toList();
                String folded = wordOf(Architecture.fold(states, report.ended()));
                String read = theLegs.get(leg.name()).equals("not started") ? "waiting" : theLegs.get(leg.name());
                if (!folded.equals(read)) {
                    disagreeing.add(leg.name() + " of " + byStep + ": " + folded + ", not " + read);
                }
            }
        }

        claim("over nine reports holding every state a step can be in, every part of the steps chart folds to the state"
                        + " a stage of the drawing folds the same steps to",
                () -> assertThat(disagreeing).isEmpty());
        claim("a step that Stop left running, which the steps chart reads as stopped, leaves its stage stopped",
                () -> assertThat(Architecture.fold(List.of("finished", "stopped"), false)).isEqualTo(Lit.STOPPED));
    }

    @Test
    @Story("The Architecture window lights what runs")
    @DisplayName("A running step lights its stage, the ledger, the CLI and what it touches; a label and a publish light theirs; a container not answering is down; the control panel is never lit; and the line says each")
    void aRunALabelAndAPublishLightTheirParts() {
        InvocationReportFile.Report extracting = report(false, List.of(
                step("census", "finished", null, null),
                step("byte-level-reduction", "finished", null, null),
                step("extraction", "running", DONE, TOTAL)));
        Architecture.Lighting run = Architecture.light(new Architecture.Reading(extracting, null,
                child(ControlPanelState.Start.RUN, true, null), BOTH_ANSWER, true));
        Architecture.Lighting onTheCpu = Architecture.light(new Architecture.Reading(extracting, null,
                child(ControlPanelState.Start.RUN, true, null), BOTH_ANSWER, false));
        Architecture.Lighting doclingDown = Architecture.light(new Architecture.Reading(extracting, null,
                child(ControlPanelState.Start.RUN, true, null), check(DockerCheck.Mark.KO, DockerCheck.Mark.OK), true));
        Architecture.Lighting stopped = Architecture.light(new Architecture.Reading(extracting, StepsChart.STOPPED,
                child(ControlPanelState.Start.RUN, false, HowItEnded.STOPPED), BOTH_ANSWER, true));
        Architecture.Lighting labelling = Architecture.light(new Architecture.Reading(null, null,
                child(ControlPanelState.Start.LABEL_AUTO, true, null), BOTH_ANSWER, false));
        Architecture.Lighting publishing = Architecture.light(new Architecture.Reading(null, null,
                new Architecture.Child(ControlPanelState.Start.PUBLISH, true, Optional.empty(), List.of("confluence")),
                BOTH_ANSWER, false));
        Architecture.Lighting published = Architecture.light(new Architecture.Reading(null, null,
                new Architecture.Child(ControlPanelState.Start.PUBLISH, false, Optional.of(HowItEnded.FAILED_OR_REFUSED),
                        List.of("confluence")), BOTH_ANSWER, false));
        Architecture.Lighting nothing = Architecture.light(new Architecture.Reading(null, null, null, null, false));

        claim("extraction running lights stage 2, the ledger, the CLI, the document archive, Docling, the reports and"
                        + " the GPU it runs on, and nothing else; stages 0 and 1 read finished",
                () -> {
                    assertThat(running(run)).containsExactlyInAnyOrder(Part.STAGE_2, Part.LEDGER, Part.CLI,
                            Part.ARCHIVE, Part.DOCLING, Part.REPORTS, Part.GPU);
                    assertThat(run.of(Part.STAGE_0)).isEqualTo(Lit.FINISHED);
                    assertThat(run.of(Part.STAGE_1)).isEqualTo(Lit.FINISHED);
                    assertThat(run.of(Part.STAGE_3)).isEqualTo(Lit.WAITING);
                });
        claim("the line names the stage, the step, how far it has got, the container it calls on the GPU, and the ledger",
                () -> assertThat(run.now()).isEqualTo(
                        "Now: stage 2 Extraction, step extraction 37%, using Docling on the GPU, writing the ledger"));
        claim("with the containers not run on the GPU, the GPU reads down and the line names no GPU",
                () -> {
                    assertThat(onTheCpu.of(Part.GPU)).isEqualTo(Lit.DOWN);
                    assertThat(onTheCpu.now()).doesNotContain("GPU");
                });
        claim("Docling not answering the header's check reads down, though the running step calls it",
                () -> assertThat(doclingDown.of(Part.DOCLING)).isEqualTo(Lit.DOWN));
        claim("the run stopped by Stop leaves stage 2 stopped and the CLI stopped, lights nothing running, and the line"
                        + " says how it ended and at which step",
                () -> {
                    assertThat(stopped.of(Part.STAGE_2)).isEqualTo(Lit.STOPPED);
                    assertThat(stopped.of(Part.CLI)).isEqualTo(Lit.STOPPED);
                    assertThat(running(stopped)).isEmpty();
                    assertThat(stopped.now()).isEqualTo(
                            "Ended: vespera run was stopped by you, at step extraction (stage 2), stopped.");
                });
        claim("vespera label --auto lights the CLI, the ledger and Ollama, and no stage",
                () -> {
                    assertThat(running(labelling)).containsExactlyInAnyOrder(Part.CLI, Part.LEDGER, Part.OLLAMA);
                    assertThat(labelling.now()).startsWith("Now: vespera label --auto");
                });
        claim("a publish to Confluence lights the CLI, the deliverable and Confluence, not the website; once it fails,"
                        + " Confluence and the CLI read failed",
                () -> {
                    assertThat(running(publishing)).containsExactlyInAnyOrder(Part.CLI, Part.DELIVERABLE,
                            Part.CONFLUENCE);
                    assertThat(publishing.of(Part.WEBSITE)).isEqualTo(Lit.IDLE);
                    assertThat(publishing.now()).isEqualTo("Now: vespera publish is sending the deliverable to Confluence.");
                    assertThat(published.of(Part.CONFLUENCE)).isEqualTo(Lit.FAILED);
                    assertThat(published.of(Part.CLI)).isEqualTo(Lit.FAILED);
                    assertThat(published.of(Part.WEBSITE)).isEqualTo(Lit.IDLE);
                });
        claim("with no child and no report, nothing is lit and the line says nothing is running",
                () -> {
                    assertThat(running(nothing)).isEmpty();
                    assertThat(nothing.now()).isEqualTo("Now: nothing is running.");
                });
        claim("the control panel is never lit, whatever runs",
                () -> assertThat(List.of(run, stopped, labelling, publishing, published, nothing))
                        .allMatch(lighting -> lighting.of(Part.CONTROL_PANEL) == Lit.IDLE));
        claim("a wire carries data exactly while the part at its far end runs: Docling's while extraction runs, Ollama's"
                        + " not",
                () -> {
                    assertThat(run.flows(wire("docling"))).isTrue();
                    assertThat(run.flows(wire("ollama"))).isFalse();
                    assertThat(run.flows(wire("stage-2"))).isTrue();
                    assertThat(run.flows(wire("stage-1"))).isFalse();
                });
    }

    // ---- what the tests share

    private static Map<String, Set<Part>> asTheCodeHasIt() {
        Map<String, Set<Part>> table = new LinkedHashMap<>();
        table.put("census", Set.of(Part.ARCHIVE, Part.SEED_FOLDER));
        table.put("byte-level-reduction", Set.of(Part.ARCHIVE, Part.REPORTS));
        table.put("extraction", Set.of(Part.ARCHIVE, Part.DOCLING, Part.REPORTS));
        table.put("content-census", Set.of(Part.REPORTS));
        table.put("redundancy-signature", Set.of());
        table.put("content-redundancy", Set.of());
        table.put("seed-extraction", Set.of(Part.SEED_FOLDER, Part.DOCLING));
        table.put("seed-corpus-comparison", Set.of(Part.REPORTS));
        table.put("embedding-scoring", Set.of(Part.OLLAMA));
        table.put("relevance-scoring", Set.of(Part.OLLAMA));
        table.put("relevance-floor", Set.of());
        table.put("clustering", Set.of(Part.REPORTS));
        table.put("relevance-report", Set.of(Part.REPORTS));
        table.put("arrangement", Set.of(Part.OLLAMA, Part.REPORTS));
        table.put("generation", Set.of(Part.ARCHIVE, Part.OLLAMA, Part.DELIVERABLE, Part.REPORTS));
        return table;
    }

    private static List<Part> running(Architecture.Lighting lighting) {
        return lighting.parts().entrySet().stream().filter(entry -> entry.getValue() == Lit.RUNNING)
                .map(Map.Entry::getKey).toList();
    }

    private static Architecture.Wire wire(String name) {
        return Architecture.WIRES.stream().filter(wire -> wire.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("the drawing has no wire " + name));
    }

    /** The word a part of the steps chart reads for {@code lit}: waiting stands for waiting and not started alike. */
    private static String wordOf(Lit lit) {
        return switch (lit) {
            case WAITING, IDLE -> "waiting";
            case RUNNING -> "running";
            case FINISHED -> "finished";
            case FAILED -> "failed";
            case STOPPED -> "stopped";
            case GATE -> "stopped at a gate";
            case DOWN -> "down";
        };
    }

    private static Architecture.Child child(ControlPanelState.Start started, boolean alive, HowItEnded ended) {
        return new Architecture.Child(started, alive, Optional.ofNullable(ended), List.of());
    }

    private static DockerCheck check(DockerCheck.Mark docling, DockerCheck.Mark ollama) {
        return new DockerCheck(DockerCheck.Mark.OK,
                new DockerCheck.Service(docling, DockerCheck.Where.THIS_MACHINE, DockerControl.DEFAULT_DOCLING),
                new DockerCheck.Service(ollama, DockerCheck.Where.THIS_MACHINE, DockerControl.DEFAULT_OLLAMA),
                DockerCheck.Mark.OK, DockerCheck.Mark.OK);
    }

    /** The report of a job whose steps named are in the states given, the others not written, ended where {@code ended}. */
    private static InvocationReportFile.Report report(boolean ended, Map<String, String> states) {
        List<InvocationReportFile.Step> steps = StopCosts.ROWS.stream().map(StopCosts.StopCost::step)
                .filter(states::containsKey).map(name -> step(name, states.get(name), null, null)).toList();
        return report(ended, steps);
    }

    private static InvocationReportFile.Report report(boolean ended, List<InvocationReportFile.Step> steps) {
        InvocationReportFile.Report none = InvocationReportFile.Report.none();
        InvocationReportFile.Invocation invocation = new InvocationReportFile.Invocation("20261122T000000Z", 1L,
                "run", Instant.EPOCH, null, ended ? "ended" : "running", null, null, Instant.EPOCH, 1L);
        return new InvocationReportFile.Report(none.format(), invocation, null, null, null, steps, List.of(),
                List.of(), List.of(), List.of(), List.of(), null, null, List.of(), List.of());
    }

    private static InvocationReportFile.Step step(String name, String state, Long done, Long total) {
        return new InvocationReportFile.Step(name, null, state, done, total);
    }
}
