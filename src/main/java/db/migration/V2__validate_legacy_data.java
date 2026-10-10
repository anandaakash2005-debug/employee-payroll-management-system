package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

/** Fail closed rather than rounding, deleting or repairing ambiguous legacy financial data. */
public class V2__validate_legacy_data extends BaseJavaMigration {
    @Override
    public Integer getChecksum() { return 1; }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        checkMoney(connection, "SELECT monthly_salary FROM full_time_employees");
        checkMoney(connection, "SELECT hourly_rate FROM part_time_employees");
        rejectRows(connection, "SELECT employee_id FROM salary_increments GROUP BY employee_id, effective_date HAVING COUNT(*) > 1",
                "Duplicate increment dates require manual review");
        rejectRows(connection, "SELECT id FROM salary_increments WHERE previous_rate <= 0 OR new_rate <= previous_rate",
                "Invalid legacy increment requires manual review");
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of(
                System.getenv().getOrDefault("PAYROLL_TIMEZONE", "Asia/Kolkata")));
        try (var statement = connection.prepareStatement("SELECT id FROM salary_increments WHERE effective_date > ?")) {
            statement.setDate(1, java.sql.Date.valueOf(today));
            try (var rows = statement.executeQuery()) {
                if (rows.next()) fail("Prematurely applied legacy increment requires manual review");
            }
        }
        rejectRows(connection, "SELECT id FROM employee_bonuses WHERE amount <= 0 OR status NOT IN ('PENDING','PAID','CANCELLED')",
                "Invalid legacy bonus requires manual review");
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT s.employee_id, s.previous_rate, s.new_rate, s.pay_basis, e.employee_type, " +
                "COALESCE(f.monthly_salary, p.hourly_rate) AS current_rate " +
                "FROM salary_increments s JOIN employees e ON e.id=s.employee_id " +
                "LEFT JOIN full_time_employees f ON f.id=e.id LEFT JOIN part_time_employees p ON p.id=e.id " +
                "ORDER BY s.employee_id, s.effective_date")) {
            Long previousEmployee = null;
            BigDecimal previousNew = null, current = null;
            while (rows.next()) {
                long id = rows.getLong("employee_id");
                if (previousEmployee != null && previousEmployee != id && previousNew.compareTo(current) != 0) fail("Legacy history does not match current compensation");
                if (previousEmployee != null && previousEmployee == id && previousNew.compareTo(rows.getBigDecimal("previous_rate")) != 0) fail("Legacy salary history is not chronological");
                String expected = "FULL_TIME".equals(rows.getString("employee_type")) ? "MONTHLY" : "HOURLY";
                if (!expected.equals(rows.getString("pay_basis"))) fail("Legacy pay basis does not match employee type");
                previousEmployee = id;
                previousNew = rows.getBigDecimal("new_rate");
                current = rows.getBigDecimal("current_rate");
                if (current == null) fail("Missing employee compensation");
            }
            if (previousEmployee != null && previousNew.compareTo(current) != 0) fail("Legacy history does not match current compensation");
        }
    }

    private void checkMoney(Connection connection, String query) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) {
            while (rows.next()) {
                BigDecimal value = rows.getBigDecimal(1);
                if (value == null || value.signum() <= 0 || value.compareTo(new BigDecimal("9999999999.99")) > 0
                        || value.stripTrailingZeros().scale() > 2) fail("Legacy compensation cannot convert losslessly to DECIMAL(12,2)");
            }
        }
    }

    private void rejectRows(Connection connection, String query, String message) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) {
            if (rows.next()) fail(message);
        }
    }

    private void fail(String message) throws SQLException { throw new SQLException(message); }
}
