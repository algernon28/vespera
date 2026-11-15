package io.algernon.vespera.engine.pipeline;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * The configuration Spring reads for a start, read by Spring itself, with nothing of the machine running the test in
 * it (ADR-397 §4, §7): a {@link SpringApplication} of no bean, whose JVM system properties and environment variables
 * are the maps a test gives in place of the process's own, started with the arguments a test gives and read once it
 * has prepared its environment and loaded its configuration files.
 *
 * <p>So the layers a test names are the only ones there, in the places Spring gives them: the arguments as the command
 * line, the two maps as the system properties and the environment, and whatever the test's own preparation of the
 * application adds. Spring's configuration loading and its precedence are Spring's own.
 */
final class SpringsOwnReading {

    /** No bean at all: the application is started for its environment alone. */
    @Configuration(proxyBeanMethods = false)
    static class NothingButTheEnvironment {
    }

    private SpringsOwnReading() {
    }

    /**
     * Starts an application prepared by {@code preparation}, with {@code arguments} on its command line,
     * {@code systemProperties} as its system properties and {@code environmentVariables} as its environment, and
     * answers what {@code reading} reads of its environment before it is closed.
     */
    static <T> T read(Consumer<SpringApplication> preparation, String[] arguments, Map<String, Object> systemProperties,
            Map<String, Object> environmentVariables, Function<ConfigurableEnvironment, T> reading) {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.replace(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
                new MapPropertySource(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, systemProperties));
        sources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environmentVariables));
        SpringApplication application = new SpringApplication(NothingButTheEnvironment.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.setEnvironment(environment);
        preparation.accept(application);
        try (ConfigurableApplicationContext context = application.run(arguments)) {
            return reading.apply(context.getEnvironment());
        }
    }
}
