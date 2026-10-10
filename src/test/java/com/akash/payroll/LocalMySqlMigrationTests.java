package com.akash.payroll;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Explicit opt-in, hard-coded loopback port and disposable schema; never uses DB_URL. */
@EnabledIfEnvironmentVariable(named = "PAYROLL_LOCAL_MYSQL_TESTS", matches = "true")
class LocalMySqlMigrationTests extends MySqlMigrationCases {
    String url() { return "jdbc:mysql://127.0.0.1:13317/payroll_migration_test?useSSL=false&allowPublicKeyRetrieval=true"; }
    String username() { return "payroll_test"; }
    String password() { return "isolated-test-only"; }
    @BeforeEach void resetDisposableSchema() {
        Flyway.configure().dataSource(url(),username(),password()).cleanDisabled(false).load().clean();
    }
}
