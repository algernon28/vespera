package io.algernon.vespera.extraction;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.ResourceAccessException;

/**
 * The <b>real</b> {@link DoclingExtractor} over the <b>real</b> {@link ExtractionCache}, with a client
 * that can be told to stop answering part-way through one folder, the way {@code docling-serve} did
 * when it crashed during seed extraction on 2026-09-26 (#306).
 *
 * <p>Once armed with {@link #stopAnsweringAfter}, conversions of files under the named folder are
 * answered that many times and then every later one throws the {@link ResourceAccessException} the
 * real client lets through when the sidecar is gone. Every later one, not just the next: a crashed
 * sidecar does not come back by itself. Files outside that folder, the corpus among them, are always
 * answered, so the corpus pass that runs first cannot use up the count.
 *
 * <p>The failure is thrown by the client, one layer below the extractor, so the cache lookup and the
 * cache write around it are the production ones. That is the same seam {@link CountingDoclingBeans}
 * uses, and for the same reason: it lives in this package because {@link ExtractionCache},
 * {@link DoclingExtractor}'s constructor and {@link DoclingClient}'s {@code convert} are all
 * package-private.
 *
 * <p>The arming is static, because the client is a bean built once per Spring context and every test
 * method of a class shares it. A test that arms it resets it in both {@code @BeforeEach} and {@code
 * @AfterEach}, since class order differs from one machine to the next.
 *
 * <p>{@code @TestConfiguration} rather than {@code @Configuration}, for the reason {@code
 * StubbedExtractionBeans} documents: left plain, this sits inside the application's component-scan
 * package and would silently replace the real client everywhere.
 */
@TestConfiguration
public class ConverterStopsAnsweringBeans {

    /**
     * What the client throws once it has stopped answering: an I/O failure on the call itself, with no
     * response. Not a timeout, so the real client's own timeout translation would not apply to it
     * either.
     */
    public static final String CONNECTION_FAILURE =
            "I/O error on POST request for http://localhost:5001/v1/convert/file: Unexpected end of file from server";

    /** The title every converted document carries, so nothing downstream finds a document with none. */
    private static final String WITH_TEXT = "{\"document\":{\"json_content\":{\"texts\":["
            + "{\"text\":\"A Stubbed Document\",\"label\":\"title\"},"
            + "{\"text\":\"stubbed but real content\"}]}}}";

    /** The folder whose conversions are counted, or {@code null} while unarmed. */
    private static final AtomicReference<Path> COUNTED_FOLDER = new AtomicReference<>();

    /** How many more conversions under {@link #COUNTED_FOLDER} are answered before the client stops. */
    private static final AtomicInteger ANSWERS_LEFT = new AtomicInteger();

    /** How many conversions the client refused since it was last armed or disarmed. */
    private static final AtomicInteger REFUSED = new AtomicInteger();

    /**
     * Arms the crash: the next {@code answered} conversions of files under {@code folder} are answered,
     * and every one after them fails.
     */
    public static void stopAnsweringAfter(Path folder, int answered) {
        ANSWERS_LEFT.set(answered);
        REFUSED.set(0);
        COUNTED_FOLDER.set(real(folder));
    }

    /**
     * The path as the file system names it, so that a temporary folder reached through a short or
     * linked name still matches the canonical root the walk resolved it to.
     */
    private static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException("the fixture could not resolve " + path, e);
        }
    }

    /** Disarms it, which is the sidecar being brought back. */
    public static void keepAnswering() {
        COUNTED_FOLDER.set(null);
        ANSWERS_LEFT.set(0);
        REFUSED.set(0);
    }

    /** How many conversions were refused since the last arming or disarming. */
    public static int refusedConversions() {
        return REFUSED.get();
    }

    @Bean
    DoclingClient doclingClient() {
        return new DoclingClient("unused") {

            @Override
            public void checkHealth() {}

            @Override
            public Map<String, String> version() {
                return Map.of("docling-serve", "1.32.0", "docling", "2.124.0");
            }

            @Override
            DoclingResponse convert(Path file, DetectedFormat format, DetectedSubtype subtype) {
                Path folder = COUNTED_FOLDER.get();
                if (folder != null
                        && real(file).startsWith(folder)
                        && ANSWERS_LEFT.getAndDecrement() <= 0) {
                    REFUSED.incrementAndGet();
                    throw new ResourceAccessException(CONNECTION_FAILURE);
                }
                return new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 0d, null, WITH_TEXT);
            }
        };
    }

    @Bean
    ExtractionCache extractionCache(JdbcTemplate jdbcTemplate) {
        return new ExtractionCache(jdbcTemplate);
    }

    @Bean
    DoclingExtractor doclingExtractor(DoclingClient doclingClient, ExtractionCache extractionCache) {
        return new DoclingExtractor(doclingClient, extractionCache);
    }
}
