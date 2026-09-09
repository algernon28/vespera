package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Turning a completed label file into rows (ADR-088, #111), invoked by a person through
 * {@code vespera label} and never as a step in the job.
 *
 * <p><b>It mints no run.</b> A label is a fact about a document rather than something derived under
 * a configuration, so there is nothing here for a run to own. That is also why the answers survive
 * everything that produced them: a re-score under a new model re-reads them rather than asking
 * again.
 *
 * <p><b>The current sample is the label file the last scoring run wrote.</b> A file offered against
 * any other sample is refused whole rather than partially matched, and the refusal names both runs
 * so the operator can find the right file.
 */
@Component
class LabelIngestion {

    private static final Logger LOG = LoggerFactory.getLogger(LabelIngestion.class);

    /** What an operator is told when the thing they named is not there to read. */
    static final String NO_FILE = "no label file to read";

    private final Ledger ledger;
    private final RelevanceLabels relevanceLabels;
    private final ProfileStore profileStore;
    private final Path workingDirectory;

    LabelIngestion(
            Ledger ledger,
            RelevanceLabels relevanceLabels,
            ProfileStore profileStore,
            @Value("${vespera.working-dir}") Path workingDirectory) {
        this.ledger = ledger;
        this.relevanceLabels = relevanceLabels;
        this.profileStore = profileStore;
        this.workingDirectory = workingDirectory;
    }

    /** What ingesting a file did, or why it did nothing. */
    record Outcome(boolean refused, String message) {

        static Outcome refused(String message) {
            return new Outcome(true, message);
        }

        static Outcome recorded(String message) {
            return new Outcome(false, message);
        }
    }

    /**
     * Records the answers in {@code namedFile}, or the label file the last run wrote when no file is
     * named — that is where this application put it, so pointing at it is not a guess.
     */
    Outcome ingest(Optional<Path> namedFile) {
        Path generated = workingDirectory.resolve(RelevanceLabelFile.FILE_NAME);
        Path offered = namedFile.orElse(generated);

        if (!Files.isRegularFile(offered)) {
            return Outcome.refused(NO_FILE + " at " + offered
                    + ". A sample is written there by a scoring run; run vespera against the corpus"
                    + " first, answer the questions in the file, then run this again.");
        }
        if (!Files.isRegularFile(generated)) {
            return Outcome.refused("no sample has been generated yet, so there is nothing to check this"
                    + " file against. Run vespera against the corpus first.");
        }

        String offeredYaml = read(offered);
        Optional<String> currentRun = LabelFileReader.runNamedBy(read(generated));
        if (currentRun.isEmpty()) {
            return Outcome.refused("the generated label file at " + generated + " names no run, so there"
                    + " is no sample to check this file against. Regenerate it and label the new file.");
        }

        LabelFileReader.Outcome outcome = LabelFileReader.read(offeredYaml, currentRun.get());
        if (outcome instanceof LabelFileReader.Refused refused) {
            return Outcome.refused(refused.reason());
        }

        LabelFileReader.Answers answers = (LabelFileReader.Answers) outcome;
        RunId run = new RunId(currentRun.get());
        Optional<WalkId> walk = ledger.walkOf(run);
        if (walk.isEmpty()) {
            return Outcome.refused("the label file names run " + run.value() + ", which this database does"
                    + " not hold. Nothing was recorded.");
        }

        String seedSet = seedSet();
        if (seedSet == null) {
            return Outcome.refused("no seed folder is named in the profile, so there is no seed set for"
                    + " these answers to be about. Nothing was recorded.");
        }

        int recorded = 0;
        int unknown = 0;
        for (LabelFileReader.Answer answer : answers.answers()) {
            Optional<OccurrenceId> occurrence =
                    ledger.occurrenceId(walk.get(), new OccurrencePath(answer.path()));
            if (occurrence.isEmpty()) {
                unknown++;
                continue;
            }
            relevanceLabels.record(
                    occurrence.get(),
                    seedSet,
                    answer.relevant(),
                    run,
                    answer.scoreShown(),
                    answers.embedderIdentity());
            recorded++;
        }

        String message = "recorded " + recorded + " answer(s) about the seed set at " + seedSet
                + (answers.unanswered() > 0 ? "; " + answers.unanswered() + " question(s) are still blank" : "")
                + (unknown > 0 ? "; " + unknown + " named a document this walk does not hold" : "");
        LOG.info("{}", message);
        return Outcome.recorded(message);
    }

    /** The seed folder the answers are about, canonicalised the way every other reader of it is. */
    private String seedSet() {
        Profile profile = profileStore.load();
        if (!profile.seedFolder().isSet()) {
            return null;
        }
        return Walk.canonicalRoot(Path.of(profile.seedFolder().value())).toString();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }
}
