package io.algernon.vespera.pipeline;

import io.algernon.vespera.profile.MisshapenProfileException;
import io.algernon.vespera.profile.ProfileStore;
import java.nio.file.Path;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

/**
 * Loads {@code profile.yaml} once before anything else asks for it, so a file not in the shape a
 * profile takes ends the invocation before the application context exists (#321).
 *
 * <p>Without it, the first component to read the profile at start-up was whichever bean happened to
 * be built first, and it failed the context: Spring logged its own account under that bean's name,
 * then the whole stack, and printed the banner and start-up lines above it — about ninety lines on
 * {@code run} and on {@code label} alike, none naming the file. Failing here, while the environment
 * is prepared, is what leaves {@link MisshapenProfileRefusal}'s one line as the only thing printed:
 * no banner has been shown yet, no bean exists to be named, and no context has begun a refresh it
 * would have to report cancelling.
 *
 * <p>An environment listener rather than a bean for the reason {@link WorkingDirectoryPreparer} is
 * one: {@code vespera.working-dir} is known here and nothing has been built. It adds no rule of its
 * own. What counts as misshapen is {@link ProfileStore}'s, and this only asks it earlier; a profile
 * edited out of shape part-way through an invocation still fails wherever it is next read, with the
 * same line as the message.
 */
public class ProfileShapeCheck implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    /**
     * Reads the profile in the working directory the environment names, and keeps nothing of it.
     *
     * @throws MisshapenProfileException when the file does not load
     */
    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        String configured = event.getEnvironment().getProperty(WorkingDirectoryPreparer.PROPERTY);
        if (configured == null || configured.isBlank()) {
            return;
        }
        new ProfileStore(Path.of(configured)).load();
    }
}
