package io.algernon.vespera.ui;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Where each field of the window starts, and what of it reaches the child and the launch values file (ADR-344 §3).
 *
 * <p>A field starts from the first of the launch values file, the window's environment, {@code .env}, for the Docling
 * image alone the GPU image, and the jar's own {@code application.yaml} that names it, not blank; and says which. A
 * value is <em>kept</em> where it came from the launch values file or the operator changed it, and <em>passed</em> where
 * it is kept or the child would read another without it. The class reads nothing itself: it is handed the four
 * sources, so a window a test draws starts from what the test gives it.
 */
public final class StartingValues {

    /** Where a field's starting value came from. */
    public enum Source { LAUNCH_VALUE, ENVIRONMENT, DOT_ENV, GPU_PRESELECTION, APPLICATION_YAML, NONE }

    /** A field's starting value and where it came from; empty and {@link Source#NONE} where no source names it. */
    public record Starting(String value, Source source) {
    }

    /**
     * What the four sources say.
     *
     * @param launchValues by property, as the launch values file holds them
     * @param environment by variable
     * @param dotEnv by variable
     * @param applicationYaml by dotted property
     */
    public record Sources(Map<String, String> launchValues, Map<String, String> environment,
            Map<String, String> dotEnv, Map<String, String> applicationYaml) {

        /** No source names anything. */
        public static Sources none() {
            return new Sources(Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    /** The property of the converter's image, which alone is preselected as the GPU image. */
    static final String DOCLING_IMAGE = "vespera.docling.image";

    /** The converter's image {@code compose.gpu.yaml} runs, preselected where nothing above {@code application.yaml} names one (ADR-334 §11). */
    public static final String DOCLING_IMAGE_GPU =
            "vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0-r2";

    /** The converter's image {@code compose.yaml} and the jar's {@code application.yaml} run. */
    public static final String DOCLING_IMAGE_CPU =
            "vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2";

    private StartingValues() {
    }

    /** The starting value of {@code property}: the first source that names it, not blank (§3). */
    public static Starting of(String property, Sources sources) {
        String launched = sources.launchValues().get(property);
        if (launched != null && !launched.isBlank()) {
            return new Starting(launched, Source.LAUNCH_VALUE);
        }
        String variable = variableOf(property);
        String environment = sources.environment().get(variable);
        if (environment != null && !environment.isBlank()) {
            return new Starting(environment, Source.ENVIRONMENT);
        }
        String dotEnv = sources.dotEnv().get(variable);
        if (dotEnv != null && !dotEnv.isBlank()) {
            return new Starting(dotEnv, Source.DOT_ENV);
        }
        if (property.equals(DOCLING_IMAGE)) {
            return new Starting(DOCLING_IMAGE_GPU, Source.GPU_PRESELECTION);
        }
        String configured = sources.applicationYaml().get(property);
        if (configured != null && !configured.isBlank()) {
            return new Starting(configured, Source.APPLICATION_YAML);
        }
        return new Starting("", Source.NONE);
    }

    /** The variable Spring's relaxed binding reads {@code property} from: capitals, each {@code .} and {@code -} an underscore. */
    public static String variableOf(String property) {
        return property.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }

    /**
     * {@code .env} read as {@code NAME=value} lines, the white space around both taken off, a line starting with
     * {@code #} and a line with no {@code =} skipped, a value in matching single or double quotes taken without them, a
     * value holding {@code =} kept whole.
     */
    public static Map<String, String> readDotEnv(String text) {
        Map<String, String> read = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            String stripped = line.strip();
            int equals = stripped.indexOf('=');
            if (stripped.startsWith("#") || equals < 0) {
                continue;
            }
            String name = stripped.substring(0, equals).strip();
            String value = stripped.substring(equals + 1).strip();
            if (value.length() >= 2 && (value.charAt(0) == '"' || value.charAt(0) == '\'')
                    && value.charAt(value.length() - 1) == value.charAt(0)) {
                value = value.substring(1, value.length() - 1);
            }
            if (!name.isEmpty()) {
                read.put(name, value);
            }
        }
        return read;
    }

    /**
     * The jar's own {@code application.yaml} by dotted names, each scalar; a value holding {@code ${} is a placeholder
     * Spring resolves and is not taken.
     */
    public static Map<String, String> readApplicationYaml(String text) {
        Map<String, String> read = new LinkedHashMap<>();
        flatten("", new YAMLMapper().readTree(text), read);
        return read;
    }

    private static void flatten(String prefix, JsonNode node, Map<String, String> into) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                flatten(prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey(), entry.getValue(), into);
            }
        } else if (node.isValueNode() && !node.isNull() && !prefix.isEmpty()) {
            String value = node.asString();
            if (!value.contains("${")) {
                into.put(prefix, value);
            }
        }
    }

    /** Whether {@code value} is kept in the launch values file: not blank, and a launch value or changed by the operator (§3). */
    public static boolean remembered(String value, Starting starting) {
        return !value.isBlank() && (starting.source() == Source.LAUNCH_VALUE || !value.equals(starting.value()));
    }

    /**
     * Whether {@code value} is passed to the child: not blank, and either kept or not what the child reads with nothing
     * passed, the environment's, else {@code application.yaml}'s (§3).
     */
    public static boolean passed(String property, String value, Starting starting, Sources sources) {
        if (value.isBlank()) {
            return false;
        }
        if (remembered(value, starting)) {
            return true;
        }
        String environment = sources.environment().get(variableOf(property));
        String childReads = environment != null && !environment.isBlank() ? environment
                : sources.applicationYaml().get(property);
        return !value.equals(childReads);
    }
}
