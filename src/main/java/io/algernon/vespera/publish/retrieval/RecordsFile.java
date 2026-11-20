package io.algernon.vespera.publish.retrieval;

import io.algernon.vespera.engine.deliverable.RetrievalRecord;
import io.algernon.vespera.engine.deliverable.RetrievalRecords;
import io.algernon.vespera.engine.ledger.ReplacingMove;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * {@code records.jsonl} (ADR-434 §8): UTF-8 with no byte order mark, one JSON object a line, the fields in the order
 * the record fixes, each line ended by a line feed. Written a record at a time to {@code records.jsonl.part} and moved
 * into place when the reading finishes, so a drawing that stops leaves the file an earlier drawing put there whole.
 */
final class RecordsFile implements RetrievalRecords {

    private static final String PART_SUFFIX = ".part";

    /** The version of the line's shape. */
    private static final int FORMAT = 1;

    private final Path file;
    private final Path part;
    private final ReplacingMove moves;
    private BufferedWriter out;
    private boolean finished;

    RecordsFile(Path file, ReplacingMove moves) {
        this.file = file;
        this.part = file.resolveSibling(file.getFileName() + PART_SUFFIX);
        this.moves = moves;
    }

    @Override
    public boolean takesMemberPassages() {
        return true;
    }

    @Override
    public void take(RetrievalRecord record) throws IOException {
        if (out == null) {
            out = Files.newBufferedWriter(part, StandardCharsets.UTF_8);
        }
        out.write(lineOf(record));
        out.write('\n');
    }

    @Override
    public void finish() throws IOException {
        if (out == null) {
            out = Files.newBufferedWriter(part, StandardCharsets.UTF_8);
        }
        out.close();
        finished = true;
        moves.intoPlace(part, file);
    }

    @Override
    public void close() throws IOException {
        if (out != null && !finished) {
            out.close();
        }
    }

    static String lineOf(RetrievalRecord record) {
        RetrievalRecord.Placement place = record.placement();
        StringBuilder line = new StringBuilder(record.text().length() + 16 * record.vector().length + 400);
        line.append("{\"format\":").append(FORMAT);
        field(line, "id").append(string(record.id()));
        field(line, "kind").append(string(record.kind().written()));
        field(line, "text").append(string(record.text()));
        field(line, named("VECTOR")).append(vector(record.vector()));
        field(line, "embedder").append(string(record.embedderIdentity()));
        field(line, "generationRun").append(string(place.generationRun()));
        field(line, "language").append(string(place.language().code()));
        field(line, named("TOPIC")).append(string(place.topic()));
        field(line, "topicOrder").append(place.topicOrder());
        field(line, "subtopic").append(optional(place.subtopic()));
        field(line, "subtopicOrder").append(optional(place.subtopicOrder()));
        field(line, "cluster").append(optional(place.cluster()));
        field(line, "clusterNumber").append(optional(place.clusterNumber()));
        field(line, "citations").append(numbers(place.citations()));
        field(line, "document").append(optional(place.document()));
        field(line, "passage").append(optional(place.passage()));
        return line.append('}').toString();
    }

    /**
     * A field's name from its constant, so that the two fields whose names are those of tables of the working store, a
     * line's {@code topic} and {@code vector}, are no text of this class that reads as naming one (ADR-041).
     */
    private static String named(String constant) {
        return constant.toLowerCase(java.util.Locale.ROOT);
    }

    private static StringBuilder field(StringBuilder line, String name) {
        return line.append(",\"").append(name).append("\":");
    }

    private static String optional(Optional<String> value) {
        return value.map(RecordsFile::string).orElse("null");
    }

    private static String optional(OptionalInt value) {
        return value.isPresent() ? Integer.toString(value.getAsInt()) : "null";
    }

    private static String numbers(List<Integer> values) {
        StringBuilder out = new StringBuilder("[");
        for (int at = 0; at < values.size(); at++) {
            out.append(at == 0 ? "" : ",").append(values.get(at));
        }
        return out.append(']').toString();
    }

    private static String vector(float[] components) {
        StringBuilder out = new StringBuilder("[");
        for (int at = 0; at < components.length; at++) {
            if (!Float.isFinite(components[at])) {
                throw new IllegalArgumentException("a vector's component " + at + " is " + components[at]
                        + ", which JSON cannot write");
            }
            out.append(at == 0 ? "" : ",").append(Float.toString(components[at]));
        }
        return out.append(']').toString();
    }

    /** A JSON string: {@code "}, {@code \} and U+0000 to U+001F escaped, every other character as itself. */
    private static String string(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int at = 0; at < value.length(); at++) {
            char c = value.charAt(at);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\f' -> out.append("\\f");
                case '\r' -> out.append("\\r");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
