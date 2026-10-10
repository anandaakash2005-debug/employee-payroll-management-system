-- V2 validates legacy financial data before this schema conversion.
-- MySQL DDL implicitly commits. Stop writers and review any partial failure manually.
ALTER TABLE employees ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE full_time_employees MODIFY monthly_salary DECIMAL(12,2) NOT NULL;
ALTER TABLE part_time_employees MODIFY hourly_rate DECIMAL(12,2) NOT NULL;
ALTER TABLE full_time_employees ADD CONSTRAINT ck_full_rate CHECK (monthly_salary > 0);
ALTER TABLE part_time_employees ADD CONSTRAINT ck_part_rate CHECK (hourly_rate > 0);

-- Legacy increments were immediately applied. Never label them as scheduled or replay them.
ALTER TABLE salary_increments
    MODIFY pay_basis VARCHAR(255) NOT NULL,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPLIED',
    ADD COLUMN created_by VARCHAR(255) NULL,
    ADD COLUMN applied_at DATETIME(6) NULL,
    ADD COLUMN cancelled_at DATETIME(6) NULL,
    ADD COLUMN cancelled_by VARCHAR(255) NULL,
    ADD CONSTRAINT uk_increment_employee_date UNIQUE (employee_id, effective_date),
    ADD CONSTRAINT ck_increment_status CHECK (status IN ('SCHEDULED','APPLIED','CANCELLED')),
    ADD CONSTRAINT ck_increment_amount CHECK (new_rate > 0 AND previous_rate > 0),
    ADD INDEX ix_increment_due (status, effective_date, employee_id);
ALTER TABLE employee_bonuses
    MODIFY status VARCHAR(255) NOT NULL,
    ADD COLUMN created_by VARCHAR(255) NULL,
    ADD INDEX ix_bonus_employee_date (employee_id, bonus_date),
    ADD CONSTRAINT ck_bonus_amount CHECK (amount > 0),
    ADD CONSTRAINT ck_bonus_status CHECK (status IN ('PENDING','PAID','CANCELLED'));

CREATE TABLE payroll_records (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    employee_name VARCHAR(255) NOT NULL,
    department VARCHAR(255) NOT NULL,
    pay_basis VARCHAR(20) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    base_pay DECIMAL(16,2) NOT NULL,
    finalized_at DATETIME(6) NOT NULL,
    finalized_by VARCHAR(255) NOT NULL,
    calculation_details TEXT NOT NULL,
    CONSTRAINT fk_payroll_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT uk_payroll_employee_month UNIQUE (employee_id, period_start),
    CONSTRAINT ck_payroll_dates CHECK (period_end >= period_start),
    CONSTRAINT ck_payroll_amount CHECK (base_pay >= 0),
    INDEX ix_payroll_employee_end (employee_id, period_end)
);
