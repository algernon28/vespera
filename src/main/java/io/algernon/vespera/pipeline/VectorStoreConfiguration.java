package io.algernon.vespera.pipeline;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The vector store reaches Chroma when it is first asked for, and not while the application starts
 * (ADR-142).
 *
 * <p>Spring AI's Chroma store fetches its collection as the bean is built, so an eager one makes
 * every command need a reachable Chroma, {@code --version} included — and a command that fails to
 * start exits 1 before picocli has read what the operator typed. Chroma is a derived projection
 * (ADR-039) that no command reads yet, so the bean stays configured and is only deferred: whatever
 * first asks for it is what connects, and meets the refusal if Chroma is down.
 *
 * <p>Only the vector store is made lazy. Global lazy initialisation would defer every bean, and with
 * them every configuration fault a start-up is currently the place to find.
 */
@Configuration(proxyBeanMethods = false)
class VectorStoreConfiguration {

    /**
     * Static, because a post-processor has to exist before the configuration class that declares it
     * is itself instantiated. Names are looked up without eager initialisation, so finding the bean
     * does not build it.
     */
    @Bean
    static BeanFactoryPostProcessor vectorStoreIsCreatedOnFirstUse() {
        return beanFactory -> {
            for (String name : beanFactory.getBeanNamesForType(VectorStore.class, true, false)) {
                beanFactory.getBeanDefinition(name).setLazyInit(true);
            }
        };
    }
}
