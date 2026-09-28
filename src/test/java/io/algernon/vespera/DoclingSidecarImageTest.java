package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The Docling sidecar's image is named in three places, and the three have to agree (ADR-147).
 *
 * <p>{@code compose.yaml} runs it, {@code TestcontainersConfiguration} tests against it, and
 * {@code application.yaml}'s {@code vespera.docling.image} is what the extractor identity carries. An
 * identity naming an image the sidecar is not running is a cache key that claims something untrue
 * about every row under it, which is the failure the image was put in the key to prevent.
 *
 * <p>The image replaces the PDF parser its base ships with an exact release, and its tag names that
 * release beside the base (ADR-163): the parser the base ships crashed the sidecar and lost pages when
 * several pages of one PDF were decoded at once in a fresh process.
 *
 * <p>The same {@code Containerfile} also builds on docling-serve's CUDA 12.8 base, where
 * {@code compose.gpu.yaml} asks for it, under a tag of its own (ADR-170). The GPU build converts some PDFs
 * slightly differently and reports the same versions, so only its name keeps its conversions apart from
 * the processor build's. The three places above go on naming the processor build, which is the default
 * everywhere.
 *
 * <p>Read from the files themselves, with no Docker: each fact here is a line an operator can edit.
 */
@Epic("Extraction")
@Feature("The Docling sidecar")
@Link(name = "ADR-147", url = Adr.THE_DOCLING_SIDECAR_IS_A_DERIVED_IMAGE, type = "adr")
class DoclingSidecarImageTest {

    /** The compose file at the repository root, which is where Maven runs the tests from. */
    private static final Path COMPOSE_FILE = Path.of("compose.yaml");

    /** The service the sidecar runs as in it. */
    private static final String SERVICE = "docling-serve";

    /** The file the sidecar's image is built from, relative to the same root. */
    private static final Path CONTAINERFILE = Path.of("docker/docling-serve/Containerfile");

    /** The PDF parser's package name, as pip knows it. */
    private static final String PARSER = "docling-parse";

    /** An exact pin of that package: {@code ==} and a release, nothing looser. */
    private static final Pattern PINNED_PARSER = Pattern.compile("\\b" + PARSER + "==([0-9][0-9A-Za-z.]*)");

    /**
     * The parser release measured on 2026-09-26: no crash and no lost page in 60 conversions from a cold
     * process, against 9 crashes in 30 and pages lost in 30 of 60 on 7.16.0, which the base image ships.
     */
    private static final String MEASURED_PARSER = "7.17.0";

    /** The repository's own name for the image; the tag after the colon is what says which one. */
    private static final String IMAGE_NAME = "vespera/docling-serve-cpu-libreoffice";

    /** The image with that parser, on the {@code docling-serve} v1.32.0 base, written out. */
    private static final String THE_IMAGE = IMAGE_NAME + ":v1.32.0-docling-parse-" + MEASURED_PARSER;

    /** The file that gives the sidecar the GPU, and builds it on a GPU base under its own tag (ADR-170). */
    private static final Path GPU_FILE = Path.of("compose.gpu.yaml");

    /** The build argument the {@code Containerfile} takes its base from. */
    private static final String BASE_ARG = "DOCLING_SERVE_BASE";

    /** The base every build uses unless told otherwise: docling-serve's CPU image, at the pinned release. */
    private static final String CPU_BASE = "quay.io/docling-project/docling-serve-cpu:v1.32.0";

    /**
     * docling-serve's CUDA 12.8 image, without its tag: the RTX 50 series needs CUDA 12.8 or later, and
     * {@code cu126} has no v1.32.0 tag (ADR-170).
     */
    private static final String GPU_BASE_REPOSITORY = "quay.io/docling-project/docling-serve-cu128";

    @Test
    @Story("One image, named the same everywhere")
    @DisplayName("The image the sidecar runs, the image tests run, and the image the extraction cache names are one")
    void namesOneImageEverywhere() throws IOException {
        String composed = (String) service().get("image");

        claim(
                "the image the tests start is the image compose.yaml runs",
                () -> assertThat(TestcontainersConfiguration.DOCLING_SERVE_IMAGE).isEqualTo(composed));
        claim(
                "and it is the image the extractor identity names, so every cache row says truly which"
                        + " image made it",
                () -> assertThat(configuredImage()).isEqualTo(composed));
    }

    @Test
    @Story("The image with LibreOffice is built here and runs locked down")
    @DisplayName("compose.yaml builds the image from the repository and runs it with an init process and no privileges")
    void buildsTheImageAndRunsItLockedDown() throws IOException {
        Map<String, Object> service = service();
        @SuppressWarnings("unchecked")
        Map<String, Object> build = (Map<String, Object>) service.get("build");

        claim(
                "the image is built from the repository's own Containerfile, because no published image"
                        + " carries LibreOffice",
                () -> assertThat(Path.of((String) build.get("context")).resolve((String) build.get("dockerfile")))
                        .isRegularFile());
        claim(
                "it runs with an init process, without which every conversion leaves a dead soffice behind",
                () -> assertThat(service.get("init")).isEqualTo(true));
        claim(
                "every Linux capability is dropped, since converting a file needs none of them",
                () -> assertThat(service.get("cap_drop")).isEqualTo(List.of("ALL")));
        claim(
                "and no process in it can gain privileges it did not start with",
                () -> assertThat(service.get("security_opt")).isEqualTo(List.of("no-new-privileges:true")));
    }

    @Test
    @Story("The PDF parser is the release that loads its fonts safely")
    @DisplayName("The image installs exactly the PDF parser release measured not to crash, and checks it fits")
    @Link(name = "ADR-163", url = Adr.THE_DOCLING_SIDECAR_PINS_DOCLING_PARSE, type = "adr")
    void pinsTheParserThatPublishesItsFontsWhole() throws IOException {
        List<String> installs = instructionsInstallingTheParser();

        claim(
                "the Containerfile installs the PDF parser in exactly one instruction, so there is one"
                        + " place its version is decided",
                () -> assertThat(installs).hasSize(1));
        String install = installs.getFirst();
        claim(
                "that instruction names one exact release with ==, so a rebuild can never pick up a"
                        + " different parser, and conversions, unannounced",
                () -> assertThat(pinnedParserVersion()).isNotNull());
        claim(
                "the release is " + MEASURED_PARSER + ", the one measured with no crash and no lost page in"
                        + " 60 conversions from a cold start, where the release the base image ships"
                        + " crashed in 9 of 30 and lost pages in 30 of 60",
                () -> assertThat(pinnedParserVersion()).isEqualTo(MEASURED_PARSER));
        claim(
                "it replaces that one package and nothing else, so the rest of the converter stays the one"
                        + " already measured",
                () -> assertThat(install).contains("--no-deps"));
        claim(
                "and the same instruction checks that the installed packages still agree, so a mismatch"
                        + " fails the build rather than the first conversion",
                () -> assertThat(install).contains("pip check"));
    }

    @Test
    @Story("One image, named the same everywhere")
    @DisplayName("The image is tagged for the base it is built on and the PDF parser it carries")
    @Link(name = "ADR-163", url = Adr.THE_DOCLING_SIDECAR_PINS_DOCLING_PARSE, type = "adr")
    void tagsTheImageForItsBaseAndItsParser() throws IOException {
        String composed = (String) service().get("image");

        claim(
                "the tag compose.yaml gives the image is the base image's own tag, then the PDF parser"
                        + " release it carries, so a different parser can never run under the same name",
                () -> assertThat(composed).isEqualTo(
                        IMAGE_NAME + ":" + baseTag() + "-docling-parse-" + pinnedParserVersion()));
        claim(
                "which, written out, is " + THE_IMAGE + " -- the name recorded beside every conversion"
                        + " this image makes",
                () -> assertThat(composed).isEqualTo(THE_IMAGE));
    }

    @Test
    @Story("One Containerfile, built for the processor or for the GPU")
    @DisplayName("The Containerfile takes its base as a build argument, and the processor base is the default")
    @Link(name = "ADR-170", url = Adr.DOCLING_RUNS_ON_THE_GPU_WITH_AN_IMAGE_TAG_OF_ITS_OWN, type = "adr")
    void buildsFromABaseArgumentThatDefaultsToTheCpuBase() throws IOException {
        List<String> instructions = instructions();

        claim(
                "the Containerfile is built FROM the base named by the " + BASE_ARG + " build argument, so"
                        + " the processor build and the GPU build share every layer Vespera adds",
                () -> assertThat(instructions).contains("FROM ${" + BASE_ARG + "}"));
        claim(
                "and that argument defaults to " + CPU_BASE + ", so every build that passes no argument,"
                        + " which is every build from compose.yaml alone, is the processor build it was before",
                () -> assertThat(instructions).contains("ARG " + BASE_ARG + "=" + CPU_BASE));
    }

    @Test
    @Story("One Containerfile, built for the processor or for the GPU")
    @DisplayName("The GPU build has a tag of its own, which names the same base release and PDF parser")
    @Link(name = "ADR-170", url = Adr.DOCLING_RUNS_ON_THE_GPU_WITH_AN_IMAGE_TAG_OF_ITS_OWN, type = "adr")
    void tagsTheGpuImageApartWithTheSameSuffix() throws IOException {
        String cpuImage = (String) service().get("image");
        String gpuImage = (String) gpuService().get("image");
        String suffix = ":" + baseTag() + "-docling-parse-" + pinnedParserVersion();

        claim(
                "compose.gpu.yaml names the GPU build by an image name of its own",
                () -> assertThat(gpuImage).isNotBlank());
        claim(
                "which is not the processor build's, because the two convert some PDFs differently and"
                        + " the sidecar cannot say which one it is, so one name for both would record one"
                        + " extractor's conversions as the other's",
                () -> assertThat(gpuImage).isNotEqualTo(cpuImage));
        claim(
                "its tag is the base release, then the PDF parser release the Containerfile pins, as the"
                        + " processor build's is, so a parser bump cannot update one tag and forget the other",
                () -> {
                    assertThat(gpuImage).endsWith(suffix);
                    assertThat(cpuImage).endsWith(suffix);
                });
        claim(
                "which, written out, ends both tags with :v1.32.0-docling-parse-" + MEASURED_PARSER,
                () -> assertThat(gpuImage).endsWith(":v1.32.0-docling-parse-" + MEASURED_PARSER));
    }

    @Test
    @Story("One Containerfile, built for the processor or for the GPU")
    @DisplayName("The GPU build is on docling-serve's CUDA 12.8 base, at the same release as the processor base")
    @Link(name = "ADR-170", url = Adr.DOCLING_RUNS_ON_THE_GPU_WITH_AN_IMAGE_TAG_OF_ITS_OWN, type = "adr")
    void buildsTheGpuImageOnCu128AtTheSameRelease() throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> build = (Map<String, Object>) gpuService().get("build");
        @SuppressWarnings("unchecked")
        Map<String, Object> args = build == null ? null : (Map<String, Object>) build.get("args");

        claim(
                "compose.gpu.yaml passes the Containerfile a base of its own, through " + BASE_ARG,
                () -> assertThat(args).isNotNull().containsKey(BASE_ARG));
        String gpuBase = (String) args.get(BASE_ARG);
        claim(
                "that base is docling-serve's CUDA 12.8 image, the lowest CUDA the RTX 50 series runs on",
                () -> assertThat(gpuBase).startsWith(GPU_BASE_REPOSITORY + ":"));
        claim(
                "at the same docling-serve release as the processor base the Containerfile defaults to, so"
                        + " the two builds differ by the device and by nothing anyone chose",
                () -> assertThat(gpuBase).isEqualTo(GPU_BASE_REPOSITORY + ":" + baseTag()));
    }

    /** Every {@code RUN} instruction of the {@code Containerfile} that pins the parser. */
    private static List<String> instructionsInstallingTheParser() throws IOException {
        return instructions().stream()
                .filter(instruction -> instruction.startsWith("RUN ") && instruction.contains(PARSER + "=="))
                .toList();
    }

    /** The release after {@code docling-parse==} in the {@code Containerfile}, or {@code null} if nothing pins one. */
    private static String pinnedParserVersion() throws IOException {
        for (String instruction : instructionsInstallingTheParser()) {
            Matcher pin = PINNED_PARSER.matcher(instruction);
            if (pin.find()) {
                return pin.group(1);
            }
        }
        return null;
    }

    /**
     * The tag of the image the {@code Containerfile} is built {@code FROM} when no build argument is
     * passed: the {@code FROM} line's own image, or, where it names an {@code ARG}, that argument's default.
     */
    private static String baseTag() throws IOException {
        List<String> instructions = instructions();
        String from = instructions.stream()
                .filter(instruction -> instruction.startsWith("FROM "))
                .findFirst()
                .orElseThrow()
                .substring("FROM ".length())
                .trim();
        Matcher argument = Pattern.compile("^\\$\\{?(\\w+)}?$").matcher(from);
        if (argument.matches()) {
            String declared = "ARG " + argument.group(1) + "=";
            from = instructions.stream()
                    .filter(instruction -> instruction.startsWith(declared))
                    .findFirst()
                    .orElseThrow()
                    .substring(declared.length())
                    .trim();
        }
        return from.substring(from.lastIndexOf(':') + 1);
    }

    /** The {@code Containerfile}'s instructions, comments dropped and each continued line joined to the next. */
    private static List<String> instructions() throws IOException {
        List<String> instructions = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : Files.readAllLines(CONTAINERFILE)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.endsWith("\\")) {
                current.append(trimmed, 0, trimmed.length() - 1).append(' ');
                continue;
            }
            instructions.add(current.append(trimmed).toString());
            current.setLength(0);
        }
        return instructions;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> service() throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(COMPOSE_FILE));
        return (Map<String, Object>) ((Map<String, Object>) compose.get("services")).get(SERVICE);
    }

    /** The sidecar's service in {@code compose.gpu.yaml}, or an empty map where the file gives it nothing. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> gpuService() throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(GPU_FILE));
        Map<String, Object> service =
                (Map<String, Object>) ((Map<String, Object>) compose.get("services")).get(SERVICE);
        return service == null ? Map.of() : service;
    }

    @SuppressWarnings("unchecked")
    private static String configuredImage() throws IOException {
        try (InputStream in = DoclingSidecarImageTest.class.getResourceAsStream("/application.yaml")) {
            Map<String, Object> application = new Yaml().load(in);
            Map<String, Object> docling =
                    (Map<String, Object>) ((Map<String, Object>) application.get("vespera")).get("docling");
            return (String) docling.get("image");
        }
    }
}
