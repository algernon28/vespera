package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.deliverable.ListedSurvivor;
import io.algernon.vespera.engine.deliverable.RecordInputs;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;

/** {@link RecordInputs} resolved at the first record composed, so a drawing no plugin reads records in needs none. */
final class LazyRecordInputs implements RecordInputs {

    private final ObjectProvider<RecordInputsOf> provider;
    private RecordInputs resolved;

    LazyRecordInputs(ObjectProvider<RecordInputsOf> provider) {
        this.provider = provider;
    }

    private RecordInputs inputs() {
        if (resolved == null) {
            resolved = provider.getObject().forOneDrawing();
        }
        return resolved;
    }

    @Override
    public String embedderIdentity() throws IOException {
        return inputs().embedderIdentity();
    }

    @Override
    public float[] vectorOf(String writing) throws IOException {
        return inputs().vectorOf(writing);
    }

    @Override
    public void passagesOf(ListedSurvivor member, PassageConsumer passages) throws IOException {
        inputs().passagesOf(member, passages);
    }
}
