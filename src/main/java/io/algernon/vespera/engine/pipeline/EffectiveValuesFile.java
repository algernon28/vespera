package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.ledger.ReplacingMove;
import io.algernon.vespera.engine.synthesis.ClusterSynthesis;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

/**
 * {@code effective-values.properties}: what the jar would use where the profile names nothing, each value with where it
 * came from, written beside {@code vespera.db} for the control panel to read (ADR-330 §1).
 *
 * <p>The file is {@code java.util.Properties} in UTF-8 and holds a closed list of keys, each with its
 * {@code <key>.source} beside it, and nothing else. A key of the application's own file that nothing names, or one
 * bound blank, is left out with its source; a missing or blank address is instead the code's own, with the source
 * {@code code}, and is left out only where the address named has no host; and the context window and the language of
 * the deliverable are always written, as the code's own, with the source {@code code}. So the file never says an
 * empty value. Its values are model names, an image's name, numbers, durations, booleans and two addresses cut to
 * scheme, host and port, so no secret, path or text of a document reaches it. It is never a filter over what the
 * process holds.
 *
 * <p>{@code vespera run} and {@code vespera label} write it once the working-directory lock is held and past the last
 * refusal of their start, before their first step; {@code vespera publish} writes none. It is written whole to a
 * {@code .part} file and moved over the target, and one that cannot be written is logged at WARN and fails nothing. It
 * is never deleted. The sixteen presets of ADR-330 §9 are on the list, the resolution under the profile's own name,
 * {@code clusterResolution}.
 */
final class EffectiveValuesFile {

    private static final Logger LOG = LoggerFactory.getLogger(EffectiveValuesFile.class);

    /** The file's name, in the working directory beside the database file. */
    static final String FILE_NAME = "effective-values.properties";

    /** Where the configuration the profile's {@code embeddingModel} overrides is read. */
    private static final String EMBEDDING_MODEL = "spring.ai.ollama.embedding.options.model";

    /** Where the configuration the profile's {@code generationModel} overrides is read ({@link GenerationModel}). */
    private static final String GENERATION_MODEL = GenerationModel.CONFIGURED_DEFAULT_KEY;

    /** The label model's own key, and the chat model it falls back to (ADR-334 §2). */
    private static final String LABEL_MODEL = "vespera.label.model";

    private static final String CHAT_MODEL = "spring.ai.ollama.chat.options.model";

    /** The label model the code holds where configuration names neither key. */
    static final String THE_CODES_LABEL_MODEL = "qwen3:8b";

    /** The keys of the application's own file the control panel shows, in the order of ADR-330 §1. */
    private static final String[] AS_THE_APPLICATION_RESOLVES_THEM = {
        "vespera.docling.image",
        SidecarStartSettings.START_PROPERTY,
        SidecarStartSettings.WAIT_AT_MOST_PROPERTY,
        "vespera.progress-interval",
        // The presets of ADR-330 §9, in the order of its table: the neighbours first, then the fourteen from the OCR
        // preset to the sample's number from each band. The resolution is said under the profile's own name, above.
        Presets.NEIGHBOURS_OF_A_DOCUMENT,
        Presets.OCR_PRESET,
        Presets.TIMEOUTS_IN_A_ROW,
        Presets.SHINGLE_WORDS,
        Presets.PERMUTATIONS,
        Presets.BANDS_OF_A_SIGNATURE,
        Presets.ROWS_PER_BAND,
        Presets.SEED,
        Presets.NEAR_DUPLICATE_JACCARD,
        Presets.CONTAINMENT_INDEX,
        Presets.RARE_SHINGLE_SAMPLE,
        Presets.RARE_SHINGLE_HITS,
        Presets.TOP_SIMILARITIES,
        Presets.BANDS_IN_THE_SAMPLE,
        Presets.DOCUMENTS_FROM_EACH_BAND
    };

    private final Environment environment;
    private final Path workingDirectory;

    EffectiveValuesFile(Environment environment, Path workingDirectory) {
        this.environment = environment;
        this.workingDirectory = workingDirectory;
    }

    /** Writes the file over any there; a file that cannot be written is logged at WARN and fails nothing. */
    void write() {
        Path file = workingDirectory.resolve(FILE_NAME);
        Path part = workingDirectory.resolve(FILE_NAME + ".part");
        try {
            Properties properties = new Properties();
            properties.setProperty(
                    "written-at",
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)));
            for (Map.Entry<String, String[]> value : valuesBeneathTheProfile().entrySet()) {
                properties.setProperty(value.getKey(), value.getValue()[0]);
                properties.setProperty(value.getKey() + ".source", value.getValue()[1]);
            }
            try (Writer writer = Files.newBufferedWriter(part, StandardCharsets.UTF_8)) {
                properties.store(writer, null);
            }
            ReplacingMove.ATOMIC.intoPlace(part, file);
        } catch (IOException | RuntimeException cannotBeWritten) {
            LOG.warn("Could not write {}, so the control panel is not told what the jar would use: {}", file,
                    cannotBeWritten.toString());
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
                // a part file left behind is written over by the next invocation
            }
        }
    }

    /** Each value of the closed list that something names, with its source: {@code [value, source]}. */
    private Map<String, String[]> valuesBeneathTheProfile() {
        Map<String, String[]> values = new LinkedHashMap<>();
        named(values, "embeddingModel", EMBEDDING_MODEL);
        named(values, "generationModel", GENERATION_MODEL);
        values.put("generationContextWindow", new String[] {String.valueOf(ClusterSynthesis.CONTEXT_WINDOW), "code"});
        values.put("deliverableLanguage", new String[] {DeliverableLanguage.DEFAULT.code(), "code"});
        // What applies where the profile's clusterResolution names none: the preset as configuration resolves it.
        named(values, "clusterResolution", Presets.RESOLUTION);
        for (String key : AS_THE_APPLICATION_RESOLVES_THEM) {
            named(values, key, key);
        }
        address(values, "vespera.docling.base-url", SidecarStartSettings.DEFAULT_DOCLING);
        address(values, "spring.ai.ollama.base-url", SidecarStartSettings.DEFAULT_OLLAMA);
        labelModel(values);
        return values;
    }

    /**
     * The label model as {@code OllamaRelevanceLabeller} resolves it (ADR-334 §2): {@code vespera.label.model} where
     * configuration names it, else {@code spring.ai.ollama.chat.options.model}, each with the source of the layer that
     * named it, else {@value #THE_CODES_LABEL_MODEL} with the source {@code code}. Always written.
     */
    private void labelModel(Map<String, String[]> values) {
        for (String property : new String[] {LABEL_MODEL, CHAT_MODEL}) {
            String value = environment.getProperty(property);
            if (value != null && !value.isBlank()) {
                values.put(LABEL_MODEL, new String[] {value, sourceOf(property)});
                return;
            }
        }
        values.put(LABEL_MODEL, new String[] {THE_CODES_LABEL_MODEL, "code"});
    }

    /**
     * {@code key} as the application resolves {@code property}, with the source that names it; left out when blank or
     * unnamed.
     */
    private void named(Map<String, String[]> values, String key, String property) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            return;
        }
        values.put(key, new String[] {value, sourceOf(property)});
    }

    /**
     * An address cut to scheme, host and port, from configuration or, where none names it, the code's own; one that
     * has no host is left out.
     */
    private void address(Map<String, String[]> values, String property, String codesOwn) {
        String named = environment.getProperty(property);
        if (named == null || named.isBlank()) {
            values.put(property, new String[] {codesOwn, "code"});
            return;
        }
        String cut = schemeHostAndPortOf(named);
        if (cut != null) {
            values.put(property, new String[] {cut, sourceOf(property)});
        }
    }

    /**
     * The address as its scheme, host and port alone, so that a user, a password, a path and a query do not reach the
     * file; {@code null} where it has no host.
     */
    private static String schemeHostAndPortOf(String address) {
        try {
            URI uri = URI.create(address.strip());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException notAnAddress) {
            return null;
        }
    }

    /**
     * Where the value came from (ADR-330 §1): the first property source that names it. The jar's own
     * {@code application.yaml} is {@code shipped}; another file of application configuration, such as
     * {@code application-local.yaml}, {@code local-file}; the command line {@code command-line}; and an environment
     * variable, a system property or any other source a process is given, {@code environment}.
     */
    private String sourceOf(String property) {
        if (!(environment instanceof ConfigurableEnvironment configurable)) {
            return "code";
        }
        for (PropertySource<?> source : configurable.getPropertySources()) {
            if (source.getName().equals("configurationProperties") || !source.containsProperty(property)) {
                continue;
            }
            String name = source.getName();
            if (name.startsWith("Config resource")) {
                return name.contains("class path resource [application.yaml]")
                                || name.contains("class path resource [application.yml]")
                        ? "shipped"
                        : "local-file";
            }
            return name.startsWith("commandLineArgs") ? "command-line" : "environment";
        }
        return "code";
    }
}
