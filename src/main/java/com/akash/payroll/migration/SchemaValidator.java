package com.akash.payroll.migration;

import com.akash.payroll.model.*;
import javax.sql.DataSource;
import org.hibernate.cfg.Configuration;

/** Hibernate validation without starting application writers. */
public final class SchemaValidator {
    private SchemaValidator() {}
    public static void validate(DataSource dataSource) {
        Configuration configuration = new Configuration();
        for (Class<?> entity : new Class<?>[]{Employee.class, FullTimeEmployee.class, PartTimeEmployee.class,
                User.class, SalaryIncrement.class, EmployeeBonus.class, PayrollRecord.class}) {
            configuration.addAnnotatedClass(entity);
        }
        configuration.getProperties().put("hibernate.connection.datasource", dataSource);
        configuration.setProperty("hibernate.hbm2ddl.auto", "validate");
        configuration.setPhysicalNamingStrategy(new org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy());
        configuration.setProperty("hibernate.jdbc.time_zone", "UTC");
        try (var factory = configuration.buildSessionFactory()) {
            // Building the factory runs schema validation, without any application data writes.
        }
    }
}
