package com.clinicdesk;

import com.clinicdesk.config.AppConfig;
import com.clinicdesk.config.DatabaseConfig;
import com.clinicdesk.config.StorageConfig;
import com.clinicdesk.logging.AppLogger;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main JavaFX Application lifecycle and entrypoint for ClinicDesk.
 */
public class Main extends Application {

    private static final Logger LOGGER = AppLogger.getLogger(Main.class);

    @Override
    public void init() throws Exception {
        LOGGER.info("Initializing ClinicDesk application context...");
        AppConfig.load();
        StorageConfig.initialize();
    }

    @Override
    public void start(Stage primaryStage) {
        LOGGER.info("Starting ClinicDesk JavaFX desktop interface...");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 900, 650);
            primaryStage.setScene(scene);
            primaryStage.setTitle("ClinicDesk — Sign In");
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            primaryStage.show();

            LOGGER.info("ClinicDesk login stage presented successfully.");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load LoginView.fxml", e);
        }
    }

    @Override
    public void stop() throws Exception {
        LOGGER.info("Shutting down ClinicDesk resources cleanly...");
        DatabaseConfig.close();
        LOGGER.info("ClinicDesk stopped.");
    }

    public static void main(String[] args) {
        AppLogger.init();
        LOGGER.info("ClinicDesk bootstrap starting...");
        launch(args);
    }
}
