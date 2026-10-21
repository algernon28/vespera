package io.algernon.controlpanel;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The two read-only questions the launch page asks of the sidecars (ADR-302 §5; ADR-179 §1 to §3): which image Docling
 * runs, from {@code GET /version}, and which models Ollama serves, from {@code GET /api/tags}. Each is one
 * {@link HttpURLConnection} of {@code java.base} with a connect and a read timeout of 2 seconds, its answer read by
 * {@link JsonReader}; nothing is started, stopped or pulled, and a check that cannot be made is a result, never a
 * refusal of anything.
 */
final class SidecarChecks {

    /** The connect and the read timeout, each. */
    static final int TIMEOUT_MILLIS = 2000;

    /** The most of an answer that is read; a longer one is an answer the reader refuses. */
    private static final int LARGEST_ANSWER = 4 * 1024 * 1024;

    /** The values {@code data-check} takes. */
    enum Result {
        NOT_CHECKED("not-checked"), MATCHES("matches"), ANOTHER_IMAGE("another-image"), NOT_ANSWERING("not-answering"),
        SERVES_ALL("serves-all"), MISSING("missing");

        private final String attribute;

        Result(String attribute) {
            this.attribute = attribute;
        }

        String attribute() {
            return attribute;
        }
    }

    /** One check: its result, and the warning it gives where it gives one. */
    record Check(Result result, List<String> warnings) {
        Check(Result result) {
            this(result, List.of());
        }
    }

    /** What the launch page shows of both. */
    record Checks(Check docling, Check ollama) {
    }

    private SidecarChecks() {
    }

    /**
     * Asks both at once.
     *
     * @param modelsNamed the models Ollama is expected to serve, none where nothing is named
     */
    static Checks ask(String doclingBaseUrl, String doclingImage, String ollamaBaseUrl, List<String> modelsNamed) {
        CompletableFuture<Check> docling = CompletableFuture.supplyAsync(() -> docling(doclingBaseUrl, doclingImage));
        CompletableFuture<Check> ollama = CompletableFuture.supplyAsync(() -> ollama(ollamaBaseUrl, modelsNamed));
        return new Checks(docling.join(), ollama.join());
    }

    static Check docling(String baseUrl, String imageNamed) {
        if (baseUrl.isBlank() || imageNamed.isBlank()) {
            return new Check(Result.NOT_CHECKED);
        }
        Object answer;
        try {
            answer = get(baseUrl, "/version");
        } catch (IOException | RuntimeException notAnswering) {
            return new Check(Result.NOT_ANSWERING);
        }
        Object image = answer instanceof Map<?, ?> members ? members.get("vespera-image") : null;
        if (image instanceof String run && run.equals(imageNamed)) {
            return new Check(Result.MATCHES);
        }
        String ran = image instanceof String run ? run : "no image";
        return new Check(Result.ANOTHER_IMAGE, List.of("the Docling sidecar at " + baseUrl + " runs " + ran
                + ", where " + imageNamed + " is named, and the jar stops before it converts anything under another"
                + " image"));
    }

    static Check ollama(String baseUrl, List<String> modelsNamed) {
        if (baseUrl.isBlank() || modelsNamed.isEmpty()) {
            return new Check(Result.NOT_CHECKED);
        }
        Set<String> served = new java.util.HashSet<>();
        try {
            Object answer = get(baseUrl, "/api/tags");
            if (!(answer instanceof Map<?, ?> members) || !(members.get("models") instanceof List<?> models)) {
                return new Check(Result.NOT_ANSWERING);
            }
            for (Object model : models) {
                if (model instanceof Map<?, ?> entries && entries.get("name") instanceof String name) {
                    served.add(name);
                }
            }
        } catch (IOException | RuntimeException notAnswering) {
            return new Check(Result.NOT_ANSWERING);
        }
        List<String> warnings = new ArrayList<>();
        for (String model : modelsNamed) {
            boolean isServed = served.contains(model) || (!model.contains(":") && served.contains(model + ":latest"));
            if (!isServed) {
                warnings.add("the model service at " + baseUrl + " does not serve " + model);
            }
        }
        return new Check(warnings.isEmpty() ? Result.SERVES_ALL : Result.MISSING, warnings);
    }

    /** The JSON the address answers 200 with; an {@link IOException} for any other answer or none in time. */
    private static Object get(String baseUrl, String path) throws IOException {
        URI address = URI.create(baseUrl.strip().replaceAll("/+$", "") + path);
        String scheme = address.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IOException("not an http address");
        }
        URL url = address.toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != 200) {
                throw new IOException("answered " + connection.getResponseCode());
            }
            try (InputStream body = connection.getInputStream()) {
                byte[] bytes = body.readNBytes(LARGEST_ANSWER + 1);
                if (bytes.length > LARGEST_ANSWER) {
                    throw new IOException("an answer too long");
                }
                return JsonReader.read(new String(bytes, StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }
}
