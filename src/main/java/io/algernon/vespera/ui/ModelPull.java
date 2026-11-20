package io.algernon.vespera.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One download of a model into Ollama for the window (ADR-430 §7): {@code POST /api/pull} with the body {@code
 * {"model": "<name>"}} and nothing else, its answer streamed over the {@link HttpURLConnection} of {@code java.base} on a
 * thread of its own. It tells how many bytes Ollama has reported {@code completed} and {@code total}, each summed over
 * the layers named so far, and ends with {@code success} as done, or lost: on an error Ollama sends, on a status that is
 * not 2xx, on a connection that fails, and on a download that has not moved for the stalled-at-most bound (ADR-329 §6).
 * It sends nothing else to Ollama and deletes nothing. {@link #cancel()} closes the connection and tells nothing more.
 */
final class ModelPull implements DockerControl.Download {

    private final URI ollama;
    private final String model;
    private final Duration stalledAtMost;
    private final Consumer<DockerControl.Downloaded> told;
    private volatile boolean cancelled;
    private volatile HttpURLConnection connection;

    private ModelPull(URI ollama, String model, Duration stalledAtMost, Consumer<DockerControl.Downloaded> told) {
        this.ollama = ollama;
        this.model = model;
        this.stalledAtMost = stalledAtMost;
        this.told = told;
    }

    /** Starts the download on a thread of its own. */
    static ModelPull start(URI ollama, String model, Duration stalledAtMost, Consumer<DockerControl.Downloaded> told) {
        return start(ollama, model, stalledAtMost, told,
                work -> Thread.ofPlatform().daemon().name("the control panel downloads " + model).start(work));
    }

    /** Hands the download to {@code executor}, which runs it; a cancel before it runs sends Ollama no request. */
    static ModelPull start(URI ollama, String model, Duration stalledAtMost, Consumer<DockerControl.Downloaded> told,
            java.util.concurrent.Executor executor) {
        ModelPull pull = new ModelPull(ollama, model, stalledAtMost, told);
        executor.execute(pull::run);
        return pull;
    }

    @Override
    public void cancel() {
        cancelled = true;
        HttpURLConnection open = connection;
        if (open != null) {
            open.disconnect();
        }
    }

    private void run() {
        Map<String, Long> completedByLayer = new HashMap<>();
        Map<String, Long> totalByLayer = new HashMap<>();
        long completed = 0;
        long total = 0;
        try {
            String root = ollama.toString();
            while (root.endsWith("/")) {
                root = root.substring(0, root.length() - 1);
            }
            HttpURLConnection open = (HttpURLConnection) URI.create(root + "/api/pull").toURL().openConnection();
            connection = open;
            if (cancelled) {
                return;
            }
            open.setRequestMethod("POST");
            open.setDoOutput(true);
            open.setConnectTimeout(DockerControl.ASK_MILLIS);
            open.setReadTimeout((int) Math.min(Integer.MAX_VALUE, stalledAtMost.toMillis()));
            open.setRequestProperty("Content-Type", "application/json");
            try (OutputStream body = open.getOutputStream()) {
                body.write(("{\"model\": " + quoted(model) + "}").getBytes(StandardCharsets.UTF_8));
            }
            if (cancelled) {
                return;
            }
            int status = open.getResponseCode();
            if (status / 100 != 2) {
                String words = new String(open.getErrorStream() == null ? new byte[0]
                        : open.getErrorStream().readNBytes(64 * 1024), StandardCharsets.UTF_8).strip().replaceAll("\\s+", " ");
                lost("Ollama would not pull " + model + " (" + status + (words.isEmpty() ? "" : ": " + words)
                        + "), so nothing of it was pulled; correct the name and download it again", completed, total);
                return;
            }
            String lastStatus = null;
            long lastMoved = System.nanoTime();
            try (BufferedReader lines = new BufferedReader(
                    new InputStreamReader(open.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = lines.readLine()) != null) {
                    if (cancelled) {
                        return;
                    }
                    if (line.isBlank()) {
                        continue;
                    }
                    if (!(JsonReader.read(line) instanceof Map<?, ?> said)) {
                        continue;
                    }
                    if (said.get("error") instanceof String error) {
                        lost("Ollama could not pull " + model + " (" + error + "); what was downloaded stays in"
                                + " Ollama, and downloading it again resumes from it", completed, total);
                        return;
                    }
                    String statusWords = said.get("status") instanceof String text ? text : "";
                    boolean moved = !statusWords.equals(lastStatus);
                    lastStatus = statusWords;
                    if (said.get("digest") instanceof String layer) {
                        if (said.get("total") instanceof Number layerTotal) {
                            totalByLayer.put(layer, layerTotal.longValue());
                        }
                        if (said.get("completed") instanceof Number layerCompleted) {
                            long was = completedByLayer.getOrDefault(layer, 0L);
                            if (layerCompleted.longValue() > was) {
                                moved = true;
                            }
                            completedByLayer.put(layer, layerCompleted.longValue());
                        }
                    }
                    long nowCompleted = completedByLayer.values().stream().mapToLong(Long::longValue).sum();
                    long nowTotal = totalByLayer.values().stream().mapToLong(Long::longValue).sum();
                    if (moved) {
                        lastMoved = System.nanoTime();
                    } else if (System.nanoTime() - lastMoved > stalledAtMost.toNanos()) {
                        lost(stalled(), completed, total);
                        return;
                    }
                    if ("success".equals(statusWords)) {
                        long whole = Math.max(nowTotal, nowCompleted);
                        told.accept(new DockerControl.Downloaded(DockerControl.Downloaded.State.DONE, whole, whole, ""));
                        return;
                    }
                    if (nowCompleted != completed || nowTotal != total) {
                        completed = nowCompleted;
                        total = nowTotal;
                        told.accept(new DockerControl.Downloaded(DockerControl.Downloaded.State.DOWNLOADING, completed,
                                total, ""));
                    }
                }
            }
            lost("Ollama stopped answering while pulling " + model + " (the answer ended before the pull did); what was"
                    + " downloaded stays in Ollama, and downloading it again resumes from it", completed, total);
        } catch (SocketTimeoutException silent) {
            lost(stalled(), completed, total);
        } catch (IOException | RuntimeException failed) {
            String why = failed.getMessage() == null || failed.getMessage().isBlank()
                    ? failed.getClass().getSimpleName() : failed.getMessage();
            lost("the connection to Ollama was lost: " + why + "; what was downloaded stays in Ollama, and downloading"
                    + " it again resumes from it", completed, total);
        }
    }

    private String stalled() {
        return "Ollama pulled nothing more of " + model + " for " + stalledAtMost.toSeconds() + " seconds, so Vespera"
                + " stopped waiting; what was downloaded stays in Ollama, and downloading it again resumes from it";
    }

    /** Tells the download lost, unless the window cancelled it, which closed the connection and wants nothing said. */
    private void lost(String reason, long completed, long total) {
        if (!cancelled) {
            told.accept(new DockerControl.Downloaded(DockerControl.Downloaded.State.LOST, completed, total, reason));
        }
    }

    private static String quoted(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char each : text.toCharArray()) {
            switch (each) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (each < 0x20) {
                        out.append(String.format("\\u%04x", (int) each));
                    } else {
                        out.append(each);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
