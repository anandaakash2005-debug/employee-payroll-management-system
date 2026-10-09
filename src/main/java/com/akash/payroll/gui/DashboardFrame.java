package com.akash.payroll.gui;

import com.akash.payroll.dto.EmployeeResponse;
import com.akash.payroll.dto.PayrollSummaryResponse;
import com.akash.payroll.gui.services.EmployeeService;

import javax.swing.*;
import java.awt.*;

public class DashboardFrame extends JFrame {
    private final EmployeeService employeeService = new EmployeeService();
    private final JLabel welcomeLabel = new JLabel("Welcome");
    private final SummaryPanel summaryPanel = new SummaryPanel();

    public DashboardFrame() {
        super("Employee Payroll Management System");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1200, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(12, 12));

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                confirmExit();
            }
        });

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(54, 87, 197));
        header.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        welcomeLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        welcomeLabel.setForeground(Color.WHITE);
        header.add(welcomeLabel, BorderLayout.WEST);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actionPanel.setOpaque(false);
        JButton refreshButton = new JButton("Refresh");
        JButton logoutButton = new JButton("Logout");
        JButton exitButton = new JButton("Exit");
        actionPanel.add(refreshButton);
        actionPanel.add(logoutButton);
        actionPanel.add(exitButton);
        header.add(actionPanel, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        center.add(summaryPanel, BorderLayout.NORTH);

        JPanel buttonsPanel = new JPanel(new GridLayout(2, 4, 18, 18));
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buttonsPanel.add(createActionButton("Employee Management", this::openEmployeeManagement));
        buttonsPanel.add(createActionButton("Add Full-Time Employee", () -> openEmployeeForm(true, null)));
        buttonsPanel.add(createActionButton("Add Part-Time Employee", () -> openEmployeeForm(false, null)));
        buttonsPanel.add(createActionButton("Search Employee", this::openSearchWindow));
        buttonsPanel.add(createActionButton("Payroll Summary", this::showPayrollSummary));
        buttonsPanel.add(createActionButton("Refresh", this::refreshDashboard));
        buttonsPanel.add(createActionButton("Logout", this::logout));
        buttonsPanel.add(createActionButton("Exit", this::confirmExit));
        center.add(buttonsPanel, BorderLayout.CENTER);

        add(center, BorderLayout.CENTER);

        refreshButton.addActionListener(e -> refreshDashboard());
        logoutButton.addActionListener(e -> logout());
        exitButton.addActionListener(e -> confirmExit());

        refreshDashboard();
    }

    private JButton createActionButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setFocusPainted(false);
        button.addActionListener(e -> action.run());
        return button;
    }

    private void updateWelcomeText() {
        String user = SessionManager.getCurrentUser();
        welcomeLabel.setText(user == null || user.isBlank() ? "Welcome" : "Welcome, " + user + "!");
    }

    private void refreshDashboard() {
        updateWelcomeText();
        SwingWorker<PayrollSummaryResponse, Void> worker = new SwingWorker<>() {
            @Override
            protected PayrollSummaryResponse doInBackground() throws Exception {
                return employeeService.getSummary();
            }

            @Override
            protected void done() {
                try {
                    summaryPanel.setSummary(get());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(DashboardFrame.this, ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage(), "Summary load failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void openEmployeeManagement() {
        EmployeeManagementFrame frame = new EmployeeManagementFrame(this);
        frame.setVisible(true);
    }

    private void openEmployeeForm(boolean fullTime, EmployeeResponse employee) {
        AddEmployeeDialog dialog = new AddEmployeeDialog(this, fullTime, employee);
        dialog.setVisible(true);
    }

    private void openSearchWindow() {
        String query = JOptionPane.showInputDialog(this, "Search employee by name:", "Search Employee", JOptionPane.PLAIN_MESSAGE);
        if (query == null) {
            return;
        }

        EmployeeManagementFrame managementFrame = new EmployeeManagementFrame(this, query.trim());
        managementFrame.setVisible(true);
    }

    private void showPayrollSummary() {
        SwingWorker<PayrollSummaryResponse, Void> worker = new SwingWorker<>() {
            @Override
            protected PayrollSummaryResponse doInBackground() throws Exception {
                return employeeService.getSummary();
            }

            @Override
            protected void done() {
                try {
                    PayrollSummaryResponse summary = get();
                    JOptionPane.showMessageDialog(DashboardFrame.this,
                            "Total Employees: " + summary.getTotalEmployees() + "\n"
                                    + "Full-Time: " + summary.getFullTimeCount() + "\n"
                                    + "Part-Time: " + summary.getPartTimeCount() + "\n"
                                    + "Total Payroll: ₹" + summary.getTotalPayroll() + "\n"
                                    + "Average Salary: ₹" + summary.getAverageSalary(),
                            "Payroll Summary",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(DashboardFrame.this, ex.getMessage(), "Summary failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void logout() {
        int choice = JOptionPane.showConfirmDialog(this, "Do you want to logout?", "Logout", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            SessionManager.logout();
            dispose();
            new LoginFrame().setVisible(true);
        }
    }

    private void confirmExit() {
        int choice = JOptionPane.showConfirmDialog(this, "Are you sure you want to exit?", "Exit", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            System.exit(0);
        }
    }
}
