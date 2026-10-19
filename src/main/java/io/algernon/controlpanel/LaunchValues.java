package io.algernon.controlpanel;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The values the control panel passes when it starts an invocation, and the last seed folder, remembered per user in
 * one UTF-8 {@link Properties} file under a folder of the user's own in the temp directory (ADR-292). Keyed by the
 * jar's property names as text, linking no class of the jar (ADR-285 §3). A null or blank component is {@code ""}.
 */
public record LaunchValues(String corpusRoot, String workingDirectory, String deliverableRoot, String doclingImage,
        String doclingBaseUrl, String ollamaBaseUrl, String labelModel, String lastSeedFolder) {

    private static final String CORPUS_ROOT = "vespera.corpus-root";
    private static final String WORKING_DIRECTORY = "vespera.working-dir";
    private static final String DELIVERABLE_ROOT = "vespera.deliverable-root";
    private static final String DOCLING_IMAGE = "vespera.docling.image";
    private static final String DOCLING_BASE_URL = "vespera.docling.base-url";
    private static final String OLLAMA_BASE_URL = "spring.ai.ollama.base-url";
    private static final String LABEL_MODEL = "vespera.label.model";
    private static final String LAST_SEED_FOLDER = "last-seed-folder";

    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    public LaunchValues {
        corpusRoot = unblank(corpusRoot);
        workingDirectory = unblank(workingDirectory);
        deliverableRoot = unblank(deliverableRoot);
        doclingImage = unblank(doclingImage);
        doclingBaseUrl = unblank(doclingBaseUrl);
        ollamaBaseUrl = unblank(ollamaBaseUrl);
        labelModel = unblank(labelModel);
        lastSeedFolder = unblank(lastSeedFolder);
    }

    private static String unblank(String value) {
        return value == null || value.isBlank() ? "" : value;
    }

    public static LaunchValues none() {
        return new LaunchValues("", "", "", "", "", "", "", "");
    }

    /** ADR-292 §3's path of the file; the folder is named for {@code userName}, made safe. */
    public static Path fileIn(Path tempDirectory, String userName) {
        String safe = userName == null || userName.isEmpty() ? "_" : userName.replaceAll("[^A-Za-z0-9._-]", "_");
        return tempDirectory.resolve("vespera-control-panel-" + safe).resolve("launch-values.properties");
    }

    /** ADR-292 §3's line for a folder that exists and is not the user's own; empty for one that does not exist. */
    public static Optional<String> refusalOfFolder(Path folder, String userName) {
        if (Files.notExists(folder, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        return FolderOwnership.isOwn(folder, userName) ? Optional.empty()
                : Optional.of("the control panel neither reads nor keeps its launch values in " + folder
                        + ", which is not a folder only this user can write in");
    }

    /** Never throws: what cannot be read is every field empty, with at most one line to {@code said} (§3, §5). */
    public static LaunchValues load(Path file, String userName, Consumer<String> said) {
        Optional<String> refusal = refusalOfFolder(file.toAbsolutePath().getParent(), userName);
        if (refusal.isPresent()) {
            said.accept(refusal.get());
            return none();
        }
        if (Files.notExists(file)) {
            return none();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            said.accept("the control panel could not read its launch values at " + file
                    + ", and every field starts empty: " + reason(e));
            return none();
        }
        return new LaunchValues(properties.getProperty(CORPUS_ROOT), properties.getProperty(WORKING_DIRECTORY),
                properties.getProperty(DELIVERABLE_ROOT), properties.getProperty(DOCLING_IMAGE),
                properties.getProperty(DOCLING_BASE_URL), properties.getProperty(OLLAMA_BASE_URL),
                properties.getProperty(LABEL_MODEL), properties.getProperty(LAST_SEED_FOLDER));
    }

    /** Never throws: writes the file whole and moves it into place, or says one line (§2, §3, §5). */
    public void store(Path file, String userName, Consumer<String> said) {
        Path folder = file.toAbsolutePath().getParent();
        Optional<String> refusal = refusalOfFolder(folder, userName);
        if (refusal.isPresent()) {
            said.accept(refusal.get());
            return;
        }
        Properties properties = new Properties();
        put(properties, CORPUS_ROOT, corpusRoot);
        put(properties, WORKING_DIRECTORY, workingDirectory);
        put(properties, DELIVERABLE_ROOT, deliverableRoot);
        put(properties, DOCLING_IMAGE, doclingImage);
        put(properties, DOCLING_BASE_URL, doclingBaseUrl);
        put(properties, OLLAMA_BASE_URL, ollamaBaseUrl);
        put(properties, LABEL_MODEL, labelModel);
        put(properties, LAST_SEED_FOLDER, lastSeedFolder);
        Path temporary = null;
        try {
            if (Files.notExists(folder)) {
                createFolder(folder);
                // another user may have made the folder between the check and the creation
                Optional<String> refusalOnceMade = refusalOfFolder(folder, userName);
                if (refusalOnceMade.isPresent()) {
                    said.accept(refusalOnceMade.get());
                    return;
                }
            }
            temporary = Files.createTempFile(folder, "launch-values", ".tmp", ownerOnly(
                    "rw-------"));
            try (Writer writer = Files.newBufferedWriter(temporary, UTF_8)) {
                properties.store(writer, null);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
        } catch (IOException | RuntimeException e) {
            said.accept("the control panel could not keep its launch values at " + file + ": " + reason(e));
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // nothing more can be done about a temporary file
                }
            }
        }
    }

    /** The five variables of ADR-292 §4, the deliverable root absolute, every empty value left out. */
    public Map<String, String> toChildEnvironment() {
        Map<String, String> environment = new LinkedHashMap<>();
        if (!deliverableRoot.isEmpty()) {
            environment.put("VESPERA_DELIVERABLE_ROOT", Path.of(deliverableRoot).toAbsolutePath().toString());
        }
        passed(environment, "VESPERA_DOCLING_IMAGE", doclingImage);
        passed(environment, "VESPERA_DOCLING_BASE_URL", doclingBaseUrl);
        passed(environment, "SPRING_AI_OLLAMA_BASE_URL", ollamaBaseUrl);
        passed(environment, "VESPERA_LABEL_MODEL", labelModel);
        return environment;
    }

    public Optional<String> refusalToRun() {
        if (corpusRoot.isEmpty() && workingDirectory.isEmpty()) {
            return Optional.of("Run needs the corpus root and the working directory");
        }
        if (corpusRoot.isEmpty()) {
            return Optional.of("Run needs the corpus root");
        }
        return workingDirectory.isEmpty() ? Optional.of("Run needs the working directory") : Optional.empty();
    }

    public Optional<String> refusalToLabel() {
        return workingDirectory.isEmpty() ? Optional.of("Label needs the working directory") : Optional.empty();
    }

    public List<String> warningsToRun() {
        return doclingImage.isEmpty()
                ? List.of("no Docling image is named, so the jar expects the sidecar to run its own default image,"
                        + " and stops before converting anything where it runs another")
                : List.of();
    }

    private static void put(Properties properties, String key, String value) {
        if (!value.isEmpty()) {
            properties.setProperty(key, value);
        }
    }

    private static void passed(Map<String, String> environment, String variable, String value) {
        if (!value.isEmpty()) {
            environment.put(variable, value);
        }
    }

    private static void createFolder(Path folder) throws IOException {
        if (POSIX) {
            Files.createDirectories(folder, ownerOnly("rwx------"));
        } else {
            Files.createDirectories(folder);
        }
    }

    private static FileAttribute<?>[] ownerOnly(String permissions) {
        return POSIX
                ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(permissions))}
                : new FileAttribute<?>[0];
    }

    private static String reason(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getClass().getSimpleName() + ": "
                + e.getMessage();
    }
}
