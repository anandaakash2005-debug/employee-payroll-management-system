package com.akash.payroll.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/** Conservative allow-list for the reviewed V1 and production dump shapes. No DDL. */
public final class LegacySchemaGuard {
    private LegacySchemaGuard() {}
    private static final Map<String, Map<String, String>> COLUMNS = Map.of(
        "employees", columns("id:bigint,employee_type:varchar(31),name:varchar(255),department:varchar(255)"),
        "full_time_employees", columns("id:bigint,monthly_salary:double"),
        "part_time_employees", columns("id:bigint,hourly_rate:double,hours_worked:int"),
        "users", columns("id:bigint,username:varchar(255),password:varchar(255),role:enum('USER';'ADMIN')"),
        "salary_increments", columns("id:bigint,employee_id:bigint,pay_basis:varchar(255)|enum('MONTHLY';'HOURLY'),previous_rate:decimal(12;2),new_rate:decimal(12;2),effective_date:date,reason:varchar(500),created_at:datetime(6)|timestamp"),
        "employee_bonuses", columns("id:bigint,employee_id:bigint,amount:decimal(12;2),bonus_date:date,reason:varchar(500),status:varchar(255)|enum('PENDING';'PAID';'CANCELLED'),created_at:datetime(6)|timestamp")
    );
    private static Map<String, String> columns(String spec) {
        Map<String, String> result = new HashMap<>();
        for (String entry : spec.split(",")) {
            String[] pair = entry.split(":", 2);
            result.put(pair[0], pair[1].replace(';', ','));
        }
        return result;
    }
    public static Set<String> tables(Connection connection) throws SQLException {
        Set<String> result = new TreeSet<>();
        try (var s = connection.createStatement(); var r = s.executeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()")) {
            while (r.next()) result.add(r.getString(1));
        }
        return result;
    }
    public static void validate(Connection connection) throws SQLException {
        Set<String> tables = tables(connection);
        tables.remove("flyway_schema_history");
        require(tables.equals(COLUMNS.keySet()), "Unreviewed or partial legacy table set; manual schema review required");
        try (var s = connection.createStatement(); var r = s.executeQuery(
                "SELECT table_name,engine,table_type FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name <> 'flyway_schema_history'")) {
            while (r.next()) require("InnoDB".equals(r.getString(2)) && "BASE TABLE".equals(r.getString(3)), "Expected InnoDB base tables");
        }
        Map<String, Set<String>> seen = new HashMap<>();
        try (var s = connection.createStatement(); var r = s.executeQuery(
                "SELECT table_name,column_name,column_type,is_nullable,extra FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name <> 'flyway_schema_history'")) {
            while (r.next()) {
                String table = r.getString(1), column = r.getString(2);
                String expected = COLUMNS.get(table).get(column);
                require(expected != null && Arrays.asList(expected.split("\\|")).contains(r.getString(3)),
                        "Unreviewed or partial legacy column: " + table + "." + column);
                require(column.equals("created_at") || "NO".equals(r.getString(4)), "Unexpected nullable legacy column: " + table + "." + column);
                boolean identity = column.equals("id") && !table.equals("full_time_employees") && !table.equals("part_time_employees");
                require(r.getString(5).equals(identity ? "auto_increment" : "")
                        || (column.equals("created_at") && r.getString(5).equals("DEFAULT_GENERATED")), "Unreviewed generated column");
                seen.computeIfAbsent(table, k -> new HashSet<>()).add(column);
            }
        }
        for (String table : tables) require(COLUMNS.get(table).keySet().equals(seen.get(table)), "Missing legacy columns in " + table);
        var metadata = connection.getMetaData();
        for (String table : tables) {
            List<String> primary = new ArrayList<>();
            try (var r = metadata.getPrimaryKeys(connection.getCatalog(), null, table)) {
                while (r.next()) primary.add(r.getString("COLUMN_NAME"));
            }
            require(primary.equals(List.of("id")), "Unexpected primary key in " + table);
            int references = 0;
            try (var r = metadata.getImportedKeys(connection.getCatalog(), null, table)) {
                while (r.next()) {
                    references++;
                    String column = table.endsWith("employees") ? "id" : "employee_id";
                    require(column.equals(r.getString("FKCOLUMN_NAME")) && "employees".equals(r.getString("PKTABLE_NAME"))
                            && "id".equals(r.getString("PKCOLUMN_NAME")) && r.getShort("KEY_SEQ") == 1
                            && connection.getCatalog().equals(r.getString("PKTABLE_CAT")), "Unreviewed foreign key in " + table);
                    for (String rule : List.of("DELETE_RULE", "UPDATE_RULE")) {
                        short value = r.getShort(rule);
                        require(value == java.sql.DatabaseMetaData.importedKeyRestrict || value == java.sql.DatabaseMetaData.importedKeyNoAction,
                                "Unreviewed cascading foreign key in " + table);
                    }
                }
            }
            require(references == (table.equals("users") || table.equals("employees") ? 0 : 1), "Missing or extra foreign key in " + table);
            boolean usernameUnique = false;
            try (var r = metadata.getIndexInfo(connection.getCatalog(), null, table, false, false)) {
                while (r.next()) {
                    String name = r.getString("INDEX_NAME"), column = r.getString("COLUMN_NAME");
                    if (name == null) continue;
                    require(!Set.of("uk_increment_employee_date", "ix_increment_due", "ix_bonus_employee_date").contains(name.toLowerCase(Locale.ROOT)), "V3 index already exists; partial migration review required");
                    if (!r.getBoolean("NON_UNIQUE") && !name.equals("PRIMARY")) {
                        require(table.equals("users") && "username".equals(column) && r.getShort("ORDINAL_POSITION") == 1, "Unreviewed unique index");
                        usernameUnique = true;
                    }
                }
            }
            if (table.equals("users")) require(usernameUnique, "Missing unique username index");
        }
        try (var s = connection.createStatement(); var r = s.executeQuery(
                "SELECT constraint_name FROM information_schema.table_constraints WHERE constraint_schema=DATABASE()")) {
            while (r.next()) require(!Set.of("ck_full_rate", "ck_part_rate", "uk_increment_employee_date", "ck_increment_status",
                    "ck_increment_amount", "ck_bonus_amount", "ck_bonus_status", "fk_payroll_employee", "uk_payroll_employee_month",
                    "ck_payroll_dates", "ck_payroll_amount").contains(r.getString(1).toLowerCase(Locale.ROOT)),
                    "V3 constraint name already exists; manual review required");
        }
        Map<String, String> checks = Map.of(
                "chk_employee_bonus_amount", "employee_bonuses:amount>0",
                "chk_salary_changed", "salary_increments:previous_rate<>new_rate",
                "chk_salary_new_rate", "salary_increments:new_rate>0",
                "chk_salary_previous_rate", "salary_increments:previous_rate>0");
        try (var s = connection.createStatement(); var r = s.executeQuery(
                "SELECT t.constraint_name,t.table_name,c.check_clause,t.enforced FROM information_schema.table_constraints t JOIN information_schema.check_constraints c ON c.constraint_schema=t.constraint_schema AND c.constraint_name=t.constraint_name WHERE t.constraint_schema=DATABASE() AND t.constraint_type='CHECK'")) {
            while (r.next()) {
                String expression = r.getString(3).replaceAll("[`()\\s]", "").toLowerCase(Locale.ROOT);
                require((r.getString(2) + ":" + expression).equals(checks.get(r.getString(1))) && "YES".equals(r.getString(4)),
                        "Unreviewed check constraint or partial V3 migration");
            }
        }
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new Refusal(message);
    }
    public static final class Refusal extends IllegalStateException {
        private Refusal(String message) { super(message); }
    }
}
