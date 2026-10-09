package com.akash.payroll.gui;

import com.akash.payroll.dto.EmployeeResponse;
import com.akash.payroll.gui.services.EmployeeService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class EmployeeManagementFrame extends JFrame {
    private final EmployeeService employeeService = new EmployeeService();
    private final EmployeeTableModel tableModel = new EmployeeTableModel();
    private final JTable employeeTable = new JTable(tableModel);
    private final JTextField searchField = new JTextField(18);
    private final JFrame dashboardFrame;

    public EmployeeManagementFrame(JFrame dashboardFrame) {
        this(dashboardFrame, "");
    }

    public EmployeeManagementFrame(JFrame dashboardFrame, String initialSearch) {
        super("Employee Management");
        this.dashboardFrame = dashboardFrame;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1100, 650);
        setLocationRelativeTo(dashboardFrame);
        setLayout(new BorderLayout(12, 12));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        top.add(new JLabel("Search by name:"));
        top.add(searchField);
        JButton searchButton = new JButton("Search");
        JButton clearButton = new JButton("Clear");
        JButton refreshButton = new JButton("Refresh");
        JButton addButton = new JButton("Add");
        JButton viewButton = new JButton("View");
        JButton updateButton = new JButton("Update");
        JButton deleteButton = new JButton("Delete");
        JButton backButton = new JButton("Back");

        top.add(searchButton);
        top.add(clearButton);
        top.add(refreshButton);
        top.add(addButton);
        top.add(viewButton);
        top.add(updateButton);
        top.add(deleteButton);
        top.add(backButton);

        add(top, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(employeeTable);
        add(scrollPane, BorderLayout.CENTER);

        searchButton.addActionListener(e -> searchEmployees());
        clearButton.addActionListener(e -> clearSearch());
        refreshButton.addActionListener(e -> loadEmployees());
        addButton.addActionListener(e -> openAddForm());
        viewButton.addActionListener(e -> viewSelectedEmployee());
        updateButton.addActionListener(e -> updateSelectedEmployee());
        deleteButton.addActionListener(e -> deleteSelectedEmployee());
        backButton.addActionListener(e -> dispose());

        String query = initialSearch == null ? "" : initialSearch.trim();
        if (query.isEmpty()) {
            loadEmployees();
        } else {
            searchField.setText(query);
            SwingUtilities.invokeLater(this::searchEmployees);
        }
    }

    private void loadEmployees() {
        SwingWorker<List<EmployeeResponse>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<EmployeeResponse> doInBackground() throws Exception {
                return employeeService.getAllEmployees();
            }

            @Override
            protected void done() {
                try {
                    tableModel.setEmployees(get());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(EmployeeManagementFrame.this, ex.getMessage(), "Load failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void searchEmployees() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            loadEmployees();
            return;
        }

        SwingWorker<List<EmployeeResponse>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<EmployeeResponse> doInBackground() throws Exception {
                return employeeService.searchByName(query);
            }

            @Override
            protected void done() {
                try {
                    List<EmployeeResponse> results = get();
                    if (results == null || results.isEmpty()) {
                        JOptionPane.showMessageDialog(EmployeeManagementFrame.this, "No employee found.", "Search Results", JOptionPane.INFORMATION_MESSAGE);
                        tableModel.clear();
                        return;
                    }
                    tableModel.setEmployees(results);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(EmployeeManagementFrame.this, ex.getMessage(), "Search failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void clearSearch() {
        searchField.setText("");
        loadEmployees();
    }

    private void openAddForm() {
        AddEmployeeDialog dialog = new AddEmployeeDialog(this, true, null);
        dialog.setVisible(true);
        loadEmployees();
    }

    private void viewSelectedEmployee() {
        EmployeeResponse employee = getSelectedEmployee();
        if (employee == null) {
            JOptionPane.showMessageDialog(this, "Please select an employee.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append("ID: ").append(employee.getId()).append("\n");
        message.append("Name: ").append(employee.getName()).append("\n");
        message.append("Department: ").append(employee.getDepartment()).append("\n");
        message.append("Type: ").append(employee.getEmployeeType()).append("\n");
        message.append("Salary: ").append(employee.getSalary());
        JOptionPane.showMessageDialog(this, message.toString(), "Employee Details", JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateSelectedEmployee() {
        EmployeeResponse employee = getSelectedEmployee();
        if (employee == null) {
            JOptionPane.showMessageDialog(this, "Please select an employee to update.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        AddEmployeeDialog dialog = new AddEmployeeDialog(this, "FULL_TIME".equalsIgnoreCase(employee.getEmployeeType()), employee);
        dialog.setVisible(true);
        loadEmployees();
    }

    private void deleteSelectedEmployee() {
        EmployeeResponse employee = getSelectedEmployee();
        if (employee == null) {
            JOptionPane.showMessageDialog(this, "Please select an employee to delete.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this employee?", "Delete Employee", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                employeeService.deleteEmployee(employee.getId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(EmployeeManagementFrame.this, "Employee deleted successfully.", "Delete success", JOptionPane.INFORMATION_MESSAGE);
                    loadEmployees();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(EmployeeManagementFrame.this, ex.getMessage(), "Delete failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private EmployeeResponse getSelectedEmployee() {
        int row = employeeTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return tableModel.getEmployeeAt(row);
    }
}
