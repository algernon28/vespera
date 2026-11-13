package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.ui.CodeSetRenames.Rename;
import io.algernon.vespera.ui.ProfileEdit;
import io.algernon.vespera.ui.ProfileEdit.Stated;
import io.algernon.vespera.ui.ProfileKey;
import io.algernon.vespera.ui.WorkingDirectoryWrite;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.CodeSetNames;
import io.algernon.vespera.engine.embedding.RelevanceDistribution;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.profile.Measurement;
import io.algernon.vespera.engine.profile.NumericValue;
import io.algernon.vespera.engine.profile.Profile;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.profile.ProfileValue;
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

    private static final long CHILD_BOUND_SECONDS = 60;

    /** The key ADR-340 §8.1 adds, by the name profile.yaml spells. */
    private static final String FOLDERS_NOT_WALKED = "foldersNotWalked";

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

    /** The key ADR-384 adds, by the name profile.yaml spells, which the jar writes as a comment while it is unset. */
    private static final String MODELS_RUN_ON = "modelsRunOn";

    /** The three lines the jar writes in its place while it is unset (ADR-384 §6). */
    private static final String THE_COMMENTED_KEY = "# Where the models run: local, on this machine. Cloud is not"
            + " supported yet.\n# modelsRunOn:\n#   value: local\n";

    @Test
    @Story("The jar reads what the control panel writes")
    @DisplayName("Where the models run, which the jar writes as a comment while it is unset, is read as unset by the control panel and kept line for line by its saves")
    @Link(name = "ADR-384", url = Adr.WHETHER_THE_MODELS_RUN_LOCALLY_OR_IN_THE_CLOUD_IS_THE_PROFILES_TO_SAY, type = "adr")
    void theCommentedKeyTheJarWritesIsKeptByTheControlPanel(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        store.save(store.load());
        String asTheJarWroteIt = Files.readString(store.file(), UTF_8);

        claim("the jar wrote where the models run as its three lines of comment",
                () -> assertThat(asTheJarWroteIt).contains(THE_COMMENTED_KEY));
        claim("the control panel reads the file, and finds no value of where the models run in it",
                () -> assertThat(ProfileEdit.read(asTheJarWroteIt).keySet())
                        .noneMatch(key -> key.key().equals(MODELS_RUN_ON)));

        String rewritten = ProfileEdit.rewrite(
                Optional.of(asTheJarWroteIt), ProfileKey.GENERATION_MODEL, "a-writing-model:8b", "measured");
        String withoutTheModel = ProfileEdit.without(rewritten, ProfileKey.GENERATION_MODEL);
        claim("a value saved beside the comment, and the block before it taken out, each keep its three lines as the jar"
                        + " wrote them",
                () -> assertThat(List.of(rewritten, withoutTheModel)).allSatisfy(text -> assertThat(text)
                        .contains(THE_COMMENTED_KEY)));
        Files.writeString(store.file(), rewritten, UTF_8);
        Profile reloaded = store.load();
        claim("and the jar loads the file the control panel wrote, where the models run unset and the value saved",
                () -> {
                    assertThat(reloaded.modelsRunOn().isSet()).isFalse();
                    assertThat(reloaded.generationModel().value()).isEqualTo("a-writing-model:8b");
                });
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

    /**
     * The control panel's key of ADR-340 §8.1, found by the name profile.yaml spells, so that this compiles before the
     * table has it; a table without it fails the test naming it.
     */
    private static ProfileKey foldersNotWalked() {
        return Arrays.stream(ProfileKey.values()).filter(key -> key.key().equals(FOLDERS_NOT_WALKED)).findFirst()
                .orElseThrow(() -> new AssertionError("the control panel's table of keys has no " + FOLDERS_NOT_WALKED));
    }

    /** The component of {@code profile} that answers {@code key}, found by the name profile.yaml spells. */
    private static ProfileValue componentOf(Profile profile, ProfileKey key) {
        try {
            return (ProfileValue) Profile.class.getMethod(key.key()).invoke(profile);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(key.key() + " is not a component of the profile", e);
        }
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

    /**
     * The value of {@code key}'s component, built by the component's own type, which takes the value, the provenance and
     * the measurement as every value type does, so that a kind a record adds needs no case here.
     */
    private static ProfileValue valueOfKind(ProfileKey key, String value, String provenance, Measurement measurement) {
        Class<?> type = Profile.class.getRecordComponents()[key.ordinal()].getType();
        try {
            return (ProfileValue) type.getConstructor(String.class, String.class, Measurement.class)
                    .newInstance(value, provenance, measurement);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(type.getSimpleName() + " takes no value, provenance and measurement", e);
        }
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
