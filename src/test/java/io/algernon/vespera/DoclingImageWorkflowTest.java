package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
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
 * The two Docling images are built once, by a workflow of this repository, pushed to GitHub's container
 * registry under the repository owner, and pulled by digest everywhere they run (ADR-349).
 *
 * <p>A build of the {@code Containerfile} installs LibreOffice from repositories that move, so two builds under one
 * tag can be two images that convert differently and report the same versions (ADR-147). The image is therefore
 * built once, and the tag it is pushed under is never pushed over: what holds that is the workflow's shape, which is
 * what this test reads. The digests it pushes are pinned by a later change, which {@link DoclingSidecarImageTest}
 * holds once it is made.
 *
 * <p>Read from the files themselves, with no Docker and no network: the workflow with SnakeYAML, and the
 * {@code Containerfile} through {@link DoclingSidecarImageTest}'s own reading of it.
 */
@Epic("Extraction")
@Feature("The Docling sidecar")
@Link(name = "ADR-349", url = Adr.THE_DOCLING_IMAGES_ARE_BUILT_ONCE_AND_PULLED_BY_DIGEST, type = "adr")
class DoclingImageWorkflowTest {

    /** The workflow, relative to the repository root Maven runs the tests in. */
    private static final Path WORKFLOW = Path.of(".github/workflows/docling-image.yml");

    /** Where the images are pushed: GitHub's container registry, under the repository's owner. */
    private static final String NAMESPACE = "ghcr.io/algernon28/";

    /** The processor build's package. */
    private static final String CPU_IMAGE = NAMESPACE + "vespera-docling-serve-cpu-libreoffice";

    /** The CUDA 12.8 build's package. */
    private static final String GPU_IMAGE = NAMESPACE + "vespera-docling-serve-cu128-libreoffice";

    /**
     * The revision of Vespera's own layers the first pushed images take: revision 2 is the image each machine built
     * for itself, and an image built on another day from moving repositories is not that image.
     */
    private static final String REVISION = "-r3";

    /** The one job, which builds both images side by side. */
    private static final String JOB = "image";

    /** GitHub's hosted Linux runner. */
    private static final String RUNNER = "ubuntu-latest";

    /**
     * Two hours: the longer entry pulls six gigabytes of CUDA base, installs LibreOffice, and pushes all of it, and a
     * build that hangs is stopped by this rather than by GitHub's six-hour default.
     */
    private static final int TIMEOUT_MINUTES = 120;

    /**
     * Gigabytes the processor build needs free: about 8.6 of image and 2.2 of compressed layers while it pulls, and a
     * margin.
     */
    private static final int CPU_NEEDS_GB = 12;

    /**
     * Gigabytes the CUDA build needs free: up to 21 of base on disk, estimated from its 6 compressed, 1 of
     * LibreOffice and those 6 while it pulls, and a margin. More than the runner's documented 14, so this entry frees
     * the runner's disk first.
     */
    private static final int GPU_NEEDS_GB = 30;

    /** The name each image is built under, which it reports, set from the matrix. */
    private static final String REFERENCE = "REFERENCE";

    /** The base each image is built on, set from the matrix. */
    private static final String BASE = "BASE";

    /** The gigabytes the build needs free, set from the matrix. */
    private static final String NEEDS_GB = "NEEDS_GB";

    private static final String CHECKOUT = "Check out the repository";
    private static final String LOGIN = "Log in to ghcr.io";
    private static final String CHECK = "The tag is not in the registry yet";
    private static final String FREE = "Free the runner's disk";
    private static final String ROOM = "The disk has room for the build";
    private static final String BUILD = "Build the image";
    private static final String PUSH = "Push the image";
    private static final String DIGEST = "The digest the registry holds";

    /** The workflow's eight steps, in the order they run. */
    private static final List<String> STEPS = List.of(CHECKOUT, LOGIN, CHECK, FREE, ROOM, BUILD, PUSH, DIGEST);

    /** A shell variable as a run line names it, bare or braced, quoted or not. */
    private static String variable(String name) {
        return "\"?\\$\\{?" + name + "}?\"?";
    }

    @Test
    @Story("The images are built once and pushed where they are pulled from")
    @DisplayName("The images are built when asked, or when what goes into them changes on the main branch, and at no other time")
    void startsOnADispatchOrOnAChangeToTheImageOnMain() throws IOException {
        Map<Object, Object> triggers = map(triggers());
        Map<Object, Object> push = map(triggers.get("push"));

        claim(
                "the workflow starts when someone asks for it or when a commit lands, and on nothing else: never for a"
                        + " pull request, whose build would push nothing",
                () -> assertThat(triggers.keySet()).containsExactlyInAnyOrder("workflow_dispatch", "push"));
        claim(
                "a commit starts it only on the main branch, and only where it names nothing but a branch and a path",
                () -> assertThat(push.keySet()).containsExactlyInAnyOrder("branches", "paths"));
        claim(
                "the branch is main alone",
                () -> assertThat(push.get("branches")).isEqualTo(List.of("main")));
        claim(
                "and the commit must change what the images are built from, the folder with the Containerfile, so"
                        + " a change to anything else pushes nothing",
                () -> assertThat(push.get("paths")).isEqualTo(List.of("docker/docling-serve/**")));
    }

    @Test
    @Story("The images are built once and pushed where they are pulled from")
    @DisplayName("The workflow may read the repository and write its packages, and nothing else")
    void holdsNoPermissionButReadingTheRepositoryAndWritingItsPackages() throws IOException {
        Map<String, Object> checkout = step(CHECKOUT);
        Map<String, Object> login = step(LOGIN);
        List<String> secrets = new ArrayList<>();
        Matcher secret = Pattern.compile("secrets\\.(\\w+)").matcher(text());
        while (secret.find()) {
            secrets.add(secret.group(1));
        }

        claim(
                "the workflow's token may read the repository and write its packages, and nothing more",
                () -> assertThat(workflow().get("permissions"))
                        .isEqualTo(Map.of("contents", "read", "packages", "write")));
        claim(
                "no job widens that with permissions of its own",
                () -> assertThat(job()).doesNotContainKey("permissions"));
        claim(
                "the only secret it names is the token GitHub gives each workflow, so no long-lived credential is held",
                () -> assertThat(secrets).isNotEmpty().allMatch("GITHUB_TOKEN"::equals));
        claim(
                "it logs in to the registry with that token, read from standard input and never from the command line",
                () -> {
                    assertThat(run(LOGIN)).containsPattern("\\bdocker login ghcr\\.io\\b").contains("--password-stdin");
                    assertThat(map(login.get("env"))).containsEntry("GITHUB_TOKEN", "${{ secrets.GITHUB_TOKEN }}");
                });
        claim(
                "the checkout keeps no credential behind it, since nothing after it speaks to git",
                () -> {
                    assertThat((String) checkout.get("uses")).startsWith("actions/checkout@");
                    assertThat(map(checkout.get("with"))).containsEntry("persist-credentials", false);
                });
        claim(
                "and no step but the checkout runs an action, so no code from outside the repository runs beside a"
                        + " token that writes its packages",
                () -> assertThat(steps())
                        .filteredOn(step -> step.containsKey("uses"))
                        .extracting(step -> step.get("name"))
                        .containsExactly(CHECKOUT));
    }

    @Test
    @Story("The images are built once and pushed where they are pulled from")
    @DisplayName("Both images, the processor's and the CUDA one, are built side by side on GitHub's hosted runner and pushed under the repository's owner")
    void buildsTheTwoImagesSideBySideOnTheHostedRunner() throws IOException {
        Map<String, Object> job = job();
        Map<Object, Object> strategy = map(job.get("strategy"));

        claim(
                "the workflow has one job, which builds both images",
                () -> assertThat(map(workflow().get("jobs")).keySet()).containsExactly(JOB));
        claim(
                "it runs on GitHub's hosted Linux runner",
                () -> assertThat(job.get("runs-on")).isEqualTo(RUNNER));
        claim(
                "and is stopped after " + TIMEOUT_MINUTES + " minutes, two hours for the longer image to pull its base,"
                        + " install LibreOffice and push",
                () -> assertThat(job.get("timeout-minutes")).isEqualTo(TIMEOUT_MINUTES));
        claim(
                "one image stopping does not stop the other",
                () -> assertThat(strategy.get("fail-fast")).isEqualTo(false));
        claim(
                "the matrix has two entries, the processor image and the CUDA image, and no other",
                () -> assertThat(entries())
                        .extracting(entry -> entry.get("image"))
                        .containsExactlyInAnyOrder(CPU_IMAGE, GPU_IMAGE));
        claim(
                "both under " + NAMESPACE + ", the repository owner's own namespace, which no one else can push to",
                () -> assertThat(entries())
                        .extracting(entry -> (String) entry.get("image"))
                        .allMatch(image -> image.startsWith(NAMESPACE)));
    }

    @Test
    @Story("The images are built once and pushed where they are pulled from")
    @DisplayName("Each image is tagged for the base it is built on, the PDF parser it carries and the revision of what Vespera adds, and no tag moves")
    @Link(name = "ADR-179", url = Adr.NO_ENTRY_POINT_STARTS_THE_SIDECARS, type = "adr")
    void tagsEachImageForItsBaseItsParserAndItsRevision() throws IOException {
        String tag = DoclingSidecarImageTest.baseTag() + "-docling-parse-" + DoclingSidecarImageTest.pinnedParserVersion()
                + REVISION;

        claim(
                "each image's tag is the base image's own tag, then the PDF parser release the Containerfile pins, then"
                        + " the revision of the layers Vespera adds, the same for both images",
                () -> assertThat(entries()).extracting(entry -> entry.get("tag")).containsOnly(tag));
        claim(
                "which, written out, is v1.32.0-docling-parse-7.17.0" + REVISION,
                () -> assertThat(tag).isEqualTo("v1.32.0-docling-parse-7.17.0" + REVISION));
        claim(
                "and no image is given a tag that moves, such as latest",
                () -> assertThat(text()).doesNotContainPattern(":latest\\b"));
    }

    @Test
    @Story("The images are built once and pushed where they are pulled from")
    @DisplayName("Each image is built from the one Containerfile, on its own base, under the name it is pushed as")
    @Link(name = "ADR-170", url = Adr.DOCLING_RUNS_ON_THE_GPU_WITH_AN_IMAGE_TAG_OF_ITS_OWN, type = "adr")
    @Link(name = "ADR-179", url = Adr.NO_ENTRY_POINT_STARTS_THE_SIDECARS, type = "adr")
    void buildsEachImageFromTheOneContainerfileOnItsOwnBase() throws IOException {
        Map<Object, Object> env = map(job().get("env"));
        String build = run(BUILD);

        claim(
                "the processor image is built on the base the Containerfile defaults to",
                () -> assertThat(entry(CPU_IMAGE).get("base")).isEqualTo(DoclingSidecarImageTest.CPU_BASE));
        claim(
                "the CUDA image on docling-serve's CUDA 12.8 base, at the same docling-serve release",
                () -> assertThat(entry(GPU_IMAGE).get("base"))
                        .isEqualTo(DoclingSidecarImageTest.GPU_BASE_REPOSITORY + ":" + DoclingSidecarImageTest.baseTag()));
        claim(
                "the name each image is built under is its package and its tag, without anything else",
                () -> assertThat(env).containsEntry(REFERENCE, "${{ matrix.image }}:${{ matrix.tag }}"));
        claim(
                "and its base is its own entry's",
                () -> assertThat(env).containsEntry(BASE, "${{ matrix.base }}"));
        claim(
                "the build passes the Containerfile that base",
                () -> assertThat(build).containsPattern("--build-arg\\s+\"?DOCLING_SERVE_BASE=" + variable(BASE) + "(\\s|$)"));
        claim(
                "and that name, which the image keeps and reports, so it says truly which image it is",
                () -> assertThat(build).containsPattern("--build-arg\\s+\"?VESPERA_IMAGE=" + variable(REFERENCE) + "(\\s|$)"));
        claim(
                "it tags the image with that name",
                () -> assertThat(build).containsPattern("(-t|--tag)\\s+" + variable(REFERENCE) + "(\\s|$)"));
        claim(
                "and builds it from the one Containerfile, in the folder beside it",
                () -> assertThat(build)
                        .containsPattern("\\bdocker (buildx )?build\\b")
                        .containsPattern("(-f|--file)\\s+\"?docker/docling-serve/Containerfile\"?(\\s|$)")
                        .containsPattern("\\s\"?docker/docling-serve/?\"?(\\s|$)"));
    }

    @Test
    @Story("The images are built once and pushed where they are pulled from")
    @DisplayName("Each image is labelled with the repository it comes from, the commit it is built from and the base it is built on")
    void labelsEachImageWithTheRepositoryAndTheCommitItIsBuiltFrom() throws IOException {
        String build = run(BUILD);

        claim(
                "the image names the repository it comes from, which links its package to it",
                () -> assertThat(build).containsPattern(
                        "--label\\s+\"?org\\.opencontainers\\.image\\.source=https://github\\.com/algernon28/vespera\"?(\\s|$)"));
        claim(
                "the commit it was built from",
                () -> assertThat(build).containsPattern(
                        "--label\\s+\"?org\\.opencontainers\\.image\\.revision=(" + variable("GITHUB_SHA")
                                + "|\\$\\{\\{\\s*github\\.sha\\s*}})"));
        claim(
                "and the base it was built on",
                () -> assertThat(build).containsPattern(
                        "--label\\s+\"?org\\.opencontainers\\.image\\.base\\.name=" + variable(BASE)));
    }

    @Test
    @Story("A tag the registry holds is never pushed over")
    @DisplayName("The workflow checks the tag is not in the registry before it builds, pushes that one tag once, and never runs two pushes at once")
    void neverPushesOverATagTheRegistryHolds() throws IOException {
        List<String> names = steps().stream().map(step -> (String) step.get("name")).toList();
        Map<Object, Object> concurrency = map(workflow().get("concurrency"));
        Pattern push = Pattern.compile("\\bdocker (image )?push\\b");

        claim(
                "the steps are these eight, in this order: check out, log in, check the tag, free the disk, check the"
                        + " room, build, push, and report the digest; so the tag is checked, and the room, before"
                        + " anything is pulled or built, and the digest read only once the push is done",
                () -> assertThat(names).isEqualTo(STEPS));
        claim(
                "the check asks the registry about the very name the image is pushed under",
                () -> assertThat(run(CHECK)).containsPattern(variable(REFERENCE)));
        claim(
                "one step pushes, the push step",
                () -> assertThat(steps())
                        .filteredOn(step -> push.matcher(String.valueOf(step.get("run"))).find())
                        .extracting(step -> step.get("name"))
                        .containsExactly(PUSH));
        claim(
                "and it pushes that name, once, and nothing else",
                () -> {
                    assertThat(run(PUSH)).containsPattern("^\\s*docker push\\s+" + variable(REFERENCE) + "\\s*$");
                    assertThat(push.matcher(run(PUSH)).results().count()).isEqualTo(1);
                });
        claim(
                "no step gives the image a second name to push",
                () -> assertThat(text()).doesNotContainPattern("\\bdocker (image )?tag\\b|imagetools create"));
        claim(
                "runs of the workflow wait for one another rather than overlap, so two never check and push the same tag"
                        + " at once",
                () -> assertThat(concurrency.get("group")).isEqualTo("docling-image"));
        claim(
                "and a run is never cancelled for the next one, between its push and its report",
                () -> assertThat(concurrency.get("cancel-in-progress")).isEqualTo(false));
    }

    @Test
    @Story("The CUDA image fits on the hosted runner")
    @DisplayName("The CUDA image's entry frees the runner's disk first, and each entry checks it has the room it needs before it builds")
    void freesTheDiskBeforeBuildingTheCudaImage() throws IOException {
        Map<Object, Object> env = map(job().get("env"));

        claim(
                "the CUDA image's entry frees the runner's disk, since its build needs more than the runner is"
                        + " documented to have free",
                () -> assertThat(entry(GPU_IMAGE).get("free-disk")).isEqualTo(true));
        claim(
                "the processor image's entry does not, since its build fits as the runner comes",
                () -> assertThat(entry(CPU_IMAGE).get("free-disk")).isEqualTo(false));
        claim(
                "the CUDA build needs " + GPU_NEEDS_GB + " GB free, its estimated size and the compressed layers it"
                        + " pulls, with a margin",
                () -> assertThat(entry(GPU_IMAGE).get("needs-gb")).isEqualTo(GPU_NEEDS_GB));
        claim(
                "the processor build needs " + CPU_NEEDS_GB + " GB, its size and its compressed layers, with a margin",
                () -> assertThat(entry(CPU_IMAGE).get("needs-gb")).isEqualTo(CPU_NEEDS_GB));
        claim(
                "the disk is freed only where the entry says so",
                () -> assertThat(String.valueOf(step(FREE).get("if"))).contains("matrix.free-disk"));
        claim(
                "and the room is checked against the entry's own need",
                () -> {
                    assertThat(env).containsEntry(NEEDS_GB, "${{ matrix.needs-gb }}");
                    assertThat(run(ROOM)).containsPattern(variable(NEEDS_GB));
                });
    }

    @Test
    @Story("A tag the registry holds is never pushed over")
    @DisplayName("After the push the workflow reads the digest back from the registry and writes it where it can be copied")
    void reportsTheDigestTheRegistryHoldsAfterThePush() throws IOException {
        String report = run(DIGEST);

        claim(
                "the digest is read from the registry, for the name just pushed, and not from the runner's own copy",
                () -> assertThat(report).contains("imagetools inspect").containsPattern(variable(REFERENCE)));
        claim(
                "and written to the run's summary, where the change that pins it copies it from",
                () -> assertThat(report).contains("GITHUB_STEP_SUMMARY"));
    }

    /** The workflow's text, once it is there. */
    private static String text() throws IOException {
        claim(
                "the workflow that builds and pushes the images is " + WORKFLOW,
                () -> assertThat(WORKFLOW).isRegularFile());
        return Files.readString(WORKFLOW);
    }

    private static Map<Object, Object> workflow() throws IOException {
        return map(new Yaml().load(text()));
    }

    /** The {@code on} key, which a YAML 1.1 reader such as SnakeYAML reads as the boolean {@code true}. */
    private static Object triggers() throws IOException {
        Map<Object, Object> workflow = workflow();
        return workflow.containsKey("on") ? workflow.get("on") : workflow.get(Boolean.TRUE);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> job() throws IOException {
        Object job = map(workflow().get("jobs")).get(JOB);
        return job == null ? Map.of() : (Map<String, Object>) job;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> steps() throws IOException {
        Object steps = job().get("steps");
        return steps == null ? List.of() : (List<Map<String, Object>>) steps;
    }

    private static Map<String, Object> step(String name) throws IOException {
        return steps().stream().filter(step -> name.equals(step.get("name"))).findFirst().orElse(Map.of());
    }

    /** A step's shell lines, each continued line joined to the next, or an empty string where it runs none. */
    private static String run(String name) throws IOException {
        Object run = step(name).get("run");
        return run == null ? "" : ((String) run).replaceAll("\\\\\\r?\\n\\s*", " ").strip();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> entries() throws IOException {
        Object include = map(map(job().get("strategy")).get("matrix")).get("include");
        return include == null ? List.of() : (List<Map<String, Object>>) include;
    }

    private static Map<String, Object> entry(String image) throws IOException {
        return entries().stream().filter(entry -> image.equals(entry.get("image"))).findFirst().orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<Object, Object>) map : Map.of();
    }
}
