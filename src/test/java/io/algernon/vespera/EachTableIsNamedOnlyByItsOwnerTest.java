package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/**
 * Which module's SQL names which table: each table's own module, and no other (ADR-209 section 3, closing
 * the gap ADR-041 recorded and ADR-060 narrowed for one query).
 *
 * <p>{@code ModuleBoundariesTest} cannot see this, because a statement is a string and not a reference
 * to a type. So the rule is read in two halves. Who owns a table is read from {@code schema.sql}, where
 * the line above each {@code CREATE TABLE} says so. What each shipped class asks of the database is read
 * from its compiled form, where a statement written as several joined literals is one text.
 *
 * <p>A table is counted as named where a text has it after {@code FROM}, {@code JOIN}, {@code INTO},
 * {@code UPDATE}, {@code TABLE} or {@code ON}, and where a text is the table's name and nothing else,
 * which is how a statement built around a table handed to it carries the name. A column that shares a
 * table's name, a file's header and a sentence for the operator are none of those. Before ADR-209 was
 * built this failed on its first claim, {@code schema.sql} naming no owner; with the owners named and
 * nothing moved it named three classes, holding five statements between them: the ledger deleting
 * {@code corpus}'s walk anomalies, {@code similarity}'s resolution reading {@code extraction}'s metrics,
 * and {@code embedding}'s comparison reading them and the two ends of their span.
 *
 * <p>One class is excepted, by name and by table: the invocation account, whose six counting statements
 * ADR-198 section 3 allowed in the module that assembles the job. The exception is held to its bounds
 * here, so a seventh table, a second class, or a statement of the account's that does more than count
 * fails this test.
 */
@Epic("Architecture")
@Feature("Module boundaries")
@Issue("350")
@Link(name = "ADR-209", url = Adr.THE_LEDGER_IS_FOUR_RECORDS_AND_A_TABLES_SQL_IS_ITS_OWNERS, type = "adr")
@Link(name = "ADR-041", url = Adr.LEDGER_OWNS_IDENTITY_AND_VERDICTS, type = "adr")
class EachTableIsNamedOnlyByItsOwnerTest {

    /** The line that says whose a table is, which stands directly above the table's {@code CREATE TABLE}. */
    private static final Pattern OWNED_TABLE =
            Pattern.compile("^-- owner: ([a-z]+)\\R^CREATE TABLE IF NOT EXISTS ([a-z_]+) \\(", Pattern.MULTILINE);

    private static final Pattern ANY_TABLE = Pattern.compile("^CREATE TABLE IF NOT EXISTS ([a-z_]+) \\(", Pattern.MULTILINE);

    /** Where a statement names the table it reads, writes, alters or indexes. */
    private static final Pattern TABLE_IN_A_STATEMENT =
            Pattern.compile("\\b(?:FROM|JOIN|INTO|UPDATE|TABLE|ON)\\s+([a-z_]+)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * The modules that own tables, which are the modules that record a schema version: {@code
     * SchemaVersionDeclarationTest} holds the same six to their version rows. {@code profile} is a file and
     * {@code pipeline} assembles the others, so neither owns a table.
     */
    private static final Set<String> MODULES_OWNING_TABLES =
            Set.of("ledger", "corpus", "extraction", "similarity", "embedding", "synthesis");

    /**
     * The one class ADR-198 section 3 lets count rows in tables it does not own, for the invocation
     * account, which ADR-209 section 3 leaves standing: counts and nothing else, made nowhere else.
     */
    private static final String THE_CLASS_THAT_COUNTS_FOR_THE_ACCOUNT = "io.algernon.vespera.pipeline.InvocationAccount";

    /** The seven tables that exception names, and no more: the ledger's three, extraction's one, synthesis's three. */
    private static final Set<String> TABLES_THE_ACCOUNT_COUNTS = Set.of(
            "file_occurrence", "run", "verdict", "extraction_fault", "cluster", "cluster_fault", "synthesis_doc");

    @Test
    @Story("A table is reached only through the module that owns it")
    @DisplayName("The schema says which module owns each table it creates")
    void theSchemaSaysWhoOwnsEachTable() throws Exception {
        String schema = schema();
        Set<String> created = new TreeSet<>();
        Matcher any = ANY_TABLE.matcher(schema);
        while (any.find()) {
            created.add(any.group(1));
        }
        Map<String, String> owners = ownersByTable(schema);

        claim(
                "every table the schema creates has a line directly above it naming its owner, so whose a"
                        + " table is can be read where the table is declared and not only in a comment's prose",
                () -> assertThat(owners.keySet()).containsExactlyElementsOf(created));
        claim(
                "the owners named are exactly the modules that record a schema version: a name missing here"
                        + " is a module that checks a version for tables it does not have, and a name unexpected"
                        + " here is a table whose owner checks no version for it",
                () -> assertThat(new TreeSet<>(owners.values())).containsExactlyInAnyOrderElementsOf(MODULES_OWNING_TABLES));
    }

    @Test
    @Story("A table is reached only through the module that owns it")
    @DisplayName("No shipped class asks the database about a table another module owns, but the one that counts for the invocation's account")
    void noClassNamesATableAnotherModuleOwns() throws Exception {
        Map<String, String> owners = ownersByTable(schema());
        Map<String, List<String>> strings = ShippedClasses.stringsByClass();

        List<String> reachingAcross = new ArrayList<>();
        int classesNamingATable = 0;
        for (Map.Entry<String, List<String>> shipped : strings.entrySet()) {
            String module = ShippedClasses.moduleOf(shipped.getKey());
            Set<String> named = tablesNamedIn(shipped.getValue(), owners.keySet());
            if (!named.isEmpty()) {
                classesNamingATable++;
            }
            for (String table : named) {
                boolean excepted = THE_CLASS_THAT_COUNTS_FOR_THE_ACCOUNT.equals(shipped.getKey())
                        && TABLES_THE_ACCOUNT_COUNTS.contains(table);
                if (!owners.get(table).equals(module) && !excepted) {
                    reachingAcross.add(shipped.getKey() + " names " + table + ", which is " + owners.get(table) + "'s");
                }
            }
        }
        int found = classesNamingATable;
        List<String> statementsOfTheAccount = strings.getOrDefault(THE_CLASS_THAT_COUNTS_FOR_THE_ACCOUNT, List.of()).stream()
                .filter(text -> !tablesNamedIn(List.of(text), owners.keySet()).isEmpty())
                .toList();

        claim(
                "the one class allowed to name other modules' tables names them only in statements that"
                        + " count: each begins with SELECT and carries COUNT(*), so it writes nothing and"
                        + " carries no row out, which is the bound its exception was given",
                () -> assertThat(statementsOfTheAccount)
                        .allMatch(text -> text.startsWith("SELECT ") && text.contains("COUNT(*)")));

        claim(
                "the schema names an owner for its tables, so the answer below is about real tables",
                () -> assertThat(owners).isNotEmpty());
        claim(
                "some shipped classes do name a table, so an empty answer below is not an empty scan",
                () -> assertThat(found).isPositive());
        claim(
                "no shipped class names a table that belongs to another module: a class named here reads,"
                        + " writes or deletes rows whose shape another module decides, and the check on module"
                        + " boundaries does not see it, because a statement is text and not a reference to a type",
                () -> assertThat(reachingAcross).isEmpty());
    }

    /** The tables {@code texts} name, among {@code tables}: after a statement's keyword, or as the whole text. */
    private static Set<String> tablesNamedIn(List<String> texts, Set<String> tables) {
        Set<String> named = new TreeSet<>();
        for (String text : texts) {
            if (tables.contains(text)) {
                named.add(text);
            }
            Matcher statement = TABLE_IN_A_STATEMENT.matcher(text);
            while (statement.find()) {
                if (tables.contains(statement.group(1))) {
                    named.add(statement.group(1));
                }
            }
        }
        return named;
    }

    private static Map<String, String> ownersByTable(String schema) {
        Map<String, String> owners = new TreeMap<>();
        Matcher owned = OWNED_TABLE.matcher(schema);
        while (owned.find()) {
            owners.put(owned.group(2), owned.group(1));
        }
        return owners;
    }

    private static String schema() throws IOException {
        return StreamUtils.copyToString(new ClassPathResource("schema.sql").getInputStream(), StandardCharsets.UTF_8);
    }
}
