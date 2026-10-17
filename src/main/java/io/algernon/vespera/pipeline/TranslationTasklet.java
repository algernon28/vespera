package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.ClusteringProgress;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.embedding.TranslationOf;
import io.algernon.vespera.embedding.Translations;
import io.algernon.vespera.extraction.ChunkingRule;
import io.algernon.vespera.extraction.ExtractionCacheKeys;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.HybridChunker;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.profile.LanguagesValue;
import io.algernon.vespera.profile.Measurement;
import io.algernon.vespera.profile.NumericValue;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stage 5's translation step (ADR-271): between embedding and relevance scoring, under the scoring run, it
 * removes all but one copy of a content held in several languages, as {@code translation-of}.
 *
 * <p><b>It holds no rule.</b> It reads the survivors of the seed measurement run a page at a time, their paths
 * through the ledger and their languages through {@code ExtractionMetrics}, and hands them to {@code embedding},
 * whose {@link Translations} decides which are translations of which and which copy survives, and whose {@link
 * TranslationOf} finds the likenesses among their mean vectors. This class writes what comes back: the verdicts,
 * the rows, the page, and the profile's pointers at the page.
 *
 * <p><b>It is not a gate</b>, for ADR-088's reason: it writes the page both keys are read off. It consults the
 * gates every step of the scoring half consults, and records its completion under the scoring run, which names
 * everything it reads (ADR-116).
 */
@Component
@StepScope
class TranslationTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(TranslationTasklet.class);

    /** The step's own name, which its lines open with. */
    private static final String STAGE = "Stage 5 (translations)";

    /** The page's name in the working directory (ADR-271 section 7). */
    static final String PAGE_FILE_NAME = "translations.html";

    /** What a removal records as its reason; it names no path. */
    private static final String REASON = "the content is carried, in another language, by an occurrence that survives";

    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final StageRuns stageRuns;
    private final Ledger ledger;
    private final HybridChunker hybridChunker;
    private final RelevanceDistribution relevanceDistribution;
    private final TranslationOf translationOf;
    private final ExtractionMetrics extractionMetrics;
    private final ExtractionCacheKeys cacheKeys;
    private final ProfileStore profileStore;
    private final Clock clock;
    private final Path workingDirectory;

    TranslationTasklet(
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            StageRuns stageRuns,
            Ledger ledger,
            HybridChunker hybridChunker,
            RelevanceDistribution relevanceDistribution,
            TranslationOf translationOf,
            ExtractionMetrics extractionMetrics,
            JdbcTemplate jdbcTemplate,
            ProfileStore profileStore,
            Clock clock,
            @Value("${vespera.working-dir}") Path workingDirectory) {
        this.seedGate = seedGate;
        this.usableSeedGate = usableSeedGate;
        this.stageRuns = stageRuns;
        this.ledger = ledger;
        this.hybridChunker = hybridChunker;
        this.relevanceDistribution = relevanceDistribution;
        this.translationOf = translationOf;
        this.extractionMetrics = extractionMetrics;
        this.cacheKeys = new ExtractionCacheKeys(jdbcTemplate);
        this.profileStore = profileStore;
        this.clock = clock;
        this.workingDirectory = workingDirectory;
    }

    /** A survivor as read: the two forms {@code embedding} is handed it in. */
    private record Entry(Translations.Member member, TranslationOf.Candidate candidate) {}

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        StageFiveGates.Preamble preamble = StageFiveGates.modelSeedWalkUsable(
                "stage 5's translation step", stageRuns, seedGate, usableSeedGate);
        if (!preamble.isOpen()) {
            LOG.info(preamble.shutSentence().orElseThrow());
            ShutGates.in(chunkContext).record(StepNames.TRANSLATION);
            return RepeatStatus.FINISHED;
        }

        RunId measurementRun = stageRuns.seedMeasurement();
        RunId scoring = stageRuns.embeddingScoring();
        String modelName = stageRuns.embeddingModelName();

        return TaskletSteps.once(
                ledger,
                scoring,
                StepNames.TRANSLATION,
                // The page is not rewritten: it was written from these very rows (ADR-262 section 3).
                () -> LOG.info("{} was already recorded under run {}", STAGE, scoring.value()),
                () -> {
                    ledger.verdicts().discardVerdicts(scoring, VerdictKind.TRANSLATION_OF);
                    translationOf.discardForRun(scoring);
                },
                () -> {
                    Optional<String> identity = TimedStatement.of(
                            STAGE,
                            "reading",
                            "read",
                            "the embedder identities",
                            () -> relevanceDistribution.embedderIdentityFor(
                                    modelName, stageRuns.embeddingModelArtefact()));
                    if (identity.isEmpty()) {
                        LOG.info(
                                "stage 5's translation step is gated: the vectors under {} carry no single"
                                        + " embedder identity, so no survivor's mean vector can be read. Nothing"
                                        + " was compared.",
                                modelName);
                        ShutGates.in(chunkContext).record(StepNames.TRANSLATION);
                        return false;
                    }
                    List<Entry> entries = readSurvivors(measurementRun);
                    LOG.info("{} starting under scoring run {}: {} survivor(s) enter", STAGE, scoring.value(), entries.size());

                    List<Translations.Likeness> likenesses = TimedStatement.of(
                            STAGE,
                            "comparing",
                            "compared",
                            "the mean vectors of " + entries.size() + " survivor(s) across their languages",
                            () -> translationOf.likenessesAmong(
                                    entries.stream().map(Entry::candidate).toList(),
                                    hybridChunker.identity(),
                                    ChunkingRule.DEFAULT.identity().value(),
                                    identity.get(),
                                    blocksCompared()));

                    OptionalDouble floor = stageRuns.translationSimilarityFloor();
                    List<String> preferred = stageRuns.preferredLanguages();
                    Translations.Resolution resolution = Translations.resolve(
                            entries.stream().map(Entry::member).toList(), likenesses, floor, preferred);
                    StageProgress written =
                            StageProgress.over("Stage 5 (translations, translation-of verdicts)", resolution.removals().size());
                    for (Translations.Removal removal : resolution.removals()) {
                        ledger.verdicts().verdict(removal.removed(), scoring, VerdictKind.TRANSLATION_OF, REASON);
                        translationOf.record(scoring, removal);
                        written.itemDone();
                    }

                    Path page = writePage(resolution, scoring, modelName, entries.size(), floor, preferred);
                    pointTheProfileAt(page);

                    long found = resolution.rows().stream()
                            .mapToLong(Translations.SimilarityRow::likenesses)
                            .sum();
                    LOG.info(
                            "{} finished under scoring run {}: {} translation set(s), {} occurrence(s) removed as"
                                    + " translation-of, {} set(s) left standing; the page is {}",
                            STAGE,
                            scoring.value(),
                            resolution.sets(),
                            resolution.removals().size(),
                            resolution.setsStanding(),
                            page);
                    if (preferred.isEmpty() && resolution.sets() - resolution.setsStanding() > 0) {
                        LOG.info(
                                "{} removed nothing: {} translation set(s) wait for preferredLanguages. Write the"
                                        + " languages to keep into preferredLanguages in profile.yaml, most"
                                        + " preferred first, reading {}.",
                                STAGE,
                                resolution.sets() - resolution.setsStanding(),
                                PAGE_FILE_NAME);
                    }
                    if (floor.isEmpty() && found > 0) {
                        LOG.info(
                                "{}: {} likeness(es) across languages were not applied. translationSimilarityFloor"
                                        + " is read off {}.",
                                STAGE,
                                found,
                                PAGE_FILE_NAME);
                    }
                    return true;
                });
    }

    /**
     * The survivors of {@code measurementRun}, a page at a time, with their languages and the keys their vectors
     * are stored under, in occurrence order. What the step holds is their ids, paths, languages and keys.
     */
    private List<Entry> readSurvivors(RunId measurementRun) {
        RunId extractionRun = stageRuns.upstream(StageModules.EXTRACTION);
        long total = TimedStatement.of(
                STAGE, "counting", "counted", "the survivors", () -> ledger.verdicts().survivorCount(measurementRun));
        ProgressPage.entered(total);
        StageProgress read = StageProgress.over("Stage 5 (translations, survivors read)", total);
        List<Entry> entries = new ArrayList<>();
        ledger.verdicts().eachPageOfSurvivorsByPath(measurementRun, page -> {
            List<OccurrenceId> ids = page.stream().map(at -> at.occurrenceId()).toList();
            Map<OccurrenceId, String> languages = new HashMap<>();
            extractionMetrics.eachMeasuredFormOf(
                    extractionRun, ids, (occurrence, language, scoreIsNull, words, pages, vowelless, single) ->
                            languages.put(occurrence, language));
            Map<OccurrenceId, String> keys = cacheKeys.requireForOccurrences(ids, extractionRun);
            page.forEach(at -> {
                String language = languages.get(at.occurrenceId());
                entries.add(new Entry(
                        new Translations.Member(at.occurrenceId(), at.path(), language),
                        new TranslationOf.Candidate(at.occurrenceId(), keys.get(at.occurrenceId()), language)));
                read.itemDone();
            });
        });
        entries.sort(Comparator.comparingLong(entry -> entry.member().occurrence().value()));
        return entries;
    }

    private static ClusteringProgress blocksCompared() {
        return new ClusteringProgress() {
            private StageProgress compared;

            @Override
            public void toCompareBlocks(long blockPairs) {
                compared = StageProgress.over("Stage 5 (translations, comparison blocks)", blockPairs);
            }

            @Override
            public void blockPairCompared() {
                compared.itemDone();
            }
        };
    }

    private Path writePage(
            Translations.Resolution resolution,
            RunId scoring,
            String modelName,
            int survivors,
            OptionalDouble floor,
            List<String> preferred) {
        Profile profile = profileStore.load();
        String floorReading = floor.isPresent()
                ? "read as " + floor.getAsDouble() + ", at or above which a likeness is applied"
                : profile.translationSimilarityFloor().reading() instanceof NumericValue.Unreadable
                        ? "unreadable, so no likeness is applied"
                        : "unset, so no likeness is applied";
        String languagesReading = !preferred.isEmpty()
                ? "read as " + String.join(", ", preferred) + ", most preferred first"
                : profile.preferredLanguages().reading() instanceof LanguagesValue.Unreadable
                        ? "unreadable, so nothing is removed"
                        : "unset, so nothing is removed";
        String html = TranslationReport.render(
                resolution,
                scoring,
                modelName,
                survivors,
                resolution.removals().size(),
                floorReading,
                languagesReading,
                !preferred.isEmpty());
        Path file = workingDirectory.resolve(PAGE_FILE_NAME);
        try {
            Files.createDirectories(workingDirectory);
            Files.writeString(file, html, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the report at " + file, e);
        }
        return file;
    }

    private void pointTheProfileAt(Path page) {
        Measurement measurement = new Measurement(page.toString(), clock.instant());
        profileStore.save(profileStore.load()
                .withTranslationSimilarityFloorMeasurement(measurement)
                .withPreferredLanguagesMeasurement(measurement));
    }
}
