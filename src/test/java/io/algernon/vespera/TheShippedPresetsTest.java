package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * The presets the application ships, read as Spring reads the file (ADR-330 §8): clustering's two numbers, at the
 * values every run was minted under while they were constants of the code, held exactly, because another value here
 * re-clusters every corpus whose profile names none.
 */
@Epic("Architecture")
@Feature("Shipped configuration")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_CLUSTERING_SHIPS_AS_PRESETS, type = "adr")
@Disabled("ADR-330: awaits the two presets in application.yaml")
class TheShippedPresetsTest {

    /** The file the application ships with. */
    private static final String SHIPPED = "application.yaml";

    /** How many neighbours clustering keeps of each document, the value ADR-087 chose. */
    private static final String SHIPPED_NEIGHBOURS = "15";

    /** How finely clustering divides a partition, the value ADR-087 chose. */
    private static final String SHIPPED_RESOLUTION = "1.0";

    @Test
    @Story("How finely documents are clustered is a preset")
    @DisplayName("The shipped file names the neighbours and the resolution at the values every earlier run was made under")
    void theShippedFileNamesBothAtTheirEarlierValues() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(SHIPPED));
        yaml.afterPropertiesSet();
        Map<Object, Object> shipped = yaml.getObject();

        claim("the neighbours are " + SHIPPED_NEIGHBOURS + " and the resolution " + SHIPPED_RESOLUTION
                        + ", so a corpus naming neither is clustered as it was",
                () -> assertThat(List.of(String.valueOf(shipped.get("vespera.clustering.neighbours")),
                                String.valueOf(shipped.get("vespera.clustering.resolution"))))
                        .containsExactly(SHIPPED_NEIGHBOURS, SHIPPED_RESOLUTION));
    }
}
