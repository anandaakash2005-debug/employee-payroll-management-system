package com.akash.payroll.gui;

import com.akash.payroll.gui.models.AuthResponse;
import com.akash.payroll.gui.services.AuthService;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class LoginFrame extends JFrame {
    private final JTextField usernameField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JButton loginButton = new JButton("Login");
    private final JButton registerButton = new JButton("Register");
    private final JButton exitButton = new JButton("Exit");
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);

    private final AuthService authService = new AuthService();

    public LoginFrame() {
        super("Employee Payroll Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(420, 420);
        setLocationRelativeTo(null);
        setResizable(false);
        setLayout(new BorderLayout(18, 18));

        JPanel main = new JPanel(new GridBagLayout());
        main.setBackground(new Color(245, 247, 255));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10, 10, 10, 10);
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Payroll Login", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setForeground(new Color(51, 71, 160));
        c.gridwidth = 2;
        c.gridx = 0;
        c.gridy = 0;
        main.add(title, c);

        JLabel usernameLabel = new JLabel("Username:");
        c.gridwidth = 1;
        c.gridy = 1;
        c.gridx = 0;
        main.add(usernameLabel, c);

        usernameField.setColumns(18);
        c.gridx = 1;
        main.add(usernameField, c);

        JLabel passwordLabel = new JLabel("Password:");
        c.gridx = 0;
        c.gridy = 2;
        main.add(passwordLabel, c);

        passwordField.setColumns(18);
        c.gridx = 1;
        main.add(passwordField, c);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);
        buttonPanel.add(exitButton);
        c.gridwidth = 2;
        c.gridx = 0;
        c.gridy = 3;
        main.add(buttonPanel, c);

        statusLabel.setForeground(new Color(180, 40, 40));
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        c.gridy = 4;
        main.add(statusLabel, c);

        add(main, BorderLayout.CENTER);

        loginButton.addActionListener(e -> login());
        registerButton.addActionListener(e -> openRegister());
        exitButton.addActionListener(e -> exitApp());

        getRootPane().setDefaultButton(loginButton);
    }

    private void login() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            showStatus("Username and password are required");
            return;
        }

        setBusy(true);
        SwingWorker<AuthResponse, Void> worker = new SwingWorker<>() {
            @Override
            protected AuthResponse doInBackground() throws Exception {
                return authService.login(username, password);
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    AuthResponse response = get();
                    if (response != null && response.getToken() != null && !response.getToken().isBlank()) {
                        DashboardFrame dashboardFrame = new DashboardFrame();
                        dashboardFrame.setVisible(true);
                        dispose();
                    } else {
                        showStatus(Objects.requireNonNullElse(response != null ? response.getMessage() : null, "Login failed"));
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    showStatus(cause.getMessage() != null ? cause.getMessage() : "Login failed");
                }
            }
        };
        worker.execute();
    }

    private void openRegister() {
        RegisterFrame registerFrame = new RegisterFrame(this);
        registerFrame.setVisible(true);
        setVisible(false);
    }

    private void exitApp() {
        int choice = JOptionPane.showConfirmDialog(this, "Are you sure you want to exit?", "Exit", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            System.exit(0);
        }
    }

    private void setBusy(boolean busy) {
        loginButton.setEnabled(!busy);
        registerButton.setEnabled(!busy);
        exitButton.setEnabled(!busy);
        if (busy) {
            loginButton.setText("Logging in...");
        } else {
            loginButton.setText("Login");
        }
    }

    private void showStatus(String message) {
        statusLabel.setText(message);
    }
}
