package com.akash.payroll;

import db.migration.V2__validate_legacy_data;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MigrationPreflightTests {
    @Test void validLegacyDataSurvivesAndAmbiguousOrLossyDataIsRejected() throws Exception {
        try (var connection=DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID());
             var statement=connection.createStatement()) {
            statement.execute("CREATE TABLE employees(id BIGINT, employee_type VARCHAR(31))");
            statement.execute("CREATE TABLE full_time_employees(id BIGINT, monthly_salary DOUBLE)");
            statement.execute("CREATE TABLE part_time_employees(id BIGINT, hourly_rate DOUBLE)");
            statement.execute("CREATE TABLE salary_increments(id BIGINT, employee_id BIGINT, effective_date DATE, previous_rate DECIMAL(12,2), new_rate DECIMAL(12,2), pay_basis VARCHAR(20))");
            statement.execute("CREATE TABLE employee_bonuses(id BIGINT, amount DECIMAL(12,2), status VARCHAR(20))");
            statement.execute("INSERT INTO employees VALUES(1,'FULL_TIME')");
            statement.execute("INSERT INTO full_time_employees VALUES(1,60000.25)");
            statement.execute("INSERT INTO salary_increments VALUES(1,1,'2020-01-01',50000,60000.25,'MONTHLY')");
            Context context=new Context() {
                public Configuration getConfiguration() { return null; }
                public Connection getConnection() { return connection; }
            };
            var migration=new V2__validate_legacy_data();
            assertDoesNotThrow(() -> migration.migrate(context));
            statement.execute("UPDATE full_time_employees SET monthly_salary=60000.251");
            assertThrows(SQLException.class, () -> migration.migrate(context));
            statement.execute("UPDATE full_time_employees SET monthly_salary=60000.25");
            statement.execute("INSERT INTO salary_increments VALUES(2,1,'2020-01-01',60000.25,70000,'MONTHLY')");
            assertThrows(SQLException.class, () -> migration.migrate(context));
        }
    }
}
