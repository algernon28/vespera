package io.algernon.vespera.engine.pipeline;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.support.EnvironmentPostProcessorApplicationListener;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

/**
 * Refuses a start of the command line through Spring whose {@code vespera.working-dir} names no folder (ADR-436 §1):
 * not set, empty or white space alone. It hears the environment prepared after the configuration files are read into it
 * ({@link EnvironmentPostProcessorApplicationListener}) and before Logback is set up
 * ({@link LoggingApplicationListener}), which would open the log under the root of a drive: no folder is made, no log
 * file written and no database opened.
 */
public final class WorkingDirectoryNamedCheck implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        String named = event.getEnvironment().getProperty(WorkingDirectoryPreparer.PROPERTY);
        if (named == null || named.isBlank()) {
            throw new NoWorkingDirectoryNamedException();
        }
    }

    /** Between the configuration files' order, {@code HIGHEST_PRECEDENCE + 10}, and Logback's, {@code + 20}. */
    @Override
    public int getOrder() {
        return EnvironmentPostProcessorApplicationListener.DEFAULT_ORDER + 5;
    }
}
