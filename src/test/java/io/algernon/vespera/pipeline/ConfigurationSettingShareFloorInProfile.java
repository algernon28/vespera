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
 * Writes the profile's {@code configurationSettingShareFloor} key (ADR-287 §7) into {@code profile.yaml} by editing
 * the file's YAML tree, as {@link CodeNameEndingsInProfile} does for its key and for the same reason: the tests that
 * use it compile whether or not the record has the key, and fail where {@code ProfileStore} refuses a key it does not
 * know. The rest of the file is kept as it stands, and a file not yet written is begun.
 */
final class ConfigurationSettingShareFloorInProfile {

    /** The key, as the profile names it. */
    static final String KEY = "configurationSettingShareFloor";

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private ConfigurationSettingShareFloorInProfile() {
    }

    /** Writes {@code value} into the key, with {@code provenance} beside it, replacing whatever it held. */
    static void write(ProfileStore profileStore, String value, String provenance) {
        Map<String, Object> profile = read(profileStore);
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("value", value);
        answer.put("provenance", provenance);
        profile.put(KEY, answer);
        try {
            Files.createDirectories(profileStore.file().getParent());
            Files.writeString(profileStore.file(), YAML.writeValueAsString(profile), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the profile at " + profileStore.file(), e);
        }
    }

    /** Where the key's measurement points, or {@code null} where it carries none or is absent. */
    @SuppressWarnings("unchecked")
    static Object measurementSource(ProfileStore profileStore) {
        Object answer = read(profileStore).get(KEY);
        Object measurement = answer instanceof Map<?, ?> fields ? ((Map<String, Object>) fields).get("measurement") : null;
        return measurement instanceof Map<?, ?> pointer ? ((Map<String, Object>) pointer).get("source") : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(ProfileStore profileStore) {
        if (!Files.exists(profileStore.file())) {
            return new LinkedHashMap<>();
        }
        try {
            String yaml = Files.readString(profileStore.file(), StandardCharsets.UTF_8);
            Map<String, Object> profile = yaml.isBlank() ? null : YAML.readValue(yaml, Map.class);
            return profile == null ? new LinkedHashMap<>() : new LinkedHashMap<>(profile);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the profile at " + profileStore.file(), e);
        }
    }
}
