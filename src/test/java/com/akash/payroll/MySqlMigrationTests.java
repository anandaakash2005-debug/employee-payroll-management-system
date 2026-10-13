package com.akash.payroll;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class MySqlMigrationTests extends MySqlMigrationCases {
    @Container final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");
    String url() { return mysql.getJdbcUrl(); }
    String username() { return mysql.getUsername(); }
    String password() { return mysql.getPassword(); }
}
