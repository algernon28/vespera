package io.algernon.vespera.pipeline;

import io.algernon.vespera.profile.ProfileStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Writes the profile's {@code extractionAttempt} key (ADR-185) into {@code profile.yaml}, or takes it
 * out, by editing the file's YAML tree rather than through {@code ProfileFixture}.
 *
 * <p><b>Through the tree on purpose.</b> {@code ProfileFixture} builds a {@code Profile}
 * positionally, so a builder method for a key the record does not have yet would not compile, and a test
 * tree that does not compile fails every test, not only the ones about this key. Edited as YAML, the file
 * carries the key whether or not the code knows it. Until it does, {@code ProfileStore} refuses the file,
 * because the record is the schema (ADR-061) and it is read with {@code FAIL_ON_UNKNOWN_PROPERTIES}; that
 * is how the tests that use this fail before the key is built.
 *
 * <p>The rest of the file is read and written back as it stands, so every other answer, and census's
 * pointers beside them, are kept.
 */
final class ExtractionAttemptInProfile {

    /** The key, as the profile names it. */
    static final String KEY = "extractionAttempt";

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private ExtractionAttemptInProfile() {
    }

    /** Writes {@code value} into the key, with {@code provenance} beside it, replacing whatever it held. */
    static void write(ProfileStore profileStore, String value, String provenance) {
        Map<String, Object> profile = read(profileStore);
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("value", value);
        answer.put("provenance", provenance);
        profile.put(KEY, answer);
        save(profileStore, profile);
    }

    /** Takes the key out of the file, which is the operator putting the value back to unset. */
    static void remove(ProfileStore profileStore) {
        Map<String, Object> profile = read(profileStore);
        profile.remove(KEY);
        save(profileStore, profile);
    }

    /** The pointer census keeps beside the key, or {@code null} where the key carries none or is absent. */
    @SuppressWarnings("unchecked")
    static Object measurement(ProfileStore profileStore) {
        Object answer = read(profileStore).get(KEY);
        return answer instanceof Map<?, ?> fields ? ((Map<String, Object>) fields).get("measurement") : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(ProfileStore profileStore) {
        try {
            String yaml = Files.readString(profileStore.file(), StandardCharsets.UTF_8);
            return new LinkedHashMap<String, Object>(YAML.readValue(yaml, Map.class));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the profile at " + profileStore.file(), e);
        }
    }

    private static void save(ProfileStore profileStore, Map<String, Object> profile) {
        try {
            Files.writeString(profileStore.file(), YAML.writeValueAsString(profile), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the profile at " + profileStore.file(), e);
        }
    }
}
