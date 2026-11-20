package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.ledger.ReplacingMove;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import io.algernon.vespera.engine.synthesis.Unwritten;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Every plugin's drawing of the tree being drawn, called as one (ADR-333 §2, §4, §11): each call is made to every
 * plugin still drawing, in the order of {@link Plugin#name()}, and {@link #entriesOf} hands {@code ClusterPage} one
 * {@link Entries} that passes each call on to the entries each plugin's own drawing returned.
 *
 * <p><b>A plugin's failed drawing stops that plugin alone.</b> An {@link IOException} or a {@link RuntimeException} out
 * of any call of a plugin's drawing or its entries, or out of opening its drawing, is caught here: the drawing is
 * closed, what closing it throws swallowed, it is called no more, {@link DeliverableProgress} is told its name and the
 * type of what it threw, and the drawing goes on. An {@link Error} is not caught.
 *
 * <p><b>This class writes the state of each drawing</b>, {@value #STATE_FILE_NAME} in the plugin's folder, in place:
 * {@value #DRAWING} as it opens the plugin's drawing, {@value #DRAWN} once its {@code finish} and {@code close} return,
 * {@value #FAILED} when it is stopped. No plugin writes it, and nothing deletes it (ADR-333 §11).
 */
final class PluginDrawings implements AutoCloseable {

    /** The file of a plugin's folder holding the state of its last drawing. */
    static final String STATE_FILE_NAME = Plugin.DRAWING_STATE_FILE_NAME;

    private static final String DRAWING = "drawing";
    private static final String DRAWN = Plugin.DRAWN;
    private static final String FAILED = "failed";

    private final List<Slot> slots = new ArrayList<>();
    private final DeliverableProgress progress;

    /** One plugin, its folder and its open drawing. */
    private static final class Slot {
        final Plugin plugin;
        final Path folder;
        Drawing drawing;
        RetrievalRecords records;
        boolean takesPassages;
        boolean active = true;
        boolean closed;

        Slot(Plugin plugin, Path folder) {
            this.plugin = plugin;
            this.folder = folder;
        }
    }

    /** One call to a plugin's entries. */
    @FunctionalInterface
    private interface EntriesCall {
        void on(Entries entries) throws IOException;
    }

    /**
     * Opens the drawing of each of {@code plugins} that draws from the tree, where {@code drawsTheTree}, and the
     * reading of the retrieval records of each that reads them, in the order of their names, each in its folder at the
     * tree's root, which it adds to {@code written} (ADR-434 §4).
     */
    PluginDrawings(
            List<Plugin> plugins,
            boolean drawsTheTree,
            Path tree,
            String generationRun,
            ReplacingMove moves,
            Set<String> written,
            DeliverableLanguage language,
            DeliverableProgress progress) {
        this.progress = progress;
        List<Plugin> inOrder = plugins.stream().sorted(Comparator.comparing(Plugin::name)).toList();
        try {
            for (Plugin plugin : inOrder) {
                Slot slot = new Slot(plugin, tree.resolve(plugin.name()));
                slots.add(slot);
                try {
                    Files.createDirectories(slot.folder);
                    written.add(plugin.name());
                    writeState(slot, DRAWING);
                    Plugin.DrawingContext context =
                            new Plugin.DrawingContext(tree, generationRun, moves, written, language);
                    if (drawsTheTree && plugin.drawsFromTheTree()) {
                        slot.drawing = plugin.drawing(context);
                    }
                    slot.records = plugin.records(context).orElse(null);
                    if (slot.records != null) {
                        slot.takesPassages = slot.records.takesMemberPassages();
                    }
                } catch (IOException | RuntimeException e) {
                    fail(slot, e);
                }
            }
        } catch (Error stopped) {
            close();
            throw stopped;
        }
    }

    /** The names of the plugins this drawing opened, whether or not they went on. */
    List<String> names() {
        return slots.stream().map(slot -> slot.plugin.name()).toList();
    }

    void topicStarted(
            ListedTopic topic, int topicCount, List<ListedSubtopic> subtopics, ListedOverview overview) {
        for (Slot slot : slots) {
            callDrawing(slot, () -> slot.drawing.topicStarted(topic, topicCount, subtopics, overview));
        }
    }

    /** One page of a topic's see-also lines, handed to every plugin still drawing. */
    void seeAlso(ListedTopic topic, List<SeeAlsoLine> lines) {
        for (Slot slot : slots) {
            callDrawing(slot, () -> slot.drawing.seeAlso(topic, lines));
        }
    }

    /** Opens one cluster's entries in every plugin still drawing, and answers the one set that passes each call on. */
    Entries entriesOf(
            ListedTopic topic, RecordedCluster recorded, List<ListedSurvivor> members, Path clusterFolder,
            Copies copies) {
        Fanned fanned = new Fanned();
        for (Slot slot : slots) {
            if (!slot.active || slot.drawing == null) {
                continue;
            }
            Entries[] own = new Entries[1];
            call(slot, () -> own[0] = slot.drawing.entriesOf(topic, recorded, members, clusterFolder, copies));
            if (slot.active) {
                fanned.add(slot, own[0]);
            }
        }
        return fanned;
    }

    /** Tells each plugin the cluster is drawn, handing it back the entries its own {@code entriesOf} returned. */
    void clusterDrawn(
            ListedTopic topic,
            RecordedCluster recorded,
            Optional<SynthesisDoc> doc,
            Unwritten why,
            int entryCount,
            Optional<ClusterPage.PageBreaks> breaks,
            Entries entries,
            boolean writingFinished) {
        Fanned fanned = (Fanned) entries;
        for (int at = 0; at < fanned.slots.size(); at++) {
            Slot slot = fanned.slots.get(at);
            Entries own = fanned.own.get(at);
            callDrawing(slot, () -> slot.drawing.clusterDrawn(
                    topic, recorded, doc, why, entryCount, breaks, own, writingFinished));
        }
    }

    void topicEnded() {
        for (Slot slot : slots) {
            callDrawing(slot, () -> slot.drawing.topicEnded());
        }
    }

    /**
     * Finishes each drawing once {@code documents.csv} stands, {@code manifest} being null where no tree is drawn, and
     * each reading of the records, then closes them; a plugin that gets so far is drawn.
     */
    void finish(Path manifest) {
        for (Slot slot : slots) {
            callDrawing(slot, () -> slot.drawing.finish(manifest));
            call(slot, () -> {
                if (slot.records != null) {
                    slot.records.finish();
                }
            });
        }
        for (Slot slot : slots) {
            if (!slot.active) {
                continue;
            }
            slot.closed = true;
            try {
                if (slot.drawing != null) {
                    slot.drawing.close();
                }
                if (slot.records != null) {
                    slot.records.close();
                }
                writeStateOrFail(slot, DRAWN);
            } catch (IOException | RuntimeException e) {
                fail(slot, e);
            }
        }
    }

    /** Closes what is still open, where the drawing stopped before {@link #finish}; no state is written. */
    @Override
    public void close() {
        for (Slot slot : slots) {
            closeQuietly(slot);
        }
    }

    /** Whether some plugin still active reads retrieval records. */
    boolean readsRecords() {
        return slots.stream().anyMatch(slot -> slot.active && slot.records != null);
    }

    /** Whether some plugin still active reading retrieval records takes member passages. */
    boolean takesMemberPassages() {
        return slots.stream().anyMatch(slot -> slot.active && slot.records != null && slot.takesPassages);
    }

    /** Hands {@code record} to each plugin still active that reads it, in the order of their names. */
    void take(RetrievalRecord record) {
        boolean passage = record.kind() == RetrievalRecord.Kind.MEMBER_PASSAGE;
        for (Slot slot : slots) {
            if (slot.records != null && (!passage || slot.takesPassages)) {
                call(slot, () -> slot.records.take(record));
            }
        }
    }

    /** Stops each plugin still active that reads retrieval records, composing them having failed. */
    void recordsFailed(Exception thrown) {
        for (Slot slot : slots) {
            if (slot.records != null) {
                fail(slot, thrown);
            }
        }
    }

    private void callDrawing(Slot slot, DrawingCall call) {
        if (slot.drawing != null) {
            call(slot, call);
        }
    }

    private void call(Slot slot, DrawingCall call) {
        if (!slot.active) {
            return;
        }
        try {
            call.run();
        } catch (IOException | RuntimeException e) {
            fail(slot, e);
        }
    }

    /** One call to a plugin's drawing. */
    @FunctionalInterface
    private interface DrawingCall {
        void run() throws IOException;
    }

    /** Stops {@code slot}'s plugin: its drawing closed, its state failed, the progress told. */
    private void fail(Slot slot, Exception thrown) {
        if (!slot.active) {
            return;
        }
        slot.active = false;
        closeQuietly(slot);
        try {
            writeState(slot, FAILED);
        } catch (IOException | RuntimeException unwritable) {
            // A plugin whose folder cannot be written has no state to say so; the closing line says it (ADR-333 Known limits).
        }
        progress.pluginDrawingFailed(slot.plugin.name(), thrown.getClass().getSimpleName());
    }

    private void writeStateOrFail(Slot slot, String state) {
        try {
            writeState(slot, state);
        } catch (IOException | RuntimeException e) {
            fail(slot, e);
        }
    }

    private static void closeQuietly(Slot slot) {
        if (slot.closed) {
            return;
        }
        slot.closed = true;
        if (slot.drawing != null) {
            try {
                slot.drawing.close();
            } catch (IOException | RuntimeException swallowed) {
                // What closing a stopped drawing throws is not told (ADR-333 §11).
            }
        }
        if (slot.records != null) {
            try {
                slot.records.close();
            } catch (IOException | RuntimeException swallowed) {
                // As above.
            }
        }
    }

    private static void closeQuietly(Entries entries) {
        try {
            entries.close();
        } catch (IOException | RuntimeException swallowed) {
            // As above.
        }
    }

    private static void writeState(Slot slot, String state) throws IOException {
        Files.writeString(slot.folder.resolve(STATE_FILE_NAME), state + "\n", StandardCharsets.UTF_8);
    }

    /** The one set of entries {@code ClusterPage} writes through, passing each call on to every plugin still drawing. */
    private final class Fanned implements Entries {

        private final List<Slot> slots = new ArrayList<>();
        private final List<Entries> own = new ArrayList<>();
        private final List<Boolean> closedOwn = new ArrayList<>();

        void add(Slot slot, Entries entries) {
            slots.add(slot);
            own.add(entries);
            closedOwn.add(false);
        }

        private void each(EntriesCall call) {
            for (int at = 0; at < slots.size(); at++) {
                Slot slot = slots.get(at);
                if (!slot.active) {
                    continue;
                }
                Entries entries = own.get(at);
                try {
                    call.on(entries);
                } catch (IOException | RuntimeException e) {
                    fail(slot, e);
                    if (!closedOwn.get(at)) {
                        closedOwn.set(at, true);
                        closeQuietly(entries);
                    }
                }
            }
        }

        @Override
        public void start(int number, Optional<ListedSurvivor> entry) {
            each(entries -> entries.start(number, entry));
        }

        @Override
        public void copyShown() {
            each(Entries::copyShown);
        }

        @Override
        public void picture(String digest, boolean png, ListedPicture picture) {
            each(entries -> entries.picture(digest, png, picture));
        }

        @Override
        public void notShown(int notShown) {
            each(entries -> entries.notShown(notShown));
        }

        @Override
        public void end() {
            each(Entries::end);
        }

        @Override
        public void close() {
            for (int at = 0; at < slots.size(); at++) {
                if (closedOwn.get(at)) {
                    continue;
                }
                closedOwn.set(at, true);
                Slot slot = slots.get(at);
                if (!slot.active) {
                    closeQuietly(own.get(at));
                    continue;
                }
                Entries entries = own.get(at);
                call(slot, entries::close);
            }
        }
    }
}
