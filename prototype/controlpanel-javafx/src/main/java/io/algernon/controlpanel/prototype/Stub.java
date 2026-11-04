package io.algernon.controlpanel.prototype;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.util.Duration;

/**
 * PROTOTYPE. In-memory stand-in for everything the browser control panel reads from the
 * working directory and the child process. Nothing is written to disk; the "child" is a
 * timeline that prints made-up lines. Shared by every variant, so switching variants keeps
 * the state.
 */
final class Stub {

    record Step(String stage, String name, String stopCost) {}

    /**
     * A profile key: what the form shows ({@code label}, {@code about}), which control constrains it
     * ({@code type}: share, whole, decimal, folder, folders, endings, languages, model, choice), its
     * bounds where it is a number, and where else it is edited ({@code editedIn}, null when only here).
     */
    record Key(String name, String type, String earliestRun, String example, String about, String label,
            double min, double max, double step, String editedIn) {

        Key(String name, String type, String earliestRun, String example, String about) {
            this(name, type, earliestRun, example, about, name, 0, 1, 0.01, null);
        }
    }

    private static Key share(String name, String label, String earliestRun, String example, String about) {
        return new Key(name, "share", earliestRun, example, about, label, 0, 1, 0.01, null);
    }

    private static Key whole(String name, String label, String earliestRun, String example, String about,
            double min, double max, double step) {
        return new Key(name, "whole", earliestRun, example, about, label, min, max, step, null);
    }

    private static Key of(String type, String name, String label, String earliestRun, String example, String about) {
        return new Key(name, type, earliestRun, example, about, label, 0, 0, 0, null);
    }

    private static Key elsewhere(String type, String name, String label, String earliestRun, String example,
            String about, String editedIn) {
        return new Key(name, type, earliestRun, example, about, label, 0, 0, 0, editedIn);
    }

    /** The steps a change makes again, in plain words. */
    static String rerunOf(Key key) {
        return switch (key.earliestRun()) {
            case "none" -> "Changing it re-runs nothing: the next run only draws the pages again.";
            case "byte-level-reduction" -> "Changing it re-runs every step after the census.";
            case "extraction" -> "Changing it re-runs from text extraction on.";
            case "content-redundancy" -> "Changing it re-runs from duplicate detection on.";
            case "seed-measurement" -> "Changing it re-runs from the comparison with the exemplars on.";
            case "embedding-scoring" -> "Changing it re-runs from relevance scoring on (nothing is embedded again).";
            case "generation" -> "Changing it re-runs only the writing of the pages.";
            default -> "Changing it re-runs from " + key.earliestRun() + " on.";
        };
    }

    static final List<Step> STEPS = List.of(
            new Step("0", "census", "the entries walked since the last commit are walked again"),
            new Step("1", "byte-level-reduction", "the step, done again from the start"),
            new Step("2", "extraction", "at most the batch open when it stopped"),
            new Step("3", "content-census", "the pass, done again"),
            new Step("4a", "redundancy-signature", "its own work, from the stored text"),
            new Step("4b", "content-redundancy", "its own work; the signatures stay"),
            new Step("5a", "seed-extraction", "its own work, from the extraction cache"),
            new Step("5b", "seed-corpus-comparison", "its own work, done again"),
            new Step("5c", "embedding-scoring", "nothing: every vector stored is kept"),
            new Step("5", "translation", "its decisions, made again"),
            new Step("5d", "relevance-scoring", "the scores, from the stored vectors"),
            new Step("5e", "relevance-floor", "nothing: it decides again every time"),
            new Step("5f", "clustering", "the groups, formed again"),
            new Step("5", "relevance-report", "nothing: its files are written again"),
            new Step("6a", "arrangement", "the arrangement, made again"),
            new Step("6b", "generation", "the group being written; written groups are kept"));

    static final List<String> RUN_CHAIN = List.of("byte-level-reduction", "extraction", "content-census",
            "content-redundancy", "seed-measurement", "embedding-scoring", "arrangement", "generation");

    static final List<Key> KEYS = List.of(
            elsewhere("folder", "seedFolder", "Exemplars folder", "seed-measurement", "D:\\archive\\exemplars",
                    "Documents you know are relevant. The archive is measured against them.", "Seed folder"),
            share("degenerateOutputConfidenceFloor", "Minimum reading confidence", "extraction", "0.5",
                    "Files the converter read with less confidence than this are left out as unreadable. See confidence-distribution."),
            whole("degenerateOutputWordFloor", "Minimum words per file", "extraction", "20",
                    "Files whose text has fewer words than this are left out as holding nothing readable. See word-count-distribution.",
                    0, 100_000, 1),
            share("boilerplateDocumentFrequencyFloor", "Boilerplate threshold", "content-redundancy", "0.4",
                    "A five-word passage found in more than this share of documents is boilerplate (headers, disclaimers) and is not used to compare documents. See document-frequency."),
            of("model", "embeddingModel", "Scoring model", "embedding-scoring", "bge-m3:latest",
                    "The local model that turns each document into numbers to score how relevant it is."),
            share("relevanceScoreFloor", "Relevance cut-off", "embedding-scoring", "0.62",
                    "Documents scoring below this are left out as not relevant. See relevance-labelling."),
            of("arrangement", "arrangementApproved", "Approved arrangement", "generation", "auto",
                    "The grouping and order you approve before the pages are written. 'auto' approves every one unread. See arrangement."),
            of("model", "generationModel", "Writing model", "generation", "qwen3:8b",
                    "The local model that writes the text connecting each group's documents."),
            whole("generationContextWindow", "Reading window (tokens)", "generation", "32768",
                    "How much of a group the writing model reads in one go. Larger needs a stronger machine.", 2048, 1_048_576, 1024),
            whole("clusterPageByteCeiling", "Largest page (bytes)", "none", "10485760",
                    "A group's page larger than this is split into several pages. See cluster-page-sizes.", 1, 1e12, 1_000_000),
            share("logTimestampShareFloor", "Log file threshold", "byte-level-reduction", "0.7",
                    "Text files where at least this share of lines begin with a date or time are left out as logs. See format-mix."),
            whole("extractionAttempt", "Extraction attempt", "extraction", "2",
                    "Set 2, then 3, to ask the converter again about the files it failed on. See extraction-failures.", 1, 99, 1),
            of("endings", "codeNameEndings", "Code file endings", "byte-level-reduction", ".java .sql",
                    "File name endings read as code. See format-mix."),
            of("endings", "outOfScopeNameEndings", "Endings to leave out", "byte-level-reduction", ".ok .tmp",
                    "Unrecognised files with these name endings are left out. See format-mix."),
            share("translationSimilarityFloor", "Translation likeness", "embedding-scoring", "0.85",
                    "Two documents in different languages at least this alike are taken as one document. See translations."),
            of("languages", "preferredLanguages", "Preferred languages", "embedding-scoring", "it, en",
                    "Of a document held in several languages, the copy in the first language listed is kept. See translations."),
            share("configurationSettingShareFloor", "Configuration file threshold", "byte-level-reduction", "0.8",
                    "Text files where at least this share of lines are settings are left out as configuration. See format-mix."),
            of("choice", "deliverableLanguage", "Deliverable language", "generation", "it",
                    "The language the pages are written in. Not set: English."),
            new Key("clusterResolution", "decimal", "embedding-scoring", "2.5",
                    "Higher makes more, smaller groups. Not set: 1.0. See cluster-sizes.", "Grouping fineness", 0.1, 20, 0.1, null),
            elsewhere("folders", "codeSetFolders", "Code folders outside the corpus root", "byte-level-reduction",
                    "D:\\code\\billing", "Code kept beside the archive, read with it and copied whole.", "Code sets"));

    static final List<String> REPORTS = List.of("progress", "format-mix", "extraction-failures",
            "extraction-survivors", "confidence-distribution", "document-frequency", "word-count-distribution",
            "seed-corpus-comparison", "translations", "cluster-sizes", "relevance-labelling", "arrangement",
            "cluster-page-sizes");

    static final Map<String, List<String>> REPORTS_OF_KEY = Map.of(
            "relevanceScoreFloor", List.of("relevance-labelling", "cluster-sizes", "seed-corpus-comparison"),
            "boilerplateDocumentFrequencyFloor", List.of("document-frequency"),
            "degenerateOutputConfidenceFloor", List.of("confidence-distribution"),
            "degenerateOutputWordFloor", List.of("word-count-distribution"),
            "arrangementApproved", List.of("arrangement"),
            "clusterResolution", List.of("cluster-sizes"));

    static final String GPU_IMAGE = "vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0-r2";
    static final String CPU_IMAGE = "vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2";

    final Config config;
    final StringProperty corpusRoot = new SimpleStringProperty("");
    final StringProperty workingDirectory = new SimpleStringProperty("");
    final StringProperty outputFolder = new SimpleStringProperty("");
    final StringProperty doclingImage = new SimpleStringProperty("");
    final StringProperty doclingAddress = new SimpleStringProperty("");
    final StringProperty ollamaAddress = new SimpleStringProperty("");
    final StringProperty labelModel = new SimpleStringProperty("");
    /** How far each of {@link #STEPS} has got, 0 to 1. */
    final List<DoubleProperty> stepProgress = STEPS.stream()
            .map(s -> (DoubleProperty) new SimpleDoubleProperty(0)).toList();
    /** Folder, from the corpus root, to the name the deliverable gives its code set. */
    final ObservableMap<String, String> codeSetNames = FXCollections.observableMap(new LinkedHashMap<>());
    final ObservableList<String> codeSetFolders = FXCollections.observableArrayList();
    /** Which file the profile was last read from or saved to. */
    final StringProperty profileSource = new SimpleStringProperty("");

    /** none, running, ended, failed, stopped — the panel's {@code HowItEnded}, flattened. */
    final StringProperty childState = new SimpleStringProperty("none");
    final StringProperty command = new SimpleStringProperty("");
    /** Index into {@link #STEPS} of the step running or last reached; -1 before any run. */
    final IntegerProperty currentStep = new SimpleIntegerProperty(-1);
    final ObservableList<String> log = FXCollections.observableArrayList();
    /** What next-invocation.properties would say: run, label or none; empty before a run ends. */
    final StringProperty nextInvocation = new SimpleStringProperty("");
    final ObservableList<String> wants = FXCollections.observableArrayList();
    final ObservableMap<String, String> profile = FXCollections.observableMap(new LinkedHashMap<>());
    final ObservableMap<String, String> why = FXCollections.observableMap(new LinkedHashMap<>());
    final ObservableList<String> notRelevantFolders = FXCollections.observableArrayList();
    final StringProperty outcome = new SimpleStringProperty("");

    private Timeline ticker;
    private int ticks;
    private int stopAt;

    /** One sample of the progress page's charts: when, the database's size, the running step's count, the verdicts so far. */
    record Sample(double seconds, double databaseBytes, String step, double done, double total, Map<String, Double> verdicts) {}

    /** The steps that remove occurrences and the kinds each records, as the progress page has them. */
    static final Map<String, List<String>> OUTCOME_STEPS = Map.of(
            "byte-level-reduction", List.of("broken", "duplicate-of", "superseded-by", "out-of-scope", "code"),
            "extraction", List.of("extraction-failed", "degenerate-output"),
            "content-redundancy", List.of("redundant-with"),
            "translation", List.of("translation-of"),
            "relevance-floor", List.of("below-threshold"));

    final ObservableList<Sample> samples = FXCollections.observableArrayList();
    private final Map<String, Double> verdictsSoFar = new LinkedHashMap<>();
    private double databaseBytes = 48_000_000;

    /** Made-up numbers shaped like a run's: the database grows, the step counts up, removing steps record verdicts. */
    private void sample(Step step, double fraction) {
        int index = STEPS.indexOf(step);
        double total = 2_000 + index * 750;
        databaseBytes += 1_500_000 + index * 400_000;
        List<String> kinds = OUTCOME_STEPS.get(step.name());
        if (kinds != null) {
            for (int k = 0; k < kinds.size(); k++) {
                verdictsSoFar.merge(kinds.get(k), (double) (40 + 25 * k + index * 3), Double::sum);
            }
        }
        samples.add(new Sample(ticks * 5.0, databaseBytes, step.name(), Math.round(total * fraction), total,
                new LinkedHashMap<>(verdictsSoFar)));
    }

    Stub() {
        this(Config.load());
    }

    Stub(Config config) {
        this.config = config;
        corpusRoot.set(config.launch.get("corpusRoot").value());
        workingDirectory.set(config.launch.get("workingDirectory").value());
        outputFolder.set(config.launch.get("deliverableRoot").value());
        String image = config.launch.get("doclingImage").value();
        doclingImage.set(image.isBlank() ? GPU_IMAGE : image);
        doclingAddress.set(config.launch.get("doclingAddress").value());
        ollamaAddress.set(config.launch.get("ollamaAddress").value());
        labelModel.set(config.launch.get("labelModel").value());
        notRelevantFolders.setAll(config.notRelevantFolders);
        codeSetNames.putAll(config.codeSetNames);
        loadFlags();
        loadProfile(java.nio.file.Files.isRegularFile(Config.SCRATCH_PROFILE) ? Config.SCRATCH_PROFILE : config.profileFile);
    }

    /** Where a launch value came from, for the line under its field. */
    String sourceOf(String name) {
        return config.launch.get(name).source();
    }

    private void loadProfile(java.nio.file.Path file) {
        profile.clear();
        why.clear();
        if (file != null) {
            config.readProfile(file);
            config.profile.forEach((k, e) -> {
                profile.put(k, e.value());
                why.put(k, e.provenance());
            });
        }
        if (!profile.containsKey("seedFolder") && !config.launch.get("seedFolder").value().isBlank()) {
            profile.put("seedFolder", config.launch.get("seedFolder").value());
        }
        codeSetFolders.setAll(java.util.Arrays.stream(profile.getOrDefault("codeSetFolders", "").split(";"))
                .map(String::strip).filter(s -> !s.isEmpty()).toList());
        profileSource.set(file == null ? "no profile.yaml yet"
                : file.equals(Config.SCRATCH_PROFILE) ? "the prototype's copy, " + file : "profile.yaml in the working directory");
        profileVersion.set(profileVersion.get() + 1);
    }

    /** Moves on each time the profile is read again, so a form knows to redraw. */
    final IntegerProperty profileVersion = new SimpleIntegerProperty(0);

    /** Save every value at once, into the prototype's own copy; the operator's profile.yaml is never written. */
    void saveProfile(Map<String, String> values, Map<String, String> given) {
        // The jar requires a provenance line beside every value. The form fills in the note for the
        // operator (they may replace it); a value saved with no note gets the filled-in one.
        Map<String, String> reasons = new LinkedHashMap<>(given);
        Map<String, String> kept = new LinkedHashMap<>();
        values.forEach((k, v) -> {
            if (!v.isBlank()) {
                kept.put(k, v);
                if (reasons.getOrDefault(k, "").isBlank()) {
                    reasons.put(k, AUTO_PROVENANCE);
                }
            }
        });
        try {
            Config.writeProfile(Config.SCRATCH_PROFILE, kept, reasons);
            loadProfile(Config.SCRATCH_PROFILE);
            outcome.set(kept.size() + " values saved in the prototype's copy, " + Config.SCRATCH_PROFILE);
        } catch (java.io.IOException e) {
            outcome.set("Nothing is saved: the prototype's copy could not be written.");
        }
    }

    /** Back to the file last saved: the prototype's copy if there is one, else profile.yaml. */
    void resetProfile() {
        loadProfile(java.nio.file.Files.isRegularFile(Config.SCRATCH_PROFILE) ? Config.SCRATCH_PROFILE : config.profileFile);
        outcome.set("The form is back to " + profileSource.get() + " as last saved.");
    }

    void saveInclusions(List<String> leftOut) {
        notRelevantFolders.setAll(leftOut);
        try {
            Config.writeNotRelevant(Config.SCRATCH_INCLUSIONS, leftOut);
            outcome.set(leftOut.size() + " folders left out, saved in the prototype's copy, " + Config.SCRATCH_INCLUSIONS);
        } catch (java.io.IOException e) {
            outcome.set("Nothing is saved: the prototype's copy could not be written.");
        }
    }

    boolean running() {
        return "running".equals(childState.get());
    }

    void start(String cmd) {
        if (running()) {
            outcome.set(cap(cmd) + " is not started: the invocation the control panel started runs.");
            return;
        }
        command.set(cmd);
        childState.set("running");
        nextInvocation.set("");
        wants.clear();
        log.clear();
        samples.clear();
        verdictsSoFar.clear();
        ticks = 0;
        if (cmd.equals("run")) {
            int from = Math.max(currentStep.get(), 0);
            currentStep.set(from);
            for (int i = from; i < STEPS.size(); i++) {
                stepProgress.get(i).set(0);
            }
            stopAt = profile.containsKey("relevanceScoreFloor") ? STEPS.size() - 1 : 11;
            line("> " + commandLine(cmd));
        } else {
            stopAt = -1;
            line("> " + commandLine(cmd));
        }
        outcome.set(cap(cmd) + " is started.");
        ticker = new Timeline(new KeyFrame(Duration.millis(350), e -> tick()));
        ticker.setCycleCount(Timeline.INDEFINITE);
        ticker.play();
    }

    void stop() {
        if (!running()) {
            return;
        }
        ticker.stop();
        line("stopped by the control panel");
        childState.set("stopped");
        outcome.set("The invocation is stopped. The next vespera run continues from the last commit.");
    }

    private void tick() {
        ticks++;
        if (stopAt < 0) {
            line("label: question " + ticks + " of 40 answered");
            if (ticks >= 12) {
                finish("run", List.of());
            }
            return;
        }
        Step step = STEPS.get(currentStep.get());
        stepProgress.get(currentStep.get()).set((ticks % 6 == 0 ? 6 : ticks % 6) / 6.0);
        sample(step, stepProgress.get(currentStep.get()).get());
        line(step.name() + ": " + (ticks % 6) * 17 + "% — batch " + ticks);
        if (ticks % 6 == 0) {
            line(step.name() + ": done");
            if (currentStep.get() >= stopAt) {
                if (stopAt == STEPS.size() - 1) {
                    finish("none", List.of());
                } else {
                    finish("run", List.of("relevanceScoreFloor"));
                }
                return;
            }
            currentStep.set(currentStep.get() + 1);
        }
    }

    private void finish(String next, List<String> wanted) {
        ticker.stop();
        line("the next invocation is " + next + (wanted.isEmpty() ? "" : ", it wants " + String.join(", ", wanted)));
        childState.set("ended");
        wants.setAll(wanted);
        nextInvocation.set(next);
    }

    static final String AUTO_PROVENANCE = "set in the control panel";

    /**
     * The note a form shows beside a value: the one saved with it, or the filled-in one when there is
     * none. A value changed while its note is still the old one gets the filled-in note on Save, since
     * the old note described the old value.
     */
    String noteFor(String key) {
        String saved = why.getOrDefault(key, "");
        return saved.isBlank() ? AUTO_PROVENANCE : saved;
    }

    String noteToSave(String key, String value, String note) {
        boolean changed = !value.equals(profile.getOrDefault(key, ""));
        boolean untouched = note.strip().equals(why.getOrDefault(key, "").strip());
        return changed && untouched || note.isBlank() ? AUTO_PROVENANCE : note.strip();
    }

    void save(String key, String value, String reason) {
        Map<String, String> values = new LinkedHashMap<>(profile);
        values.put(key, value);
        Map<String, String> reasons = new LinkedHashMap<>(why);
        reasons.put(key, noteToSave(key, value, reason == null ? why.getOrDefault(key, "") : reason));
        saveProfile(values, reasons);
        wants.remove(key);
    }

    /** A command-line option the jar takes: a picocli flag, or a Spring property given as --name=value. */
    record Flag(String name, String label, String type, String defaultValue, String about, String commands) {}

    final List<Flag> flags = new ArrayList<>();
    /** Each flag's value as the operator set it; a value equal to the default is left off the command line. */
    final ObservableMap<String, String> flagValues = FXCollections.observableMap(new LinkedHashMap<>());

    private void loadFlags() {
        flags.add(new Flag("vespera.open-browser", "Open the progress page in the browser", "bool",
                config.appValue("vespera.open-browser", "true"),
                "Each run and label opens progress.html in your default browser as it starts.", "run, label"));
        flags.add(new Flag("vespera.progress-interval", "Progress page refresh (seconds)", "seconds",
                config.appValue("vespera.progress-interval", "5s"),
                "How often the progress page reloads itself.", "run, label"));
        flags.add(new Flag("vespera.sidecars.start", "Start Docling and Ollama when not answering", "bool",
                config.appValue("vespera.sidecars.start", "true"),
                "Starts the converter and the model server in Docker, and Docker itself, when they are not running.", "run, label --auto"));
        flags.add(new Flag("vespera.sidecars.wait-at-most", "Wait for the services (seconds)", "seconds",
                config.appValue("vespera.sidecars.wait-at-most", "180s"),
                "How long to wait for the converter and the model server to answer once started.", "run, label --auto"));
        flags.add(new Flag("vespera.models.pull", "Download missing models", "bool",
                config.appValue("vespera.models.pull", "true"),
                "Pulls a model the run needs and Ollama does not have yet.", "run"));
        flags.add(new Flag("vespera.models.pull-stalled-at-most", "Give up a stalled download (seconds)", "seconds",
                config.appValue("vespera.models.pull-stalled-at-most", "600s"),
                "A model download that makes no progress for this long is abandoned.", "run"));
        flags.add(new Flag("vespera.relevance.sample.bands", "Relevance question bands", "whole",
                config.appValue("vespera.relevance.sample.bands", "5"),
                "Into how many score bands the documents you are asked about are drawn.", "run"));
        flags.add(new Flag("vespera.relevance.sample.per-band", "Questions per band", "whole",
                config.appValue("vespera.relevance.sample.per-band", "12"),
                "How many documents are drawn from each band for you to answer.", "run"));
        flags.add(new Flag("vespera.corpus-root-lock-folder", "Lock folder", "folder",
                config.appValue("vespera.corpus-root-lock-folder", ""),
                "Where the lock that stops two runs walking one corpus root is kept. Empty: .vespera-root-locks in your home folder.", "run"));
        flags.add(new Flag("label-file", "Label file to record", "file", "",
                "A completed label file kept elsewhere. Empty: the one the last run wrote in the working directory.", "label"));
        flags.forEach(f -> flagValues.put(f.name(), f.defaultValue()));
    }

    /** The command line an invocation would be started with, showing only what differs from the defaults. */
    String commandLine(String cmd) {
        StringBuilder line = new StringBuilder("vespera ").append(cmd);
        if (cmd.equals("run") && !corpusRoot.get().isBlank()) {
            line.append(' ').append(quote(corpusRoot.get()));
        }
        if (cmd.startsWith("label") && !flagValues.getOrDefault("label-file", "").isBlank()) {
            line.append(' ').append(quote(flagValues.get("label-file")));
        }
        if (!workingDirectory.get().isBlank()) {
            line.append(" --db-dir=").append(quote(workingDirectory.get()));
        }
        String base = cmd.split(" ")[0];
        for (Flag flag : flags) {
            String value = flagValues.getOrDefault(flag.name(), "");
            boolean applies = flag.commands().contains(cmd) || flag.commands().equals(base)
                    || flag.commands().contains(base + ",") || flag.commands().endsWith(", " + base);
            if (applies && !flag.name().equals("label-file") && !value.equals(flag.defaultValue()) && !value.isBlank()) {
                line.append(" --").append(flag.name()).append('=').append(quote(value));
            }
        }
        return line.toString();
    }

    private static String quote(String s) {
        return s.contains(" ") ? "\"" + s + "\"" : s;
    }

    static Key key(String name) {
        return KEYS.stream().filter(k -> k.name().equals(name)).findFirst().orElseThrow();
    }

    static String costOf(String keyName) {
        Key key = key(keyName);
        int from = RUN_CHAIN.indexOf(key.earliestRun());
        if (from < 0) {
            return "Saving " + keyName + " makes no run again: it enters none.";
        }
        return "Saving " + keyName + " makes the next invocation mint again the runs of "
                + String.join(", ", RUN_CHAIN.subList(from, RUN_CHAIN.size())) + ".";
    }

    String childLine() {
        return switch (childState.get()) {
            case "none" -> "no invocation was started since the control panel started";
            case "failed" -> command.get() + ": failed or was refused (exit code 1)";
            case "stopped" -> command.get() + ": stopped by the control panel";
            default -> command.get() + ": " + childState.get();
        };
    }

    static String kindOf(String childState) {
        return switch (childState) {
            case "running", "stopped" -> "warn";
            case "ended" -> "ok";
            case "failed" -> "bad";
            default -> "idle";
        };
    }

    private void line(String text) {
        log.add(text);
        if (log.size() > 200) {
            log.remove(0);
        }
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Rule 5 of the prototype skill: the whole state, printed on every variant switch. */
    String dump() {
        // Names and counts only: no path and no value of the operator's is printed.
        List<String> lines = new ArrayList<>();
        lines.add("sources: " + config.launch.entrySet().stream().map(e -> e.getKey() + " from " + e.getValue().source())
                .collect(Collectors.joining(", ")));
        lines.add("child=" + childLine() + "  step=" + (currentStep.get() < 0 ? "-" : STEPS.get(currentStep.get()).name()));
        lines.add("next=" + (nextInvocation.get().isEmpty() ? "-" : nextInvocation.get()) + "  wants=" + wants);
        lines.add("profile keys set=" + profile.size() + "  folders left out=" + notRelevantFolders.size()
                + "  code set names=" + codeSetNames.size() + "  code set folders=" + codeSetFolders.size());
        return String.join(System.lineSeparator() + "  ", lines);
    }
}
