package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.publish.confluence.ConfluenceValues;
import io.algernon.vespera.engine.profile.MisshapenProfileException;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.profile.Profile;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * The five Confluence values a working directory's {@code profile.yaml} names, handed to {@code confluence} as plain
 * values (ADR-359 §5). It holds no rule: it reads five values and hands them on. An unset or blank value is {@code
 * null}; no file, a file that does not load, or a file naming none of the five gives {@link ConfluenceValues#NONE}, so
 * that the publish reads the shell alone, as before (ADR-359 §2).
 */
@Component
public class ConfluenceValuesOfTheProfile implements ConfluenceValues.InTheProfile {

    @Override
    public ConfluenceValues of(Path workingDirectory) {
        Profile profile;
        try {
            profile = new ProfileStore(workingDirectory).load();
        } catch (MisshapenProfileException | UncheckedIOException notRead) {
            return ConfluenceValues.NONE;
        }
        ConfluenceValues read = new ConfluenceValues(
                named(profile.confluenceBaseUrl().value()),
                named(profile.confluenceSpaceKey().value()),
                named(profile.confluenceFolderId().value()),
                named(profile.confluenceFolderTitle().value()),
                named(profile.confluenceEmail().value()));
        return read.equals(ConfluenceValues.NONE) ? ConfluenceValues.NONE : read;
    }

    private static String named(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
