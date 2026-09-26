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

    /** The tag of the image the {@code Containerfile} is built {@code FROM}. */
    private static String baseTag() throws IOException {
        String from = instructions().stream()
                .filter(instruction -> instruction.startsWith("FROM "))
                .findFirst()
                .orElseThrow();
        return from.substring(from.lastIndexOf(':') + 1).trim();
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
