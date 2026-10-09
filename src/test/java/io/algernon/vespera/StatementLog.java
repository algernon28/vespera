package io.algernon.vespera;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * A template whose connections keep, in the order they come, the text of every statement they prepare or
 * run, whichever method of the template issued it and whether or not it went through a callback of its own
 * on the connection (ADR-211, Tests).
 *
 * <p>A test hands the class under test this template, and its ledger one built on the same, and reads back
 * what was asked of the database and in which order. It can put notes of its own among the statements, so a
 * call the class makes on something the test handed it is placed among the statements around it.
 *
 * <p>Every method of a connection goes to the connection it wraps, {@code unwrap} included, so a statement
 * SQLite counts finds the driver's own connection beneath it as it always does. It names nothing a module
 * owns, so each module's test can use it.
 */
public final class StatementLog {

    private final List<String> said = Collections.synchronizedList(new ArrayList<>());
    private final JdbcTemplate jdbcTemplate;

    /** A template over {@code dataSource}, keeping what each of its connections is asked. */
    public StatementLog(DataSource dataSource) {
        DataSource keeping = new DelegatingDataSource(dataSource) {
            @Override
            public Connection getConnection() throws SQLException {
                return keeping(super.getConnection());
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return keeping(super.getConnection(username, password));
            }
        };
        this.jdbcTemplate = new JdbcTemplate(keeping);
    }

    /** The template to hand the class under test. */
    public JdbcTemplate jdbcTemplate() {
        return jdbcTemplate;
    }

    /** Every statement's text and every note, in the order they came. */
    public List<String> said() {
        synchronized (said) {
            return List.copyOf(said);
        }
    }

    /** Puts {@code note} among the statements, where it falls in time. */
    public void note(String note) {
        said.add(note);
    }

    /** Forgets everything kept so far. */
    public void clear() {
        said.clear();
    }

    private Connection keeping(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                StatementLog.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                    if (method.getName().startsWith("prepare") && arguments != null && arguments[0] instanceof String sql) {
                        said.add(sql);
                    }
                    Object answer = invoke(method, connection, arguments);
                    if (answer instanceof Statement statement && !(answer instanceof PreparedStatement)) {
                        return keeping(statement);
                    }
                    return answer;
                });
    }

    private Statement keeping(Statement statement) {
        return (Statement) Proxy.newProxyInstance(
                StatementLog.class.getClassLoader(), new Class<?>[] {Statement.class}, (proxy, method, arguments) -> {
                    if ((method.getName().startsWith("execute") || method.getName().equals("addBatch"))
                            && arguments != null
                            && arguments.length > 0
                            && arguments[0] instanceof String sql) {
                        said.add(sql);
                    }
                    return invoke(method, statement, arguments);
                });
    }

    private static Object invoke(Method method, Object target, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
