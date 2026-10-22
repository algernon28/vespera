package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.controlpanel.CodeSetRenames;
import io.algernon.controlpanel.CodeSetRenames.Rename;
import io.algernon.controlpanel.LabelAnswers;
import io.algernon.controlpanel.ProfileEdit;
import io.algernon.controlpanel.ProfileEdit.Stated;
import io.algernon.controlpanel.ProfileKey;
import io.algernon.controlpanel.WorkingDirectoryWrite;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.deliverable.CodeSetNames;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.LanguagesValue;
import io.algernon.vespera.profile.Measurement;
import io.algernon.vespera.profile.NameEndingsValue;
import io.algernon.vespera.profile.NumericValue;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.algernon.vespera.profile.TextValue;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel writes {@code profile.yaml}, the answers of {@code relevance-labels.yaml} and
 * {@code code-set-names.yaml} with rules of its own in {@code java.base}, linking no class of the jar (ADR-285 §3);
 * this is the contract, on a test class path that sees both, that holds those rules to the jar's (ADR-295): its table
 * of keys to {@code Profile} and {@code StageModules} (§5), every value it saves to an answered reading by the jar, and
 * every sample it refuses to one the jar cannot act on (§3, §4), a profile the jar wrote to its reading and rewrite
 * (§3), the label file to {@code LabelFileReader} (§6), the code-set names to {@code CodeSetNames} (§7), and its lock to
 * the jar's {@link WorkingDirectoryLock} in another process (§1).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("566")
@Link(name = "ADR-295", url = Adr.THE_CONTROL_PANEL_WRITES_HOLDING_THE_WORKING_DIRECTORY_LOCK, type = "adr")
class TheJarReadsWhatTheControlPanelWritesTest {

    private static final String USER = System.getProperty("user.name");

    /** A provenance the jar breaks past 80 columns, with a quote, a backslash, a tab, a line feed and letters outside ASCII. */
    private static final String AN_ODD_PROVENANCE = "read off the page, \"as written\" \\ with a\ttab and\na second"
            + " line, łódź 漢字 and a no-break space, " + "and so on ".repeat(12);

    /** Fifty-two hexadecimal digits, which after an approval's twelve make a run id's sixty-four. */
    private static final String THE_REST_OF_A_RUN_ID = "0".repeat(52);

    private static final long CHILD_BOUND_SECONDS = 60;

    @Test
    @Story("The jar reads what the control panel writes")
    @DisplayName("The control panel's table of keys is the profile's keys and types in order, and its runs are the jar's stages in order")
    void theKeyTableIsTheProfilesComponentsAndTheRunsAreTheStagesModules() {
        Map<Class<?>, ProfileKey.Kind> kindOf = Map.of(
                TextValue.class, ProfileKey.Kind.TEXT,
                NumericValue.class, ProfileKey.Kind.NUMBER,
                NameEndingsValue.class, ProfileKey.Kind.NAME_ENDINGS,
                LanguagesValue.class, ProfileKey.Kind.LANGUAGES);

        claim("the control panel's keys are the profile's components, in the profile's order",
                () -> assertThat(Arrays.stream(ProfileKey.values()).map(ProfileKey::key).toList())
                        .containsExactlyElementsOf(Arrays.stream(Profile.class.getRecordComponents())
                                .map(RecordComponent::getName)
                                .toList()));
        claim("each key's type is the type of its component's value",
                () -> assertThat(Arrays.stream(ProfileKey.values()).map(ProfileKey::kind).toList())
                        .containsExactlyElementsOf(Arrays.stream(Profile.class.getRecordComponents())
                                .map(component -> kindOf.get(component.getType()))
                                .toList()));
        claim("the control panel's runs are the jar's stages, by name and in the cascade's order",
                () -> assertThat(Arrays.stream(ProfileKey.Run.values()).map(ProfileKey.Run::stageName).toList())
                        .containsExactlyElementsOf(Arrays.stream(StageModules.values()).map(StageModules::stage).toList()));
    }

    @Test
    @Story("The jar reads what the control panel writes")
    @DisplayName("Every value the control panel saves, for every key, loads through the jar as written and is read by the jar's rule as an answer")
    void everyValueTheControlPanelSavesIsReadAsAnsweredAndEqual(@TempDir Path temp) throws IOException {
        Path seeds = Files.createDirectory(temp.resolve("seeds"));
        Path workingDirectory = Files.createDirectory(temp.resolve("work"));
        Map<ProfileKey, List<String>> accepted = acceptedSamples(seeds);
        claim("there are samples for every key of the table",
                () -> assertThat(accepted.keySet()).containsExactlyInAnyOrder(ProfileKey.values()));

        ProfileStore store = new ProfileStore(workingDirectory);
        for (Map.Entry<ProfileKey, List<String>> samples : accepted.entrySet()) {
            ProfileKey key = samples.getKey();
            for (String typed : samples.getValue()) {
                claim(key.key() + " \"" + typed + "\" is accepted by the control panel",
                        () -> assertThat(ProfileEdit.refusalOf(key, typed, AN_ODD_PROVENANCE)).isEmpty());
                Files.writeString(store.file(),
                        ProfileEdit.rewrite(Optional.empty(), key, typed, AN_ODD_PROVENANCE), UTF_8);
                ProfileValue loaded = componentOf(store.load(), key);
                claim("and " + key.key() + " \"" + typed + "\" loads through the jar as written, trimmed, with the"
                                + " provenance as typed",
                        () -> {
                            assertThat(loaded.value()).isEqualTo(typed.strip());
                            assertThat(loaded.provenance()).isEqualTo(AN_ODD_PROVENANCE);
                        });
                claim("and the jar's rule for " + key.key() + " reads \"" + typed + "\" as an answer",
                        () -> assertThat(readAsAnAnswerByTheJar(key, loaded, store)).isTrue());
            }
        }
        Files.writeString(store.file(),
                ProfileEdit.rewrite(Optional.empty(), ProfileKey.EXTRACTION_ATTEMPT, "", "back to the first"), UTF_8);
        claim("an empty value loads as a key nobody answered, with the provenance as typed",
                () -> {
                    NumericValue unset = store.load().extractionAttempt();
                    assertThat(unset.isSet()).isFalse();
                    assertThat(unset.provenance()).isEqualTo("back to the first");
                });
    }

    @Test
    @Story("The jar reads what the control panel writes")
    @DisplayName("Every sample the control panel refuses is one the jar reads as unreadable, ignores or stops on")
    void everySampleTheControlPanelRefusesTheJarCannotActOn(@TempDir Path temp) throws IOException {
        Path workingDirectory = Files.createDirectory(temp.resolve("work"));
        Path aFile = Files.createFile(temp.resolve("a-file"));
        Map<ProfileKey, List<String>> refused = new LinkedHashMap<>();
        for (ProfileKey floor : List.of(ProfileKey.DEGENERATE_OUTPUT_CONFIDENCE_FLOOR,
                ProfileKey.BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR, ProfileKey.RELEVANCE_SCORE_FLOOR,
                ProfileKey.LOG_TIMESTAMP_SHARE_FLOOR, ProfileKey.TRANSLATION_SIMILARITY_FLOOR,
                ProfileKey.CONFIGURATION_SETTING_SHARE_FLOOR)) {
            refused.put(floor, List.of("0,4", "0.4x"));
        }
        refused.put(ProfileKey.DEGENERATE_OUTPUT_WORD_FLOOR, List.of("-1", "1.5", "NaN", "2147483648"));
        refused.put(ProfileKey.EXTRACTION_ATTEMPT, List.of("0", "1.5", "Infinity", "2147483648", "two"));
        refused.put(ProfileKey.GENERATION_CONTEXT_WINDOW, List.of("1281", "4096.5", "0", "1e18", "NaN", "big"));
        refused.put(ProfileKey.CLUSTER_PAGE_BYTE_CEILING, List.of("0", "-1", "1.5", "NaN", "big", "9223372036854775808"));
        refused.put(ProfileKey.OUT_OF_SCOPE_NAME_ENDINGS, List.of(".P3A *.dgn", "dgn", ".."));
        refused.put(ProfileKey.CODE_NAME_ENDINGS, List.of(".java src/"));
        refused.put(ProfileKey.PREFERRED_LANGUAGES, List.of("it, eng", "italian"));
        refused.put(ProfileKey.ARRANGEMENT_APPROVED, List.of("0123456789AB"));
        refused.put(ProfileKey.SEED_FOLDER, List.of(temp.resolve("absent").toString(), aFile.toString()));

        ProfileStore store = new ProfileStore(workingDirectory);
        for (Map.Entry<ProfileKey, List<String>> samples : refused.entrySet()) {
            ProfileKey key = samples.getKey();
            for (String typed : samples.getValue()) {
                claim(key.key() + " \"" + typed + "\" is refused by the control panel",
                        () -> assertThat(ProfileEdit.refusalOf(key, typed, AN_ODD_PROVENANCE)).isPresent());
                store.save(profileWith(key, typed));
                claim("and the jar, given " + key.key() + " \"" + typed + "\" as the profile holds it, cannot act on it",
                        () -> assertThat(readAsAnAnswerByTheJar(key, componentOf(store.load(), key), store)).isFalse());
            }
        }
    }

    @Test
    @Story("The jar reads what the control panel writes")
    @DisplayName("A profile the jar wrote is read by the control panel as the jar reads it, and rewritten with every other value kept")
    void aProfileTheJarWroteIsReadAndRewrittenWithItsOtherValuesKept(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Profile everyKeyAnswered = everyKeyAnswered();
        store.save(everyKeyAnswered);
        String asTheJarWroteIt = Files.readString(store.file(), UTF_8);
        Map<ProfileKey, Stated> expected = new LinkedHashMap<>();
        for (ProfileKey key : ProfileKey.values()) {
            ProfileValue value = componentOf(everyKeyAnswered, key);
            expected.put(key, new Stated(value.value(), value.provenance()));
        }

        claim("the jar broke a provenance across lines, so the file is one the control panel must join to read",
                () -> assertThat(asTheJarWroteIt).contains("\\\n"));
        claim("the control panel reads every key's value and provenance as the jar wrote them",
                () -> assertThat(ProfileEdit.read(asTheJarWroteIt)).containsExactlyInAnyOrderEntriesOf(expected));

        Files.writeString(store.file(), ProfileEdit.rewrite(
                Optional.of(asTheJarWroteIt), ProfileKey.EXTRACTION_ATTEMPT, "3", "a third attempt"), UTF_8);
        Profile reloaded = store.load();
        claim("once the attempt is rewritten, the jar loads it as saved, its measurement kept",
                () -> assertThat(reloaded.extractionAttempt()).isEqualTo(new NumericValue(
                        "3", "a third attempt", everyKeyAnswered.extractionAttempt().measurement())));
        claim("and every other key exactly as the jar wrote it, the seed folder's measurement among them",
                () -> assertThat(reloaded).usingRecursiveComparison().ignoringFields("extractionAttempt")
                        .isEqualTo(everyKeyAnswered));
    }

    @Test
    @Story("The jar reads what the control panel writes")
    @DisplayName("A label file the jar wrote, answered by the control panel, is read by vespera label with those answers and its stamps")
    void theLabelFileTheJarWroteIsAnsweredAndReadWithItsStamps() {
        String scoringRun = "a-scoring-run";
        String seedSet = "/seeds";
        String first = "a/first.pdf";
        String second = "c d/ł \"quoted\".txt";
        String third = "e/third.txt";
        List<RelevanceLabelFile.Entry> entries = List.of(
                new RelevanceLabelFile.Entry(first, sampled(1, 0.83, 2), "seed/one.docx"),
                new RelevanceLabelFile.Entry(second, sampled(2, 0.1, 0), "seed/one.docx"),
                new RelevanceLabelFile.Entry(third, sampled(3, 0.5, 1), "seed/two.docx"));
        String asTheJarWroteIt = RelevanceLabelFile.render(scoringRun, "embedder@digest", seedSet, entries,
                Map.of(new OccurrenceId(1), true, new OccurrenceId(3), false), Map.of(new OccurrenceId(3), "qwen3:8b"));
        Map<String, Optional<Boolean>> answers = new LinkedHashMap<>();
        answers.put(second, Optional.of(true));
        answers.put(third, Optional.empty());

        String answered = LabelAnswers.rewrite(Optional.of(asTheJarWroteIt), answers);

        claim("the control panel reads the jar's questions by their paths, in the file's order",
                () -> assertThat(LabelAnswers.read(asTheJarWroteIt).stream().map(LabelAnswers.Question::path).toList())
                        .containsExactly(first, second, third));
        claim("vespera label reads the first answer as it was and the second as the control panel wrote it, each with"
                        + " its score, and counts the third, blanked, as waiting",
                () -> assertThat(LabelFileReader.read(answered, scoringRun)).isEqualTo(new LabelFileReader.Answers(
                        "embedder@digest",
                        List.of(new LabelFileReader.Answer(first, true, 0.83),
                                new LabelFileReader.Answer(second, true, 0.1)),
                        1)));
        claim("and the stamps are as the jar wrote them",
                () -> {
                    assertThat(LabelFileReader.runNamedBy(answered)).contains(scoringRun);
                    assertThat(LabelFileReader.seedSetNamedBy(answered)).contains(seedSet);
                });
    }

    @Test
    @Story("The jar reads what the control panel writes")
    @DisplayName("Each single-quoted and plain value the control panel reads by hand is the text, or nothing, the jar reads from it")
    void theJarReadsEachScalarTheControlPanelReadsToTheSameText(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        List<String> profileScalars = List.of("'D:\\archive\\exemplars'", "'it''s'", "''", "0.35", "0.35   ", "-0.2",
                "1.50", "~", "Null", "true", "null", "qwen3:14b", "a#b", ".P3A .DGN, .x", "it, en");
        for (String scalar : profileScalars) {
            String text = "relevanceScoreFloor:\n  value: " + scalar + "\n  provenance: 'by hand'\n";
            Files.writeString(store.file(), text, UTF_8);
            NumericValue theJars = store.load().relevanceScoreFloor();
            claim("the control panel reads value: " + scalar + " as the jar loads it",
                    () -> assertThat(ProfileEdit.read(text).get(ProfileKey.RELEVANCE_SCORE_FLOOR))
                            .isEqualTo(new Stated(theJars.value(), theJars.provenance())));
        }

        String adr284sExample = "- folder: shop/core\n  name: Shop core\n- folder: \"\"\n  name: Archive\n";
        for (String file : List.of(adr284sExample, "- folder: 'lib'\n  name: 'It''s ours'\n",
                "- folder: lib\n  name: 0x10\n", "- folder: lib\n  name: 007\n", "- folder: lib\n  name: yes\n",
                "- folder: lib\n  name: Null\n", "- folder: lib\n  name: ~\n")) {
            claim("the control panel reads " + file.replace("\n", " / ") + " to the entries CodeSetNames reads",
                    () -> assertThat(CodeSetRenames.read(Optional.of(file)).stream()
                                    .map(rename -> new CodeSetNames.Entry(rename.folder(), rename.name()))
                                    .toList())
                            .containsExactlyElementsOf(CodeSetNames.parse(file)));
        }
        for (String notText : List.of("null", "true", "false", "0.35", "-1e3", "12")) {
            String file = "- folder: lib\n  name: " + notText + "\n";
            claim("a plain name of " + notText + " is refused by CodeSetNames, and by the control panel",
                    () -> {
                        assertThatThrownBy(() -> CodeSetNames.parse(file)).isInstanceOf(IllegalArgumentException.class);
                        assertThatThrownBy(() -> CodeSetRenames.read(Optional.of(file)))
                                .isInstanceOf(IllegalArgumentException.class);
                    });
        }
    }

    @Test
    @Story("The jar reads what the control panel writes")
    @DisplayName("The jar's reader of code-set names reads the list the control panel writes, and refuses each list the control panel refuses")
    void codeSetNamesReadsTheListTheControlPanelWrites() {
        List<Rename> renames = List.of(new Rename("shop/core", "Shop \"core\" \\ ł"), new Rename("", "Archive"),
                new Rename("lib", "Libraries"));
        List<List<Rename>> refusedLists = List.of(
                List.of(new Rename("lib", "One"), new Rename("lib", "Two")),
                List.of(new Rename("lib", " ")),
                List.of(new Rename("shop\\core", "Shop")),
                List.of(new Rename("/shop", "Shop")),
                List.of(new Rename("shop/", "Shop")),
                List.of(new Rename("shop//core", "Shop")));

        claim("CodeSetNames reads the control panel's list as the same entries in the same order",
                () -> assertThat(CodeSetNames.parse(CodeSetRenames.render(renames))).containsExactlyElementsOf(
                        renames.stream().map(rename -> new CodeSetNames.Entry(rename.folder(), rename.name())).toList()));
        claim("and reads the control panel's empty list as none",
                () -> assertThat(CodeSetNames.parse(CodeSetRenames.render(List.of()))).isEmpty());
        for (List<Rename> list : refusedLists) {
            claim("the list " + list + " is refused by the control panel and, written by hand, by CodeSetNames",
                    () -> {
                        assertThatThrownBy(() -> CodeSetRenames.render(list)).isInstanceOf(IllegalArgumentException.class);
                        assertThatThrownBy(() -> CodeSetNames.parse(byHand(list)))
                                .isInstanceOf(IllegalArgumentException.class);
                    });
        }
    }

    @Test
    @Story("The jar reads what the control panel writes")
    @DisplayName("The jar and the control panel exclude each other from a working directory, each naming the other's holder line")
    void theJarAndTheControlPanelExcludeEachOther(@TempDir Path workingDirectory) throws Exception {
        WorkingDirectoryWrite write = new WorkingDirectoryWrite(() -> false, USER);
        Path lockFile = workingDirectory.resolve(WorkingDirectoryLock.FILE_NAME);
        Path profile = workingDirectory.resolve(ProfileEdit.FILE_NAME);
        String saved = ProfileEdit.rewrite(Optional.empty(), ProfileKey.EXTRACTION_ATTEMPT, "2", "a second attempt");

        Process holder = WorkingDirectoryLockTest.startHolder(workingDirectory);
        Optional<String> whileTheJarHoldsIt;
        String theJarsLine;
        try {
            theJarsLine = Files.readString(lockFile, UTF_8).strip();
            whileTheJarHoldsIt = write.write(workingDirectory, ProfileEdit.FILE_NAME, current -> saved);
        } finally {
            holder.destroyForcibly();
            holder.waitFor(CHILD_BOUND_SECONDS, TimeUnit.SECONDS);
        }
        claim("while the jar holds the working directory, the control panel writes nothing, naming the jar's line",
                () -> {
                    assertThat(whileTheJarHoldsIt).contains("another Vespera invocation is using the working directory "
                            + workingDirectory.toRealPath() + " (" + theJarsLine + "), so " + ProfileEdit.FILE_NAME
                            + " was not written; save again once it has ended");
                    assertThat(profile).doesNotExist();
                });

        List<String> theJarSaidDuringTheWrite = new ArrayList<>();
        Optional<String> once = write.write(workingDirectory, ProfileEdit.FILE_NAME, current -> {
            theJarSaidDuringTheWrite.add(tryInASecondProcess(workingDirectory));
            return saved;
        });
        claim("while the control panel writes, the jar is refused with its own line, naming the control panel's write",
                () -> assertThat(theJarSaidDuringTheWrite).singleElement().asString()
                        .startsWith(TriesTheWorkingDirectory.REFUSED
                                + "another Vespera invocation is already using the working directory ")
                        .contains("command=control-panel write " + ProfileEdit.FILE_NAME + ")"));
        claim("the control panel wrote the file", () -> {
            assertThat(once).isEmpty();
            assertThat(Files.readString(profile, UTF_8)).isEqualTo(saved);
        });
        claim("and once it has written, the jar takes the working directory",
                () -> assertThat(tryInASecondProcess(workingDirectory)).isEqualTo(TriesTheWorkingDirectory.HELD));
    }

    /** The samples §4 saves for each key; the seed folder is a folder that exists. */
    private static Map<ProfileKey, List<String>> acceptedSamples(Path seeds) {
        Map<ProfileKey, List<String>> samples = new LinkedHashMap<>();
        samples.put(ProfileKey.SEED_FOLDER, List.of(seeds.toString(), " " + seeds + " "));
        samples.put(ProfileKey.DEGENERATE_OUTPUT_CONFIDENCE_FLOOR, List.of("0.4", "0"));
        samples.put(ProfileKey.DEGENERATE_OUTPUT_WORD_FLOOR, List.of("0", "30", "2.0", "2147483647"));
        samples.put(ProfileKey.BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR, List.of("0.8", "1.5"));
        samples.put(ProfileKey.EMBEDDING_MODEL, List.of("nomic-embed-text:v1.5"));
        samples.put(ProfileKey.RELEVANCE_SCORE_FLOOR, List.of("0.579", " 0.6 "));
        samples.put(ProfileKey.ARRANGEMENT_APPROVED, List.of("0123456789ab", "fedcba987654", "auto"));
        samples.put(ProfileKey.GENERATION_MODEL, List.of("qwen3:14b"));
        samples.put(ProfileKey.GENERATION_CONTEXT_WINDOW, List.of("1282", "8192", "16384.0", "2147483647"));
        samples.put(ProfileKey.CLUSTER_PAGE_BYTE_CEILING, List.of("1", "300000", "2.0", "9223372036854775807"));
        samples.put(ProfileKey.LOG_TIMESTAMP_SHARE_FLOOR, List.of("0.6"));
        samples.put(ProfileKey.EXTRACTION_ATTEMPT, List.of("1", "2", "3.0", "2147483647"));
        samples.put(ProfileKey.CODE_NAME_ENDINGS, List.of(".java .kt", ".PY,.rs"));
        samples.put(ProfileKey.OUT_OF_SCOPE_NAME_ENDINGS, List.of(".P3A .DGN, .x.y"));
        samples.put(ProfileKey.TRANSLATION_SIMILARITY_FLOOR, List.of("0.85", "-0.2"));
        samples.put(ProfileKey.PREFERRED_LANGUAGES, List.of("it, en", "EN"));
        samples.put(ProfileKey.CONFIGURATION_SETTING_SHARE_FLOOR, List.of("0.7"));
        return samples;
    }

    /**
     * Whether the jar reads {@code loaded} as an answer it acts on, by the rule it applies to {@code key}: the reading
     * of the value's type, and for each key with a rule of its own, that rule.
     */
    private static boolean readAsAnAnswerByTheJar(ProfileKey key, ProfileValue loaded, ProfileStore store) {
        return switch (key) {
            case SEED_FOLDER -> {
                try {
                    Walk.canonicalRoot(Path.of(loaded.value()));
                    yield true;
                } catch (IllegalArgumentException notAFolder) {
                    yield false;
                }
            }
            case ARRANGEMENT_APPROVED -> new ArrangementGate(store)
                    .approvedArrangement(Optional.of(new RunId(
                            loaded.value().strip().toLowerCase(java.util.Locale.ROOT) + THE_REST_OF_A_RUN_ID)))
                    .isPresent()
                    && RunId.of("version", "{}", new WalkId(1), List.of()).value().matches("[0-9a-f]{64}");
            case EMBEDDING_MODEL, GENERATION_MODEL -> loaded.isSet();
            case DEGENERATE_OUTPUT_WORD_FLOOR ->
                    DegenerateOutputWordFloor.of((NumericValue) loaded) instanceof DegenerateOutputWordFloor.Floor;
            case CLUSTER_PAGE_BYTE_CEILING ->
                    ClusterPageByteCeiling.of((NumericValue) loaded) instanceof ClusterPageByteCeiling.Ceiling;
            case EXTRACTION_ATTEMPT -> !(ExtractionAttempt.of((NumericValue) loaded) instanceof ExtractionAttempt.Ignored)
                    && loaded.isSet();
            case GENERATION_CONTEXT_WINDOW -> {
                try {
                    yield new GenerationContextWindow(store).size()
                            == (int) Double.parseDouble(loaded.value().strip());
                } catch (IllegalStateException stopped) {
                    yield false;
                }
            }
            case CODE_NAME_ENDINGS, OUT_OF_SCOPE_NAME_ENDINGS ->
                    ((NameEndingsValue) loaded).reading() instanceof NameEndingsValue.Answered;
            case PREFERRED_LANGUAGES -> ((LanguagesValue) loaded).reading() instanceof LanguagesValue.Answered;
            default -> ((NumericValue) loaded).reading() instanceof NumericValue.Answered;
        };
    }

    /** The component of {@code profile} that answers {@code key}, found by the name profile.yaml spells. */
    private static ProfileValue componentOf(Profile profile, ProfileKey key) {
        try {
            return (ProfileValue) Profile.class.getMethod(key.key()).invoke(profile);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(key.key() + " is not a component of the profile", e);
        }
    }

    /** A profile holding {@code value} under {@code key} as the jar would read it, every other key unset. */
    private static Profile profileWith(ProfileKey key, String value) {
        Object[] components = new Object[ProfileKey.values().length];
        components[key.ordinal()] = valueOfKind(key, value, "typed by hand", null);
        return newProfile(components);
    }

    /** Every key answered with a value of its own and the odd provenance, the seed folder carrying a measurement. */
    private static Profile everyKeyAnswered() {
        Object[] components = new Object[ProfileKey.values().length];
        for (ProfileKey key : ProfileKey.values()) {
            components[key.ordinal()] = valueOfKind(key, "the " + key.key() + " value", AN_ODD_PROVENANCE,
                    key == ProfileKey.SEED_FOLDER
                            ? new Measurement("walk 3", Instant.parse("2026-10-19T10:00:00Z"))
                            : null);
        }
        return newProfile(components);
    }

    private static ProfileValue valueOfKind(ProfileKey key, String value, String provenance, Measurement measurement) {
        return switch (key.kind()) {
            case TEXT -> new TextValue(value, provenance, measurement);
            case NUMBER -> new NumericValue(value, provenance, measurement);
            case NAME_ENDINGS -> new NameEndingsValue(value, provenance, measurement);
            case LANGUAGES -> new LanguagesValue(value, provenance, measurement);
        };
    }

    private static Profile newProfile(Object[] components) {
        try {
            Class<?>[] types = Arrays.stream(Profile.class.getRecordComponents())
                    .map(RecordComponent::getType)
                    .toArray(Class<?>[]::new);
            return Profile.class.getConstructor(types).newInstance(components);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the profile's canonical constructor could not be called", e);
        }
    }

    private static RelevanceDistribution.Sampled sampled(long occurrence, double score, int band) {
        return new RelevanceDistribution.Sampled(new OccurrenceId(occurrence), score, new OccurrenceId(99), band);
    }

    /** The list as an operator would write it by hand, in the control panel's own form, refused or not. */
    private static String byHand(List<Rename> list) {
        StringBuilder text = new StringBuilder("---\n");
        for (Rename rename : list) {
            text.append("- folder: \"").append(rename.folder().replace("\\", "\\\\")).append("\"\n")
                    .append("  name: \"").append(rename.name()).append("\"\n");
        }
        return text.toString();
    }

    /** What a second JVM taking the working directory as an invocation does printed: held, or refused and why. */
    private static String tryInASecondProcess(Path workingDirectory) {
        try {
            Process process = new ProcessBuilder(
                            ProcessHandle.current().info().command().orElseThrow(),
                            "-cp", System.getProperty("java.class.path"),
                            TriesTheWorkingDirectory.class.getName(),
                            workingDirectory.toString())
                    .redirectErrorStream(true)
                    .start();
            String said;
            try (BufferedReader printed =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8))) {
                said = printed.lines()
                        .filter(line -> line.startsWith(TriesTheWorkingDirectory.HELD)
                                || line.startsWith(TriesTheWorkingDirectory.REFUSED))
                        .findFirst()
                        .orElse("");
            }
            process.waitFor(CHILD_BOUND_SECONDS, TimeUnit.SECONDS);
            return said.strip();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
