package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.ChildCommand;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.objenesis.ObjenesisStd;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

/**
 * Every option and positional argument of the commands the control panel starts is accounted for by the control
 * panel (ADR-336 §2): {@link ChildCommand#ARGUMENTS} holds each one of picocli's model of {@code vespera} and of each
 * of its commands, once, by the command's name and the argument's, with the control of the launch form that sets it
 * or none, and why.
 *
 * <p>The table names an option without its leading {@code --}, {@code help} for {@code --help}, so that no class of the
 * control panel holds the text of the two options ADR-278 §1's scan forbids it (ADR-336 §2); this test strips the
 * prefix from picocli's longest name before it compares.
 *
 * <p>The model is read as the jar builds it, its mixins included, so an option added to a command, a mixin's among
 * them, or a command added with arguments of its own, fails here until the control panel's table says how it is set
 * or that it is never passed. That each control the table names is drawn on the launch form is held by {@code
 * controlpanel.TheLaunchPageStartsLabelAutoScreenTest.everyControlTheTableNamesIsOnTheLaunchForm}.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-336", url = Adr.THE_LAUNCH_PAGE_STARTS_LABEL_AUTO_AND_EVERY_ARGUMENT_OF_THE_JAR_IS_ACCOUNTED_FOR, type = "adr")
@Link(name = "ADR-278", url = Adr.THE_CONTROL_PANEL_STARTS_THE_JAR_AS_ITS_CHILD_AND_STOPS_ONLY_ITS_OWN, type = "adr")
class TheControlPanelAccountsForEveryArgumentOfTheJarsCommandsTest {

    /**
     * Builds each command without calling its constructor, whose collaborators are the application's, so that picocli
     * reads the commands' model, mixins included, with no application context, as {@code
     * NothingShippedReachesConfluenceTest} builds it; anything else, a mixin among them, is built as picocli builds it.
     */
    private static final IFactory WITHOUT_ITS_COLLABORATORS = new IFactory() {
        private final ObjenesisStd objenesis = new ObjenesisStd();

        @Override
        public <K> K create(Class<K> type) throws Exception {
            return type.isAnnotationPresent(Command.class)
                    ? objenesis.newInstance(type)
                    : CommandLine.defaultFactory().create(type);
        }
    };

    /** The launch form's control for the working directory, which sets every command's {@code --db-dir}. */
    private static final String THE_WORKING_DIRECTORY = "vespera.working-dir";

    @Test
    @Story("Every option of the jar's commands is set from the control panel or said not to be")
    @DisplayName("Every option and positional argument of vespera and its commands is in the control panel's table, once, and the table holds no other")
    void everyOptionAndPositionalArgumentOfTheCommandTreeIsInTheControlPanelsTableOnce() {
        CommandSpec vespera = new CommandLine(VesperaCommand.class, WITHOUT_ITS_COLLABORATORS).getCommandSpec();
        List<String> inTheModel = new ArrayList<>();
        walk(vespera, inTheModel);
        List<String> inTheTable = ChildCommand.ARGUMENTS.stream()
                .map(argument -> argument.command() + " " + argument.name())
                .toList();

        claim("the model was read through the command's own tree, so the answers below are about the command line an"
                        + " operator types, its mixins included",
                () -> assertThat(inTheModel).contains("vespera help", "run <root>", "label auto", "publish db-dir"));
        claim("the table names each argument once",
                () -> assertThat(inTheTable).doesNotHaveDuplicates());
        claim("and it names every option and positional argument of the model, by command and name, and nothing the"
                        + " model does not hold",
                () -> assertThat(inTheTable).containsExactlyInAnyOrderElementsOf(inTheModel));
    }

    @Test
    @Story("Every option of the jar's commands is set from the control panel or said not to be")
    @DisplayName("Each argument is set by a control of the launch page or never passed, and the table says why for each")
    void eachArgumentIsSetByALaunchControlOrNeverPassedAndSaysWhy() {
        Map<String, String> controls = new LinkedHashMap<>();
        for (ChildCommand.Argument argument : ChildCommand.ARGUMENTS) {
            controls.put(argument.command() + " " + argument.name(), argument.control());
        }
        Set<String> withoutAReason = ChildCommand.ARGUMENTS.stream()
                .filter(argument -> argument.reason() == null || argument.reason().isBlank())
                .map(argument -> argument.command() + " " + argument.name())
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> neverPassed = controls.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));

        claim("every argument says why it is set as it is, or never passed",
                () -> assertThat(withoutAReason).as("arguments of the table with no reason").isEmpty());
        claim("the corpus root is set by its field, every command's working directory by the working directory's,"
                        + " and --auto by Have the local model answer",
                () -> assertThat(controls).containsAllEntriesOf(Map.of(
                        "run <root>", "vespera.corpus-root",
                        "run db-dir", THE_WORKING_DIRECTORY,
                        "label db-dir", THE_WORKING_DIRECTORY,
                        "publish db-dir", THE_WORKING_DIRECTORY,
                        "label auto", "label-auto")));
        claim("and the arguments never passed are vespera's help and version options, and label's file, and no other",
                () -> assertThat(neverPassed).containsExactlyInAnyOrder("vespera help", "vespera version",
                        "label <file>"));
    }

    /**
     * Every command under {@code command}, itself included: each of its options by its longest name without its
     * leading {@code --} and each of its positional arguments by its label, after the command's name and a space, the
     * standard help options among them.
     */
    private static void walk(CommandSpec command, List<String> arguments) {
        for (OptionSpec option : command.options()) {
            arguments.add(command.name() + " " + withoutTheLeadingDashes(option.longestName()));
        }
        for (PositionalParamSpec positional : command.positionalParameters()) {
            arguments.add(command.name() + " " + positional.paramLabel());
        }
        command.subcommands().values().forEach(sub -> walk(sub.getCommandSpec(), arguments));
    }

    /** An option's longest name as the control panel's table names it (ADR-336 §2): {@code db-dir} for {@code --db-dir}. */
    private static String withoutTheLeadingDashes(String longestName) {
        return longestName.startsWith("--") ? longestName.substring(2) : longestName;
    }
}
