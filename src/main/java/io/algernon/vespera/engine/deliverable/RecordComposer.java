package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.synthesis.Citation;
import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeSet;
import java.util.regex.Matcher;

/**
 * Composes the retrieval records of one topic at a time and hands each to every plugin reading them (ADR-434 §4, §5):
 * the topic's overview where it is written, then each cluster in the topic's one sequence, its synthesis doc where one
 * is written and then its members' passages where some plugin takes them. Nothing is composed where no plugin still
 * reads records; a failure in composing stops every plugin reading them, and the drawing goes on.
 */
final class RecordComposer {

    private final ArrangedSurvivors source;
    private final RecordInputs inputs;
    private final PluginDrawings drawings;
    private final String generationRun;
    private final DeliverableLanguage language;
    private final int topicCount;

    RecordComposer(
            ArrangedSurvivors source,
            RecordInputs inputs,
            PluginDrawings drawings,
            String generationRun,
            DeliverableLanguage language,
            int topicCount) {
        this.source = source;
        this.inputs = inputs;
        this.drawings = drawings;
        this.generationRun = generationRun;
        this.language = language;
        this.topicCount = topicCount;
    }

    /** Composes and hands on {@code topic}'s records. */
    void compose(ListedTopic topic) {
        if (!drawings.readsRecords()) {
            return;
        }
        try {
            if (inputs == null) {
                throw new IllegalStateException("retrieval records are read and no inputs compose them");
            }
            String embedder = inputs.embedderIdentity();
            TopicLayout layout = new TopicLayout(
                    topic,
                    topicCount,
                    source.subtopicsOf(topic),
                    Deliverable.inOneSequence(source.clustersOf(topic)));
            if (source.overviewOf(topic) instanceof ListedOverview.Written written) {
                String text = written.writing();
                drawings.take(new RetrievalRecord(
                        RetrievalRecordId.ofTopicOverview(topic.topic()),
                        RetrievalRecord.Kind.TOPIC_OVERVIEW,
                        text,
                        inputs.vectorOf(text),
                        embedder,
                        placement(topic, Optional.empty(), OptionalInt.empty(), Optional.empty(), OptionalInt.empty(),
                                citedIn(text), Optional.empty(), OptionalInt.empty())));
            }
            Map<ClusterSlot, List<ListedSurvivor>> membersByCluster = new HashMap<>();
            if (drawings.takesMemberPassages()) {
                for (ListedSurvivor survivor : source.survivorsOf(topic)) {
                    membersByCluster.computeIfAbsent(survivor.slot(), key -> new ArrayList<>()).add(survivor);
                }
            }
            for (RecordedCluster recorded : layout.clusters()) {
                if (!drawings.readsRecords()) {
                    return;
                }
                ClusterSlot slot = ClusterSlot.of(recorded);
                Optional<ListedSubtopic> subtopic = layout.subtopicOf(recorded);
                Optional<String> subtopicName = subtopic.map(ListedSubtopic::name);
                OptionalInt subtopicOrder =
                        subtopic.map(s -> OptionalInt.of(s.subtopicOrder())).orElse(OptionalInt.empty());
                OptionalInt number = OptionalInt.of(recorded.cluster().clusterOrder());
                Optional<SynthesisDoc> doc = source.writtenOver(slot);
                String clusterName = doc.map(SynthesisDoc::title).orElse(recorded.label().value());
                if (doc.isPresent()) {
                    String text = doc.get().prose();
                    drawings.take(new RetrievalRecord(
                            RetrievalRecordId.ofSynthesisDoc(slot),
                            RetrievalRecord.Kind.SYNTHESIS_DOC,
                            text,
                            inputs.vectorOf(text),
                            embedder,
                            placement(topic, subtopicName, subtopicOrder, Optional.of(clusterName), number,
                                    citedIn(text), Optional.empty(), OptionalInt.empty())));
                }
                if (!drawings.takesMemberPassages()) {
                    continue;
                }
                Map<OccurrenceId, Integer> ordinalInCall = new HashMap<>();
                doc.ifPresent(written -> {
                    for (int at = 0; at < written.sent().size(); at++) {
                        ordinalInCall.put(written.sent().get(at), at + 1);
                    }
                });
                for (ListedSurvivor member : membersByCluster.getOrDefault(slot, List.of())) {
                    List<Integer> cites = ordinalInCall.containsKey(member.occurrence())
                            ? List.of(ordinalInCall.get(member.occurrence()))
                            : List.of();
                    inputs.passagesOf(member, (ordinal, text, vector) -> drawings.take(new RetrievalRecord(
                            RetrievalRecordId.ofMemberPassage(slot, member.contentHash(), ordinal),
                            RetrievalRecord.Kind.MEMBER_PASSAGE,
                            text,
                            vector,
                            embedder,
                            placement(topic, subtopicName, subtopicOrder, Optional.of(clusterName), number, cites,
                                    Optional.of(member.path().value()), OptionalInt.of(ordinal)))));
                }
            }
        } catch (IOException | RuntimeException e) {
            drawings.recordsFailed(e);
        }
    }

    private RetrievalRecord.Placement placement(
            ListedTopic topic,
            Optional<String> subtopic,
            OptionalInt subtopicOrder,
            Optional<String> cluster,
            OptionalInt clusterNumber,
            List<Integer> citations,
            Optional<String> document,
            OptionalInt passage) {
        return new RetrievalRecord.Placement(
                generationRun, language, topic.name(), topic.topicOrder(), subtopic, subtopicOrder, cluster,
                clusterNumber, citations, document, passage);
    }

    /** The ordinals {@code text} cites as {@code [n]}, each once, ascending. */
    private static List<Integer> citedIn(String text) {
        TreeSet<Integer> cited = new TreeSet<>();
        Matcher found = Citation.AS_WRITTEN.matcher(text);
        while (found.find()) {
            cited.add(Integer.parseInt(found.group(1)));
        }
        return List.copyOf(cited);
    }
}
