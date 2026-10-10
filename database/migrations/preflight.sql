-- READ ONLY. Run against a restored backup first; investigate every returned row.
-- Never use the legacy repository dumps to initialize production: they contain destructive statements.
SELECT id FROM full_time_employees
 WHERE monthly_salary <= 0 OR monthly_salary >= 10000000000
    OR ABS(monthly_salary - ROUND(monthly_salary, 2)) > 0.000001;
SELECT id FROM part_time_employees
 WHERE hourly_rate <= 0 OR hourly_rate >= 10000000000
    OR ABS(hourly_rate - ROUND(hourly_rate, 2)) > 0.000001 OR hours_worked < 0;
-- Run the following only when these tables already exist.
SELECT employee_id, effective_date, COUNT(*) FROM salary_increments
 GROUP BY employee_id, effective_date HAVING COUNT(*) > 1;
SELECT id FROM salary_increments
 WHERE previous_rate <= 0 OR new_rate <= previous_rate OR effective_date > CURRENT_DATE
    OR reason IS NULL OR TRIM(reason) = '';
SELECT id FROM employee_bonuses
 WHERE amount <= 0 OR status NOT IN ('PENDING','PAID','CANCELLED');
-- Inspect actual column types, nullability, foreign keys and indexes before baselining.
SELECT table_name, column_name, column_type, is_nullable
 FROM information_schema.columns WHERE table_schema = DATABASE()
 ORDER BY table_name, ordinal_position;
