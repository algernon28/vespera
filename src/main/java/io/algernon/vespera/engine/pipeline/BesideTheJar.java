package io.algernon.vespera.engine.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The folder beside the jar (ADR-397): the configuration Vespera reads and the compose files it starts the sidecars
 * from lie there, written from the jar's shipped copies where they are missing and never over a file already there.
 *
 * <p>As a listener it hears that a start has begun, before Spring prepares its environment, and prepares the folder
 * (§3); it says what differs from the shipped copies once the logging is up (§5). {@link #pointSpringAt} gives Spring
 * the two files to read (§4) and {@link #composeFiles} the compose files where they lie (§6).
 */
public final class BesideTheJar implements ApplicationListener<ApplicationEvent> {

    /** Where the jar carries its shipped copies (ADR-397 §2). */
    static final String RESOURCE_FOLDER = "beside-the-jar/";

    /** What the name of a shipped copy written beside a file that differs ends in (ADR-397 §5). */
    static final String NEW_SUFFIX = ".new";

    private static final List<String> YAML_FILES = List.of("application.yaml", "compose.yaml", "compose.gpu.yaml");

    private static final String BUILD_CONTEXT = "docker/";

    private static final String LOCAL_FILE = "application-local.yaml";

    /** A file beside the jar that differs from its shipped copy, the keys that differ, and the line that says so. */
    record Difference(String file, List<String> keys, String line) {
    }

    private final Path folder;

    private List<String> lines = List.of();

    public BesideTheJar(Path folder) {
        this.folder = folder;
    }

    /** The folder beside the jar of this application: that of the code source this class was loaded from (§1). */
    public static BesideTheJar ofThisApplication() {
        Path current = Path.of("").toAbsolutePath();
        CodeSource source = BesideTheJar.class.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            return new BesideTheJar(current);
        }
        return new BesideTheJar(folderOf(source.getLocation(), current));
    }

    /**
     * The folder beside the jar for a code source's URL (ADR-397 §1): that of the file it names; the current directory
     * for a URL that names no file, a {@code jrt:} or an opaque one among them.
     */
    static Path folderOf(URL codeSource, Path currentDirectory) {
        return folderOf(codeSourceOf(codeSource), currentDirectory);
    }

    /**
     * The folder beside the jar for a code source (ADR-397 §1): the folder a jar lies in; the folder above a folder of
     * compiled classes; the current directory for a code source that is not there.
     */
    static Path folderOf(Path codeSource, Path currentDirectory) {
        if (codeSource == null) {
            return currentDirectory;
        }
        Path absolute = codeSource.toAbsolutePath();
        if (!Files.exists(absolute)) {
            return currentDirectory;
        }
        Path parent = absolute.getParent();
        return parent == null ? currentDirectory : parent;
    }

    /** The file or folder a code source's URL names; a nested jar as its outer file. */
    private static Path codeSourceOf(URL location) {
        try {
            String text = location.toString();
            if (text.startsWith("jar:")) {
                text = text.substring("jar:".length());
            }
            if (text.startsWith("nested:")) {
                text = text.substring("nested:".length());
            }
            int inside = text.indexOf("/!");
            if (inside < 0) {
                inside = text.indexOf("!/");
            }
            if (inside >= 0) {
                text = text.substring(0, inside);
            }
            if (!text.startsWith("file:")) {
                // Another scheme (jrt:, mailto:) names no file; a lone letter before the colon is a drive.
                int colon = text.indexOf(':');
                if (colon > 1 && text.substring(0, colon).matches("[A-Za-z][A-Za-z0-9+.-]*")) {
                    return null;
                }
                text = "file:" + text;
            }
            return Path.of(URI.create(text));
        } catch (RuntimeException notAFile) {
            return null;
        }
    }

    public Path folder() {
        return folder;
    }

    /**
     * Prepares the folder and keeps the lines of §5 for {@link #lines}; the entry point's way of doing what the listener
     * does, for the control panel.
     *
     * @throws BesideTheJarUnwritableException where a file cannot be written
     */
    public List<String> prepare() {
        writeWhatIsMissing();
        lines = writeNewWhereDifferent().stream().map(Difference::line).toList();
        return lines;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationStartingEvent) {
            prepare();
        } else if (event instanceof ApplicationPreparedEvent) {
            lines.forEach(OperatorLines::warn);
        }
    }

    /** Each file of the shipped copy that is not beside the jar, written there (ADR-397 §3); the names written. */
    List<String> writeWhatIsMissing() {
        List<String> written = new ArrayList<>();
        for (String name : namesOfTheShippedCopy()) {
            Path target = folder.resolve(name);
            if (Files.exists(target)) {
                continue;
            }
            try {
                // Never over a file that appears meanwhile, as a second start in this folder may place it.
                if (!writeFrom(name, target, false)) {
                    continue;
                }
            } catch (IOException | RuntimeException unwritten) {
                throw new BesideTheJarUnwritableException(name, folder, unwritten);
            }
            written.add(name);
        }
        return written;
    }

    /**
     * Writes {@code <name>.new} beside each file that differs, and names them (ADR-397 §5); a {@code .new} that cannot
     * be written is named in the line of its file, with the reason.
     */
    List<Difference> writeNewWhereDifferent() {
        List<Difference> differences = new ArrayList<>();
        for (String name : namesOfTheShippedCopy()) {
            Path beside = folder.resolve(name);
            if (!Files.isRegularFile(beside)) {
                continue;
            }
            List<String> keys;
            try {
                keys = keysThatDiffer(name, beside);
            } catch (IOException unread) {
                continue;
            }
            if (keys == null) {
                continue;
            }
            Path copy = folder.resolve(name + NEW_SUFFIX);
            String said = name + " beside the jar differs from this jar's own"
                    + (keys.isEmpty() ? "" : " at " + String.join(", ", keys));
            String line;
            try {
                writeFrom(name, copy, true);
                line = said + "; this jar's is written beside it as " + name + NEW_SUFFIX;
            } catch (IOException | RuntimeException unwritten) {
                line = said + "; this jar's cannot be written beside it as " + name + NEW_SUFFIX + " ("
                        + SidecarLockUnavailableException.reasonOf(unwritten) + ")";
            }
            differences.add(new Difference(name, keys, line));
        }
        return differences;
    }

    /** {@code null} where the file is the shipped copy's as far as ADR-397 §5 compares; else the keys that differ. */
    private List<String> keysThatDiffer(String name, Path beside) throws IOException {
        byte[] theirs = Files.readAllBytes(beside);
        byte[] ours = shippedBytes(name);
        if (!name.endsWith(".yaml")) {
            return java.util.Arrays.equals(theirs, ours) ? null : List.of();
        }
        Map<String, String> flatTheirs;
        Map<String, String> flatOurs;
        try {
            flatTheirs = flatten(theirs);
            flatOurs = flatten(ours);
        } catch (RuntimeException doesNotLoad) {
            // A file that does not load differs at no key (ADR-397 §5): it cannot be the shipped copy, which loads.
            return List.of();
        }
        TreeSet<String> keys = new TreeSet<>();
        for (String key : flatTheirs.keySet()) {
            if (!java.util.Objects.equals(flatTheirs.get(key), flatOurs.get(key))) {
                keys.add(key);
            }
        }
        for (String key : flatOurs.keySet()) {
            if (!flatTheirs.containsKey(key)) {
                keys.add(key);
            }
        }
        return keys.isEmpty() ? null : List.copyOf(keys);
    }

    /** A YAML file's keys as Spring flattens them, each with its value as text. */
    static Map<String, String> flatten(byte[] yaml) {
        YamlPropertiesFactoryBean loader = new YamlPropertiesFactoryBean();
        loader.setResources(new ByteArrayResource(yaml));
        Properties properties = loader.getObject();
        Map<String, String> flat = new TreeMap<>();
        if (properties != null) {
            properties.forEach((key, value) -> flat.put(key.toString(), String.valueOf(value)));
        }
        return flat;
    }

    /** The keys of the shipped copy of {@code application.yaml} with their values as text (ADR-397 §7). */
    static Map<String, String> shippedApplicationValues() {
        try {
            return flatten(shippedBytes("application.yaml"));
        } catch (IOException | RuntimeException unread) {
            return Map.of();
        }
    }

    /** Points Spring at the two files beside the jar (ADR-397 §4), among its default properties. */
    public void pointSpringAt(SpringApplication application) {
        // Spring cuts the location at its commas, so a comma of the folder's path is written %2C, which it decodes.
        application.setDefaultProperties(Map.of("spring.config.location",
                folder.resolve("application.yaml").toUri().toString().replace(",", "%2C") + ",optional:"
                        + folder.resolve(LOCAL_FILE).toUri().toString().replace(",", "%2C")));
    }

    /** The compose files where they lie beside the jar (ADR-397 §6); none is written out or deleted. */
    ComposeFiles composeFiles() {
        return ComposeFiles.in(folder);
    }

    /** What the jar carries, by its name below the folder: the three YAML files, then the build context by name. */
    private static List<String> namesOfTheShippedCopy() {
        List<String> names = new ArrayList<>(YAML_FILES);
        try {
            URL root = BesideTheJar.class.getClassLoader().getResource(RESOURCE_FOLDER + BUILD_CONTEXT);
            if (root != null) {
                String rootUrl = root.toString().endsWith("/") ? root.toString() : root + "/";
                TreeSet<String> context = new TreeSet<>();
                for (Resource resource : new PathMatchingResourcePatternResolver(BesideTheJar.class.getClassLoader())
                        .getResources("classpath*:" + RESOURCE_FOLDER + BUILD_CONTEXT + "**")) {
                    String url = resource.getURL().toString();
                    if (!url.startsWith(rootUrl) || url.endsWith("/") || !resource.isReadable()) {
                        continue;
                    }
                    context.add(BUILD_CONTEXT + java.net.URLDecoder.decode(
                            url.substring(rootUrl.length()).replace("+", "%2B"), StandardCharsets.UTF_8));
                }
                names.addAll(context);
            }
        } catch (IOException unreadable) {
            throw new IllegalStateException("the jar's shipped copy of " + BUILD_CONTEXT + " cannot be listed",
                    unreadable);
        }
        return names;
    }

    private static byte[] shippedBytes(String name) throws IOException {
        try (InputStream in = new ClassPathResource(RESOURCE_FOLDER + name).getInputStream()) {
            return in.readAllBytes();
        }
    }

    /**
     * Writes the shipped copy of {@code name} to {@code target}, through {@code <target>.part}; over a file already
     * there only where {@code replace}. Without it a file that is there is left, and {@code false} is answered.
     */
    private boolean writeFrom(String name, Path target, boolean replace) throws IOException {
        byte[] bytes = shippedBytes(name);
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Files.write(part, bytes);
        try {
            if (replace) {
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(part, target);
            }
        } catch (FileAlreadyExistsException alreadyThere) {
            Files.deleteIfExists(part);
            return false;
        }
        return true;
    }
}
