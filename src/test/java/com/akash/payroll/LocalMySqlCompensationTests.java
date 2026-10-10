package com.akash.payroll;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/** Fixed disposable loopback database, independent of production environment variables. */
@EnabledIfEnvironmentVariable(named = "PAYROLL_LOCAL_MYSQL_TESTS", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:13317/payroll_behavior_test?useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=payroll_test", "spring.datasource.password=isolated-test-only",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect"})
class LocalMySqlCompensationTests extends CompensationIntegrationTests {}
