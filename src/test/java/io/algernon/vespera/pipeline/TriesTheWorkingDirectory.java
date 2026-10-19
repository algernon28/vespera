package io.algernon.vespera.pipeline;

import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.mock.env.MockEnvironment;

/**
 * A second process that takes a working directory once the way an invocation does, for
 * {@link TheJarReadsWhatTheControlPanelWritesTest} to see refused while the control panel writes (ADR-295 §1). It fires
 * the listener {@code VesperaApplication.main} registers over the directory named by its first argument, prints
 * {@value #HELD} or {@value #REFUSED} followed by the refusal's message, and ends, releasing what it took.
 */
public final class TriesTheWorkingDirectory {

    /** Printed when the working directory was taken. */
    public static final String HELD = "held";

    /** Printed, followed by the refusal's one line, when it was not. */
    public static final String REFUSED = "refused ";

    private TriesTheWorkingDirectory() {
    }

    public static void main(String[] args) {
        try {
            new WorkingDirectoryLock().onApplicationEvent(new ApplicationEnvironmentPreparedEvent(
                    new DefaultBootstrapContext(),
                    new SpringApplication(),
                    new String[] {"run", "started-by-hand"},
                    new MockEnvironment().withProperty(WorkingDirectoryPreparer.PROPERTY, Path.of(args[0]).toString())));
            System.out.println(HELD);
        } catch (WorkingDirectoryInUseException refused) {
            System.out.println(REFUSED + refused.getMessage());
        }
        System.out.flush();
    }
}
