package com.akash.payroll.gui;

import com.akash.payroll.dto.EmployeeResponse;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class EmployeeTableModel extends AbstractTableModel {
    private final String[] columnNames = {"ID", "Name", "Department", "Type", "Salary", "Monthly Salary", "Hours", "Hourly Rate"};
    private final List<EmployeeResponse> employees = new ArrayList<>();

    @Override
    public int getRowCount() {
        return employees.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        EmployeeResponse employee = employees.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> employee.getId();
            case 1 -> employee.getName();
            case 2 -> employee.getDepartment();
            case 3 -> employee.getEmployeeType();
            case 4 -> employee.getSalary();
            case 5 -> employee.getMonthlySalary() == null ? "-" : employee.getMonthlySalary();
            case 6 -> employee.getHoursWorked() == null ? "-" : employee.getHoursWorked();
            case 7 -> employee.getHourlyRate() == null ? "-" : employee.getHourlyRate();
            default -> "";
        };
    }

    public void setEmployees(List<EmployeeResponse> list) {
        employees.clear();
        if (list != null) {
            employees.addAll(list);
        }
        fireTableDataChanged();
    }

    public EmployeeResponse getEmployeeAt(int row) {
        if (row < 0 || row >= employees.size()) {
            return null;
        }
        return employees.get(row);
    }

    public void clear() {
        employees.clear();
        fireTableDataChanged();
    }
}
