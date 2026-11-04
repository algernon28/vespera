package io.algernon.controlpanel.prototype;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.yaml.snakeyaml.Yaml;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * PROTOTYPE. Where the cockpit's starting values come from, read only, never written: the process
 * environment, the main checkout's {@code .env}, the control panel's own {@code launch-values.properties},
 * the jar's {@code application.yaml}, and the working directory's {@code profile.yaml},
 * {@code not-relevant-folders.yaml} and {@code code-set-names.yaml}. Each value carries the name of the
 * file it came from, so the cockpit can say so. What cannot be read is left out, quietly: a stack trace
 * here would name an operator's path.
 */
final class Config {

    record Value(String value, String source) {}

    record ProfileEntry(String value, String provenance) {}

    final Map<String, Value> launch = new LinkedHashMap<>();
    final Map<String, ProfileEntry> profile = new LinkedHashMap<>();
    final List<String> notRelevantFolders = new ArrayList<>();
    final Map<String, String> codeSetNames = new LinkedHashMap<>();
    Path profileFile;
    Map<String, Object> app = Map.of();

    /** A value of application.yaml by its dotted name, or {@code fallback}. */
    String appValue(String key, String fallback) {
        String value = dotted(app, key);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** The prototype's own copy of what Save writes, under its target folder: PROTOTYPE, wipe me. */
    static final Path SCRATCH_PROFILE = Path.of("target", "profile.PROTOTYPE-wipe-me.yaml").toAbsolutePath();
    static final Path SCRATCH_INCLUSIONS = Path.of("target", "not-relevant-folders.PROTOTYPE-wipe-me.yaml").toAbsolutePath();

    static Config load() {
        Config config = new Config();
        Path here = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Map<String, String> env = readEnvFile(findUp(here, ".env"));
        Properties launchValues = readProperties(launchValuesFile());
        Map<String, Object> app = readYaml(findUp(here, "src/main/resources/application.yaml"));
        config.app = app;

        config.pick("corpusRoot", "", app, "vespera.corpus-root", launchValues, "vespera.corpus-root",
                env, "VESPERA_CORPUS_ROOT");
        config.pick("workingDirectory", Path.of(System.getProperty("user.dir"), ".vespera").toString(), app, null,
                launchValues, "vespera.working-dir", env, "VESPERA_WORKING_DIR");
        config.pick("deliverableRoot", "", app, "vespera.deliverable-root", launchValues, "vespera.deliverable-root",
                env, null);
        config.pick("doclingImage", "", app, "vespera.docling.image", launchValues, "vespera.docling.image",
                env, "VESPERA_DOCLING_IMAGE");
        config.pick("doclingAddress", "http://localhost:5001", app, "vespera.docling.base-url", launchValues,
                "vespera.docling.base-url", env, null);
        config.pick("ollamaAddress", "http://localhost:11434", app, "spring.ai.ollama.base-url", launchValues,
                "spring.ai.ollama.base-url", env, null);
        config.pick("labelModel", "qwen3:8b", app, "spring.ai.ollama.chat.options.model", launchValues,
                "vespera.label.model", env, null);
        config.pick("seedFolder", "", app, null, launchValues, "last-seed-folder", env, null);

        String workingDirectory = config.launch.get("workingDirectory").value();
        if (!workingDirectory.isBlank()) {
            Path wd = Path.of(workingDirectory);
            config.profileFile = wd.resolve("profile.yaml");
            config.readProfile(config.profileFile);
            config.readNotRelevant(wd.resolve("not-relevant-folders.yaml"));
            config.readCodeSetNames(wd.resolve("code-set-names.yaml"));
        }
        return config;
    }

    /** Lowest precedence first: built-in, application.yaml, launch values, .env, the process environment. */
    private void pick(String name, String builtIn, Map<String, Object> app, String appKey, Properties launchValues,
            String launchKey, Map<String, String> env, String envKey) {
        Value value = new Value(builtIn, "built into the prototype");
        String fromApp = appKey == null ? null : dotted(app, appKey);
        if (fromApp != null && !fromApp.isBlank()) {
            value = new Value(fromApp, "application.yaml");
        }
        String fromLaunch = launchValues.getProperty(launchKey);
        if (fromLaunch != null && !fromLaunch.isBlank()) {
            value = new Value(fromLaunch, "the control panel's launch values");
        }
        if (envKey != null && env.get(envKey) != null && !env.get(envKey).isBlank()) {
            value = new Value(env.get(envKey), ".env");
        }
        if (envKey != null && System.getenv(envKey) != null && !System.getenv(envKey).isBlank()) {
            value = new Value(System.getenv(envKey), "the environment");
        }
        launch.put(name, value);
    }

    void readProfile(Path file) {
        profile.clear();
        for (Map.Entry<String, Object> entry : readYaml(file).entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> map) {
                profile.put(entry.getKey(), new ProfileEntry(text(map.get("value")), text(map.get("provenance"))));
            }
        }
    }

    private void readNotRelevant(Path file) {
        for (Object item : readYamlList(file)) {
            if (item instanceof Map<?, ?> map && map.get("folder") != null) {
                notRelevantFolders.add(text(map.get("folder")));
            }
        }
    }

    private void readCodeSetNames(Path file) {
        for (Object item : readYamlList(file)) {
            if (item instanceof Map<?, ?> map && map.get("folder") != null) {
                codeSetNames.put(text(map.get("folder")), text(map.get("name")));
            }
        }
    }

    static void writeProfile(Path file, Map<String, String> values, Map<String, String> why) throws IOException {
        StringBuilder yaml = new StringBuilder("# PROTOTYPE, wipe me: written by the JavaFX prototype, read by nothing else.\n");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            yaml.append(entry.getKey()).append(":\n")
                    .append("  value: ").append(quoted(entry.getValue())).append('\n')
                    .append("  provenance: ").append(quoted(why.getOrDefault(entry.getKey(), ""))).append('\n');
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, UTF_8);
    }

    static void writeNotRelevant(Path file, List<String> folders) throws IOException {
        StringBuilder yaml = new StringBuilder("# PROTOTYPE, wipe me\n---\n");
        folders.forEach(f -> yaml.append("- folder: ").append(quoted(f)).append('\n'));
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, UTF_8);
    }

    private static String quoted(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    private static String text(Object o) {
        return o == null ? "" : o.toString();
    }

    private static Path findUp(Path from, String relative) {
        for (Path dir = from; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static Path launchValuesFile() {
        String user = System.getProperty("user.name", "");
        String safe = user.isEmpty() ? "_" : user.replaceAll("[^A-Za-z0-9._-]", "_");
        return Path.of(System.getProperty("java.io.tmpdir"), "vespera-control-panel-" + safe, "launch-values.properties");
    }

    private static Map<String, String> readEnvFile(Path file) {
        Map<String, String> env = new LinkedHashMap<>();
        if (file == null) {
            return env;
        }
        try {
            for (String line : Files.readAllLines(file, UTF_8)) {
                int eq = line.indexOf('=');
                if (eq > 0 && !line.stripLeading().startsWith("#")) {
                    String value = line.substring(eq + 1).strip();
                    if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                            || value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    env.put(line.substring(0, eq).strip(), value);
                }
            }
        } catch (IOException | RuntimeException e) {
            // left out, quietly
        }
        return env;
    }

    private static Properties readProperties(Path file) {
        Properties properties = new Properties();
        if (file != null && Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, UTF_8)) {
                properties.load(reader);
            } catch (IOException | RuntimeException e) {
                // left out, quietly
            }
        }
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readYaml(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return Map.of();
        }
        try (Reader reader = Files.newBufferedReader(file, UTF_8)) {
            Object loaded = new Yaml().load(reader);
            return loaded instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
    }

    private static List<?> readYamlList(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(file, UTF_8)) {
            Object loaded = new Yaml().load(reader);
            return loaded instanceof List<?> list ? list : List.of();
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    private static String dotted(Map<String, Object> yaml, String key) {
        Object at = yaml;
        for (String part : key.split("\\.")) {
            if (!(at instanceof Map<?, ?> map)) {
                return null;
            }
            at = map.get(part);
        }
        return at == null || at instanceof Map ? null : at.toString();
    }
}
