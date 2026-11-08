package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * ADR-211 section 12: SQLite's directory for temporary files is set once, when the process starts, and no
 * connection pool sets it.
 *
 * <p>SQLite keeps that directory in one variable for the whole process, and its documentation says never to
 * change it while another thread is running any SQLite interface. A setting carried by the datasource, in
 * its URL or among its properties, is run again by every connection the pool opens, and the shipped pool
 * opens a new one every 35 seconds. It would also reach every test context, since {@code
 * application-test.yaml} is layered over {@code application.yaml}: measured, with such a key in the shipped
 * file, 391 tests of 76 classes failed, the first to open a connection on a working directory that does not
 * exist, and later ones because the directory had been left pointing at a folder a test had deleted.
 *
 * <p>So this holds the door shut: the shipped configuration names no such setting, and a connection of the
 * test profile's pool reports no directory. The second claim also fails if anything else run in this JVM
 * before it set the directory and did not put the default back.
 *
 * <p>The third test holds what the tests of the listener itself rely on, {@code
 * TemporaryFilesInTheWorkingDirectoryTest}: they change the process's setting while the contexts cached
 * across test classes still hold their connections, and that is tolerable only while no thread of such a
 * pool calls SQLite. The test profile's pool retires nothing and drops nothing, by {@code
 * application-test.yaml}, and does not check its connection either, by the {@code keepalive-time: 0} it
 * inherits from {@code application.yaml}: Hikari 7.0.2 would otherwise check an idle connection every two
 * minutes on its housekeeping thread, and a lifetime of 0 does not turn that off. The values are read from
 * the pool a test context builds, so a change to either file that brings one of the three back fails here.
 *
 * <p>Green before ADR-211 is built, and after.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Architecture")
@Feature("Shipped configuration")
@Issue("456")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
class NoPoolSetsSqlitesTemporaryDirectoryTest {

    /** SQLite's name for the setting, as a datasource URL or a driver property would carry it. */
    private static final String THE_SETTING = "temp_store_directory";

    /** The shipped file and the one the test profile layers over it. */
    private static final List<String> CONFIGURATION_FILES = List.of("application.yaml", "application-test.yaml");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What the database sorts on disk is written in the working directory")
    @DisplayName("No configuration file gives the connection pool a directory for the database's temporary files")
    void noConfigurationFileCarriesTheSetting() {
        for (String file : CONFIGURATION_FILES) {
            YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(new ClassPathResource(file));
            yaml.afterPropertiesSet();
            claim(
                    file + " names the setting nowhere, neither as a key nor inside a value such as the"
                            + " datasource's URL: a pool that carried it would run it on every connection it"
                            + " opens, while other connections are at work",
                    () -> assertThat(yaml.getObject()).allSatisfy((key, value) -> {
                        assertThat(String.valueOf(key)).doesNotContain(THE_SETTING);
                        assertThat(String.valueOf(value)).doesNotContain(THE_SETTING);
                    }));
        }
    }

    @Test
    @Story("What the database sorts on disk is written in the working directory")
    @DisplayName("A connection of the test suite's pool has no directory set for the database's temporary files")
    void aTestProfileConnectionReportsNoDirectory() {
        // Through the template: the slice's transaction holds the pool's one connection, so a second would never come.
        List<String> reported = jdbcTemplate.query(
                "PRAGMA temp_store_directory",
                (resultSet, rowNumber) -> resultSet.getString(1) == null ? "" : resultSet.getString(1));
        claim(
                "the database reports no directory of its own choosing for temporary files, so it uses the"
                        + " system's: nothing the suite ran before this left the whole process pointing at a"
                        + " folder that may be gone",
                () -> assertThat(reported).allSatisfy(directory -> assertThat(directory).isEmpty()));
    }
}
