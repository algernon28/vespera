package io.algernon.controlpanel.prototype;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.IntegerProperty;
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

    record Key(String name, String type, String earliestRun, String example, String about) {}

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
            new Key("seedFolder", "folder", "seed-measurement", "D:\\archive\\exemplars", "The folder of documents known to be relevant."),
            new Key("degenerateOutputConfidenceFloor", "number", "extraction", "0.5", "Below this conversion confidence a document is dropped."),
            new Key("degenerateOutputWordFloor", "number", "extraction", "20", "Below this many words a conversion is dropped."),
            new Key("boilerplateDocumentFrequencyFloor", "number", "content-redundancy", "0.4", "A shingle in more than this share of documents is boilerplate."),
            new Key("embeddingModel", "text", "embedding-scoring", "bge-m3:latest", "The model that embeds every survivor."),
            new Key("relevanceScoreFloor", "number", "embedding-scoring", "0.62", "Below this score a document is not relevant."),
            new Key("arrangementApproved", "text", "generation", "auto", "The arrangement the deliverable is written from."),
            new Key("generationModel", "text", "generation", "qwen3:8b", "The model that writes each group's page."),
            new Key("generationContextWindow", "number", "generation", "32768", "Tokens the generation model is given."),
            new Key("clusterPageByteCeiling", "number", "none", "10485760", "Largest page a group may have."),
            new Key("logTimestampShareFloor", "number", "byte-level-reduction", "0.7", "Above this share of timestamped lines a file is a log."),
            new Key("extractionAttempt", "number", "extraction", "2", "Which attempt at the failed conversions this is."),
            new Key("codeNameEndings", "endings", "byte-level-reduction", ".java .sql", "Name endings read as code."),
            new Key("outOfScopeNameEndings", "endings", "byte-level-reduction", ".ok .tmp", "Name endings never kept."),
            new Key("translationSimilarityFloor", "number", "embedding-scoring", "0.85", "Above this similarity two documents are translations."),
            new Key("preferredLanguages", "languages", "embedding-scoring", "it, en", "Which translation is kept."),
            new Key("configurationSettingShareFloor", "number", "byte-level-reduction", "0.8", "Above this share of settings a file is configuration."),
            new Key("deliverableLanguage", "choice", "generation", "it", "The language the deliverable is written in."),
            new Key("clusterResolution", "number", "embedding-scoring", "2.5", "Higher makes more, smaller groups."),
            new Key("codeSetFolders", "folders", "byte-level-reduction", "D:\\code\\billing", "Code folders outside the corpus root."));

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

    final StringProperty corpusRoot = new SimpleStringProperty("D:\\archive\\corpus");
    final StringProperty workingDirectory = new SimpleStringProperty("E:\\vespera_working_dir");
    final StringProperty outputFolder = new SimpleStringProperty("");
    final StringProperty doclingImage = new SimpleStringProperty(GPU_IMAGE);
    final StringProperty doclingAddress = new SimpleStringProperty("http://localhost:5001");
    final StringProperty ollamaAddress = new SimpleStringProperty("http://localhost:11434");
    final StringProperty labelModel = new SimpleStringProperty("qwen3:8b");

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
    final ObservableList<String> notRelevantFolders = FXCollections.observableArrayList("archive/old-flows");
    final StringProperty outcome = new SimpleStringProperty("");

    private Timeline ticker;
    private int ticks;
    private int stopAt;

    Stub() {
        profile.put("seedFolder", "D:\\archive\\exemplars");
        why.put("seedFolder", "the folder the team keeps its reference manuals in");
        profile.put("degenerateOutputConfidenceFloor", "0.5");
        why.put("degenerateOutputConfidenceFloor", "read off confidence-distribution: the knee at 0.5");
        profile.put("boilerplateDocumentFrequencyFloor", "0.4");
        why.put("boilerplateDocumentFrequencyFloor", "read off document-frequency");
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
        ticks = 0;
        if (cmd.equals("run")) {
            int from = Math.max(currentStep.get(), 0);
            currentStep.set(from);
            stopAt = profile.containsKey("relevanceScoreFloor") ? STEPS.size() - 1 : 11;
            line("vespera run " + corpusRoot.get());
            line("working directory " + workingDirectory.get());
        } else {
            stopAt = -1;
            line("vespera " + cmd + " " + corpusRoot.get());
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

    void save(String key, String value, String reason) {
        if (reason == null || reason.isBlank()) {
            outcome.set(key + " is not saved: say why this value.");
            return;
        }
        profile.put(key, value);
        why.put(key, reason);
        wants.remove(key);
        outcome.set(key + " is saved in " + workingDirectory.get() + "\\profile.yaml");
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
        List<String> lines = new ArrayList<>();
        lines.add("corpusRoot=" + corpusRoot.get() + "  workingDirectory=" + workingDirectory.get());
        lines.add("child=" + childLine() + "  step=" + (currentStep.get() < 0 ? "-" : STEPS.get(currentStep.get()).name()));
        lines.add("next=" + (nextInvocation.get().isEmpty() ? "-" : nextInvocation.get()) + "  wants=" + wants);
        lines.add("profile=" + profile.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(", ")));
        return String.join(System.lineSeparator() + "  ", lines);
    }
}
