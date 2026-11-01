package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.LocalOllamaModel;
import io.algernon.vespera.embedding.OllamaClient;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks ADR-202's check whether a model may be sent text, and pulls into Ollama a model it does not serve before the
 * check is asked a last time (ADR-329).
 *
 * <p>The order (§3): the check is asked; a model it passes is never pulled. Only where it refused, pulling is on, the
 * name does not end in {@code cloud} and {@code /api/show} answers 404 again is {@code POST /api/pull} sent, with the
 * name as given and the answer streamed. The check is then asked again and its answer stands.
 *
 * <p>The pull is given up when it has not moved for {@code vespera.models.pull-stalled-at-most} (§6), whether Ollama
 * goes on sending lines that change nothing or sends none. Nothing is deleted whatever ends it (§4, §7).
 */
@Component
class ModelPull {

    private static final Logger LOG = LoggerFactory.getLogger(ModelPull.class);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The bytes of a megabyte as the counter line counts them. */
    private static final long MEGABYTE = 1_000_000L;

    /** The counter line is written at most once for each twentieth of the total. */
    private static final int TWENTIETHS = 20;

    private static final String SUCCESS = "success";

    /** The end of the answer, as the reading thread hands it over. */
    private static final Object END = new Object();

    private static final String NOTHING_SERVED =
            "it serves nothing of it until a pull ends, and the next pull resumes what was downloaded.";

    private final ModelPullSettings settings;
    private final OllamaClient ollama;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Autowired
    ModelPull(Environment environment, OllamaClient ollama) {
        this(ModelPullSettings.from(environment), ollama);
    }

    ModelPull(ModelPullSettings settings, OllamaClient ollama) {
        this.settings = settings;
        this.ollama = ollama;
    }

    /**
     * The reason {@code modelName} must not be sent anything, or empty where Ollama serves it on this machine,
     * after pulling it where ADR-329 §3 says to.
     *
     * @param stage the step asking, as it names itself in its lines, such as {@code Stage 5c (embedding scoring)}
     */
    Optional<String> refusalOf(String stage, String modelName) {
        Optional<String> refusal = LocalOllamaModel.refusalOf(modelName, ollama);
        if (refusal.isEmpty()
                || !settings.pull()
                || modelName.toLowerCase(Locale.ROOT).endsWith("cloud")
                || !ollamaServesNothingNamed(modelName)) {
            return refusal;
        }
        LOG.info("{} Ollama serves no model named {}; pulling it", stage, modelName);
        long started = System.nanoTime();
        Optional<String> failure = pull(stage, modelName);
        if (failure.isPresent()) {
            return failure;
        }
        LOG.info(
                "{} pulled {} into Ollama in {} seconds",
                stage,
                modelName,
                TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started));
        return LocalOllamaModel.refusalOf(modelName, ollama);
    }

    /** Whether {@code /api/show} answers 404: the one answer from which a model is pulled. */
    private boolean ollamaServesNothingNamed(String modelName) {
        try {
            ollama.isRemote(modelName);
            return false;
        } catch (HttpClientErrorException.NotFound neverPulled) {
            return true;
        } catch (RuntimeException other) {
            return false;
        }
    }

    /** Pulls the model, answering the reason it did not end in {@code success}, or empty where it did. */
    private Optional<String> pull(String stage, String modelName) {
        Duration bound = settings.stalledAtMost();
        long boundSeconds = bound.toSeconds();
        Pulled progress = new Pulled(stage.replaceFirst("\\)$", ", megabytes pulled)"));
        long lastMoved = System.nanoTime();
        InputStream body = null;
        CompletableFuture<HttpResponse<InputStream>> sent = null;
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(settings.ollamaBaseUrl().toString().replaceAll("/+$", "") + "/api/pull"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            JSON.writeValueAsString(Map.of("model", modelName)), StandardCharsets.UTF_8))
                    .build();
            sent = http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
            HttpResponse<InputStream> response;
            try {
                response = sent.get(bound.toNanos(), TimeUnit.NANOSECONDS);
            } catch (TimeoutException silent) {
                return Optional.of(stalled(modelName, boundSeconds));
            }
            body = response.body();
            if (response.statusCode() / 100 != 2) {
                BlockingQueue<Object> refusal = readInBackground(
                        body, (answer, into) -> into.add(new String(answer.readAllBytes(), StandardCharsets.UTF_8)));
                int code = response.statusCode();
                long left = bound.toNanos() - (System.nanoTime() - lastMoved);
                Object answered = left <= 0 ? null : refusal.poll(left, TimeUnit.NANOSECONDS);
                if (answered == null) {
                    return Optional.of(refused(
                            modelName,
                            code,
                            "had not come " + boundSeconds + " seconds after the pull was asked for"));
                }
                if (answered instanceof Exception failedRead) {
                    return Optional.of(refused(modelName, code, "could not be read: " + innermostMessage(failedRead)));
                }
                String text = (String) answered;
                return Optional.of("Ollama would not pull " + modelName + " (" + response.statusCode() + ": "
                        + oneLine(errorIn(text).orElse(text.strip()))
                        + "), so nothing of it was pulled; correct the name and run the same command again");
            }
            BlockingQueue<Object> lines = readInBackground(body, ModelPull::readLines);
            String lastStatus = null;
            Map<String, Long> completedOf = new HashMap<>();
            while (true) {
                long remaining = bound.toNanos() - (System.nanoTime() - lastMoved);
                if (remaining <= 0) {
                    return Optional.of(stalled(modelName, boundSeconds));
                }
                Object next = lines.poll(remaining, TimeUnit.NANOSECONDS);
                if (next == null) {
                    return Optional.of(stalled(modelName, boundSeconds));
                }
                if (next == END) {
                    return Optional.of(stopped(modelName, "the answer ended before the pull did"));
                }
                if (next instanceof IOException failed) {
                    return Optional.of(stopped(modelName, innermostMessage(failed)));
                }
                JsonNode line;
                try {
                    line = JSON.readTree((String) next);
                } catch (RuntimeException unreadable) {
                    continue;
                }
                String error = line.path("error").asString("");
                if (!error.isEmpty()) {
                    return Optional.of("Ollama could not pull " + modelName + " (" + oneLine(error) + "); "
                            + NOTHING_SERVED + " Correct the name, make room on Ollama's drive or restore its"
                            + " network, as Ollama's words say, and run the same command again");
                }
                String status = line.path("status").asString("");
                if (SUCCESS.equals(status)) {
                    return Optional.empty();
                }
                boolean moved = !status.equals(lastStatus);
                lastStatus = status;
                String digest = line.path("digest").asString("");
                if (!digest.isEmpty()) {
                    long completed = line.path("completed").asLong(0L);
                    long total = line.path("total").asLong(0L);
                    Long before = completedOf.put(digest, completed);
                    moved |= before == null ? completed > 0 : completed > before;
                    progress.layer(digest, completed, total);
                }
                if (moved) {
                    lastMoved = System.nanoTime();
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.of(stopped(modelName, "interrupted"));
        } catch (ExecutionException failed) {
            return Optional.of(stopped(modelName, innermostMessage(failed)));
        } catch (RuntimeException failed) {
            return Optional.of(stopped(modelName, innermostMessage(failed)));
        } finally {
            if (body != null) {
                try {
                    body.close();
                } catch (IOException | RuntimeException ignored) {
                    // the connection is being dropped either way
                }
            }
            if (sent != null) {
                sent.cancel(true);
            }
        }
    }

    /** What a virtual thread does with the answer's body, handing what it reads to {@code into}. */
    @FunctionalInterface
    private interface Reading {
        void read(InputStream body, BlockingQueue<Object> into) throws IOException;
    }

    /** Reads the body on a virtual thread; a failure to read is handed over as an {@link IOException}. */
    private static BlockingQueue<Object> readInBackground(InputStream body, Reading reading) {
        BlockingQueue<Object> into = new LinkedBlockingQueue<>();
        Thread.ofVirtual().start(() -> {
            try {
                reading.read(body, into);
            } catch (IOException | RuntimeException failed) {
                into.add(failed instanceof IOException io ? io : new IOException(failed.getMessage(), failed));
            }
        });
        return into;
    }

    private static void readLines(InputStream body, BlockingQueue<Object> lines) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            lines.add(END);
        }
    }

    /** The innermost cause's message, or its class's simple name where that message is null or blank (ADR-329 §8). */
    private static String innermostMessage(Throwable failure) {
        Set<Throwable> passed = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable innermost = failure;
        passed.add(innermost);
        while (innermost.getCause() != null && passed.add(innermost.getCause())) {
            innermost = innermost.getCause();
        }
        String message = innermost.getMessage();
        return message == null || message.isBlank() ? innermost.getClass().getSimpleName() : message;
    }

    private static Optional<String> errorIn(String text) {
        try {
            String error = JSON.readTree(text).path("error").asString("");
            return error.isEmpty() ? Optional.empty() : Optional.of(error);
        } catch (RuntimeException notJson) {
            return Optional.empty();
        }
    }

    private static String oneLine(String words) {
        return words.replaceAll("\r\n|\r|\n", " ");
    }

    private static String stopped(String modelName, String whatHappened) {
        return "Ollama stopped answering while pulling " + modelName + " (" + oneLine(whatHappened) + "); "
                + NOTHING_SERVED + " Run the same command again once Ollama answers";
    }

    private static String refused(String modelName, int code, String restOfIt) {
        return "Ollama would not pull " + modelName + " (it answered " + code + ", and the rest of its answer "
                + oneLine(restOfIt) + "), so nothing of it was pulled; correct the name and run the same command again";
    }

    private static String stalled(String modelName, long seconds) {
        return "Ollama pulled nothing more of " + modelName + " for " + seconds + " seconds, so Vespera stopped"
                + " waiting; " + NOTHING_SERVED + " Check the network of the machine Ollama runs on and run the same"
                + " command again, or set vespera.models.pull-stalled-at-most longer";
    }

    /** The bytes pulled over the layers named so far, and the counter line written for each twentieth reached. */
    private static final class Pulled {

        private final String label;
        private final Map<String, long[]> layers = new HashMap<>();
        private long written;

        Pulled(String label) {
            this.label = label;
        }

        void layer(String digest, long completed, long total) {
            layers.put(digest, new long[] {completed, total});
            long done = 0;
            long all = 0;
            for (long[] layer : layers.values()) {
                done += layer[0];
                all += layer[1];
            }
            if (all <= 0) {
                return;
            }
            long reached = Math.min(TWENTIETHS, done * TWENTIETHS / all);
            if (reached > written && reached > 0) {
                written = reached;
                LOG.info(
                        "{}: {} of {} ({}%)",
                        label,
                        String.format(Locale.ROOT, "%,d", done / MEGABYTE),
                        String.format(Locale.ROOT, "%,d", all / MEGABYTE),
                        Math.min(100, done * 100 / all));
            }
        }
    }
}
