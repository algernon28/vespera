package io.algernon.vespera.profile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
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

    private static final YAMLMapper YAML =
            YAMLMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

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
        return YAML.readValue(yaml, Profile.class);
    }

    /** Writes the profile out whole, creating the working directory if it is not there yet. */
    public void save(Profile profile) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, YAML.writeValueAsString(profile), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the profile at " + file, e);
        }
    }
}
