package io.algernon.vespera.engine.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Checks a JSON instance against one of the schemas in {@code docs/report} with the eight keywords a schema may use
 * and no library (ADR-362 section 3): type, properties, required, items, enum, const, additionalProperties and
 * description.
 */
final class SchemaWalker {

    /** The keywords a schema may use, and no other (ADR-362 section 3). */
    static final Set<String> KEYWORDS =
            Set.of("type", "properties", "required", "items", "enum", "const", "additionalProperties", "description");

    private SchemaWalker() {}

    /** Each place where {@code schema} uses a keyword outside the eight, or leaves a position undescribed. */
    static List<String> keywordProblems(JsonNode schema) {
        List<String> problems = new ArrayList<>();
        keywords(schema, "$", problems);
        return problems;
    }

    /** Each place where {@code instance} is not what {@code schema} says; empty where it follows it. */
    static List<String> problems(JsonNode instance, JsonNode schema) {
        List<String> problems = new ArrayList<>();
        validate(instance, schema, "$", problems);
        return problems;
    }

    private static void keywords(JsonNode schema, String path, List<String> problems) {
        schema.propertyNames().forEach(name -> {
            if (!KEYWORDS.contains(name)) {
                problems.add(path + " uses the keyword " + name);
            }
        });
        if (!schema.has("description")) {
            problems.add(path + " is not described");
        }
        if (schema.has("properties")) {
            schema.get("properties").propertyNames()
                    .forEach(name -> keywords(schema.get("properties").get(name), path + "." + name, problems));
        }
        if (schema.has("items")) {
            keywords(schema.get("items"), path + "[]", problems);
        }
    }

    private static void validate(JsonNode instance, JsonNode schema, String path, List<String> problems) {
        if (schema.has("type")) {
            List<String> types = new ArrayList<>();
            if (schema.get("type").isArray()) {
                schema.get("type").forEach(type -> types.add(type.asString()));
            } else {
                types.add(schema.get("type").asString());
            }
            if (types.stream().noneMatch(type -> isOf(instance, type))) {
                problems.add(path + " is not of type " + types + ": " + instance);
                return;
            }
        }
        if (schema.has("enum")) {
            boolean found = false;
            for (JsonNode allowed : schema.get("enum")) {
                found |= allowed.equals(instance);
            }
            if (!found) {
                problems.add(path + " is none of the values allowed: " + instance);
            }
        }
        if (schema.has("const") && !schema.get("const").equals(instance)) {
            problems.add(path + " is not " + schema.get("const"));
        }
        if (instance.isObject() && schema.has("properties")) {
            JsonNode properties = schema.get("properties");
            instance.propertyNames().forEach(name -> {
                if (!properties.has(name)) {
                    problems.add(path + "." + name + " is written and the schema does not name it");
                } else {
                    validate(instance.get(name), properties.get(name), path + "." + name, problems);
                }
            });
            if (schema.has("required")) {
                schema.get("required").forEach(name -> {
                    if (!instance.has(name.asString())) {
                        problems.add(path + "." + name.asString() + " is required and not written");
                    }
                });
            }
        }
        if (instance.isArray() && schema.has("items")) {
            for (int i = 0; i < instance.size(); i++) {
                validate(instance.get(i), schema.get("items"), path + "[" + i + "]", problems);
            }
        }
    }

    private static boolean isOf(JsonNode instance, String type) {
        return switch (type) {
            case "object" -> instance.isObject();
            case "array" -> instance.isArray();
            case "string" -> instance.isString();
            case "integer" -> instance.isIntegralNumber();
            case "number" -> instance.isNumber();
            case "boolean" -> instance.isBoolean();
            case "null" -> instance.isNull();
            default -> false;
        };
    }
}
