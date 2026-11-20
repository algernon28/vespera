package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Whether a container is on this machine is worked out by resolving its base URL's host (ADR-430 §10, amending ADR-323
 * §3's step 2, which read the host as written): it is where any address the host resolves to is a loopback or a
 * wildcard address or one of this machine's own; it is another machine's where none is; and a host that cannot be
 * resolved is neither, a host under {@code .invalid} among them, never looked up.
 *
 * <p>No name is resolved: {@link ThisMachine} is given a resolver of the test's, which answers a few made-up names and
 * records each it is asked, and this machine's own addresses as the test names them. The last test asks the control
 * panel's way in, {@code DockerEngine}, which resolves for real, with {@code localhost}, a literal and {@code .invalid}
 * hosts alone, none of which reaches a resolver of the network.
 */
@Epic("Pipeline")
@Feature("The containers")
@Issue("889")
@Link(name = "ADR-430", url = Adr.THE_CONTROL_PANEL_CHECKS_DOCKER_AND_STARTS_NOTHING_UNTIL_START_DOCKER, type = "adr")
class AServiceIsOnThisMachineWhereItsHostResolvesHereTest {

    /** This machine's own address on its network, as {@code NetworkInterface} would give it. */
    private static final InetAddress OWN = InetAddress.ofLiteral("192.168.1.20");

    /** Another machine's address on the same network. */
    private static final InetAddress ANOTHERS = InetAddress.ofLiteral("192.168.1.30");

    /** The names the test's resolver answers, and what each resolves to. */
    private static final Map<String, List<InetAddress>> RESOLVES = Map.of(
            "localhost", List.of(InetAddress.ofLiteral("127.0.0.1"), InetAddress.ofLiteral("::1")),
            "my-pc", List.of(OWN),
            "host.docker.internal", List.of(OWN),
            "gpu-box.lan", List.of(ANOTHERS),
            "both-ways.lan", List.of(ANOTHERS, InetAddress.ofLiteral("127.0.1.1")));

    /** A resolver of the test's: it answers {@link #RESOLVES} and refuses any other name, recording each it is asked. */
    private static final class Resolving implements ThisMachine.Resolver {

        final List<String> asked = new CopyOnWriteArrayList<>();

        @Override
        public InetAddress[] allByName(String host) throws UnknownHostException {
            asked.add(host);
            List<InetAddress> addresses = RESOLVES.get(host);
            if (addresses == null) {
                throw new UnknownHostException(host + ": no such host is known");
            }
            return addresses.toArray(InetAddress[]::new);
        }
    }

    @TempDir
    Path folder;

    @Test
    @Story("Vespera starts only the containers on this machine")
    @DisplayName("A container is on this machine where its host resolves to a loopback, a wildcard or one of this machine's own addresses, and on another where it resolves to none of them")
    void aHostIsThisMachinesWhereAnAddressItResolvesToIsLoopbackWildcardOrOneOfItsOwn() {
        Resolving resolver = new Resolving();
        ThisMachine thisMachine = new ThisMachine(resolver, () -> List.of(OWN));
        Map<String, ThisMachine.Where> expected = new LinkedHashMap<>();
        expected.put("http://localhost:5001", ThisMachine.Where.THIS_MACHINE);
        expected.put("http://127.0.0.1:5001", ThisMachine.Where.THIS_MACHINE);
        expected.put("http://[::1]:11434", ThisMachine.Where.THIS_MACHINE);
        expected.put("http://0.0.0.0:5001", ThisMachine.Where.THIS_MACHINE);
        expected.put("http://192.168.1.20:5001", ThisMachine.Where.THIS_MACHINE);
        expected.put("http://my-pc:5001", ThisMachine.Where.THIS_MACHINE);
        expected.put("http://host.docker.internal:11434", ThisMachine.Where.THIS_MACHINE);
        expected.put("http://both-ways.lan:11434", ThisMachine.Where.THIS_MACHINE);
        expected.put("http://gpu-box.lan:11434", ThisMachine.Where.ANOTHER_MACHINE);
        expected.put("http://192.168.1.30:11434", ThisMachine.Where.ANOTHER_MACHINE);

        Map<String, ThisMachine.Where> found = new LinkedHashMap<>();
        expected.keySet().forEach(url -> found.put(url, thisMachine.where(URI.create(url))));

        claim("localhost, 127.0.0.1, ::1 and the wildcard 0.0.0.0, this machine's own address 192.168.1.20, the names my-pc"
                        + " and host.docker.internal resolving to it, and a name resolving to another machine's address"
                        + " and to a loopback one, are this machine; gpu-box.lan, resolving to 192.168.1.30 alone, and"
                        + " that address written out, are another machine",
                () -> assertThat(found).containsExactlyEntriesOf(expected));
        claim("and only the names were asked of the resolver, never an address written out",
                () -> assertThat(resolver.asked).containsExactlyInAnyOrder(
                        "localhost", "my-pc", "host.docker.internal", "both-ways.lan", "gpu-box.lan"));
    }

    @Test
    @Story("Vespera starts only the containers on this machine")
    @DisplayName("A host that cannot be resolved is neither this machine nor another, and a host under .invalid is never looked up")
    void aHostThatCannotBeResolvedIsNeitherAndAnInvalidOneIsNeverLookedUp() {
        Resolving resolver = new Resolving();
        ThisMachine thisMachine = new ThisMachine(resolver, () -> List.of(OWN));
        List<String> invalid = List.of("http://containers.invalid:5001", "http://Docling.INVALID:5001",
                "http://containers.invalid.:11434");

        ThisMachine.Where unknown = thisMachine.where(URI.create("http://docling.example.lan:5001"));
        ThisMachine.Where noHost = thisMachine.where(URI.create("file:///no/host"));
        List<ThisMachine.Where> underInvalid = invalid.stream().map(url -> thisMachine.where(URI.create(url))).toList();

        claim("a name the resolver knows nothing of cannot be resolved",
                () -> assertThat(unknown).isEqualTo(ThisMachine.Where.NOT_RESOLVED));
        claim("nor can a base URL with no host", () -> assertThat(noHost).isEqualTo(ThisMachine.Where.NOT_RESOLVED));
        claim("a host under .invalid, in any case and with a final dot, cannot be resolved either",
                () -> assertThat(underInvalid).containsOnly(ThisMachine.Where.NOT_RESOLVED).hasSize(invalid.size()));
        claim("and of these only the unknown name was asked of the resolver: no host under .invalid is looked up",
                () -> assertThat(resolver.asked).containsExactly("docling.example.lan"));
    }

    @Test
    @Story("The control panel checks Docker and starts nothing as it opens")
    @DisplayName("The control panel asks the same rule through the Docker engine's class, and containers that cannot be resolved are neither asked nor started")
    void theControlPanelAsksTheSameRuleThroughTheDockerEngine() throws IOException {
        URI doclingNowhere = URI.create("http://containers.invalid:5001");
        URI ollamaNowhere = URI.create("http://containers.invalid:11434");
        Path notAFolder = Files.createFile(folder.resolve("a-file-where-the-lock-folder-would-be"));
        DockerEngine engine = DockerEngine.of("", Duration.ofSeconds(1), notAFolder, null);
        List<Optional<Boolean>> where = new ArrayList<>();
        for (String url : List.of("http://localhost:5001", "http://127.0.0.1:11434", "http://containers.invalid:5001")) {
            where.add(DockerEngine.isOnThisMachine(URI.create(url)));
        }

        Optional<Boolean> answers = engine.engineAnswers(doclingNowhere, ollamaNowhere);
        Optional<String> started = engine.startTheContainers(doclingNowhere, ollamaNowhere, "");

        claim("localhost and 127.0.0.1 are this machine, and a host under .invalid cannot be resolved",
                () -> assertThat(where).containsExactly(Optional.of(true), Optional.of(true), Optional.empty()));
        claim("with neither container on this machine, the daemon is not asked",
                () -> assertThat(answers).isEmpty());
        claim("and Start Docker's start does nothing and says nothing, never reaching the container lock in a folder that"
                        + " is a file", () -> assertThat(started).isEmpty());
    }
}
