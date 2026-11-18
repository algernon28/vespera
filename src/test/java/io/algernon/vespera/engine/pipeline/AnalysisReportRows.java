package io.algernon.vespera.engine.pipeline;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * The rows of an analysis report's table as its JSON holds them (ADR-362 §9), read so that a claim compares values and
 * not their spelling: a text as itself, a number as a {@code double}, whether written {@code 3}, {@code 3.0} or {@code
 * 0.500}, and {@code null} as null.
 */
final class AnalysisReportRows {

    private AnalysisReportRows() {}

    /** The rows of {@code section}'s table, each a list of its cells in the columns' order. */
    static List<List<Object>> of(JsonNode section) {
        List<List<Object>> rows = new ArrayList<>();
        for (JsonNode row : section.get("table").get("rows")) {
            List<Object> cells = new ArrayList<>();
            for (JsonNode cell : row) {
                cells.add(cell.isNull() ? null : cell.isNumber() ? (Object) cell.asDouble() : cell.asString());
            }
            rows.add(cells);
        }
        return rows;
    }
}
