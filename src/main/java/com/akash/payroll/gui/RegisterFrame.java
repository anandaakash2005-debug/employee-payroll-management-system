package com.akash.payroll.gui;

import com.akash.payroll.gui.models.AuthResponse;
import com.akash.payroll.gui.services.AuthService;

import javax.swing.*;
import java.awt.*;

public class RegisterFrame extends JFrame {
    private final JTextField usernameField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JButton registerButton = new JButton("Register");
    private final JButton backButton = new JButton("Back to Login");
    private final JButton clearButton = new JButton("Clear");
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private final AuthService authService = new AuthService();
    private final LoginFrame loginFrame;

    public RegisterFrame(LoginFrame loginFrame) {
        super("Register");
        this.loginFrame = loginFrame;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(420, 340);
        setLocationRelativeTo(null);
        setResizable(false);
        setLayout(new BorderLayout(15, 15));

        JPanel main = new JPanel(new GridBagLayout());
        main.setBackground(new Color(248, 250, 255));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10, 10, 10, 10);
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Create Account", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
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

        c.gridx = 1;
        main.add(usernameField, c);

        JLabel passwordLabel = new JLabel("Password:");
        c.gridx = 0;
        c.gridy = 2;
        main.add(passwordLabel, c);

        c.gridx = 1;
        main.add(passwordField, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.setOpaque(false);
        buttons.add(registerButton);
        buttons.add(clearButton);
        buttons.add(backButton);
        c.gridwidth = 2;
        c.gridx = 0;
        c.gridy = 3;
        main.add(buttons, c);

        statusLabel.setForeground(new Color(180, 40, 40));
        c.gridy = 4;
        main.add(statusLabel, c);

        add(main, BorderLayout.CENTER);

        registerButton.addActionListener(e -> register());
        clearButton.addActionListener(e -> clearFields());
        backButton.addActionListener(e -> backToLogin());
    }

    private void register() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            showStatus("Username and password are required");
            return;
        }
        if (password.length() < 6) {
            showStatus("Password must be at least 6 characters");
            return;
        }

        setBusy(true);
        SwingWorker<AuthResponse, Void> worker = new SwingWorker<>() {
            @Override
            protected AuthResponse doInBackground() throws Exception {
                return authService.register(username, password);
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    AuthResponse response = get();
                    if (response != null && response.getToken() != null && !response.getToken().isBlank()) {
                        JOptionPane.showMessageDialog(RegisterFrame.this, response.getMessage(), "Registration successful", JOptionPane.INFORMATION_MESSAGE);
                        backToLogin();
                    } else {
                        showStatus(response != null ? response.getMessage() : "Registration failed");
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    showStatus(cause.getMessage() != null ? cause.getMessage() : "Registration failed");
                }
            }
        };
        worker.execute();
    }

    private void clearFields() {
        usernameField.setText("");
        passwordField.setText("");
        statusLabel.setText(" ");
    }

    private void backToLogin() {
        dispose();
        if (loginFrame != null) {
            loginFrame.setVisible(true);
        }
    }

    private void setBusy(boolean busy) {
        registerButton.setEnabled(!busy);
        clearButton.setEnabled(!busy);
        backButton.setEnabled(!busy);
        if (busy) {
            registerButton.setText("Registering...");
        } else {
            registerButton.setText("Register");
        }
    }

    private void showStatus(String message) {
        statusLabel.setText(message);
        statusLabel.setForeground(new Color(180, 40, 40));
    }
}
