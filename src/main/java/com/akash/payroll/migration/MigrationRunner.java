package com.akash.payroll.migration;

import org.flywaydb.core.Flyway;

/** Explicit offline deployment utility. It never starts the web application or scheduler. */
public final class MigrationRunner {
    private MigrationRunner() {}
    public static void main(String[] args) {
        if (args.length != 1 || !java.util.Set.of("info", "baseline-reviewed-backup", "migrate", "validate").contains(args[0])) {
            throw new IllegalArgumentException("Usage: info | baseline-reviewed-backup | migrate | validate");
        }
        Flyway flyway = Flyway.configure().dataSource(required("DB_URL"),required("DB_USERNAME"),required("DB_PASSWORD"))
                .baselineVersion("0").baselineOnMigrate(false).cleanDisabled(true).load();
        switch (args[0]) {
            case "info" -> {
                for (var migration : flyway.info().all()) {
                    System.out.println(migration.getVersion() + " " + migration.getDescription() + " " + migration.getState());
                }
            }
            case "baseline-reviewed-backup" -> flyway.baseline();
            case "migrate" -> flyway.migrate();
            case "validate" -> flyway.validate();
            default -> throw new IllegalArgumentException("Unsupported command");
        }
    }
    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing environment variable: " + key);
        return value;
    }
}
