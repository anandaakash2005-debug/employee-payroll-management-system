package com.akash.payroll.gui;

import com.akash.payroll.dto.EmployeeResponse;
import com.akash.payroll.dto.PayrollSummaryResponse;
import com.akash.payroll.gui.services.EmployeeService;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

public class SummaryPanel extends JPanel {
    private final JLabel totalEmployeesLabel = new JLabel("0");
    private final JLabel fullTimeLabel = new JLabel("0");
    private final JLabel partTimeLabel = new JLabel("0");
    private final JLabel totalPayrollLabel = new JLabel("₹0.00");
    private final JLabel averageSalaryLabel = new JLabel("₹0.00");

    public SummaryPanel() {
        setLayout(new GridLayout(1, 5, 12, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(createMetricCard("Total Employees", totalEmployeesLabel));
        add(createMetricCard("Full-Time", fullTimeLabel));
        add(createMetricCard("Part-Time", partTimeLabel));
        add(createMetricCard("Total Payroll", totalPayrollLabel));
        add(createMetricCard("Average Salary", averageSalaryLabel));
    }

    private JPanel createMetricCard(String title, JLabel valueLabel) {
        JPanel panel = new JPanel();
        panel.setBackground(new Color(245, 247, 255));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(205, 214, 255), 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12))
        );
        panel.setLayout(new BorderLayout());

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLabel.setForeground(new Color(70, 82, 122));

        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);
        valueLabel.setForeground(new Color(35, 91, 255));

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(valueLabel, BorderLayout.CENTER);
        return panel;
    }

    public void setSummary(PayrollSummaryResponse summary) {
        if (summary == null) {
            totalEmployeesLabel.setText("0");
            fullTimeLabel.setText("0");
            partTimeLabel.setText("0");
            totalPayrollLabel.setText("₹0.00");
            averageSalaryLabel.setText("₹0.00");
            return;
        }

        totalEmployeesLabel.setText(String.valueOf(summary.getTotalEmployees()));
        fullTimeLabel.setText(String.valueOf(summary.getFullTimeCount()));
        partTimeLabel.setText(String.valueOf(summary.getPartTimeCount()));
        totalPayrollLabel.setText(formatCurrency(summary.getTotalPayroll()));
        averageSalaryLabel.setText(formatCurrency(summary.getAverageSalary()));
    }

    private String formatCurrency(double value) {
        return String.format(Locale.US, "₹%.2f", value);
    }
}
