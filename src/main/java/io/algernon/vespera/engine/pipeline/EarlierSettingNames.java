package io.algernon.vespera.engine.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.core.env.Environment;

/**
 * The earlier names of the two container settings, which are still read (ADR-433 §5): a name is set where a source
 * gives it a value that is not blank, the new name wins where both are set, and each earlier name set is said in one
 * line, whether or not the new one is set too.
 */
public final class EarlierSettingNames {

    /** A setting and the name it was read under before ADR-433. */
    record Setting(String now, String earlier) {
    }

    /** The distribution before the wait, as the lines say them. */
    static final List<Setting> THE_SETTINGS = List.of(
            new Setting(ContainerStartSettings.WSL_DISTRIBUTION_PROPERTY, "vespera.sidecars.wsl-distribution"),
            new Setting(ContainerStartSettings.WAIT_AT_MOST_PROPERTY, "vespera.sidecars.wait-at-most"));

    static final Setting WSL_DISTRIBUTION = THE_SETTINGS.get(0);

    static final Setting WAIT_AT_MOST = THE_SETTINGS.get(1);

    private EarlierSettingNames() {
    }

    /**
     * The lines for each earlier name that is set in {@code environment}, the distribution's first, which the jar logs
     * at WARN once an invocation.
     */
    public static List<String> linesFor(Environment environment) {
        List<String> lines = new ArrayList<>();
        for (Setting setting : THE_SETTINGS) {
            if (valueOf(environment, setting.earlier()).isPresent()) {
                lines.add(lineFor(setting));
            }
        }
        return lines;
    }

    /**
     * The value of {@code setting}: the new name's where it is set, else the earlier name's where that is set, else
     * empty.
     */
    static Optional<String> read(Environment environment, Setting setting) {
        Optional<String> now = valueOf(environment, setting.now());
        return now.isPresent() ? now : valueOf(environment, setting.earlier());
    }

    /** The name {@code setting} is read under: the new one where it is set, else the earlier one. */
    static String nameSet(Environment environment, Setting setting) {
        return valueOf(environment, setting.now()).isPresent() || valueOf(environment, setting.earlier()).isEmpty()
                ? setting.now()
                : setting.earlier();
    }

    private static Optional<String> valueOf(Environment environment, String name) {
        String value = environment.getProperty(name);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static String lineFor(Setting setting) {
        return setting.earlier() + " is set: it is the earlier name of " + setting.now() + ", and is read only where "
                + setting.now() + " is not set. Rename it: a later major version reads only " + setting.now() + ".";
    }
}
