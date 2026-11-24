package com.clinicdesk.ui.controller;

import com.clinicdesk.dao.mysql.MySQLUserDAO;
import com.clinicdesk.exception.AuthenticationException;
import com.clinicdesk.exception.ClinicDeskException;
import com.clinicdesk.logging.AppLogger;
import com.clinicdesk.model.User;
import com.clinicdesk.service.AuthService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Login screen.
 */
public class LoginController {

    private static final Logger LOGGER = AppLogger.getLogger(LoginController.class);

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;
    @FXML private ProgressIndicator loadingIndicator;

    private final AuthService authService;

    public LoginController() {
        this.authService = new AuthService(new MySQLUserDAO());
    }

    public LoginController(AuthService authService) {
        this.authService = authService;
    }

    @FXML
    public void initialize() {
        hideError();
    }

    @FXML
    public void handleLogin(ActionEvent event) {
        String email = emailField.getText();
        String password = passwordField.getText();

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            showError("Please enter both email and password.");
            return;
        }

        setLoading(true);
        hideError();

        // Run authentication in background thread to avoid freezing UI
        Task<User> loginTask = new Task<>() {
            @Override
            protected User call() {
                return authService.login(email, password);
            }
        };

        loginTask.setOnSucceeded(e -> {
            setLoading(false);
            User user = loginTask.getValue();
            LOGGER.info("Login succeeded for " + user.getEmail() + ". Navigating to main shell.");
            navigateToMainShell();
        });

        loginTask.setOnFailed(e -> {
            setLoading(false);
            Throwable error = loginTask.getException();
            LOGGER.log(Level.WARNING, "Login failed: " + error.getMessage());
            showError(error.getMessage() != null ? error.getMessage() : "Invalid email or password.");
        });

        Thread thread = new Thread(loginTask, "Login-Worker-Thread");
        thread.setDaemon(true);
        thread.start();
    }

    private void navigateToMainShell() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainShellView.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) loginButton.getScene().getWindow();
            Scene scene = new Scene(root, 1100, 750);
            stage.setScene(scene);
            stage.setTitle("ClinicDesk — Patient & Clinic Management System");
            stage.centerOnScreen();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load MainShellView.fxml", ex);
            showError("Failed to load application shell: " + ex.getMessage());
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void setLoading(boolean loading) {
        loginButton.setDisable(loading);
        emailField.setDisable(loading);
        passwordField.setDisable(loading);
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
    }
}
