package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.RunId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Stage 6b's walk over the clusters of one run (ADR-190): the skipping of what is already written
 * (ADR-115), the two unsendable branches (ADR-121), the call, the fault row for a turned-down answer
 * (ADR-111), the breaker at {@value #CONSECUTIVE_TURNED_DOWN_ANSWERS} in a row, ADR-166 §4a's exemption,
 * the repair pass's deletion and the completion rule (ADR-116).
 *
 * <p>It knows no stage, names no Spring Batch type and logs nothing: what the operator reads is said
 * by the caller, through {@link GenerationProgress}, which hears of each cluster left unwritten, of
 * every cluster gone through (ADR-192) and of the walk's two reads, the clusters already written and
 * the faults standing (ADR-193), and the {@link GenerationOutcome} it returns.
 */
@Component
public class ClusterGeneration {

    /**
     * How many answers turned down one after another stop the walk (ADR-111). Five, matching ADR-071's
     * service-scope count rather than its lower timeout count, because like that one this fires on a
     * mix of kinds.
     */
    public static final int CONSECUTIVE_TURNED_DOWN_ANSWERS = 5;

    private final ClusterSynthesis clusterSynthesis;
    private final SynthesisDocs synthesisDocs;
    private final ClusterFaults clusterFaults;

    public ClusterGeneration(
            ClusterSynthesis clusterSynthesis, SynthesisDocs synthesisDocs, ClusterFaults clusterFaults) {
        this.clusterSynthesis = clusterSynthesis;
        this.synthesisDocs = synthesisDocs;
        this.clusterFaults = clusterFaults;
    }

    /**
     * Writes every cluster of {@code clusters}, in the order given, under the run {@code generation}.
     * Writes rows in the caller's transaction. A {@link RuntimeException} other than {@link
     * ClusterFaultException}, from {@code docFor}, from {@code exemplars} or from a write, leaves this
     * method as it was raised, and so does an {@link Error}.
     */
    public GenerationOutcome write(
            RunId generation,
            List<RecordedCluster> clusters,
            ClusterExemplars exemplars,
            String modelName,
            int contextWindow,
            GenerationProgress progress) {
        // Rows an earlier invocation of this run already wrote (ADR-115, ADR-116).
        // A timed statement (ADR-193 sections 1 and 6): the read goes through a temp B-tree and has no cheap
        // total, so the caller is told it starts and ends, and nothing between.
        progress.statementStarting(SynthesisStatement.WRITTEN, OptionalLong.empty());
        Set<ClusterSlot> alreadyWritten = synthesisDocs.forRun(generation).stream()
                .map(ClusterSlot::of)
                .collect(Collectors.toSet());
        progress.statementEnded(SynthesisStatement.WRITTEN);
        progress.toGoThrough(clusters.size());
        int written = 0;
        int skipped = 0;
        int faulted = 0;
        // The faults themselves are kept rather than a count: what the operator has to act on is which
        // checks the run of answers failed (ADR-111).
        List<ClusterFault> turnedDownInARow = new ArrayList<>();
        Map<ClusterSlot, Unwritten> unsendable = new LinkedHashMap<>();
        for (RecordedCluster recorded : clusters) {
            ClusterSlot slot = ClusterSlot.of(recorded);
            if (alreadyWritten.contains(slot)) {
                skipped++;
                progress.clusterGoneThrough();
                continue;
            }
            ClusterMaterial material = exemplars.of(recorded);
            if (material.exemplars().isEmpty()) {
                progress.noSendableDocument(recorded);
                unsendable.put(slot, Unwritten.NO_SENDABLE_DOCUMENT);
                progress.clusterGoneThrough();
                continue;
            }
            if (ClusterSynthesis.nothingFitsIn(contextWindow, material.exemplars())) {
                progress.nothingFitsTheWindow(recorded, material.exemplars().size(), contextWindow);
                unsendable.put(slot, Unwritten.NOTHING_FITS_THE_WINDOW);
                progress.clusterGoneThrough();
                continue;
            }
            SynthesisDoc doc;
            try {
                doc = clusterSynthesis.docFor(
                        new ClusterCall(recorded.label().value(), material.seedPath(), material.exemplars()),
                        modelName,
                        contextWindow);
            } catch (ClusterFaultException e) {
                if (e.noAnswerWasAskedFor()) {
                    progress.noDocumentCountedInsideTheRoom(recorded, e.fault());
                } else {
                    progress.answerTurnedDown(recorded, e.fault());
                }
                clusterFaults.record(generation, slot.winningSeed(), slot.clusterOrdinal(), e.fault());
                faulted++;
                // ADR-166 §4a: a cluster no answer was asked for is no evidence either way, so it is
                // left out of the streak, neither added to it nor clearing it.
                if (!e.noAnswerWasAskedFor()) {
                    turnedDownInARow.add(e.fault());
                    if (turnedDownInARow.size() >= CONSECUTIVE_TURNED_DOWN_ANSWERS) {
                        // The fifth is counted before the walk returns (ADR-192 section 2).
                        progress.clusterGoneThrough();
                        return new GenerationOutcome.Stopped(List.copyOf(turnedDownInARow), frozen(unsendable));
                    }
                }
                progress.clusterGoneThrough();
                continue;
            }
            synthesisDocs.record(generation, slot.winningSeed(), slot.clusterOrdinal(), doc);
            // The repair pass (ADR-111, #185): a fault row from an earlier invocation would now say the
            // cluster both failed and succeeded under one run.
            clusterFaults.delete(generation, slot.winningSeed(), slot.clusterOrdinal());
            written++;
            // Only a believed answer drops the streak.
            turnedDownInARow.clear();
            progress.clusterGoneThrough();
        }
        // Completion needs two things (ADR-116): no unsendable cluster, and no fault row standing.
        progress.statementStarting(SynthesisStatement.STANDING_FAULTS, OptionalLong.empty());
        int standingFaults = clusterFaults.countForRun(generation);
        progress.statementEnded(SynthesisStatement.STANDING_FAULTS);
        if (!unsendable.isEmpty() || standingFaults > 0) {
            return new GenerationOutcome.LeftUnfinished(standingFaults, faulted, frozen(unsendable));
        }
        return new GenerationOutcome.Finished(written, skipped, frozen(unsendable));
    }

    private static Map<ClusterSlot, Unwritten> frozen(Map<ClusterSlot, Unwritten> unsendable) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(unsendable));
    }
}
