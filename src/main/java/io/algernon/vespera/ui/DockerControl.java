package io.algernon.vespera.ui;

import io.algernon.vespera.engine.pipeline.DockerEngine;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * What the window asks of Docker (ADR-390 §4, ADR-430 §12): check the daemon, the containers and the models and start
 * nothing, start Docker and the containers on <i>Start Docker</i>, download a model Ollama has not, stop the containers
 * as it closes, and shut down the containers and the engine on its button. A start, a shut-down and a stop each answer a
 * {@link Said}: whether it went through, and its line. {@link #inProcess} is the window's, over {@link DockerEngine},
 * the one class of the jar the control panel names; {@link #NONE} runs nothing.
 */
interface DockerControl {

    /** What a call came to: whether it went through, and the one line it said (empty where it went through with nothing to say). */
    record Said(boolean done, String line) {
    }

    /** The names of the two models in force, which the check asks Ollama for (ADR-430 §3); blank where no source names one. */
    record Models(String embedding, String generative) {
    }

    /** A download the window started, which it may stop (ADR-430 §7). */
    interface Download {

        /** Closes the connection to Ollama; what was downloaded stays in Ollama. */
        void cancel();
    }

    /**
     * What a download tells as it goes: its state, the bytes Ollama has reported {@code completed} and {@code total}
     * over the layers named so far, and the reason where its connection was {@link State#LOST}.
     */
    record Downloaded(State state, long completed, long total, String reason) {

        /** Where a download is. */
        enum State { DOWNLOADING, DONE, LOST }
    }

    /**
     * <i>Start Docker</i>'s start (ADR-430 §5): sees to {@code .wslconfig} on Windows, then starts the engine and brings
     * up and waits for the containers on this machine, for the addresses {@code values} hold.
     */
    default Said start(LaunchValues values) {
        return start(values, line -> {
        });
    }

    /**
     * As {@link #start(LaunchValues)}, telling {@code told} each line the start logs, as it is logged, on the thread that
     * runs it (ADR-437 §3).
     */
    Said start(LaunchValues values, Consumer<String> told);

    /** Checks the daemon, the two containers and the two models, for the addresses {@code values} hold, and starts nothing (ADR-430 §3). */
    DockerCheck check(LaunchValues values, Models models);

    /**
     * Downloads {@code model} into the Ollama {@code values} names, on a thread of its own, telling {@code told} as it
     * goes, on that thread; ends with a {@link Downloaded.State#DONE} or a {@link Downloaded.State#LOST}, or with nothing
     * where it is cancelled (ADR-430 §7). {@code stalledAtMost} is how long it may stand still, as Command options holds it
     * when the download begins; no source is read for it here.
     */
    Download download(LaunchValues values, String model, Duration stalledAtMost, Consumer<Downloaded> told);

    /** Stops the containers and then the engine. */
    default Said shutDown() {
        return shutDown(line -> {
        });
    }

    /** As {@link #shutDown()}, telling {@code told} each line of the two stops and what each printed (ADR-437 §3). */
    Said shutDown(Consumer<String> told);

    /** Stops the containers alone, where one answers on this machine, for the addresses {@code values} hold. */
    Said stopTheContainers(LaunchValues values);

    /**
     * A start refused because the graphics card cannot be used (ADR-444 §5, §10): whether the toolkit is offered, the
     * line's first sentence, the install's commands as the confirmation lists them, where they run, and whether the
     * install asks for a password.
     */
    record CardRefused(boolean offersTheToolkit, String why, List<String> commands, String where,
            boolean asksForAPassword) {
    }

    /** What the check of the graphics card found: whether Docker lists the nvidia runtime, empty where unknown, and the warnings (ADR-444 §5). */
    record CardCheck(Optional<Boolean> runtimeListed, List<String> warnings) {
    }

    /** What a start's refusal says of the graphics card, empty where it is no refusal for the card (ADR-444 §5). */
    Optional<CardRefused> cardRefused(String refusal);

    /**
     * Installs the NVIDIA Container Toolkit (ADR-444 §6, §8), telling {@code told} each line it logs. {@code sudoPassword}
     * is handed to the install alone and cleared by it; null where none is asked.
     */
    Said installTheNvidiaToolkit(char[] sudoPassword, Consumer<String> told);

    /** Asks Docker for its nvidia runtime and the cards' warnings, for the addresses {@code values} hold, and starts nothing (ADR-444 §5). */
    CardCheck graphicsCard(LaunchValues values);

    /** Runs nothing and answers done with no line, every row of a check OK and every download done: what a state built with no Docker holds. */
    DockerControl NONE = new DockerControl() {
        @Override
        public Said start(LaunchValues values, Consumer<String> told) {
            return new Said(true, "");
        }

        @Override
        public Optional<CardRefused> cardRefused(String refusal) {
            return Optional.empty();
        }

        @Override
        public Said installTheNvidiaToolkit(char[] sudoPassword, Consumer<String> told) {
            return new Said(true, "");
        }

        @Override
        public CardCheck graphicsCard(LaunchValues values) {
            return new CardCheck(Optional.empty(), List.of());
        }

        @Override
        public DockerCheck check(LaunchValues values, Models models) {
            return new DockerCheck(DockerCheck.Mark.OK,
                    new DockerCheck.Service(DockerCheck.Mark.OK, DockerCheck.Where.THIS_MACHINE, DEFAULT_DOCLING),
                    new DockerCheck.Service(DockerCheck.Mark.OK, DockerCheck.Where.THIS_MACHINE, DEFAULT_OLLAMA),
                    DockerCheck.Mark.OK, DockerCheck.Mark.OK, "", List.of());
        }

        @Override
        public Download download(LaunchValues values, String model, Duration stalledAtMost, Consumer<Downloaded> told) {
            told.accept(new Downloaded(Downloaded.State.DONE, 0, 0, ""));
            return () -> {
            };
        }

        @Override
        public Said shutDown(Consumer<String> told) {
            return new Said(true, "");
        }

        @Override
        public Said stopTheContainers(LaunchValues values) {
            return new Said(true, "");
        }
    };

    /**
     * Where the engine's code takes its settings from (ADR-390 §4).
     *
     * @param wslDistribution the WSL distribution, blank for WSL's default
     * @param waitAtMost the bound of every start and stop command
     * @param lockFolder the folder the corpus-root locks lie in, where {@code containers.lock} lies too
     * @param profileFolder the Windows profile folder {@code .wslconfig} lies in (ADR-421 §6)
     * @param composeFolder the folder the compose files are read from, null for the folder beside the jar (ADR-430 §12)
     */
    record Settings(String wslDistribution, Duration waitAtMost, Path lockFolder, Path profileFolder,
            Path composeFolder, StartingValues.Sources sources) {
    }

    /**
     * The jar's own default addresses, which a blank one stands for where the environment, {@code .env} and the jar's
     * {@code application.yaml} name none either (ADR-390 §4, §7).
     */
    String DEFAULT_DOCLING = "http://localhost:5001";

    String DEFAULT_OLLAMA = "http://localhost:11434";

    String DOCLING_PROPERTY = "vespera.docling.base-url";

    String OLLAMA_PROPERTY = "spring.ai.ollama.base-url";

    /** The connect and the read timeout of each question of the check, as the launch page's are (ADR-430 §3). */
    int ASK_MILLIS = 2000;

    /** The control: {@link DockerEngine}, called in this process. */
    static DockerControl inProcess(Settings settings) {
        DockerEngine engine = DockerEngine.of(
                settings.wslDistribution(), settings.waitAtMost(), settings.lockFolder(), settings.profileFolder(),
                settings.composeFolder());
        return inProcess(settings, engine, engine::engineAnswers);
    }

    /** The control with the daemon's answer given, which a test of the check supplies in place of the engine's. */
    static DockerControl inProcess(Settings settings,
            java.util.function.BiFunction<URI, URI, Optional<Boolean>> daemonAnswers) {
        return inProcess(settings, DockerEngine.of(
                settings.wslDistribution(), settings.waitAtMost(), settings.lockFolder(), settings.profileFolder(),
                settings.composeFolder()), daemonAnswers);
    }

    private static DockerControl inProcess(Settings settings, DockerEngine engine,
            java.util.function.BiFunction<URI, URI, Optional<Boolean>> daemonAnswers) {
        return new DockerControl() {
            @Override
            public Said start(LaunchValues values, Consumer<String> told) {
                URI docling = address(values.doclingBaseUrl(), DOCLING_PROPERTY, DEFAULT_DOCLING);
                URI ollama = address(values.ollamaBaseUrl(), OLLAMA_PROPERTY, DEFAULT_OLLAMA);
                Optional<String> wslLine = engine.keepTheWslDistributionUp(docling, ollama, told);
                String image = values.doclingImage() == null || values.doclingImage().isBlank()
                        ? StartingValues.of(StartingValues.DOCLING_IMAGE, settings.sources()).value()
                        : values.doclingImage().strip();
                Optional<String> refusal = engine.startTheContainers(docling, ollama, image, told);
                if (refusal.isPresent()) {
                    return new Said(false, refusal.get());
                }
                return new Said(true, wslLine.orElse(""));
            }

            @Override
            public DockerCheck check(LaunchValues values, Models models) {
                URI docling = address(values.doclingBaseUrl(), DOCLING_PROPERTY, DEFAULT_DOCLING);
                URI ollama = address(values.ollamaBaseUrl(), OLLAMA_PROPERTY, DEFAULT_OLLAMA);
                DockerCheck.Mark daemon = daemonAnswers.apply(docling, ollama)
                        .map(answers -> answers ? DockerCheck.Mark.OK : DockerCheck.Mark.KO)
                        .orElse(DockerCheck.Mark.NOT_ASKED);
                DockerCheck.Service doclingAnswers = service(docling, "/health");
                DockerCheck.Service ollamaAnswers = service(ollama, "/api/version");
                // ADR-440 §6: the image is asked of /version only where Docling answered /health
                String image = doclingAnswers.mark() == DockerCheck.Mark.OK ? imageDoclingRuns(docling) : "";
                if (ollamaAnswers.mark() != DockerCheck.Mark.OK) {
                    return new DockerCheck(daemon, doclingAnswers, ollamaAnswers, DockerCheck.Mark.NOT_ASKED,
                            DockerCheck.Mark.NOT_ASKED, image, List.of());
                }
                List<String> listed = namesOllamaLists(ollama);
                return new DockerCheck(daemon, doclingAnswers, ollamaAnswers, markOf(models.embedding(), listed),
                        markOf(models.generative(), listed), image, listed);
            }

            @Override
            public Download download(LaunchValues values, String model, Duration stalledAtMost, Consumer<Downloaded> told) {
                URI ollama = address(values.ollamaBaseUrl(), OLLAMA_PROPERTY, DEFAULT_OLLAMA);
                return ModelPull.start(ollama, model, stalledAtMost, told);
            }

            @Override
            public Said shutDown(Consumer<String> told) {
                String line = engine.shutDown(told);
                return new Said(DockerEngine.SHUT_DOWN_DONE.equals(line), line);
            }

            @Override
            public Said stopTheContainers(LaunchValues values) {
                return said(engine.stopTheContainers(address(values.doclingBaseUrl(), DOCLING_PROPERTY, DEFAULT_DOCLING),
                        address(values.ollamaBaseUrl(), OLLAMA_PROPERTY, DEFAULT_OLLAMA)));
            }

            @Override
            public Optional<CardRefused> cardRefused(String refusal) {
                Optional<Boolean> offers = DockerEngine.offersTheNvidiaToolkit(refusal);
                if (offers.isEmpty()) {
                    return Optional.empty();
                }
                int firstSentence = refusal.indexOf(". ");
                String why = firstSentence < 0 ? refusal : refusal.substring(0, firstSentence + 1);
                boolean toolkit = offers.get();
                return Optional.of(new CardRefused(toolkit, why,
                        toolkit ? engine.nvidiaToolkitCommands() : List.of(),
                        toolkit ? engine.whereTheToolkitIsInstalled() : "",
                        toolkit && engine.installAsksForAPassword()));
            }

            @Override
            public Said installTheNvidiaToolkit(char[] sudoPassword, Consumer<String> told) {
                String line = engine.installTheNvidiaToolkit(sudoPassword, told);
                return new Said(DockerEngine.NVIDIA_TOOLKIT_INSTALLED.equals(line), line);
            }

            @Override
            public CardCheck graphicsCard(LaunchValues values) {
                URI docling = address(values.doclingBaseUrl(), DOCLING_PROPERTY, DEFAULT_DOCLING);
                URI ollama = address(values.ollamaBaseUrl(), OLLAMA_PROPERTY, DEFAULT_OLLAMA);
                try {
                    Optional<Boolean> listed = engine.nvidiaRuntimeListed(docling, ollama);
                    List<String> warnings = listed.orElse(false) ? engine.graphicsCardWarnings(docling, ollama) : List.of();
                    return new CardCheck(listed, warnings);
                } catch (RuntimeException unasked) {
                    return new CardCheck(Optional.empty(), List.of());
                }
            }

            /** One of Docling and Ollama: where its host resolves to and, where it does, whether {@code path} answers 2xx within 2 s (ADR-430 §3). */
            private DockerCheck.Service service(URI base, String path) {
                Optional<Boolean> here = DockerEngine.isOnThisMachine(base);
                if (here.isEmpty()) {
                    return new DockerCheck.Service(DockerCheck.Mark.KO, DockerCheck.Where.NOT_RESOLVED, base.toString());
                }
                return new DockerCheck.Service(answersTwoXx(base, path) ? DockerCheck.Mark.OK : DockerCheck.Mark.KO,
                        here.get() ? DockerCheck.Where.THIS_MACHINE : DockerCheck.Where.ANOTHER_MACHINE,
                        base.toString());
            }

            private static Said said(Optional<String> refusal) {
                return new Said(refusal.isEmpty(), refusal.orElse(""));
            }

            /** The held address, or where it is blank the one the window's fields and checks start from (ADR-390 §4). */
            private URI address(String held, String property, String blank) {
                String named = held == null || held.isBlank() ? StartingValues.of(property, settings.sources()).value() : held;
                try {
                    return URI.create(named == null || named.isBlank() ? blank : named.strip());
                } catch (IllegalArgumentException unreadable) {
                    return URI.create(blank);
                }
            }
        };
    }

    /** Whether {@code path} at {@code base} answers with a 2xx status within {@link #ASK_MILLIS}; a failure is no answer. */
    private static boolean answersTwoXx(URI base, String path) {
        try {
            HttpURLConnection connection = open(base, path);
            try {
                int status = connection.getResponseCode();
                return status >= 200 && status < 300;
            } finally {
                connection.disconnect();
            }
        } catch (IOException | RuntimeException noAnswer) {
            return false;
        }
    }

    /** The string member {@code vespera-image} of Docling's {@code GET /version} (ADR-440 §6); empty where there is none or no 2xx answer. */
    private static String imageDoclingRuns(URI docling) {
        try {
            HttpURLConnection connection = open(docling, "/version");
            try {
                if (connection.getResponseCode() / 100 != 2) {
                    return "";
                }
                String body = new String(connection.getInputStream().readNBytes(4 * 1024 * 1024),
                        StandardCharsets.UTF_8);
                return JsonReader.read(body) instanceof Map<?, ?> answer && answer.get("vespera-image") instanceof String image
                        ? image : "";
            } finally {
                connection.disconnect();
            }
        } catch (IOException | RuntimeException unreadable) {
            return "";
        }
    }

    /** The names Ollama lists under {@code /api/tags}, none where the list cannot be read. */
    private static List<String> namesOllamaLists(URI ollama) {
        try {
            HttpURLConnection connection = open(ollama, "/api/tags");
            try {
                if (connection.getResponseCode() / 100 != 2) {
                    return List.of();
                }
                String body = new String(connection.getInputStream().readNBytes(4 * 1024 * 1024),
                        StandardCharsets.UTF_8);
                if (!(JsonReader.read(body) instanceof Map<?, ?> answer)
                        || !(answer.get("models") instanceof List<?> listed)) {
                    return List.of();
                }
                return listed.stream().filter(Map.class::isInstance).map(each -> ((Map<?, ?>) each).get("name"))
                        .filter(String.class::isInstance).map(String.class::cast).toList();
            } finally {
                connection.disconnect();
            }
        } catch (IOException | RuntimeException unreadable) {
            return List.of();
        }
    }

    private static HttpURLConnection open(URI base, String path) throws IOException {
        String root = base.toString();
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        HttpURLConnection connection = (HttpURLConnection) URI.create(root + path).toURL().openConnection();
        connection.setConnectTimeout(ASK_MILLIS);
        connection.setReadTimeout(ASK_MILLIS);
        return connection;
    }

    /** A model's mark: OK where Ollama lists its name, or the name with {@code :latest} where it has no tag; not asked where none is named. */
    private static DockerCheck.Mark markOf(String name, List<String> listed) {
        if (name == null || name.isBlank()) {
            return DockerCheck.Mark.NOT_ASKED;
        }
        String named = name.strip();
        boolean tagged = named.substring(named.lastIndexOf('/') + 1).contains(":");
        String wanted = tagged ? named : named + ":latest";
        return listed.stream().anyMatch(each -> each.equalsIgnoreCase(named) || each.equalsIgnoreCase(wanted))
                ? DockerCheck.Mark.OK
                : DockerCheck.Mark.KO;
    }
}
