package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.OccurrenceId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.springframework.batch.core.listener.ItemWriteListener;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.step.item.ChunkOrientedStep;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Listens beside {@link ExtractionFaultRecorder} on the shipped {@code extractionStep} bean, and records, for
 * each set-aside the step hears, where it was heard: on which thread, whether inside a transaction, which one,
 * whether that transaction could already see the occurrence's fault row, and whether it went on to commit or
 * roll back (ADR-214 section 14).
 *
 * <p>The step is the one {@link ExtractionJobConfiguration} builds, with its builder, its transaction manager
 * and its listeners, and nothing about it is replaced: the probe is registered on it after it is built, as one
 * more skip listener and one item-write listener, the same calls the builder makes for its own. A skip listener
 * runs in the order it was registered, so this one hears each set-aside after the fault recorder has written
 * its row. A change to how the step is built that moved the set-aside out of the chunk's transaction, onto
 * another thread or into a transaction of its own, shows here; so does a step that is no longer a
 * {@code ChunkOrientedStep}, which {@link #STEP_PROBED} records.
 *
 * <p>Static, on {@link DrainThreadProbe}'s precedent: the step is one bean per Spring context and the test reads
 * what it saw after the command returns. {@link #forget()} runs before each test.
 */
@TestConfiguration
class SetAsideProbe {

    /** One set-aside the step heard. */
    record Heard(
            OccurrenceId occurrence,
            String thread,
            boolean inATransaction,
            Object transaction,
            Integer faultRowsTheTransactionSaw,
            AtomicReference<String> howTheTransactionEnded) {}

    /** Every set-aside heard since {@link #forget()}, in order. */
    static final List<Heard> HEARD = new CopyOnWriteArrayList<>();

    /** The transaction each chunk's write ran in, since {@link #forget()}. */
    static final List<Object> WRITTEN_IN = new CopyOnWriteArrayList<>();

    /** The class of the {@code extractionStep} bean the probe was offered, and probed where it could be. */
    static final AtomicReference<Class<?>> STEP_PROBED = new AtomicReference<>();

    static void forget() {
        HEARD.clear();
        WRITTEN_IN.clear();
    }

    /** Static, so the bean post-processor is built before the step it listens on. */
    @Bean
    static BeanPostProcessor setAsideProbing(ObjectProvider<DataSource> dataSources) {
        return new BeanPostProcessor() {
            @Override
            @SuppressWarnings("unchecked")
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if ("extractionStep".equals(beanName)) {
                    STEP_PROBED.set(bean.getClass());
                    if (bean instanceof ChunkOrientedStep<?, ?> step) {
                        Listener listener = new Listener(dataSources);
                        ((ChunkOrientedStep<OccurrenceId, ExtractionOutcome>) step).registerSkipListener(listener);
                        ((ChunkOrientedStep<OccurrenceId, ExtractionOutcome>) step).registerItemWriteListener(listener);
                    }
                }
                return bean;
            }
        };
    }

    private static final class Listener
            implements SkipListener<OccurrenceId, ExtractionOutcome>, ItemWriteListener<ExtractionOutcome> {

        private final ObjectProvider<DataSource> dataSources;

        Listener(ObjectProvider<DataSource> dataSources) {
            this.dataSources = dataSources;
        }

        @Override
        public void onSkipInProcess(OccurrenceId item, Throwable t) {
            DataSource dataSource = dataSources.getObject();
            boolean inATransaction = TransactionSynchronizationManager.isActualTransactionActive();
            AtomicReference<String> ended = new AtomicReference<>("not known");
            Object transaction = null;
            Integer faultRows = null;
            if (inATransaction) {
                transaction = TransactionSynchronizationManager.getResource(dataSource);
                faultRows = new JdbcTemplate(dataSource)
                        .queryForObject(
                                "SELECT COUNT(*) FROM extraction_fault WHERE occurrence_id = ?",
                                Integer.class,
                                item.value());
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            ended.set(status == STATUS_COMMITTED
                                    ? "committed"
                                    : status == STATUS_ROLLED_BACK ? "rolled back" : "not known");
                        }
                    });
                }
            }
            HEARD.add(new Heard(item, Thread.currentThread().getName(), inATransaction, transaction, faultRows, ended));
        }

        @Override
        public void beforeWrite(Chunk<? extends ExtractionOutcome> items) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                WRITTEN_IN.add(TransactionSynchronizationManager.getResource(dataSources.getObject()));
            }
        }
    }
}
