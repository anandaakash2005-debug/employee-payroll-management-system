package com.akash.payroll.migration;

import org.flywaydb.core.Flyway;
import db.migration.V2__validate_legacy_data;
import java.sql.Connection;
import java.util.Set;
import static com.akash.payroll.migration.LegacySchemaGuard.require;

/** Explicit offline deployment utility. It never starts the web application or scheduler. */
public final class MigrationRunner {
    private MigrationRunner() {}
    public static final String BASELINE_CONFIRMATION = "--confirm-reviewed-backup-and-stopped-writers";
    public static final String MIGRATE_CONFIRMATION = "--confirm-stopped-writers";

    public static void main(String[] args) {
        // Driver errors/logs can contain connection strings or row values. Never print them.
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
                .setLevel(ch.qos.logback.classic.Level.OFF);
        try {
            require(args.length >= 1 && args.length <= 2, "Usage: info | preflight | baseline-reviewed-backup <confirmation> | migrate <confirmation> | validate | validate-schema");
            String url = required("DB_URL");
            require(url.startsWith("jdbc:mysql://") && !url.matches("(?i).*[?&](user|username|password)=.*")
                    && !url.substring(13).split("/", 2)[0].contains("@"), "Use a MySQL JDBC URL without embedded credentials");
            Flyway flyway = Flyway.configure().dataSource(url, required("DB_USERNAME"), required("DB_PASSWORD"))
                    .baselineVersion("0").baselineOnMigrate(false).cleanDisabled(true).outOfOrder(false).load();
            execute(flyway, args[0], args.length == 2 ? args[1] : "");
            System.out.println("Completed: " + args[0]);
        } catch (LegacySchemaGuard.Refusal e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Operation refused or failed. Stop; review schema, history and legacy data locally. No automatic repair. Connection and row details suppressed.");
            System.exit(1);
        }
    }

    public static void execute(Flyway flyway, String command, String confirmation) throws Exception {
        require(Set.of("info", "preflight", "baseline-reviewed-backup", "migrate", "validate", "validate-schema").contains(command), "Unknown command");
        if (command.equals("baseline-reviewed-backup")) require(BASELINE_CONFIRMATION.equals(confirmation), "Explicit reviewed-backup and stopped-writers confirmation required");
        else if (command.equals("migrate")) require(MIGRATE_CONFIRMATION.equals(confirmation), "Explicit stopped-writers confirmation required");
        else require(confirmation.isEmpty(), "Unexpected argument");
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            // Serializes this utility; maintenance mode must separately stop application writers.
            try (var s = connection.createStatement(); var r = s.executeQuery("SELECT GET_LOCK('payroll-schema-migration',0)")) {
                require(r.next() && r.getInt(1) == 1, "Another migration utility is running; stop and review");
            }
            Set<String> tables = LegacySchemaGuard.tables(connection);
            boolean history = tables.contains("flyway_schema_history");
            if (command.equals("info")) {
                System.out.println("Flyway history: " + (history ? "present" : "absent"));
                for (var migration : flyway.info().all()) System.out.println(migration.getVersion() + " " + migration.getState());
                return;
            }
            if (command.equals("baseline-reviewed-backup")) {
                require(!history && !tables.isEmpty(), "Baseline requires a nonempty database with no Flyway history");
                preflight(connection);
                flyway.baseline();
                return;
            }
            int version = history ? version(connection) : 0;
            if (history) {
                // Pending versions are expected during preflight; all applied checksums still validate.
                if (version < 3) Flyway.configure().configuration(flyway.getConfiguration())
                        .ignoreMigrationPatterns("*:pending").load().validate();
                else flyway.validate();
            }
            if (command.equals("validate") || command.equals("validate-schema")) {
                require(history && version == 3, "Validation requires successful V1, V2 and V3 history");
                if (command.equals("validate-schema")) SchemaValidator.validate(flyway.getConfiguration().getDataSource());
                return;
            }
            if (version < 3 && !tables.isEmpty()) preflight(connection);
            if (command.equals("preflight")) {
                if (version == 3) SchemaValidator.validate(flyway.getConfiguration().getDataSource());
                return;
            }
            require(history || tables.isEmpty(), "Nonempty unversioned database: review backup and explicitly baseline at version 0 first");
            flyway.migrate();
        }
    }

    private static void preflight(Connection connection) throws Exception {
        LegacySchemaGuard.validate(connection);
        new V2__validate_legacy_data().validate(connection);
    }

    private static int version(Connection connection) throws Exception {
        int version = 0;
        boolean any = false;
        try (var s = connection.createStatement(); var r = s.executeQuery(
                "SELECT version,type,success FROM flyway_schema_history ORDER BY installed_rank")) {
            while (r.next()) {
                require(r.getBoolean(3), "Failed migration history: manual recovery review required");
                String value = r.getString(1), type = r.getString(2);
                if ("BASELINE".equals(type)) require(!any && "0".equals(value), "Only an explicit version 0 baseline is supported");
                else {
                    require(String.valueOf(version + 1).equals(value) && version < 3
                            && ("SQL".equals(type) || "JDBC".equals(type)), "Missing, unexpected or out-of-order migration history");
                    version++;
                }
                any = true;
            }
        }
        require(any, "Empty history table: manual recovery review required");
        return version;
    }
    private static String required(String key) {
        String value = System.getenv(key);
        require(value != null && !value.isBlank(), "Missing environment variable: " + key);
        return value;
    }
}
