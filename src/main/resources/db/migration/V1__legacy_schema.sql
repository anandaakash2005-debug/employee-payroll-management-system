-- Baseline shape verified from repository entities and legacy dumps.
-- Existing populated installations must explicitly baseline at version 0.
CREATE TABLE IF NOT EXISTS employees (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_type VARCHAR(31) NOT NULL,
    name VARCHAR(255) NOT NULL,
    department VARCHAR(255) NOT NULL
);
CREATE TABLE IF NOT EXISTS full_time_employees (
    id BIGINT NOT NULL PRIMARY KEY,
    monthly_salary DOUBLE NOT NULL,
    CONSTRAINT fk_full_employee FOREIGN KEY (id) REFERENCES employees(id)
);
CREATE TABLE IF NOT EXISTS part_time_employees (
    id BIGINT NOT NULL PRIMARY KEY,
    hourly_rate DOUBLE NOT NULL,
    hours_worked INT NOT NULL,
    CONSTRAINT fk_part_employee FOREIGN KEY (id) REFERENCES employees(id)
);
CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role ENUM('USER','ADMIN') NOT NULL
);
CREATE TABLE IF NOT EXISTS salary_increments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    pay_basis VARCHAR(255) NOT NULL,
    previous_rate DECIMAL(12,2) NOT NULL,
    new_rate DECIMAL(12,2) NOT NULL,
    effective_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_increment_employee FOREIGN KEY (employee_id) REFERENCES employees(id)
);
CREATE TABLE IF NOT EXISTS employee_bonuses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    bonus_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_bonus_employee FOREIGN KEY (employee_id) REFERENCES employees(id)
);
