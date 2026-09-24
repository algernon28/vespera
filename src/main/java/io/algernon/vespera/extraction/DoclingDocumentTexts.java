package io.algernon.vespera.extraction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

/**
 * A Docling response's extracted text (ADR-145): its text items and its tables' rows, in the reading
 * order of the JSON/{@code DoclingDocument} export {@link DoclingClient} requests, each read once. It is
 * the one reading of "extracted text" in the system -- the metrics and the no-text floor
 * ({@link ExtractedText}), the chunker and a document's title (ADR-029, ADR-106), and, through
 * {@link #lines}, {@code pipeline}'s shingler and seed pass all read it.
 *
 * <p><b>Reading order is Docling's {@code body} tree</b>, then its {@code furniture} (page headers and
 * footers), each child a reference into {@code texts}, {@code tables}, {@code groups} or
 * {@code pictures}. A group or a picture is read through to the text items under it. A response with
 * no {@code body} is read from {@code texts} in list order, as every reader did before ADR-145.
 *
 * <p><b>A table is read as one item per row</b>, under Docling's own {@code table} label: its cells
 * with text, joined by {@code "; "}, in column order. Each cell is read once, in the row it starts in,
 * so one spanning several columns or rows is not repeated. The table's own children are not read
 * again: they are the paragraphs inside its cells, which the cell's text already carries (measured on
 * the GesPOS corpus: 3,060 of 3,061).
 *
 * <p>An item with blank text is dropped; a missing {@code label} reads as {@code "text"}, Docling's
 * own generic fallback label, rather than failing the whole document over one under-described item.
 */
public final class DoclingDocumentTexts {

    /** Docling's own fallback label for a text item that carries no more specific one. */
    private static final String DEFAULT_LABEL = "text";

    /** Docling's own label for a table, and the one each of its rows is read under. */
    private static final String TABLE_LABEL = "table";

    /** What separates a row's cells; attached to the word before it, so it adds no word of its own. */
    private static final String CELL_SEPARATOR = "; ";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private DoclingDocumentTexts() {}

    /**
     * The document's extracted text as one string, an item per line -- what {@code pipeline} hands the
     * shingler and reads a seed's usability from.
     */
    public static String lines(String rawDoclingResponse) {
        return parse(rawDoclingResponse).stream().map(DocumentText::text).collect(Collectors.joining("\n"));
    }

    /** The document's text items and table rows, in reading order, or empty when the export carries none. */
    static List<DocumentText> parse(String rawDoclingResponse) {
        JsonNode content = MAPPER.readTree(rawDoclingResponse).path("document").path("json_content");
        List<DocumentText> items = new ArrayList<>();
        if (content.path("body").path("children").isArray()) {
            Set<String> read = new HashSet<>();
            readChildren(content, content.path("body"), items, read);
            readChildren(content, content.path("furniture"), items, read);
        } else {
            content.path("texts").valueStream().forEach(item -> addText(item, items));
        }
        return List.copyOf(items);
    }

    private static void readChildren(JsonNode content, JsonNode node, List<DocumentText> items, Set<String> read) {
        for (JsonNode child : node.path("children")) {
            String ref = child.path("$ref").asString("");
            // A node reachable twice is read the first time only; that also ends any cycle.
            if (!read.add(ref)) {
                continue;
            }
            JsonNode item = resolve(content, ref);
            if (item.isMissingNode()) {
                continue;
            }
            if (ref.startsWith("#/tables/")) {
                addRows(item, items);
            } else {
                if (ref.startsWith("#/texts/")) {
                    addText(item, items);
                }
                readChildren(content, item, items, read);
            }
        }
    }

    /** {@code #/texts/3} is the fourth entry of {@code content.texts}; anything else resolves to nothing. */
    private static JsonNode resolve(JsonNode content, String ref) {
        String[] parts = ref.split("/");
        if (parts.length != 3 || !"#".equals(parts[0]) || !parts[2].chars().allMatch(Character::isDigit)) {
            return MissingNode.getInstance();
        }
        return content.path(parts[1]).path(Integer.parseInt(parts[2]));
    }

    private static void addText(JsonNode item, List<DocumentText> items) {
        String text = item.path("text").asString("");
        if (!text.isBlank()) {
            items.add(new DocumentText(text, item.path("label").asString(DEFAULT_LABEL)));
        }
    }

    private static void addRows(JsonNode table, List<DocumentText> items) {
        Map<Integer, List<JsonNode>> rows = new TreeMap<>();
        for (JsonNode cell : table.path("data").path("table_cells")) {
            if (!cell.path("text").asString("").isBlank()) {
                rows.computeIfAbsent(cell.path("start_row_offset_idx").asInt(0), row -> new ArrayList<>())
                        .add(cell);
            }
        }
        for (List<JsonNode> row : rows.values()) {
            String text = row.stream()
                    .sorted(Comparator.comparingInt(cell -> cell.path("start_col_offset_idx").asInt(0)))
                    .map(cell -> cell.path("text").asString("").strip())
                    .collect(Collectors.joining(CELL_SEPARATOR));
            items.add(new DocumentText(text, TABLE_LABEL));
        }
    }
}
