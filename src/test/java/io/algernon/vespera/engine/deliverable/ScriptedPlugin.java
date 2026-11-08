package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import io.algernon.vespera.engine.synthesis.Unwritten;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A plugin that draws nothing and writes down every call it hears, in one list it may share with other plugins, so
 * a test reads the order the calls of several plugins came in (ADR-333 §2, §4). It can be told to throw at the first
 * call of a kind, an {@link IOException} or a {@link RuntimeException} or an {@link Error}, to stand for a drawing
 * that fails (ADR-333 §11).
 *
 * <p>Each call is written down as {@code <name>:<call>}, the call being one of {@value #DRAWING}, {@value
 * #PARTITION_STARTED}, {@value #ENTRIES_OF}, {@value #START}, {@value #COPY_SHOWN}, {@value #PICTURE}, {@value
 * #NOT_SHOWN}, {@value #END}, {@value #ENTRIES_CLOSED}, {@value #CLUSTER_DRAWN}, {@value #PARTITION_ENDED}, {@value
 * #FINISH} or {@value #CLOSE}, followed where it has them by what it was handed, in words of the test's.
 *
 * <p>It writes beneath its own folder alone, adding that folder to {@code written} as ADR-312 §4 asks of a plugin,
 * unless told {@link #addingNoFolderOfItsOwn()}, so that a test can tell what the drawing itself counts as written
 * from what a plugin adds; and writes one file there, {@value #ITS_FILE}, at {@code finish}, so that a test can tell
 * a drawing that went to its end from one that did not.
 */
public final class ScriptedPlugin implements Plugin {

    public static final String DRAWING = "drawing";
    public static final String PARTITION_STARTED = "partitionStarted";
    public static final String ENTRIES_OF = "entriesOf";
    public static final String START = "start";
    public static final String COPY_SHOWN = "copyShown";
    public static final String PICTURE = "picture";
    public static final String NOT_SHOWN = "notShown";
    public static final String END = "end";
    public static final String ENTRIES_CLOSED = "entries.close";
    public static final String CLUSTER_DRAWN = "clusterDrawn";
    public static final String PARTITION_ENDED = "partitionEnded";
    public static final String FINISH = "finish";
    public static final String CLOSE = "close";

    /** What the plugin writes in its folder at {@code finish}. */
    public static final String ITS_FILE = "finished.txt";

    /** What a stopped plugin's own throw is, where a test asks for an {@link Error}. */
    public static final class ScriptedError extends Error {
        public ScriptedError(String message) {
            super(message);
        }
    }

    /** How a scripted failure is thrown. */
    public enum Failure {
        IO_EXCEPTION("IOException"),
        RUNTIME_EXCEPTION("IllegalStateException"),
        ERROR("ScriptedError");

        /** The simple name of the type thrown, as the closing line names it (ADR-333 §11). */
        public final String type;

        Failure(String type) {
            this.type = type;
        }
    }

    private final String name;
    private final List<String> heard;
    private String failingAt;
    private Failure failure;
    private Plugin.DrawingContext context;
    private boolean addsItsFolder = true;

    /** A plugin named {@code name}, writing down what it hears in {@code heard}. */
    public ScriptedPlugin(String name, List<String> heard) {
        this.name = name;
        this.heard = heard;
    }

    /** This plugin, told to add nothing to {@code written}, its own folder included. */
    public ScriptedPlugin addingNoFolderOfItsOwn() {
        this.addsItsFolder = false;
        return this;
    }

    /** This plugin, told to throw {@code failure} at the first call of {@code call}. */
    public ScriptedPlugin failingAt(String call, Failure failure) {
        this.failingAt = call;
        this.failure = failure;
        return this;
    }

    /** This plugin, told to throw nothing. */
    public ScriptedPlugin healthy() {
        this.failingAt = null;
        this.failure = null;
        return this;
    }

    /** What the drawing handed this plugin, the last time it opened its drawing. */
    public Plugin.DrawingContext context() {
        return context;
    }

    /** The calls this plugin heard, in order, without its name. */
    public List<String> calls() {
        String prefix = name + ":";
        return heard.stream().filter(call -> call.startsWith(prefix)).map(call -> call.substring(prefix.length()))
                .toList();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String listName() {
        return "scripted.json";
    }

    @Override
    public Drawing drawing(Plugin.DrawingContext context) throws IOException {
        this.context = context;
        heard(DRAWING, DRAWING);
        if (addsItsFolder) {
            context.written().add(name);
        }
        return new ScriptedDrawing(context.tree().resolve(name));
    }

    @Override
    public Delivery deliver(Path rendering, String generationRun, Path workingDirectory, Consumer<String> out) {
        return new Delivery(true, name + " delivered " + generationRun);
    }

    /** Writes {@code call} down as heard, then throws where this plugin was told to throw at it, the first time. */
    private void heard(String call, String said) throws IOException {
        heard.add(name + ":" + said);
        if (call.equals(failingAt)) {
            Failure thrown = failure;
            failingAt = null;
            switch (thrown) {
                case IO_EXCEPTION -> throw new IOException("scripted at " + call);
                case RUNTIME_EXCEPTION -> throw new IllegalStateException("scripted at " + call);
                case ERROR -> throw new ScriptedError("scripted at " + call);
            }
        }
    }

    private final class ScriptedDrawing implements Drawing {

        private final Path folder;

        ScriptedDrawing(Path folder) {
            this.folder = folder;
        }

        @Override
        public void partitionStarted(ListedPartition partition, int partitionCount) throws IOException {
            heard(PARTITION_STARTED, PARTITION_STARTED + " " + partition.partitionOrder() + "/" + partitionCount);
        }

        @Override
        public Entries entriesOf(
                ListedPartition partition, RecordedCluster recorded, List<ListedSurvivor> members, Path clusterFolder,
                Copies copies) throws IOException {
            heard(ENTRIES_OF, ENTRIES_OF + " " + recorded.cluster().clusterOrder() + " of " + members.size());
            return new ScriptedEntries(recorded.cluster().clusterOrder());
        }

        @Override
        public void clusterDrawn(
                ListedPartition partition, RecordedCluster recorded, Optional<SynthesisDoc> doc, Unwritten why,
                int entryCount, Optional<ClusterPage.PageBreaks> breaks, Entries entries, boolean writingFinished)
                throws IOException {
            boolean its = entries instanceof ScriptedEntries mine && mine.cluster == recorded.cluster().clusterOrder();
            heard(CLUSTER_DRAWN, CLUSTER_DRAWN + " " + recorded.cluster().clusterOrder() + " of " + entryCount
                    + (doc.isPresent() ? " written" : " unwritten") + (its ? " with its entries" : " with other entries"));
        }

        @Override
        public void partitionEnded() throws IOException {
            heard(PARTITION_ENDED, PARTITION_ENDED);
        }

        @Override
        public void finish(Path manifest) throws IOException {
            heard(FINISH, FINISH + " " + manifest.getFileName() + (Files.isRegularFile(manifest) ? " standing" : " missing"));
            Files.writeString(Files.createDirectories(folder).resolve(ITS_FILE), "finished");
        }

        @Override
        public void close() throws IOException {
            heard(CLOSE, CLOSE);
        }
    }

    private final class ScriptedEntries implements Entries {

        private final int cluster;

        ScriptedEntries(int cluster) {
            this.cluster = cluster;
        }

        @Override
        public void start(int number, Optional<ListedSurvivor> entry) throws IOException {
            heard(START, START + " " + number + (entry.isPresent() ? " held" : " no longer held"));
        }

        @Override
        public void copyShown() throws IOException {
            heard(COPY_SHOWN, COPY_SHOWN);
        }

        @Override
        public void picture(String digest, boolean png, ListedPicture picture) throws IOException {
            heard(PICTURE, PICTURE + (png ? " png" : " jpeg"));
        }

        @Override
        public void notShown(int notShown) throws IOException {
            heard(NOT_SHOWN, NOT_SHOWN + " " + notShown);
        }

        @Override
        public void end() throws IOException {
            heard(END, END);
        }

        @Override
        public void close() throws IOException {
            heard(ENTRIES_CLOSED, ENTRIES_CLOSED);
        }
    }
}
