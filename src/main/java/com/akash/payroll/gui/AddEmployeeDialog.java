package com.akash.payroll.gui;

import com.akash.payroll.dto.EmployeeResponse;
import com.akash.payroll.gui.services.EmployeeService;

import javax.swing.*;
import java.awt.*;

public class AddEmployeeDialog extends JDialog {
    private final EmployeeService employeeService = new EmployeeService();
    private final JTextField nameField = new JTextField();
    private final JTextField departmentField = new JTextField();
    private final JTextField amountField = new JTextField();
    private final JTextField extraField = new JTextField();
    private final JRadioButton fullTimeRadio = new JRadioButton("Full-Time");
    private final JRadioButton partTimeRadio = new JRadioButton("Part-Time");
    private final JButton saveButton = new JButton("Save");
    private final JButton cancelButton = new JButton("Cancel");
    private final JLabel titleLabel = new JLabel("Add Employee");
    private final JLabel extraLabel = new JLabel("Monthly Salary:");

    private final boolean editing;
    private final EmployeeResponse employee;

    public AddEmployeeDialog(Window owner, boolean fullTime, EmployeeResponse employee) {
        super(owner, ModalityType.APPLICATION_MODAL);
        setTitle(fullTime ? "Add Full-Time Employee" : "Add Part-Time Employee");
        setSize(440, 360);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(12, 12));
        this.employee = employee;
        editing = employee != null;

        ButtonGroup group = new ButtonGroup();
        group.add(fullTimeRadio);
        group.add(partTimeRadio);
        fullTimeRadio.setSelected(fullTime);
        partTimeRadio.setSelected(!fullTime);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER));
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        top.add(titleLabel);
        add(top, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; form.add(new JLabel("Employee Type:"), c);
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        typePanel.add(fullTimeRadio);
        typePanel.add(partTimeRadio);
        c.gridx = 1; form.add(typePanel, c);

        c.gridx = 0; c.gridy = 1; form.add(new JLabel("Name:"), c);
        c.gridx = 1; form.add(nameField, c);

        c.gridx = 0; c.gridy = 2; form.add(new JLabel("Department:"), c);
        c.gridx = 1; form.add(departmentField, c);

        c.gridx = 0; c.gridy = 3; form.add(extraLabel, c);
        c.gridx = 1; form.add(amountField, c);

        c.gridx = 0; c.gridy = 4; form.add(new JLabel("Additional Value:"), c);
        c.gridx = 1; form.add(extraField, c);

        add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(saveButton);
        buttons.add(cancelButton);
        add(buttons, BorderLayout.SOUTH);

        fullTimeRadio.addActionListener(e -> updateFields());
        partTimeRadio.addActionListener(e -> updateFields());
        saveButton.addActionListener(e -> saveEmployee());
        cancelButton.addActionListener(e -> dispose());
        updateFields();

        if (employee != null) {
            titleLabel.setText(editing ? "Update Employee" : "Add Employee");
            nameField.setText(employee.getName());
            departmentField.setText(employee.getDepartment());
            if (employee.getEmployeeType() != null && employee.getEmployeeType().equalsIgnoreCase("FULL_TIME")) {
                fullTimeRadio.setSelected(true);
                amountField.setText(String.valueOf(employee.getMonthlySalary()));
                extraField.setText("");
            } else {
                partTimeRadio.setSelected(true);
                amountField.setText(String.valueOf(employee.getHoursWorked()));
                extraField.setText(String.valueOf(employee.getHourlyRate()));
            }
        }
    }

    private void updateFields() {
        boolean fullTime = fullTimeRadio.isSelected();
        if (fullTime) {
            extraLabel.setText("Monthly Salary:");
            extraField.setToolTipText("Monthly salary");
        } else {
            extraLabel.setText("Hours Worked:");
            extraField.setToolTipText("Hourly rate");
        }
    }

    private void saveEmployee() {
        String name = nameField.getText().trim();
        String department = departmentField.getText().trim();

        if (name.isEmpty() || department.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name and department are required.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            boolean fullTime = fullTimeRadio.isSelected();
            if (fullTime) {
                double monthlySalary = Double.parseDouble(amountField.getText().trim());
                if (monthlySalary <= 0) {
                    throw new NumberFormatException("Monthly salary must be positive");
                }
                if (editing && employee != null) {
                    employeeService.updateFullTimeEmployee(employee.getId(), name, department, monthlySalary);
                    JOptionPane.showMessageDialog(this, "Employee updated successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    employeeService.addFullTimeEmployee(name, department, monthlySalary);
                    JOptionPane.showMessageDialog(this, "Employee saved successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            } else {
                int hoursWorked = Integer.parseInt(amountField.getText().trim());
                double hourlyRate = Double.parseDouble(extraField.getText().trim());
                if (hoursWorked <= 0 || hourlyRate <= 0) {
                    throw new NumberFormatException("Hours worked and hourly rate must be positive");
                }
                if (editing && employee != null) {
                    employeeService.updatePartTimeEmployee(employee.getId(), name, department, hoursWorked, hourlyRate);
                    JOptionPane.showMessageDialog(this, "Employee updated successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    employeeService.addPartTimeEmployee(name, department, hoursWorked, hourlyRate);
                    JOptionPane.showMessageDialog(this, "Employee saved successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            }
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid Input", JOptionPane.ERROR_MESSAGE);
        }
    }
}
