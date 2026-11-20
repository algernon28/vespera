package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.deliverable.Outputs;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.algernon.vespera.engine.profile.ProfileStore;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The refusal at the start of {@code vespera run} of a profile whose {@code outputs} names what no drawing can draw
 * (ADR-434 §3): a name that is no output, or a plugin that draws from the HTML tree named without {@code tree}. The
 * line has the shape of ADR-384 §2's and is written after that refusal and before the corpus-root lock, so nothing is
 * walked, no run is minted and no sidecar is started.
 *
 * <p>It reads the one key and nothing else. A profile that cannot be read refuses nothing here and is left to the
 * job's own refusal, as {@link ModelsAtTheStart} leaves it. {@code vespera label} and {@code vespera publish} read no
 * {@code outputs}.
 */
@Component
class OutputsAtTheStart {

    private final ProfileStore profileStore;
    private final List<Plugin> plugins;

    @Autowired
    OutputsAtTheStart(ProfileStore profileStore, ObjectProvider<Plugin> plugins) {
        this(profileStore, plugins.orderedStream().toList());
    }

    OutputsAtTheStart(ProfileStore profileStore, List<Plugin> plugins) {
        this.profileStore = profileStore;
        this.plugins = List.copyOf(plugins);
    }

    /** The line that ends the start of {@code vespera run}, or empty where the outputs can be drawn. */
    Optional<String> refusal() {
        String stated;
        try {
            stated = profileStore.load().outputs().value();
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
        return Outputs.read(stated).refusal(plugins).map(refusal -> "vespera run stopped at its start: "
                + refusal.reason() + ", so nothing was walked and no run was minted -- " + refusal.advice());
    }
}
