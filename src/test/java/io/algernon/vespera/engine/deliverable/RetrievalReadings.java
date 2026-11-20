package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.ListedDoc;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * What the tests of the retrieval records share (ADR-434 §4, §5): {@link ThreeTopics} with the overview of one topic
 * written, a plugin that reads the records and writes down each as it takes it, and inputs that compose a vector of
 * their own for each text, two passages for each member.
 */
final class RetrievalReadings {

    /** The identity every vector of the fixture is under. */
    static final String EMBEDDER = "model=fixture;digest=d;dtype=F16;dimension=2;instruction=none";

    /** The overview the topic of the seed alpha is written with, citing its two clusters. */
    static final String ALPHA_OVERVIEW = "Stairs [1] and risers [2], stairs [1] again.";

    private RetrievalReadings() {}

    /** {@link ThreeTopics} as a source whose topic of alpha has an overview written. */
    static ArrangedSurvivors source() {
        Map<ClusterSlot, SynthesisDoc> written = new LinkedHashMap<>();
        for (ListedDoc doc : ThreeTopics.written()) {
            written.put(doc.cluster(), doc.doc());
        }
        ArrangedSurvivors plain =
                ListedTree.of(ThreeTopics.arrangement(), written, ThreeTopics.survivors(), ThreeTopics.unwritten());
        return ArrangedSurvivors.reading(
                plain::topics,
                plain::survivorCount,
                plain::clustersOf,
                plain::survivorsOf,
                plain::writtenOver,
                plain::whyUnwritten,
                plain::placesOf,
                plain::subtopicsOf,
                topic -> topic.topic().equals(ListedTree.topicOf(ThreeTopics.ALPHA))
                        ? new ListedOverview.Written(ALPHA_OVERVIEW, 2)
                        : ListedTree.NO_OVERVIEW_YET,
                ListedTree.NO_SEE_ALSO,
                plain::eachPageOfSurvivorsForTheirPictures,
                plain::eachPageOfSurvivorsForTheManifest,
                false,
                DeliverableLanguage.ENGLISH);
    }

    /** Inputs giving each text the vector {@code [length, 1]} and each member the passages 0 and 1. */
    static RecordInputs inputs(List<String> asked) {
        return new RecordInputs() {
            @Override
            public String embedderIdentity() {
                return EMBEDDER;
            }

            @Override
            public float[] vectorOf(String writing) {
                asked.add("vectorOf " + writing);
                return new float[] {writing.length(), 1f};
            }

            @Override
            public void passagesOf(ListedSurvivor member, PassageConsumer passages) throws IOException {
                asked.add("passagesOf " + member.occurrence().value());
                for (int ordinal = 0; ordinal < 2; ordinal++) {
                    passages.accept(
                            ordinal, "passage " + ordinal + " of " + member.contentHash(), new float[] {ordinal, 2f});
                }
            }
        };
    }

    /** A line for a record: its kind and what places it. */
    static String labelOf(RetrievalRecord record) {
        RetrievalRecord.Placement place = record.placement();
        return switch (record.kind()) {
            case TOPIC_OVERVIEW -> "topic-overview " + place.topic();
            case SYNTHESIS_DOC -> "synthesis-doc " + place.cluster().orElseThrow();
            case MEMBER_PASSAGE -> "member-passage " + place.document().orElseThrow() + "#" + place.passage().getAsInt();
        };
    }

    /**
     * A plugin reading retrieval records, drawing from no tree, that writes each record it is taken down, as {@code
     * <name>:<label>}, in the list it shares, and keeps them. It writes {@code <name>/<list name>} at {@code finish}.
     */
    static final class Recording implements Plugin {

        private final String name;
        private final boolean takesPassages;
        private final List<String> heard;
        private final List<RetrievalRecord> taken = new ArrayList<>();
        private String failingAt;

        Recording(String name, boolean takesPassages, List<String> heard) {
            this.name = name;
            this.takesPassages = takesPassages;
            this.heard = heard;
        }

        /** This plugin, throwing an {@link IOException} at the first call of {@code call}: records, takes, finish. */
        Recording failingAt(String call) {
            this.failingAt = call;
            return this;
        }

        List<RetrievalRecord> taken() {
            return taken;
        }

        private void maybeThrow(String call) throws IOException {
            if (call.equals(failingAt)) {
                failingAt = null;
                throw new IOException("scripted at " + call);
            }
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String listName() {
            return name + ".jsonl";
        }

        @Override
        public boolean drawsFromTheTree() {
            return false;
        }

        @Override
        public Drawing drawing(DrawingContext context) {
            throw new UnsupportedOperationException("draws from no tree");
        }

        @Override
        public Delivery deliver(Path rendering, String generationRun, Path workingDirectory, Consumer<String> out) {
            return new Delivery(true, "delivered");
        }

        @Override
        public Optional<RetrievalRecords> records(DrawingContext context) throws IOException {
            maybeThrow("records");
            return Optional.of(new RetrievalRecords() {
                @Override
                public boolean takesMemberPassages() {
                    return takesPassages;
                }

                @Override
                public void take(RetrievalRecord record) throws IOException {
                    maybeThrow("take");
                    taken.add(record);
                    heard.add(name + ":" + labelOf(record));
                }

                @Override
                public void finish() throws IOException {
                    maybeThrow("finish");
                    Files.writeString(context.tree().resolve(name).resolve(listName()), taken.size() + "\n");
                }

                @Override
                public void close() {}
            });
        }
    }
}
