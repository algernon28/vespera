package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.PathScriptedExtractor;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The corpus of ADR-330 §9's cut, shared by the class that finds its pair at the shipped cut and the one that does not at
 * another. The first two documents share their first {@value #SHARED_WORDS} of {@value #WORDS} words: at five words a
 * shingle that is 86 shingles shared of 96 each, a Jaccard of 86 over 106, about 0.81, so at or above the shipped 0.80
 * and below 0.9; and a containment of 86 over 96, about 0.90, below the shipped 0.95, so the near-duplicate cut alone
 * decides. A third document of its own keeps the shared shingles from being every document's, which the floor would
 * strip as boilerplate.
 */
@TestConfiguration
class TwoDocumentsNearlyAlike {

    private static final int WORDS = 100;

    private static final int SHARED_WORDS = 90;

    /** Above every measured document frequency here, so stage 4's gate is open, and strips only what all three share. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    @Bean
    @Primary
    DoclingExtractor twoDocumentsNearlyAlikeAndAThird(JdbcTemplate jdbcTemplate) {
        String shared = TextsOfTheirOwn.words("shared", SHARED_WORDS);
        return new PathScriptedExtractor().cachingInto(jdbcTemplate)
                .answering("near-0.txt", TextsOfTheirOwn.converted(
                        shared + " " + TextsOfTheirOwn.words("first", WORDS - SHARED_WORDS)))
                .answering("near-1.txt", TextsOfTheirOwn.converted(
                        shared + " " + TextsOfTheirOwn.words("second", WORDS - SHARED_WORDS)))
                .answering("apart.txt", TextsOfTheirOwn.converted(TextsOfTheirOwn.words("apart", WORDS)));
    }

    /** The three files, and a profile with stage 4's gate open. */
    static void aCorpusIn(Path root, ProfileStore profileStore) throws IOException {
        Files.writeString(root.resolve("near-0.txt"), "the bytes of the first");
        Files.writeString(root.resolve("near-1.txt"), "the bytes of the second");
        Files.writeString(root.resolve("apart.txt"), "the bytes of the third");
        profileStore.save(ProfileFixture.profile()
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .build());
    }
}
