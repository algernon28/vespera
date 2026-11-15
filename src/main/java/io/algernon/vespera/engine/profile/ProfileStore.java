package io.algernon.vespera.engine.profile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * {@code profile.yaml}, read and written (ADR-061).
 *
 * <p>It lives in the working directory beside the database, not in the corpus (ADR-054): census has
 * to be able to run against a read-only mount, and a file the operator edits has no business inside
 * the archive being curated.
 *
 * <p><b>The reader is strict about shape and tolerant about content</b> (ADR-120). An unknown key
 * fails the load, because a key nothing reads is a typo nobody would ever be told about. A value the
 * key cannot use does not fail the load: it is carried as written and read as unreadable at the point
 * of use, which is a state of its own beside unset, and the closing line names it and quotes it back.
 *
 * <p>That split is the whole of ADR-120, and it is a departure from ADR-061, which asked for the
 * value to be typed here and to throw on a mismatch. The reason is ADR-047: this is a file a person
 * edits between every invocation, so one mistyped character must not cost the pass that was about to
 * tell them what to write. What must never happen is the third thing — carrying on with the key
 * silently unset, gating on it as though nobody had answered, and saying nothing — and that is what
 * the unreadable state exists to prevent.
 *
 * <p>Writing is always read-modify-write, which is what makes ADR-062's merge fall out of
 * {@link Profile}'s own constructor rather than needing logic here: load, and every key the code has
 * since learned about is present and unset; save, and every answer already in the file is still
 * exactly as it was.
 */
@Component
public class ProfileStore {

    /** The profile's name in the working directory, fixed by ADR-054. */
    static final String FILE_NAME = "profile.yaml";

    private static final Logger LOG = LoggerFactory.getLogger(ProfileStore.class);

    /** The two keys the translation step read, which a file an earlier version wrote may still name (ADR-392 section 4). */
    private static final List<String> RETIRED_KEYS = List.of("translationSimilarityFloor", "preferredLanguages");

    /** Names the retired keys to the reader as ones to pass over, so that {@link Profile} holds no component for them. */
    @JsonIgnoreProperties({"translationSimilarityFloor", "preferredLanguages"})
    private abstract static class RetiredKeysPassedOver {}

    private static final YAMLMapper YAML = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .addMixIn(Profile.class, RetiredKeysPassedOver.class)
            .build();

    /** The files this process has already warned of, by their path: the line is written once for each (ADR-392 section 4). */
    private static final Set<Path> WARNED_OF = ConcurrentHashMap.newKeySet();

    private final Path file;

    public ProfileStore(@Value("${vespera.working-dir}") Path workingDirectory) {
        this.file = workingDirectory.resolve(FILE_NAME);
    }

    /** Where the profile is, for an operator who has to be told which file to edit. */
    public Path file() {
        return file;
    }

    /**
     * The profile as it stands, with every key the code knows about present — including the keys the
     * file does not mention yet, which arrive unset.
     *
     * <p>A corpus with no profile yet is not a special case: it loads as the same complete skeleton
     * of unset keys that an empty file would.
     *
     * @throws MisshapenProfileException when the file is not in the shape a profile takes — a key
     *     nobody knows, a key written flat, or text that is not YAML — carrying the one line the
     *     operator is told (#321). Jackson's own message named a Java type and no file.
     */
    public Profile load() {
        if (!Files.exists(file)) {
            return Profile.skeleton();
        }
        String yaml;
        try {
            yaml = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the profile at " + file, e);
        }
        if (yaml.isBlank()) {
            return Profile.skeleton();
        }
        Profile profile;
        try {
            profile = YAML.readValue(yaml, Profile.class);
        } catch (JacksonException notInShape) {
            throw new MisshapenProfileException(file, notInShape);
        }
        warnOfRetiredKeysHoldingAValue(yaml);
        return profile;
    }

    /**
     * Writes one line at WARN, the first time this process loads the file, naming the retired keys that hold a value:
     * a {@code value} neither null nor blank (ADR-392 section 4). A retired key unset passes silently.
     */
    private void warnOfRetiredKeysHoldingAValue(String yaml) {
        List<String> holding = new ArrayList<>();
        try {
            JsonNode root = YAML.readTree(yaml);
            for (String key : RETIRED_KEYS) {
                JsonNode block = root.isObject() ? root.get(key) : null;
                JsonNode value = block != null && block.isObject() ? block.get("value") : null;
                if (value != null && !value.isNull() && !value.isMissingNode() && !value.asText().isBlank()) {
                    holding.add(key);
                }
            }
        } catch (JacksonException unreadAsATree) {
            return;
        }
        if (holding.isEmpty() || !WARNED_OF.add(file.toAbsolutePath().normalize())) {
            return;
        }
        boolean both = holding.size() == 2;
        LOG.warn("{} holds {}, which Vespera no longer reads, since every copy of a document in another language is"
                        + " now kept: {} ignored, and left out the next time Vespera writes the profile",
                file, String.join(" and ", holding), both ? "they are" : "it is");
    }

    /** Writes the profile out whole, creating the working directory if it is not there yet. */
    public void save(Profile profile) {
        try {
            Files.createDirectories(file.getParent());
            String written = YAML.writeValueAsString(profile);
            Files.writeString(file, whereTheModelsRunIsUnset(profile) ? withModelsRunOnCommented(written) : written,
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the profile at " + file, e);
        }
    }

    /** Whether {@code modelsRunOn} holds no value, no provenance and no measurement, so that it is written commented (ADR-384 §6). */
    private static boolean whereTheModelsRunIsUnset(Profile profile) {
        TextValue where = profile.modelsRunOn();
        return where.measurement() == null
                && (where.value() == null || where.value().isBlank())
                && (where.provenance() == null || where.provenance().isBlank());
    }

    /** The three lines ADR-384 §6 has the file hold in place of an unset {@code modelsRunOn}. */
    private static final String MODELS_RUN_ON_COMMENTED = "# Where the models run: local, on this machine. Cloud is not"
            + " supported yet.\n# modelsRunOn:\n#   value: local\n";

    /** The written file with the lines of {@code modelsRunOn}'s block replaced by the three lines of comment. */
    private static String withModelsRunOnCommented(String written) {
        StringBuilder out = new StringBuilder();
        boolean inTheBlock = false;
        for (String line : written.split("(?<=\n)")) {
            if (inTheBlock && (line.startsWith(" ") || line.startsWith("\t"))) {
                continue;
            }
            inTheBlock = false;
            if (line.startsWith("modelsRunOn:")) {
                out.append(MODELS_RUN_ON_COMMENTED);
                inTheBlock = true;
                continue;
            }
            out.append(line);
        }
        return out.toString();
    }
}
