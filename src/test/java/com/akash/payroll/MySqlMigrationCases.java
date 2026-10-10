package com.akash.payroll;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

abstract class MySqlMigrationCases {
    abstract String url();
    abstract String username();
    abstract String password();

    Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(url(),username(),password());
        if (target != null) config.target(target);
        return config.load();
    }
    @Test void emptyDatabaseMigratesAndRerunIsNoOp() {
        assertEquals(3,flyway(null).migrate().migrationsExecuted);
        assertEquals(0,flyway(null).migrate().migrationsExecuted);
        flyway(null).validate();
    }
    @Test void baselineZeroUpgradesUnversionedPopulatedSchemaWithoutChangingUsers() throws Exception {
        try (var connection=DriverManager.getConnection(url(),username(),password()); var statement=connection.createStatement()) {
            statement.execute("CREATE TABLE employees(id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, employee_type VARCHAR(31) NOT NULL, name VARCHAR(255) NOT NULL, department VARCHAR(255) NOT NULL)");
            statement.execute("CREATE TABLE full_time_employees(id BIGINT NOT NULL PRIMARY KEY, monthly_salary DOUBLE NOT NULL, FOREIGN KEY(id) REFERENCES employees(id))");
            statement.execute("CREATE TABLE users(id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, username VARCHAR(255) NOT NULL UNIQUE, password VARCHAR(255) NOT NULL, role ENUM('USER','ADMIN') NOT NULL)");
            statement.execute("INSERT INTO employees VALUES(17,'FULL_TIME','Existing employee','IT')");
            statement.execute("INSERT INTO full_time_employees VALUES(17,12345.67)");
            statement.execute("INSERT INTO users VALUES(4,'existing-admin','preserved-test-hash','ADMIN')");
            var migration=Flyway.configure().dataSource(url(),username(),password()).baselineVersion("0").load();
            migration.baseline();
            assertEquals(3,migration.migrate().migrationsExecuted);
            try (var rows=statement.executeQuery("SELECT password,role FROM users WHERE id=4")) {
                assertTrue(rows.next()); assertEquals("preserved-test-hash",rows.getString(1)); assertEquals("ADMIN",rows.getString(2));
            }
            try (var rows=statement.executeQuery("SELECT monthly_salary FROM full_time_employees WHERE id=17")) {
                assertTrue(rows.next()); assertEquals("12345.67",rows.getBigDecimal(1).toPlainString());
            }
        }
    }
    @Test void populatedLegacyDatabasePreservesCompensationAndAudit() throws Exception {
        flyway("1").migrate();
        try (var connection=DriverManager.getConnection(url(),username(),password());
             var statement=connection.createStatement()) {
            statement.execute("INSERT INTO employees(id,employee_type,name,department) VALUES(1,'FULL_TIME','Legacy','IT')");
            statement.execute("INSERT INTO full_time_employees(id,monthly_salary) VALUES(1,60000.25)");
            statement.execute("INSERT INTO salary_increments(employee_id,pay_basis,previous_rate,new_rate,effective_date,reason) VALUES(1,'MONTHLY',50000,60000.25,'2020-01-01','Legacy review')");
            assertEquals(2,flyway(null).migrate().migrationsExecuted);
            try (var rows=statement.executeQuery("SELECT f.monthly_salary, s.status, s.created_by FROM full_time_employees f JOIN salary_increments s ON f.id=s.employee_id")) {
                assertTrue(rows.next()); assertEquals("60000.25",rows.getBigDecimal(1).toPlainString());
                assertEquals("APPLIED",rows.getString(2)); assertNull(rows.getString(3));
            }
            assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO salary_increments(employee_id,pay_basis,previous_rate,new_rate,effective_date,reason) VALUES(1,'MONTHLY',60000.25,70000,'2020-01-01','Duplicate')"));
            assertEquals(0,flyway(null).migrate().migrationsExecuted);
        }
    }
    @Test void invalidPrecisionStopsBeforeDecimalConversion() throws Exception {
        flyway("1").migrate();
        try (var connection=DriverManager.getConnection(url(),username(),password());
             var statement=connection.createStatement()) {
            statement.execute("INSERT INTO employees(id,employee_type,name,department) VALUES(1,'FULL_TIME','Legacy','IT')");
            statement.execute("INSERT INTO full_time_employees(id,monthly_salary) VALUES(1,60000.251)");
            assertThrows(org.flywaydb.core.api.FlywayException.class, () -> flyway(null).migrate());
            try (var rows=statement.executeQuery("SELECT monthly_salary FROM full_time_employees WHERE id=1")) {
                assertTrue(rows.next()); assertEquals(60000.251,rows.getDouble(1));
            }
        }
    }
}
