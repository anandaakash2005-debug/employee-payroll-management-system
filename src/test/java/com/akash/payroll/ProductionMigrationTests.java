package com.akash.payroll;

import com.akash.payroll.migration.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic rows, with DDL extracted from the supplied backup (no real records). */
@Testcontainers(disabledWithoutDocker = true)
class ProductionMigrationTests {
    @Container static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");
    Flyway flyway;
    Connection connection;

    @BeforeEach void restoreFixture() throws Exception {
        flyway = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .baselineVersion("0").baselineOnMigrate(false).cleanDisabled(false).load();
        flyway.clean(); // Testcontainers' disposable schema only.
        connection = flyway.getConfiguration().getDataSource().getConnection();
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("migration/production-legacy.sql"));
        sql("INSERT INTO users VALUES(4,'synthetic-hash-do-not-use','ADMIN','existing-admin')");
        sql("INSERT INTO employees VALUES('FULL_TIME',17,'IT','Full time'),('PART_TIME',18,'HR','Part time')");
        sql("INSERT INTO full_time_employees VALUES(60000.25,17)");
        sql("INSERT INTO part_time_employees VALUES(125.50,12,18)");
        sql("INSERT INTO salary_increments VALUES(9,17,'MONTHLY',50000,60000.25,'2020-01-01','Review','2020-01-02 12:34:56')");
        sql("INSERT INTO employee_bonuses VALUES(6,17,100.50,'2020-02-01','Bonus','PAID','2020-01-03 12:34:56')");
    }
    @AfterEach void close() throws Exception { if (connection != null) connection.close(); }
    void sql(String sql) throws Exception { try (var s = connection.createStatement()) { s.execute(sql); } }
    void run(String command) throws Exception { MigrationRunner.execute(flyway, command, ""); }
    void baseline() throws Exception { MigrationRunner.execute(flyway, "baseline-reviewed-backup", MigrationRunner.BASELINE_CONFIRMATION); }
    void migrate() throws Exception { MigrationRunner.execute(flyway, "migrate", MigrationRunner.MIGRATE_CONFIRMATION); }
    boolean history() throws Exception { return LegacySchemaGuard.tables(connection).contains("flyway_schema_history"); }

    @Test void completeProductionSequencePreservesEveryLegacyValueAndValidatesHibernate() throws Exception {
        Map<String, List<String>> columns = new TreeMap<>();
        try (var s = connection.createStatement(); var r = s.executeQuery("SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY table_name,ordinal_position")) {
            while (r.next()) columns.computeIfAbsent(r.getString(1), k -> new ArrayList<>()).add(r.getString(2));
        }
        var before = snapshot(columns);
        run("info"); run("preflight");
        assertFalse(history());
        baseline(); migrate(); run("validate"); run("validate-schema");
        assertEquals(before, snapshot(columns));
        try (var s = connection.createStatement(); var r = s.executeQuery("SELECT status,created_by,applied_at,cancelled_at,cancelled_by FROM salary_increments")) {
            assertTrue(r.next()); assertEquals("APPLIED", r.getString(1));
            for (int i = 2; i <= 5; i++) assertNull(r.getObject(i));
        }
        try (var s = connection.createStatement(); var r = s.executeQuery("SELECT created_by FROM employee_bonuses")) {
            assertTrue(r.next()); assertNull(r.getObject(1));
        }
        migrate(); // Success is a no-op, with checksums verified.
        assertEquals(before, snapshot(columns));
    }
    private Map<String, List<String>> snapshot(Map<String, List<String>> columns) throws Exception {
        Map<String, List<String>> result = new TreeMap<>();
        for (var entry : columns.entrySet()) {
            List<String> values = new ArrayList<>();
            try (var s = connection.createStatement(); var r = s.executeQuery("SELECT " + String.join(",", entry.getValue()) + " FROM " + entry.getKey() + " ORDER BY id")) {
                while (r.next()) for (int i = 1; i <= entry.getValue().size(); i++) {
                    String column = entry.getValue().get(i - 1);
                    values.add(column.equals("monthly_salary") || column.equals("hourly_rate")
                            ? r.getBigDecimal(i).stripTrailingZeros().toPlainString() : r.getString(i));
                }
            }
            result.put(entry.getKey(), values);
        }
        return result;
    }
    @Test void missingBonusCreatedByReproducesFatalValidationError() throws Exception {
        baseline(); migrate();
        sql("ALTER TABLE employee_bonuses DROP COLUMN created_by");
        Exception failure = assertThrows(Exception.class, () -> run("validate-schema"));
        StringBuilder messages = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) messages.append(t.getMessage());
        assertTrue(messages.toString().contains("missing column [created_by] in table [employee_bonuses]"));
    }
    @Test void noImplicitBaselineAndExplicitConfirmationRequired() throws Exception {
        assertThrows(IllegalStateException.class, () -> migrate());
        assertThrows(IllegalStateException.class, () -> run("baseline-reviewed-backup"));
        assertFalse(history());
        baseline();
        assertThrows(IllegalStateException.class, () -> baseline());
        assertThrows(IllegalStateException.class, () -> run("migrate"));
        assertThrows(IllegalStateException.class, () -> run("validate"));
    }
    @ParameterizedTest
    @ValueSource(strings = {
        "ALTER TABLE employees ADD active BOOLEAN NOT NULL DEFAULT TRUE",
        "ALTER TABLE employee_bonuses ADD created_by VARCHAR(255)",
        "ALTER TABLE full_time_employees MODIFY monthly_salary DECIMAL(12,2) NOT NULL",
        "CREATE TABLE payroll_records(id BIGINT)",
        "CREATE INDEX IX_INCREMENT_DUE ON salary_increments(effective_date)",
        "UPDATE salary_increments SET effective_date='2999-01-01'",
        "UPDATE full_time_employees SET monthly_salary=60000.251",
        "UPDATE part_time_employees SET hours_worked=-1",
        "UPDATE employees SET employee_type='UNKNOWN' WHERE id=17",
        "INSERT INTO salary_increments SELECT 10,employee_id,pay_basis,previous_rate,new_rate,effective_date,reason,created_at FROM salary_increments"
    })
    void unsafeLegacyStateRefusedBeforeHistoryWrite(String mutation) throws Exception {
        sql(mutation);
        assertThrows(Exception.class, () -> baseline());
        assertFalse(history());
    }
    @Test void partialV3WithApparentlySuccessfulV2HistoryIsRefused() throws Exception {
        baseline();
        Flyway.configure().configuration(flyway.getConfiguration()).target("2").load().migrate();
        sql("ALTER TABLE employees ADD active BOOLEAN NOT NULL DEFAULT TRUE");
        assertThrows(IllegalStateException.class, () -> migrate());
    }
    @Test void failedOrEmptyOrWrongBaselineHistoryIsRefused() throws Exception {
        baseline();
        sql("UPDATE flyway_schema_history SET success=0");
        assertThrows(IllegalStateException.class, () -> migrate());
        sql("UPDATE flyway_schema_history SET success=1,version='1'");
        assertThrows(IllegalStateException.class, () -> migrate());
        sql("DELETE FROM flyway_schema_history");
        assertThrows(IllegalStateException.class, () -> migrate());
    }
    @Test void emptyDatabaseCannotBeBaselinedButCanMigrate() throws Exception {
        connection.close(); flyway.clean();
        connection = flyway.getConfiguration().getDataSource().getConnection();
        assertThrows(IllegalStateException.class, () -> baseline());
        migrate(); run("validate-schema");
    }
}
